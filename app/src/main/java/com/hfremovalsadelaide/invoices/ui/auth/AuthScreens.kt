package com.hfremovalsadelaide.invoices.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hfremovalsadelaide.invoices.R
import com.hfremovalsadelaide.invoices.ui.theme.ForestActive
import com.hfremovalsadelaide.invoices.ui.theme.ForestCanopy
import com.hfremovalsadelaide.invoices.ui.theme.ForestGreen
import com.hfremovalsadelaide.invoices.ui.theme.GoldAccent
import com.hfremovalsadelaide.invoices.ui.theme.PureWhite

enum class AuthMode(val label: String) {
    STAFF("Staff Sign-In"),
    ADMIN("Owner / Admin"),
    ACCEPT_INVITE("Accept Invite"),
    RESET_PASSWORD("Reset Password")
}

@Composable
fun BrandedSignInScreen(
    busy: Boolean,
    error: String?,
    onLoginStaff: (email: String, password: String) -> Unit,
    onLoginAdmin: (password: String) -> Unit,
    onAcceptInvite: (token: String, password: String) -> Unit,
    onResetPassword: (token: String, newPassword: String) -> Unit,
    onUseLegacyWebLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ForestCanopy, ForestGreen, ForestActive)))
            .imePadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.55f)),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .testTag("sign_in_card")
        ) {
            SignInFormContent(
                busy = busy,
                error = error,
                onLoginStaff = onLoginStaff,
                onLoginAdmin = onLoginAdmin,
                onAcceptInvite = onAcceptInvite,
                onResetPassword = onResetPassword,
                onUseLegacyWebLogin = onUseLegacyWebLogin,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInModalSheet(
    busy: Boolean,
    error: String?,
    onLoginStaff: (email: String, password: String) -> Unit,
    onLoginAdmin: (password: String) -> Unit,
    onAcceptInvite: (token: String, password: String) -> Unit,
    onResetPassword: (token: String, newPassword: String) -> Unit,
    onUseLegacyWebLogin: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        SignInFormContent(
            busy = busy,
            error = error,
            onLoginStaff = onLoginStaff,
            onLoginAdmin = onLoginAdmin,
            onAcceptInvite = onAcceptInvite,
            onResetPassword = onResetPassword,
            onUseLegacyWebLogin = onUseLegacyWebLogin,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignInFormContent(
    busy: Boolean,
    error: String?,
    onLoginStaff: (email: String, password: String) -> Unit,
    onLoginAdmin: (password: String) -> Unit,
    onAcceptInvite: (token: String, password: String) -> Unit,
    onResetPassword: (token: String, newPassword: String) -> Unit,
    onUseLegacyWebLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.STAFF) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.hf_logo),
                contentDescription = "HF Removals Adelaide",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Column {
                Text(
                    text = "HF Removals Adelaide",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Operations, Dispatch & Workforce Portal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AuthMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { mode = m },
                    label = { Text(m.label) },
                    modifier = Modifier.testTag("auth_mode_${m.name.lowercase()}")
                )
            }
        }

        when (mode) {
            AuthMode.STAFF -> {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Work email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email")
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visible = showPassword,
                    onToggleVisible = { showPassword = !showPassword },
                    onDone = { onLoginStaff(email, password) },
                    testTag = "auth_password"
                )
            }

            AuthMode.ADMIN -> {
                Text(
                    text = "Sign in with the primary administrator password configured on HF Removals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Administrator password",
                    visible = showPassword,
                    onToggleVisible = { showPassword = !showPassword },
                    onDone = { onLoginAdmin(password) },
                    testTag = "auth_admin_password"
                )
            }

            AuthMode.ACCEPT_INVITE -> {
                Text(
                    text = "Paste your invitation token from your manager and choose a password (10+ characters).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Invitation token") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_token")
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Choose new password (min 10 chars)",
                    visible = showPassword,
                    onToggleVisible = { showPassword = !showPassword },
                    onDone = { onAcceptInvite(token, password) },
                    testTag = "auth_password"
                )
            }

            AuthMode.RESET_PASSWORD -> {
                Text(
                    text = "Enter the reset token issued by your administrator and your new password (10+ characters).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Reset token") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_token")
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "New password (min 10 chars)",
                    visible = showPassword,
                    onToggleVisible = { showPassword = !showPassword },
                    onDone = { onResetPassword(token, password) },
                    testTag = "auth_password"
                )
            }
        }

        if (!error.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Button(
            onClick = {
                when (mode) {
                    AuthMode.STAFF -> onLoginStaff(email, password)
                    AuthMode.ADMIN -> onLoginAdmin(password)
                    AuthMode.ACCEPT_INVITE -> onAcceptInvite(token, password)
                    AuthMode.RESET_PASSWORD -> onResetPassword(token, password)
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(
                containerColor = ForestGreen,
                contentColor = PureWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("auth_submit")
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PureWhite, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    when (mode) {
                        AuthMode.STAFF, AuthMode.ADMIN -> "Sign In"
                        AuthMode.ACCEPT_INVITE -> "Activate Account"
                        AuthMode.RESET_PASSWORD -> "Update Password"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (onUseLegacyWebLogin != null) {
            TextButton(
                onClick = onUseLegacyWebLogin,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Open Classic Web Workspace")
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onDone: () -> Unit,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
