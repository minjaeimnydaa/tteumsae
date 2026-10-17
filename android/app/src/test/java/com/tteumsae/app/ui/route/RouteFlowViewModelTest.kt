package com.tteumsae.app.ui.route

import androidx.lifecycle.SavedStateHandle
import com.tteumsae.app.data.RecommendationResult
import com.tteumsae.app.data.route.RouteGateway
import com.tteumsae.app.data.route.RouteWaypoint
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.OperationStatus
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.route.RouteLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteFlowViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_787_899_800_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `유효한 검색은 로딩 뒤 결과로 전환된다`() = runTest(dispatcher) {
        val gateway = FakeRouteGateway(results = ArrayDeque(listOf(result(recommendation("one")))))
        val viewModel = viewModel(gateway)
        validInput(viewModel)

        viewModel.search()
        assertEquals(RouteStage.LOADING, viewModel.uiState.value.stage)
        advanceUntilIdle()

        assertEquals(RouteStage.RESULTS, viewModel.uiState.value.stage)
        assertEquals(listOf("one"), viewModel.uiState.value.recommendations.map { it.place.id })
        assertEquals(now, viewModel.uiState.value.calculatedAtEpochMillis)
    }

    @Test
    fun `빈 결과와 초기 검색 실패를 구분한다`() = runTest(dispatcher) {
        val emptyViewModel = viewModel(FakeRouteGateway(ArrayDeque(listOf(result()))))
        validInput(emptyViewModel)
        emptyViewModel.search()
        advanceUntilIdle()
        assertEquals(RouteStage.RESULTS, emptyViewModel.uiState.value.stage)
        assertTrue(emptyViewModel.uiState.value.recommendations.isEmpty())
        assertNull(emptyViewModel.uiState.value.errorMessage)

        val failedViewModel = viewModel(
            FakeRouteGateway(ArrayDeque(listOf(Result.failure(IllegalStateException("network"))))),
        )
        validInput(failedViewModel)
        failedViewModel.search()
        advanceUntilIdle()
        assertEquals(RouteStage.LOCATION, failedViewModel.uiState.value.stage)
        assertTrue(failedViewModel.uiState.value.errorMessage!!.isNotBlank())
    }

    @Test
    fun `한 곳 선택은 이전 선택을 교체하고 명시적으로 해제된다`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakeRouteGateway(
                ArrayDeque(listOf(result(recommendation("one"), recommendation("two")))),
            ),
        )
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()

        viewModel.selectPlace("one")
        viewModel.selectPlace("two")
        assertEquals("two", viewModel.uiState.value.selectedPlaceId)
        viewModel.clearSelection()
        assertNull(viewModel.uiState.value.selectedPlaceId)
    }

    @Test
    fun `새 검색은 이전 선택을 먼저 비운다`() = runTest(dispatcher) {
        val gateway = FakeRouteGateway(
            ArrayDeque(
                listOf(
                    result(recommendation("one")),
                    result(recommendation("two")),
                ),
            ),
        )
        val viewModel = viewModel(gateway)
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()
        viewModel.selectPlace("one")

        viewModel.search()
        assertNull(viewModel.uiState.value.selectedPlaceId)
        advanceUntilIdle()
        assertEquals(listOf("two"), viewModel.uiState.value.recommendations.map { it.place.id })
    }

    @Test
    fun `조건 편집을 취소하면 기존 입력 결과와 선택을 복원한다`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val viewModel = viewModel(
            FakeRouteGateway(ArrayDeque(listOf(result(recommendation("one"))))),
            savedState,
        )
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()
        viewModel.selectPlace("one")
        val original = viewModel.uiState.value

        viewModel.beginEditing()
        viewModel.updateDestination(RouteLocation("정동진", Coordinates(37.69, 129.03)))
        viewModel.updateFilters(setOf(PlaceCategory.CAFE))
        assertEquals(RouteStage.LOCATION, viewModel.uiState.value.stage)
        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("one", viewModel.uiState.value.selectedPlaceId)
        assertEquals(original.recommendations, viewModel.uiState.value.recommendations)

        viewModel.cancelEditing()

        assertEquals(original, viewModel.uiState.value)
        assertEquals("경포대", savedState.get<String>("route.destination.name"))
        assertEquals("one", savedState.get<String>("route.selectedPlaceId"))
    }

    @Test
    fun `유효하지 않은 편집 제출은 기존 선택과 편집 취소를 보존한다`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRouteGateway(ArrayDeque(listOf(result(recommendation("one"))))))
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()
        viewModel.selectPlace("one")
        val original = viewModel.uiState.value

        viewModel.beginEditing()
        viewModel.updateDestination(null)
        viewModel.search()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("one", viewModel.uiState.value.selectedPlaceId)
        assertTrue(viewModel.uiState.value.errorMessage!!.isNotBlank())
        viewModel.cancelEditing()
        assertEquals(original, viewModel.uiState.value)
    }

    @Test
    fun `편집 후 새 추천을 제출한 때만 기존 선택과 편집 상태를 비운다`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakeRouteGateway(
                ArrayDeque(listOf(result(recommendation("one")), result(recommendation("two")))),
            ),
        )
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()
        viewModel.selectPlace("one")
        viewModel.beginEditing()
        viewModel.updateDestination(RouteLocation("정동진", Coordinates(37.69, 129.03)))

        viewModel.search()

        assertFalse(viewModel.uiState.value.isEditing)
        assertNull(viewModel.uiState.value.selectedPlaceId)
        assertEquals(RouteStage.LOADING, viewModel.uiState.value.stage)
        advanceUntilIdle()
        viewModel.cancelEditing()
        assertEquals(RouteStage.RESULTS, viewModel.uiState.value.stage)
        assertEquals("정동진", viewModel.uiState.value.input.destination?.name)
        assertEquals(listOf("two"), viewModel.uiState.value.recommendations.map { it.place.id })
    }

    @Test
    fun `조건 편집은 진행 중인 새로고침을 취소하고 취소 후 다시 새로고침할 수 있다`() = runTest(dispatcher) {
        val pending = CompletableDeferred<RecommendationResult>()
        var calls = 0
        val gateway = object : RouteGateway {
            override suspend fun recommendations(criteria: SearchCriteria): RecommendationResult =
                when (calls++) {
                    0 -> result(recommendation("one")).getOrThrow()
                    1 -> pending.await()
                    else -> result(recommendation("two")).getOrThrow()
                }

            override suspend fun calculateRoute(
                start: Coordinates,
                destination: Coordinates,
                waypoints: List<RouteWaypoint>,
            ): RouteSummary = error("not used")
        }
        val viewModel = viewModel(gateway)
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()
        viewModel.selectPlace("one")
        viewModel.refresh()
        runCurrent()

        viewModel.beginEditing()
        pending.complete(result(recommendation("late")).getOrThrow())
        advanceUntilIdle()
        assertEquals(RouteStage.LOCATION, viewModel.uiState.value.stage)
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals("one", viewModel.uiState.value.selectedPlaceId)
        viewModel.cancelEditing()
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(listOf("two"), viewModel.uiState.value.recommendations.map { it.place.id })
    }

    @Test
    fun `명시적 새 검색은 편집을 종료하고 이전 결과로 취소 복귀하지 않는다`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeRouteGateway(ArrayDeque(listOf(result(recommendation("one"))))))
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()
        viewModel.selectPlace("one")
        viewModel.beginEditing()

        viewModel.startNewSearch()
        viewModel.cancelEditing()

        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals(RouteStage.LOCATION, viewModel.uiState.value.stage)
        assertNull(viewModel.uiState.value.selectedPlaceId)
        assertTrue(viewModel.uiState.value.recommendations.isEmpty())
    }

    @Test
    fun `편집 중 프로세스 복원은 마지막 입력만 복원하며 빈 결과로 복귀하지 않는다`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val first = viewModel(
            FakeRouteGateway(ArrayDeque(listOf(result(recommendation("one"))))),
            savedState,
        )
        validInput(first)
        first.search()
        advanceUntilIdle()
        first.beginEditing()
        first.updateDestination(RouteLocation("정동진", Coordinates(37.69, 129.03)))

        val restored = viewModel(FakeRouteGateway(), savedState)

        assertFalse(restored.uiState.value.isEditing)
        assertEquals(RouteStage.LOCATION, restored.uiState.value.stage)
        assertEquals("정동진", restored.uiState.value.input.destination?.name)
        assertTrue(restored.uiState.value.recommendations.isEmpty())
        restored.cancelEditing()
        assertEquals(RouteStage.LOCATION, restored.uiState.value.stage)
    }

    @Test
    fun `새로고침 실패는 마지막 안전 결과를 보존한다`() = runTest(dispatcher) {
        val gateway = FakeRouteGateway(
            ArrayDeque(
                listOf(
                    result(recommendation("one")),
                    Result.failure(IllegalStateException("refresh failed")),
                ),
            ),
        )
        val viewModel = viewModel(gateway)
        validInput(viewModel)
        viewModel.search()
        advanceUntilIdle()

        viewModel.refresh()
        assertTrue(viewModel.uiState.value.isRefreshing)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(listOf("one"), viewModel.uiState.value.recommendations.map { it.place.id })
        assertTrue(viewModel.uiState.value.warning.isNotBlank())
    }

    @Test
    fun `로딩 중 새 검색으로 돌아가면 늦은 응답을 무시한다`() = runTest(dispatcher) {
        val pending = CompletableDeferred<RecommendationResult>()
        val gateway = object : RouteGateway {
            override suspend fun recommendations(criteria: SearchCriteria): RecommendationResult =
                pending.await()

            override suspend fun calculateRoute(
                start: Coordinates,
                destination: Coordinates,
                waypoints: List<RouteWaypoint>,
            ): RouteSummary = error("not used")
        }
        val viewModel = viewModel(gateway)
        validInput(viewModel)
        viewModel.search()
        runCurrent()

        viewModel.startNewSearch()
        pending.complete(result(recommendation("late")).getOrThrow())
        advanceUntilIdle()

        assertEquals(RouteStage.LOCATION, viewModel.uiState.value.stage)
        assertTrue(viewModel.uiState.value.recommendations.isEmpty())
    }

    @Test
    fun `복원 데이터에 추천 payload가 없으면 위치 단계에서 재개한다`() = runTest(dispatcher) {
        val savedStateHandle = SavedStateHandle()
        val first = viewModel(FakeRouteGateway(ArrayDeque(listOf(result(recommendation("one"))))), savedStateHandle)
        validInput(first)
        first.updateFilters(setOf(PlaceCategory.CAFE))
        first.search()
        advanceUntilIdle()
        first.selectPlace("one")

        val restored = viewModel(FakeRouteGateway(), savedStateHandle)

        assertEquals(RouteStage.LOCATION, restored.uiState.value.stage)
        assertTrue(restored.uiState.value.recommendations.isEmpty())
        assertEquals("강릉역", restored.uiState.value.input.start?.name)
        assertEquals("경포대", restored.uiState.value.input.destination?.name)
        assertEquals(now + 60 * 60_000, restored.uiState.value.input.arrivalDeadlineEpochMillis)
        assertEquals(setOf(PlaceCategory.CAFE), restored.uiState.value.input.categories)
        assertEquals("one", restored.uiState.value.selectedPlaceId)
    }

    @Test
    fun `이전 버전 좌표는 주소 이름이어도 복원하지 않고 다른 저장 상태는 보존한다`() {
        val state = legacyLocationState()
        state["unrelated.savedPlace"] = "keep"
        val restored = viewModel(FakeRouteGateway(), state)

        assertNull(restored.uiState.value.input.start)
        assertNull(restored.uiState.value.input.destination)
        assertNull(restored.uiState.value.selectedPlaceId)
        assertFalse(state.contains("route.start.latitude"))
        assertFalse(state.contains("route.start.longitude"))
        assertEquals("keep", state.get<String>("unrelated.savedPlace"))
    }

    @Test
    fun `현재 빌드와 다른 위치 모드의 경로를 복원하지 않는다`() {
        val state = legacyLocationState()
        state["route.locationMode"] = "unrecognized_mode"
        val restored = viewModel(FakeRouteGateway(), state)

        assertNull(restored.uiState.value.input.start)
        assertNull(restored.uiState.value.input.arrivalDeadlineEpochMillis)
        assertFalse(state.contains("route.destination.latitude"))
    }

    private fun legacyLocationState() = SavedStateHandle(
        mapOf(
            "route.start.name" to "강원특별자치도 강릉시 교동",
            "route.start.latitude" to 37.75,
            "route.start.longitude" to 128.87,
            "route.destination.name" to "경포대",
            "route.destination.latitude" to 37.80,
            "route.destination.longitude" to 128.90,
            "route.arrivalDeadlineEpochMillis" to now + 60 * 60_000,
            "route.selectedPlaceId" to "one",
        ),
    )

    private fun viewModel(
        gateway: RouteGateway,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = RouteFlowViewModel(savedStateHandle, gateway) { now }

    private fun validInput(viewModel: RouteFlowViewModel) {
        viewModel.updateStart(RouteLocation("강릉역", Coordinates(37.75, 128.87)))
        viewModel.updateDestination(RouteLocation("경포대", Coordinates(37.80, 128.90)))
        viewModel.updateDeadline(now + 60 * 60_000)
    }

    private fun result(vararg recommendations: SafeRecommendation) = Result.success(
        RecommendationResult(
            recommendations = recommendations.toList(),
            warning = "",
            calculatedAtEpochMillis = now,
            arrivalDeadlineEpochMillis = now + 60 * 60_000,
            minimumStayMinutes = 15,
        ),
    )

    private fun recommendation(id: String) = SafeRecommendation(
        place = PlaceCandidate(
            id = id,
            name = id,
            category = PlaceCategory.CAFE,
            stayMinutes = 0,
            firstLegMinutes = 10,
            secondLegMinutes = 10,
            detourMinutes = 5,
            reason = "",
            tags = emptyList(),
            latitude = 37.76,
            longitude = 128.88,
        ),
        totalMinutes = 20,
        marginMinutes = 35,
        safetyLevel = SafetyLevel.AVAILABLE,
        operationStatus = OperationStatus.UNKNOWN,
        minimumStayMinutes = 15,
        maximumStayMinutes = 35,
        latestDepartureEpochMillis = now + 40 * 60_000,
    )

    private class FakeRouteGateway(
        private val results: ArrayDeque<Result<RecommendationResult>> = ArrayDeque(),
    ) : RouteGateway {
        override suspend fun recommendations(criteria: SearchCriteria): RecommendationResult =
            results.removeFirst().getOrThrow()

        override suspend fun calculateRoute(
            start: Coordinates,
            destination: Coordinates,
            waypoints: List<RouteWaypoint>,
        ): RouteSummary = error("not used")
    }
}
