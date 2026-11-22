package com.hfremovalsadelaide.invoices.ui.invoices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.data.CustomerSummary
import com.hfremovalsadelaide.invoices.data.InvoiceSummary
import com.hfremovalsadelaide.invoices.ui.theme.ErrorContainerTint
import com.hfremovalsadelaide.invoices.ui.theme.ErrorCrimson
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.ForestSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.GoldDeep
import com.hfremovalsadelaide.invoices.ui.theme.GoldSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.MutedSage
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageOutline
import com.hfremovalsadelaide.invoices.ui.theme.SageSurface
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class ActiveDatePicker {
    NONE, ISSUE, DUE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    formState: CreateInvoiceFormState,
    availableCustomers: List<CustomerSummary>,
    onSelectCustomer: (CustomerSummary?) -> Unit,
    onCustomerNameChanged: (String) -> Unit,
    onCustomerAddressChanged: (String) -> Unit,
    onServicesPerformedChanged: (String) -> Unit,
    onTotalAmountChanged: (String) -> Unit,
    onIssueDateChanged: (String) -> Unit,
    onDueDateChanged: (String) -> Unit,
    onIssueImmediatelyChanged: (Boolean) -> Unit,
    onResetForm: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
    onGoToSignIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onNavigateBack()
    }

    val focusManager = LocalFocusManager.current
    var activeDatePicker by remember { mutableStateOf(ActiveDatePicker.NONE) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("create_invoice_screen"),
        // The shell's NavigationBar already consumes the system navigation-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = SageSurface,
        topBar = {
            CreateInvoiceTopBar(
                onBackClick = onNavigateBack,
                onResetClick = onResetForm
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp)
            ) {
                val errorText = formState.submissionError
                    ?: formState.submissionErrorResId?.let { stringResource(id = it) }

                if (!errorText.isNullOrBlank()) {
                    SubmissionErrorBanner(
                        message = errorText,
                        showSignInAction = formState.submissionErrorResId == R.string.create_invoice_auth_needed,
                        onSignInClick = onGoToSignIn
                    )
                }

                // Section 1: Customer Details
                FormSectionCard(
                    title = stringResource(id = R.string.create_invoice_section_customer)
                ) {
                    if (availableCustomers.isNotEmpty()) {
                        Text(
                            text = stringResource(id = R.string.create_invoice_quick_fill_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedSage
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = formState.selectedCustomerId == null,
                                onClick = { onSelectCustomer(null) },
                                label = { Text(stringResource(id = R.string.create_invoice_new_customer_chip)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ForestGreen,
                                    selectedLabelColor = PureWhite
                                )
                            )
                            availableCustomers.take(12).forEach { customer ->
                                val selected = formState.selectedCustomerId == customer.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectCustomer(customer) },
                                    label = { Text(customer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForestGreen,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    OutlinedTextField(
                        value = formState.customerName,
                        onValueChange = onCustomerNameChanged,
                        label = { Text(stringResource(id = R.string.field_customer_name)) },
                        placeholder = { Text(stringResource(id = R.string.field_customer_name_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = ForestGreen
                            )
                        },
                        isError = formState.customerNameError != null,
                        supportingText = formState.customerNameError?.let { errId ->
                            { Text(text = stringResource(id = errId)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
                        shape = MaterialTheme.shapes.medium,
                        colors = hfTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_customer_name")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.customerAddress,
                        onValueChange = onCustomerAddressChanged,
                        label = { Text(stringResource(id = R.string.field_customer_address)) },
                        placeholder = { Text(stringResource(id = R.string.field_customer_address_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = ForestGreen
                            )
                        },
                        isError = formState.customerAddressError != null,
                        supportingText = formState.customerAddressError?.let { errId ->
                            { Text(text = stringResource(id = errId)) }
                        },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        shape = MaterialTheme.shapes.medium,
                        colors = hfTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_customer_address")
                    )
                }

                // Section 2: Services Performed, Dates & Total Amount
                FormSectionCard(
                    title = stringResource(id = R.string.create_invoice_section_service)
                ) {
                    OutlinedTextField(
                        value = formState.servicesPerformed,
                        onValueChange = onServicesPerformedChanged,
                        label = { Text(stringResource(id = R.string.field_services_performed)) },
                        placeholder = { Text(stringResource(id = R.string.field_services_performed_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.LocalShipping,
                                contentDescription = null,
                                tint = ForestGreen
                            )
                        },
                        isError = formState.servicesPerformedError != null,
                        supportingText = formState.servicesPerformedError?.let { errId ->
                            { Text(text = stringResource(id = errId)) }
                        },
                        minLines = 3,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        shape = MaterialTheme.shapes.medium,
                        colors = hfTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_services_performed")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = formState.totalAmount,
                        onValueChange = onTotalAmountChanged,
                        label = { Text(stringResource(id = R.string.field_total_amount)) },
                        placeholder = { Text(stringResource(id = R.string.field_total_amount_hint)) },
                        prefix = {
                            Text(
                                text = "$ ",
                                style = MaterialTheme.typography.titleMedium,
                                color = ForestGreen
                            )
                        },
                        suffix = {
                            Text(
                                text = "AUD",
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldDeep
                            )
                        },
                        isError = formState.totalAmountError != null,
                        supportingText = formState.totalAmountError?.let { errId ->
                            { Text(text = stringResource(id = errId)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        shape = MaterialTheme.shapes.medium,
                        colors = hfTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_total_amount")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DateSelectorField(
                            label = stringResource(id = R.string.field_issue_date),
                            isoDate = formState.issuedDate,
                            onClick = { activeDatePicker = ActiveDatePicker.ISSUE },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_issue_date")
                        )

                        DateSelectorField(
                            label = stringResource(id = R.string.field_due_date),
                            isoDate = formState.dueDate,
                            onClick = { activeDatePicker = ActiveDatePicker.DUE },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_due_date")
                        )
                    }
                }

                // Section 3: Draft vs Issue Status
                FormSectionCard(
                    title = stringResource(id = R.string.create_invoice_section_status)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatusModeTile(
                            title = stringResource(id = R.string.mode_save_draft),
                            selected = !formState.issueImmediately,
                            onClick = { onIssueImmediatelyChanged(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_draft_chip")
                        )
                        StatusModeTile(
                            title = stringResource(id = R.string.mode_issue_invoice),
                            selected = formState.issueImmediately,
                            onClick = { onIssueImmediatelyChanged(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mode_issue_chip")
                        )
                    }
                }

                // Live Branded Summary Preview
                InvoiceLiveSummaryCard(formState = formState)

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSubmit()
                    },
                    enabled = !formState.isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = PureWhite
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("submit_invoice_button")
                ) {
                    if (formState.isSubmitting) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            trackColor = PureWhite.copy(alpha = 0.25f),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.create_invoice_submitting),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                id = if (formState.issueImmediately) {
                                    R.string.create_invoice_submit_issue
                                } else {
                                    R.string.create_invoice_submit_draft
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (activeDatePicker != ActiveDatePicker.NONE) {
        val currentIso = if (activeDatePicker == ActiveDatePicker.ISSUE) {
            formState.issuedDate
        } else {
            formState.dueDate
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseIsoToUtcMillis(currentIso)
        )

        DatePickerDialog(
            onDismissRequest = { activeDatePicker = ActiveDatePicker.NONE },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatted = formatUtcMillisToIso(millis)
                            if (activeDatePicker == ActiveDatePicker.ISSUE) {
                                onIssueDateChanged(formatted)
                            } else {
                                onDueDateChanged(formatted)
                            }
                        }
                        activeDatePicker = ActiveDatePicker.NONE
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.action_ok),
                        color = ForestGreen
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDatePicker = ActiveDatePicker.NONE }) {
                    Text(
                        text = stringResource(id = R.string.action_cancel),
                        color = MutedSage
                    )
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun CreateInvoiceTopBar(
    onBackClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Surface(
        color = ForestCanopy,
        contentColor = PureWhite,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("create_invoice_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.nav_back_description),
                        tint = PureWhite
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.create_invoice_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(id = R.string.create_invoice_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onResetClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("create_invoice_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(id = R.string.create_invoice_reset),
                        tint = PureWhite.copy(alpha = 0.9f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(GoldAccent.copy(alpha = 0.65f))
            )
        }
    }
}

@Composable
private fun FormSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = PureWhite,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, SageOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = GoldDeep
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DateSelectorField(
    label: String,
    isoDate: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = SageSurface,
        border = BorderStroke(1.dp, SageOutline),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSage
                )
                Text(
                    text = isoDate,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StatusModeTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) ForestSoftTint else SageSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) ForestGreen else SageOutline
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) ForestGreen else MutedSage
            )
        }
    }
}

@Composable
private fun InvoiceLiveSummaryCard(
    formState: CreateInvoiceFormState
) {
    val displayAmount = InvoiceSummary.formatAud(formState.parsedAmount ?: BigDecimal.ZERO)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = PureWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SageOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("create_invoice_summary_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(ForestGreen)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.create_invoice_preview_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldDeep
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (formState.issueImmediately) GoldSoftTint else ForestSoftTint
                    ) {
                        Text(
                            text = if (formState.issueImmediately) "Issued" else "Draft",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (formState.issueImmediately) GoldDeep else ForestGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = formState.customerName.ifBlank { "Customer name" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (formState.customerAddress.isNotBlank()) {
                    Text(
                        text = formState.customerAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSage,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (formState.servicesPerformed.isNotBlank()) {
                    Text(
                        text = formState.servicesPerformed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(
                    color = SageOutline,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${formState.issuedDate} · Due ${formState.dueDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedSage
                    )
                    Text(
                        text = displayAmount,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ForestCanopy
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(GoldAccent)
            )
        }
    }
}

@Composable
private fun SubmissionErrorBanner(
    message: String,
    showSignInAction: Boolean,
    onSignInClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = ErrorContainerTint,
        border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("create_invoice_error_banner")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = ErrorCrimson,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorCrimson,
                    modifier = Modifier.weight(1f)
                )
            }
            if (showSignInAction) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onSignInClick) {
                    Text(
                        text = stringResource(id = R.string.invoices_go_to_sign_in),
                        color = ForestGreen,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun hfTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ForestGreen,
    unfocusedBorderColor = SageOutline,
    focusedLabelColor = ForestGreen,
    cursorColor = ForestGreen,
    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite
)

private fun parseIsoToUtcMillis(isoDate: String): Long? {
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        parser.parse(isoDate)?.time
    }.getOrNull()
}

private fun formatUtcMillisToIso(millis: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(millis))
}
