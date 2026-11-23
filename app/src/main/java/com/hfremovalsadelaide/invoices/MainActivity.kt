package com.hfremovalsadelaide.invoices

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.hfremovalsadelaide.invoices.jobs.JobNotifier
import com.hfremovalsadelaide.invoices.jobs.JobsBackground
import com.hfremovalsadelaide.invoices.pdf.DownloadNotificationHelper
import com.hfremovalsadelaide.invoices.settings.AppSettings
import com.hfremovalsadelaide.invoices.settings.PrivacyWindowBinder
import com.hfremovalsadelaide.invoices.ui.MainAppShell
import com.hfremovalsadelaide.invoices.ui.theme.HFRemovalsTheme

class MainActivity : ComponentActivity() {

    /** Set when a job notification was tapped; the shell opens that job and then clears it. */
    private val pendingJobId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )

        DownloadNotificationHelper.ensureChannel(this)
        JobsBackground.start(this)
        pendingJobId.value = intent?.getStringExtra(JobNotifier.EXTRA_JOB_ID)

        // Optional Privacy Mode (off by default): FLAG_SECURE blocks screenshots/recording.
        PrivacyWindowBinder.bind(this, AppSettings.get(this))

        setContent {
            HFRemovalsTheme {
                MainAppShell(
                    initialJobId = pendingJobId.value,
                    onJobIntentConsumed = { pendingJobId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(JobNotifier.EXTRA_JOB_ID)?.let { pendingJobId.value = it }
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        super.onStop()
    }
}
