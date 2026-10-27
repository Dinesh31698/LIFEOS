package com.lifeos.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LIFEOS Theme System Modes.
 * Supports real-time switching without app restart.
 */
enum class AppThemeMode(val displayName: String, val description: String) {
    SYSTEM("System Default", "Follows device dark/light setting"),
    DARK("Cybernetic Dark", "Deep indigo & dark cybernetic palette"),
    LIGHT("Frosted Light", "Clean light background with frosted glass cards"),
    AMOLED("AMOLED Pitch Black", "Pure pitch black (#000000) for maximum OLED battery savings")
}

/**
 * Persistent Manager for LIFEOS Theme.
 * Stores selected mode in SharedPreferences and provides a reactive StateFlow.
 */
object ThemeManager {
    private const val PREFS_NAME = "lifeos_theme_prefs"
    private const val KEY_THEME_MODE = "lifeos_app_theme_mode"

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        _themeMode.value = try {
            AppThemeMode.valueOf(saved)
        } catch (e: Exception) {
            AppThemeMode.DARK
        }
        isInitialized = true
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        _themeMode.value = mode
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
