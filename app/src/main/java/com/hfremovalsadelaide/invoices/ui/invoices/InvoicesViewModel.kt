package com.hfremovalsadelaide.invoices.ui.invoices

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.data.CreateInvoiceLineItem
import com.hfremovalsadelaide.invoices.data.CreateInvoiceRequest
import com.hfremovalsadelaide.invoices.data.CustomerSummary
import com.hfremovalsadelaide.invoices.data.InvoiceApiRepository
import com.hfremovalsadelaide.invoices.data.InvoiceRepositoryResult
import com.hfremovalsadelaide.invoices.data.InvoiceStatus
import com.hfremovalsadelaide.invoices.data.InvoiceSummary
import com.hfremovalsadelaide.invoices.data.PlatformRepository
import com.hfremovalsadelaide.invoices.data.RateCard
import com.hfremovalsadelaide.invoices.data.WorkspaceCompanyConfig
import com.hfremovalsadelaide.invoices.network.ApiResult
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed interface InvoicesLoadState {
    data object Loading : InvoicesLoadState
    data object AuthRequired : InvoicesLoadState
    data class Loaded(
        val invoices: List<InvoiceSummary>,
        val customers: List<CustomerSummary>,
        val config: WorkspaceCompanyConfig
    ) : InvoicesLoadState

    data class Error(
        @StringRes val messageResId: Int,
        val detail: String? = null
    ) : InvoicesLoadState
}

data class CreateInvoiceFormState(
    val selectedCustomerId: String? = null,
    val customerName: String = "",
    val customerAddress: String = "",
    val pickup: String = "",
    val delivery: String = "",
    val servicesPerformed: String = "",
    val totalAmount: String = "",
    val issuedDate: String = defaultDateString(offsetDays = 0),
    val dueDate: String = defaultDateString(offsetDays = 7),
    val issueImmediately: Boolean = false,
    val segment: String = "residential",
    val rateCardId: String? = null,
    val extraItems: List<CreateInvoiceLineItem> = emptyList(),
    @StringRes val customerNameError: Int? = null,
    @StringRes val customerAddressError: Int? = null,
    @StringRes val servicesPerformedError: Int? = null,
    @StringRes val totalAmountError: Int? = null,
    val isSubmitting: Boolean = false,
    val submissionError: String? = null,
    @StringRes val submissionErrorResId: Int? = null
) {
    val parsedAmount: BigDecimal?
        get() = runCatching {
            val cleaned = totalAmount.replace(",", "").replace("$", "").trim()
            if (cleaned.isBlank()) null else BigDecimal(cleaned)
        }.getOrNull()

    companion object {
        fun defaultDateString(offsetDays: Int): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, offsetDays)
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        }
    }
}

data class InvoicesUiState(
    val loadState: InvoicesLoadState = InvoicesLoadState.Loading,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val selectedStatusFilter: InvoiceStatus? = null,
    val form: CreateInvoiceFormState = CreateInvoiceFormState(),
    val rateCards: List<RateCard> = emptyList(),
    val selectedInvoice: InvoiceSummary? = null,
    val actionBusy: Boolean = false,
    val actionMessage: String? = null
) {
    val allInvoices: List<InvoiceSummary>
        get() = (loadState as? InvoicesLoadState.Loaded)?.invoices.orEmpty()

    val availableCustomers: List<CustomerSummary>
        get() = (loadState as? InvoicesLoadState.Loaded)?.customers.orEmpty()

    val companyConfig: WorkspaceCompanyConfig
        get() = (loadState as? InvoicesLoadState.Loaded)?.config ?: WorkspaceCompanyConfig()

    val filteredInvoices: List<InvoiceSummary>
        get() {
            val query = searchQuery.trim().lowercase()
            return allInvoices.filter { invoice ->
                val matchesStatus = selectedStatusFilter == null || invoice.status == selectedStatusFilter
                val matchesQuery = query.isBlank() ||
                    invoice.number.lowercase().contains(query) ||
                    invoice.customerName.lowercase().contains(query) ||
                    invoice.servicesDescription.lowercase().contains(query) ||
                    invoice.issuedDate.lowercase().contains(query) ||
                    invoice.status.serverLabel.lowercase().contains(query)
                matchesStatus && matchesQuery
            }
        }

    val paidCount: Int
        get() = allInvoices.count { it.status == InvoiceStatus.PAID }

    val outstandingCount: Int
        get() = allInvoices.count {
            it.status == InvoiceStatus.ISSUED ||
                it.status == InvoiceStatus.PARTIALLY_PAID ||
                it.status == InvoiceStatus.OVERDUE
        }
}

sealed interface InvoicesEvent {
    data class InvoiceSaved(
        val invoiceId: String,
        val wasIssued: Boolean
    ) : InvoicesEvent

    data object RequireSignIn : InvoicesEvent
}

class InvoicesViewModel(
    private val repository: InvoiceApiRepository = InvoiceApiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(InvoicesUiState())
    val uiState: StateFlow<InvoicesUiState> = _uiState.asStateFlow()

    fun resetAccountState() {
        viewModelScope.coroutineContext.cancelChildren()
        _uiState.value = InvoicesUiState()
    }

    private val _events = MutableSharedFlow<InvoicesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<InvoicesEvent> = _events.asSharedFlow()

    fun loadInvoices(isPullRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { state ->
                if (isPullRefresh && state.loadState is InvoicesLoadState.Loaded) {
                    state.copy(isRefreshing = true)
                } else {
                    state.copy(
                        loadState = InvoicesLoadState.Loading,
                        isRefreshing = false
                    )
                }
            }

            when (val result = repository.fetchInvoices()) {
                is InvoiceRepositoryResult.Success -> {
                    _uiState.update { state ->
                        val updatedSelected = state.selectedInvoice?.let { sel ->
                            result.data.invoices.firstOrNull { it.id == sel.id }
                        }
                        state.copy(
                            loadState = InvoicesLoadState.Loaded(
                                invoices = result.data.invoices,
                                customers = result.data.customers,
                                config = result.data.config
                            ),
                            selectedInvoice = updatedSelected,
                            isRefreshing = false
                        )
                    }
                    loadRateCards()
                }

                InvoiceRepositoryResult.AuthRequired -> {
                    _uiState.update {
                        it.copy(
                            loadState = InvoicesLoadState.AuthRequired,
                            isRefreshing = false
                        )
                    }
                }

                InvoiceRepositoryResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            loadState = InvoicesLoadState.Error(R.string.invoices_error_network),
                            isRefreshing = false
                        )
                    }
                }

                is InvoiceRepositoryResult.ServerError -> {
                    _uiState.update {
                        it.copy(
                            loadState = InvoicesLoadState.Error(
                                messageResId = R.string.invoices_error_server,
                                detail = result.message
                            ),
                            isRefreshing = false
                        )
                    }
                }
            }
        }
    }

    fun loadRateCards() {
        viewModelScope.launch {
            val res = PlatformRepository.fetchRateCards()
            if (res is ApiResult.Success) {
                _uiState.update { it.copy(rateCards = res.data.filter { rc -> rc.active }) }
            }
        }
    }

    fun selectInvoice(invoice: InvoiceSummary?) {
        _uiState.update { it.copy(selectedInvoice = invoice) }
    }

    fun consumeActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun recordPayment(invoiceId: String, amount: String, date: String, reference: String, notes: String) {
        if (amount.isBlank()) {
            _uiState.update { it.copy(actionMessage = "Enter a valid payment amount.") }
            return
        }
        runInvoiceAction("Payment recorded") {
            repository.recordPayment(invoiceId, amount.trim(), date.trim(), reference.trim(), notes.trim())
        }
    }

    fun markPaid(invoiceId: String, date: String, reference: String) {
        runInvoiceAction("Invoice marked Paid") {
            repository.markPaid(invoiceId, date.trim(), reference.trim())
        }
    }

    fun voidInvoice(invoiceId: String) {
        runInvoiceAction("Invoice voided") {
            repository.voidInvoice(invoiceId)
        }
    }

    fun duplicateInvoice(invoiceId: String) {
        runInvoiceAction("Invoice duplicated as Draft") {
            repository.duplicateInvoice(invoiceId)
        }
    }

    private fun runInvoiceAction(okMsg: String, call: suspend () -> InvoiceRepositoryResult<String>) {
        if (_uiState.value.actionBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionBusy = true) }
            when (val res = call()) {
                is InvoiceRepositoryResult.Success -> {
                    _uiState.update { it.copy(actionBusy = false, actionMessage = okMsg, selectedInvoice = null) }
                    loadInvoices(isPullRefresh = true)
                }
                InvoiceRepositoryResult.AuthRequired -> {
                    _uiState.update { it.copy(actionBusy = false) }
                    _events.tryEmit(InvoicesEvent.RequireSignIn)
                }
                InvoiceRepositoryResult.NetworkError -> {
                    _uiState.update { it.copy(actionBusy = false, actionMessage = "No connection. Financial actions require live server verification.") }
                }
                is InvoiceRepositoryResult.ServerError -> {
                    _uiState.update { it.copy(actionBusy = false, actionMessage = res.message ?: "Action failed.") }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onStatusFilterSelected(status: InvoiceStatus?) {
        _uiState.update { it.copy(selectedStatusFilter = status) }
    }

    fun onSelectExistingCustomer(customer: CustomerSummary?) {
        _uiState.update { state ->
            if (customer == null) {
                state.copy(
                    form = state.form.copy(
                        selectedCustomerId = null,
                        customerName = "",
                        customerAddress = "",
                        customerNameError = null,
                        customerAddressError = null,
                        submissionError = null,
                        submissionErrorResId = null
                    )
                )
            } else {
                state.copy(
                    form = state.form.copy(
                        selectedCustomerId = customer.id,
                        customerName = customer.name,
                        customerAddress = customer.address,
                        segment = if (customer.kind.equals("Business", ignoreCase = true)) "commercial" else state.form.segment,
                        customerNameError = null,
                        customerAddressError = null,
                        submissionError = null,
                        submissionErrorResId = null
                    )
                )
            }
        }
    }

    fun onSegmentChanged(segment: String) {
        _uiState.update { state ->
            state.copy(form = state.form.copy(segment = segment, rateCardId = null))
        }
    }

    fun onRateCardSelected(rateCard: RateCard?) {
        _uiState.update { state ->
            if (rateCard == null) {
                state.copy(form = state.form.copy(rateCardId = null))
            } else {
                val nextServices = state.form.servicesPerformed.ifBlank {
                    "${rateCard.name} (${rateCard.truckClass}, ${rateCard.movers} movers @ $${rateCard.hourlyRate}/hr)"
                }
                state.copy(
                    form = state.form.copy(
                        rateCardId = rateCard.id,
                        segment = rateCard.segment,
                        servicesPerformed = nextServices,
                        servicesPerformedError = null
                    )
                )
            }
        }
    }

    fun onPickupChanged(value: String) {
        _uiState.update { state -> state.copy(form = state.form.copy(pickup = value)) }
    }

    fun onDeliveryChanged(value: String) {
        _uiState.update { state -> state.copy(form = state.form.copy(delivery = value)) }
    }

    fun addExtraItem(item: CreateInvoiceLineItem) {
        if (item.description.isBlank()) return
        _uiState.update { state ->
            state.copy(form = state.form.copy(extraItems = state.form.extraItems + item))
        }
    }

    fun removeExtraItem(index: Int) {
        _uiState.update { state ->
            state.copy(
                form = state.form.copy(
                    extraItems = state.form.extraItems.filterIndexed { i, _ -> i != index }
                )
            )
        }
    }

    fun onCustomerNameChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                form = state.form.copy(
                    selectedCustomerId = null,
                    customerName = value,
                    customerNameError = null,
                    submissionError = null,
                    submissionErrorResId = null
                )
            )
        }
    }

    fun onCustomerAddressChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                form = state.form.copy(
                    customerAddress = value,
                    customerAddressError = null,
                    submissionError = null,
                    submissionErrorResId = null
                )
            )
        }
    }

    fun onServicesPerformedChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                form = state.form.copy(
                    servicesPerformed = value,
                    servicesPerformedError = null,
                    submissionError = null,
                    submissionErrorResId = null
                )
            )
        }
    }

    fun onTotalAmountChanged(value: String) {
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val dotCount = sanitized.count { it == '.' }
        val normalized = if (dotCount <= 1) {
            val parts = sanitized.split('.')
            if (parts.size == 2 && parts[1].length > 2) {
                "${parts[0]}.${parts[1].take(2)}"
            } else {
                sanitized
            }
        } else {
            _uiState.value.form.totalAmount
        }

        _uiState.update { state ->
            state.copy(
                form = state.form.copy(
                    totalAmount = normalized,
                    totalAmountError = null,
                    submissionError = null,
                    submissionErrorResId = null
                )
            )
        }
    }

    fun onIssueDateChanged(isoDate: String) {
        _uiState.update { state ->
            state.copy(form = state.form.copy(issuedDate = isoDate))
        }
    }

    fun onDueDateChanged(isoDate: String) {
        _uiState.update { state ->
            state.copy(form = state.form.copy(dueDate = isoDate))
        }
    }

    fun onIssueImmediatelyChanged(issueImmediately: Boolean) {
        _uiState.update { state ->
            state.copy(form = state.form.copy(issueImmediately = issueImmediately))
        }
    }

    fun resetForm() {
        _uiState.update { it.copy(form = CreateInvoiceFormState()) }
    }

    fun submitNewInvoice() {
        val currentState = _uiState.value
        val form = currentState.form
        if (form.isSubmitting) return

        val nameError = if (form.customerName.trim().isEmpty()) {
            R.string.validation_customer_name_required
        } else null

        val addressError = if (form.customerAddress.trim().isEmpty()) {
            R.string.validation_customer_address_required
        } else null

        val servicesError = if (form.servicesPerformed.trim().isEmpty()) {
            R.string.validation_services_required
        } else null

        val parsedAmount = form.parsedAmount
        val amountError = if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO) {
            R.string.validation_amount_invalid
        } else null

        if (nameError != null || addressError != null || servicesError != null || amountError != null) {
            _uiState.update { state ->
                state.copy(
                    form = state.form.copy(
                        customerNameError = nameError,
                        customerAddressError = addressError,
                        servicesPerformedError = servicesError,
                        totalAmountError = amountError
                    )
                )
            }
            return
        }

        val amount = parsedAmount ?: return
        val request = CreateInvoiceRequest(
            customerId = form.selectedCustomerId,
            customerName = form.customerName.trim(),
            customerAddress = form.customerAddress.trim(),
            servicesPerformed = form.servicesPerformed.trim(),
            totalAmount = amount,
            issuedDate = form.issuedDate,
            dueDate = form.dueDate,
            issueImmediately = form.issueImmediately,
            segment = form.segment,
            rateCardId = form.rateCardId,
            pickup = form.pickup.trim(),
            delivery = form.delivery.trim(),
            extraItems = form.extraItems
        )

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    form = state.form.copy(
                        isSubmitting = true,
                        submissionError = null,
                        submissionErrorResId = null
                    )
                )
            }

            when (val result = repository.createInvoice(request, currentState.companyConfig)) {
                is InvoiceRepositoryResult.Success -> {
                    _uiState.update { state ->
                        state.copy(form = CreateInvoiceFormState())
                    }
                    loadInvoices(isPullRefresh = false)
                    _events.tryEmit(
                        InvoicesEvent.InvoiceSaved(
                            invoiceId = result.data,
                            wasIssued = request.issueImmediately
                        )
                    )
                }

                InvoiceRepositoryResult.AuthRequired -> {
                    _uiState.update { state ->
                        state.copy(
                            form = state.form.copy(
                                isSubmitting = false,
                                submissionErrorResId = R.string.create_invoice_auth_needed
                            )
                        )
                    }
                    _events.tryEmit(InvoicesEvent.RequireSignIn)
                }

                InvoiceRepositoryResult.NetworkError -> {
                    _uiState.update { state ->
                        state.copy(
                            form = state.form.copy(
                                isSubmitting = false,
                                submissionErrorResId = R.string.invoices_error_network
                            )
                        )
                    }
                }

                is InvoiceRepositoryResult.ServerError -> {
                    _uiState.update { state ->
                        state.copy(
                            form = state.form.copy(
                                isSubmitting = false,
                                submissionError = result.message,
                                submissionErrorResId = if (result.message.isNullOrBlank()) {
                                    R.string.invoices_error_server
                                } else null
                            )
                        )
                    }
                }
            }
        }
    }
}
