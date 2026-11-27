package com.hfremovalsadelaide.invoices.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfremovalsadelaide.invoices.auth.AuthRepository
import com.hfremovalsadelaide.invoices.auth.SessionUser
import com.hfremovalsadelaide.invoices.data.AnalyticsSnapshot
import com.hfremovalsadelaide.invoices.data.CrmSnapshot
import com.hfremovalsadelaide.invoices.data.CustomerSummary
import com.hfremovalsadelaide.invoices.data.DuplicateCustomerMatch
import com.hfremovalsadelaide.invoices.data.FleetSnapshot
import com.hfremovalsadelaide.invoices.data.NotificationFeedItem
import com.hfremovalsadelaide.invoices.data.PlatformRepository
import com.hfremovalsadelaide.invoices.data.PricingPolicyRecord
import com.hfremovalsadelaide.invoices.data.PricingRepository
import com.hfremovalsadelaide.invoices.data.QuotationRepository
import com.hfremovalsadelaide.invoices.data.QuoteDetailPayload
import com.hfremovalsadelaide.invoices.data.QuoteDraftStore
import com.hfremovalsadelaide.invoices.data.QuotePriceResult
import com.hfremovalsadelaide.invoices.data.QuoteRecord
import com.hfremovalsadelaide.invoices.data.QuoteSelectedServiceDraft
import com.hfremovalsadelaide.invoices.data.QuoteServiceDayDraft
import com.hfremovalsadelaide.invoices.data.QuoteWizardDraft
import com.hfremovalsadelaide.invoices.data.QuoteWizardStop
import com.hfremovalsadelaide.invoices.data.RateCard
import com.hfremovalsadelaide.invoices.data.ServiceCatalogueItem
import com.hfremovalsadelaide.invoices.data.ServiceUnit
import com.hfremovalsadelaide.invoices.data.GstTreatment
import com.hfremovalsadelaide.invoices.data.StaffMember
import com.hfremovalsadelaide.invoices.data.SubcontractorProfileRecord
import com.hfremovalsadelaide.invoices.data.TermsVersionRecord
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Navigation sections accessible inside the "More" hub or via sub-navigation.
 */
enum class MoreSection {
    HUB,
    STAFF,
    FLEET,
    PRICING,
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
    val subcontractors: List<SubcontractorProfileRecord> = emptyList(),
    val staffLoaded: Boolean = false,
    val fleet: FleetSnapshot? = null,
    val rateCards: List<RateCard> = emptyList(),
    val pricingPolicies: List<PricingPolicyRecord> = emptyList(),
    val serviceCatalogue: List<ServiceCatalogueItem> = emptyList(),
    val termsVersions: List<TermsVersionRecord> = emptyList(),
    val pricingLoaded: Boolean = false,
    val crm: CrmSnapshot? = null,
    val showQuoteWizard: Boolean = false,
    val quoteWizardDraft: QuoteWizardDraft? = null,
    val quotePricePreview: QuotePriceResult? = null,
    val quotePricingBusy: Boolean = false,
    val quoteDuplicateMatches: List<DuplicateCustomerMatch> = emptyList(),
    val activeQuoteDetail: QuoteDetailPayload? = null,
    val quoteBookingConflictMessage: String? = null,
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
    private val scheduleBackgroundSync: () -> Unit = {},
    private val quoteDraftStore: QuoteDraftStore? = null
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
        if (user.can("settings.manage") && user.can("invoices.read")) loadPricingAdmin()
        else if (user.can("crm.read") && user.can("invoices.read")) {
            loadPricingAdmin()
        }
        if (user.can("reports.read")) loadAnalytics()
        if (user.can("crm.read")) {
            val savedDraft = quoteDraftStore?.loadDraft(user.id)
            if (savedDraft != null) _state.update { it.copy(quoteWizardDraft = savedDraft) }
            loadCrm()
        }
        loadTimesheets()
    }

    fun loadStaff() = accountScope.launch {
        val res = PlatformRepository.fetchStaff()
        val subRes = if (_state.value.user?.can("staff.manage") == true) PlatformRepository.fetchSubcontractors() else null
        if (res is ApiResult.Success) {
            _state.update {
                it.copy(
                    staff = res.data,
                    subcontractors = (subRes as? ApiResult.Success)?.data ?: it.subcontractors,
                    staffLoaded = true
                )
            }
        }
    }

    fun saveSubcontractorProfile(
        staffId: String,
        companyName: String,
        contactName: String,
        contactPhone: String,
        licenceNumber: String,
        insuranceProvider: String,
        insurancePolicy: String,
        insuranceExpiry: String?,
        paymentTermsDays: Int?,
        notes: String
    ) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PlatformRepository.saveSubcontractorProfile(
            staffId, companyName, contactName, contactPhone, licenceNumber, insuranceProvider, insurancePolicy, insuranceExpiry, paymentTermsDays, notes
        )) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Subcontractor profile saved") }
                loadStaff()
            }
            else -> handleMutationFailure(res)
        }
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

    fun loadPricingAdmin() = accountScope.launch {
        val pRes = PricingRepository.fetchPolicies()
        val sRes = PricingRepository.fetchServices()
        val rRes = PlatformRepository.fetchRateCards()
        val tRes = QuotationRepository.fetchTerms()
        _state.update { state ->
            state.copy(
                pricingPolicies = (pRes as? ApiResult.Success)?.data ?: state.pricingPolicies,
                serviceCatalogue = (sRes as? ApiResult.Success)?.data ?: state.serviceCatalogue,
                rateCards = (rRes as? ApiResult.Success)?.data ?: state.rateCards,
                termsVersions = (tRes as? ApiResult.Success)?.data ?: state.termsVersions,
                pricingLoaded = pRes is ApiResult.Success && sRes is ApiResult.Success
            )
        }
    }

    fun saveTermsDraft(segment: String?, title: String, body: String, effectiveFrom: String, supersedesId: String? = null) =
        accountScope.launch {
            _state.update { it.copy(loadingModule = true) }
            when (val res = QuotationRepository.saveTermsDraft(segment, title, body, effectiveFrom, supersedesId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loadingModule = false, feedbackMessage = "Terms draft saved") }
                    loadPricingAdmin()
                    loadCrm()
                }
                else -> handleMutationFailure(res)
            }
        }

    fun approveTermsVersion(termsId: String) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = QuotationRepository.approveTerms(termsId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Terms & conditions approved") }
                loadPricingAdmin()
                loadCrm()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun savePolicyDraft(segment: String, key: String, value: String, effectiveFrom: String, notes: String) =
        accountScope.launch {
            _state.update { it.copy(loadingModule = true) }
            when (val res = PricingRepository.savePolicyDraft(segment, key, value, effectiveFrom, notes)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(loadingModule = false, feedbackMessage = "Policy draft saved") }
                    loadPricingAdmin()
                }
                else -> handleMutationFailure(res)
            }
        }

    fun approvePolicy(policyId: String) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PricingRepository.approvePolicy(policyId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Policy approved and activated") }
                loadPricingAdmin()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun saveRateCard(
        id: String?,
        name: String,
        segment: String,
        truckClass: String,
        movers: Int,
        hourlyRate: String,
        callOutHours: String,
        callOutFee: String,
        overtimeRate: String?,
        effectiveFrom: String,
        active: Boolean,
        notes: String
    ) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PricingRepository.saveRateCard(
            id, name, segment, truckClass, movers, hourlyRate,
            callOutHours, callOutFee, overtimeRate, effectiveFrom, active, notes
        )) {
            is ApiResult.Success -> {
                val msg = if (res.data.second) "New rate card version created" else "Rate card saved"
                _state.update { it.copy(loadingModule = false, feedbackMessage = msg) }
                loadPricingAdmin()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun saveServiceItem(
        id: String?,
        name: String,
        description: String,
        segment: String?,
        unit: ServiceUnit,
        rate: String?,
        gstTreatment: GstTreatment?,
        effectiveFrom: String,
        active: Boolean
    ) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = PricingRepository.saveService(
            id, name, description, segment, unit, rate, gstTreatment, effectiveFrom, active
        )) {
            is ApiResult.Success -> {
                val msg = if (res.data.second) "New service version created" else "Service saved"
                _state.update { it.copy(loadingModule = false, feedbackMessage = msg) }
                loadPricingAdmin()
            }
            else -> handleMutationFailure(res)
        }
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

    fun openQuoteWizard(prefillCustomer: CustomerSummary? = null, startFresh: Boolean = false) {
        val userId = _state.value.user?.id.orEmpty()
        val today = LocalDate.now().toString()
        val existing = if (startFresh) null else (_state.value.quoteWizardDraft ?: quoteDraftStore?.loadDraft(userId))
        val initial = when {
            prefillCustomer != null -> QuoteWizardDraft(
                customerId = prefillCustomer.id,
                customerKind = prefillCustomer.kind,
                customerName = prefillCustomer.name,
                customerEmail = prefillCustomer.email,
                customerPhone = prefillCustomer.phone,
                customerAddress = prefillCustomer.address,
                preferredDate = today,
                serviceDays = listOf(QuoteServiceDayDraft(date = today))
            )
            existing != null -> existing
            else -> QuoteWizardDraft(
                preferredDate = today,
                serviceDays = listOf(QuoteServiceDayDraft(date = today))
            )
        }
        if (userId.isNotBlank()) quoteDraftStore?.saveDraft(userId, initial)
        _state.update {
            it.copy(
                quoteWizardDraft = initial,
                showQuoteWizard = true,
                quotePricePreview = null,
                quoteDuplicateMatches = emptyList()
            )
        }
        if (!_state.value.pricingLoaded) loadPricingAdmin()
        refreshQuoteServerPrice(initial)
    }

    fun updateQuoteWizardDraft(transform: (QuoteWizardDraft) -> QuoteWizardDraft) {
        val cur = _state.value.quoteWizardDraft ?: return
        val next = transform(cur)
        _state.value.user?.id?.let { uid -> quoteDraftStore?.saveDraft(uid, next) }
        _state.update { it.copy(quoteWizardDraft = next) }
    }

    fun closeQuoteWizard(clearSavedDraft: Boolean = false) {
        if (clearSavedDraft) {
            _state.value.user?.id?.let { uid -> quoteDraftStore?.clearDraft(uid) }
        }
        _state.update {
            it.copy(
                showQuoteWizard = false,
                quoteWizardDraft = if (clearSavedDraft) null else it.quoteWizardDraft,
                quotePricePreview = if (clearSavedDraft) null else it.quotePricePreview,
                quoteDuplicateMatches = emptyList()
            )
        }
    }

    fun checkDuplicateCustomers(name: String, email: String, phone: String) = accountScope.launch {
        if (name.trim().length < 2 && email.trim().isEmpty() && phone.trim().length < 6) {
            _state.update { it.copy(quoteDuplicateMatches = emptyList()) }
            return@launch
        }
        val res = QuotationRepository.checkDuplicateCustomers(name, email, phone)
        if (res is ApiResult.Success) _state.update { it.copy(quoteDuplicateMatches = res.data) }
    }

    fun refreshQuoteServerPrice(draft: QuoteWizardDraft? = _state.value.quoteWizardDraft) = accountScope.launch {
        val target = draft ?: return@launch
        _state.update { it.copy(quotePricingBusy = true) }
        val res = QuotationRepository.priceQuote(target, LocalDate.now().toString())
        when (res) {
            is ApiResult.Success -> _state.update { it.copy(quotePricingBusy = false, quotePricePreview = res.data) }
            else -> _state.update { it.copy(quotePricingBusy = false) }
        }
    }

    fun saveQuoteWizardDraftToServer(draft: QuoteWizardDraft, onSaved: (String) -> Unit = {}) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        val today = LocalDate.now().toString()
        when (val res = QuotationRepository.saveWizardQuote(draft, today)) {
            is ApiResult.Success -> {
                val updated = draft.copy(editingQuoteId = res.data)
                _state.value.user?.id?.let { uid -> quoteDraftStore?.saveDraft(uid, updated) }
                _state.update {
                    it.copy(
                        loadingModule = false,
                        quoteWizardDraft = updated,
                        feedbackMessage = "Quotation draft saved"
                    )
                }
                loadCrm()
                refreshQuoteServerPrice(updated)
                onSaved(res.data)
            }
            else -> handleMutationFailure(res)
        }
    }

    fun issueQuoteFromWizard(draft: QuoteWizardDraft, reason: String, onIssued: (String) -> Unit = {}) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        val today = LocalDate.now().toString()
        when (val saved = QuotationRepository.saveWizardQuote(draft, today)) {
            is ApiResult.Success -> {
                val quoteId = saved.data
                when (val issued = QuotationRepository.issueQuoteRevision(quoteId, reason, draft.termsVersionId)) {
                    is ApiResult.Success -> {
                        _state.value.user?.id?.let { uid -> quoteDraftStore?.clearDraft(uid) }
                        _state.update {
                            it.copy(
                                loadingModule = false,
                                showQuoteWizard = false,
                                quoteWizardDraft = null,
                                quotePricePreview = null,
                                feedbackMessage = "Quotation issued"
                            )
                        }
                        loadCrm()
                        loadQuoteDetail(quoteId)
                        onIssued(quoteId)
                    }
                    else -> {
                        val updated = draft.copy(editingQuoteId = quoteId)
                        _state.update { it.copy(quoteWizardDraft = updated) }
                        handleMutationFailure(issued)
                    }
                }
            }
            else -> handleMutationFailure(saved)
        }
    }

    fun loadQuoteDetail(quoteId: String) = accountScope.launch {
        when (val res = QuotationRepository.fetchQuoteDetail(quoteId)) {
            is ApiResult.Success -> _state.update { it.copy(activeQuoteDetail = res.data) }
            else -> handleMutationFailure(res)
        }
    }

    fun openRevisionDraftInWizard(quote: QuoteRecord, reason: String) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val opened = QuotationRepository.createRevisionDraft(quote.id, reason)) {
            is ApiResult.Success -> {
                val detailRes = QuotationRepository.fetchQuoteDetail(quote.id)
                val detail = (detailRes as? ApiResult.Success)?.data
                val hydrated = hydrateWizardFromQuote(quote, detail, reason)
                _state.value.user?.id?.let { uid -> quoteDraftStore?.saveDraft(uid, hydrated) }
                _state.update {
                    it.copy(
                        loadingModule = false,
                        quoteWizardDraft = hydrated,
                        showQuoteWizard = true,
                        activeQuoteDetail = detail
                    )
                }
                loadCrm()
                refreshQuoteServerPrice(hydrated)
            }
            else -> handleMutationFailure(opened)
        }
    }

    fun cloneQuoteVariation(quoteId: String, reason: String) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = QuotationRepository.cloneVariation(quoteId, reason)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Variation quote created cleanly") }
                loadCrm()
            }
            else -> handleMutationFailure(res)
        }
    }

    fun acceptQuoteRevision(quoteId: String, revisionId: String, method: String, notes: String) = accountScope.launch {
        _state.update { it.copy(loadingModule = true) }
        when (val res = QuotationRepository.acceptQuoteRevision(quoteId, revisionId, method, notes)) {
            is ApiResult.Success -> {
                _state.update { it.copy(loadingModule = false, feedbackMessage = "Customer acceptance recorded") }
                loadCrm()
                loadQuoteDetail(quoteId)
            }
            else -> handleMutationFailure(res)
        }
    }

    fun convertQuoteToBooking(quoteId: String, overrideReason: String? = null, onConverted: (String) -> Unit = {}) = accountScope.launch {
        _state.update { it.copy(loadingModule = true, quoteBookingConflictMessage = null) }
        when (val res = QuotationRepository.convertQuoteToBooking(quoteId, overrideReason)) {
            is ApiResult.Success -> {
                val msg = if (res.data.second) "Already linked to booking" else "Confirmed booking created"
                _state.update { it.copy(loadingModule = false, feedbackMessage = msg) }
                loadCrm()
                loadQuoteDetail(quoteId)
                onConverted(res.data.first)
            }
            is ApiResult.ServerError -> {
                if (res.code == 409) {
                    _state.update { it.copy(loadingModule = false, quoteBookingConflictMessage = res.message) }
                } else {
                    handleMutationFailure(res)
                }
            }
            else -> handleMutationFailure(res)
        }
    }

    fun dismissQuoteBookingConflict() {
        _state.update { it.copy(quoteBookingConflictMessage = null) }
    }

    fun renderTemplate(
        kind: String,
        customerName: String,
        reference: String?,
        amount: String?,
        date: String? = null,
        pickup: String? = null,
        delivery: String? = null,
        revisionNumber: Int? = null,
        validUntil: String? = null,
        depositAmount: String? = null,
        balance: String? = null,
        truck: String? = null,
        movers: Int? = null
    ) = accountScope.launch {
        when (val res = PlatformRepository.renderTemplate(
            kind, customerName, reference, amount, date, pickup, delivery, revisionNumber, validUntil, depositAmount, balance, truck, movers
        )) {
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

    private fun hydrateWizardFromQuote(quote: QuoteRecord, detail: QuoteDetailPayload?, reason: String): QuoteWizardDraft {
        val cust = _state.value.crm?.customers.orEmpty().firstOrNull { it.id == quote.customerId }
        val d = detail?.draftDetailsJson
        val req = detail?.pricingRequestJson
        val datesArr = d?.optJSONArray("preferredDates")
        val extraDates = if (datesArr != null && datesArr.length() > 1) {
            List(datesArr.length() - 1) { i -> datesArr.optString(i + 1) }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        val stopsArr = d?.optJSONArray("stops")
        val stops = if (stopsArr != null) {
            List(stopsArr.length()) { i ->
                val s = stopsArr.getJSONObject(i)
                QuoteWizardStop(
                    kind = s.optString("kind", "stop"),
                    address = s.optString("address", ""),
                    notes = s.optString("notes", "")
                )
            }
        } else {
            emptyList()
        }
        val reqDaysArr = req?.optJSONArray("serviceDays")
        val days = if (reqDaysArr != null && reqDaysArr.length() > 0) {
            List(reqDaysArr.length()) { i ->
                val dayObj = reqDaysArr.getJSONObject(i)
                QuoteServiceDayDraft(
                    date = dayObj.optString("date", quote.preferredDate ?: LocalDate.now().toString()),
                    rateCardId = if (dayObj.isNull("rateCardId")) null else dayObj.optString("rateCardId").takeIf { it.isNotBlank() },
                    rateUnit = dayObj.optString("rateUnit", "half_hour"),
                    truckClass = dayObj.optString("truckClass", quote.truckClass),
                    manualRate = if (dayObj.isNull("manualRate")) "" else dayObj.optString("manualRate", ""),
                    manualRateReason = if (dayObj.isNull("manualRateReason")) "" else dayObj.optString("manualRateReason", ""),
                    movers = dayObj.optInt("movers", quote.movers),
                    estimatedHours = if (dayObj.isNull("estimatedHours")) "3" else dayObj.optString("estimatedHours", "3"),
                    publicHoliday = dayObj.optBoolean("publicHoliday", false),
                    callOut = dayObj.optBoolean("callOut", false),
                    travel = dayObj.optBoolean("travel", false),
                    expanded = i == 0
                )
            }
        } else {
            listOf(
                QuoteServiceDayDraft(
                    date = quote.preferredDate ?: LocalDate.now().toString(),
                    truckClass = quote.truckClass,
                    movers = quote.movers,
                    estimatedHours = quote.estimatedHours ?: "3"
                )
            )
        }
        val reqSvcArr = req?.optJSONArray("services")
        val catalogById = _state.value.serviceCatalogue.associateBy { it.id }
        val selectedSvcs = if (reqSvcArr != null) {
            List(reqSvcArr.length()) { i ->
                val so = reqSvcArr.getJSONObject(i)
                val catId = so.optString("catalogueId")
                val cat = catalogById[catId]
                QuoteSelectedServiceDraft(
                    catalogueId = catId,
                    name = cat?.name ?: "Service",
                    unit = cat?.unit ?: ServiceUnit.PER_ITEM,
                    quantity = so.optString("quantity", "1")
                )
            }
        } else {
            emptyList()
        }
        val disc = req?.optJSONObject("discount")
        return QuoteWizardDraft(
            stepIndex = 0,
            editingQuoteId = quote.id,
            revisionReason = reason,
            customerId = quote.customerId,
            customerKind = cust?.kind ?: "Individual",
            customerName = cust?.name ?: quote.customerName,
            customerEmail = cust?.email.orEmpty(),
            customerPhone = cust?.phone.orEmpty(),
            customerAddress = cust?.address.orEmpty(),
            segment = quote.segment,
            preferredDate = quote.preferredDate ?: datesArr?.optString(0).orEmpty().ifBlank { LocalDate.now().toString() },
            extraDates = extraDates,
            pickup = quote.pickup,
            delivery = quote.delivery,
            stops = stops,
            propertyType = d?.optString("propertyType", "").orEmpty(),
            bedrooms = d?.optString("bedrooms", "").orEmpty(),
            stairsAndLifts = d?.optString("stairsAndLifts", "").orEmpty(),
            parkingAndAccess = d?.optString("parkingAndAccess", "").orEmpty(),
            inventorySummary = d?.optString("inventorySummary", "").orEmpty(),
            heavyItemsSummary = d?.optString("heavyItemsSummary", "").orEmpty(),
            specialInstructions = d?.optString("specialInstructions", "").orEmpty(),
            pricingMode = req?.optString("pricingMode", "hourly_estimate") ?: "hourly_estimate",
            fixedPrice = if (req != null && !req.isNull("fixedPrice")) req.optString("fixedPrice", "") else "",
            serviceDays = days,
            selectedServices = selectedSvcs,
            packingQuantity = if (req != null && !req.isNull("packingQuantity")) req.optString("packingQuantity", "") else "",
            heavyItemCount = if (req != null && !req.isNull("heavyItemCount")) req.optInt("heavyItemCount", 0).toString() else "",
            materialsNotes = d?.optString("materialsNotes", "").orEmpty(),
            discountEnabled = disc != null,
            discountType = disc?.optString("type", "percent") ?: "percent",
            discountValue = disc?.optString("value", "") ?: "",
            discountReason = disc?.optString("reason", "") ?: "",
            depositReceived = if (req != null && !req.isNull("depositReceived")) req.optString("depositReceived", "") else "",
            termsVersionId = quote.termsVersionId,
            notes = quote.notes,
            followUpDate = quote.followUpDate.orEmpty()
        )
    }
}
