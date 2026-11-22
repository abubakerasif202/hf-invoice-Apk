package com.hfremovalsadelaide.invoices.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.pdf.DownloadedDocument
import com.hfremovalsadelaide.invoices.ui.JsDialogRequest
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.ForestSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.GoldDeep
import com.hfremovalsadelaide.invoices.ui.theme.GoldSoftTint
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import java.text.DateFormat
import java.util.Date

/**
 * Bottom sheet displayed immediately after an invoice PDF or CSV export finishes downloading,
 * giving one-tap access to Open, Share, and Print (A4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReadySheet(
    document: DownloadedDocument,
    onOpen: (DownloadedDocument) -> Unit,
    onShare: (DownloadedDocument) -> Unit,
    onPrint: (DownloadedDocument) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .testTag("document_ready_sheet")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ForestSoftTint,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = ForestGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                id = if (document.isPdf) {
                                    R.string.download_complete_title
                                } else {
                                    R.string.download_csv_complete_title
                                }
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                id = if (document.savedToPublicDownloads) {
                                    R.string.download_saved_to_downloads
                                } else {
                                    R.string.download_saved_to_app_storage
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = if (document.isPdf) {
                                Icons.Rounded.PictureAsPdf
                            } else {
                                Icons.Rounded.Description
                            },
                            contentDescription = null,
                            tint = if (document.isPdf) ForestGreen else GoldDeep,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = document.formattedSize,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onOpen(document) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForestGreen,
                        contentColor = PureWhite
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("sheet_open_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            id = if (document.isPdf) R.string.action_open_pdf else R.string.action_open_file
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { onShare(document) },
                        border = BorderStroke(1.2.dp, GoldAccent),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = GoldSoftTint,
                            contentColor = GoldDeep
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("sheet_share_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.action_share),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (document.isPdf) {
                        OutlinedButton(
                            onClick = { onPrint(document) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ForestGreen
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("sheet_print_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Print,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.action_print),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet showing recently downloaded invoice PDFs and CSV exports on this device so the
 * administrator can reopen, share, or print any invoice at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDocumentsSheet(
    documents: List<DownloadedDocument>,
    onOpen: (DownloadedDocument) -> Unit,
    onShare: (DownloadedDocument) -> Unit,
    onPrint: (DownloadedDocument) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .testTag("saved_documents_sheet")
            ) {
                Text(
                    text = stringResource(id = R.string.saved_pdfs_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(id = R.string.saved_pdfs_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (documents.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = ForestGreen.copy(alpha = 0.65f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(id = R.string.saved_pdfs_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.saved_pdfs_empty_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(documents, key = { it.fileName }) { doc ->
                            SavedDocumentRow(
                                document = doc,
                                onOpen = { onOpen(doc) },
                                onShare = { onShare(doc) },
                                onPrint = { onPrint(doc) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedDocumentRow(
    document: DownloadedDocument,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit
) {
    val dateLabel = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(document.downloadedAtMillis))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (document.isPdf) {
                Icons.Rounded.PictureAsPdf
            } else {
                Icons.Rounded.Description
            },
            contentDescription = null,
            tint = if (document.isPdf) ForestGreen else GoldDeep,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = document.fileName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${document.formattedSize} · $dateLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onOpen,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("saved_doc_open_${document.fileName}")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(id = R.string.action_open_pdf),
                tint = ForestGreen
            )
        }

        IconButton(
            onClick = onShare,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("saved_doc_share_${document.fileName}")
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(id = R.string.action_share),
                tint = GoldDeep
            )
        }

        if (document.isPdf) {
            IconButton(
                onClick = onPrint,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("saved_doc_print_${document.fileName}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Print,
                    contentDescription = stringResource(id = R.string.action_print),
                    tint = ForestGreen
                )
            }
        }
    }
}

/**
 * Material 3 dialog bridging JavaScript alert() and confirm() calls from the hosted invoice
 * workspace (such as confirming Void Invoice).
 */
@Composable
fun JsDialogHost(
    request: JsDialogRequest?,
    onDismiss: (confirmed: Boolean) -> Unit
) {
    if (request == null) return

    when (request) {
        is JsDialogRequest.Confirm -> {
            AlertDialog(
                onDismissRequest = { onDismiss(false) },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = stringResource(id = R.string.dialog_js_confirm_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = request.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onDismiss(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier.testTag("js_confirm_ok_button")
                    ) {
                        Text(text = stringResource(id = R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onDismiss(false) },
                        modifier = Modifier.testTag("js_confirm_cancel_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.action_cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        is JsDialogRequest.Alert -> {
            AlertDialog(
                onDismissRequest = { onDismiss(true) },
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Text(
                        text = stringResource(id = R.string.dialog_js_alert_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Text(
                        text = request.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { onDismiss(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier.testTag("js_alert_ok_button")
                    ) {
                        Text(text = stringResource(id = R.string.action_ok))
                    }
                }
            )
        }
    }
}
