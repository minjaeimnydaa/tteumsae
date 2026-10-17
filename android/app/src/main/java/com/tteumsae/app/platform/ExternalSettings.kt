package com.tteumsae.app.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import com.tteumsae.app.BuildConfig

internal const val CONTACT_EMAIL = "godburgundy@gmail.com"
internal val PRIVACY_POLICY_URL = "${BuildConfig.API_BASE_URL}/privacy"
internal const val LOCATION_TERMS_URL = ""

internal val savedImageCache = object : LruCache<String, Bitmap>(16 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

internal fun clearAppCache(context: Context): Boolean = runCatching {
    savedImageCache.evictAll()
    context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
}.isSuccess

internal fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

internal fun openPolicy(context: Context, url: String) {
    if (url.isBlank()) {
        Toast.makeText(context, "공개 문서를 준비 중이에요.", Toast.LENGTH_SHORT).show()
        return
    }
    if (runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isFailure
    ) {
        Toast.makeText(context, "문서를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

internal fun openContactEmail(context: Context) {
    val subject = Uri.encode("[틈새] 앱 문의")
    val body = Uri.encode("앱 버전: ${BuildConfig.VERSION_NAME}\n\n문의 내용을 작성해 주세요.")
    val intent = Intent(
        Intent.ACTION_SENDTO,
        Uri.parse("mailto:$CONTACT_EMAIL?subject=$subject&body=$body"),
    )
    if (runCatching { context.startActivity(intent) }.isFailure) {
        Toast.makeText(context, "메일 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}
