package com.tteumsae.app

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class TteumsaeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_MAP_NATIVE_APP_KEY.isNotBlank()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_MAP_NATIVE_APP_KEY)
        }
    }
}
