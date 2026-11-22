package com.hfremovalsadelaide.invoices.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.ui.ConnectionErrorKind
import com.hfremovalsadelaide.invoices.ui.theme.ForestActive
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.GoldDeep
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite
import com.hfremovalsadelaide.invoices.ui.theme.SageSurface

/**
 * Branded launch overlay displaying the official HF Removals Adelaide emblem over an
 * atmospheric forest green gradient while the live workspace initializes.
 */
@Composable
fun BrandedSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ForestCanopy, ForestGreen, ForestActive)
                    )
                )
                .padding(horizontal = 32.dp)
                .testTag("branded_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = PureWhite,
                    shadowElevation = 14.dp,
                    border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.85f)),
                    modifier = Modifier.size(136.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hf_logo),
                        contentDescription = stringResource(id = R.string.splash_logo_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = stringResource(id = R.string.brand_company_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = PureWhite,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .background(GoldAccent)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(id = R.string.brand_workspace_subtitle).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldAccent,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(36.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = GoldAccent,
                        trackColor = PureWhite.copy(alpha = 0.18f),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.splash_connecting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureWhite.copy(alpha = 0.88f)
                    )
                }
            }
        }
    }
}

/**
 * Full-screen branded recovery panel displayed when the device is offline, a connection error
 * occurs, or an untrusted SSL certificate is blocked.
 */
@Composable
fun ConnectionErrorScreen(
    errorKind: ConnectionErrorKind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, titleRes, messageRes) = when (errorKind) {
        ConnectionErrorKind.OFFLINE -> Triple(
            Icons.Rounded.WifiOff,
            R.string.error_offline_title,
            R.string.error_offline_message
        )
        ConnectionErrorKind.SSL_BLOCKED -> Triple(
            Icons.Rounded.GppBad,
            R.string.error_ssl_title,
            R.string.error_ssl_message
        )
        else -> Triple(
            Icons.Rounded.CloudOff,
            R.string.error_connection_title,
            R.string.error_connection_message
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SageSurface)
            .padding(24.dp)
            .testTag("connection_error_screen"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(ForestGreen, GoldAccent, ForestGreen)
                            )
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hf_logo),
                        contentDescription = stringResource(id = R.string.splash_logo_description),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(72.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        shape = CircleShape,
                        color = if (errorKind == ConnectionErrorKind.SSL_BLOCKED) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (errorKind == ConnectionErrorKind.SSL_BLOCKED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    ForestGreen
                                },
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(id = titleRes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(id = messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    Button(
                        onClick = onRetry,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForestGreen,
                            contentColor = PureWhite
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.action_retry),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact pill displayed at the bottom while an invoice PDF or CSV is being securely downloaded.
 */
@Composable
fun DownloadProgressBanner(
    visible: Boolean,
    statusText: String,
    progress: Float? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ForestCanopy,
            border = BorderStroke(1.dp, GoldAccent),
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("download_progress_banner")
        ) {
            val percentLabel = progress?.let {
                stringResource(id = R.string.download_progress_percent, (it * 100).toInt())
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(start = 18.dp, end = if (onCancel != null) 6.dp else 18.dp)
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = listOfNotNull(statusText, percentLabel).joinToString(", ")
                    }
            ) {
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        color = GoldAccent,
                        trackColor = PureWhite.copy(alpha = 0.2f),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        color = GoldAccent,
                        trackColor = PureWhite.copy(alpha = 0.2f),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (percentLabel != null) "$statusText  $percentLabel" else statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = PureWhite,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onCancel != null) {
                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = GoldAccent),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("download_cancel_button")
                    ) {
                        Text(stringResource(id = R.string.download_cancel_action))
                    }
                }
            }
        }
    }
}

/**
 * Subtle non-intrusive offline strip displayed if network drops after the workspace has loaded.
 */
@Composable
fun OfflineStatusBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        Surface(
            color = GoldDeep,
            contentColor = PureWhite,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("offline_status_banner")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.banner_offline_indicator),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
