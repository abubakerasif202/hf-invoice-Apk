package com.hfremovalsadelaide.invoices.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfremovalsadelaide.invoices.auth.AuthRepository
import com.hfremovalsadelaide.invoices.auth.SessionUser
import com.hfremovalsadelaide.invoices.data.AnalyticsSnapshot
import com.hfremovalsadelaide.invoices.data.CrmSnapshot
import com.hfremovalsadelaide.invoices.data.FleetSnapshot
import com.hfremovalsadelaide.invoices.data.NotificationFeedItem
import com.hfremovalsadelaide.invoices.data.PlatformRepository
import com.hfremovalsadelaide.invoices.data.RateCard
import com.hfremovalsadelaide.invoices.data.StaffMember
import com.hfremovalsadelaide.invoices.data.TimesheetsSnapshot
import com.hfremovalsadelaide.invoices.network.ApiResult
import com.hfremovalsadelaide.invoices.offline.OfflineSyncQueue
import com.hfremovalsadelaide.invoices.offline.QueuedOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Navigation sections accessible inside the "More" hub or via sub-navigation.
 */
enum class MoreSection {
    HUB,
    STAFF,
    FLEET,
    TIMESHEETS,
    ANALYTICS,
    NOTIFICATIONS,
    OFFLINE_QUEUE,
    WORKSPACE
}

data class PlatformUiState(
    val checkingSession: Boolean = true,
    val authRequired: Boolean = false,
    val user: SessionUser? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val showSignInSheet: Boolean = false,
    val isOffline: Boolean = false,
    val queuedOps: List<QueuedOperation> = emptyList(),
    val syncBusy: Boolean = false,
    val staff: List<StaffMember> = emptyList(),
    val fleet: FleetSnapshot? = null,
    val rateCards: List<RateCard> = emptyList(),
    val crm: CrmSnapshot? = null,
    val timesheets: TimesheetsSnapshot? = null,
    val analytics: AnalyticsSnapshot? = null,
    val notifications: List<NotificationFeedItem> = emptyList(),
    val loadingModule: Boolean = false,
    val lastInviteOrResetToken: String? = null,
    val portalLinkPreview: String? = null,
    val renderedTemplate: Pair<String, String>? = null,
    val feedbackMessage: String? = null
) {
    val unreadNotificationsCount: Int
        get() = notifications.count { it.readAt == null }

    val pendingOfflineCount: Int
        get() = queuedOps.count { it.status == com.hfremovalsadelaide.invoices.offline.QueueOpStatus.PENDING }

    val conflictOfflineCount: Int
        get() = queuedOps.count { it.status != com.hfremovalsadelaide.invoices.offline.QueueOpStatus.PENDING }
}

class PlatformViewModel(
    private val authRepository: AuthRepository,
    private val syncQueue: OfflineSyncQueue,
    private val scheduleBackgroundSync: () -> Unit = {}
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlatformUiState(
            user = authRepository.cachedUser(),
            queuedOps = syncQueue.operations.value
        )
    )
    val state: StateFlow<PlatformUiState> = _state.asStateFlow()
    private val accountJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val accountScope = CoroutineScope(viewModelScope.coroutineContext + accountJob)
    private var sessionRefresh: Job? = null

    private fun stateForAccount(user: SessionUser?): PlatformUiState {
        val current = _state.value
        if (user != null && current.user?.id == user.id &&
            current.user.role == user.role && current.user.permissions == user.permissions
        ) return current
        accountJob.cancelChildren()
        return PlatformUiState(isOffline = current.isOffline, queuedOps = syncQueue.operations.value)
    }

    init {
        viewModelScope.launch {
            syncQueue.operations.collect { ops ->
                _state.update { it.copy(queuedOps = ops) }
            }
        }
        refreshSession()
    }

    fun setOfflineStatus(offline: Boolean) {
        val wasOffline = _state.value.isOffline
        _state.update { it.copy(isOffline = offline) }
        if (wasOffline && !offline && syncQueue.pendingCount > 0) {
            flushOfflineQueue()
        }
    }

    fun openSignInSheet() {
        _state.update { it.copy(showSignInSheet = true, authError = null) }
    }

    fun dismissSignInSheet() {
        _state.update { it.copy(showSignInSheet = false, authError = null) }
    }

    fun refreshSession() {
        if (_state.value.authBusy || sessionRefresh?.isActive == true) return
        sessionRefresh = viewModelScope.launch {
            _state.update { it.copy(checkingSession = true) }
            when (val res = authRepository.fetchMe()) {
                is ApiResult.Success -> {
                    _state.value =
                        stateForAccount(res.data).copy(
                            checkingSession = false,
                            authRequired = false,
                            user = res.data,
                            showSignInSheet = false,
                            authError = null
                        )
                    preloadRoleSafeModules(res.data)
                }
                ApiResult.AuthRequired -> {
                    _state.value =
                        stateForAccount(null).copy(
                            checkingSession = false,
                            authRequired = true,
                            user = null
                        )
                }
                is ApiResult.NetworkError -> {
                    // Offline with cached encrypted session: remain usable for safe cached operations.
                    _state.update {
                        it.copy(
                            checkingSession = false,
                            authRequired = it.user == null
                        )
                    }
                }
                is ApiResult.Forbidden -> {
                    _state.value =
                        stateForAccount(null).copy(
                            checkingSession = false,
                            authRequired = true,
                            authError = res.message,
                            user = null
                        )
                }
                is ApiResult.ServerError -> {
                    _state.value = stateForAccount(null).copy(
                        checkingSession = false, authRequired = true, authError = res.message
                    )
                }
            }
        }
    }

    fun loginAdmin(password: String, onAuthenticated: () -> Unit = {}) {
        if (password.isBlank()) {
            _state.update { it.copy(authError = "Enter your administrator password.") }
            return
        }
        if (_state.value.authBusy) return
        sessionRefresh?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authError = null) }
            when (val res = authRepository.loginAdmin(password)) {
                is ApiResult.Success -> {
                    _state.value =
                        stateForAccount(res.data).copy(
                            checkingSession = false,
                            authBusy = false,
                            authRequired = false,
                            user = res.data,
                            showSignInSheet = false,
                            authError = null,
                            feedbackMessage = "Signed in as ${res.data.name}"
                        )
                    preloadRoleSafeModules(res.data)
                    onAuthenticated()
                }
                else -> _state.update { it.copy(checkingSession = false, authBusy = false, authError = extractAuthError(res)) }
            }
        }
    }

    fun loginStaff(email: String, password: String, onAuthenticated: () -> Unit = {}) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(authError = "Enter your email and password.") }
            return
        }
        if (_state.value.authBusy) return
        sessionRefresh?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authError = null) }
            when (val res = authRepository.loginStaff(email, password)) {
                is ApiResult.Success -> {
                    _state.value =
                        stateForAccount(res.data).copy(
                            checkingSession = false,
                            authBusy = false,
                            authRequired = false,
                            user = res.data,
                            showSignInSheet = false,
                            authError = null,
                            feedbackMessage = "Signed in as ${res.data.name}"
                        )
                    preloadRoleSafeModules(res.data)
                    onAuthenticated()
                }
                else -> _state.update { it.copy(checkingSession = false, authBusy = false, authError = extractAuthError(res)) }
            }
        }
    }

    fun acceptInvite(token: String, password: String, onAuthenticated: () -> Unit = {}) {
        if (token.isBlank() || password.length < 10) {
            _state.update { it.copy(authError = "Enter the invitation token and a password of at least 10 characters.") }
            return
        }
        if (_state.value.authBusy) return
        sessionRefresh?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authError = null) }
            when (val res = authRepository.acceptInvite(token, password)) {
                is ApiResult.Success -> {
                    _state.value =
                        stateForAccount(res.data).copy(
                            checkingSession = false,
                            authBusy = false,
                            authRequired = false,
                            user = res.data,
                            showSignInSheet = false,
                            feedbackMessage = "Account activated for ${res.data.name}"
                        )
                    preloadRoleSafeModules(res.data)
                    onAuthenticated()
                }
                else -> _state.update { it.copy(checkingSession = false, authBusy = false, authError = extractAuthError(res)) }
            }
        }
    }

    fun resetPassword(token: String, newPassword: String) {
        if (token.isBlank() || newPassword.length < 10) {
            _state.update { it.copy(authError = "Enter the reset token and a password of at least 10 characters.") }
            return
        }
        if (_state.value.authBusy) return
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authError = null) }
            when (val res = authRepository.resetPassword(token, newPassword)) {
                is ApiResult.Success -> _state.update {
                    it.copy(authBusy = false, authError = null, feedbackMessage = "Password updated. Sign in with your new password.")
                }
                else -> _state.update { it.copy(authBusy = false, authError = extractAuthError(res)) }
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        if (_state.value.authBusy) return
        sessionRefresh?.cancel()
        viewModelScope.launch {
            _state.value = stateForAccount(null).copy(authRequired = true, authBusy = true)
            val revoked = authRepository.logout()
            _state.value = stateForAccount(null).copy(
                checkingSession = false,
                authRequired = true,
                feedbackMessage = if (revoked) "Signed out and local cache cleared" else
                    "Signed out on this device. The server session could not be revoked."
            )
            onComplete()
        }
    }

    fun preloadRoleSafeModules(user: SessionUser? = _state.value.user) {
        user ?: return
        loadNotifications()
        if (user.can("fleet.read")) loadFleet()
        if (user.can("staff.manage") || user.can("jobs.assign")) loadStaff()
        if (user.can("invoices.read") || user.can("crm.read")) loadRateCards()
        if (user.can("reports.read")) loadAnalytics()
        if (user.can("crm.read")) loadCrm()
        loadTimesheets()
    }

    fun loadStaff() = accountScope.launch {
        val res = PlatformRepository.fetchStaff()
        if (res is ApiResult.Success) _state.update { it.copy(staff = res.data) }
    }

    fun createStaff(name: String, email: String, phone: String, role: String, employmentType: String, hourlyRate: String?) {
        accountScope.launch {
            _state.update { it.copy(loadingModule = true) }
            when (val res = PlatformRepository.createStaff(name, email, phone, role, employmentType, hourlyRate)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            loadingModule = false,
                            lastInviteOrResetToken = res.data,
                            feedbackMessage = "Staff invite created"
                        )
                    }
                    loadStaff()
                }
                else -> handleMutationFailure(res)
            }
        }
    }

    fun resetStaffPassword(staffId: String) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PlatformRepository.resetStaffPassword(staffId)) {
            is ApiResult.Success -> _state.update {
                it.copy(loadingModule = false, lastInviteOrResetToken = res.data, feedbackMessage = "Reset token issued")
            }
            else -> handleMutationFailure(res)
        }
    }

    fun setStaffActive(staffId: String, active: Boolean) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PlatformRepository.setStaffActive(staffId, active)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = if (active) "Account restored" else "Account revoked") }
                loadStaff()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun loadFleet() = accountScope.launch {
        val res = PlatformRepository.fetchFleet()
        if (res is ApiResult.Success) _state.update { it.copy(fleet = res.data) }
    }

    fun saveVehicle(
        id: String?,
        name: String,
        registration: String,
        vehicleClass: String,
        capacityTonnes: String?,
        status: String,
        regoExpiry: String?,
        nextServiceDate: String?,
        notes: String
    ) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PlatformRepository.saveVehicle(id, name, registration, vehicleClass, capacityTonnes, status, regoExpiry, nextServiceDate, notes)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Vehicle saved") }
                loadFleet()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun addVehicleLog(vehicleId: String, kind: String, occurredOn: String, cost: String?, odometer: Int?, vendor: String, notes: String) =
        accountScope.launch {
            _state.update { it.copy(loadingModule = true) }
            when (val res = PlatformRepository.addVehicleLog(vehicleId, kind, occurredOn, cost, odometer, vendor, notes)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loadingModule = false, feedbackMessage = "Fleet log recorded") }
                    loadFleet()
                }
                else -> handleMutationFailure(res)
            }
        }

    fun loadRateCards() = accountScope.launch {
        val res = PlatformRepository.fetchRateCards()
        if (res is ApiResult.Success) _state.update { it.copy(rateCards = res.data) }
    }

    fun loadCrm() = accountScope.launch {
        val res = PlatformRepository.fetchCrm()
        if (res is ApiResult.Success) _state.update { it.copy(crm = res.data) }
    }

    fun saveCustomer(id: String?, name: String, kind: String, email: String, phone: String, address: String, notes: String) =
        accountScope.launch {
            _state.update { it.copy(loadingModule = true) }
            when (val res = PlatformRepository.saveCustomer(id, name, kind, email, phone, address, notes)) {
                is ApiResult.Success -> {
                    val dupNote = if (res.data.second > 0) " (${res.data.second} possible duplicate found)" else ""
                    _state.update { it.copy(loadingModule = false, feedbackMessage = "Customer saved$dupNote") }
                    loadCrm()
                }
                else -> handleMutationFailure(res)
            }
        }

    fun saveQuote(
        customerId: String?,
        newCustomerName: String?,
        stage: String,
        segment: String,
        pickup: String,
        delivery: String,
        truckClass: String,
        estimatedTotal: String?,
        notes: String
    ) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PlatformRepository.saveQuote(customerId, newCustomerName, stage, segment, pickup, delivery, truckClass, estimatedTotal, notes)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Quote saved") }
                loadCrm()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun advanceQuote(quoteId: String, nextStage: String) = accountScope.launch {
        when (val res = PlatformRepository.advanceQuote(quoteId, nextStage)) {
            is ApiResult.Success -> {
                _state.update { it.copy(feedbackMessage = "Quote moved to $nextStage") }
                loadCrm()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun createPortalLink(customerId: String, jobId: String? = null, quoteId: String? = null) = accountScope.launch {
        when (val res = PlatformRepository.createPortalLink(customerId, jobId, quoteId)) {
            is ApiResult.Success -> _state.update { it.copy(portalLinkPreview = res.data, feedbackMessage = "Customer portal link created") }
            else -> handleMutationFailure(res)
        }
    }

    fun reviewPortalSubmission(submissionId: String, decision: String) = accountScope.launch {
        when (val res = PlatformRepository.reviewPortalSubmission(submissionId, decision)) {
            is ApiResult.Success -> {
                _state.update { it.copy(feedbackMessage = "Portal update $decision") }
                loadCrm()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun renderTemplate(kind: String, customerName: String, reference: String?, amount: String?) = accountScope.launch {
        when (val res = PlatformRepository.renderTemplate(kind, customerName, reference, amount)) {
            is ApiResult.Success -> _state.update { it.copy(renderedTemplate = res.data) }
            else -> handleMutationFailure(res)
        }
    }

    fun clearPreviews() {
        _state.update { it.copy(portalLinkPreview = null, renderedTemplate = null, lastInviteOrResetToken = null) }
    }

    fun loadTimesheets() = accountScope.launch {
        val res = PlatformRepository.fetchTimesheets()
        if (res is ApiResult.Success) _state.update { it.copy(timesheets = res.data) }
    }

    fun submitOwnTimesheet(workDate: String, regularMinutes: Int, overtimeMinutes: Int, breakMinutes: Int, reimbursements: String, notes: String) =
        accountScope.launch {
            _state.update { it.copy(loadingModule = true) }
            when (val res = PlatformRepository.submitOwnTimesheet(workDate, regularMinutes, overtimeMinutes, breakMinutes, reimbursements, notes)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loadingModule = false, feedbackMessage = "Timesheet submitted") }
                    loadTimesheets()
                }
                else -> handleMutationFailure(res)
            }
        }

    fun approveTimesheet(timesheetId: String) = accountScope.launch {
        when (val res = PlatformRepository.approveTimesheet(timesheetId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(feedbackMessage = "Timesheet approved") }
                loadTimesheets()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun generatePaymentStatement(staffId: String, periodStart: String, periodEnd: String) = accountScope.launch {
        when (val res = PlatformRepository.generatePaymentStatement(staffId, periodStart, periodEnd)) {
            is ApiResult.Success -> {
                _state.update { it.copy(feedbackMessage = "Statement ${res.data} created") }
                loadTimesheets()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun loadAnalytics(from: String? = null, to: String? = null) = accountScope.launch {
        val res = PlatformRepository.fetchAnalytics(from, to)
        if (res is ApiResult.Success) _state.update { it.copy(analytics = res.data) }
    }

    fun loadNotifications() = accountScope.launch {
        val res = PlatformRepository.fetchNotifications()
        if (res is ApiResult.Success) _state.update { it.copy(notifications = res.data) }
    }

    fun refreshNotificationsOnServer() = accountScope.launch {
        PlatformRepository.generateNotifications()
        loadNotifications()
    }

    fun markNotificationRead(eventId: String) = accountScope.launch {
        if (PlatformRepository.markNotificationRead(eventId) is ApiResult.Success) {
            loadNotifications()
        }
    }

    fun flushOfflineQueue(onApplied: () -> Unit = {}) {
        accountScope.launch {
            _state.update { it.copy(syncBusy = true) }
            when (val res = syncQueue.flush()) {
                is ApiResult.Success -> {
                    val s = res.data
                    val msg = buildString {
                        append("Synced ${s.applied} update(s)")
                        if (s.deduped > 0) append(", ${s.deduped} already applied")
                        if (s.conflicts > 0) append(", ${s.conflicts} conflict(s) need review")
                        if (s.rejected > 0) append(", ${s.rejected} rejected")
                    }
                    _state.update { it.copy(syncBusy = false, feedbackMessage = msg) }
                    if (s.applied > 0) onApplied()
                }
                is ApiResult.NetworkError -> {
                    scheduleBackgroundSync()
                    _state.update { it.copy(syncBusy = false, feedbackMessage = "Still offline — queued for automatic retry") }
                }
                ApiResult.AuthRequired -> _state.update { it.copy(syncBusy = false, authRequired = true) }
                is ApiResult.Forbidden -> _state.update { it.copy(syncBusy = false, feedbackMessage = res.message) }
                is ApiResult.ServerError -> _state.update { it.copy(syncBusy = false, feedbackMessage = res.message) }
            }
        }
    }

    fun dismissQueuedOp(idempotencyKey: String) {
        syncQueue.dismiss(idempotencyKey)
    }

    fun consumeFeedback() {
        _state.update { it.copy(feedbackMessage = null) }
    }

    private fun handleMutationFailure(res: ApiResult<*>) {
        val msg = when (res) {
            ApiResult.AuthRequired -> "Sign in required."
            is ApiResult.Forbidden -> res.message
            is ApiResult.NetworkError -> res.message
            is ApiResult.ServerError -> res.message
            is ApiResult.Success -> ""
        }
        _state.update {
            it.copy(
                loadingModule = false,
                authRequired = res is ApiResult.AuthRequired || it.authRequired,
                feedbackMessage = msg
            )
        }
    }

    private fun extractAuthError(res: ApiResult<*>): String = when (res) {
        ApiResult.AuthRequired -> "Invalid credentials. Check and try again."
        is ApiResult.Forbidden -> res.message
        is ApiResult.NetworkError -> res.message
        is ApiResult.ServerError -> res.message
        is ApiResult.Success -> ""
    }
}
