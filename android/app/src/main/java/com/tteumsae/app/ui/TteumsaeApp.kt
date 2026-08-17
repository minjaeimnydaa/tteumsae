package com.tteumsae.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.shape.DotPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles
import com.kakao.vectormap.shape.PolygonStylesSet
import com.tteumsae.app.BuildConfig
import com.tteumsae.app.data.TteumsaeApi
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.LocationSearchResult
import com.tteumsae.app.domain.OperationStatus
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.SearchMode
import com.tteumsae.app.domain.TransportMode
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class AppScreen {
    HOME,
    SAVED,
    SETTINGS,
    LOCATION,
    TIME,
    CONDITIONS,
    LOADING,
    RESULTS,
    DETAIL,
}

private enum class MainTab {
    EXPLORE,
    SAVED,
    SETTINGS,
}

private enum class SavedSort {
    RECENT,
    NAME,
}

internal enum class RecommendationIntent(val label: String) {
    ANY("아무거나"),
    MEAL("식사"),
    CAFE("카페"),
    WALK_TOUR("산책·관광"),
    INDOOR("실내 활동"),
    NO_FOOD("지금은 음식 제외"),
}

private const val MIN_DEADLINE_MINUTES = 15
private const val SLIDER_MAX_DEADLINE_MINUTES = 360
private const val MAX_DEADLINE_MINUTES = 1440
private const val MAX_KAKAO_WAYPOINTS = 5

private data class MapCandidate(
    val id: String,
    val coordinates: Coordinates,
    val category: PlaceCategory,
    val selectedOrder: Int?,
)

internal fun orderWaypointIdsAlongRoute(
    start: Coordinates,
    destination: Coordinates,
    waypoints: List<Pair<String, Coordinates>>,
): List<String> {
    val latitudeDelta = destination.latitude - start.latitude
    val longitudeDelta = destination.longitude - start.longitude
    val denominator = latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta
    if (denominator == 0.0) return waypoints.map { it.first }
    return waypoints.sortedBy { (_, point) ->
        ((point.latitude - start.latitude) * latitudeDelta +
            (point.longitude - start.longitude) * longitudeDelta) / denominator
    }.map(Pair<String, Coordinates>::first)
}

internal fun selectedRouteEstimate(
    deadlineMinutes: Int,
    recommendations: List<SafeRecommendation>,
): Pair<Int, Int> {
    if (recommendations.isEmpty()) return 0 to deadlineMinutes
    val first = recommendations.first().place
    val directMinutes = (first.firstLegMinutes + first.secondLegMinutes - first.detourMinutes)
        .coerceAtLeast(0)
    val totalMinutes = directMinutes + recommendations.sumOf {
        it.place.detourMinutes + it.place.stayMinutes
    }
    return totalMinutes to (deadlineMinutes - totalMinutes)
}

internal fun extendedDeadlineMinutes(current: Int): Int =
    (current + 30).coerceAtMost(MAX_DEADLINE_MINUTES)

internal fun deniedLocationPermissionNeedsSettings(
    permissions: Map<String, Boolean>,
    canAskAgain: (String) -> Boolean,
): Boolean = permissions.any { (permission, granted) ->
    !granted && !canAskAgain(permission)
}

internal fun networkFailureMessage(operation: String, detail: String?): String =
    "${operation}에 실패했어요. ${detail?.takeIf(String::isNotBlank) ?: "네트워크 연결을 확인해 주세요."}"

internal fun shouldAutoLocateStart(startName: String, hasLocation: Boolean): Boolean =
    startName == "현재 위치" && !hasLocation
private const val CONTACT_EMAIL = "minjaeimnyda@gmail.com"
private const val PRIVACY_POLICY_URL = ""
private const val LOCATION_TERMS_URL = ""
private const val HOME_INTRO_PREFERENCES = "home_intro"
private const val HOME_INTRO_HIDDEN_DATE = "hidden_date"

private data class SavedPlaceEntry(
    val place: PlaceCandidate,
    val savedAtMillis: Long,
)

private data class PlaceCardItem(
    val place: PlaceCandidate,
    val savedEntry: SavedPlaceEntry?,
)

private val savedImageCache = object : LruCache<String, Bitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

@Composable
fun TteumsaeApp() {
    val context = LocalContext.current
    val api = remember { TteumsaeApi() }
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var mode by rememberSaveable { mutableStateOf(SearchMode.ON_THE_WAY) }
    var startName by rememberSaveable { mutableStateOf("현재 위치") }
    var endName by rememberSaveable { mutableStateOf("") }
    var startLocation by remember { mutableStateOf<LocationSearchResult?>(null) }
    var endLocation by remember { mutableStateOf<LocationSearchResult?>(null) }
    var deadline by rememberSaveable { mutableStateOf(90) }
    var buffer by rememberSaveable { mutableStateOf(15) }
    var transport by rememberSaveable { mutableStateOf(TransportMode.CAR) }
    var categories by remember { mutableStateOf(emptySet<PlaceCategory>()) }
    var excludeRestaurants by rememberSaveable { mutableStateOf(false) }
    var recommendations by remember { mutableStateOf(emptyList<SafeRecommendation>()) }
    var recommendationWarning by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SafeRecommendation?>(null) }
    var activeCriteria by remember { mutableStateOf<SearchCriteria?>(null) }
    var locationChecking by remember { mutableStateOf(false) }
    var currentLocationTarget by remember { mutableStateOf<RequestedMapLocation?>(null) }
    var savedPlaces by remember { mutableStateOf(loadSavedPlaces(context)) }
    var catalogPlaces by remember { mutableStateOf(emptyList<PlaceCandidate>()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogLoadingMore by remember { mutableStateOf(false) }
    var catalogPage by remember { mutableStateOf(1) }
    var catalogHasMore by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogLoadAttempt by rememberSaveable { mutableStateOf(0) }
    var showHomeIntro by rememberSaveable { mutableStateOf(shouldShowHomeIntro(context)) }
    val appScope = rememberCoroutineScope()
    val updateSavedPlaces: (List<SavedPlaceEntry>) -> Unit = { updated ->
        savedPlaces = updated
        storeSavedPlaces(context, updated)
    }

    LaunchedEffect(screen, catalogLoadAttempt) {
        if (screen == AppScreen.SAVED) {
            catalogLoading = true
            catalogError = null
            try {
                val firstPage = api.places()
                catalogPlaces = firstPage.places
                catalogPage = 1
                catalogHasMore = firstPage.hasMore
            } catch (error: Exception) {
                catalogError = error.message ?: "TourAPI 장소를 불러오지 못했어요."
            } finally {
                catalogLoading = false
            }
        }
    }

    val criteria = SearchCriteria(
        mode = mode,
        startName = startName.ifBlank { "현재 위치" },
        endName = endName.ifBlank { if (mode == SearchMode.NEARBY) startName else "" },
        deadlineMinutesFromNow = deadline,
        safetyBufferMinutes = buffer,
        transportMode = if (mode == SearchMode.ON_THE_WAY) TransportMode.CAR else transport,
        categories = recommendationCategories(categories, excludeRestaurants),
        startCoordinates = startLocation?.coordinates,
        endCoordinates = endLocation?.coordinates,
    )

    when (screen) {
        AppScreen.HOME -> HomeScreen(
            showIntro = showHomeIntro,
            currentLocationTarget = currentLocationTarget,
            onCurrentLocationTargetChange = { currentLocationTarget = it },
            onDismissIntro = { hideForToday ->
                if (hideForToday) hideHomeIntroForToday(context)
                showHomeIntro = false
            },
            onStart = { coordinates ->
                startName = "현재 위치"
                val currentLocation = coordinates?.let {
                    LocationSearchResult(
                        id = "current-location",
                        name = "현재 위치",
                        address = "GPS로 확인한 위치",
                        coordinates = it,
                    )
                }
                startLocation = currentLocation
                if (mode == SearchMode.NEARBY) {
                    endName = "현재 위치"
                    endLocation = currentLocation
                } else {
                    endName = ""
                    endLocation = null
                }
                screen = AppScreen.LOCATION
            },
            onTabSelected = { tab ->
                screen = when (tab) {
                    MainTab.EXPLORE -> AppScreen.HOME
                    MainTab.SAVED -> AppScreen.SAVED
                    MainTab.SETTINGS -> AppScreen.SETTINGS
                }
            },
        )

        AppScreen.SAVED -> SavedPlacesScreen(
            catalogPlaces = catalogPlaces,
            savedPlaces = savedPlaces,
            isLoading = catalogLoading,
            isLoadingMore = catalogLoadingMore,
            hasMore = catalogHasMore,
            errorMessage = catalogError,
            onRetry = { catalogLoadAttempt += 1 },
            onLoadMore = {
                if (!catalogLoading && !catalogLoadingMore && catalogHasMore) {
                    catalogLoadingMore = true
                    appScope.launch {
                        try {
                            val nextPage = api.places(page = catalogPage + 1)
                            catalogPlaces = (catalogPlaces + nextPage.places)
                                .distinctBy { it.id }
                            catalogPage += 1
                            catalogHasMore = nextPage.hasMore
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "장소를 더 불러오지 못했어요.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } finally {
                            catalogLoadingMore = false
                        }
                    }
                }
            },
            onToggleSave = { place ->
                val existing = savedPlaces.find { it.place.id == place.id }
                if (existing == null) {
                    updateSavedPlaces(listOf(SavedPlaceEntry(place, System.currentTimeMillis())) + savedPlaces)
                } else {
                    updateSavedPlaces(savedPlaces.filterNot { it.place.id == place.id })
                }
            },
            onRestore = { entry ->
                updateSavedPlaces(
                    (savedPlaces.filterNot { it.place.id == entry.place.id } + entry)
                        .sortedByDescending { it.savedAtMillis },
                )
            },
            onTabSelected = { tab ->
                screen = when (tab) {
                    MainTab.EXPLORE -> AppScreen.HOME
                    MainTab.SAVED -> AppScreen.SAVED
                    MainTab.SETTINGS -> AppScreen.SETTINGS
                }
            },
        )

        AppScreen.SETTINGS -> SettingsTabScreen(
            savedCount = savedPlaces.size,
            onClearSaved = { updateSavedPlaces(emptyList()) },
            onTabSelected = { tab ->
                screen = when (tab) {
                    MainTab.EXPLORE -> AppScreen.HOME
                    MainTab.SAVED -> AppScreen.SAVED
                    MainTab.SETTINGS -> AppScreen.SETTINGS
                }
            },
        )

        AppScreen.LOCATION -> LocationScreen(
            mode = mode,
            startName = startName,
            endName = endName,
            startLocation = startLocation,
            endLocation = endLocation,
            searchPlaces = api::searchPlaces,
            resolveCurrentAddress = api::regionAddress,
            onModeChange = {
                mode = it
                if (it == SearchMode.NEARBY) {
                    endName = startName
                    endLocation = startLocation
                } else {
                    endName = ""
                    endLocation = null
                }
                if (it == SearchMode.ON_THE_WAY) transport = TransportMode.CAR
            },
            onStartNameChange = {
                startName = it
                startLocation = null
                currentLocationTarget = null
                if (mode == SearchMode.NEARBY) {
                    endName = it
                    endLocation = null
                }
            },
            onEndNameChange = {
                endName = it
                endLocation = null
            },
            onStartSelected = {
                startName = it.name
                startLocation = it
                currentLocationTarget = if (it.id == "current-location") {
                    RequestedMapLocation(
                        latitude = it.coordinates.latitude,
                        longitude = it.coordinates.longitude,
                        requestId = System.nanoTime(),
                    )
                } else {
                    null
                }
                if (mode == SearchMode.NEARBY) {
                    endName = it.name
                    endLocation = it
                }
            },
            onEndSelected = {
                endName = it.name
                endLocation = it
            },
            onUseStartAsEnd = {
                endName = startName
                endLocation = startLocation
            },
            isChecking = locationChecking,
            onBack = { screen = AppScreen.HOME },
            onNext = {
                if (!locationChecking) {
                    locationChecking = true
                    appScope.launch {
                        try {
                            val resolvedStart = startLocation ?: api.searchPlace(
                                startName,
                                gangwonOnly = mode == SearchMode.NEARBY,
                            )
                            val resolvedEnd = if (mode == SearchMode.NEARBY) {
                                resolvedStart
                            } else {
                                endLocation ?: api.searchPlace(endName, gangwonOnly = true)
                            }
                            val gangwonLocation = if (mode == SearchMode.NEARBY) resolvedStart else resolvedEnd
                            if (!api.isGangwon(gangwonLocation.coordinates)) {
                                Toast.makeText(
                                    context,
                                    if (mode == SearchMode.NEARBY) {
                                        "현재 위치는 강원도 밖이라 근처 장소를 추천할 수 없어요."
                                    } else {
                                        "목적지가 강원도 밖이라 이동 중 들를 장소를 추천할 수 없어요."
                                    },
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                startName = resolvedStart.name
                                startLocation = resolvedStart
                                endName = resolvedEnd.name
                                endLocation = resolvedEnd
                                screen = AppScreen.TIME
                            }
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                error.message ?: "위치를 확인하지 못했어요.",
                                Toast.LENGTH_LONG,
                            ).show()
                        } finally {
                            locationChecking = false
                        }
                    }
                }
            },
        )

        AppScreen.TIME -> TimeScreen(
            mode = mode,
            deadline = deadline,
            buffer = buffer,
            transport = transport,
            onDeadlineChange = { deadline = it },
            onBufferChange = { buffer = it },
            onTransportChange = { transport = it },
            onBack = { screen = AppScreen.LOCATION },
            onNext = { screen = AppScreen.CONDITIONS },
        )

        AppScreen.CONDITIONS -> ConditionsScreen(
            mode = mode,
            categories = categories,
            excludeRestaurants = excludeRestaurants,
            onIntentSelected = { intent ->
                val (selectedCategories, excludeFood) = recommendationIntentFilters(intent)
                categories = selectedCategories
                excludeRestaurants = excludeFood
            },
            onBack = { screen = AppScreen.TIME },
            onSearch = { screen = AppScreen.LOADING },
        )

        AppScreen.LOADING -> LoadingScreen(
            onLoad = {
                val start = startLocation ?: api.searchPlace(
                    criteria.startName,
                    gangwonOnly = criteria.mode == SearchMode.NEARBY,
                )
                val end = if (criteria.mode == SearchMode.NEARBY) {
                    start
                } else {
                    endLocation ?: api.searchPlace(criteria.endName, gangwonOnly = true)
                }
                val resolvedCriteria = criteria.copy(
                    startName = start.name,
                    endName = end.name,
                    startCoordinates = start.coordinates,
                    endCoordinates = end.coordinates,
                )
                val result = api.recommendations(resolvedCriteria)
                recommendations = result.recommendations
                recommendationWarning = result.warning
                activeCriteria = resolvedCriteria
            },
            onFinished = { screen = AppScreen.RESULTS },
            onBack = { screen = AppScreen.CONDITIONS },
        )

        AppScreen.RESULTS -> ResultsScreen(
            criteria = activeCriteria ?: criteria,
            recommendations = recommendations,
            warning = recommendationWarning,
            onBack = { screen = AppScreen.CONDITIONS },
            onEdit = { screen = AppScreen.TIME },
            onWidenSearch = {
                deadline = extendedDeadlineMinutes(deadline)
                screen = AppScreen.LOADING
            },
            onClearConditions = {
                categories = emptySet()
                excludeRestaurants = false
                screen = AppScreen.LOADING
            },
            onSearchOtherPlace = { screen = AppScreen.LOCATION },
            onOpenRoute = { selectedRecommendations ->
                openKakaoMapMultiRoute(
                    context = context,
                    start = (activeCriteria ?: criteria).startCoordinates,
                    startName = (activeCriteria ?: criteria).startName,
                    waypoints = selectedRecommendations.mapNotNull { recommendation ->
                        recommendation.place.latitude?.let { latitude ->
                            recommendation.place.longitude?.let { longitude ->
                                recommendation.place.name to Coordinates(latitude, longitude)
                            }
                        }
                    },
                    destination = (activeCriteria ?: criteria).endCoordinates,
                    destinationName = (activeCriteria ?: criteria).endName,
                )
            },
            onSelect = {
                selected = it
                screen = AppScreen.DETAIL
            },
        )

        AppScreen.DETAIL -> selected?.let {
            DetailScreen(
                criteria = activeCriteria ?: criteria,
                recommendation = it,
                warning = recommendationWarning,
                isSaved = savedPlaces.any { entry -> entry.place.id == it.place.id },
                onToggleSave = {
                    updateSavedPlaces(if (savedPlaces.any { entry -> entry.place.id == it.place.id }) {
                        savedPlaces.filterNot { entry -> entry.place.id == it.place.id }
                    } else {
                        listOf(SavedPlaceEntry(it.place, System.currentTimeMillis())) + savedPlaces
                    })
                },
                onBack = { screen = AppScreen.RESULTS },
            )
        } ?: run {
            screen = AppScreen.RESULTS
        }
    }
}

@Composable
private fun HomeScreen(
    showIntro: Boolean,
    currentLocationTarget: RequestedMapLocation?,
    onCurrentLocationTargetChange: (RequestedMapLocation?) -> Unit,
    onDismissIntro: (Boolean) -> Unit,
    onStart: (Coordinates?) -> Unit,
    onTabSelected: (MainTab) -> Unit,
) {
    val context = LocalContext.current
    var isLocating by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    val locateCurrentPosition: () -> Unit = {
        isLocating = true
        requestCurrentLocation(
            context = context,
            onSuccess = { location ->
                onCurrentLocationTargetChange(
                    RequestedMapLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        requestId = System.nanoTime(),
                    ),
                )
                isLocating = false
            },
            onLocationDisabled = {
                isLocating = false
                showLocationSettingsDialog = true
            },
            onUnavailable = {
                isLocating = false
                Toast.makeText(
                    context,
                    "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            locateCurrentPosition()
        } else if (deniedLocationPermissionNeedsSettings(permissions) { permission ->
                (context as? android.app.Activity)
                    ?.shouldShowRequestPermissionRationale(permission) == true
            }
        ) {
            showPermissionSettingsDialog = true
        } else {
            Toast.makeText(
                context,
                "현재 위치를 사용하려면 위치 권한을 허용해 주세요.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigation(
                selectedTab = MainTab.EXPLORE,
                onTabSelected = onTabSelected,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MapBackground(
                modifier = Modifier.fillMaxSize(),
                requestedLocation = currentLocationTarget,
            )
            SearchBar(
                text = "장소 검색",
                onClick = {
                    onStart(
                        currentLocationTarget?.let {
                            Coordinates(it.latitude, it.longitude)
                        },
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(20.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 106.dp),
            ) {
                RoundMapButton(
                    onClick = if (isLocating) null else {
                        {
                            if (currentLocationTarget != null) {
                                onCurrentLocationTargetChange(null)
                            } else if (hasLocationPermission(context)) {
                                locateCurrentPosition()
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        }
                    },
                    foreground = if (currentLocationTarget != null) TteumRed else TteumInk,
                    border = if (currentLocationTarget != null) {
                        BorderStroke(1.5.dp, TteumRed)
                    } else {
                        null
                    },
                    icon = {
                        if (isLocating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = TteumRed,
                            )
                        } else {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "현재 위치",
                            )
                        }
                    },
                )
            }

            Button(
                onClick = {
                    onStart(
                        currentLocationTarget?.let {
                            Coordinates(it.latitude, it.longitude)
                        },
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(20.dp)
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("남는 시간으로 장소 찾기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showLocationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            title = { Text("위치 서비스를 켜 주세요") },
            text = { Text("현재 위치를 찾으려면 휴대폰의 위치 서비스를 켜야 합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocationSettingsDialog = false
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                ) {
                    Text("위치 설정 열기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettingsDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (showPermissionSettingsDialog) {
        LocationPermissionSettingsDialog(
            onDismiss = { showPermissionSettingsDialog = false },
            onOpenSettings = {
                showPermissionSettingsDialog = false
                openAppSettings(context)
            },
        )
    }

    if (showIntro) {
        HomeIntroDialog(onDismiss = onDismissIntro)
    }
}

@Composable
private fun HomeIntroDialog(onDismiss: (Boolean) -> Unit) {
    var hideForToday by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "강원도에서 남는 시간, 어디 갈까?",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "이동 시간과 머무는 시간을 계산해\n남는 시간에 다녀올 장소를 찾아드려요.",
                    color = TteumMuted,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.clickable { hideForToday = !hideForToday },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = hideForToday,
                        onCheckedChange = { hideForToday = it },
                    )
                    Text("오늘 하루 보지 않기", color = TteumMuted)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onDismiss(hideForToday) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("확인", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BottomNavigation(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = TteumRed.copy(alpha = 0.10f),
                spotColor = TteumRed.copy(alpha = 0.10f),
            ),
        color = Color.White,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            BottomNavItem(
                icon = Icons.Default.Explore,
                label = "장소 찾기",
                selected = selectedTab == MainTab.EXPLORE,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(MainTab.EXPLORE) },
            )
            BottomNavItem(
                icon = Icons.Default.FormatListBulleted,
                label = "장소 둘러보기",
                selected = selectedTab == MainTab.SAVED,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(MainTab.SAVED) },
            )
            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "설정",
                selected = selectedTab == MainTab.SETTINGS,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(MainTab.SETTINGS) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val color = if (selected) TteumInk else Color(0xFFA0A4AC)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = color,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPlacesScreen(
    catalogPlaces: List<PlaceCandidate>,
    savedPlaces: List<SavedPlaceEntry>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSave: (PlaceCandidate) -> Unit,
    onRestore: (SavedPlaceEntry) -> Unit,
    onTabSelected: (MainTab) -> Unit,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<PlaceCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SavedSort.RECENT) }
    var selectedPlace by remember { mutableStateOf<PlaceCandidate?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    selectedPlace?.let { place ->
        SavedPlaceDetailScreen(
            place = place,
            onBack = { selectedPlace = null },
        )
        return
    }

    val savedById = savedPlaces.associateBy { it.place.id }
    val visiblePlaces = catalogPlaces
        .asSequence()
        .filter { selectedCategory == null || it.category == selectedCategory }
        .filter { place ->
            query.isBlank() ||
                place.name.contains(query, ignoreCase = true) ||
                place.address.contains(query, ignoreCase = true)
        }
        .map { catalogPlace ->
            val savedEntry = savedById[catalogPlace.id]
            PlaceCardItem(
                place = if (savedEntry == null) {
                    catalogPlace
                } else {
                    catalogPlace.copy(tags = savedEntry.place.tags)
                },
                savedEntry = savedEntry,
            )
        }
        .let { entries ->
            when (sort) {
                SavedSort.RECENT -> entries.sortedWith(
                    compareByDescending<PlaceCardItem> { it.savedEntry?.savedAtMillis ?: Long.MIN_VALUE }
                        .thenBy { it.place.name },
                )
                SavedSort.NAME -> entries.sortedBy { it.place.name }
            }
        }
        .toList()
    val shouldLoadMore by remember(visiblePlaces.size, hasMore, isLoadingMore) {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && !isLoadingMore &&
                (visiblePlaces.isEmpty() || lastVisibleIndex >= visiblePlaces.lastIndex - 6)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FA),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigation(
                selectedTab = MainTab.SAVED,
                onTabSelected = onTabSelected,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 20.dp, bottom = 16.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("장소 검색") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("강원도 전체", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SavedFilterChip(
                            text = "전체",
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                        )
                    }
                    items(PlaceCategory.entries) { category ->
                        SavedFilterChip(
                            text = category.label,
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "현재 ${visiblePlaces.size}개 표시 중",
                        color = TteumMuted,
                        fontWeight = FontWeight.Bold,
                    )
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text("아래로 스크롤하면 장소를 계속 불러와요.")
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "목록 안내",
                                tint = TteumMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        sort = if (sort == SavedSort.RECENT) SavedSort.NAME else SavedSort.RECENT
                    },
                ) {
                    Text(
                        if (sort == SavedSort.RECENT) "저장 우선순⌄" else "이름순⌄",
                        color = TteumMuted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TteumRed)
                }
            } else if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(errorMessage, color = TteumMuted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry) { Text("다시 불러오기") }
                }
            } else if (visiblePlaces.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (catalogPlaces.isEmpty()) "등록된 장소가 아직 없어요." else "조건에 맞는 장소가 없어요.",
                        color = TteumMuted,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(visiblePlaces, key = { it.place.id }) { place ->
                        SavedPlaceCard(
                            place = place.place,
                            isSaved = place.savedEntry != null,
                            onClick = { selectedPlace = place.place },
                            onToggleSave = {
                                val removedEntry = place.savedEntry
                                onToggleSave(place.place)
                                if (removedEntry != null) {
                                    coroutineScope.launch {
                                        if (
                                            snackbarHostState.showSnackbar(
                                                message = "저장을 해제했어요.",
                                                actionLabel = "되돌리기",
                                            ) == SnackbarResult.ActionPerformed
                                        ) {
                                            onRestore(removedEntry)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabScreen(
    savedCount: Int,
    onClearSaved: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var locationPermissionGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    var kakaoMapAvailable by remember { mutableStateOf(isKakaoMapAvailable(context)) }
    var showSavedClearDialog by remember { mutableStateOf(false) }
    var showCacheClearDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationPermissionGranted = hasLocationPermission(context)
                kakaoMapAvailable = isKakaoMapAvailable(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color(0xFFF7F8FA),
        bottomBar = {
            BottomNavigation(
                selectedTab = MainTab.SETTINGS,
                onTabSelected = onTabSelected,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            item {
                Spacer(Modifier.height(24.dp))
                Text("설정", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                SettingsSectionTitle("앱 사용")
                SettingsGroup {
                    SettingsRow(
                        title = "위치 권한",
                        description = if (locationPermissionGranted) "허용됨" else "허용되지 않음",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "카카오맵",
                        description = if (kakaoMapAvailable) "설치됨 · 눌러서 실행" else "설치 필요",
                        onClick = {
                            if (kakaoMapAvailable) {
                                openKakaoMapHome(context)
                            } else {
                                openKakaoMapInstallPage(context)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))
                SettingsSectionTitle("저장 공간")
                SettingsGroup {
                    SettingsRow(
                        title = "캐시 지우기",
                        description = "임시 이미지와 지도 데이터를 정리해요.",
                        onClick = { showCacheClearDialog = true },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "저장한 장소 비우기",
                        description = "현재 ${savedCount}개 저장됨",
                        titleColor = if (savedCount > 0) TteumRed else TteumMuted,
                        onClick = if (savedCount > 0) {
                            { showSavedClearDialog = true }
                        } else {
                            null
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))
                SettingsSectionTitle("약관 및 지원")
                SettingsGroup {
                    SettingsRow(
                        title = "개인정보처리방침",
                        description = if (PRIVACY_POLICY_URL.isBlank()) "준비 중" else "보기",
                        onClick = { openPolicy(context, PRIVACY_POLICY_URL) },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "위치기반서비스 이용약관",
                        description = if (LOCATION_TERMS_URL.isBlank()) "준비 중" else "보기",
                        onClick = { openPolicy(context, LOCATION_TERMS_URL) },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "문의하기",
                        description = CONTACT_EMAIL,
                        onClick = { openContactEmail(context) },
                    )
                }

                Spacer(Modifier.height(24.dp))
                SettingsSectionTitle("앱 정보")
                SettingsGroup {
                    SettingsRow(
                        title = "앱 버전",
                        description = BuildConfig.VERSION_NAME,
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "데이터 출처",
                        description = "한국관광공사 TourAPI · 카카오맵",
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showSavedClearDialog) {
        AlertDialog(
            onDismissRequest = { showSavedClearDialog = false },
            title = { Text("저장 목록을 비울까요?") },
            text = { Text("이 기기에 저장한 장소가 모두 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearSaved()
                        showSavedClearDialog = false
                    },
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavedClearDialog = false }) {
                    Text("취소")
                }
            },
        )
    }

    if (showCacheClearDialog) {
        AlertDialog(
            onDismissRequest = { showCacheClearDialog = false },
            title = { Text("캐시를 지울까요?") },
            text = { Text("저장한 장소는 유지되고 임시 데이터만 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleared = clearAppCache(context)
                        showCacheClearDialog = false
                        Toast.makeText(
                            context,
                            if (cleared) "캐시를 정리했어요." else "캐시를 정리하지 못했어요.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text("지우기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCacheClearDialog = false }) {
                    Text("취소")
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        color = TteumMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    titleColor: Color = TteumInk,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(description, color = TteumMuted, fontSize = 13.sp)
        }
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFB7BAC1),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF0F1F3)),
    )
}

@Composable
private fun SavedPlaceDetailScreen(
    place: PlaceCandidate,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = { openKakaoMap(context, place.name) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("카카오맵 실행 및 안내", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Box {
                    SavedPlaceImage(
                        imageUrl = place.imageUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(14.dp)
                            .background(Color.White.copy(alpha = 0.92f), CircleShape),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Text(place.category.label, color = TteumRed, fontWeight = FontWeight.Bold)
                    Text(place.name, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    if (place.address.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(place.address, color = TteumMuted)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "추천 머무는 시간 ${formatMinutes(place.stayMinutes)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(place.tags) { tag ->
                            Surface(
                                color = Color(0xFFF1F2F4),
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    color = Color(0xFF55585F),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) TteumInk else Color(0xFFF4F5F7),
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE1E3E8)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
            color = if (selected) Color.White else Color(0xFF596170),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SavedPlaceCard(
    place: PlaceCandidate,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val (visibleTags, hiddenTagCount) = compactTags(place.tags)
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column {
            Box {
                SavedPlaceImage(
                    imageUrl = place.imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp),
                )
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.92f), CircleShape),
                ) {
                    Icon(
                        if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isSaved) "저장 해제" else "저장",
                        tint = if (isSaved) TteumRed else Color(0xFFB8BBC1),
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    place.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 155.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(place.category.label, color = Color(0xFF55585F), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "추천 머무는 시간 ${formatMinutes(place.stayMinutes)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                val compactItems = visibleTags + if (hiddenTagCount > 0) listOf("+ $hiddenTagCount") else emptyList()
                compactItems.chunked(2).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTags.forEach { tag ->
                            Surface(
                                color = Color(0xFFF1F2F4),
                                shape = RoundedCornerShape(3.dp),
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    color = Color(0xFF55585F),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun SavedPlaceImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        imageUrl,
    ) {
        value = if (imageUrl.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                savedImageCache.get(imageUrl)?.asImageBitmap() ?: runCatching {
                    val connection = URL(imageUrl).openConnection().apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                    }
                    connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                        ?.also { savedImageCache.put(imageUrl, it) }
                        ?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    if (bitmap == null) {
        Box(
            modifier = modifier.background(TteumRedSoft),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = TteumRed,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text("틈새", color = TteumRed, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

internal fun compactTags(
    tags: List<String>,
    characterBudget: Int = 18,
): Pair<List<String>, Int> {
    var used = 0
    val visible = tags.takeWhile { tag ->
        val fits = used + tag.length + 1 <= characterBudget
        if (fits) used += tag.length + 1
        fits
    }
    return visible to (tags.size - visible.size)
}

internal fun recommendationCategories(
    selected: Set<PlaceCategory>,
    excludeRestaurants: Boolean,
): Set<PlaceCategory> {
    if (!excludeRestaurants) return selected
    return (selected.ifEmpty { PlaceCategory.entries.toSet() } - PlaceCategory.RESTAURANT)
        .ifEmpty { PlaceCategory.entries.toSet() - PlaceCategory.RESTAURANT }
}

internal fun recommendationIntentFilters(
    intent: RecommendationIntent,
): Pair<Set<PlaceCategory>, Boolean> = when (intent) {
    RecommendationIntent.ANY -> emptySet<PlaceCategory>() to false
    RecommendationIntent.MEAL -> setOf(PlaceCategory.RESTAURANT) to false
    RecommendationIntent.CAFE -> setOf(PlaceCategory.CAFE) to false
    RecommendationIntent.WALK_TOUR -> setOf(PlaceCategory.ATTRACTION, PlaceCategory.LEISURE) to false
    RecommendationIntent.INDOOR -> setOf(PlaceCategory.CULTURE, PlaceCategory.SHOPPING) to false
    RecommendationIntent.NO_FOOD -> emptySet<PlaceCategory>() to true
}

internal fun selectedRecommendationIntent(
    categories: Set<PlaceCategory>,
    excludeRestaurants: Boolean,
): RecommendationIntent = when {
    excludeRestaurants -> RecommendationIntent.NO_FOOD
    categories == setOf(PlaceCategory.RESTAURANT) -> RecommendationIntent.MEAL
    categories == setOf(PlaceCategory.CAFE) -> RecommendationIntent.CAFE
    categories == setOf(PlaceCategory.ATTRACTION, PlaceCategory.LEISURE) -> RecommendationIntent.WALK_TOUR
    categories == setOf(PlaceCategory.CULTURE, PlaceCategory.SHOPPING) -> RecommendationIntent.INDOOR
    else -> RecommendationIntent.ANY
}

private fun formatMinutes(minutes: Int): String =
    when {
        minutes < 60 -> "${minutes}분"
        minutes % 60 == 0 -> "${minutes / 60}시간"
        else -> "${minutes / 60}시간 ${minutes % 60}분"
    }

private fun PlaceCategory.activityLabel(): String = when (this) {
    PlaceCategory.ATTRACTION -> "관광·구경"
    PlaceCategory.RESTAURANT -> "식사"
    PlaceCategory.CAFE -> "카페·휴식"
    PlaceCategory.CULTURE -> "전시·문화"
    PlaceCategory.FESTIVAL -> "축제·행사"
    PlaceCategory.SHOPPING -> "쇼핑"
    PlaceCategory.LEISURE -> "체험·레포츠"
}

private fun recommendationConditionSummary(categories: Set<PlaceCategory>): String? = when {
    categories.isEmpty() -> null
    categories.size == PlaceCategory.entries.size - 1 &&
        PlaceCategory.RESTAURANT !in categories -> "음식점 제외"
    else -> categories.joinToString(" · ") { it.activityLabel() }
}

private const val SAVED_PLACES_PREFERENCES = "saved_places"
private const val SAVED_PLACES_KEY = "entries"

private fun loadSavedPlaces(context: Context): List<SavedPlaceEntry> {
    val json = context
        .getSharedPreferences(SAVED_PLACES_PREFERENCES, Context.MODE_PRIVATE)
        .getString(SAVED_PLACES_KEY, null)
        ?: return emptyList()

    return runCatching {
        val entries = JSONArray(json)
        buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val tags = item.getJSONArray("tags")
                add(
                    SavedPlaceEntry(
                        place = PlaceCandidate(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            category = PlaceCategory.valueOf(item.getString("category")),
                            stayMinutes = item.getInt("stayMinutes"),
                            firstLegMinutes = item.getInt("firstLegMinutes"),
                            secondLegMinutes = item.getInt("secondLegMinutes"),
                            detourMinutes = item.getInt("detourMinutes"),
                            reason = item.optString("reason"),
                            tags = buildList {
                                for (tagIndex in 0 until tags.length()) {
                                    add(tags.getString(tagIndex))
                                }
                            },
                            address = item.optString("address"),
                            imageUrl = item.optString("imageUrl"),
                            latitude = item.optDouble("latitude").takeUnless { it.isNaN() },
                            longitude = item.optDouble("longitude").takeUnless { it.isNaN() },
                            isOpen = item.optBoolean("isOpen", true),
                        ),
                        savedAtMillis = item.getLong("savedAtMillis"),
                    ),
                )
            }
        }.sortedByDescending { it.savedAtMillis }
    }.getOrDefault(emptyList())
}

private fun storeSavedPlaces(
    context: Context,
    entries: List<SavedPlaceEntry>,
) {
    val json = JSONArray().apply {
        entries.forEach { entry ->
            put(
                JSONObject()
                    .put("id", entry.place.id)
                    .put("name", entry.place.name)
                    .put("category", entry.place.category.name)
                    .put("stayMinutes", entry.place.stayMinutes)
                    .put("firstLegMinutes", entry.place.firstLegMinutes)
                    .put("secondLegMinutes", entry.place.secondLegMinutes)
                    .put("detourMinutes", entry.place.detourMinutes)
                    .put("reason", entry.place.reason)
                    .put("tags", JSONArray(entry.place.tags))
                    .put("address", entry.place.address)
                    .put("imageUrl", entry.place.imageUrl)
                    .put("latitude", entry.place.latitude ?: JSONObject.NULL)
                    .put("longitude", entry.place.longitude ?: JSONObject.NULL)
                    .put("isOpen", entry.place.isOpen)
                    .put("savedAtMillis", entry.savedAtMillis),
            )
        }
    }

    context
        .getSharedPreferences(SAVED_PLACES_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(SAVED_PLACES_KEY, json.toString())
        .apply()
}

@Composable
private fun LocationScreen(
    mode: SearchMode,
    startName: String,
    endName: String,
    startLocation: LocationSearchResult?,
    endLocation: LocationSearchResult?,
    searchPlaces: suspend (String, Boolean) -> List<LocationSearchResult>,
    resolveCurrentAddress: suspend (Coordinates) -> String,
    onModeChange: (SearchMode) -> Unit,
    onStartNameChange: (String) -> Unit,
    onEndNameChange: (String) -> Unit,
    onStartSelected: (LocationSearchResult) -> Unit,
    onEndSelected: (LocationSearchResult) -> Unit,
    onUseStartAsEnd: () -> Unit,
    isChecking: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    var isLocating by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    val locateCurrentPosition: () -> Unit = {
        isLocating = true
        requestCurrentLocation(
            context = context,
            onSuccess = { location ->
                onStartSelected(
                    LocationSearchResult(
                        id = "current-location",
                        name = "현재 위치",
                        address = "GPS로 확인한 위치",
                        coordinates = Coordinates(location.latitude, location.longitude),
                    ),
                )
                isLocating = false
            },
            onLocationDisabled = {
                isLocating = false
                showLocationSettingsDialog = true
            },
            onUnavailable = {
                isLocating = false
                Toast.makeText(
                    context,
                    "현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            locateCurrentPosition()
        } else if (deniedLocationPermissionNeedsSettings(permissions) { permission ->
                (context as? android.app.Activity)
                    ?.shouldShowRequestPermissionRationale(permission) == true
            }
        ) {
            showPermissionSettingsDialog = true
        } else {
            Toast.makeText(
                context,
                "현재 위치를 사용하려면 위치 권한을 허용해 주세요.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val useCurrentLocation = {
        if (hasLocationPermission(context)) {
            locateCurrentPosition()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(startName, startLocation?.id) {
        if (shouldAutoLocateStart(startName, startLocation != null)) {
            useCurrentLocation()
        }
    }

    LaunchedEffect(startLocation?.id, startLocation?.name, startLocation?.coordinates) {
        val location = startLocation
        if (location?.id == "current-location" && location.name == "현재 위치") {
            val address = try {
                resolveCurrentAddress(location.coordinates)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            }
            if (!address.isNullOrBlank()) {
                onStartSelected(location.copy(name = address, address = address))
            }
        }
    }

    MapSheetScreen(
        onBack = onBack,
        mapCoordinates = startLocation?.coordinates,
        bottomContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(0.32f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF3F4F6),
                        contentColor = TteumInk,
                    ),
                    border = null,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("이전", fontSize = 17.sp)
                }
                Button(
                    onClick = onNext,
                    enabled = startName.isNotBlank() &&
                        endName.isNotBlank() &&
                        !(startName == "현재 위치" && startLocation == null) &&
                        !isChecking &&
                        !isLocating,
                    modifier = Modifier
                        .weight(0.68f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (isChecking) "위치 확인 중..." else "다음",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) {
        ModeSelector(mode = mode, onModeChange = onModeChange)
        Spacer(Modifier.height(30.dp))
        Text(
            if (mode == SearchMode.ON_THE_WAY) {
                "지금 어디로 가는 길인가요?"
            } else {
                "어디에서 출발하고,\n어디로 돌아갈까요?"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (mode == SearchMode.ON_THE_WAY) {
                "목적지로 가는 길에 잠깐 들를 곳을 찾아요."
            } else {
                "주변을 둘러본 뒤 원하는 장소에 시간 맞춰 도착해요."
            },
            color = TteumMuted,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            color = Color(0xFFF3F4F6),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column {
                LocationSearchField(
                    value = startName,
                    onValueChange = onStartNameChange,
                    selected = startLocation,
                    label = if (mode == SearchMode.ON_THE_WAY) "출발지" else "시작",
                    labelBackground = Color(0xFF9EA3AB),
                    searchPlaces = searchPlaces,
                    gangwonOnly = mode == SearchMode.NEARBY,
                    onSelected = onStartSelected,
                    onUseCurrentLocation = useCurrentLocation,
                    isLocating = isLocating,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color(0xFFDDE0E5),
                )
                LocationSearchField(
                    value = endName,
                    onValueChange = onEndNameChange,
                    selected = endLocation,
                    label = if (mode == SearchMode.ON_THE_WAY) "목적지" else "돌아올 곳",
                    labelBackground = TteumInk,
                    searchPlaces = searchPlaces,
                    gangwonOnly = true,
                    onSelected = onEndSelected,
                )
            }
        }
        if (mode == SearchMode.NEARBY) {
            TextButton(onClick = onUseStartAsEnd) {
                Text("시작 위치로 돌아오기")
            }
        }
    }

    if (showLocationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            title = { Text("위치 서비스를 켜 주세요") },
            text = { Text("현재 위치를 자동으로 설정하려면 휴대폰의 위치 서비스가 필요해요.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocationSettingsDialog = false
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                ) {
                    Text("위치 설정 열기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationSettingsDialog = false }) {
                    Text("취소")
                }
            },
        )
    }


    if (showPermissionSettingsDialog) {
        LocationPermissionSettingsDialog(
            onDismiss = { showPermissionSettingsDialog = false },
            onOpenSettings = {
                showPermissionSettingsDialog = false
                openAppSettings(context)
            },
        )
    }
}

@Composable
private fun LocationPermissionSettingsDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("위치 권한을 켜 주세요") },
        text = {
            Text("위치 권한을 다시 묻지 않도록 설정했어요. 앱 설정에서 위치 권한을 허용해 주세요.")
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("앱 설정 열기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun LocationSearchField(
    value: String,
    selected: LocationSearchResult?,
    label: String,
    searchPlaces: suspend (String, Boolean) -> List<LocationSearchResult>,
    gangwonOnly: Boolean,
    onValueChange: (String) -> Unit,
    onSelected: (LocationSearchResult) -> Unit,
    labelBackground: Color,
    onUseCurrentLocation: (() -> Unit)? = null,
    isLocating: Boolean = false,
) {
    var results by remember { mutableStateOf(emptyList<LocationSearchResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var searchFailed by remember { mutableStateOf(false) }
    var searchAttempt by remember { mutableStateOf(0) }
    var focusAfterClear by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isCurrentLocation = selected?.id == "current-location"

    LaunchedEffect(selected) {
        if (selected == null && focusAfterClear) {
            focusRequester.requestFocus()
            focusAfterClear = false
        }
    }

    LaunchedEffect(value, selected?.id, searchAttempt) {
        results = emptyList()
        isLoading = false
        searchMessage = null
        searchFailed = false
        if (value.length < 2 || selected?.name == value || value == "현재 위치") return@LaunchedEffect
        delay(350)
        isLoading = true
        try {
            results = searchPlaces(value, gangwonOnly)
            if (results.isEmpty()) searchMessage = "검색 결과가 없어요. 장소명을 더 자세히 입력해 주세요."
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            searchFailed = true
            searchMessage = networkFailureMessage("장소 검색", error.message)
        } finally {
            isLoading = false
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = labelBackground,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (selected != null) {
                    Text(
                        value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                focusAfterClear = true
                                onValueChange("")
                            },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (value.isBlank()) {
                    Text("장소 검색", color = TteumMuted)
                }
                if (selected == null) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = TteumInk,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
            if (isLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            if (onUseCurrentLocation != null) {
                Spacer(Modifier.width(10.dp))
                Surface(
                    modifier = Modifier.clickable(
                        enabled = !isLocating,
                        onClick = {
                            if (isCurrentLocation) onValueChange("") else onUseCurrentLocation()
                        },
                    ),
                    color = if (isCurrentLocation) TteumRed else TteumRedSoft,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .heightIn(min = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isLocating) "확인 중" else "현위치",
                            color = if (isCurrentLocation) Color.White else TteumRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (results.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White),
            ) {
                results.take(5).forEach { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(result) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(result.name, fontWeight = FontWeight.Bold)
                        if (result.address.isNotBlank()) {
                            Text(
                                result.address,
                                color = TteumMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else if (searchMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    searchMessage.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = TteumMuted,
                    fontSize = 12.sp,
                )
                if (searchFailed) {
                    TextButton(onClick = { searchAttempt += 1 }) { Text("다시 시도") }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        ModeTab(
            label = "경로 따라 갈 장소",
            icon = Icons.Default.DirectionsCar,
            selected = mode == SearchMode.ON_THE_WAY,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(SearchMode.ON_THE_WAY) },
        )
        ModeTab(
            label = "근처에서 갈 장소",
            icon = Icons.Default.DirectionsWalk,
            selected = mode == SearchMode.NEARBY,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(SearchMode.NEARBY) },
        )
    }
}

@Composable
private fun ModeTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) Color.White else Color.Transparent,
        shape = RoundedCornerShape(11.dp),
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) TteumInk else TteumMuted)
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (selected) TteumInk else TteumMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeScreen(
    mode: SearchMode,
    deadline: Int,
    buffer: Int,
    transport: TransportMode,
    onDeadlineChange: (Int) -> Unit,
    onBufferChange: (Int) -> Unit,
    onTransportChange: (TransportMode) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var deadlineText by rememberSaveable(deadline) {
        mutableStateOf(deadline.toString())
    }
    var isAdjustingTime by remember { mutableStateOf(false) }
    val typedDeadline = deadlineText.toIntOrNull()
    val isDeadlineValid = typedDeadline != null &&
        typedDeadline in MIN_DEADLINE_MINUTES..MAX_DEADLINE_MINUTES

    MapSheetScreen(
        onBack = onBack,
        bottomContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(0.32f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF3F4F6),
                        contentColor = TteumInk,
                    ),
                ) {
                    Text("이전", fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(0.68f)) {
                    PrimaryButton(text = "다음", enabled = isDeadlineValid, onClick = onNext)
                }
            }
        },
    ) {
        Text(
            if (mode == SearchMode.ON_THE_WAY) {
                "목적지 도착까지 얼마나 시간이 남았나요?"
            } else {
                "돌아오기까지 얼마나 시간이 남았나요?"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(26.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val ticks = listOf(15 to "15분", 60 to "1시간", 120 to "2시간", 240 to "4시간", 360 to "6시간")
            ticks.forEachIndexed { index, (minutes, label) ->
                val markerModifier = when (index) {
                    0 -> Modifier.align(Alignment.CenterStart)
                    ticks.lastIndex -> Modifier.align(Alignment.CenterEnd)
                    else -> Modifier
                        .offset(
                            x = maxWidth * (
                                (minutes - MIN_DEADLINE_MINUTES).toFloat() /
                                    (SLIDER_MAX_DEADLINE_MINUTES - MIN_DEADLINE_MINUTES)
                                ),
                        )
                        .width(0.dp)
                }
                Box(modifier = markerModifier, contentAlignment = Alignment.Center) {
                    Text(label, modifier = Modifier.wrapContentSize(unbounded = true))
                }
            }
        }
        Spacer(Modifier.height(38.dp))
        Slider(
            value = deadline.coerceIn(MIN_DEADLINE_MINUTES, SLIDER_MAX_DEADLINE_MINUTES).toFloat(),
            onValueChange = { value ->
                isAdjustingTime = true
                onDeadlineChange(
                    ((value / 15f).roundToInt() * 15)
                        .coerceIn(MIN_DEADLINE_MINUTES, SLIDER_MAX_DEADLINE_MINUTES),
                )
            },
            onValueChangeFinished = { isAdjustingTime = false },
            valueRange = MIN_DEADLINE_MINUTES.toFloat()..SLIDER_MAX_DEADLINE_MINUTES.toFloat(),
            steps = 22,
            modifier = Modifier.fillMaxWidth(),
            thumb = {
                TimeSliderThumb(
                    value = deadline,
                    showValue = isAdjustingTime,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = deadlineText,
            onValueChange = { input ->
                if (input.length <= 4 && input.all(Char::isDigit)) {
                    deadlineText = input
                    input.toIntOrNull()
                        ?.takeIf { it in MIN_DEADLINE_MINUTES..MAX_DEADLINE_MINUTES }
                        ?.let(onDeadlineChange)
                }
            },
            label = { Text("남은 시간") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text("분") },
            isError = deadlineText.isNotEmpty() && !isDeadlineValid,
            supportingText = if (deadlineText.isEmpty() || isDeadlineValid) {
                null
            } else {
                { Text("15분에서 24시간(1440분) 사이로 입력해 주세요.") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("이동수단")
        if (mode == SearchMode.ON_THE_WAY) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F2F5), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = TteumInk)
                Spacer(Modifier.width(10.dp))
                Text(
                    "가는 길에 들를 장소는 자동차 경로를 기준으로 계산해요.",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectButton(
                    text = "도보",
                    icon = Icons.Default.DirectionsWalk,
                    selected = transport == TransportMode.WALK,
                    modifier = Modifier.weight(1f),
                    onClick = { onTransportChange(TransportMode.WALK) },
                )
                SelectButton(
                    text = "차량",
                    icon = Icons.Default.DirectionsCar,
                    selected = transport == TransportMode.CAR,
                    modifier = Modifier.weight(1f),
                    onClick = { onTransportChange(TransportMode.CAR) },
                )
            }
        }

        SectionTitle("늦지 않기 위한 여유")
        ChoiceRow(
            choices = listOf(10 to "10분", 15 to "15분", 20 to "20분", 30 to "30분"),
            selected = buffer,
            onSelect = onBufferChange,
        )
        Text(
            "추천 장소에서 사용할 수 있는 시간은 최대 ${(deadline - buffer).coerceAtLeast(0)}분이에요.",
            modifier = Modifier
                .fillMaxWidth()
                .background(TteumRedSoft, RoundedCornerShape(12.dp))
                .padding(14.dp),
            color = TteumRed,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun TimeSliderThumb(
    value: Int,
    showValue: Boolean,
) {
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (showValue) {
            Box(
                modifier = Modifier
                    .requiredSize(44.dp)
                    .background(TteumRed.copy(alpha = 0.14f), CircleShape),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-47).dp)
                    .wrapContentSize(unbounded = true),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = TteumRed,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "${value}분",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Canvas(Modifier.width(10.dp).height(6.dp)) {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        },
                        color = TteumRed,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(TteumRed, CircleShape),
        )
    }
}

@Composable
private fun ConditionsScreen(
    mode: SearchMode,
    categories: Set<PlaceCategory>,
    excludeRestaurants: Boolean,
    onIntentSelected: (RecommendationIntent) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
) {
    MapSheetScreen(
        onBack = onBack,
        bottomContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(0.32f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF3F4F6),
                        contentColor = TteumInk,
                    ),
                ) {
                    Text("이전", fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(0.68f)) {
                    PrimaryButton(text = "장소 추천받기", onClick = onSearch)
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F2F5), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (mode == SearchMode.ON_THE_WAY) Icons.Default.DirectionsCar else Icons.Default.DirectionsWalk,
                contentDescription = null,
                tint = TteumInk,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("탐색 방식", color = TteumMuted, fontSize = 12.sp)
                Text(
                    if (mode == SearchMode.ON_THE_WAY) "경로 따라 갈 장소" else "근처에서 갈 장소",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "지금 무엇이 끌리나요?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "하나만 골라주시면 어울리는 장소를 먼저 보여드려요.",
            color = TteumMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        val selectedIntent = selectedRecommendationIntent(categories, excludeRestaurants)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RecommendationIntent.entries) { intent ->
                FilterChip(
                    selected = intent == selectedIntent,
                    onClick = { onIntentSelected(intent) },
                    label = { Text(intent.label) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoadingScreen(
    onLoad: suspend () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attempt by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        errorMessage = null
        try {
            onLoad()
            delay(500)
            onFinished()
        } catch (error: Exception) {
            errorMessage = networkFailureMessage("장소 추천", error.message)
        }
    }
    Box(Modifier.fillMaxSize()) {
        MapBackground(Modifier.fillMaxSize())
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(28.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = TteumRedSoft,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TteumRed,
                        modifier = Modifier.padding(18.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (errorMessage == null) {
                    Text(
                        "남은 시간과 여유시간을 반영해\n방문할 장소를 찾고 있어요.",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    listOf("출발지와 목적지 확인", "실시간 이동 시간 계산", "갈 만한 장소 확인", "도착 전 여유 시간 적용").forEach {
                        Text(
                            "✓  $it",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            color = TteumMuted,
                        )
                    }
                } else {
                    Text(
                        "장소를 추천하지 못했어요.",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(errorMessage.orEmpty(), color = TteumMuted)
                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(text = "다시 시도", onClick = { attempt += 1 })
                    TextButton(onClick = onBack) {
                        Text("조건으로 돌아가기")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsScreen(
    criteria: SearchCriteria,
    recommendations: List<SafeRecommendation>,
    warning: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onWidenSearch: () -> Unit,
    onClearConditions: () -> Unit,
    onSearchOtherPlace: () -> Unit,
    onOpenRoute: (List<SafeRecommendation>) -> Unit,
    onSelect: (SafeRecommendation) -> Unit,
) {
    val context = LocalContext.current
    var selectedIds by rememberSaveable(criteria.startName, criteria.endName) {
        mutableStateOf(emptyList<String>())
    }
    val recommendationsById = recommendations.associateBy { it.place.id }
    val selectedRecommendations = selectedIds.mapNotNull(recommendationsById::get)
    val (selectedTotalMinutes, selectedMarginMinutes) = selectedRouteEstimate(
        criteria.deadlineMinutesFromNow,
        selectedRecommendations,
    )
    val toggleSelection: (SafeRecommendation) -> Unit = { recommendation ->
        if (recommendation.place.id in selectedIds) {
            selectedIds = selectedIds - recommendation.place.id
        } else if (selectedIds.size >= MAX_KAKAO_WAYPOINTS) {
            Toast.makeText(context, "경유지는 최대 5곳까지 선택할 수 있어요.", Toast.LENGTH_SHORT).show()
        } else {
            val updatedIds = selectedIds + recommendation.place.id
            val orderedIds = if (criteria.startCoordinates != null && criteria.endCoordinates != null) {
                orderWaypointIdsAlongRoute(
                    criteria.startCoordinates,
                    criteria.endCoordinates,
                    updatedIds.mapNotNull { id ->
                        recommendationsById[id]?.place?.let { place ->
                            place.latitude?.let { latitude ->
                                place.longitude?.let { longitude -> id to Coordinates(latitude, longitude) }
                            }
                        }
                    },
                )
            } else {
                updatedIds
            }
            val (_, remainingMinutes) = selectedRouteEstimate(
                criteria.deadlineMinutesFromNow,
                orderedIds.mapNotNull(recommendationsById::get),
            )
            if (remainingMinutes < criteria.safetyBufferMinutes) {
                Toast.makeText(
                    context,
                    "이 장소를 추가하면 도착 전 여유시간이 부족해요.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                selectedIds = orderedIds
            }
        }
    }
    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${criteria.startName} → ${criteria.endName}", fontWeight = FontWeight.Bold)
                        Text("${formatMinutes(criteria.deadlineMinutesFromNow)} 내 · 도착 전 여유 ${criteria.safetyBufferMinutes}분", color = TteumMuted, fontSize = 13.sp)
                    }
                    TextButton(onClick = onEdit) { Text("조건 수정") }
                }
            }
        },
        bottomBar = {
            if (recommendations.isNotEmpty()) {
                Surface(color = Color.White, shadowElevation = 10.dp) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "경유지 ${selectedIds.size}/$MAX_KAKAO_WAYPOINTS 선택" +
                                if (selectedIds.isEmpty()) "" else " · 약 ${selectedTotalMinutes}분 · ${selectedMarginMinutes}분 여유",
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(
                            text = if (selectedIds.isEmpty()) "지도에서 장소를 선택해 주세요" else "선택한 경유지로 안내",
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { onOpenRoute(selectedRecommendations) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RouteMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    criteria = criteria,
                    recommendation = recommendations.minByOrNull { it.place.detourMinutes },
                    candidates = recommendations,
                    selectedIds = selectedIds,
                    onCandidateClick = { id -> recommendationsById[id]?.let(toggleSelection) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 18.dp)) {
                    Text(
                        if (recommendations.isEmpty()) "조건에 맞는 예상 장소가 없어요" else "경로 주변에서 장소를 선택해 보세요",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (recommendations.isEmpty()) {
                            "도착 시간을 늦추거나 조건을 줄여 다시 찾아보세요."
                        } else {
                            "붉은 영역 안의 핀이나 카드를 눌러 최대 5곳까지 추가할 수 있어요."
                        },
                        color = TteumMuted,
                    )
                    if (warning.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            warning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF6E7), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            color = Color(0xFF765A22),
                            fontSize = 13.sp,
                        )
                    }
                    recommendationConditionSummary(criteria.categories)?.let { summary ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "반영한 선택: $summary",
                            color = TteumRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (recommendations.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PrimaryButton(
                            text = if (criteria.mode == SearchMode.NEARBY) {
                                "주변 반경 넓혀 다시 찾기"
                            } else {
                                "여유시간 30분 늘려 다시 찾기"
                            },
                            onClick = onWidenSearch,
                        )
                        if (criteria.categories.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearConditions,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("조건 일부 해제하기")
                            }
                        }
                        OutlinedButton(
                            onClick = onSearchOtherPlace,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("다른 장소 검색하기")
                        }
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("시간과 조건 직접 수정하기")
                        }
                    }
                }
            } else {
                itemsIndexed(recommendations, key = { _, item -> item.place.id }) { index, recommendation ->
                    RecommendationCard(
                        recommendation = recommendation,
                        position = index + 1,
                        total = recommendations.size,
                        selectedOrder = selectedIds.indexOf(recommendation.place.id)
                            .takeIf { it >= 0 }
                            ?.plus(1),
                        modifier = Modifier.padding(horizontal = 18.dp),
                        onToggle = { toggleSelection(recommendation) },
                        onClick = { onSelect(recommendation) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: SafeRecommendation,
    position: Int,
    total: Int,
    selectedOrder: Int?,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = selectedOrder?.let { BorderStroke(2.dp, TteumRed) },
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${position}번 후보 [$position/$total]",
                    modifier = Modifier.weight(1f),
                    color = TteumMuted,
                    fontSize = 13.sp,
                )
                OutlinedButton(onClick = onToggle) {
                    Text(selectedOrder?.let { "경유지 $it" } ?: "추가")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(recommendation.place.category.label, color = TteumRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(recommendation.place.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                SafetyBadge(recommendation)
            }
            Spacer(Modifier.height(12.dp))
            Text(recommendation.place.reason, color = TteumMuted)
            Spacer(Modifier.height(8.dp))
            OperationStatusText(recommendation)
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("머무는 시간", "${recommendation.place.stayMinutes}분")
                Metric("전체 예상", "${recommendation.totalMinutes}분")
                Metric("남는 시간", "${recommendation.marginMinutes}분", TteumRed)
            }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(recommendation.place.tags) { tag ->
                    Surface(color = Color(0xFFF1F2F5), shape = RoundedCornerShape(50)) {
                        Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClick) {
                    Text("상세 보기", color = TteumRed, fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TteumRed)
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    criteria: SearchCriteria,
    recommendation: SafeRecommendation,
    warning: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        if (criteria.mode == SearchMode.ON_THE_WAY) {
                            openKakaoMapRoute(
                                context = context,
                                start = criteria.startCoordinates,
                                startName = criteria.startName,
                                waypoint = recommendation.place.latitude?.let { latitude ->
                                    recommendation.place.longitude?.let { longitude ->
                                        Coordinates(latitude, longitude)
                                    }
                                },
                                destination = criteria.endCoordinates,
                                destinationName = criteria.endName,
                                transport = TransportMode.CAR,
                            )
                        } else {
                            openKakaoMapRoute(
                                context = context,
                                start = criteria.startCoordinates,
                                startName = criteria.startName,
                                waypoint = null,
                                destination = recommendation.place.latitude?.let { latitude ->
                                    recommendation.place.longitude?.let { longitude ->
                                        Coordinates(latitude, longitude)
                                    }
                                },
                                destinationName = recommendation.place.name,
                                transport = criteria.transportMode,
                            )
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (criteria.mode == SearchMode.ON_THE_WAY) {
                            "카카오맵으로 들르는 길 안내"
                        } else {
                            "카카오맵으로 길 안내"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Box {
                    RouteMap(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        criteria = criteria,
                        recommendation = recommendation,
                    )
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(14.dp)
                            .background(Color.White, CircleShape),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            }
            item {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(recommendation.place.category.label, color = TteumRed, fontWeight = FontWeight.Bold)
                            Text(recommendation.place.name, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onToggleSave) {
                            Icon(
                                if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "저장 해제" else "저장",
                                tint = if (isSaved) TteumRed else TteumMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(recommendation.place.reason, color = TteumMuted)
                    if (warning.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            warning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF6E7), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            color = Color(0xFF765A22),
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    SafetySummary(recommendation)
                    SectionTitle("시간 예상")
                    TimelineRow(criteria.startName, "${recommendation.place.firstLegMinutes}분 이동")
                    TimelineRow(recommendation.place.name, "${recommendation.place.stayMinutes}분 머물기", highlighted = true)
                    TimelineRow(criteria.endName, "${recommendation.place.secondLegMinutes}분 이동")
                    SectionTitle("장소 정보")
                    OperationStatusText(recommendation)
                    recommendation.place.openingHours.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text("운영시간  $it", color = TteumMuted, fontSize = 14.sp)
                    }
                    recommendation.place.closedDays.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text("휴무일  $it", color = TteumMuted, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(recommendation.place.tags.joinToString(" · "), color = TteumMuted)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "이동시간과 영업 정보는 실시간 상황에 따라 달라질 수 있어요. 출발 전 최신 정보를 확인해 주세요.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF6E7), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        color = Color(0xFF765A22),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

@Composable
private fun OperationStatusText(recommendation: SafeRecommendation) {
    Text(
        text = if (recommendation.operationStatus == OperationStatus.OPEN) {
            "도착 예상 시간에 운영 중"
        } else {
            "운영시간 확인 필요"
        },
        color = if (recommendation.operationStatus == OperationStatus.OPEN) TteumInk else TteumRed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
}

@Composable
private fun SafetySummary(
    recommendation: SafeRecommendation,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = TteumRedSoft),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                "여유시간 반영 예상 · ${recommendation.marginMinutes}분 남아요",
                color = TteumRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "이동과 머무는 시간은 총 ${recommendation.totalMinutes}분으로 예상돼요.",
                color = TteumInk,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    title: String,
    detail: String,
    highlighted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(if (highlighted) TteumRed else Color(0xFFA9ADB5), CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium)
        Text(detail, color = if (highlighted) TteumRed else TteumMuted)
    }
}

@Composable
private fun SafetyBadge(recommendation: SafeRecommendation) {
    val background = when (recommendation.safetyLevel) {
        SafetyLevel.COMFORTABLE -> Color(0xFFE8F7EE)
        SafetyLevel.AVAILABLE -> Color(0xFFFFF6E0)
        SafetyLevel.TIGHT -> TteumRedSoft
    }
    val foreground = when (recommendation.safetyLevel) {
        SafetyLevel.COMFORTABLE -> Color(0xFF197342)
        SafetyLevel.AVAILABLE -> Color(0xFF8A5C00)
        SafetyLevel.TIGHT -> TteumRed
    }
    Surface(color = background, shape = RoundedCornerShape(50)) {
        Text(
            "${recommendation.safetyLevel.label} ${recommendation.marginMinutes}분",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, valueColor: Color = TteumInk) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TteumMuted, fontSize = 12.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapSheetScreen(
    onBack: () -> Unit,
    mapCoordinates: Coordinates? = null,
    bottomContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetState = rememberStandardBottomSheetState(skipHiddenState = false)
        val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

        LaunchedEffect(sheetState.currentValue) {
            if (sheetState.currentValue == SheetValue.Hidden) onBack()
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = maxHeight * 0.54f,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetContainerColor = Color.White,
            sheetShadowElevation = 10.dp,
            sheetDragHandle = { BottomSheetDefaults.DragHandle() },
            sheetContent = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.90f)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = if (bottomContent == null) 20.dp else 112.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                            }
                            Text("장소 찾기", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Column(content = content)
                    }
                }
            },
        ) {
            val requestedLocation = mapCoordinates?.let { coordinates ->
                RequestedMapLocation(
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    requestId = coordinates.hashCode().toLong(),
                )
            }
            MapBackground(
                modifier = Modifier.fillMaxSize(),
                requestedLocation = requestedLocation,
            )
        }

        if (bottomContent != null && sheetState.currentValue != SheetValue.Hidden) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp, bottom = 16.dp),
                ) {
                    bottomContent()
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, color = TteumMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RoundMapButton(
    onClick: (() -> Unit)?,
    icon: @Composable () -> Unit,
    background: Color = Color.White,
    foreground: Color = TteumInk,
    border: BorderStroke? = null,
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        color = background,
        contentColor = foreground,
        shape = CircleShape,
        border = border,
        shadowElevation = 5.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
}

@Composable
private fun <T> ChoiceRow(
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SelectButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) TteumRedSoft else Color.Transparent,
            contentColor = if (selected) TteumRed else TteumInk,
        ),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(7.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MapBackground(
    modifier: Modifier = Modifier,
    requestedLocation: RequestedMapLocation? = null,
) {
    KakaoMapSurface(
        modifier = modifier,
        latitude = 37.7645,
        longitude = 128.8996,
        zoomLevel = 13,
        requestedLocation = requestedLocation,
    )
}

@Composable
private fun MapPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFFEDEDE8))) {
        val road = Color.White
        val minorRoad = Color(0xFFF7F7F4)
        repeat(7) { index ->
            val y = size.height * (0.12f + index * 0.13f)
            drawLine(
                color = road,
                start = Offset(0f, y),
                end = Offset(size.width, y - size.height * 0.10f),
                strokeWidth = 18f,
                cap = StrokeCap.Round,
            )
        }
        repeat(5) { index ->
            val x = size.width * (0.1f + index * 0.2f)
            drawLine(
                color = minorRoad,
                start = Offset(x, 0f),
                end = Offset(x + size.width * 0.18f, size.height),
                strokeWidth = 10f,
            )
        }
        drawCircle(TteumRed, radius = 14f, center = Offset(size.width * 0.5f, size.height * 0.46f))
        drawCircle(TteumRed.copy(alpha = 0.33f), radius = 28f, center = Offset(size.width * 0.5f, size.height * 0.46f))
    }
}

@Composable
private fun RouteMap(
    modifier: Modifier = Modifier,
    criteria: SearchCriteria,
    recommendation: SafeRecommendation?,
    candidates: List<SafeRecommendation> = emptyList(),
    selectedIds: List<String> = emptyList(),
    onCandidateClick: (String) -> Unit = {},
) {
    val waypoint = recommendation?.place?.latitude?.let { latitude ->
        recommendation.place.longitude?.let { longitude -> Coordinates(latitude, longitude) }
    }
    val fallbackPoints = listOfNotNull(
        criteria.startCoordinates,
        waypoint,
        criteria.endCoordinates,
    )
    val routeStops = buildList {
        criteria.startCoordinates?.let { add("출발" to it) }
        if (criteria.mode == SearchMode.ON_THE_WAY) {
            criteria.endCoordinates?.let { add("도착" to it) }
        }
    }
    val mapCandidates = candidates.mapNotNull { candidate ->
        candidate.place.latitude?.let { latitude ->
            candidate.place.longitude?.let { longitude ->
                MapCandidate(
                    id = candidate.place.id,
                    coordinates = Coordinates(latitude, longitude),
                    category = candidate.place.category,
                    selectedOrder = selectedIds.indexOf(candidate.place.id).takeIf { it >= 0 }?.plus(1),
                )
            }
        }
    }
    val routePoints = recommendation?.routePoints?.ifEmpty { fallbackPoints } ?: fallbackPoints
    KakaoMapSurface(
        modifier = modifier,
        latitude = waypoint?.latitude ?: criteria.endCoordinates?.latitude ?: 37.7645,
        longitude = waypoint?.longitude ?: criteria.endCoordinates?.longitude ?: 128.8996,
        zoomLevel = 15,
        routePoints = routePoints,
        routeStops = routeStops,
        candidateMarkers = mapCandidates,
        corridorPoints = routePoints,
        corridorRadiusMeters = (criteria.deadlineMinutesFromNow * 20).coerceIn(800, 8_000),
        onCandidateClick = onCandidateClick,
    )
}

@Composable
private fun KakaoMapSurface(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Int,
    requestedLocation: RequestedMapLocation? = null,
    routePoints: List<Coordinates> = emptyList(),
    routeStops: List<Pair<String, Coordinates>> = emptyList(),
    candidateMarkers: List<MapCandidate> = emptyList(),
    corridorPoints: List<Coordinates> = emptyList(),
    corridorRadiusMeters: Int = 0,
    onCandidateClick: (String) -> Unit = {},
) {
    if (BuildConfig.KAKAO_MAP_NATIVE_APP_KEY.isBlank()) {
        Box(modifier) {
            MapPlaceholder(Modifier.fillMaxSize())
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = Color.White.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "카카오맵 앱 키 설정이 필요합니다.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = TteumMuted,
                    fontSize = 13.sp,
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapAttempt by remember { mutableStateOf(0) }
    val mapView = remember(mapAttempt) { MapView(context) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var currentLocationLabel by remember { mutableStateOf<Label?>(null) }

    DisposableEffect(mapView, lifecycleOwner) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                override fun onMapError(error: Exception) {
                    mapError = networkFailureMessage("지도 불러오기", error.message)
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(readyMap: KakaoMap) {
                    kakaoMap = readyMap
                }

                override fun getPosition(): LatLng =
                    LatLng.from(latitude, longitude)

                override fun getZoomLevel(): Int = zoomLevel
            },
        )

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.resume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentLocationLabel = null
            kakaoMap = null
            mapView.pause()
            mapView.finish()
        }
    }

    LaunchedEffect(kakaoMap, requestedLocation?.requestId) {
        val map = kakaoMap ?: return@LaunchedEffect
        val target = requestedLocation
        if (target == null) {
            currentLocationLabel?.remove()
            currentLocationLabel = null
            return@LaunchedEffect
        }
        val position = LatLng.from(target.latitude, target.longitude)

        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(position, 16),
            CameraAnimation.from(500),
        )

        val existingLabel = currentLocationLabel
        if (existingLabel == null) {
            currentLocationLabel = map.labelManager?.layer?.addLabel(
                LabelOptions.from("current-location", position)
                    .setStyles(createCurrentLocationMarkerBitmap(context)),
            )
        } else {
            existingLabel.moveTo(position, 350)
        }
    }

    DisposableEffect(kakaoMap, routePoints, routeStops, candidateMarkers, corridorPoints, corridorRadiusMeters) {
        val map = kakaoMap
        if (map == null || routePoints.size < 2) {
            onDispose { }
        } else {
            val points = routePoints.map { LatLng.from(it.latitude, it.longitude) }
            val routeLine = map.routeLineManager?.layer?.addRouteLine(
                RouteLineOptions.from(
                    RouteLineSegment.from(
                        points,
                        RouteLineStyle.from(12f, TteumRed.toArgb(), 3f, Color.White.toArgb()),
                    ),
                ),
            )
            val labels = routeStops.mapIndexedNotNull { index, (name, coordinates) ->
                map.labelManager?.layer?.addLabel(
                    LabelOptions.from(
                        "route-stop-$index",
                        LatLng.from(coordinates.latitude, coordinates.longitude),
                    ).setStyles(createRouteStopBitmap(context, name)),
                )
            }
            val candidateLabels = candidateMarkers.mapNotNull { candidate ->
                map.labelManager?.layer?.addLabel(
                    LabelOptions.from(
                        "candidate-${candidate.id}",
                        LatLng.from(candidate.coordinates.latitude, candidate.coordinates.longitude),
                    ).setStyles(createCandidateMarkerBitmap(context, candidate.category, candidate.selectedOrder)),
                )?.apply {
                    setTag(candidate.id)
                    setClickable(true)
                }
            }
            map.setOnLabelClickListener { _, _, label ->
                (label.tag as? String)?.let(onCandidateClick)
                label.tag is String
            }
            val corridorPolygons = if (corridorRadiusMeters > 0) {
                val step = (corridorPoints.size / 10).coerceAtLeast(1)
                corridorPoints.filterIndexed { index, _ ->
                    index == 0 || index == corridorPoints.lastIndex || index % step == 0
                }.mapNotNull { point ->
                    map.shapeManager?.layer?.addPolygon(
                        PolygonOptions.from(
                            DotPoints.fromCircle(
                                LatLng.from(point.latitude, point.longitude),
                                corridorRadiusMeters.toFloat(),
                            ),
                        ).setStylesSet(
                            PolygonStylesSet.from(
                                PolygonStyles.from(TteumRed.copy(alpha = 0.12f).toArgb()),
                            ),
                        ),
                    )
                }
            } else {
                emptyList()
            }
            map.moveCamera(
                CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 80),
                CameraAnimation.from(500),
            )

            onDispose {
                routeLine?.remove()
                labels.forEach(Label::remove)
                candidateLabels.forEach(Label::remove)
                corridorPolygons.forEach { it.remove() }
                map.setOnLabelClickListener(null)
            }
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        mapError?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = TteumMuted, fontSize = 13.sp)
                    TextButton(
                        onClick = {
                            mapError = null
                            mapAttempt += 1
                        },
                    ) {
                        Text("지도 다시 시도")
                    }
                }
            }
        }
    }
}

private fun createRouteStopBitmap(context: Context, text: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 13f * density
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val width = (textPaint.measureText(text) + 24f * density).roundToInt()
    val height = (34f * density).roundToInt()
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val background = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (text == "출발") 0xFF135BB5.toInt() else TteumRed.toArgb()
        }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, background)
        val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(text, width / 2f, baseline, textPaint)
    }
}

private fun createCandidateMarkerBitmap(
    context: Context,
    category: PlaceCategory,
    selectedOrder: Int?,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = 48f * density
    return Bitmap.createBitmap(size.roundToInt(), size.roundToInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val background = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selectedOrder == null) 0xFFD4D7DC.toInt() else TteumRed.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = (if (selectedOrder == null) 1.5f else 2.5f) * density
        }
        val center = size / 2f
        val outerRadius = 19f * density
        canvas.drawCircle(center, center, outerRadius, background)
        canvas.drawCircle(center, center, outerRadius, border)

        val iconBackground = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (selectedOrder == null) 0xFF3D73D9.toInt() else TteumRed.toArgb()
        }
        canvas.drawCircle(center, center, 13f * density, iconBackground)
        drawCategoryMarkerIcon(canvas, category, center, center, density)

        selectedOrder?.let { order ->
            val badgeCenter = 38f * density
            canvas.drawCircle(badgeCenter, 10f * density, 9f * density, background)
            canvas.drawCircle(badgeCenter, 10f * density, 8f * density, iconBackground)
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 10f * density
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val baseline = 10f * density - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(order.toString(), badgeCenter, baseline, textPaint)
        }
    }
}

private fun drawCategoryMarkerIcon(
    canvas: android.graphics.Canvas,
    category: PlaceCategory,
    centerX: Float,
    centerY: Float,
    density: Float,
) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.7f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(
        centerX + x1 * density,
        centerY + y1 * density,
        centerX + x2 * density,
        centerY + y2 * density,
        paint,
    )
    when (category) {
        PlaceCategory.RESTAURANT -> {
            line(-4f, -6f, -4f, 6f)
            line(-7f, -6f, -7f, -1f)
            line(-1f, -6f, -1f, -1f)
            line(-7f, -1f, -1f, -1f)
            line(4f, -6f, 4f, 6f)
            line(4f, -6f, 7f, -2f)
        }
        PlaceCategory.CAFE -> {
            canvas.drawRoundRect(
                android.graphics.RectF(centerX - 6f * density, centerY - 4f * density, centerX + 4f * density, centerY + 4f * density),
                1.5f * density,
                1.5f * density,
                paint,
            )
            canvas.drawArc(
                android.graphics.RectF(centerX + 2f * density, centerY - 2.5f * density, centerX + 8f * density, centerY + 3f * density),
                -80f,
                160f,
                false,
                paint,
            )
            line(-7f, 6f, 7f, 6f)
        }
        PlaceCategory.ATTRACTION -> {
            line(-7f, 5f, -2f, -4f)
            line(-2f, -4f, 1f, 1f)
            line(1f, 1f, 4f, -3f)
            line(4f, -3f, 8f, 5f)
            line(-7f, 5f, 8f, 5f)
        }
        PlaceCategory.CULTURE -> {
            line(-7f, -3f, 0f, -7f)
            line(0f, -7f, 7f, -3f)
            line(-7f, -3f, 7f, -3f)
            listOf(-5f, 0f, 5f).forEach { x -> line(x, -2f, x, 5f) }
            line(-8f, 6f, 8f, 6f)
        }
        PlaceCategory.SHOPPING -> {
            canvas.drawRoundRect(
                android.graphics.RectF(centerX - 6f * density, centerY - 3f * density, centerX + 6f * density, centerY + 7f * density),
                1.5f * density,
                1.5f * density,
                paint,
            )
            canvas.drawArc(
                android.graphics.RectF(centerX - 3f * density, centerY - 7f * density, centerX + 3f * density, centerY - 1f * density),
                180f,
                180f,
                false,
                paint,
            )
        }
        PlaceCategory.FESTIVAL -> {
            repeat(8) { index ->
                val angle = Math.toRadians(index * 45.0)
                line(
                    (kotlin.math.cos(angle) * 3f).toFloat(),
                    (kotlin.math.sin(angle) * 3f).toFloat(),
                    (kotlin.math.cos(angle) * 7f).toFloat(),
                    (kotlin.math.sin(angle) * 7f).toFloat(),
                )
            }
            canvas.drawCircle(centerX, centerY, 2f * density, paint)
        }
        PlaceCategory.LEISURE -> {
            canvas.drawCircle(centerX, centerY, 7f * density, paint)
            line(-6f, -3f, 6f, 3f)
            line(-6f, 3f, 6f, -3f)
        }
    }
}

internal fun shouldShowHomeIntro(hiddenDate: String?, today: String): Boolean =
    hiddenDate != today

private fun shouldShowHomeIntro(context: Context): Boolean {
    val hiddenDate = context
        .getSharedPreferences(HOME_INTRO_PREFERENCES, Context.MODE_PRIVATE)
        .getString(HOME_INTRO_HIDDEN_DATE, null)
    return shouldShowHomeIntro(hiddenDate, LocalDate.now().toString())
}

private fun hideHomeIntroForToday(context: Context) {
    context
        .getSharedPreferences(HOME_INTRO_PREFERENCES, Context.MODE_PRIVATE)
        .edit()
        .putString(HOME_INTRO_HIDDEN_DATE, LocalDate.now().toString())
        .apply()
}

private fun isKakaoMapAvailable(context: Context): Boolean =
    context.packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://open")),
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    ) != null

private fun openKakaoMapHome(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://open")))
    } catch (_: ActivityNotFoundException) {
        openKakaoMapInstallPage(context)
    }
}

private fun openKakaoMapInstallPage(context: Context) {
    val query = Uri.encode("카카오맵")
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$query")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$query&c=apps")),
    )
    if (intents.none { runCatching { context.startActivity(it) }.isSuccess }) {
        Toast.makeText(context, "앱 스토어를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun clearAppCache(context: Context): Boolean = runCatching {
    savedImageCache.evictAll()
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
}.isSuccess

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

private fun openPolicy(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "공개 문서를 준비 중이에요.", Toast.LENGTH_SHORT).show()
        return
    }
    if (runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isFailure
    ) {
        Toast.makeText(context, "문서를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun openContactEmail(context: Context) {
    val subject = Uri.encode("[틈새] 앱 문의")
    val body = Uri.encode("앱 버전: ${BuildConfig.VERSION_NAME}\n\n문의 내용을 작성해 주세요.")
    val intent = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("mailto:$CONTACT_EMAIL?subject=$subject&body=$body"),
    )
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "메일 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun openKakaoMap(context: Context, placeName: String) {
    val encoded = Uri.encode(placeName)
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("kakaomap://search?q=$encoded"))
    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://map.kakao.com/link/search/$encoded"),
        )
        try {
            context.startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openKakaoMapRoute(
    context: Context,
    start: Coordinates?,
    startName: String?,
    waypoint: Coordinates?,
    destination: Coordinates?,
    destinationName: String?,
    transport: TransportMode,
) {
    if (start == null || destination == null) {
        Toast.makeText(
            context,
            "경로 좌표를 확인할 수 없어 카카오맵을 열지 못했어요.",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    val routeQuery = buildKakaoMapRouteQuery(
        start = start,
        destination = destination,
        transport = transport,
        waypoint = waypoint,
        startName = startName,
        destinationName = destinationName,
    )
    val appIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("kakaomap://route?$routeQuery"),
    )

    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://m.map.kakao.com/scheme/route?$routeQuery"),
        )
        try {
            context.startActivity(webIntent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openKakaoMapMultiRoute(
    context: Context,
    start: Coordinates?,
    startName: String,
    waypoints: List<Pair<String, Coordinates>>,
    destination: Coordinates?,
    destinationName: String,
) {
    if (start == null || destination == null || waypoints.isEmpty()) {
        Toast.makeText(context, "경로 좌표를 확인할 수 없어요.", Toast.LENGTH_SHORT).show()
        return
    }
    val url = buildKakaoMapMultiRouteUrl(
        startName,
        start,
        waypoints.take(MAX_KAKAO_WAYPOINTS),
        destinationName,
        destination,
    )
    if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure) {
        Toast.makeText(context, "카카오맵을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

internal fun buildKakaoMapMultiRouteUrl(
    startName: String,
    start: Coordinates,
    waypoints: List<Pair<String, Coordinates>>,
    destinationName: String,
    destination: Coordinates,
): String {
    fun stop(name: String, coordinates: Coordinates): String =
        "${URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20")}," +
            "${coordinates.latitude},${coordinates.longitude}"
    return buildString {
        append("https://map.kakao.com/link/by/car/")
        append(stop(startName, start))
        waypoints.take(MAX_KAKAO_WAYPOINTS).forEach { (name, coordinates) ->
            append("/")
            append(stop(name, coordinates))
        }
        append("/")
        append(stop(destinationName, destination))
    }
}

internal fun buildKakaoMapRouteQuery(
    start: Coordinates,
    destination: Coordinates,
    transport: TransportMode,
    waypoint: Coordinates? = null,
    startName: String? = null,
    destinationName: String? = null,
): String =
    buildString {
        append("sp=${start.latitude},${start.longitude}")
        startName?.takeIf(String::isNotBlank)?.let {
            append("&sn=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
        }
        waypoint?.let { append("&vp=${it.latitude},${it.longitude}") }
        append("&ep=${destination.latitude},${destination.longitude}")
        destinationName?.takeIf(String::isNotBlank)?.let {
            append("&en=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}")
        }
        append("&by=${if (transport == TransportMode.CAR) "car" else "foot"}")
    }
