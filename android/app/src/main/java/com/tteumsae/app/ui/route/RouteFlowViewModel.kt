package com.tteumsae.app.ui.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tteumsae.app.data.route.RouteGateway
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.SearchMode
import com.tteumsae.app.domain.TransportMode
import com.tteumsae.app.domain.route.RouteFlowInput
import com.tteumsae.app.domain.route.RouteLocation
import com.tteumsae.app.domain.route.SAFETY_BUFFER_MINUTES
import com.tteumsae.app.domain.route.isValidArrivalDeadline
import com.tteumsae.app.domain.route.remainingWholeMinutes
import com.tteumsae.app.location.LocationAccessPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RouteFlowViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val gateway: RouteGateway,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    init {
        if (savedStateHandle.get<String>(KEY_LOCATION_MODE) != LocationAccessPolicy.persistenceMode) {
            // Legacy GPS coordinates may already have an address as their display name.
            // Reset this route only, without touching account or saved-place storage.
            listOf(
                KEY_START_NAME, KEY_START_LATITUDE, KEY_START_LONGITUDE,
                KEY_DESTINATION_NAME, KEY_DESTINATION_LATITUDE, KEY_DESTINATION_LONGITUDE,
                KEY_ARRIVAL_DEADLINE, KEY_TRANSPORT_MODE, KEY_CATEGORIES, KEY_SELECTED_PLACE_ID,
            ).forEach { savedStateHandle.remove<Any?>(it) }
            savedStateHandle[KEY_LOCATION_MODE] = LocationAccessPolicy.persistenceMode
        }
    }

    private val _uiState = MutableStateFlow(
        RouteFlowUiState(
            input = restoreInput(savedStateHandle),
            selectedPlaceId = savedStateHandle[KEY_SELECTED_PLACE_ID],
        ),
    )
    val uiState: StateFlow<RouteFlowUiState> = _uiState.asStateFlow()

    private var lastSearchCriteria: SearchCriteria? = null
    private var searchJob: Job? = null
    private var editingSnapshot: RouteFlowUiState? = null

    fun updateStart(location: RouteLocation?) {
        updateInput(_uiState.value.input.copy(start = location))
    }

    fun updateDestination(location: RouteLocation?) {
        updateInput(_uiState.value.input.copy(destination = location))
    }

    fun updateDeadline(arrivalDeadlineEpochMillis: Long?) {
        updateInput(
            _uiState.value.input.copy(
                arrivalDeadlineEpochMillis = arrivalDeadlineEpochMillis,
            ),
        )
    }

    fun updateFilters(categories: Set<PlaceCategory>) {
        updateInput(_uiState.value.input.copy(categories = categories))
    }

    fun search() {
        val criteria = criteriaOrNull() ?: return
        editingSnapshot = null
        lastSearchCriteria = criteria
        persistSelectedPlaceId(null)
        _uiState.value = _uiState.value.copy(
            stage = RouteStage.LOADING,
            recommendations = emptyList(),
            baseRoute = null,
            selectedPlaceId = null,
            calculatedAtEpochMillis = null,
            warning = "",
            errorMessage = null,
            isRefreshing = false,
            isEditing = false,
        )
        requestRecommendations(criteria, isRefresh = false)
    }

    fun beginEditing() {
        val current = _uiState.value
        if (current.stage != RouteStage.RESULTS || current.isEditing) return
        searchJob?.cancel()
        searchJob = null
        editingSnapshot = current.copy(isRefreshing = false)
        _uiState.value = current.copy(
            stage = RouteStage.LOCATION,
            isRefreshing = false,
            isEditing = true,
            errorMessage = null,
        )
    }

    fun cancelEditing() {
        val snapshot = editingSnapshot ?: return
        editingSnapshot = null
        persistInput(snapshot.input)
        persistSelectedPlaceId(snapshot.selectedPlaceId)
        _uiState.value = snapshot
    }

    fun selectPlace(placeId: String) {
        if (_uiState.value.recommendations.none { it.place.id == placeId }) return
        persistSelectedPlaceId(placeId)
        _uiState.value = _uiState.value.copy(selectedPlaceId = placeId)
    }

    fun clearSelection() {
        persistSelectedPlaceId(null)
        _uiState.value = _uiState.value.copy(selectedPlaceId = null)
    }

    fun refresh() {
        val criteria = lastSearchCriteria ?: return
        if (_uiState.value.stage != RouteStage.RESULTS || _uiState.value.isRefreshing) return
        _uiState.value = _uiState.value.copy(
            isRefreshing = true,
            errorMessage = null,
        )
        requestRecommendations(criteria, isRefresh = true)
    }

    fun startNewSearch() {
        searchJob?.cancel()
        searchJob = null
        editingSnapshot = null
        lastSearchCriteria = null
        persistSelectedPlaceId(null)
        _uiState.value = RouteFlowUiState(input = _uiState.value.input)
    }

    private fun criteriaOrNull(): SearchCriteria? {
        val input = _uiState.value.input
        val start = input.start
        val destination = input.destination
        val deadline = input.arrivalDeadlineEpochMillis
        val now = nowEpochMillis()
        if (
            start == null ||
            destination == null ||
            deadline == null ||
            !isValidArrivalDeadline(deadline, now)
        ) {
            _uiState.value = _uiState.value.copy(
                stage = RouteStage.LOCATION,
                errorMessage = "출발지, 목적지와 15분~24시간 이내 도착 마감을 확인해주세요.",
            )
            return null
        }
        return SearchCriteria(
            mode = SearchMode.ON_THE_WAY,
            startName = start.name,
            endName = destination.name,
            deadlineMinutesFromNow = remainingWholeMinutes(deadline, now),
            safetyBufferMinutes = SAFETY_BUFFER_MINUTES,
            transportMode = input.transportMode,
            categories = input.categories,
            startCoordinates = start.coordinates,
            endCoordinates = destination.coordinates,
            arrivalDeadlineEpochMillis = deadline,
        )
    }

    private fun requestRecommendations(criteria: SearchCriteria, isRefresh: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            runCatching { gateway.recommendations(criteria) }
                .onSuccess { result ->
                    val selectedPlaceId = _uiState.value.selectedPlaceId
                        ?.takeIf { selected ->
                            result.recommendations.any { it.place.id == selected }
                        }
                    if (_uiState.value.selectedPlaceId != selectedPlaceId) {
                        persistSelectedPlaceId(selectedPlaceId)
                    }
                    _uiState.value = _uiState.value.copy(
                        stage = RouteStage.RESULTS,
                        recommendations = result.recommendations,
                        baseRoute = result.baseRoute,
                        selectedPlaceId = selectedPlaceId,
                        calculatedAtEpochMillis = result.calculatedAtEpochMillis,
                        corridorRadiusMeters = result.corridorRadiusMeters,
                        warning = result.warning,
                        errorMessage = null,
                        isRefreshing = false,
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    val message = error.message?.takeIf(String::isNotBlank)
                        ?: "추천을 불러오지 못했습니다."
                    _uiState.value = if (isRefresh) {
                        _uiState.value.copy(
                            warning = "현재 교통으로 다시 확인하지 못했어요. 마지막 결과를 보여드려요.",
                            errorMessage = message,
                            isRefreshing = false,
                        )
                    } else {
                        _uiState.value.copy(
                            stage = RouteStage.LOCATION,
                            recommendations = emptyList(),
                            baseRoute = null,
                            errorMessage = message,
                            isRefreshing = false,
                        )
                    }
                }
        }
    }

    private fun updateInput(input: RouteFlowInput) {
        persistInput(input)
        _uiState.value = _uiState.value.copy(input = input, errorMessage = null)
    }

    private fun persistInput(input: RouteFlowInput) {
        savedStateHandle[KEY_START_NAME] = input.start?.name
        savedStateHandle[KEY_START_LATITUDE] = input.start?.coordinates?.latitude
        savedStateHandle[KEY_START_LONGITUDE] = input.start?.coordinates?.longitude
        savedStateHandle[KEY_DESTINATION_NAME] = input.destination?.name
        savedStateHandle[KEY_DESTINATION_LATITUDE] = input.destination?.coordinates?.latitude
        savedStateHandle[KEY_DESTINATION_LONGITUDE] = input.destination?.coordinates?.longitude
        savedStateHandle[KEY_ARRIVAL_DEADLINE] = input.arrivalDeadlineEpochMillis
        savedStateHandle[KEY_TRANSPORT_MODE] = input.transportMode.name
        savedStateHandle[KEY_CATEGORIES] = ArrayList(input.categories.map { it.name }.sorted())
    }

    private fun persistSelectedPlaceId(placeId: String?) {
        savedStateHandle[KEY_SELECTED_PLACE_ID] = placeId
    }

    companion object {
        fun factory(gateway: RouteGateway): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RouteFlowViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    gateway = gateway,
                )
            }
        }

        private const val KEY_START_NAME = "route.start.name"
        private const val KEY_LOCATION_MODE = "route.locationMode"
        private const val KEY_START_LATITUDE = "route.start.latitude"
        private const val KEY_START_LONGITUDE = "route.start.longitude"
        private const val KEY_DESTINATION_NAME = "route.destination.name"
        private const val KEY_DESTINATION_LATITUDE = "route.destination.latitude"
        private const val KEY_DESTINATION_LONGITUDE = "route.destination.longitude"
        private const val KEY_ARRIVAL_DEADLINE = "route.arrivalDeadlineEpochMillis"
        private const val KEY_TRANSPORT_MODE = "route.transportMode"
        private const val KEY_CATEGORIES = "route.categories"
        private const val KEY_SELECTED_PLACE_ID = "route.selectedPlaceId"

        private fun restoreInput(savedStateHandle: SavedStateHandle): RouteFlowInput =
            RouteFlowInput(
                start = restoredLocation(
                    savedStateHandle,
                    KEY_START_NAME,
                    KEY_START_LATITUDE,
                    KEY_START_LONGITUDE,
                ),
                destination = restoredLocation(
                    savedStateHandle,
                    KEY_DESTINATION_NAME,
                    KEY_DESTINATION_LATITUDE,
                    KEY_DESTINATION_LONGITUDE,
                ),
                arrivalDeadlineEpochMillis = savedStateHandle[KEY_ARRIVAL_DEADLINE],
                transportMode = savedStateHandle.get<String>(KEY_TRANSPORT_MODE)
                    ?.let { runCatching { TransportMode.valueOf(it) }.getOrNull() }
                    ?: TransportMode.CAR,
                categories = savedStateHandle.get<ArrayList<String>>(KEY_CATEGORIES)
                    .orEmpty()
                    .mapNotNull { runCatching { PlaceCategory.valueOf(it) }.getOrNull() }
                    .toSet(),
            )

        private fun restoredLocation(
            savedStateHandle: SavedStateHandle,
            nameKey: String,
            latitudeKey: String,
            longitudeKey: String,
        ): RouteLocation? {
            val name = savedStateHandle.get<String>(nameKey) ?: return null
            val latitude = savedStateHandle.get<Double>(latitudeKey) ?: return null
            val longitude = savedStateHandle.get<Double>(longitudeKey) ?: return null
            return RouteLocation(name, Coordinates(latitude, longitude))
        }
    }
}
