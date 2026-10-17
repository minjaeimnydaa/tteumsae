package com.tteumsae.app.ui.route

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.Coordinates
import com.tteumsae.app.domain.DestinationSupport
import com.tteumsae.app.domain.LocationSearchResult
import com.tteumsae.app.domain.LocationSearchResultType
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.destinationSupport
import com.tteumsae.app.domain.route.RouteFlowInput
import com.tteumsae.app.domain.route.RouteLocation
import com.tteumsae.app.domain.route.isValidArrivalDeadline
import com.tteumsae.app.domain.route.resolveArrivalDeadline
import com.tteumsae.app.location.LocationAccessPolicy
import com.tteumsae.app.platform.openAppSettings
import com.tteumsae.app.ui.deniedLocationPermissionNeedsSettings
import com.tteumsae.app.ui.hasLocationPermission
import com.tteumsae.app.ui.networkFailureMessage
import com.tteumsae.app.ui.requestCurrentLocation
import com.tteumsae.app.ui.shouldAutoLocateStart
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumHandle
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal const val LOCATION_SEARCH_VISIBLE_LIMIT = 10
private const val UNSUPPORTED_DESTINATION_MESSAGE = "현재는 강원도 목적지만 추천할 수 있어요."

internal fun initialRouteStartQuery(
    selectedName: String?,
    automaticLocationEnabled: Boolean,
    hasPermission: Boolean,
): String = selectedName ?: if (automaticLocationEnabled && hasPermission) "현재 위치" else ""

internal fun canContinueRouteInput(
    input: RouteFlowInput,
    nowEpochMillis: Long,
    isBusy: Boolean,
): Boolean = !isBusy &&
    input.start != null &&
    input.destination != null &&
    input.arrivalDeadlineEpochMillis?.let {
        isValidArrivalDeadline(it, nowEpochMillis)
    } == true

internal fun routeInputGuidance(
    input: RouteFlowInput,
    nowEpochMillis: Long,
    isBusy: Boolean,
): String? = when {
    isBusy -> null
    input.start == null -> "출발지를 선택해 주세요"
    input.destination == null -> "목적지를 선택해 주세요"
    input.arrivalDeadlineEpochMillis == null -> "도착 시각을 정해 주세요"
    !isValidArrivalDeadline(input.arrivalDeadlineEpochMillis, nowEpochMillis) ->
        "도착 시각은 지금부터 15분 이후로 정해 주세요"
    else -> null
}
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
internal fun RouteLocationScreen(
    input: RouteFlowInput,
    errorMessage: String?,
    searchPlaces: suspend (String, Boolean) -> List<LocationSearchResult>,
    resolveCurrentAddress: suspend (Coordinates) -> String,
    onStartSelected: (RouteLocation?) -> Unit,
    onDestinationSelected: (RouteLocation?) -> Unit,
    onDeadlineSelected: (Long?) -> Unit,
    onFiltersChanged: (Set<PlaceCategory>) -> Unit,
    isChecking: Boolean,
    isEditing: Boolean = false,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val automaticLocationEnabled = LocationAccessPolicy.automaticLocationEnabled
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isImeVisible = WindowInsets.isImeVisible
    var startQuery by rememberSaveable {
        mutableStateOf(
            initialRouteStartQuery(
                selectedName = input.start?.name,
                automaticLocationEnabled = automaticLocationEnabled,
                hasPermission = automaticLocationEnabled && hasLocationPermission(context),
            ),
        )
    }
    var destinationQuery by rememberSaveable {
        mutableStateOf(input.destination?.name.orEmpty())
    }
    var isLocating by remember { mutableStateOf(false) }
    var cancelLocationRequest by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    val initialDeadlineClock = remember {
        Instant.ofEpochMilli(
            input.arrivalDeadlineEpochMillis
                ?: System.currentTimeMillis() + 60L * 60L * 1_000L,
        ).atZone(ZoneId.of("Asia/Seoul"))
    }
    var selectedHour by rememberSaveable { mutableIntStateOf(initialDeadlineClock.hour) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialDeadlineClock.minute) }
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(input.arrivalDeadlineEpochMillis) {
        val deadline = input.arrivalDeadlineEpochMillis ?: return@LaunchedEffect
        val clock = Instant.ofEpochMilli(deadline).atZone(ZoneId.of("Asia/Seoul"))
        selectedHour = clock.hour
        selectedMinute = clock.minute
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowEpochMillis = System.currentTimeMillis()
        }
    }

    val locateCurrentPosition: () -> Unit = locate@{
        if (!automaticLocationEnabled) return@locate
        cancelLocationRequest?.invoke()
        isLocating = true
        val cancel = requestCurrentLocation(
            context = context,
            onSuccess = { location ->
                val routeLocation = RouteLocation(
                    name = "현재 위치",
                    coordinates = Coordinates(location.latitude, location.longitude),
                )
                startQuery = routeLocation.name
                onStartSelected(routeLocation)
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
                    "현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
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
        } else if (
            deniedLocationPermissionNeedsSettings(permissions) { permission ->
                (context as? android.app.Activity)
                    ?.shouldShowRequestPermissionRationale(permission) == true
            }
        ) {
            showPermissionSettingsDialog = true
        } else {
            Toast.makeText(
                context,
                "현재 위치 대신 출발지를 직접 검색할 수 있어요.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val useCurrentLocation: () -> Unit = useCurrent@{
        if (!automaticLocationEnabled) return@useCurrent
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

    LaunchedEffect(Unit) {
        if (input.arrivalDeadlineEpochMillis == null) {
            onDeadlineSelected(
                resolveArrivalDeadline(
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    nowEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    LaunchedEffect(startQuery, input.start) {
        if (
            automaticLocationEnabled && hasLocationPermission(context) &&
            shouldAutoLocateStart(startQuery, input.start != null, automaticLocationEnabled)
        ) {
            useCurrentLocation()
        }
    }
    LaunchedEffect(input.start?.coordinates, input.start?.name) {
        if (!automaticLocationEnabled) return@LaunchedEffect
        val start = input.start ?: return@LaunchedEffect
        if (start.name != "현재 위치") return@LaunchedEffect
        val address = try {
            resolveCurrentAddress(start.coordinates)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
        if (!address.isNullOrBlank()) {
            startQuery = address
            onStartSelected(start.copy(name = address))
        }
    }

    val busy = isChecking || isLocating
    val canContinue = canContinueRouteInput(input, nowEpochMillis, busy)
    val continueGuidance = routeInputGuidance(input, nowEpochMillis, busy)
    val panelState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Expanded,
        skipHiddenState = true,
    )
    val panelScaffoldState = rememberBottomSheetScaffoldState(panelState)
    val collapsedRouteTitle = listOfNotNull(input.start?.name, input.destination?.name)
        .joinToString(" → ")
        .ifBlank { "경로를 확인해 보세요" }
    val collapsedRouteSummary = input.arrivalDeadlineEpochMillis?.let { formatArrivalDeadline(it, nowEpochMillis) }
        ?.let { "$it · $collapsedRouteTitle" }
        ?: collapsedRouteTitle

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && panelState.currentValue != SheetValue.Expanded) {
            panelState.expand()
        }
    }
    LaunchedEffect(panelState.currentValue) {
        if (panelState.currentValue == SheetValue.PartiallyExpanded) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    BackHandler(enabled = isImeVisible) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The drag-handle slot adds about 45dp outside sheetContent. Subtract it
        // together with a 37dp top reveal so the rounded edge starts below the
        // status area instead of being clamped to y=0.
        val expandedPanelHeight = maxHeight - 82.dp
        BottomSheetScaffold(
            scaffoldState = panelScaffoldState,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            sheetContainerColor = androidx.compose.ui.graphics.Color.White,
            sheetShape = TteumShapes.sheet,
            sheetShadowElevation = 16.dp,
            sheetPeekHeight = 110.dp,
            sheetDragHandle = {
                BottomSheetDefaults.DragHandle(color = TteumHandle)
            },
            sheetContent = {
                Scaffold(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(expandedPanelHeight),
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (!isImeVisible) {
                            Surface(color = androidx.compose.ui.graphics.Color.White) {
                                Column(
                                    modifier = Modifier
                                        .navigationBarsPadding()
                                        .fillMaxWidth()
                                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.sm),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    continueGuidance?.let { guidance ->
                                        Text(
                                            guidance,
                                            color = TteumMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(TteumSpacing.xs))
                                    }
                                    Button(
                                        onClick = {
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            onNext()
                                        },
                                        enabled = canContinue,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = TteumSizing.buttonHeight),
                                        shape = TteumShapes.control,
                                        contentPadding = TteumInsets.button,
                                    ) {
                                        Text(
                                            when {
                                                isChecking -> "경로 확인 중..."
                                                isEditing -> "다시 추천받기"
                                                else -> "틈새 찾기"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = TteumSpacing.sheetHorizontal, top = TteumSpacing.xs, end = TteumSpacing.sheetHorizontal, bottom = TteumSpacing.lg),
        ) {
            item {
                Row(
                    modifier = if (panelState.currentValue == SheetValue.PartiallyExpanded) {
                        Modifier.heightIn(min = 62.dp)
                    } else {
                        Modifier
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(TteumSizing.touchTarget)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "조건 수정 취소" else "뒤로",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.width(TteumSpacing.xs))
                    if (panelState.currentValue == SheetValue.PartiallyExpanded) {
                        Text(
                            collapsedRouteSummary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    } else {
                        Column {
                        Text(
                            if (isEditing) "경로 조건을 수정해요" else "어디로, 몇 시까지 가나요?",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            if (automaticLocationEnabled) {
                                "늦지 않는 선에서 들를 곳을 찾아드려요."
                            } else {
                                "선택한 출발지 기준으로 들를 곳을 찾아드려요."
                            },
                            color = TteumMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        }
                    }
                }
                if (panelState.currentValue != SheetValue.PartiallyExpanded) {
                    Spacer(Modifier.height(TteumSpacing.md))
                }
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = TteumRedSoft,
                        shape = TteumShapes.card,
                    ) {
                        Text(
                            errorMessage,
                            modifier = Modifier.padding(TteumInsets.row),
                            color = TteumRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(TteumSpacing.sm))
                }
                Surface(
                    color = TteumBackground,
                    shape = TteumShapes.card,
                ) {
                    Column {
                        RouteLocationSearchField(
                            value = startQuery,
                            selected = input.start,
                            label = "출발지",
                            labelBackground = TteumMuted,
                            searchPlaces = searchPlaces,
                            gangwonOnly = false,
                            onValueChange = {
                                startQuery = it
                                onStartSelected(null)
                            },
                            onSelected = {
                                startQuery = it.name
                                onStartSelected(RouteLocation(it.name, it.coordinates))
                            },
                            onUseCurrentLocation = useCurrentLocation.takeIf { automaticLocationEnabled },
                            isLocating = isLocating,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = TteumSpacing.cardPadding),
                            color = androidx.compose.ui.graphics.Color(0xFFDDE0E5),
                        )
                        RouteLocationSearchField(
                            value = destinationQuery,
                            selected = input.destination,
                            label = "목적지",
                            labelBackground = TteumInk,
                            searchPlaces = searchPlaces,
                            gangwonOnly = true,
                            onValueChange = {
                                destinationQuery = it
                                onDestinationSelected(null)
                            },
                            onSelected = {
                                destinationQuery = it.name
                                onDestinationSelected(RouteLocation(it.name, it.coordinates))
                            },
                            autoFocus = input.destination == null && input.start != null,
                        )
                    }
                }

                Spacer(Modifier.height(TteumSpacing.lg))
                Text("도착 마감", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(TteumSpacing.xxs))
                Surface(color = TteumRedSoft, shape = TteumShapes.control) {
                    Text(
                        input.arrivalDeadlineEpochMillis?.let { formatArrivalDeadline(it, nowEpochMillis) }
                            ?: "도착 시각을 정해 주세요.",
                        modifier = Modifier.fillMaxWidth().padding(TteumInsets.row),
                        color = TteumRed,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(TteumSpacing.xs))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                ) {
                    listOf(30 to "30분 후", 60 to "1시간 후").forEach { (minutes, label) ->
                        TextButton(
                            onClick = {
                                nowEpochMillis = System.currentTimeMillis()
                                onDeadlineSelected(quickArrivalDeadline(minutes, nowEpochMillis))
                            },
                            modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                            shape = TteumShapes.control,
                            contentPadding = TteumInsets.button,
                        ) { Text(label) }
                    }
                }
                ArrivalDeadlineWheel(
                    hour = selectedHour,
                    minute = selectedMinute,
                    onHourChanged = { hour ->
                        if (selectedHour != hour) {
                            selectedHour = hour
                            onDeadlineSelected(
                                resolveArrivalDeadline(
                                    selectedHour = hour,
                                    selectedMinute = selectedMinute,
                                    nowEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    },
                    onMinuteChanged = { minute ->
                        if (selectedMinute != minute) {
                            selectedMinute = minute
                            onDeadlineSelected(
                                resolveArrivalDeadline(
                                    selectedHour = selectedHour,
                                    selectedMinute = minute,
                                    nowEpochMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    },
                )

                Spacer(Modifier.height(TteumSpacing.lg))
                Text("들르고 싶은 곳 · 선택", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(TteumSpacing.xs))
                Text("고르지 않아도 바로 추천받을 수 있어요.", color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(TteumSpacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TteumSpacing.xs),
                ) {
                    RouteFilterChip(
                        label = "아무거나",
                        selected = input.categories.isEmpty(),
                        onClick = { onFiltersChanged(emptySet()) },
                    )
                    PlaceCategory.entries.forEach { category ->
                        RouteFilterChip(
                            label = category.label,
                            selected = category in input.categories,
                            onClick = {
                                val updated = input.categories.toMutableSet().apply {
                                    if (!add(category)) remove(category)
                                }
                                onFiltersChanged(updated)
                            },
                        )
                    }
                }
            }
        }
                }
            },
        ) { }
    }

    if (automaticLocationEnabled && showLocationSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            modifier = Modifier.widthIn(max = 420.dp),
            title = { Text("위치 서비스를 켜 주세요") },
            text = { Text("현재 위치를 자동으로 설정하려면 휴대폰의 위치 서비스가 필요해요.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationSettingsDialog = false
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) { Text("위치 설정 열기") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLocationSettingsDialog = false },
                    modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = TteumMuted),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) { Text("취소") }
            },
            shape = TteumShapes.dialog,
            containerColor = androidx.compose.ui.graphics.Color.White,
            tonalElevation = 0.dp,
        )
    }
    if (automaticLocationEnabled && showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            modifier = Modifier.widthIn(max = 420.dp),
            title = { Text("위치 권한을 켜 주세요") },
            text = { Text("앱 설정에서 위치 권한을 허용하거나 출발지를 직접 검색해 주세요.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionSettingsDialog = false
                        openAppSettings(context)
                    },
                    modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) { Text("앱 설정 열기") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionSettingsDialog = false },
                    modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = TteumMuted),
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) { Text("직접 검색") }
            },
            shape = TteumShapes.dialog,
            containerColor = androidx.compose.ui.graphics.Color.White,
            tonalElevation = 0.dp,
        )
    }
}

@Composable
private fun RouteFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = TteumSizing.touchTarget)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        color = if (selected) TteumRedSoft else TteumBackground,
        contentColor = if (selected) TteumRed else TteumMuted,
        border = if (selected) BorderStroke(1.dp, TteumRed) else null,
        shape = TteumShapes.badge,
    ) {
        Text(
            label,
            modifier = Modifier.padding(TteumInsets.row),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RouteLocationSearchField(
    value: String,
    selected: RouteLocation?,
    label: String,
    searchPlaces: suspend (String, Boolean) -> List<LocationSearchResult>,
    gangwonOnly: Boolean,
    onValueChange: (String) -> Unit,
    onSelected: (LocationSearchResult) -> Unit,
    labelBackground: androidx.compose.ui.graphics.Color,
    onUseCurrentLocation: (() -> Unit)? = null,
    isLocating: Boolean = false,
    autoFocus: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var results by remember { mutableStateOf(emptyList<LocationSearchResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var rejectedPlaceId by remember { mutableStateOf<String?>(null) }
    var searchFailed by remember { mutableStateOf(false) }
    var searchAttempt by remember { mutableIntStateOf(0) }
    var focusAfterClear by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val firstResultRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(autoFocus, selected) {
        if (autoFocus && selected == null) {
            delay(250)
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(selected) {
        if (selected == null && focusAfterClear) {
            focusRequester.requestFocus()
            focusAfterClear = false
        }
    }
    LaunchedEffect(value, selected, searchAttempt) {
        results = emptyList()
        isLoading = false
        searchMessage = null
        rejectedPlaceId = null
        searchFailed = false
        if (value.length < 2 || selected?.name == value || value == "현재 위치") return@LaunchedEffect
        delay(350)
        isLoading = true
        try {
            results = searchPlaces(value, gangwonOnly)
            if (results.isEmpty()) searchMessage = "검색 결과가 없어요. 장소명이나 도로명·지번 주소를 더 자세히 입력해 주세요."
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            searchFailed = true
            searchMessage = networkFailureMessage("장소 검색", error.message)
        } finally {
            isLoading = false
        }
    }
    LaunchedEffect(results.firstOrNull()?.id) {
        if (results.isNotEmpty()) {
            delay(50)
            firstResultRequester.bringIntoView()
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TteumSizing.inputMinHeight)
                .padding(TteumInsets.field),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = labelBackground, shape = TteumShapes.badge) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = TteumSpacing.sm, vertical = TteumSpacing.xs),
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(TteumSpacing.sm))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (selected != null) {
                    Text(
                        value,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        // Building numbers remain readable at narrow widths and large font scales.
                        // The row has a minimum height only; the surrounding input list can scroll.
                        maxLines = Int.MAX_VALUE,
                        softWrap = true,
                    )
                } else {
                    if (value.isBlank()) Text("장소명 또는 주소", color = TteumMuted, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "$label 장소 검색" }
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            },
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = TteumInk,
                        ),
                    )
                }
            }
            if (isLoading) {
                Spacer(Modifier.width(TteumSpacing.xs))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (value.isNotBlank() && !isLoading) {
                IconButton(
                    onClick = {
                        focusAfterClear = true
                        onValueChange("")
                    },
                    modifier = Modifier.size(TteumSizing.touchTarget),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "$label 지우기",
                        tint = TteumMuted,
                    )
                }
            }
            if (onUseCurrentLocation != null) {
                Spacer(Modifier.width(TteumSpacing.sm))
                Surface(
                    modifier = Modifier.clickable(
                        enabled = !isLocating,
                        role = Role.Button,
                        onClick = onUseCurrentLocation,
                    ),
                    color = TteumRedSoft,
                    shape = TteumShapes.control,
                ) {
                    Box(
                        modifier = Modifier.widthIn(min = TteumSizing.touchTarget).heightIn(min = TteumSizing.touchTarget),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isLocating) "확인 중" else "현위치",
                            color = TteumRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (searchMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(TteumInsets.row),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(searchMessage.orEmpty(), modifier = Modifier.weight(1f), color = TteumMuted, style = MaterialTheme.typography.bodySmall)
                if (searchFailed) TextButton(onClick = { searchAttempt += 1 }, shape = TteumShapes.control, contentPadding = TteumInsets.button) { Text("다시 시도") }
            }
        }
        if (gangwonOnly && selected == null && searchMessage == null) {
            Text("현재는 강원도 목적지를 지원해요", modifier = Modifier.padding(TteumInsets.row), color = TteumMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (results.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White)) {
                results.take(LOCATION_SEARCH_VISIBLE_LIMIT).forEachIndexed { index, result ->
                    val isAddress = result.type == LocationSearchResultType.ADDRESS
                    val supportingAddress = if (isAddress) result.secondaryAddress else result.address
                    val support = if (gangwonOnly) {
                        destinationSupport(result.address)
                    } else {
                        DestinationSupport.SUPPORTED
                    }
                    val isUnsupported = support == DestinationSupport.UNSUPPORTED
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (index == 0) Modifier.bringIntoViewRequester(firstResultRequester) else Modifier,
                            )
                            .clickable {
                                if (isUnsupported) {
                                    searchFailed = false
                                    rejectedPlaceId = result.id
                                    searchMessage = UNSUPPORTED_DESTINATION_MESSAGE
                                } else {
                                    onSelected(result)
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            }
                            .padding(TteumInsets.row),
                    ) {
                        if (isAddress) {
                            Text(
                                "주소",
                                color = TteumMuted,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(bottom = TteumSpacing.xxs),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                result.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isUnsupported) TteumMuted else TteumInk,
                            )
                            if (isUnsupported) {
                                Spacer(Modifier.width(TteumSpacing.xs))
                                Text(
                                    "현재 지원 지역 아님",
                                    color = TteumRed,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (isUnsupported && rejectedPlaceId == result.id) {
                            Text(UNSUPPORTED_DESTINATION_MESSAGE, color = TteumRed, style = MaterialTheme.typography.bodySmall)
                        }
                        if (supportingAddress.isNotBlank()) {
                            Text(
                                supportingAddress,
                                color = TteumMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = if (isAddress) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

    }
}
