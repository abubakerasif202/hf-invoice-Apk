package com.hfremovalsadelaide.invoices.data

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

enum class InvoiceStatus(val serverLabel: String) {
    DRAFT("Draft"),
    ISSUED("Issued"),
    PARTIALLY_PAID("Partially Paid"),
    PAID("Paid"),
    OVERDUE("Overdue"),
    VOID("Void");

    companion object {
        fun fromServerValue(raw: String?): InvoiceStatus {
            return entries.firstOrNull {
                it.serverLabel.equals(raw?.trim(), ignoreCase = true)
            } ?: DRAFT
        }
    }
}

data class InvoicePaymentRecord(
    val amount: String,
    val date: String,
    val reference: String,
    val notes: String
)

data class InvoiceSummary(
    val id: String,
    val number: String,
    val customerName: String,
    val customerAddress: String,
    val issuedDate: String,
    val dueDate: String,
    val status: InvoiceStatus,
    val servicesDescription: String,
    val totalAmount: BigDecimal,
    val balanceAmount: BigDecimal,
    val gstAmount: BigDecimal,
    val paidAmount: BigDecimal,
    val subtotalAmount: BigDecimal = BigDecimal.ZERO,
    val payments: List<InvoicePaymentRecord> = emptyList(),
    val pickup: String = "",
    val delivery: String = ""
) {
    val formattedTotal: String
        get() = formatAud(totalAmount)

    val formattedBalance: String
        get() = formatAud(balanceAmount)

    val formattedPaid: String
        get() = formatAud(paidAmount)

    val formattedGst: String
        get() = formatAud(gstAmount)

    companion object {
        private val audLocale = Locale.forLanguageTag("en-AU")

        fun formatAud(value: BigDecimal): String {
            val formatter = NumberFormat.getCurrencyInstance(audLocale)
            return formatter.format(value)
        }
    }
}

data class CustomerSummary(
    val id: String,
    val name: String,
    val address: String,
    val email: String,
    val phone: String,
    val kind: String = "Individual",
    val notes: String = ""
)

data class WorkspaceCompanyConfig(
    val terms: String = "",
    val defaultTax: String = "none",
    val gstStatus: String = "unconfirmed"
)

data class InvoicesSnapshot(
    val invoices: List<InvoiceSummary>,
    val customers: List<CustomerSummary>,
    val config: WorkspaceCompanyConfig
)

data class CreateInvoiceLineItem(
    val description: String,
    val quantity: String = "1",
    val unit: String = "each",
    val rate: String,
    val discount: Boolean = false
)

data class CreateInvoiceRequest(
    val customerId: String? = null,
    val customerName: String,
    val customerAddress: String,
    val servicesPerformed: String,
    val totalAmount: BigDecimal,
    val issuedDate: String,
    val dueDate: String,
    val issueImmediately: Boolean,
    val segment: String = "residential",
    val rateCardId: String? = null,
    val pickup: String = "",
    val delivery: String = "",
    val extraItems: List<CreateInvoiceLineItem> = emptyList()
)

sealed interface InvoiceRepositoryResult<out T> {
    data class Success<T>(val data: T) : InvoiceRepositoryResult<T>
    data object AuthRequired : InvoiceRepositoryResult<Nothing>
    data object NetworkError : InvoiceRepositoryResult<Nothing>
    data class ServerError(val message: String?) : InvoiceRepositoryResult<Nothing>
}
