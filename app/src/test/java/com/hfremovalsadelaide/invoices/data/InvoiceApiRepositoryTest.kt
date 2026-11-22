package com.hfremovalsadelaide.invoices.data

import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.ui.invoices.InvoicesViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InvoiceApiRepositoryTest {

    @Test
    fun parsesInvoicesSnapshotSortedNewestFirst() {
        val sampleJson = """
            {
              "config": {
                "terms": "7 days",
                "defaultTax": "exclusive",
                "gst": "registered"
              },
              "customers": [
                {
                  "id": "cust-1",
                  "data": {
                    "name": "Adelaide Logistics Pty Ltd",
                    "address": "42 King William St, Adelaide SA 5000",
                    "email": "accounts@example.com",
                    "phone": "0400123456"
                  }
                }
              ],
              "records": [
                {
                  "id": "inv-older",
                  "number": "INV-2026-0001",
                  "status": "Paid",
                  "customer": { "name": "Jane Smith", "address": "10 Jetty Rd, Glenelg SA" },
                  "data": {
                    "issued": "2026-04-10",
                    "due": "2026-04-17",
                    "description": "2-bedroom apartment relocation"
                  },
                  "totals": {
                    "total": "640.00",
                    "balance": "0.00",
                    "gst": "58.18",
                    "paid": "640.00"
                  }
                },
                {
                  "id": "inv-newer",
                  "number": "INV-2026-0002",
                  "status": "Overdue",
                  "buyer": { "name": "Adelaide Logistics Pty Ltd", "address": "42 King William St" },
                  "data": {
                    "issued": "2026-05-01",
                    "due": "2026-05-08",
                    "items": [
                      { "description": "Office furniture transport" }
                    ]
                  },
                  "totals": {
                    "total": "1250.50",
                    "balance": "1250.50",
                    "gst": "113.68",
                    "paid": "0.00"
                  }
                }
              ]
            }
        """.trimIndent()

        val snapshot = InvoiceApiRepository.parseInvoicesPayload(sampleJson)

        assertEquals("7 days", snapshot.config.terms)
        assertEquals("exclusive", snapshot.config.defaultTax)
        assertEquals(1, snapshot.customers.size)
        assertEquals("Adelaide Logistics Pty Ltd", snapshot.customers.first().name)

        assertEquals(2, snapshot.invoices.size)
        val first = snapshot.invoices[0]
        assertEquals("INV-2026-0002", first.number)
        assertEquals(InvoiceStatus.OVERDUE, first.status)
        assertEquals("2026-05-01", first.issuedDate)
        assertEquals("Office furniture transport", first.servicesDescription)
        assertEquals(BigDecimal("1250.50"), first.totalAmount)

        val second = snapshot.invoices[1]
        assertEquals("INV-2026-0001", second.number)
        assertEquals(InvoiceStatus.PAID, second.status)
        assertEquals("Jane Smith", second.customerName)
    }

    @Test
    fun buildsValidCreateInvoiceJsonPayloadForNewCustomer() {
        val request = CreateInvoiceRequest(
            customerId = null,
            customerName = "Liam O'Connor",
            customerAddress = "88 The Parade, Norwood SA 5067",
            servicesPerformed = "3-bedroom home move with 2 movers and 8T truck",
            totalAmount = BigDecimal("875.5"),
            issuedDate = "2026-05-15",
            dueDate = "2026-05-22",
            issueImmediately = true
        )
        val config = WorkspaceCompanyConfig(
            terms = "Payment due within 7 days",
            defaultTax = "inclusive",
            gstStatus = "registered"
        )

        val payload = InvoiceApiRepository.buildCreateInvoicePayload(request, config)

        assertEquals("issue", payload.getString("action"))
        val data = payload.getJSONObject("data")
        assertEquals("2026-05-15", data.getString("issued"))
        assertEquals("2026-05-22", data.getString("due"))
        assertEquals("3-bedroom home move with 2 movers and 8T truck", data.getString("description"))

        val items = data.getJSONArray("items")
        assertEquals(1, items.length())
        assertEquals("875.50", items.getJSONObject(0).getString("rate"))
        assertEquals("inclusive", items.getJSONObject(0).getString("tax"))

        val newCustomer = data.getJSONObject("newCustomer")
        assertEquals("Liam O'Connor", newCustomer.getString("name"))
        assertEquals("88 The Parade, Norwood SA 5067", newCustomer.getString("address"))
    }

    @Test
    fun viewModelValidatesRequiredCreateInvoiceFields() {
        val viewModel = InvoicesViewModel()

        viewModel.submitNewInvoice()

        val form = viewModel.uiState.value.form
        assertEquals(R.string.validation_customer_name_required, form.customerNameError)
        assertEquals(R.string.validation_customer_address_required, form.customerAddressError)
        assertEquals(R.string.validation_services_required, form.servicesPerformedError)
        assertEquals(R.string.validation_amount_invalid, form.totalAmountError)

        viewModel.onCustomerNameChanged("Sarah Chen")
        viewModel.onCustomerAddressChanged("12 Unley Rd, Unley SA 5061")
        viewModel.onServicesPerformedChanged("Piano move & stair access")
        viewModel.onTotalAmountChanged("420.509") // should clamp to 2 decimals

        val updated = viewModel.uiState.value.form
        assertNull(updated.customerNameError)
        assertNull(updated.customerAddressError)
        assertNull(updated.servicesPerformedError)
        assertNull(updated.totalAmountError)
        assertEquals("420.50", updated.totalAmount)
        assertNotNull(updated.parsedAmount)
        assertTrue(updated.parsedAmount!!.compareTo(BigDecimal("420.50")) == 0)
    }
}
