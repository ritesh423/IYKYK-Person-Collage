package com.ritesh.iykykcollage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ritesh.iykykcollage.ui.CollageRoute
import com.ritesh.iykykcollage.ui.theme.IYKYKCollageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IYKYKCollageTheme {
                CollageRoute()
            }
        }
    }
}

