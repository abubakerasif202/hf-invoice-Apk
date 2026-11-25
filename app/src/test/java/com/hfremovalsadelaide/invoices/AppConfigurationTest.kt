package com.hfremovalsadelaide.invoices

import android.content.Context
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.hfremovalsadelaide.invoices.pdf.DownloadNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppConfigurationTest {

    @Test
    fun verifiesAppNameAndPackageIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
        assertTrue(context.packageName in setOf("com.hfremovalsadelaide.invoices", "com.hfremovalsadelaide.invoices.qa"))
        assertEquals("HF Removals Invoices", context.getString(R.string.app_name))
        assertTrue(context.getString(R.string.app_name).length <= 30)
    }

    @Test
    fun verifiesBackupDisabledAndNotificationChannelInitializes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val allowBackup = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
        assertEquals(false, allowBackup)

        DownloadNotificationHelper.ensureChannel(context)
    }

    @Test
    fun mainActivityHandlesConfigChangesItselfSoTheWebPageSurvivesThem() {
        // Without these, a dark-mode toggle, language change or font-size change recreates the
        // Activity and the WebView reloads from the start page, losing the user's place and input.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )
        val required = mapOf(
            "orientation" to ActivityInfo.CONFIG_ORIENTATION,
            "screenSize" to ActivityInfo.CONFIG_SCREEN_SIZE,
            "smallestScreenSize" to ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            "screenLayout" to ActivityInfo.CONFIG_SCREEN_LAYOUT,
            "keyboardHidden" to ActivityInfo.CONFIG_KEYBOARD_HIDDEN,
            "uiMode" to ActivityInfo.CONFIG_UI_MODE,
            "locale" to ActivityInfo.CONFIG_LOCALE,
            "layoutDirection" to ActivityInfo.CONFIG_LAYOUT_DIRECTION,
            "fontScale" to ActivityInfo.CONFIG_FONT_SCALE,
            "density" to ActivityInfo.CONFIG_DENSITY
        )
        val missing = required.filter { (_, flag) -> info.configChanges and flag == 0 }.keys
        assertTrue("MainActivity must declare configChanges for: $missing", missing.isEmpty())
    }
}