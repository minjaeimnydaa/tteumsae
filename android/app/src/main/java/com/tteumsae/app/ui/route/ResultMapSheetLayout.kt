package com.tteumsae.app.ui.route

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.tteumsae.app.ui.theme.TteumShapes
import kotlin.math.roundToInt

/** Map and list stay mounted; dragging and explicit controls share these same three anchors. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultMapSheetLayout(
    listState: LazyListState,
    position: ResultSheetPosition,
    onPositionSettled: (ResultSheetPosition) -> Unit,
    header: @Composable (Modifier) -> Unit,
    sheetContent: @Composable ColumnScope.() -> Unit,
    mapContent: @Composable BoxScope.(obscuredHeightDp: Float, visibleHeightDp: Float, availableHeightDp: Float) -> Unit,
) {
    val density = LocalDensity.current
    val currentOnPositionSettled by rememberUpdatedState(onPositionSettled)
    var headerHeightDp by remember { mutableFloatStateOf(96f) }
    val state = remember(density.density) {
        AnchoredDraggableState(
            initialValue = position,
            positionalThreshold = { distance -> distance * 0.42f },
            velocityThreshold = { with(density) { 180.dp.toPx() } },
            snapAnimationSpec = tween(280),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    LaunchedEffect(position, state) { state.animateTo(position) }
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { currentOnPositionSettled(it) }
    }
    val nestedScroll = remember(state, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Up expands before list scrolling. Down collapses only at the list's start.
                val delta = available.y
                return if (delta < 0f || !listState.canScrollBackward) {
                    Offset(0f, state.dispatchRawDelta(delta))
                } else Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y))
                else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                val canExpand = available.y < 0f && state.requireOffset() > state.anchors.positionOf(ResultSheetPosition.LIST)
                val canCollapse = available.y > 0f && !listState.canScrollBackward &&
                    state.requireOffset() < state.anchors.positionOf(ResultSheetPosition.MAP)
                if (!canExpand && !canCollapse) return Velocity.Zero
                state.settle(available.y)
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                state.settle(available.y)
                return available
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val geometry = resultSheetGeometry(maxHeight.value, headerHeightDp)
        val anchors = with(density) {
            DraggableAnchors {
                ResultSheetPosition.LIST at 0f
                ResultSheetPosition.BALANCED at geometry.balancedOffsetDp.dp.toPx()
                ResultSheetPosition.MAP at geometry.collapsedOffsetDp.dp.toPx()
            }
        }
        SideEffect { state.updateAnchors(anchors, newTarget = state.targetValue) }
        val initialOffsetDp = when (position) {
            ResultSheetPosition.LIST -> 0f
            ResultSheetPosition.BALANCED -> geometry.balancedOffsetDp
            ResultSheetPosition.MAP -> geometry.collapsedOffsetDp
        }
        val offsetPx = state.offset.takeUnless { it.isNaN() }
            ?: with(density) { initialOffsetDp.dp.toPx() }
        val offsetDp = with(density) { offsetPx.toDp().value }.coerceIn(0f, geometry.collapsedOffsetDp)

        // A pin/cluster can request BALANCED before animateTo updates targetValue.
        // Apply that viewport now; ordinary dragging still follows its shared target.
        val viewportPosition = resultSheetViewportPosition(position, state.settledValue, state.targetValue)
        mapContent(resultMapObscuredHeightDp(geometry, viewportPosition), offsetDp, geometry.availableHeightDp)

        Surface(
            modifier = Modifier
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .fillMaxWidth()
                .height((geometry.availableHeightDp - offsetDp).dp)
                .nestedScroll(nestedScroll),
            color = Color.White,
            shape = TteumShapes.sheet,
            shadowElevation = 12.dp,
        ) {
            Column {
                header(
                    Modifier
                        .fillMaxWidth()
                        // A restored MAP stop may still use the smaller previous height.
                        // Measure the header's natural height, not that clipped estimate.
                        .wrapContentHeight(Alignment.Top, unbounded = true)
                        .onSizeChanged { headerHeightDp = with(density) { it.height.toDp().value } }
                        .anchoredDraggable(state, Orientation.Vertical),
                )
                sheetContent()
            }
        }
    }
}
