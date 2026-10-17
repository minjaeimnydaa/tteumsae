package com.tteumsae.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tteumsae.app.data.auth.AuthDeepLinkHandler
import com.tteumsae.app.data.auth.SupabaseAuthDeepLinkGateway
import com.tteumsae.app.ui.TteumsaeApp
import com.tteumsae.app.ui.theme.TteumsaeTheme

class MainActivity : ComponentActivity() {
    private val authDeepLinkHandler: AuthDeepLinkHandler by lazy {
        val container = (application as TteumsaeApplication).container
        AuthDeepLinkHandler(
            gateway = container.supabaseClient?.let(::SupabaseAuthDeepLinkGateway),
            onFailure = container.authRepository::reportLoginFailure,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authDeepLinkHandler.handle(intent)
        setContent {
            TteumsaeTheme {
                TteumsaeApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        authDeepLinkHandler.handle(intent)
    }
}

