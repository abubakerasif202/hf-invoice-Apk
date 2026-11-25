package com.hfremovalsadelaide.invoices.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.jobs.OVERRIDE_REASON_MIN
import com.hfremovalsadelaide.invoices.jobs.ScheduleConflictPrompt
import com.hfremovalsadelaide.invoices.jobs.describeConflict

/**
 * Shown when the server refuses an assignment or reschedule because of a clash it found across all bookings.
 * Only a user holding jobs.override sees the override field; the server enforces that permission regardless.
 */
@Composable
fun ScheduleConflictDialog(
    prompt: ScheduleConflictPrompt,
    canOverride: Boolean,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember(prompt) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("schedule_conflict_dialog"),
        title = { Text("Scheduling conflict") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                prompt.conflicts.forEach { conflict ->
                    Text(
                        text = "• ${describeConflict(conflict)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("schedule_conflict_item")
                    )
                }
                if (canOverride) {
                    Text(
                        "You can override this. The reason is required and is recorded in the audit log with the conflicts.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason for override (min $OVERRIDE_REASON_MIN characters)") },
                        modifier = Modifier.testTag("schedule_override_reason")
                    )
                } else {
                    Text(
                        "Nothing was changed. Choose a different crew, truck or time, or ask an administrator or dispatcher to approve an override.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            if (canOverride) {
                Button(
                    onClick = { onConfirm(reason) },
                    enabled = reason.trim().length >= OVERRIDE_REASON_MIN,
                    modifier = Modifier.testTag("schedule_override_confirm")
                ) { Text("Override and save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (canOverride) "Cancel" else "OK") } }
    )
}
