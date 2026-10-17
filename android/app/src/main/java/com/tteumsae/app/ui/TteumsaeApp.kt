package com.tteumsae.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapGravity
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.CompetitionUnit
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.OrderingType
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.shape.DotPoints
import com.kakao.vectormap.shape.PolygonOptions
import com.kakao.vectormap.shape.PolygonStyles
import com.kakao.vectormap.shape.PolygonStylesSet
import com.tteumsae.app.BuildConfig
import com.tteumsae.app.location.LocationAccessPolicy
import com.tteumsae.app.TteumsaeApplication
import com.tteumsae.app.data.TteumsaeApi
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.OperationStatus
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SafetyLevel
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.SearchMode
import com.tteumsae.app.domain.TransportMode
import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.route.RouteLocation
import com.tteumsae.app.domain.route.SAFETY_BUFFER_MINUTES
import com.tteumsae.app.domain.route.remainingWholeMinutes
import com.tteumsae.app.platform.CONTACT_EMAIL
import com.tteumsae.app.platform.LOCATION_TERMS_URL
import com.tteumsae.app.platform.PRIVACY_POLICY_URL
import com.tteumsae.app.platform.clearAppCache
import com.tteumsae.app.platform.isKakaoMapAvailable
import com.tteumsae.app.platform.openAppSettings
import com.tteumsae.app.platform.openContactEmail
import com.tteumsae.app.platform.openKakaoMap
import com.tteumsae.app.platform.openKakaoMapHome
import com.tteumsae.app.platform.openKakaoMapInstallPage
import com.tteumsae.app.platform.openKakaoMapMultiRoute
import com.tteumsae.app.platform.openKakaoMapRoute
import com.tteumsae.app.platform.openPolicy
import com.tteumsae.app.ui.common.formatDistance
import com.tteumsae.app.ui.navigation.AppDestination
import com.tteumsae.app.ui.navigation.MainTab
import com.tteumsae.app.ui.navigation.previousDestination
import com.tteumsae.app.ui.navigation.safeRestoredDestination
import com.tteumsae.app.ui.route.RouteFlowViewModel
import com.tteumsae.app.ui.route.RouteLocationScreen
import com.tteumsae.app.ui.route.RouteDetailRequestKey
import com.tteumsae.app.ui.route.RouteMapCameraSnapshot
import com.tteumsae.app.ui.route.restoredRouteMapCamera
import com.tteumsae.app.ui.route.routeDetailRequestKey
import com.tteumsae.app.reminder.ReminderNotifications
import com.tteumsae.app.reminder.ReminderDeliveryBlockReason
import com.tteumsae.app.ui.route.RouteLoadingScreen
import com.tteumsae.app.ui.route.RouteMapCandidate
import com.tteumsae.app.ui.route.RouteMapMarkerItem
import com.tteumsae.app.ui.route.RouteResultsScreen
import com.tteumsae.app.ui.route.RouteStage
import com.tteumsae.app.ui.route.CLUSTER_DISTANCE_NEUTRAL_DP
import com.tteumsae.app.ui.route.clusterRouteCandidates
import com.tteumsae.app.ui.route.clusterDistanceDpAfterZoom
import com.tteumsae.app.ui.route.explicitActivityLabel
import com.tteumsae.app.ui.route.latestDepartureLabel
import com.tteumsae.app.ui.route.latestDepartureTimeLabel
import com.tteumsae.app.ui.route.meaningfulClusterExpansionZoom
import com.tteumsae.app.ui.route.representativeClusterCategories
import com.tteumsae.app.ui.route.mergeFreshPlaceDetails
import com.tteumsae.app.ui.route.nearbyParkingDescription
import com.tteumsae.app.ui.route.normalizedHomepageUrl
import com.tteumsae.app.ui.route.normalizedVisitInfo
import com.tteumsae.app.ui.route.placeSourceCaption
import com.tteumsae.app.ui.route.placePhotoSourceCaption
import com.tteumsae.app.ui.route.placePhotoMayCrop
import com.tteumsae.app.ui.saved.TourPhotoAttribution
import com.tteumsae.app.ui.route.plainTourText
import com.tteumsae.app.ui.route.practicalVisitFacts
import com.tteumsae.app.ui.route.compactMaximumStayLabel
import com.tteumsae.app.ui.route.PlaceCategoryIcon
import com.tteumsae.app.ui.route.readableDuration
import com.tteumsae.app.ui.saved.SavedPlacesScreen
import com.tteumsae.app.ui.saved.SavedPlaceImage
import com.tteumsae.app.ui.settings.SettingsScreen
import com.tteumsae.app.ui.account.AccountViewModel
import com.tteumsae.app.ui.account.AccountViewModelFactory
import com.tteumsae.app.ui.account.ProfileEditScreen
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import com.tteumsae.app.reminder.ActiveTrip
import com.tteumsae.app.reminder.activeTripExpiryEpochMillis
import com.tteumsae.app.reminder.buildDepartureReminderNavigationUrl
import com.tteumsae.app.domain.route.RouteNavigationAction
import com.tteumsae.app.domain.route.recommendationNeedsRecheck
import com.tteumsae.app.domain.route.routeNavigationAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val gangwonRegionCodes = linkedMapOf(
    "강원도 전체" to null,
    "강릉" to 1,
    "고성" to 2,
    "동해" to 3,
    "삼척" to 4,
    "속초" to 5,
    "양구" to 6,
    "양양" to 7,
    "영월" to 8,
    "원주" to 9,
    "인제" to 10,
    "정선" to 11,
    "철원" to 12,
    "춘천" to 13,
    "태백" to 14,
    "평창" to 15,
    "홍천" to 16,
    "화천" to 17,
    "횡성" to 18,
)

private val RouteFocusBlue = Color(0xFF2F6FE4)

private sealed interface RouteMapLabelTarget {
    data class Candidate(val id: String) : RouteMapLabelTarget

    data class Cluster(
        val memberIds: List<String>,
        val coordinates: List<Coordinates>,
        val expansionZoom: Int,
    ) : RouteMapLabelTarget
}

internal fun deniedLocationPermissionNeedsSettings(
    permissions: Map<String, Boolean>,
    canAskAgain: (String) -> Boolean,
): Boolean = permissions.any { (permission, granted) ->
    !granted && !canAskAgain(permission)
}

internal fun networkFailureMessage(operation: String, detail: String?): String =
    "${operation}에 실패했어요. ${detail?.takeIf(String::isNotBlank) ?: "네트워크 연결을 확인해 주세요."}"

internal fun shouldAutoLocateStart(
    startName: String,
    hasLocation: Boolean,
    automaticLocationEnabled: Boolean = true,
): Boolean = automaticLocationEnabled && startName == "현재 위치" && !hasLocation
private const val HOME_INTRO_PREFERENCES = "home_intro"
private const val HOME_INTRO_HIDDEN_DATE = "hidden_date"

@Composable
fun TteumsaeApp() {
    val context = LocalContext.current
    val resultUiStateHolder = rememberSaveableStateHolder()
    val automaticLocationEnabled = LocationAccessPolicy.automaticLocationEnabled
    val api = remember { TteumsaeApi() }
    val application = context.applicationContext as TteumsaeApplication
    val routeViewModel: RouteFlowViewModel = viewModel(
        factory = remember(application) {
            RouteFlowViewModel.factory(application.container.routeGateway)
        },
    )
    val routeState by routeViewModel.uiState.collectAsStateWithLifecycle()
    val reminderCoordinator = remember(application) {
        application.container.departureReminderCoordinator
    }
    var reminderEnabledPlaceId by rememberSaveable {
        mutableStateOf(reminderCoordinator.currentEnabledStopId())
    }
    var reminderBlockedReason by remember {
        mutableStateOf(ReminderNotifications.deliveryBlockReason(context))
    }
    fun refreshReminderDeliveryState() {
        val nextReason = ReminderNotifications.deliveryBlockReason(context)
        reminderCoordinator.reschedulePendingDelivery(deliveryAvailable = nextReason == null)
        reminderEnabledPlaceId = reminderCoordinator.currentEnabledStopId()
        reminderBlockedReason = nextReason
    }
    val appLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(appLifecycleOwner, reminderCoordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshReminderDeliveryState()
            }
        }
        appLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { appLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(reminderCoordinator) {
        while (true) {
            refreshReminderDeliveryState()
            delay(30_000L)
        }
    }
    var pendingReminderTrip by remember { mutableStateOf<ActiveTrip?>(null) }
    var pendingReminderPlaceId by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val trip = pendingReminderTrip
        val placeId = pendingReminderPlaceId
        if (granted && trip != null && placeId != null) {
            val currentState = routeViewModel.uiState.value
            val currentRecommendation = currentState.recommendations.firstOrNull { it.place.id == placeId }
            val stillCurrent = currentState.stage == RouteStage.RESULTS &&
                !currentState.isEditing && currentState.selectedPlaceId == placeId &&
                currentRecommendation != null &&
                currentRecommendation.latestDepartureEpochMillis == trip.latestDepartureEpochMillis &&
                !recommendationNeedsRecheck(
                    currentRecommendation, currentState.calculatedAtEpochMillis, System.currentTimeMillis(),
                )
            reminderBlockedReason = ReminderNotifications.deliveryBlockReason(context)
            if (stillCurrent && reminderBlockedReason == null) {
                reminderEnabledPlaceId = reminderCoordinator.enable(trip)
            }
            if (stillCurrent && reminderBlockedReason != null) {
                Toast.makeText(context, "휴대폰 설정에서 출발 시간 알림을 허용해 주세요.", Toast.LENGTH_LONG).show()
            } else if (!stillCurrent || reminderEnabledPlaceId == null) {
                Toast.makeText(
                    context,
                    "현재 교통으로 다시 확인한 뒤 알림을 켜 주세요.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else if (!granted) {
            Toast.makeText(
                context,
                "알림 권한 없이도 길 안내는 그대로 이용할 수 있어요.",
                Toast.LENGTH_LONG,
            ).show()
        }
        pendingReminderTrip = null
        pendingReminderPlaceId = null
    }
    val accountViewModel: AccountViewModel = viewModel(
        factory = remember(application) {
            AccountViewModelFactory(
                authRepository = application.container.authRepository,
                profileRepository = application.container.profileRepository,
                deletionClient = application.container.accountDeletionClient,
            )
        },
    )
    val accountState by accountViewModel.state.collectAsStateWithLifecycle()
    val savedPlacesRepository = remember(application) {
        application.container.savedPlacesRepository
    }
    val savedPlacesFlow = remember(savedPlacesRepository) {
        savedPlacesRepository.observeSaved()
    }
    val savedPlaces by savedPlacesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var screen by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var selectedDetailPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var freshDetailPlace by remember { mutableStateOf<PlaceCandidate?>(null) }
    var freshDetailKey by remember { mutableStateOf<RouteDetailRequestKey?>(null) }
    var detailMetadataLoading by remember { mutableStateOf(false) }
    var detailMetadataFailed by remember { mutableStateOf(false) }
    var detailMetadataRequestId by remember { mutableIntStateOf(0) }
    var locationChecking by remember { mutableStateOf(false) }
    var locationCheckRequestId by remember { mutableIntStateOf(0) }
    var currentLocationTarget by remember { mutableStateOf<RequestedMapLocation?>(null) }
    var routeMapFocusTarget by remember { mutableStateOf<RequestedMapLocation?>(null) }
    var catalogPlaces by remember { mutableStateOf(emptyList<PlaceCandidate>()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogLoadingMore by remember { mutableStateOf(false) }
    var catalogLoadMoreFailed by remember { mutableStateOf(false) }
    var catalogPage by remember { mutableIntStateOf(1) }
    var catalogHasMore by remember { mutableStateOf(true) }
    var catalogRegion by rememberSaveable { mutableStateOf("강릉") }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogLoadAttempt by rememberSaveable { mutableIntStateOf(0) }
    var catalogLoadedRegion by remember { mutableStateOf<String?>(null) }
    var showHomeIntro by rememberSaveable { mutableStateOf(shouldShowHomeIntro(context)) }
    val appScope = rememberCoroutineScope()
    val latestRouteState by rememberUpdatedState(routeState)
    val detailRecommendation = routeState.recommendations.firstOrNull {
        it.place.id == selectedDetailPlaceId
    }
    val clearDepartureReminder: () -> Unit = {
        reminderCoordinator.clear()
        reminderEnabledPlaceId = null
        pendingReminderTrip = null
        pendingReminderPlaceId = null
    }
    val startNewRouteSearch: () -> Unit = {
        resultUiStateHolder.removeState("route-results")
        locationCheckRequestId += 1
        locationChecking = false
        selectedDetailPlaceId = null
        freshDetailPlace = null
        freshDetailKey = null
        detailMetadataFailed = false
        clearDepartureReminder()
        routeMapFocusTarget = null
        routeViewModel.startNewSearch()
    }
    val editRouteConditions: () -> Unit = {
        routeViewModel.beginEditing()
        screen = AppDestination.LOCATION
    }
    val leaveRouteInput: () -> Unit = {
        locationCheckRequestId += 1
        locationChecking = false
        if (routeState.isEditing) {
            routeViewModel.cancelEditing()
            screen = AppDestination.RESULTS
        } else {
            startNewRouteSearch()
            screen = AppDestination.HOME
        }
    }

    val hasRouteLocations = routeState.input.start != null &&
        routeState.input.destination != null
    val safeScreen = if (
        screen == AppDestination.LOADING &&
        routeState.stage in setOf(RouteStage.LOADING, RouteStage.RESULTS)
    ) {
        AppDestination.LOADING
    } else {
        safeRestoredDestination(
            current = screen,
            hasLocations = hasRouteLocations,
            hasResults = routeState.stage == RouteStage.RESULTS,
            hasDetail = detailRecommendation != null,
        )
    }
    LaunchedEffect(safeScreen) {
        if (screen != safeScreen) screen = safeScreen
    }
    LaunchedEffect(routeState.stage) {
        if (
            screen in setOf(
                AppDestination.LOCATION,
                AppDestination.LOADING,
                AppDestination.RESULTS,
            )
        ) {
            screen = when (routeState.stage) {
                RouteStage.LOCATION -> AppDestination.LOCATION
                RouteStage.LOADING -> AppDestination.LOADING
                RouteStage.RESULTS -> {
                    if (screen == AppDestination.LOADING) {
                        delay(620)
                    }
                    AppDestination.RESULTS
                }
            }
        }
    }

    BackHandler(enabled = screen != AppDestination.HOME) {
        when (screen) {
            AppDestination.LOADING -> {
                startNewRouteSearch()
                screen = AppDestination.LOCATION
            }
            AppDestination.RESULTS -> editRouteConditions()
            AppDestination.LOCATION -> leaveRouteInput()

            else -> screen = previousDestination(screen)
        }
    }
    LaunchedEffect(screen, catalogLoadAttempt, catalogRegion) {
        if (screen == AppDestination.SAVED && catalogLoadedRegion != catalogRegion) {
            catalogLoading = true
            catalogLoadMoreFailed = false
            catalogError = null
            try {
                val firstPage = api.places(sigunguCode = gangwonRegionCodes[catalogRegion])
                catalogPlaces = firstPage.places
                catalogPage = 1
                catalogHasMore = firstPage.hasMore
                catalogLoadedRegion = catalogRegion
            } catch (error: Exception) {
                catalogError = error.message ?: "TourAPI 장소를 불러오지 못했어요."
            } finally {
                catalogLoading = false
            }
        }
    }

    val criteria = SearchCriteria(
        mode = SearchMode.ON_THE_WAY,
        startName = routeState.input.start?.name.orEmpty(),
        endName = routeState.input.destination?.name.orEmpty(),
        deadlineMinutesFromNow = routeState.input.arrivalDeadlineEpochMillis?.let {
            remainingWholeMinutes(it, System.currentTimeMillis()).coerceAtLeast(0)
        } ?: 0,
        safetyBufferMinutes = SAFETY_BUFFER_MINUTES,
        transportMode = TransportMode.CAR,
        categories = routeState.input.categories,
        startCoordinates = routeState.input.start?.coordinates,
        endCoordinates = routeState.input.destination?.coordinates,
        arrivalDeadlineEpochMillis = routeState.input.arrivalDeadlineEpochMillis,
    )
    LaunchedEffect(
        routeState.stage,
        routeState.calculatedAtEpochMillis,
        routeState.selectedPlaceId,
        routeState.isEditing,
    ) {
        if (
            routeState.isEditing || routeState.stage != RouteStage.RESULTS ||
            routeState.calculatedAtEpochMillis == null
        ) {
            return@LaunchedEffect
        }
        val enabledStopId = reminderCoordinator.currentEnabledStopId()
            ?: run {
                reminderEnabledPlaceId = null
                return@LaunchedEffect
            }
        val refreshedRecommendation = routeState.recommendations.firstOrNull {
            it.place.id == routeState.selectedPlaceId && it.place.id == enabledStopId
        }
        reminderEnabledPlaceId = reminderCoordinator.reconcile(
            refreshedRecommendation?.let { activeTripFor(criteria, it) },
        )
    }
    val openRoute: (List<SafeRecommendation>) -> Unit = openRoute@{ routeRecommendations ->
        val resolved = criteria
        // UI timers may pause while another app is open. Check again at the
        // external-app boundary, using the current result rather than a captured card.
        val latestState = routeViewModel.uiState.value
        if (routeRecommendations.isNotEmpty() && latestState.isRefreshing) {
            screen = AppDestination.RESULTS
            Toast.makeText(context, "현재 교통을 확인하고 있어요. 잠시만 기다려 주세요.", Toast.LENGTH_SHORT).show()
            return@openRoute
        }
        val currentRecommendations = routeRecommendations.mapNotNull { requested ->
            latestState.recommendations.firstOrNull { it.place.id == requested.place.id }
        }
        val allStopsStillPresent = currentRecommendations.size == routeRecommendations.size
        val navigationAction = routeNavigationAction(
            criteria = resolved,
            recommendations = if (allStopsStillPresent) currentRecommendations else routeRecommendations,
            calculatedAtEpochMillis = latestState.calculatedAtEpochMillis.takeIf { allStopsStillPresent },
            nowEpochMillis = System.currentTimeMillis(),
        )
        when (navigationAction) {
            RouteNavigationAction.RESET_DEADLINE -> {
                startNewRouteSearch()
                screen = AppDestination.LOCATION
                Toast.makeText(context, "도착 마감을 다시 정해 주세요.", Toast.LENGTH_SHORT).show()
                return@openRoute
            }
            RouteNavigationAction.RECHECK -> {
                routeViewModel.refresh()
                screen = AppDestination.RESULTS
                Toast.makeText(context, "출발 전 체류 가능 시간을 다시 확인할게요.", Toast.LENGTH_SHORT).show()
                return@openRoute
            }
            RouteNavigationAction.OPEN_ROUTE -> Unit
        }
        openKakaoMapMultiRoute(
            context = context,
            start = resolved.startCoordinates,
            startName = resolved.startName,
            waypoints = currentRecommendations.mapNotNull { recommendation ->
                recommendation.place.latitude?.let { latitude ->
                    recommendation.place.longitude?.let { longitude ->
                        recommendation.place.name to Coordinates(latitude, longitude)
                    }
                }
            },
            destination = resolved.endCoordinates,
            destinationName = resolved.endName,
        )
    }

    when (screen) {
        AppDestination.HOME,
        AppDestination.LOCATION,
        -> {
            val routeInputOpen = screen == AppDestination.LOCATION
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    showIntro = showHomeIntro && !routeInputOpen,
                    routeInputOpen = routeInputOpen,
                    currentLocationTarget = currentLocationTarget,
                    routeMapFocusTarget = routeMapFocusTarget,
                    onCurrentLocationTargetChange = { currentLocationTarget = it },
                    onDismissIntro = { hideForToday ->
                        if (hideForToday) hideHomeIntroForToday(context)
                        showHomeIntro = false
                    },
                    onStart = { coordinates ->
                        startNewRouteSearch()
                        routeViewModel.updateStart(
                            coordinates?.takeIf { automaticLocationEnabled }
                                ?.let { RouteLocation("현재 위치", it) },
                        )
                        routeViewModel.updateDestination(null)
                        routeViewModel.updateDeadline(null)
                        routeViewModel.updateFilters(emptySet())
                        screen = AppDestination.LOCATION
                    },
                    onTabSelected = { tab ->
                        screen = when (tab) {
                            MainTab.EXPLORE -> AppDestination.HOME
                            MainTab.SAVED -> AppDestination.SAVED
                            MainTab.SETTINGS -> AppDestination.SETTINGS
                        }
                    },
                )

                AnimatedVisibility(
                    visible = routeInputOpen,
                    enter = fadeIn(tween(220)) +
                        slideInVertically(tween(280)) { fullHeight -> fullHeight / 14 },
                    exit = fadeOut(tween(160)) +
                        slideOutVertically(tween(220)) { fullHeight -> fullHeight / 16 },
                ) {
                    RouteLocationScreen(
                        input = routeState.input,
                        errorMessage = routeState.errorMessage,
                        searchPlaces = api::searchPlaces,
                        resolveCurrentAddress = api::regionAddress,
                        onStartSelected = {
                            routeViewModel.updateStart(it)
                            val selectedTarget = it?.let { location ->
                                RequestedMapLocation(
                                    latitude = location.coordinates.latitude,
                                    longitude = location.coordinates.longitude,
                                    requestId = System.nanoTime(),
                                )
                            }
                            currentLocationTarget = selectedTarget.takeIf { automaticLocationEnabled }
                            if (!automaticLocationEnabled) routeMapFocusTarget = selectedTarget
                        },
                        onDestinationSelected = { location ->
                            routeViewModel.updateDestination(location)
                            routeMapFocusTarget = location?.let {
                                RequestedMapLocation(
                                    latitude = it.coordinates.latitude,
                                    longitude = it.coordinates.longitude,
                                    requestId = System.nanoTime(),
                                )
                            }
                        },
                        onDeadlineSelected = routeViewModel::updateDeadline,
                        onFiltersChanged = routeViewModel::updateFilters,
                        isChecking = locationChecking,
                        isEditing = routeState.isEditing,
                        onBack = leaveRouteInput,
                        onNext = {
                            if (!locationChecking) {
                                val inputSnapshot = routeState.input
                                val requestId = locationCheckRequestId + 1
                                locationCheckRequestId = requestId
                                locationChecking = true
                                appScope.launch {
                                    try {
                                        val destination = requireNotNull(inputSnapshot.destination)
                                        val destinationIsGangwon = api.isGangwon(destination.coordinates)
                                        val requestIsCurrent =
                                            requestId == locationCheckRequestId &&
                                                screen == AppDestination.LOCATION &&
                                                latestRouteState.input == inputSnapshot
                                        if (!requestIsCurrent) return@launch
                                        if (!destinationIsGangwon) {
                                            Toast.makeText(
                                                context,
                                                "목적지가 강원도 밖이라 이동 중 들를 장소를 추천할 수 없어요.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        } else {
                                            routeViewModel.search()
                                            if (routeViewModel.uiState.value.stage == RouteStage.LOADING) {
                                                clearDepartureReminder()
                                                resultUiStateHolder.removeState("route-results")
                                                freshDetailPlace = null
                                                freshDetailKey = null
                                            }
                                        }
                                    } catch (error: Exception) {
                                        if (
                                            requestId == locationCheckRequestId &&
                                            screen == AppDestination.LOCATION
                                        ) {
                                            Toast.makeText(
                                                context,
                                                error.message ?: "위치를 확인하지 못했어요.",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    } finally {
                                        if (requestId == locationCheckRequestId) {
                                            locationChecking = false
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }

        AppDestination.SAVED -> SavedPlacesScreen(
            catalogPlaces = catalogPlaces,
            savedPlaces = savedPlaces,
            selectedRegion = catalogRegion,
            regions = gangwonRegionCodes.keys.toList(),
            onRegionSelected = { region ->
                if (catalogRegion != region) {
                    catalogRegion = region
                    catalogLoadingMore = false
                    catalogLoadMoreFailed = false
                }
            },
            isLoading = catalogLoading,
            isLoadingMore = catalogLoadingMore,
            hasMore = catalogHasMore,
            loadMoreFailed = catalogLoadMoreFailed,
            errorMessage = catalogError,
            onRetry = {
                catalogLoadedRegion = null
                catalogLoadMoreFailed = false
                catalogLoadAttempt += 1
            },
            onLoadMore = {
                if (
                    !catalogLoading &&
                    !catalogLoadingMore &&
                    !catalogLoadMoreFailed &&
                    catalogError == null &&
                    catalogHasMore
                ) {
                    val requestedRegion = catalogRegion
                    val requestedPage = catalogPage + 1
                    catalogLoadingMore = true
                    appScope.launch {
                        try {
                            val nextPage = api.places(
                                page = requestedPage,
                                sigunguCode = gangwonRegionCodes[requestedRegion],
                            )
                            if (
                                catalogRegion == requestedRegion &&
                                catalogLoadedRegion == requestedRegion &&
                                catalogPage < requestedPage
                            ) {
                                catalogPlaces = (catalogPlaces + nextPage.places)
                                    .distinctBy { it.id }
                                catalogPage = requestedPage
                                catalogHasMore = nextPage.hasMore
                            }
                        } catch (_: Exception) {
                            if (catalogRegion == requestedRegion) {
                                catalogLoadMoreFailed = true
                                Toast.makeText(
                                    context,
                                    "장소를 더 불러오지 못했어요.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } finally {
                            if (catalogRegion == requestedRegion) {
                                catalogLoadingMore = false
                            }
                        }
                    }
                }
            },
            onRetryLoadMore = {
                catalogLoadMoreFailed = false
            },
            onToggleSave = { place ->
                appScope.launch {
                    savedPlacesRepository.toggleGuest(place, System.currentTimeMillis())
                }
            },
            onRestore = { entry ->
                appScope.launch { savedPlacesRepository.restoreGuest(entry) }
            },
            onOpenMap = { place ->
                val coordinates = place.latitude?.let { latitude ->
                    place.longitude?.let { longitude -> Coordinates(latitude, longitude) }
                }
                openKakaoMap(context, place.name, coordinates)
            },
            onTabSelected = { tab ->
                screen = when (tab) {
                    MainTab.EXPLORE -> AppDestination.HOME
                    MainTab.SAVED -> AppDestination.SAVED
                    MainTab.SETTINGS -> AppDestination.SETTINGS
                }
            },
        )

        AppDestination.SETTINGS -> {
            val lifecycleOwner = LocalLifecycleOwner.current
            var locationPermissionGranted by remember {
                mutableStateOf(automaticLocationEnabled && hasLocationPermission(context))
            }
            var kakaoMapAvailable by remember {
                mutableStateOf(isKakaoMapAvailable(context))
            }

            DisposableEffect(lifecycleOwner, context) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        locationPermissionGranted = automaticLocationEnabled && hasLocationPermission(context)
                        kakaoMapAvailable = isKakaoMapAvailable(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            SettingsScreen(
                accountState = accountState,
                savedCount = savedPlaces.size,
                locationPermissionGranted = locationPermissionGranted,
                kakaoMapAvailable = kakaoMapAvailable,
                appVersion = BuildConfig.VERSION_NAME,
                contactEmail = CONTACT_EMAIL,
                privacyPolicyAvailable = PRIVACY_POLICY_URL.isNotBlank(),
                locationTermsAvailable = LOCATION_TERMS_URL.isNotBlank(),
                onOpenLogin = accountViewModel::openLogin,
                onDismissLogin = accountViewModel::dismissLogin,
                onLogin = accountViewModel::signIn,
                onOpenProfile = {
                    if (accountState.profile != null) screen = AppDestination.PROFILE
                },
                onRetryProfile = accountViewModel::retryProfileLoad,
                onSignOut = accountViewModel::signOut,
                onRequestAccountDeletion = accountViewModel::requestDeletion,
                onConfirmDeletionConsequences = accountViewModel::confirmDeletionConsequences,
                onRequireReauthentication = accountViewModel::requireReauthentication,
                onReauthenticateForDeletion = accountViewModel::reauthenticateForDeletion,
                onCancelDeletion = accountViewModel::cancelDeletion,
                onOpenLocationSettings = {
                    if (automaticLocationEnabled) openAppSettings(context)
                },
                onOpenKakaoMap = {
                    if (kakaoMapAvailable) {
                        openKakaoMapHome(context)
                    } else {
                        openKakaoMapInstallPage(context)
                    }
                },
                onClearCache = { clearAppCache(context) },
                onClearSaved = {
                    appScope.launch { savedPlacesRepository.clearGuest() }
                },
                onOpenPrivacyPolicy = { openPolicy(context, PRIVACY_POLICY_URL) },
                onOpenLocationTerms = { openPolicy(context, LOCATION_TERMS_URL) },
                onContact = { openContactEmail(context) },
                onShowMessage = { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
                onTabSelected = { tab ->
                    screen = when (tab) {
                        MainTab.EXPLORE -> AppDestination.HOME
                        MainTab.SAVED -> AppDestination.SAVED
                        MainTab.SETTINGS -> AppDestination.SETTINGS
                    }
                },
            )
        }

        AppDestination.PROFILE -> {
            val profile = accountState.profile
            when {
                profile != null -> ProfileEditScreen(
                    profile = profile,
                    isLoading = accountState.isLoading,
                    errorMessage = accountState.errorMessage,
                    onBack = { screen = AppDestination.SETTINGS },
                    onSave = accountViewModel::saveProfile,
                )

                accountState.session is AccountSession.Restoring || accountState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF7F8FA))
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                stateDescription = "프로필 불러오는 중"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = TteumRed)
                    }
                }

                else -> LaunchedEffect(Unit) { screen = AppDestination.SETTINGS }
            }
        }

        AppDestination.LOADING -> RouteLoadingScreen(
            completed = routeState.stage == RouteStage.RESULTS,
            hasRecommendations = routeState.recommendations.isNotEmpty(),
            onBack = {
                startNewRouteSearch()
                screen = AppDestination.LOCATION
            },
            background = { modifier ->
                RouteMap(
                    modifier = modifier,
                    criteria = criteria,
                    recommendation = null,
                    routeSummary = routeState.baseRoute,
                )
            },
        )

        AppDestination.RESULTS -> resultUiStateHolder.SaveableStateProvider("route-results") {
        RouteResultsScreen(
            criteria = criteria,
            recommendations = routeState.recommendations,
            baseRoute = routeState.baseRoute,
            corridorRadiusMeters = routeState.corridorRadiusMeters,
            selectedPlaceId = routeState.selectedPlaceId,
            warning = routeState.warning,
            calculatedAtEpochMillis = routeState.calculatedAtEpochMillis,
            isRefreshing = routeState.isRefreshing,
            reminderEnabled = routeState.selectedPlaceId != null &&
                reminderEnabledPlaceId == routeState.selectedPlaceId,
            reminderBlockedReason = reminderBlockedReason,
            onOpenReminderSettings = {
                if (!ReminderNotifications.openSettings(context, reminderBlockedReason)) {
                    Toast.makeText(context, "알림 설정을 열 수 없어요.", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectPlace = { placeId ->
                if (reminderEnabledPlaceId != null && reminderEnabledPlaceId != placeId) {
                    clearDepartureReminder()
                }
                routeViewModel.selectPlace(placeId)
            },
            onClearSelection = {
                clearDepartureReminder()
                routeViewModel.clearSelection()
            },
            onRefresh = routeViewModel::refresh,
            onReminderChanged = reminder@{ recommendation, enabled ->
                if (!enabled) {
                    clearDepartureReminder()
                    return@reminder
                }
                if (recommendationNeedsRecheck(
                        recommendation, routeState.calculatedAtEpochMillis, System.currentTimeMillis(),
                    )
                ) {
                    Toast.makeText(context, "현재 교통으로 다시 확인한 뒤 알림을 켜 주세요.", Toast.LENGTH_SHORT).show()
                    return@reminder
                }
                val trip = activeTripFor(criteria, recommendation)
                if (trip == null) {
                    Toast.makeText(context, "알림에 필요한 경로 정보가 부족해요.", Toast.LENGTH_SHORT).show()
                    return@reminder
                }
                if (trip.latestDepartureEpochMillis <= System.currentTimeMillis()) {
                    Toast.makeText(context, "출발 권장시각이 지나 알림을 설정할 수 없어요.", Toast.LENGTH_SHORT).show()
                    return@reminder
                }
                val hasNotificationPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                if (hasNotificationPermission) {
                    reminderBlockedReason = ReminderNotifications.deliveryBlockReason(context)
                    if (reminderBlockedReason != null) {
                        ReminderNotifications.openSettings(context, reminderBlockedReason)
                        return@reminder
                    }
                    reminderEnabledPlaceId = reminderCoordinator.enable(trip)
                    if (reminderEnabledPlaceId == null) {
                        Toast.makeText(
                            context,
                            "출발 권장시각이 지나 알림을 설정할 수 없어요.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    pendingReminderTrip = trip
                    pendingReminderPlaceId = recommendation.place.id
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onBack = editRouteConditions,
            onNewSearch = {
                startNewRouteSearch()
                screen = AppDestination.LOCATION
            },
            onNavigate = { recommendation ->
                openRoute(listOfNotNull(recommendation))
            },
            onDetail = { recommendation ->
                selectedDetailPlaceId = recommendation.place.id
                screen = AppDestination.DETAIL
            },
        )

        }

        AppDestination.DETAIL -> detailRecommendation?.let { recommendation ->
            val requestKey = routeDetailRequestKey(
                recommendation.place.id,
                routeState.calculatedAtEpochMillis,
                recommendation.place.firstLegMinutes,
            )
            LaunchedEffect(requestKey, detailMetadataRequestId) {
                if (
                    freshDetailPlace?.id == recommendation.place.id && freshDetailKey == requestKey &&
                    !detailMetadataFailed
                ) return@LaunchedEffect
                freshDetailPlace = null
                freshDetailKey = null
                detailMetadataLoading = true
                detailMetadataFailed = false
                try {
                    val loaded = api.place(
                        recommendation.place.id,
                        atEpochMillis = requestKey.expectedArrivalEpochMillis,
                    )
                    if (selectedDetailPlaceId == recommendation.place.id) {
                        freshDetailPlace = loaded
                        freshDetailKey = requestKey
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    if (selectedDetailPlaceId == recommendation.place.id) {
                        detailMetadataFailed = true
                    }
                } finally {
                    if (selectedDetailPlaceId == recommendation.place.id) {
                        detailMetadataLoading = false
                    }
                }
            }
            val renderedRecommendation = recommendation.copy(
                place = freshDetailPlace
                    ?.takeIf { it.id == recommendation.place.id && freshDetailKey == requestKey }
                    ?.let { mergeFreshPlaceDetails(recommendation.place, it) }
                    ?: recommendation.place,
            )
            DetailScreen(
                criteria = criteria,
                recommendation = renderedRecommendation,
                warning = routeState.warning,
                calculatedAtEpochMillis = routeState.calculatedAtEpochMillis,
                metadataLoading = detailMetadataLoading,
                metadataLoadFailed = detailMetadataFailed,
                onRetryMetadata = {
                    detailMetadataRequestId += 1
                },
                isSaved = savedPlaces.any { entry -> entry.place.id == recommendation.place.id },
                onToggleSave = {
                    appScope.launch {
                        savedPlacesRepository.toggleGuest(
                            renderedRecommendation.place,
                            System.currentTimeMillis(),
                        )
                    }
                },
                isOpeningRoute = false,
                onOpenRoute = {
                    routeViewModel.selectPlace(recommendation.place.id)
                    openRoute(listOf(recommendation))
                },
                onRefreshResult = {
                    routeViewModel.selectPlace(recommendation.place.id)
                    routeViewModel.refresh()
                    screen = AppDestination.RESULTS
                },
                onResetRoute = {
                    startNewRouteSearch()
                    screen = AppDestination.LOCATION
                },
                onBack = { screen = AppDestination.RESULTS },
            )
        } ?: run {
            LaunchedEffect(selectedDetailPlaceId) {
                screen = AppDestination.RESULTS
            }
        }
    }
}

private fun activeTripFor(
    criteria: SearchCriteria,
    recommendation: SafeRecommendation,
): ActiveTrip? {
    val start = criteria.startCoordinates ?: return null
    val destination = criteria.endCoordinates ?: return null
    val deadline = criteria.arrivalDeadlineEpochMillis ?: return null
    val stopLatitude = recommendation.place.latitude ?: return null
    val stopLongitude = recommendation.place.longitude ?: return null
    val latestDeparture = recommendation.latestDepartureEpochMillis ?: return null
    val stop = Coordinates(stopLatitude, stopLongitude)
    return ActiveTrip(
        startName = criteria.startName,
        start = start,
        destinationName = criteria.endName,
        destination = destination,
        stopId = recommendation.place.id,
        stopName = recommendation.place.name,
        stop = stop,
        arrivalDeadlineEpochMillis = deadline,
        latestDepartureEpochMillis = latestDeparture,
        navigationUrl = buildDepartureReminderNavigationUrl(
            stopName = recommendation.place.name,
            stop = stop,
            destinationName = criteria.endName,
            destination = destination,
        ),
        expiresAtEpochMillis = activeTripExpiryEpochMillis(deadline),
    )
}

internal fun homeMapLogoBottomMarginDp(
    routeInputOpen: Boolean,
    navigationBottomInsetDp: Float,
): Float = 12f + if (routeInputOpen) 110f + navigationBottomInsetDp.coerceAtLeast(0f) else 0f

@Composable
private fun HomeScreen(
    showIntro: Boolean,
    routeInputOpen: Boolean,
    currentLocationTarget: RequestedMapLocation?,
    routeMapFocusTarget: RequestedMapLocation?,
    onCurrentLocationTargetChange: (RequestedMapLocation?) -> Unit,
    onDismissIntro: (Boolean) -> Unit,
    onStart: (Coordinates?) -> Unit,
    onTabSelected: (MainTab) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val navigationBottomInsetDp = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp().value
    }
    val automaticLocationEnabled = LocationAccessPolicy.automaticLocationEnabled
    var isLocating by remember { mutableStateOf(false) }
    var cancelLocationRequest by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    val locateCurrentPosition: () -> Unit = locate@{
        if (!automaticLocationEnabled) return@locate
        cancelLocationRequest?.invoke()
        isLocating = true
        val cancel = requestCurrentLocation(
            context = context,
            onSuccess = { location ->
                onCurrentLocationTargetChange(
                    RequestedMapLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        requestId = System.nanoTime(),
                    ),
                )
                cancelLocationRequest = null
                isLocating = false
            },
            onLocationDisabled = {
                cancelLocationRequest = null
                isLocating = false
                showLocationSettingsDialog = true
            },
            onUnavailable = {
                cancelLocationRequest = null
                isLocating = false
                Toast.makeText(
                    context,
                    "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
        cancelLocationRequest = cancel.takeIf { isLocating }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelLocationRequest?.invoke()
            cancelLocationRequest = null
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (!automaticLocationEnabled) return@rememberLauncherForActivityResult
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
            if (!routeInputOpen) {
                BottomNavigation(
                    selectedTab = MainTab.EXPLORE,
                    onTabSelected = onTabSelected,
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MapBackground(
                modifier = Modifier.fillMaxSize(),
                requestedLocation = currentLocationTarget.takeIf { automaticLocationEnabled },
                centerRequestedLocation = !routeInputOpen,
                cameraTarget = routeMapFocusTarget,
                logoBottomMargin = homeMapLogoBottomMarginDp(
                    routeInputOpen,
                    navigationBottomInsetDp,
                ).dp,
            )
            AnimatedVisibility(
                visible = !routeInputOpen,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(130)),
            ) {
                SearchBar(
                    text = "목적지 검색",
                    supportingText = "가는 길에 잠깐 들를 한 곳을 찾아드려요",
                    onClick = {
                        onStart(
                            currentLocationTarget?.takeIf { automaticLocationEnabled }?.let {
                                Coordinates(it.latitude, it.longitude)
                            },
                        )
                    },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.sm),
                )
            }

            AnimatedVisibility(
                visible = !routeInputOpen,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(130)),
            ) {
                Box(modifier = Modifier.padding(end = TteumSpacing.screenHorizontal, bottom = TteumSpacing.lg)) {
                if (automaticLocationEnabled) RoundMapButton(
                    onClick = if (isLocating) null else {
                        {
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
                    },
                    foreground = if (currentLocationTarget != null) TteumRed else TteumInk,
                    contentDescription = "내 위치로 이동",
                    selected = currentLocationTarget != null,
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
                                contentDescription = null,
                            )
                        }
                    },
                )
                }
            }

        }
    }

    if (automaticLocationEnabled && showLocationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            modifier = Modifier.widthIn(max = 420.dp),
            title = { Text("위치 서비스를 켜 주세요", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("현재 위치를 찾으려면 휴대폰의 위치 서비스를 켜야 합니다.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationSettingsDialog = false
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) {
                    Text("위치 설정 열기", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLocationSettingsDialog = false },
                    modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                    colors = ButtonDefaults.textButtonColors(contentColor = TteumMuted),
                ) {
                    Text("취소", style = MaterialTheme.typography.labelLarge)
                }
            },
            shape = TteumShapes.dialog,
            containerColor = Color.White,
            tonalElevation = 0.dp,
        )
    }

    if (automaticLocationEnabled && showPermissionSettingsDialog) {
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
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.95f,
        animationSpec = tween(220),
        label = "home-intro-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(180),
        label = "home-intro-alpha",
    )

    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = { onDismiss(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(TteumSpacing.screenHorizontal)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            color = Color.White,
            shape = TteumShapes.dialog,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, Color(0xFFE7E8EC)),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(TteumInsets.dialog),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    color = TteumRedSoft,
                    contentColor = TteumRed,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(TteumSpacing.md))
                Text(
                    "강원도로 가는 길, 어디 들를까?",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(TteumSpacing.md))
                Text(
                    "기본 경로와 추가 이동·머무는 시간을 계산해\n가는 길에 들를 장소를 찾아드려요.",
                    color = TteumMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(TteumSpacing.lg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = hideForToday,
                            role = Role.Checkbox,
                            onValueChange = { hideForToday = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = hideForToday,
                        onCheckedChange = null,
                    )
                    Text("오늘 하루 보지 않기", color = TteumMuted, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(TteumSpacing.md))
                Button(
                    onClick = { onDismiss(hideForToday) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TteumSizing.buttonHeight),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) {
                    Text("확인", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun BottomNavigation(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
) {
    val shape = TteumShapes.sheet
    val mapSelected = selectedTab == MainTab.EXPLORE
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 92.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(top = TteumSpacing.md, bottom = TteumSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            BottomNavItem(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                label = "장소 탐색",
                selected = selectedTab == MainTab.SAVED,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(MainTab.SAVED) },
            )
                Spacer(Modifier.weight(1f))
            BottomNavItem(
                icon = Icons.Default.Settings,
                label = "설정",
                selected = selectedTab == MainTab.SETTINGS,
                modifier = Modifier.weight(1f),
                onClick = { onTabSelected(MainTab.SETTINGS) },
            )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(96.dp)
                    .selectable(
                        selected = mapSelected,
                        role = Role.Tab,
                        onClick = { onTabSelected(MainTab.EXPLORE) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    color = if (mapSelected) TteumRed else Color(0xFFF4F5F7),
                    contentColor = if (mapSelected) Color.White else Color(0xFF6F747D),
                    shape = CircleShape,
                    shadowElevation = if (mapSelected) 5.dp else 0.dp,
                    border = BorderStroke(
                        5.dp,
                        if (mapSelected) Color(0xFFE4E5E8) else Color(0xFFD9DCE1),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "지도",
                    modifier = Modifier.padding(bottom = TteumSpacing.xs),
                    color = if (mapSelected) TteumInk else TteumMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    fontWeight = if (mapSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
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
    val color = if (selected) TteumInk else TteumMuted
    Column(
        modifier = modifier.selectable(
            selected = selected,
            role = Role.Tab,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
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
        modifier = Modifier.widthIn(max = 420.dp),
        title = { Text("위치 권한을 켜 주세요", style = MaterialTheme.typography.headlineSmall) },
        text = { Text("앱 설정에서 위치 권한을 허용하거나 위치를 직접 검색해 주세요.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
            ) {
                Text("앱 설정 열기", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                colors = ButtonDefaults.textButtonColors(contentColor = TteumMuted),
            ) {
                Text("취소", style = MaterialTheme.typography.labelLarge)
            }
        },
        shape = TteumShapes.dialog,
        containerColor = Color.White,
        tonalElevation = 0.dp,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailScreen(
    criteria: SearchCriteria,
    recommendation: SafeRecommendation,
    warning: String,
    calculatedAtEpochMillis: Long?,
    metadataLoading: Boolean,
    metadataLoadFailed: Boolean,
    onRetryMetadata: () -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    isOpeningRoute: Boolean,
    onOpenRoute: () -> Unit,
    onRefreshResult: () -> Unit,
    onResetRoute: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val deadlineHasPassed = com.tteumsae.app.ui.route.arrivalDeadlineHasPassed(
        criteria,
        nowEpochMillis,
    )
    val needsRecheck = recommendationNeedsRecheck(
        recommendation,
        calculatedAtEpochMillis,
        nowEpochMillis,
    )
    val navigationAction = routeNavigationAction(
        criteria, listOf(recommendation), calculatedAtEpochMillis, nowEpochMillis,
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowEpochMillis = System.currentTimeMillis()
        }
    }
    val place = recommendation.place
    val visitInfo = practicalVisitFacts(place)
    val overview = plainTourText(place.overview)
    val telephone = normalizedVisitInfo(place.telephone)
    val homepageUrl = normalizedHomepageUrl(place.homepageUrl)
    val hasOperatingHours = normalizedVisitInfo(place.openingHours) != null
    val operationBadgeLabel = when {
        recommendation.operationStatus == OperationStatus.OPEN && !needsRecheck -> "운영 가능"
        hasOperatingHours -> "운영시간 확인"
        else -> "운영 확인 필요"
    }
    val operationBadgeForeground = when {
        recommendation.operationStatus == OperationStatus.OPEN && !needsRecheck -> Color(0xFF20724E)
        hasOperatingHours -> Color(0xFF365F9B)
        else -> Color(0xFF8A6418)
    }
    val operationBadgeBackground = when {
        recommendation.operationStatus == OperationStatus.OPEN && !needsRecheck -> Color(0xFFEAF6EF)
        hasOperatingHours -> Color(0xFFEAF1FB)
        else -> Color(0xFFFFF5DD)
    }
    val heroImageUrl = normalizedVisitInfo(place.imageUrl)
        ?: place.imageUrls.firstOrNull()?.let(::normalizedVisitInfo)
    val photoSourceCaption = placePhotoSourceCaption(place, heroImageUrl)
    val additionalDetails = place.detailItems.mapNotNull { item ->
        val title = normalizedVisitInfo(item.title)
        val description = plainTourText(item.description)
        if (title == null || description == null) null else title to description
    }.distinct()
    val accessibilityDetails = place.accessibilityItems.mapNotNull { item ->
        val title = normalizedVisitInfo(item.title)
        val description = plainTourText(item.description)
        if (title == null || description == null) null else title to description
    }.distinct()
    Scaffold(
        containerColor = Color.White,
        topBar = {
            Surface(color = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = TteumSpacing.sm, vertical = TteumSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(TteumSizing.touchTarget)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Text(
                        "장소 상세",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onToggleSave, modifier = Modifier.size(TteumSizing.touchTarget)) {
                        Icon(
                            if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isSaved) "저장 해제" else "저장",
                            tint = if (isSaved) TteumRed else TteumInk,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.sm),
                ) {
                    if (navigationAction != RouteNavigationAction.OPEN_ROUTE) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = TteumRedSoft,
                            contentColor = TteumInk,
                            shape = TteumShapes.compact,
                        ) {
                            Text(
                                when {
                                    deadlineHasPassed -> "도착 마감이 지났어요. 시간을 다시 정해 주세요"
                                    navigationAction == RouteNavigationAction.RESET_DEADLINE -> "다시 계산하려면 도착 마감을 새로 정해 주세요"
                                    else -> "계산 이후 시간이 지났어요. 지금 들를 수 있는지 다시 확인해 주세요"
                                },
                                modifier = Modifier.padding(TteumInsets.row),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(TteumSpacing.xs))
                    }
                    Button(
                        onClick = {
                            when (routeNavigationAction(
                                criteria, listOf(recommendation), calculatedAtEpochMillis, System.currentTimeMillis(),
                            )) {
                                RouteNavigationAction.RESET_DEADLINE -> onResetRoute()
                                RouteNavigationAction.RECHECK -> onRefreshResult()
                                RouteNavigationAction.OPEN_ROUTE -> onOpenRoute()
                            }
                        },
                        enabled = !isOpeningRoute,
                        modifier = Modifier.fillMaxWidth().heightIn(min = TteumSizing.buttonHeight),
                        shape = TteumShapes.control,
                        contentPadding = TteumInsets.button,
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null)
                        Spacer(Modifier.width(TteumSpacing.inlineGap))
                        Text(
                            when {
                                isOpeningRoute -> "경로 확인 중..."
                                navigationAction == RouteNavigationAction.RESET_DEADLINE -> "도착 마감 다시 정하기"
                                navigationAction == RouteNavigationAction.RECHECK -> "현재 교통으로 다시 확인"
                                else -> "이곳 들러 카카오맵 안내"
                            },
                            style = MaterialTheme.typography.labelLarge,
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
            contentPadding = PaddingValues(bottom = TteumSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth(),
                ) {
                if (heroImageUrl != null) {
                    SavedPlaceImage(
                        imageUrl = heroImageUrl,
                        category = place.category,
                        cropAllowed = placePhotoMayCrop(place, heroImageUrl),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TteumSpacing.screenHorizontal)
                            .clip(TteumShapes.card)
                            .aspectRatio(16f / 9f),
                    )
                    photoSourceCaption?.let { caption ->
                        TourPhotoAttribution(
                            caption = caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.xs),
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TteumSpacing.screenHorizontal)
                            .height(64.dp),
                        color = TteumBackground,
                        shape = TteumShapes.card,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PlaceCategoryIcon(category = place.category)
                        }
                    }
                }
                Column(Modifier.padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = TteumRedSoft, shape = RoundedCornerShape(50)) {
                            Text(
                                place.category.label,
                                modifier = Modifier.padding(TteumInsets.badge),
                                color = TteumRed,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(Modifier.width(TteumSpacing.inlineGap))
                        Surface(
                            color = operationBadgeBackground,
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                operationBadgeLabel,
                                modifier = Modifier.padding(TteumInsets.badge),
                                color = operationBadgeForeground,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(TteumSpacing.itemGap))
                    Text(place.name, style = MaterialTheme.typography.headlineMedium)
                    normalizedVisitInfo(place.address)?.let { address ->
                        Spacer(Modifier.height(TteumSpacing.xs))
                        Text(address, color = TteumMuted, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (telephone != null || homepageUrl != null) {
                        Spacer(Modifier.height(TteumSpacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap),
                        ) {
                            telephone?.let { phone ->
                                OutlinedButton(
                                    onClick = {
                                        openExternalDetailUri(
                                            context,
                                            Uri.parse("tel:${Uri.encode(phone)}"),
                                            "전화 앱을 열 수 없어요.",
                                        )
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = TteumSizing.touchTarget),
                                    shape = TteumShapes.control,
                                    contentPadding = TteumInsets.button,
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(TteumSpacing.inlineGap))
                                    Text("전화", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            homepageUrl?.let { url ->
                                OutlinedButton(
                                    onClick = {
                                        openExternalDetailUri(
                                            context,
                                            Uri.parse(url),
                                            "홈페이지를 열 수 없어요.",
                                        )
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = TteumSizing.touchTarget),
                                    shape = TteumShapes.control,
                                    contentPadding = TteumInsets.button,
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(TteumSpacing.inlineGap))
                                    Text("홈페이지", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(TteumSpacing.sectionGap))
                    Text("이번 경로에서", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(TteumSpacing.itemGap))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = TteumRedSoft,
                        shape = TteumShapes.card,
                    ) {
                        Column(Modifier.padding(TteumInsets.card)) {
                            BoxWithConstraints {
                                val largeText = LocalDensity.current.fontScale > 1.35f
                                if (largeText || maxWidth < 300.dp) {
                                    Column(verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap)) {
                                        DetailDecisionMetric(
                                            label = "계산 당시 최대 체류",
                                            value = compactMaximumStayLabel(recommendation),
                                        )
                                        DetailDecisionMetric(
                                            label = "경유지에서 출발 권장",
                                            value = if (needsRecheck) "다시 확인 필요" else latestDepartureTimeLabel(recommendation),
                                            emphasized = true,
                                        )
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(TteumSpacing.md)) {
                                        DetailDecisionMetric(
                                            label = "계산 당시 최대 체류",
                                            value = compactMaximumStayLabel(recommendation),
                                            modifier = Modifier.weight(1f),
                                        )
                                        DetailDecisionMetric(
                                            label = "경유지에서 출발 권장",
                                            value = if (needsRecheck) "다시 확인 필요" else latestDepartureTimeLabel(recommendation),
                                            emphasized = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(TteumSpacing.md))
                            HorizontalDivider(color = Color(0x22E5003C))
                            Spacer(Modifier.height(TteumSpacing.sm))
                            Text(
                                "장소까지 ${readableDuration(place.firstLegMinutes)} · 추가 이동 +${place.detourMinutes}분",
                                color = Color(0xFF4F535B),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.height(TteumSpacing.xxs))
                            Text(
                                detailCalculationCaption(calculatedAtEpochMillis),
                                color = TteumMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            detailRouteCaption(criteria)?.let { routeCaption ->
                                Text(routeCaption, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (visitInfo.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        Text("방문 전 확인", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(TteumSpacing.itemGap))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = TteumBackground,
                            shape = TteumShapes.card,
                        ) {
                            Column(Modifier.padding(horizontal = TteumSpacing.cardPadding, vertical = TteumSpacing.xxs)) {
                                visitInfo.forEachIndexed { index, fact ->
                                    VisitInfo(fact.label, fact.value)
                                    if (index != visitInfo.lastIndex) {
                                        HorizontalDivider(color = Color(0xFFE5E7EA))
                                    }
                                }
                            }
                        }
                    }

                    if (additionalDetails.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        Text("추가 안내", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(TteumSpacing.itemGap))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = TteumBackground,
                            shape = TteumShapes.card,
                        ) {
                            Column(Modifier.padding(horizontal = TteumSpacing.cardPadding, vertical = TteumSpacing.xxs)) {
                                additionalDetails.forEachIndexed { index, (title, description) ->
                                    VisitInfo(title, description)
                                    if (index != additionalDetails.lastIndex) {
                                        HorizontalDivider(color = Color(0xFFE5E7EA))
                                    }
                                }
                            }
                        }
                    }

                    if (accessibilityDetails.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        Text(
                            "접근성 안내",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(TteumSpacing.itemGap))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = TteumBackground,
                            shape = TteumShapes.card,
                        ) {
                            Column(Modifier.padding(horizontal = TteumSpacing.cardPadding, vertical = TteumSpacing.xxs)) {
                                accessibilityDetails.forEachIndexed { index, (title, description) ->
                                    VisitInfo(title, description)
                                    if (index != accessibilityDetails.lastIndex) {
                                        HorizontalDivider(color = Color(0xFFE5E7EA))
                                    }
                                }
                            }
                        }
                    }

                    if (place.nearbyParkingLots.isNotEmpty()) {
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        com.tteumsae.app.ui.route.NearbyParkingSection(place.nearbyParkingLots)
                    }

                    overview?.let { description ->
                        Spacer(Modifier.height(TteumSpacing.sectionGap))
                        Text("장소 소개", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(TteumSpacing.itemGap))
                        Text(
                            description,
                            color = Color(0xFF464B53),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    Spacer(Modifier.height(TteumSpacing.sectionGap))
                    when {
                        metadataLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = TteumRed,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(TteumSpacing.inlineGap))
                            Text("최신 장소 정보를 확인하고 있어요", color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        metadataLoadFailed -> Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = TteumBackground,
                            shape = TteumShapes.compact,
                        ) {
                            Row(
                                modifier = Modifier.padding(TteumInsets.row),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "경로 검색 당시 정보를 표시 중이에요.",
                                    modifier = Modifier.weight(1f),
                                    color = TteumMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(
                                    onClick = onRetryMetadata,
                                    shape = TteumShapes.control,
                                    contentPadding = TteumInsets.button,
                                ) { Text("다시 시도", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                    Spacer(Modifier.height(TteumSpacing.sm))
                    Text(
                        placeSourceCaption(place),
                        color = TteumMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (warning.isNotBlank()) {
                        Spacer(Modifier.height(TteumSpacing.itemGap))
                        Text(warning, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun DetailDecisionMetric(
    label: String,
    value: String,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, color = Color(0xFF6C727C), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(TteumSpacing.xxs))
        Text(
            value,
            color = if (emphasized) TteumRed else TteumInk,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private fun openExternalDetailUri(context: Context, uri: Uri, failureMessage: String) {
    runCatching {
        val action = if (uri.scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW
        context.startActivity(Intent(action, uri))
    }.onFailure {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DetailMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = TteumBackground, shape = TteumShapes.compact) {
        Row(
            Modifier
                .heightIn(min = TteumSizing.buttonHeight)
                .padding(TteumInsets.row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = TteumRed, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(TteumSpacing.inlineGap))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun VisitInfo(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = TteumSpacing.rowVertical),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.width(84.dp),
            color = TteumMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun detailCalculationCaption(epochMillis: Long?): String {
    if (epochMillis == null) return "현재 교통 상황을 기준으로 계산했어요"
    val time = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("HH:mm"))
    return "$time 현재 교통 상황을 기준으로 계산했어요"
}

private fun detailRouteCaption(criteria: SearchCriteria): String? {
    val deadline = criteria.arrivalDeadlineEpochMillis ?: return null
    val formatted = Instant.ofEpochMilli(deadline)
        .atZone(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
    return "${criteria.endName} · $formatted 도착 기준"
}

@Composable
private fun SearchBar(
    text: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (supportingText == null) 58.dp else 66.dp)
            .clickable(
                enabled = onClick != null,
                role = Role.Button,
                onClick = { onClick?.invoke() },
            ),
        color = Color.White,
        shape = TteumShapes.control,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(TteumInsets.field),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(TteumSpacing.sm))
            Column {
                Text(
                    text,
                    color = TteumInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                supportingText?.let {
                    Text(
                        it,
                        color = TteumMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoundMapButton(
    onClick: (() -> Unit)?,
    icon: @Composable () -> Unit,
    contentDescription: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    foreground: Color = TteumInk,
    border: BorderStroke? = null,
) {
    Surface(
        modifier = modifier
            .size(52.dp)
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = when {
                    onClick == null -> "위치 확인 중"
                    selected -> "현재 위치 표시됨"
                    else -> "현재 위치 확인 전"
                }
            }
            .clickable(
                enabled = onClick != null,
                role = Role.Button,
                onClick = { onClick?.invoke() },
            ),
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
private fun MapBackground(
    modifier: Modifier = Modifier,
    requestedLocation: RequestedMapLocation? = null,
    centerRequestedLocation: Boolean = true,
    cameraTarget: RequestedMapLocation? = null,
    logoBottomMargin: Dp? = null,
) {
    KakaoMapSurface(
        modifier = modifier,
        latitude = 37.7645,
        longitude = 128.8996,
        zoomLevel = 13,
        requestedLocation = requestedLocation,
        centerRequestedLocation = centerRequestedLocation,
        cameraTarget = cameraTarget,
        logoBottomMargin = logoBottomMargin,
        routeStops = listOfNotNull(
            cameraTarget?.let { target ->
                "목적지" to Coordinates(target.latitude, target.longitude)
            },
        ),
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
internal fun RouteMap(
    modifier: Modifier = Modifier,
    criteria: SearchCriteria,
    recommendation: SafeRecommendation?,
    routeSummary: RouteSummary? = null,
    candidates: List<SafeRecommendation> = emptyList(),
    selectedIds: List<String> = emptyList(),
    focusedPlaceId: String? = null,
    corridorRadiusMeters: Int = 0,
    requestedLocation: RequestedMapLocation? = null,
    centerRequestedLocation: Boolean = false,
    overviewRequestId: Int = 0,
    mapBottomPadding: Dp = 300.dp,
    mapTopPadding: Dp = 128.dp,
    onMapInteraction: () -> Unit = {},
    onCandidateClick: (String) -> Unit = {},
    onClusterClick: (List<String>) -> Unit = {},
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
        criteria.startCoordinates?.let { add("출발지" to it) }
        if (criteria.mode == SearchMode.ON_THE_WAY) {
            criteria.endCoordinates?.let { add("목적지" to it) }
        }
    }
    val mapCandidates = candidates.mapNotNull { candidate ->
        candidate.place.latitude?.let { latitude ->
            candidate.place.longitude?.let { longitude ->
                RouteMapCandidate(
                    id = candidate.place.id,
                    name = candidate.place.name,
                    category = candidate.place.category,
                    coordinates = Coordinates(latitude, longitude),
                    detourMinutes = candidate.place.detourMinutes,
                    selectedOrder = selectedIds.indexOf(candidate.place.id).takeIf { it >= 0 }?.plus(1),
                    isFocused = candidate.place.id == focusedPlaceId,
                )
            }
        }
    }
    val routePoints = routeSummary?.path?.ifEmpty { fallbackPoints }
        ?: recommendation?.routePoints?.ifEmpty { fallbackPoints }
        ?: fallbackPoints
    KakaoMapSurface(
        modifier = modifier,
        latitude = waypoint?.latitude ?: criteria.endCoordinates?.latitude ?: 37.7645,
        longitude = waypoint?.longitude ?: criteria.endCoordinates?.longitude ?: 128.8996,
        zoomLevel = 15,
        preserveCameraState = true,
        requestedLocation = requestedLocation,
        centerRequestedLocation = centerRequestedLocation,
        routePoints = routePoints,
        routeStops = routeStops,
        candidateMarkers = mapCandidates,
        corridorPoints = routePoints,
        corridorRadiusMeters = corridorRadiusMeters.takeIf { it > 0 }
            ?: (criteria.deadlineMinutesFromNow * 20).coerceIn(800, 8_000),
        overviewRequestId = overviewRequestId,
        mapBottomPadding = mapBottomPadding,
        mapTopPadding = mapTopPadding,
        onMapInteraction = onMapInteraction,
        onCandidateClick = onCandidateClick,
        onClusterClick = onClusterClick,
    )
}

private val RouteCameraSnapshotSaver = listSaver<RouteMapCameraSnapshot?, Double>(
    save = { it?.savedValues().orEmpty() },
    restore = ::restoredRouteMapCamera,
)

@Composable
private fun KakaoMapSurface(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoomLevel: Int,
    preserveCameraState: Boolean = false,
    requestedLocation: RequestedMapLocation? = null,
    centerRequestedLocation: Boolean = true,
    cameraTarget: RequestedMapLocation? = null,
    overviewRequestId: Int = 0,
    routePoints: List<Coordinates> = emptyList(),
    routeStops: List<Pair<String, Coordinates>> = emptyList(),
    candidateMarkers: List<RouteMapCandidate> = emptyList(),
    corridorPoints: List<Coordinates> = emptyList(),
    corridorRadiusMeters: Int = 0,
    mapBottomPadding: Dp = 300.dp,
    mapTopPadding: Dp = 128.dp,
    logoBottomMargin: Dp? = null,
    onMapInteraction: () -> Unit = {},
    onCandidateClick: (String) -> Unit = {},
    onClusterClick: (List<String>) -> Unit = {},
) {
    if (BuildConfig.KAKAO_MAP_NATIVE_APP_KEY.isBlank()) {
        Box(modifier) {
            MapPlaceholder(Modifier.fillMaxSize())
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(TteumSpacing.cardPadding),
                color = Color.White.copy(alpha = 0.92f),
                shape = TteumShapes.card,
            ) {
                Text(
                    "카카오맵 앱 키 설정이 필요합니다.",
                    modifier = Modifier.padding(TteumInsets.row),
                    color = TteumMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapAttempt by remember { mutableIntStateOf(0) }
    var savedRouteCamera by rememberSaveable(stateSaver = RouteCameraSnapshotSaver) {
        mutableStateOf<RouteMapCameraSnapshot?>(null)
    }
    val restoredCameraForMap = remember(mapAttempt) { savedRouteCamera.takeIf { preserveCameraState } }
    var suppressInitialCamera by remember(mapAttempt) { mutableStateOf(restoredCameraForMap != null) }
    val mapView = remember(mapAttempt) { MapView(context) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var mapContentReady by remember(mapAttempt) { mutableStateOf(false) }
    var currentLocationLabel by remember { mutableStateOf<Label?>(null) }
    var currentZoomLevel by remember { mutableIntStateOf(restoredCameraForMap?.zoomLevel ?: zoomLevel) }
    var clusterDistanceDp by remember {
        mutableFloatStateOf(CLUSTER_DISTANCE_NEUTRAL_DP.toFloat())
    }
    var revealedClusterMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingClusterFocus by remember { mutableStateOf<RouteMapLabelTarget.Cluster?>(null) }
    val currentOnMapInteraction by rememberUpdatedState(onMapInteraction)
    val currentOnCandidateClick by rememberUpdatedState(onCandidateClick)
    val currentOnClusterClick by rememberUpdatedState(onClusterClick)
    val latestZoomLevel by rememberUpdatedState(currentZoomLevel)
    val latestClusterDistanceDp by rememberUpdatedState(clusterDistanceDp)
    val density = LocalDensity.current
    val logoBottomMarginPixels = with(density) { logoBottomMargin?.toPx() }
    val logoEndMarginPixels = with(density) { 12.dp.toPx() }
    val hasRouteCandidates = candidateMarkers.isNotEmpty()
    val mapHorizontalPaddingPixels = with(density) {
        if (hasRouteCandidates) 72.dp.toPx().toInt() else 0
    }
    val mapTopPaddingPixels = with(density) {
        if (hasRouteCandidates) mapTopPadding.toPx().toInt() else 0
    }
    val mapBottomPaddingPixels = with(density) {
        if (hasRouteCandidates) mapBottomPadding.toPx().toInt() else 0
    }
    val latestMapViewportPadding by rememberUpdatedState(
        Triple(mapHorizontalPaddingPixels, mapTopPaddingPixels, mapBottomPaddingPixels),
    )
    val clusterFitPaddingPixels = with(density) { 32.dp.toPx().toInt() }
    val effectiveClusterDistanceDp = if (revealedClusterMemberIds.isEmpty()) {
        clusterDistanceDp
    } else {
        minOf(clusterDistanceDp, 28f)
    }
    val clusterDistancePixels = with(density) {
        effectiveClusterDistanceDp.dp.toPx().toDouble()
    }
    val highlightedCandidateMarkers = candidateMarkers.filter {
        it.isFocused || it.selectedOrder != null
    }
    val selectedCandidate = highlightedCandidateMarkers.firstOrNull { it.selectedOrder != null }
    val normalCandidateMarkers = if (selectedCandidate == null) {
        candidateMarkers
            .filter { it.selectedOrder == null }
            .map { it.copy(isFocused = false) }
    } else {
        // Once a stop is chosen, keep the map focused on the selected route.
        // Alternatives remain immediately available in the result sheet.
        emptyList()
    }
    val clusteredNormalMarkers = remember(
        normalCandidateMarkers,
        currentZoomLevel,
        clusterDistancePixels,
    ) {
        clusterRouteCandidates(
            candidates = normalCandidateMarkers,
            zoomLevel = currentZoomLevel,
            clusterDistancePixels = clusterDistancePixels,
        )
    }
    LaunchedEffect(kakaoMap, mapAttempt) {
        mapContentReady = false
        if (kakaoMap != null) {
            // KakaoMapReady is emitted before every raster tile is painted. Keep
            // the branded map skeleton visible briefly so users never see a
            // half-painted mint/blank map during screen transitions.
            delay(1_200)
            mapContentReady = true
        }
    }

    LaunchedEffect(kakaoMap) {
        if (kakaoMap != null && suppressInitialCamera) {
            // Only skip the effects of creating this map. Later explicit selection
            // and overview requests still move it normally.
            withFrameNanos { }
            suppressInitialCamera = false
        }
    }

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
                    logoBottomMarginPixels?.let { bottom ->
                        readyMap.logo?.setPosition(
                            MapGravity.BOTTOM or MapGravity.RIGHT,
                            logoEndMarginPixels,
                            bottom,
                        )
                    }
                    readyMap.setPadding(
                        mapHorizontalPaddingPixels,
                        mapTopPaddingPixels,
                        mapHorizontalPaddingPixels,
                        mapBottomPaddingPixels,
                    )
                    kakaoMap = readyMap
                }

                override fun getPosition(): LatLng =
                    restoredCameraForMap?.let { LatLng.from(it.latitude, it.longitude) }
                        ?: LatLng.from(latitude, longitude)

                override fun getZoomLevel(): Int = restoredCameraForMap?.zoomLevel ?: zoomLevel
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

    LaunchedEffect(kakaoMap, requestedLocation?.requestId, centerRequestedLocation) {
        val map = kakaoMap ?: return@LaunchedEffect
        val target = requestedLocation
        if (target == null) {
            currentLocationLabel?.remove()
            currentLocationLabel = null
            return@LaunchedEffect
        }
        val position = LatLng.from(target.latitude, target.longitude)

        if (centerRequestedLocation) {
            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(position, 16),
                CameraAnimation.from(500),
            )
        }

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

    LaunchedEffect(
        kakaoMap,
        mapHorizontalPaddingPixels,
        mapTopPaddingPixels,
        mapBottomPaddingPixels,
    ) {
        kakaoMap?.setPadding(
            mapHorizontalPaddingPixels,
            mapTopPaddingPixels,
            mapHorizontalPaddingPixels,
            mapBottomPaddingPixels,
        )
    }

    LaunchedEffect(kakaoMap, pendingClusterFocus) {
        val map = kakaoMap ?: return@LaunchedEffect
        val target = pendingClusterFocus ?: return@LaunchedEffect
        // Let the parent apply the requested sheet viewport and measure its header.
        // Only an explicit cluster request fits the camera, not later sheet drags.
        withFrameNanos { }
        if (pendingClusterFocus != target) return@LaunchedEffect
        val (horizontal, top, bottom) = latestMapViewportPadding
        map.setPadding(horizontal, top, horizontal, bottom)
        map.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                target.coordinates.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray(),
                clusterFitPaddingPixels,
                target.expansionZoom,
            ),
            CameraAnimation.from(550),
        )
        pendingClusterFocus = null
    }

    // Move only the SDK attribution, never the camera viewport. The expanded
    // search form is a temporary overlay; its collapsed map must show the logo.
    LaunchedEffect(kakaoMap, logoBottomMarginPixels, logoEndMarginPixels) {
        logoBottomMarginPixels?.let { bottom ->
            kakaoMap?.logo?.setPosition(
                MapGravity.BOTTOM or MapGravity.RIGHT,
                logoEndMarginPixels,
                bottom,
            )
        }
    }

    LaunchedEffect(kakaoMap, cameraTarget?.requestId) {
        val map = kakaoMap ?: return@LaunchedEffect
        val target = cameraTarget ?: return@LaunchedEffect
        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(target.latitude, target.longitude),
                currentZoomLevel.coerceAtLeast(15),
            ),
            CameraAnimation.from(550),
        )
    }

    DisposableEffect(kakaoMap) {
        val map = kakaoMap
        if (map == null) {
            onDispose { }
        } else {
            map.setOnMapClickListener { _, _, _, _ ->
                pendingClusterFocus = null
                revealedClusterMemberIds = emptySet()
                currentOnMapInteraction()
            }
            map.setOnCameraMoveStartListener { _, gesture ->
                if (gesture != GestureType.Unknown) {
                    pendingClusterFocus = null
                    revealedClusterMemberIds = emptySet()
                    currentOnMapInteraction()
                }
            }
            map.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                val newZoomLevel = cameraPosition.zoomLevel
                if (preserveCameraState) {
                    savedRouteCamera = RouteMapCameraSnapshot(
                        cameraPosition.position.latitude,
                        cameraPosition.position.longitude,
                        newZoomLevel,
                    )
                }
                clusterDistanceDp = clusterDistanceDpAfterZoom(
                    previousZoomLevel = latestZoomLevel,
                    newZoomLevel = newZoomLevel,
                    currentDistanceDp = latestClusterDistanceDp.toDouble(),
                ).toFloat()
                currentZoomLevel = newZoomLevel
            }
            map.setOnLabelClickListener { _, _, label ->
                when (val target = label.tag) {
                    is RouteMapLabelTarget.Candidate -> {
                        pendingClusterFocus = null
                        revealedClusterMemberIds = emptySet()
                        currentOnCandidateClick(target.id)
                        true
                    }
                    is RouteMapLabelTarget.Cluster -> {
                        revealedClusterMemberIds = target.memberIds.toSet()
                        currentOnClusterClick(target.memberIds)
                        pendingClusterFocus = target
                        true
                    }
                    else -> false
                }
            }
            onDispose {
                map.setOnMapClickListener(null)
                map.setOnCameraMoveStartListener(null)
                map.setOnCameraMoveEndListener(null)
                map.setOnLabelClickListener(null)
            }
        }
    }

    DisposableEffect(
        kakaoMap,
        routePoints,
        routeStops,
        corridorPoints,
        corridorRadiusMeters,
    ) {
        val map = kakaoMap
        if (map == null) {
            onDispose { }
        } else {
            val routeLine = if (routePoints.size >= 2) {
                val points = routePoints.map { LatLng.from(it.latitude, it.longitude) }
                map.routeLineManager?.layer?.addRouteLine(
                    RouteLineOptions.from(
                        RouteLineSegment.from(
                            points,
                            RouteLineStyle.from(
                                8f,
                                TteumRed.copy(alpha = 0.80f).toArgb(),
                                2f,
                                Color.White.toArgb(),
                            ),
                        ),
                    ),
                )
            } else {
                null
            }
            val labels = routeStops.mapIndexedNotNull { index, (name, coordinates) ->
                map.labelManager?.layer?.addLabel(
                    LabelOptions.from(
                        "route-stop-$index",
                        LatLng.from(coordinates.latitude, coordinates.longitude),
                    )
                        .setStyles(
                            LabelStyle.from(createRouteStopBitmap(context, name))
                                .setAnchorPoint(0.5f, 1f),
                        ),
                )
            }
            val corridorPolygons = if (routePoints.size >= 2 && corridorRadiusMeters > 0) {
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
                                PolygonStyles.from(TteumRed.copy(alpha = 0.003f).toArgb()),
                            ),
                        ),
                    )
                }
            } else {
                emptyList()
            }

            onDispose {
                routeLine?.remove()
                labels.forEach(Label::remove)
                corridorPolygons.forEach { it.remove() }
            }
        }
    }

    DisposableEffect(
        kakaoMap,
        clusteredNormalMarkers,
        currentZoomLevel,
        clusterDistancePixels,
    ) {
        val map = kakaoMap
        val labelManager = map?.labelManager
        if (map == null || labelManager == null) {
            onDispose { }
        } else {
            val normalLayer = labelManager.addLayer(
                LabelLayerOptions.from("tteumsae-route-candidates")
                    .setCompetitionType(CompetitionType.Same)
                    .setCompetitionUnit(CompetitionUnit.IconAndText)
                    .setOrderingType(OrderingType.Rank)
                    .setZOrder(5_000)
                    .setClickable(true),
            )
            val candidateLabels = clusteredNormalMarkers.mapIndexedNotNull { index, item ->
                when (item) {
                    is RouteMapMarkerItem.Single -> {
                        val candidate = item.candidate
                        normalLayer?.addLabel(
                            LabelOptions.from(
                                "candidate-${candidate.id}",
                                LatLng.from(
                                    candidate.coordinates.latitude,
                                    candidate.coordinates.longitude,
                                ),
                            )
                                .setStyles(
                                    LabelStyle.from(
                                        createCandidateMarkerBitmap(
                                            context,
                                            candidate.category,
                                            selectedOrder = null,
                                            isFocused = false,
                                        ),
                                    ).setAnchorPoint(0.5f, 1f),
                                )
                                .setRank((clusteredNormalMarkers.size - index).toLong())
                                .setTag(RouteMapLabelTarget.Candidate(candidate.id))
                                .setClickable(true),
                        )
                    }
                    is RouteMapMarkerItem.Cluster -> normalLayer?.addLabel(
                        LabelOptions.from(
                            item.id,
                            LatLng.from(item.center.latitude, item.center.longitude),
                        )
                            .setStyles(
                                LabelStyle.from(
                                    createClusterMarkerBitmap(context, item.members),
                                ).setAnchorPoint(0.5f, 1f),
                            )
                            .setRank(10_000L + item.members.size)
                            .setTag(
                                RouteMapLabelTarget.Cluster(
                                    memberIds = item.members.map(RouteMapCandidate::id),
                                    coordinates = item.members.map(RouteMapCandidate::coordinates),
                                    expansionZoom = meaningfulClusterExpansionZoom(
                                        members = item.members,
                                        currentZoomLevel = currentZoomLevel,
                                        clusterDistancePixels = clusterDistancePixels,
                                    ),
                                ),
                            )
                            .setClickable(true),
                    )
                }
            }

            onDispose {
                candidateLabels.forEach(Label::remove)
                normalLayer?.let(labelManager::remove)
            }
        }
    }

    DisposableEffect(kakaoMap, highlightedCandidateMarkers) {
        val map = kakaoMap
        val labelManager = map?.labelManager
        if (map == null || labelManager == null) {
            onDispose { }
        } else {
            val highlightedLayer = labelManager.addLayer(
                LabelLayerOptions.from("tteumsae-route-highlights")
                    .setCompetitionType(CompetitionType.None)
                    .setOrderingType(OrderingType.Rank)
                    .setZOrder(5_100)
                    .setClickable(true),
            )
            val candidateLabels = highlightedCandidateMarkers.mapNotNull { candidate ->
                val rank = if (candidate.isFocused) {
                    30_000L
                } else {
                    20_000L - (candidate.selectedOrder ?: 0)
                }
                highlightedLayer?.addLabel(
                    LabelOptions.from(
                        "highlight-${candidate.id}",
                        LatLng.from(candidate.coordinates.latitude, candidate.coordinates.longitude),
                    )
                        .setStyles(
                            LabelStyle.from(
                                createCandidateMarkerBitmap(
                                    context,
                                    candidate.category,
                                    candidate.selectedOrder,
                                    candidate.isFocused,
                                ),
                            ).setAnchorPoint(0.5f, 1f),
                        )
                        .setRank(rank)
                        .setTag(RouteMapLabelTarget.Candidate(candidate.id))
                        .setClickable(true),
                )
            }

            onDispose {
                candidateLabels.forEach(Label::remove)
                highlightedLayer?.let(labelManager::remove)
            }
        }
    }

    LaunchedEffect(
        kakaoMap,
        routePoints,
        requestedLocation?.requestId,
        centerRequestedLocation,
        selectedCandidate?.id,
    ) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (
            !suppressInitialCamera &&
            selectedCandidate == null &&
            (!centerRequestedLocation || requestedLocation == null) &&
            routePoints.size >= 2
        ) {
            val points = routePoints.map { LatLng.from(it.latitude, it.longitude) }
            map.moveCamera(
                CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 140),
                CameraAnimation.from(500),
            )
        }
    }

    LaunchedEffect(kakaoMap, selectedCandidate?.id) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (suppressInitialCamera) return@LaunchedEffect
        val candidate = selectedCandidate ?: return@LaunchedEffect
        pendingClusterFocus = null
        map.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(candidate.coordinates.latitude, candidate.coordinates.longitude),
                maxOf(latestZoomLevel, 15),
            ),
            CameraAnimation.from(600),
        )
    }

    LaunchedEffect(kakaoMap, overviewRequestId) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (suppressInitialCamera) return@LaunchedEffect
        if (overviewRequestId <= 0 || routePoints.size < 2) return@LaunchedEffect
        pendingClusterFocus = null
        map.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                routePoints.map { LatLng.from(it.latitude, it.longitude) }.toTypedArray(),
                140,
            ),
            CameraAnimation.from(550),
        )
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = !mapContentReady && mapError == null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(260)),
        ) {
            Box(Modifier.fillMaxSize()) {
                MapPlaceholder(Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(TteumSpacing.cardPadding),
                    color = Color.White.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(TteumInsets.row),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = TteumRed,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(TteumSpacing.inlineGap))
                        Text("지도를 불러오고 있어요", color = TteumInk, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        mapError?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(TteumSpacing.cardPadding),
                color = Color.White.copy(alpha = 0.94f),
                shape = TteumShapes.card,
            ) {
                Column(
                    modifier = Modifier.padding(TteumInsets.card),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                    TextButton(
                        shape = TteumShapes.control,
                        contentPadding = TteumInsets.button,
                        onClick = {
                            mapError = null
                            mapAttempt += 1
                        },
                    ) {
                        Text("지도 다시 시도", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun createRouteStopBitmap(context: Context, text: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = (34f * density).roundToInt()
    val height = (34f * density).roundToInt()
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val isStart = text.startsWith("출발")
        val markerColor = if (isStart) 0xFF5F6570.toInt() else TteumRed.toArgb()
        val background = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        val border = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = markerColor
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        val center = width / 2f
        val radius = 14f * density
        canvas.drawCircle(center, center, radius, background)
        canvas.drawCircle(center, center, radius, border)

        val flagPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = markerColor
            style = android.graphics.Paint.Style.FILL
            strokeWidth = 2.2f * density
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        val poleX = 13f * density
        canvas.drawLine(poleX, 9f * density, poleX, 25f * density, flagPaint)
        val flag = android.graphics.Path().apply {
            moveTo(poleX, 9f * density)
            lineTo(24f * density, 12f * density)
            lineTo(poleX, 17f * density)
            close()
        }
        canvas.drawPath(flag, flagPaint)
    }
}

private fun createClusterMarkerBitmap(
    context: Context,
    members: List<RouteMapCandidate>,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = 68f * density
    val height = 50f * density
    return Bitmap.createBitmap(
        width.roundToInt(),
        height.roundToInt(),
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        val bodyBottom = 42f * density
        val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            setShadowLayer(3f * density, 0f, density, 0x33000000)
        }
        val outline = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = RouteFocusBlue.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        val tail = android.graphics.Path().apply {
            moveTo(29f * density, 38f * density)
            lineTo(39f * density, 38f * density)
            lineTo(width / 2f, height - density)
            close()
        }
        canvas.drawPath(tail, fill)
        canvas.drawRoundRect(
            2f * density,
            2f * density,
            width - 2f * density,
            bodyBottom,
            20f * density,
            20f * density,
            fill,
        )
        canvas.drawPath(tail, outline)
        canvas.drawRoundRect(
            2f * density,
            2f * density,
            width - 2f * density,
            bodyBottom,
            20f * density,
            20f * density,
            outline,
        )

        val categories = representativeClusterCategories(members)
        categories.forEachIndexed { index, category ->
            val centerX = (18f + index * 12f) * density
            val badge = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = categoryMarkerColor(category)
            }
            canvas.drawCircle(centerX, 21f * density, 9f * density, badge)
            drawCategoryGlyph(
                canvas = canvas,
                category = category,
                centerX = centerX,
                centerY = 21f * density,
                size = 11f * density,
                color = android.graphics.Color.WHITE,
            )
        }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = RouteFocusBlue.toArgb()
            textSize = 15f * density
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val textCenterX = if (categories.size > 1) 51f * density else 47f * density
        val baseline = 21f * density - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(members.size.toString(), textCenterX, baseline, textPaint)
    }
}

private fun createCandidateMarkerBitmap(
    context: Context,
    category: PlaceCategory,
    selectedOrder: Int?,
    isFocused: Boolean,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val isSelected = selectedOrder != null
    val widthDp = if (isSelected || isFocused) 50f else 46f
    val heightDp = if (isSelected || isFocused) 56f else 52f
    val width = widthDp * density
    val height = heightDp * density
    val centerX = width / 2f
    val centerY = (if (isSelected || isFocused) 23f else 21f) * density
    val outerRadius = (if (isSelected || isFocused) 21f else 19f) * density
    val markerColor = if (isSelected) TteumRed.toArgb() else categoryMarkerColor(category)
    return Bitmap.createBitmap(
        width.roundToInt(),
        height.roundToInt(),
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        val canvas = android.graphics.Canvas(bitmap)
        if (isSelected || isFocused) {
            val halo = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isSelected) 0x22E5003C else 0x222F6FE4
            }
            canvas.drawCircle(centerX, centerY, outerRadius + 3f * density, halo)
        }
        val outer = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = markerColor
            setShadowLayer(2.5f * density, 0f, density, 0x33000000)
        }
        val tailHalfWidth = 8f * density
        val tailStartY = centerY + 12f * density
        val tail = android.graphics.Path().apply {
            moveTo(centerX - tailHalfWidth, tailStartY)
            lineTo(centerX + tailHalfWidth, tailStartY)
            lineTo(centerX, height - density)
            close()
        }
        canvas.drawPath(tail, outer)
        canvas.drawCircle(centerX, centerY, outerRadius, outer)
        val inner = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawCircle(centerX, centerY, outerRadius - 4f * density, inner)
        drawCategoryGlyph(
            canvas = canvas,
            category = category,
            centerX = centerX,
            centerY = centerY,
            size = 19f * density,
            color = markerColor,
        )
    }
}

private fun categoryMarkerColor(category: PlaceCategory): Int = when (category) {
    PlaceCategory.ATTRACTION -> 0xFF2F7D5B.toInt()
    PlaceCategory.RESTAURANT -> 0xFFE06B2E.toInt()
    PlaceCategory.CAFE -> 0xFF8A5A44.toInt()
    PlaceCategory.CULTURE -> 0xFF7056B8.toInt()
    PlaceCategory.FESTIVAL -> 0xFFC74375.toInt()
    PlaceCategory.SHOPPING -> 0xFF147C83.toInt()
    PlaceCategory.LEISURE -> RouteFocusBlue.toArgb()
}

private fun drawCategoryGlyph(
    canvas: android.graphics.Canvas,
    category: PlaceCategory,
    centerX: Float,
    centerY: Float,
    size: Float,
    color: Int,
) {
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = (size * 0.11f).coerceAtLeast(1f)
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val fill = android.graphics.Paint(stroke).apply { style = android.graphics.Paint.Style.FILL }
    val left = centerX - size / 2f
    val top = centerY - size / 2f
    when (category) {
        PlaceCategory.ATTRACTION -> {
            val mountain = android.graphics.Path().apply {
                moveTo(left, top + size * 0.80f)
                lineTo(left + size * 0.36f, top + size * 0.36f)
                lineTo(left + size * 0.55f, top + size * 0.58f)
                lineTo(left + size * 0.72f, top + size * 0.32f)
                lineTo(left + size, top + size * 0.80f)
            }
            canvas.drawPath(mountain, stroke)
            canvas.drawCircle(left + size * 0.78f, top + size * 0.20f, size * 0.08f, fill)
        }
        PlaceCategory.RESTAURANT -> {
            val forkX = left + size * 0.30f
            canvas.drawLine(forkX, top + size * 0.14f, forkX, top + size * 0.88f, stroke)
            listOf(0.20f, 0.30f, 0.40f).forEach { x ->
                canvas.drawLine(
                    left + size * x,
                    top + size * 0.12f,
                    left + size * x,
                    top + size * 0.38f,
                    stroke,
                )
            }
            canvas.drawLine(left + size * 0.20f, top + size * 0.38f, left + size * 0.40f, top + size * 0.38f, stroke)
            val spoonX = left + size * 0.72f
            canvas.drawCircle(spoonX, top + size * 0.26f, size * 0.13f, stroke)
            canvas.drawLine(spoonX, top + size * 0.39f, spoonX, top + size * 0.88f, stroke)
        }
        PlaceCategory.CAFE -> {
            canvas.drawRoundRect(
                left + size * 0.16f,
                top + size * 0.30f,
                left + size * 0.72f,
                top + size * 0.72f,
                size * 0.06f,
                size * 0.06f,
                stroke,
            )
            canvas.drawArc(
                left + size * 0.64f,
                top + size * 0.38f,
                left + size * 0.95f,
                top + size * 0.66f,
                -90f,
                180f,
                false,
                stroke,
            )
            canvas.drawLine(left + size * 0.10f, top + size * 0.82f, left + size * 0.86f, top + size * 0.82f, stroke)
        }
        PlaceCategory.CULTURE -> {
            val roof = android.graphics.Path().apply {
                moveTo(left + size * 0.08f, top + size * 0.34f)
                lineTo(centerX, top + size * 0.10f)
                lineTo(left + size * 0.92f, top + size * 0.34f)
                close()
            }
            canvas.drawPath(roof, fill)
            listOf(0.25f, 0.50f, 0.75f).forEach { x ->
                canvas.drawLine(left + size * x, top + size * 0.42f, left + size * x, top + size * 0.78f, stroke)
            }
            canvas.drawLine(left + size * 0.12f, top + size * 0.82f, left + size * 0.88f, top + size * 0.82f, stroke)
        }
        PlaceCategory.FESTIVAL -> {
            val star = android.graphics.Path()
            repeat(10) { index ->
                val angle = Math.toRadians((-90 + index * 36).toDouble())
                val radius = if (index % 2 == 0) size * 0.43f else size * 0.19f
                val x = centerX + kotlin.math.cos(angle).toFloat() * radius
                val y = centerY + kotlin.math.sin(angle).toFloat() * radius
                if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            canvas.drawPath(star, fill)
        }
        PlaceCategory.SHOPPING -> {
            canvas.drawRoundRect(
                left + size * 0.16f,
                top + size * 0.32f,
                left + size * 0.84f,
                top + size * 0.86f,
                size * 0.08f,
                size * 0.08f,
                stroke,
            )
            canvas.drawArc(
                left + size * 0.32f,
                top + size * 0.10f,
                left + size * 0.68f,
                top + size * 0.50f,
                180f,
                180f,
                false,
                stroke,
            )
        }
        PlaceCategory.LEISURE -> {
            canvas.drawCircle(left + size * 0.65f, top + size * 0.18f, size * 0.10f, fill)
            canvas.drawLine(left + size * 0.58f, top + size * 0.34f, left + size * 0.43f, top + size * 0.58f, stroke)
            canvas.drawLine(left + size * 0.44f, top + size * 0.48f, left + size * 0.22f, top + size * 0.42f, stroke)
            canvas.drawLine(left + size * 0.47f, top + size * 0.55f, left + size * 0.72f, top + size * 0.65f, stroke)
            canvas.drawLine(left + size * 0.43f, top + size * 0.59f, left + size * 0.25f, top + size * 0.86f, stroke)
            canvas.drawLine(left + size * 0.70f, top + size * 0.65f, left + size * 0.84f, top + size * 0.88f, stroke)
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
