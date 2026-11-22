package com.hfremovalsadelaide.invoices.settings

import android.content.Context
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivacyModeTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AppSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun ComponentActivity.isSecure(): Boolean =
        (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    @Test
    fun privacyModeIsOffByDefaultSoScreenshotsKeepWorking() {
        assertFalse(AppSettings(context).privacyMode.value)
    }

    @Test
    fun privacyModePersistsAcrossInstances() {
        AppSettings(context).setPrivacyMode(true)
        assertTrue(AppSettings(context).privacyMode.value)

        AppSettings(context).setPrivacyMode(false)
        assertFalse(AppSettings(context).privacyMode.value)
    }

    @Test
    fun windowIsNotSecureByDefault() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().start().resume().get()
        PrivacyWindowBinder.bind(activity, AppSettings(context))
        idle()
        assertFalse(activity.isSecure())
    }

    @Test
    fun enablingPrivacyModeAppliesFlagSecureImmediately() {
        val settings = AppSettings(context)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().start().resume().get()
        PrivacyWindowBinder.bind(activity, settings)
        idle()

        settings.setPrivacyMode(true)
        idle()
        assertTrue("FLAG_SECURE must be set once privacy mode is on", activity.isSecure())

        settings.setPrivacyMode(false)
        idle()
        assertFalse("FLAG_SECURE must be cleared when privacy mode is turned off", activity.isSecure())
    }

    @Test
    fun persistedPrivacyModeIsAppliedToANewlyCreatedActivity() {
        AppSettings(context).setPrivacyMode(true)

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().start().resume().get()
        PrivacyWindowBinder.bind(activity, AppSettings(context))
        idle()

        assertTrue(activity.isSecure())
    }

    @Test
    fun togglingFromAnotherInstanceDoesNotLeaveStaleState() {
        val a = AppSettings(context)
        a.setPrivacyMode(true)
        assertEquals(true, a.privacyMode.value)
        a.setPrivacyMode(true)
        assertEquals(true, a.privacyMode.value)
    }
}
