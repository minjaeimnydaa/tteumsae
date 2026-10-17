package com.tteumsae.app.ui.route

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumRedSoft
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing
import kotlinx.coroutines.delay

@Composable
internal fun RouteLoadingScreen(
    completed: Boolean,
    hasRecommendations: Boolean,
    onBack: () -> Unit,
    background: @Composable (Modifier) -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var isTakingLonger by remember { mutableStateOf(false) }
    val presentation = routeLoadingPresentation(completed, hasRecommendations, isTakingLonger)
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.95f,
        animationSpec = tween(220),
        label = "loading-card-scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(180),
        label = "loading-card-alpha",
    )
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(completed) {
        isTakingLonger = false
        if (!completed) {
            delay(10_000L)
            isTakingLonger = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        background(Modifier.fillMaxSize())
    }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f)),
        ) {
            val cardMaxHeight = (maxHeight - 48.dp).coerceAtLeast(240.dp)
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(TteumSpacing.screenHorizontal)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = cardMaxHeight)
                    .semantics {
                        paneTitle = "경로 검색 진행 상황"
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = presentation.title
                    }
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
                        .padding(TteumSpacing.dialogPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = TteumRedSoft,
                        shape = CircleShape,
                        modifier = Modifier.size(68.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Crossfade(
                                targetState = completed,
                                animationSpec = tween(260),
                                label = "loading-complete-icon",
                            ) { isCompleted ->
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.Check else Icons.Default.Map,
                                    contentDescription = null,
                                    tint = TteumRed,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(TteumSpacing.md))
                    Text(
                        presentation.title,
                        color = TteumInk,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(TteumSpacing.md))
                    Text(
                        presentation.message,
                        color = TteumMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    if (!completed) {
                        Spacer(Modifier.height(TteumSpacing.md))
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = TteumRed,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.height(TteumSpacing.xs))
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                            shape = TteumShapes.control,
                            contentPadding = TteumInsets.button,
                        ) {
                            Text("검색 취소")
                        }
                    }
                }
            }
        }
    }
}
