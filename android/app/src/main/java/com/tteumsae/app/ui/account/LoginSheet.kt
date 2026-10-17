package com.tteumsae.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tteumsae.app.domain.account.LoginProvider
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoginSheet(
    onDismiss: () -> Unit,
    onLogin: (LoginProvider) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        shape = TteumShapes.sheet,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TteumSpacing.sheetHorizontal, vertical = TteumSpacing.itemGap)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .semantics { paneTitle = LOGIN_SHEET_TITLE },
                verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap),
            ) {
                Text(
                    LOGIN_SHEET_TITLE,
                    color = TteumInk,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    LOGIN_SHEET_DESCRIPTION,
                    color = TteumMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(TteumSpacing.xxs))
                Button(
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                    onClick = { onLogin(LoginProvider.KAKAO) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = TteumSizing.buttonHeight),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFEE500),
                        contentColor = Color(0xFF191919),
                    ),
                ) {
                    Text("카카오로 계속하기", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    shape = TteumShapes.control,
                    contentPadding = TteumInsets.button,
                    onClick = { onLogin(LoginProvider.GOOGLE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = TteumSizing.buttonHeight),
                ) {
                    Text("Google로 계속하기", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(TteumSpacing.sm))
            }
        }
    }
}
