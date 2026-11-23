package com.hfremovalsadelaide.invoices.ui.jobs

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hfremovalsadelaide.invoices.jobs.JobsUiState
import com.hfremovalsadelaide.invoices.jobs.JobsViewModel

/**
 * The whole Jobs tab: calendar, job details, sign-off and notification settings.
 * Kept as one component so the same flow runs in the app and in the on-device end-to-end tests.
 */
@Composable
fun JobsTab(
    state: JobsUiState,
    viewModel: JobsViewModel,
    onGoToSignIn: () -> Unit,
    onDownloadCompletionPdf: (jobId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val jobs = state.snapshot?.jobs.orEmpty()
    val openJob = jobs.firstOrNull { it.id == state.openJobId }
    val signoffJob = jobs.firstOrNull { it.id == state.signoffJobId }
    var showNotifications by remember { mutableStateOf(false) }

    Box(modifier) {
        when {
            signoffJob != null -> SignOffScreen(
                job = signoffJob,
                busy = state.busy,
                onSubmit = { name, png -> viewModel.submitSignoff(signoffJob.id, name, png) },
                onSkip = { reason -> viewModel.skipSignoff(signoffJob.id, reason) },
                onDismiss = viewModel::dismissSignoff,
                modifier = Modifier.matchParentSize()
            )

            openJob != null -> JobDetailScreen(
                job = openJob,
                busy = state.busy,
                draft = state.draft,
                onBack = { viewModel.openJob(null) },
                onAct = { viewModel.act(openJob.id, it) },
                onCorrect = { field, at, reason -> viewModel.correct(openJob.id, field, at, reason) },
                onOpenSignoff = { viewModel.openSignoff(openJob.id) },
                onDownloadCompletionPdf = { onDownloadCompletionPdf(openJob.id) },
                onCreateInvoice = { viewModel.requestInvoiceDraft(openJob.id) },
                onConfirmDraft = viewModel::confirmInvoiceDraft,
                onDismissDraft = viewModel::dismissDraft,
                modifier = Modifier.matchParentSize()
            )

            else -> JobsScreen(
                state = state,
                onSync = viewModel::syncCalendar,
                onRefresh = viewModel::load,
                onSelectView = viewModel::selectView,
                onSelectDate = viewModel::selectDate,
                onOpenJob = viewModel::openJob,
                onGoToSignIn = onGoToSignIn,
                onOpenNotifications = { showNotifications = true },
                modifier = Modifier.matchParentSize()
            )
        }
        if (showNotifications) {
            JobNotificationSheet(
                preferences = state.preferences,
                onToggle = viewModel::setPreference,
                onDismiss = { showNotifications = false }
            )
        }
    }
}
