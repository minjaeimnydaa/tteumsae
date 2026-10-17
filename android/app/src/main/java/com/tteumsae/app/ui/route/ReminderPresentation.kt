package com.tteumsae.app.ui.route

import com.tteumsae.app.reminder.ReminderDeliveryBlockReason

internal data class ReminderPresentation(
    val message: String,
    val showScheduledTime: Boolean,
    val showSettingsAction: Boolean,
)

internal fun reminderPresentation(
    checked: Boolean,
    canEnable: Boolean,
    blockedReason: ReminderDeliveryBlockReason?,
): ReminderPresentation {
    val blockedMessage = when (blockedReason) {
        ReminderDeliveryBlockReason.RUNTIME_PERMISSION -> "알림 권한이 없어 알림을 받을 수 없어요"
        ReminderDeliveryBlockReason.APP_NOTIFICATIONS_DISABLED -> "휴대폰 설정에서 틈새 알림이 꺼져 있어요"
        ReminderDeliveryBlockReason.CHANNEL_DISABLED -> "휴대폰 설정에서 출발 시간 알림이 꺼져 있어요"
        null -> null
    }
    return ReminderPresentation(
        message = blockedMessage ?: when {
            !canEnable -> "현재 교통으로 다시 확인한 뒤 켜 주세요"
            checked -> "출발 5분 전 예정 · 기기 절전 등으로 늦어질 수 있어요"
            else -> "선택 알림 · 기기 절전 등으로 늦어질 수 있어요"
        },
        showScheduledTime = checked && blockedReason == null,
        showSettingsAction = blockedReason != null,
    )
}
