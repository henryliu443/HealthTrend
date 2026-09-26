package com.healthtrend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.healthtrend.ui.navigation.HealthTrendNavHost
import com.healthtrend.ui.theme.HealthTrendTheme

/**
 * The single Activity hosting the whole Compose tree.
 *
 * The Koin graph is started by [HealthTrendApplication], so nothing is wired here beyond the theme
 * and the navigation graph.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HealthTrendTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HealthTrendNavHost()
                }
            }
        }
    }
}
