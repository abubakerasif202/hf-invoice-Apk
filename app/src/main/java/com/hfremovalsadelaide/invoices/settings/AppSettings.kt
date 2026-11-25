package com.hfremovalsadelaide.invoices.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System default"),
    LIGHT("light", "Warm Light"),
    DARK("dark", "Forest Dark");

    companion object {
        fun fromKey(raw: String?) = entries.firstOrNull { it.key == raw } ?: SYSTEM
    }
}

/**
 * Small on-device UI preferences. Sensitive cookies, encrypted job caches, and offline queues are
 * stored separately and wiped on logout.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _privacyMode = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_MODE, false))
    private val _themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null)))

    /**
     * When true the app window is marked FLAG_SECURE: screenshots and screen recording are
     * blocked and the app preview is blanked in the recent-apps list. Off by default.
     */
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setPrivacyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply()
        _privacyMode.value = enabled
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
        _themeMode.value = mode
    }

    companion object {
        const val PREFS_NAME = "hf_settings"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        private const val KEY_THEME_MODE = "theme_mode"

        @Volatile
        private var shared: AppSettings? = null

        /** One shared instance so the settings screen and the Activity observe the same state. */
        fun get(context: Context): AppSettings =
            shared ?: synchronized(this) {
                shared ?: AppSettings(context).also { shared = it }
            }
    }
}
