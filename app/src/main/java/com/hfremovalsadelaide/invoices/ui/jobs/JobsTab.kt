package com.hfremovalsadelaide.invoices.ui.jobs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hfremovalsadelaide.invoices.data.StaffMember
import com.hfremovalsadelaide.invoices.data.VehicleRecord
import com.hfremovalsadelaide.invoices.jobs.JobsUiState
import com.hfremovalsadelaide.invoices.jobs.JobsViewModel

/**
 * The whole Jobs tab: calendar + dispatch board, job details, inventory & QR, condition & photos, sign-off and notification settings.
 */
@Composable
fun JobsTab(
    state: JobsUiState,
    viewModel: JobsViewModel,
    onGoToSignIn: () -> Unit,
    onDownloadCompletionPdf: (jobId: String) -> Unit,
    permissions: Set<String>,
    onDownloadInventoryPdf: (jobId: String) -> Unit = {},
    canDispatch: Boolean = false,
    availableStaff: List<StaffMember> = emptyList(),
    availableVehicles: List<VehicleRecord> = emptyList(),
    modifier: Modifier = Modifier
) {
    val jobs = state.snapshot?.jobs.orEmpty()
    val openJob = jobs.firstOrNull { it.id == state.openJobId }
    val signoffJob = jobs.firstOrNull { it.id == state.signoffJobId }
    var showNotifications by remember { mutableStateOf(false) }
    var showInventory by remember { mutableStateOf(false) }
    var showCondition by remember { mutableStateOf(false) }
    var showAssignDialog by remember { mutableStateOf(false) }
    var showRescheduleDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().then(modifier)) {
        when {
            signoffJob != null && "jobs.signoff" in permissions -> SignOffScreen(
                job = signoffJob,
                busy = state.busy,
                onSubmit = { name, png -> viewModel.submitSignoff(signoffJob.id, name, png) },
                onSkip = { reason -> viewModel.skipSignoff(signoffJob.id, reason) },
                onDismiss = viewModel::dismissSignoff,
                onSubmitDetailed = { name, png, chargesAck, damage, outstanding ->
                    viewModel.submitSignoff(signoffJob.id, name, png, chargesAck, damage, outstanding)
                },
                onSkipDetailed = { reason, damage, outstanding ->
                    viewModel.skipSignoff(signoffJob.id, reason, damage, outstanding)
                },
                modifier = Modifier.fillMaxSize()
            )

            openJob != null -> {
                JobDetailScreen(
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
                    canDispatch = canDispatch,
                    permissions = permissions,
                    onOpenAssign = { showAssignDialog = true },
                    onOpenReschedule = { showRescheduleDialog = true },
                    onOpenInventory = {
                        viewModel.loadInventory(openJob.id)
                        showInventory = true
                    },
                    onOpenCondition = { showCondition = true },
                    onRecordCrewEvent = { name, type, ack, ackName, notes ->
                        viewModel.recordCrewEvent(openJob.id, name, type, ack, ackName, notes)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (showInventory) {
                    JobInventorySheet(
                        jobId = openJob.id,
                        jobTitle = openJob.title,
                        inventory = state.inventory,
                        loading = state.inventoryLoading,
                        busy = state.busy,
                        onAddRoom = { viewModel.addRoom(openJob.id, it) },
                        onSaveItem = { viewModel.saveInventoryItem(openJob.id, it) },
                        onScanBarcode = { code, status, note -> viewModel.scanBarcode(openJob.id, code, status, note) },
                        onDownloadInventoryPdf = { onDownloadInventoryPdf(openJob.id) },
                        onDismiss = { showInventory = false }
                    )
                }

                if (showCondition) {
                    JobConditionSheet(
                        job = openJob,
                        busy = state.busy,
                        onSaveConditionReport = { viewModel.saveConditionReport(openJob.id, it) },
                        onUploadPhoto = { phase, caption, b64 -> viewModel.uploadPhoto(openJob.id, phase, caption, b64) },
                        onDismiss = { showCondition = false }
                    )
                }

                if (showAssignDialog) {
                    AssignCrewDialog(
                        job = openJob,
                        staff = availableStaff,
                        vehicles = availableVehicles,
                        otherJobs = state.snapshot?.jobs.orEmpty(),
                        onDismiss = { showAssignDialog = false },
                        onSave = { assignments, vehicleId, truck, travel, dispatch ->
                            showAssignDialog = false
                            viewModel.assignJob(openJob.id, assignments, vehicleId, truck, travel, dispatch)
                        }
                    )
                }

                if (showRescheduleDialog) {
                    RescheduleJobDialog(
                        job = openJob,
                        onDismiss = { showRescheduleDialog = false },
                        onSave = { startIso, endIso, reason ->
                            showRescheduleDialog = false
                            viewModel.rescheduleJob(openJob.id, startIso, endIso, reason)
                        }
                    )
                }
            }

            else -> JobsScreen(
                state = state,
                onSync = viewModel::syncCalendar,
                canSyncCalendar = "calendar.sync" in permissions,
                onRefresh = viewModel::load,
                onSelectView = viewModel::selectView,
                onSelectDate = viewModel::selectDate,
                onOpenJob = viewModel::openJob,
                onGoToSignIn = onGoToSignIn,
                onOpenNotifications = { showNotifications = true },
                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                onStatusFilterSelected = viewModel::onStatusFilterSelected,
                canDispatch = canDispatch,
                availableStaff = availableStaff,
                availableVehicles = availableVehicles,
                onAssignJob = viewModel::assignJob,
                onRescheduleJob = viewModel::rescheduleJob,
                onDispatchFilterChanged = viewModel::setDispatchFilter,
                onSetPriority = viewModel::setPriority,
                modifier = Modifier.fillMaxSize()
            )
        }
        state.scheduleConflict?.let { prompt ->
            ScheduleConflictDialog(
                prompt = prompt,
                canOverride = "jobs.override" in permissions,
                onConfirm = viewModel::confirmScheduleOverride,
                onDismiss = viewModel::dismissScheduleConflict
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
