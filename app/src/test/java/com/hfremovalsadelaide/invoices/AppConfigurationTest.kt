package com.hfremovalsadelaide.invoices

import android.content.Context
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
        assertEquals("com.hfremovalsadelaide.invoices", context.packageName)
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
}
