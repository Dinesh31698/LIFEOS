package com.lifeos.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifeos.app.data.LifeOsDatabase
import com.lifeos.app.ui.navigation.LifeOsNavGraph
import com.lifeos.app.ui.theme.BackgroundDark
import com.lifeos.app.ui.theme.LIFEOSTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = LifeOsDatabase.getDatabase(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(db.lifeOsDao()) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.lifeos.app.data.LifeMapRepository.initialize(applicationContext)
        com.lifeos.app.ui.theme.ThemeManager.initialize(applicationContext)
        handleModeSelectionIntent(intent)
        val routeExtra = intent?.getStringExtra("navigate_to") ?: intent?.getStringExtra("route")

        setContent {
            LIFEOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    LifeOsNavGraph(viewModel = viewModel, initialRoute = routeExtra)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleModeSelectionIntent(intent)
    }

    private fun handleModeSelectionIntent(intent: Intent?) {
        if (intent == null) return
        val targetModeId = intent.getIntExtra("target_mode_id", -1).takeIf { it != -1 }
            ?: intent.getIntExtra("mode_id", -1).takeIf { it != -1 }
        val modeName = intent.getStringExtra("mode_name")
        if (intent.hasExtra("safe_mode")) {
            val enableSafeMode = intent.getBooleanExtra("safe_mode", true)
            com.lifeos.app.engine.SafeModeManager.setSafeMode(enableSafeMode)
        }
        if (targetModeId != null) {
            viewModel.selectMode(targetModeId)
        } else if (!modeName.isNullOrBlank()) {
            val resolvedId = when {
                modeName.contains("College", ignoreCase = true) -> 1
                modeName.contains("Gym", ignoreCase = true) -> 2
                modeName.contains("Travel", ignoreCase = true) -> 3
                else -> 1
            }
            viewModel.selectMode(resolvedId)
        }
    }
}