package com.hfremovalsadelaide.invoices.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small on-device preferences. Nothing here is sensitive: only UI switches.
 * Session cookies and invoice data are never stored in these preferences.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _privacyMode = MutableStateFlow(prefs.getBoolean(KEY_PRIVACY_MODE, false))

    /**
     * When true the app window is marked FLAG_SECURE: screenshots and screen recording are
     * blocked and the app preview is blanked in the recent-apps list. Off by default.
     */
    val privacyMode: StateFlow<Boolean> = _privacyMode.asStateFlow()

    fun setPrivacyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply()
        _privacyMode.value = enabled
    }

    companion object {
        const val PREFS_NAME = "hf_settings"
        private const val KEY_PRIVACY_MODE = "privacy_mode"

        @Volatile
        private var shared: AppSettings? = null

        /** One shared instance so the settings screen and the Activity observe the same state. */
        fun get(context: Context): AppSettings =
            shared ?: synchronized(this) {
                shared ?: AppSettings(context).also { shared = it }
            }
    }
}
