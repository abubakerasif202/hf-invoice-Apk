package com.hfremovalsadelaide.invoices.settings

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/** Keeps the Activity window's FLAG_SECURE in step with the Privacy Mode setting. */
object PrivacyWindowBinder {

    fun bind(activity: ComponentActivity, settings: AppSettings) {
        // Apply the persisted value synchronously so the very first frame is already protected.
        apply(activity, settings.privacyMode.value)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.CREATED) {
                settings.privacyMode.collect { apply(activity, it) }
            }
        }
    }

    private fun apply(activity: ComponentActivity, enabled: Boolean) {
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
