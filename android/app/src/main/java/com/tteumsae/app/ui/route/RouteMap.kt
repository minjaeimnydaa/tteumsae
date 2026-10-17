package com.tteumsae.app.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.tteumsae.app.domain.RouteSummary
import com.tteumsae.app.domain.SafeRecommendation
import com.tteumsae.app.domain.SearchCriteria
import com.tteumsae.app.ui.RouteMap

/**
 * 실제 Kakao Map 렌더러는 아직 상위 UI 파일의 검증된 구현을 재사용한다.
 * Gate 2에서는 결과 화면의 선택/표시 책임을 먼저 분리하고 렌더러 이동은 후속 정리에서 수행한다.
 */
@Composable
internal fun RouteMapCanvas(
    modifier: Modifier,
    criteria: SearchCriteria,
    selected: SafeRecommendation?,
    baseRoute: RouteSummary?,
    recommendations: List<SafeRecommendation>,
    selectedPlaceId: String?,
    focusedPlaceId: String?,
    corridorRadiusMeters: Int,
    overviewRequestId: Int,
    mapBottomPadding: Dp,
    mapTopPadding: Dp,
    onCandidateClick: (String) -> Unit,
    onClusterClick: (List<String>) -> Unit,
    onMapInteraction: () -> Unit,
) {
    RouteMap(
        modifier = modifier,
        criteria = criteria,
        recommendation = selected,
        routeSummary = if (selected == null) baseRoute else null,
        candidates = recommendations,
        selectedIds = listOfNotNull(selectedPlaceId),
        focusedPlaceId = focusedPlaceId,
        corridorRadiusMeters = corridorRadiusMeters,
        overviewRequestId = overviewRequestId,
        mapBottomPadding = mapBottomPadding,
        mapTopPadding = mapTopPadding,
        onMapInteraction = onMapInteraction,
        onCandidateClick = onCandidateClick,
        onClusterClick = onClusterClick,
    )
}
