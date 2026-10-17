package com.tteumsae.app.ui.route

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.domain.route.RouteNavigationAction
import com.tteumsae.app.domain.route.recommendationNeedsRecheck
import com.tteumsae.app.domain.route.routeNavigationAction
import com.tteumsae.app.location.LocationAccessPolicy
import com.tteumsae.app.reminder.ReminderDeliveryBlockReason
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RouteResultsScreen(
    criteria: SearchCriteria,
    recommendations: List<SafeRecommendation>,
    baseRoute: RouteSummary?,
    corridorRadiusMeters: Int,
    selectedPlaceId: String?,
    warning: String,
    calculatedAtEpochMillis: Long?,
    isRefreshing: Boolean,
    reminderEnabled: Boolean,
    reminderBlockedReason: ReminderDeliveryBlockReason?,
    onSelectPlace: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRefresh: () -> Unit,
    onReminderChanged: (SafeRecommendation, Boolean) -> Unit,
    onOpenReminderSettings: () -> Unit,
    onBack: () -> Unit,
    onNewSearch: () -> Unit,
    onNavigate: (SafeRecommendation?) -> Unit,
    onDetail: (SafeRecommendation) -> Unit,
) {
    val selected = recommendations.firstOrNull { it.place.id == selectedPlaceId }
    val focusedPlaceId = selectedPlaceId
    var clusterScopeIds by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val visibleRecommendations = clusterScopeIds?.let { ids ->
        recommendations.filter { it.place.id in ids }
    }?.takeIf { it.isNotEmpty() } ?: recommendations
    var overviewRequestId by remember { mutableIntStateOf(0) }
    var sheetPosition by rememberSaveable { mutableStateOf(ResultSheetPosition.BALANCED) }
    var revealRequestId by remember { mutableIntStateOf(0) }
    var revealPlaceId by remember { mutableStateOf<String?>(null) }
    var nowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val recommendationListState = rememberLazyListState()
    val deadlineCannotBeRechecked = arrivalDeadlineCannotBeRechecked(criteria, nowEpochMillis)
    val navigationAction = routeNavigationAction(
        criteria, listOfNotNull(selected), calculatedAtEpochMillis, nowEpochMillis,
    )
    val directRouteIsTight = baseRoute != null &&
        baseRoute.totalDrivingMinutes + criteria.safetyBufferMinutes > criteria.deadlineMinutesFromNow
    val fontScale = LocalDensity.current.fontScale

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowEpochMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(recommendations.map { it.place.id }) {
        clusterScopeIds = clusterScopeIds?.intersect(recommendations.map { it.place.id }.toSet())
            ?.takeIf { it.isNotEmpty() }?.toList()
    }

    // Explicit pin/cluster actions reveal a row once. Merely browsing, refreshing or
    // expanding the sheet must not move the list away from the user's reading position.
    LaunchedEffect(revealRequestId) {
        if (revealRequestId == 0) return@LaunchedEffect
        val id = revealPlaceId ?: return@LaunchedEffect
        withFrameNanos { }
        resultCandidateRevealIndex(visibleRecommendations.map { it.place.id }, id, warning.isNotBlank())
            ?.let { recommendationListState.animateScrollToItem(it) }
    }
    BackHandler(enabled = sheetPosition == ResultSheetPosition.LIST) {
        sheetPosition = ResultSheetPosition.BALANCED
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Surface(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.xs)
                        .fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.97f),
                    shape = TteumShapes.card,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(TteumInsets.row),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (recommendations.isEmpty()) {
                                    "추천 가능한 장소가 없어요"
                                } else if (fontScale > 1.3f) {
                                    "경유지 선택"
                                } else {
                                    "들를 곳을 골라보세요"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (recommendations.isEmpty()) {
                                    "조건을 바꾸거나 목적지로 바로 이동할 수 있어요"
                                } else {
                                    resultFreshnessLabel(calculatedAtEpochMillis, nowEpochMillis)
                                },
                                color = TteumMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (!LocationAccessPolicy.automaticLocationEnabled) {
                                Text(
                                    "선택한 출발지 기준",
                                    color = TteumMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        IconButton(
                            onClick = if (deadlineCannotBeRechecked) onNewSearch else onRefresh,
                            enabled = !isRefreshing,
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.padding(TteumSpacing.xs), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = if (deadlineCannotBeRechecked) {
                                        "도착 마감 다시 정하기"
                                    } else {
                                        "현재 교통으로 다시 확인"
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Column {
                Button(
                    onClick = {
                        when (routeNavigationAction(
                            criteria, listOfNotNull(selected), calculatedAtEpochMillis, System.currentTimeMillis(),
                        )) {
                            RouteNavigationAction.RESET_DEADLINE -> onNewSearch()
                            RouteNavigationAction.RECHECK -> onRefresh()
                            RouteNavigationAction.OPEN_ROUTE -> onNavigate(selected)
                        }
                    },
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.sm)
                        .heightIn(min = TteumSizing.buttonHeight)
                        .semantics {
                            stateDescription = selected?.let {
                                "${it.place.name} 경유 선택됨"
                            } ?: "경유지 선택 안 함"
                        },
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                ) {
                    Text(
                        when {
                            navigationAction == RouteNavigationAction.RESET_DEADLINE -> "도착 마감 다시 정하기"
                            navigationAction == RouteNavigationAction.RECHECK -> "현재 교통으로 다시 확인"
                            selected == null -> "목적지로 바로 안내"
                            else -> "이곳 들러 카카오맵 안내"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
                }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ResultMapSheetLayout(
                listState = recommendationListState,
                position = sheetPosition,
                onPositionSettled = { sheetPosition = it },
                header = { dragModifier ->
                    ResultSheetHeader(
                        modifier = dragModifier,
                        count = visibleRecommendations.size,
                        calculatedAtEpochMillis = calculatedAtEpochMillis,
                        isClusterScope = clusterScopeIds != null,
                        position = sheetPosition,
                        onShowAll = { clusterScopeIds = null },
                        onPositionChanged = { sheetPosition = it },
                    )
                },
                mapContent = { obscuredHeight, visibleHeight, availableHeight ->
                val mapInsets = resultMapViewportInsets(availableHeight, obscuredHeight)
                RouteMapCanvas(
                    modifier = Modifier.fillMaxSize(),
                    criteria = criteria,
                    selected = selected,
                    baseRoute = baseRoute,
                    recommendations = recommendations,
                    selectedPlaceId = selectedPlaceId,
                    focusedPlaceId = focusedPlaceId,
                    corridorRadiusMeters = corridorRadiusMeters,
                    overviewRequestId = overviewRequestId,
                    mapTopPadding = mapInsets.topDp.dp,
                    mapBottomPadding = mapInsets.bottomDp.dp,
                    onMapInteraction = {
                        if (clusterScopeIds != null) clusterScopeIds = null
                    },
                    onCandidateClick = { tapped ->
                        if (clusterScopeIds?.contains(tapped) == false) clusterScopeIds = null
                        when (nextSelectedPlaceId(selectedPlaceId, tapped)) {
                            null -> onClearSelection()
                            else -> {
                                onSelectPlace(tapped)
                                revealPlaceId = tapped
                                revealRequestId += 1
                                sheetPosition = ResultSheetPosition.BALANCED
                            }
                        }
                    },
                    onClusterClick = { memberIds ->
                        clusterScopeIds = memberIds.toList()
                        // Browsing a cluster is not a change to the chosen trip or reminder.
                        revealPlaceId = memberIds.firstOrNull()
                        revealRequestId += 1
                        sheetPosition = ResultSheetPosition.BALANCED
                    },
                )
                if (visibleHeight > 72f) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(TteumSpacing.sm),
                        shape = TteumShapes.control,
                        color = Color.White,
                        shadowElevation = 3.dp,
                    ) {
                        TextButton(onClick = { overviewRequestId += 1 }, shape = TteumShapes.control, contentPadding = TteumInsets.button) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(TteumSpacing.xs))
                            Text("전체 경로")
                        }
                    }
                }
                },
                sheetContent = {
                    LazyColumn(
                        state = recommendationListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = TteumSpacing.lg),
                    ) {
                        if (recommendations.isEmpty()) {
                            item(key = "empty-results") {
                                EmptyResultSheet(directRouteIsTight, warning, onNewSearch)
                            }
                        }
                        if (warning.isNotBlank() && recommendations.isNotEmpty()) {
                            item(key = "result-warning") {
                                Box(Modifier.padding(horizontal = TteumSpacing.screenHorizontal, vertical = TteumSpacing.xs)) {
                                    ResultWarningBanner(
                                        message = warning,
                                        isRefreshing = isRefreshing,
                                        actionLabel = if (deadlineCannotBeRechecked) {
                                            "시간 다시 정하기"
                                        } else {
                                            "다시 확인"
                                        },
                                        onAction = if (deadlineCannotBeRechecked) onNewSearch else onRefresh,
                                    )
                                }
                            }
                        }
                        itemsIndexed(
                            items = visibleRecommendations,
                            key = { _, item -> item.place.id },
                        ) { index, recommendation ->
                            val isSelected = recommendation.place.id == selectedPlaceId
                            RouteCandidateCard(
                                recommendation = recommendation,
                                selected = isSelected,
                                needsRecheck = recommendationNeedsRecheck(
                                    recommendation,
                                    calculatedAtEpochMillis,
                                    nowEpochMillis,
                                ),
                                onSelect = {
                                    when (nextSelectedPlaceId(selectedPlaceId, recommendation.place.id)) {
                                        null -> onClearSelection()
                                        else -> onSelectPlace(recommendation.place.id)
                                    }
                                },
                                onDetail = { onDetail(recommendation) },
                                reminderContent = if (isSelected) {
                                    {
                                        ReminderToggle(
                                            recommendation = recommendation,
                                            checked = reminderEnabled,
                                            blockedReason = reminderBlockedReason,
                                            enabled = reminderEnabled ||
                                                !recommendationNeedsRecheck(
                                                    recommendation,
                                                    calculatedAtEpochMillis,
                                                    nowEpochMillis,
                                                ),
                                            onCheckedChange = {
                                                onReminderChanged(recommendation, it)
                                            },
                                            onOpenSettings = onOpenReminderSettings,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                            if (index != visibleRecommendations.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = TteumSpacing.cardPadding),
                                    color = Color(0xFFE9EBEF),
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ResultSheetHeader(
    modifier: Modifier,
    count: Int,
    calculatedAtEpochMillis: Long?,
    isClusterScope: Boolean,
    position: ResultSheetPosition,
    onShowAll: () -> Unit,
    onPositionChanged: (ResultSheetPosition) -> Unit,
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(modifier) {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(4.dp).background(Color(0xFFD3D6DB), RoundedCornerShape(50)))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TteumSpacing.sheetHorizontal, end = TteumSpacing.xs, bottom = TteumSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    isClusterScope && largeText -> "주변 ${count}곳"
                    isClusterScope -> "이 주변 ${count}곳"
                    largeText -> "추천 ${count}곳"
                    else -> "경로 주변 추천 ${count}곳"
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${calculatedTimeLabel(calculatedAtEpochMillis)} 계산 기준 · 한 곳 선택",
                color = TteumMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onPositionChanged(ResultSheetPosition.MAP) },
            enabled = position != ResultSheetPosition.MAP,
            modifier = Modifier.semantics { contentDescription = "지도 크게 보기" },
        ) {
            Icon(Icons.Default.Map, contentDescription = null)
        }
        IconButton(
            onClick = {
                onPositionChanged(if (position == ResultSheetPosition.LIST) ResultSheetPosition.BALANCED else ResultSheetPosition.LIST)
            },
            modifier = Modifier.semantics {
                contentDescription = if (position == ResultSheetPosition.LIST) "지도와 목록 함께 보기" else "목록 펼치기"
            },
        ) {
            Icon(
                if (position == ResultSheetPosition.LIST) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = null,
            )
        }
    }
    if (isClusterScope) {
        TextButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth().heightIn(min = TteumSizing.touchTarget), shape = TteumShapes.control, contentPadding = TteumInsets.button) {
            Text("전체 추천 보기", color = TteumRed)
        }
    }
    }
}

@Composable
private fun ResultWarningBanner(
    message: String,
    isRefreshing: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TteumRedSoft,
        shape = TteumShapes.card,
    ) {
        Row(
            modifier = Modifier.padding(TteumInsets.row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = TteumRed,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onAction, enabled = !isRefreshing, shape = TteumShapes.control, contentPadding = TteumInsets.button) {
                Text(if (isRefreshing) "확인 중" else actionLabel)
            }
        }
    }
}

@Composable
private fun ReminderToggle(
    recommendation: SafeRecommendation,
    checked: Boolean,
    enabled: Boolean,
    blockedReason: ReminderDeliveryBlockReason?,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val presentation = reminderPresentation(checked, enabled, blockedReason)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TteumSizing.inputMinHeight)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = androidx.compose.ui.semantics.Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(horizontal = TteumSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.NotificationsNone,
                contentDescription = null,
                tint = if (enabled) TteumRed else TteumMuted,
            )
            Spacer(Modifier.width(TteumSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    if (presentation.showScheduledTime) {
                        "${reminderTimeLabel(recommendation)} 출발 알림 예정"
                    } else {
                        "출발 시간 알림"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    presentation.message,
                    color = TteumMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
            )
        }
        if (presentation.showSettingsAction) {
            TextButton(onClick = onOpenSettings) {
                Text("휴대폰 설정에서 알림 허용")
            }
        }
    }
}

@Composable
private fun EmptyResultSheet(
    directRouteIsTight: Boolean,
    warning: String,
    onNewSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = TteumSpacing.sheetHorizontal, end = TteumSpacing.sheetHorizontal, bottom = TteumSpacing.lg),
    ) {
        Text(
            if (directRouteIsTight) {
                "지금 바로 출발해도 도착 마감이 빠듯해요."
            } else {
                "지금 조건에서 15분 이상 머물 수 있는 장소가 없어요."
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(TteumSpacing.xs))
        Text(
            warning.ifBlank { "목적지로 바로 안내받거나 도착 마감을 다시 정해보세요." },
            color = TteumMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onClick = onNewSearch, shape = TteumShapes.control, contentPadding = TteumInsets.button) { Text("경로 다시 정하기") }
    }
}

private fun calculatedTimeLabel(epochMillis: Long?): String {
    if (epochMillis == null) return "지금"
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun resultMapCaption(epochMillis: Long?, count: Int): String =
    "${calculatedTimeLabel(epochMillis)} 현재 교통 · ${count}곳"
