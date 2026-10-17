package com.tteumsae.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tteumsae.app.location.LocationAccessPolicy
import com.tteumsae.app.ui.BottomNavigation
import com.tteumsae.app.ui.account.AccountDeletionDialogs
import com.tteumsae.app.ui.account.AccountSettingsCard
import com.tteumsae.app.ui.account.AccountUiState
import com.tteumsae.app.ui.account.LoginSheet
import com.tteumsae.app.ui.navigation.MainTab
import com.tteumsae.app.ui.theme.TteumBackground
import com.tteumsae.app.ui.theme.TteumInk
import com.tteumsae.app.ui.theme.TteumInsets
import com.tteumsae.app.ui.theme.TteumMuted
import com.tteumsae.app.ui.theme.TteumRed
import com.tteumsae.app.ui.theme.TteumShapes
import com.tteumsae.app.ui.theme.TteumSizing
import com.tteumsae.app.ui.theme.TteumSpacing

@Composable
internal fun SettingsScreen(
    accountState: AccountUiState,
    savedCount: Int,
    locationPermissionGranted: Boolean,
    kakaoMapAvailable: Boolean,
    appVersion: String,
    contactEmail: String,
    privacyPolicyAvailable: Boolean,
    locationTermsAvailable: Boolean,
    onOpenLogin: () -> Unit,
    onDismissLogin: () -> Unit,
    onLogin: (com.tteumsae.app.domain.account.LoginProvider) -> Unit,
    onOpenProfile: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onRequestAccountDeletion: () -> Unit,
    onConfirmDeletionConsequences: () -> Unit,
    onRequireReauthentication: () -> Unit,
    onReauthenticateForDeletion: () -> Unit,
    onCancelDeletion: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenKakaoMap: () -> Unit,
    onClearCache: () -> Boolean,
    onClearSaved: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLocationTerms: () -> Unit,
    onContact: () -> Unit,
    onShowMessage: (String) -> Unit,
    onTabSelected: (MainTab) -> Unit,
) {
    val automaticLocationEnabled = LocationAccessPolicy.automaticLocationEnabled
    var showSavedClearDialog by remember { mutableStateOf(false) }
    var showCacheClearDialog by remember { mutableStateOf(false) }
    var showLicenseNotices by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = TteumBackground,
        bottomBar = {
            BottomNavigation(
                selectedTab = MainTab.SETTINGS,
                onTabSelected = onTabSelected,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TteumSpacing.screenHorizontal),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = TteumSpacing.itemGap, bottom = TteumSpacing.sectionGap),
            ) {
                item {
                    Text(
                        "설정",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(TteumSpacing.sectionGap))

                    SettingsSectionTitle("계정")
                    AccountSettingsCard(
                        state = accountState,
                        onLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                        onRetryProfile = onRetryProfile,
                        onSignOut = onSignOut,
                        onDeleteAccount = onRequestAccountDeletion,
                    )

                    Spacer(Modifier.height(TteumSpacing.sectionGap))

                    SettingsSectionTitle("앱 사용")
                    SettingsGroup {
                        if (automaticLocationEnabled) {
                            SettingsRow(
                                title = "위치 권한",
                                description = if (locationPermissionGranted) "허용됨" else "허용되지 않음",
                                onClick = onOpenLocationSettings,
                            )
                            SettingsDivider()
                        }
                        SettingsRow(
                            title = "카카오맵 연결 확인",
                            description = if (kakaoMapAvailable) "설치됨" else "웹으로 연결",
                            onClick = onOpenKakaoMap,
                        )
                    }

                    Spacer(Modifier.height(TteumSpacing.sectionGap))
                    SettingsSectionTitle("저장 공간")
                    SettingsGroup {
                        SettingsRow(
                            title = "캐시 지우기",
                            description = "임시 이미지와 지도 데이터를 정리해요.",
                            onClick = { showCacheClearDialog = true },
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = "저장한 장소 비우기",
                            description = guestSavedStorageDescription(savedCount),
                            titleColor = if (savedCount > 0) TteumRed else TteumMuted,
                            onClick = if (savedCount > 0) {
                                { showSavedClearDialog = true }
                            } else {
                                null
                            },
                        )
                    }

                    Spacer(Modifier.height(TteumSpacing.sectionGap))
                    SettingsSectionTitle("약관 및 지원")
                    SettingsGroup {
                        SettingsRow(
                            title = "개인정보처리방침",
                            description = if (privacyPolicyAvailable) "보기" else "문서 준비 중",
                            titleColor = if (privacyPolicyAvailable) TteumInk else TteumMuted,
                            onClick = if (privacyPolicyAvailable) onOpenPrivacyPolicy else null,
                        )
                        if (automaticLocationEnabled && locationTermsAvailable) {
                            SettingsDivider()
                            SettingsRow(
                                title = "위치기반서비스 이용약관",
                                description = "보기",
                                onClick = onOpenLocationTerms,
                            )
                        }
                        SettingsDivider()
                        SettingsRow(
                            title = "문의하기",
                            description = contactEmail,
                            onClick = onContact,
                        )
                    }

                    Spacer(Modifier.height(TteumSpacing.sectionGap))
                    SettingsSectionTitle("앱 정보")
                    SettingsGroup {
                        SettingsRow(
                            title = "앱 버전",
                            description = appVersion,
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = "데이터 출처",
                            description = "한국관광공사 TourAPI · 카카오맵",
                        )
                        SettingsDivider()
                        SettingsRow(
                            title = "오픈소스 라이선스",
                            description = "라이브러리와 폰트 이용 고지 · 오프라인 보기",
                            onClick = { showLicenseNotices = true },
                        )
                    }
                }
            }
        }
    }

    if (accountState.showLoginSheet) {
        LoginSheet(onDismiss = onDismissLogin, onLogin = onLogin)
    }

    AccountDeletionDialogs(
        step = accountState.deletionStep,
        isLoading = accountState.isLoading,
        onContinue = onConfirmDeletionConsequences,
        onRequireReauthentication = onRequireReauthentication,
        onReauthenticate = onReauthenticateForDeletion,
        onCancel = onCancelDeletion,
    )

    if (showSavedClearDialog) {
        SettingsConfirmationDialog(
            onDismissRequest = { showSavedClearDialog = false },
            title = "저장 목록을 비울까요?",
            message = "이 기기에 저장한 장소가 모두 삭제됩니다.",
            confirmText = "삭제",
            onConfirm = {
                onClearSaved()
                showSavedClearDialog = false
            },
        )
    }

    if (showLicenseNotices) {
        LicenseNoticesDialog(onDismiss = { showLicenseNotices = false })
    }

    if (showCacheClearDialog) {
        SettingsConfirmationDialog(
            onDismissRequest = { showCacheClearDialog = false },
            title = "캐시를 지울까요?",
            message = "저장한 장소는 유지되고 임시 데이터만 삭제됩니다.",
            confirmText = "지우기",
            onConfirm = {
                val cleared = onClearCache()
                showCacheClearDialog = false
                onShowMessage(
                    if (cleared) "캐시를 정리했어요." else "캐시를 정리하지 못했어요.",
                )
            },
        )
    }
}

@Composable
private fun SettingsConfirmationDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 420.dp),
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
            ) {
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(
                shape = TteumShapes.control,
                contentPadding = TteumInsets.button,
                onClick = onDismissRequest,
                modifier = Modifier.heightIn(min = TteumSizing.touchTarget),
                colors = ButtonDefaults.textButtonColors(contentColor = TteumMuted),
            ) {
                Text("취소", style = MaterialTheme.typography.labelMedium)
            }
        },
        shape = TteumShapes.dialog,
        containerColor = Color.White,
        tonalElevation = 0.dp,
    )
}

internal fun guestSavedStorageDescription(count: Int): String =
    "이 기기에 ${count}개 저장됨"
