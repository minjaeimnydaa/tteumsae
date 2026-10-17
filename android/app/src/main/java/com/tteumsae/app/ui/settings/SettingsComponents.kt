package com.tteumsae.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier
            .padding(start = TteumSpacing.xxs, bottom = TteumSpacing.itemGap)
            .semantics { heading() },
        color = TteumMuted,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = TteumShapes.card,
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    description: String,
    titleColor: Color = TteumInk,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TteumSizing.touchTarget)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(TteumInsets.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(TteumSpacing.xxs))
            Text(description, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
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
internal fun SettingsDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF0F1F3)),
    )
}
