package com.tteumsae.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tteumsae.app.ui.TteumsaeApp
import com.tteumsae.app.ui.theme.TteumsaeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TteumsaeTheme {
                TteumsaeApp()
            }
        }
    }
}

