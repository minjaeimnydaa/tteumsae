package com.tteumsae.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.LoginProvider
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

@Composable
internal fun AccountSettingsCard(
    state: AccountUiState,
    onLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = TteumShapes.card,
    ) {
        when (val session = state.session) {
            is AccountSession.SignedIn -> SignedInAccountCard(
                state = state,
                provider = session.provider,
                onOpenProfile = onOpenProfile,
                onRetryProfile = onRetryProfile,
                onSignOut = onSignOut,
                onDeleteAccount = onDeleteAccount,
            )

            AccountSession.Restoring -> LoadingAccountCard()
            else -> GuestAccountCard(state, onLogin)
        }
    }
}

@Composable
private fun GuestAccountCard(state: AccountUiState, onLogin: () -> Unit) {
    Column(Modifier.padding(TteumInsets.card), verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap)) {
        Text("계정 없이 사용 중", color = TteumInk, style = MaterialTheme.typography.titleMedium)
        Text(
            GUEST_ACCOUNT_DESCRIPTION,
            color = TteumMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            shape = TteumShapes.control,
            contentPadding = TteumInsets.button,
            onClick = onLogin,
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TteumSizing.buttonHeight)
                .semantics {
                    if (state.isLoading) {
                        liveRegion = LiveRegionMode.Polite
                        stateDescription = "로그인 처리 중"
                    }
                },
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color(0xFF777C85),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(TteumSpacing.inlineGap))
            }
            Text(if (state.isLoading) "로그인 중…" else "로그인하기", style = MaterialTheme.typography.labelLarge)
        }
        state.errorMessage?.let { AccountErrorMessage(it) }
    }
}

@Composable
private fun SignedInAccountCard(
    state: AccountUiState,
    provider: LoginProvider,
    onOpenProfile: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val profileAvailable = state.profile != null && !state.isLoading
    Column(Modifier.padding(TteumInsets.card), verticalArrangement = Arrangement.spacedBy(TteumSpacing.itemGap)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TteumSizing.buttonHeight)
                .clickable(
                    enabled = profileAvailable,
                    onClickLabel = "프로필 관리",
                    role = Role.Button,
                    onClick = onOpenProfile,
                )
                .padding(vertical = TteumSpacing.rowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF2E7E4), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.profile?.displayName?.firstOrNull()?.toString() ?: "틈",
                    color = TteumRed,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(Modifier.padding(start = TteumSpacing.itemGap).weight(1f)) {
                Text(state.profile?.displayName ?: "틈새 사용자", color = TteumInk, style = MaterialTheme.typography.titleMedium)
                Text(if (provider == LoginProvider.KAKAO) "카카오 로그인" else "Google 로그인", color = TteumMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.profile != null) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TteumMuted,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap)) {
            OutlinedButton(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onSignOut,
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f).heightIn(min = TteumSizing.touchTarget),
            ) {
                Text("로그아웃", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onDeleteAccount,
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f).heightIn(min = TteumSizing.touchTarget),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TteumRed,
                    disabledContentColor = TteumMuted.copy(alpha = 0.55f),
                ),
            ) {
                Text("계정 삭제", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (state.isLoading) AccountLoadingStatus("계정 작업 처리 중…")
        state.errorMessage?.let { AccountErrorMessage(it) }
        if (state.profile == null && state.errorMessage != null && !state.isLoading) {
            OutlinedButton(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onRetryProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TteumSizing.touchTarget),
            ) {
                Text("프로필 다시 불러오기", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun LoadingAccountCard() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(TteumInsets.card)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = "로그인 상태 확인 중"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(" 로그인 상태 확인 중", color = TteumMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountLoadingStatus(message: String) {
    Row(
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            stateDescription = message
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TteumSpacing.inlineGap),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        Text(message, color = TteumMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AccountErrorMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = TteumRed,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun AccountDeletionDialogs(
    step: AccountDeletionStep,
    isLoading: Boolean,
    onContinue: () -> Unit,
    onRequireReauthentication: () -> Unit,
    onReauthenticate: () -> Unit,
    onCancel: () -> Unit,
) {
    when (step) {
        AccountDeletionStep.NONE -> Unit
        AccountDeletionStep.FIRST_CONFIRMATION -> DeletionDialog(
            title = "계정을 삭제할까요?",
            message = ACCOUNT_DELETION_IMPACT,
            confirmText = "영향 확인",
            onConfirm = onContinue,
            onCancel = onCancel,
        )
        AccountDeletionStep.FINAL_CONFIRMATION -> DeletionDialog(
            title = "정말 영구 삭제할까요?",
            message = "카카오 또는 Google 계정 자체는 삭제되지 않지만, 틈새 계정과 연결 데이터는 영구 삭제됩니다.",
            confirmText = "계속",
            onConfirm = onRequireReauthentication,
            onCancel = onCancel,
        )
        AccountDeletionStep.REAUTHENTICATION -> DeletionDialog(
            title = "마지막으로 로그인해 주세요",
            message = "다른 사람이 계정을 지우지 못하도록 사용 중인 로그인 제공자로 본인 확인 후 즉시 삭제합니다.",
            confirmText = if (isLoading) "확인 중…" else "다시 로그인하고 삭제",
            onConfirm = onReauthenticate,
            onCancel = onCancel,
            enabled = !isLoading,
            showProgress = isLoading,
        )
    }
}

@Composable
private fun DeletionDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    AlertDialog(
        modifier = Modifier
            .padding(horizontal = TteumSpacing.dialogPadding)
            .widthIn(max = 420.dp)
            .fillMaxWidth(),
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                color = TteumInk,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = message,
                color = TteumMuted,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier
                    .heightIn(min = TteumSizing.touchTarget)
                    .semantics {
                        if (showProgress) {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = "본인 확인 중"
                        }
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TteumRed,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE7E8EB),
                    disabledContentColor = Color(0xFF777C85),
                ),
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF777C85),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(TteumSpacing.inlineGap))
                }
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onCancel,
                modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = TteumInk),
            ) {
                Text("취소", style = MaterialTheme.typography.labelMedium)
            }
        },
        shape = TteumShapes.dialog,
        containerColor = Color.White,
        tonalElevation = 0.dp,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    )
}
