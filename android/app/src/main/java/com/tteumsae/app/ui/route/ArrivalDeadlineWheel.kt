package com.tteumsae.app.ui.route

import android.annotation.SuppressLint
import android.os.Build
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSpacing
import kotlin.math.roundToInt

@Composable
internal fun ArrivalDeadlineWheel(
    hour: Int,
    minute: Int,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TteumBackground,
        shape = TteumShapes.card,
    ) {
        Row(
            modifier = Modifier.padding(TteumInsets.card),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NativeNumberWheel(
                value = hour,
                maxValue = 23,
                contentDescription = "도착 시",
                onValueChanged = onHourChanged,
            )
            Text(
                ":",
                modifier = Modifier.padding(horizontal = TteumSpacing.inlineGap),
                color = TteumRed,
                style = MaterialTheme.typography.headlineMedium,
            )
            NativeNumberWheel(
                value = minute,
                maxValue = 59,
                contentDescription = "도착 분",
                onValueChanged = onMinuteChanged,
            )
        }
    }
}

@Composable
@SuppressLint("ClickableViewAccessibility")
private fun NativeNumberWheel(
    value: Int,
    maxValue: Int,
    contentDescription: String,
    onValueChanged: (Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .width(96.dp)
            .height(132.dp)
            .semantics { this.contentDescription = contentDescription },
        factory = { context ->
            NumberPicker(context).apply {
                this.contentDescription = contentDescription
                minValue = 0
                this.maxValue = maxValue
                displayedValues = (0..maxValue).map { "%02d".format(it) }.toTypedArray()
                wrapSelectorWheel = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        -> view.parent?.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> view.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                setOnValueChangedListener { _, _, newValue -> onValueChanged(newValue) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    textColor = TteumRed.toArgb()
                    selectionDividerHeight = context.resources.displayMetrics.density
                        .roundToInt()
                        .coerceAtLeast(1)
                }
            }
        },
        update = { picker ->
            if (picker.value != value) picker.value = value
            picker.setOnValueChangedListener { _, _, newValue -> onValueChanged(newValue) }
        },
    )
}
