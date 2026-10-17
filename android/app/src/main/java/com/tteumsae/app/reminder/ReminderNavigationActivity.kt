package com.tteumsae.app.reminder

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.tteumsae.app.MainActivity

/** Notification entry point: a tap carries only an opaque token, never an old route URI. */
class ReminderNavigationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val now = System.currentTimeMillis()
        val store = ActiveTripStore(SharedPreferencesActiveTripPreferences(applicationContext))
        val url = navigationUrlForReminderTap(
            trip = store.loadValid(now),
            requestedMode = intent.getStringExtra(EXTRA_LOCATION_MODE),
            requestedToken = intent.getStringExtra(EXTRA_NAVIGATION_TOKEN),
            nowEpochMillis = now,
        )
        if (url != null) {
            if (runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isFailure) {
                showApp("지도를 열 수 없어요. 앱에서 경로를 다시 확인해 주세요.")
            }
        } else {
            showApp("이전 알림이 만료됐어요. 출발지를 다시 선택해 주세요.")
        }
        finish()
    }

    private fun showApp(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    internal companion object {
        const val EXTRA_LOCATION_MODE = "reminder_location_mode"
        const val EXTRA_NAVIGATION_TOKEN = "reminder_navigation_token"
    }
}
