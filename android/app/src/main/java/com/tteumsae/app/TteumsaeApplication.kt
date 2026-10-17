package com.tteumsae.app

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk
import com.tteumsae.app.reminder.ReminderNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TteumsaeApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifications.createChannel(this)
        applicationScope.launch {
            container.savedPlacePreferencesMigration.migrateIfNeeded()
        }
        if (BuildConfig.KAKAO_MAP_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_MAP_NATIVE_APP_KEY)
        }
    }
}
