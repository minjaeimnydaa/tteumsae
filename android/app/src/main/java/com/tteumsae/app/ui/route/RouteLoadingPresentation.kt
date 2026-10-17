package com.tteumsae.app.ui.route

internal data class RouteLoadingPresentation(
    val title: String,
    val message: String,
)

internal fun routeLoadingPresentation(
    completed: Boolean,
    hasRecommendations: Boolean,
    isTakingLonger: Boolean,
): RouteLoadingPresentation = when {
    completed && hasRecommendations -> RouteLoadingPresentation(
        title = "틈새를 찾았어요",
        message = "들를 수 있는 장소와 시간을 확인해 보세요.",
    )
    completed -> RouteLoadingPresentation(
        title = "경로를 확인했어요",
        message = "지금 조건에서는 들를 수 있는 장소가 없어요. 결과에서 경로를 확인해 보세요.",
    )
    isTakingLonger -> RouteLoadingPresentation(
        title = "가는 길 주변을 찾고 있어요",
        message = "응답이 조금 늦어지고 있어요. 잠시 기다리거나 검색을 취소할 수 있어요.",
    )
    else -> RouteLoadingPresentation(
        title = "가는 길 주변을 찾고 있어요",
        message = "현재 교통과 도착 시각을 기준으로 들를 수 있는 장소를 확인하고 있어요.",
    )
}
