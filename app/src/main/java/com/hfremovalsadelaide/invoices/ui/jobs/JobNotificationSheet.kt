package com.hfremovalsadelaide.invoices.ui.jobs

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.NotificationCategory
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobNotificationSheet(
    preferences: Map<NotificationCategory, Boolean>,
    onToggle: (NotificationCategory, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // Android 13+ needs the runtime permission; asking when the user turns a reminder on explains why.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Job notifications", style = MaterialTheme.typography.titleLarge, color = ForestGreen, fontWeight = FontWeight.Bold)
            Text(
                "Reminders are scheduled with Android's background work system, so they can arrive a few minutes late when the phone is in battery saver. " +
                    "The lock screen never shows customer phone numbers or addresses.",
                style = MaterialTheme.typography.bodySmall, color = MutedSage
            )
            NotificationCategory.entries.forEach { category ->
                val on = preferences[category] != false
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = on,
                        onCheckedChange = {
                            if (it && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            onToggle(category, it)
                        },
                        modifier = Modifier.semantics { role = Role.Switch }.testTag("notify_${category.serverKey}")
                    )
                }
            }
        }
    }
}
