package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.onGoogleSignInClicked
import com.example.data.model.AdminUser
import com.example.data.repository.AdminRepository
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldBorderSubtle
import com.example.ui.theme.LightGold
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SuccessGreen
import com.example.util.BiometricAuthManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminLoginScreen(
    currentEmail: String?,
    eligibilityChecked: Boolean,
    adminUser: AdminUser?,
    isBusy: Boolean,
    authErrorMessage: String?,
    adminRateLimit: Pair<Int, Long>,
    onGoogleVerified: () -> Unit,
    onEnrollCredentials: (operatorId: String, passcode: String, confirmPasscode: String, biometricEnabled: Boolean) -> Unit,
    onAuthenticateCredentials: (operatorId: String, passcode: String) -> Unit,
    onLaunchBiometricAdmin: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onBiometricAdminUnlocked: () -> Unit,
    onClearAuthError: () -> Unit,
    onSignOutAdmin: () -> Unit,
    onReturnToCustomerFlow: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val biometricCapability = remember(context) { BiometricAuthManager.checkCapability(context) }

    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    val defaultOpId = remember(adminUser) {
        adminUser?.let {
            it.operatorId.ifBlank { AdminRepository.defaultOperatorId(it.uid, it.email) }
        }.orEmpty()
    }

    var operatorIdInput by remember(adminUser?.uid) { mutableStateOf("") }
    var passcodeInput by remember(adminUser?.uid) { mutableStateOf("") }
    var confirmPasscodeInput by remember(adminUser?.uid) { mutableStateOf("") }
    var showPasscode by remember { mutableStateOf(false) }
    var enableBiometric2Fa by remember(adminUser?.uid) {
        mutableStateOf(biometricCapability.isUsable)
    }

    // Initialize Operator ID suggestion only during first-time enrollment
    LaunchedEffect(adminUser?.uid, adminUser?.adminPinHash) {
        if (adminUser != null && adminUser.adminPinHash.isNullOrBlank() && operatorIdInput.isBlank()) {
            operatorIdInput = defaultOpId
        }
    }

    val (failedAttempts, lockoutUntilMs) = adminRateLimit
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutUntilMs) {
        while (lockoutUntilMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
        nowMs = System.currentTimeMillis()
    }
    val remainingLockSeconds = ((lockoutUntilMs - nowMs) / 1000L).coerceAtLeast(0L)
    val isLockedOut = remainingLockSeconds > 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepBlack, CharcoalBlack, DeepBlack),
                )
            )
            .safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .widthIn(max = 540.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Isolation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onReturnToCustomerFlow,
                    modifier = Modifier.testTag("admin_back_to_customer_button"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Return to Customer Flow",
                        tint = SecondarySilver,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Customer App", color = SecondarySilver)
                }
                StatusPill(
                    text = "ISOLATED ADMIN PORTAL",
                    color = AmberOrange,
                    icon = Icons.Default.Shield,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Distinct Admin Security Crest
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ObsidianCard)
                        .border(2.dp, AmberOrange, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.swg_launcher_emblem),
                        contentDescription = "SONA-WANE GOLD Admin Crest",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "SONA-WANE ADMIN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = LightGold,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "GOVERNANCE • RBAC • AUDIT GATEWAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberOrange,
                    )
                    Text(
                        text = "Separated from customer vault & MPIN sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2-Stage Verification Progress Strip
            LuxuryCard(highlightGold = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = if (currentEmail != null && adminUser != null) SuccessGreen else RoyalGold,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "1. Admin Identity",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (currentEmail != null && adminUser != null) SuccessGreen else PureWhite,
                        )
                    }
                    Text("→", color = SecondarySilver)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = if (adminUser != null) AmberOrange else SecondarySilver,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "2. Unique Admin Credentials",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (adminUser != null) AmberOrange else SecondarySilver,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                // STATE 1: No signed-in Google account in Admin Portal
                currentEmail == null -> {
                    LuxuryCard(highlightGold = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AmberOrange)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Administrator Identity Sign-In", style = MaterialTheme.typography.titleLarge, color = PureWhite)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Sign in with your authorized corporate Google identity. Customer onboarding, customer wallets, and user MPINs are excluded from this portal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondarySilver,
                        )
                        if (!googleError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            AdminAlertBox(message = googleError!!, isError = true)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OrangeActionButton(
                            text = if (googleLoading) "Verifying Admin Identity..." else "Verify Admin with Google",
                            enabled = !googleLoading,
                            icon = Icons.Default.Shield,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_google_sign_in_button"),
                            onClick = {
                                googleError = null
                                googleLoading = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        googleLoading = false
                                        onGoogleVerified()
                                    },
                                    onAuthError = { msg ->
                                        googleLoading = false
                                        googleError = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = {
                                        googleLoading = false
                                    },
                                )
                            },
                        )
                    }
                }

                // STATE 2: Checking Firestore admins/{uid} RBAC record
                !eligibilityChecked -> {
                    LuxuryCard(highlightGold = true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(color = AmberOrange, modifier = Modifier.size(28.dp))
                            Column {
                                Text("Checking RBAC Authorization...", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                Text(currentEmail, style = MonoSmallStyle, color = LightGold)
                            }
                        }
                    }
                }

                // STATE 3: Signed-in account is NOT an active admin in Firestore RBAC
                adminUser == null -> {
                    LuxuryCard(highlightGold = true) {
                        Text("Unauthorized Admin Identity", style = MaterialTheme.typography.headlineMedium, color = ErrorRed)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Signed in as: $currentEmail",
                            style = MonoSmallStyle,
                            color = LightGold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This account does not have an active role in Firestore RBAC. Contact the Super Admin to assign a role and unique Operator ID before signing in here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondarySilver,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OrangeActionButton(
                            text = "Sign Out & Switch Admin Account",
                            icon = Icons.AutoMirrored.Filled.Logout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_unauthorized_sign_out_button"),
                            onClick = onSignOutAdmin,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldOutlinedButton(
                            text = "Return to Standard Customer App",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onReturnToCustomerFlow,
                        )
                    }
                }

                // STATE 4A: Active Admin — First-Time Unique Credential Enrollment
                adminUser.adminPinHash.isNullOrBlank() -> {
                    LuxuryCard(highlightGold = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enroll Unique Admin Credentials", style = MaterialTheme.typography.titleLarge, color = LightGold)
                                Text("${adminUser.displayName} • ${adminUser.email}", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                            }
                            StatusPill(text = adminUser.role, color = AmberOrange)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            adminUser.permissions.forEach { perm ->
                                StatusPill(text = perm, color = RoyalGold)
                            }
                        }
                        HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            "First-time portal setup: confirm your unique Admin Operator ID and create an isolated Admin Security Passcode (min 6 characters). This is separate from customer MPINs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = operatorIdInput,
                            onValueChange = {
                                onClearAuthError()
                                operatorIdInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }.take(32)
                            },
                            label = { Text("Unique Admin Operator ID *") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = AmberOrange) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_enroll_operator_id_input"),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = passcodeInput,
                            onValueChange = {
                                onClearAuthError()
                                passcodeInput = it.take(32)
                            },
                            label = { Text("New Admin Passcode (min 6 chars) *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalGold) },
                            trailingIcon = {
                                IconButton(onClick = { showPasscode = !showPasscode }) {
                                    Icon(
                                        if (showPasscode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Passcode Visibility",
                                    )
                                }
                            },
                            visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_enroll_passcode_input"),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirmPasscodeInput,
                            onValueChange = {
                                onClearAuthError()
                                confirmPasscodeInput = it.take(32)
                            },
                            label = { Text("Confirm Admin Passcode *") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = RoyalGold) },
                            visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_enroll_confirm_input"),
                        )
                        if (biometricCapability.isUsable) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Enable Biometric 2FA Unlock", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                                    Text("Allow Fingerprint / Face unlock on future admin logins", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                                }
                                Switch(checked = enableBiometric2Fa, onCheckedChange = { enableBiometric2Fa = it })
                            }
                        }
                        if (!authErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AdminAlertBox(message = authErrorMessage, isError = true)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        GoldPrimaryButton(
                            text = if (isBusy) "Enrolling Credentials..." else "Enroll Credentials & Enter Console",
                            enabled = !isBusy,
                            icon = Icons.Default.VerifiedUser,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_enroll_submit_button"),
                            onClick = {
                                onEnrollCredentials(
                                    operatorIdInput,
                                    passcodeInput,
                                    confirmPasscodeInput,
                                    enableBiometric2Fa && biometricCapability.isUsable,
                                )
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onSignOutAdmin,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text("Sign Out & Switch Account", color = SecondarySilver)
                        }
                    }
                }

                // STATE 4B: Active Admin — Unique Admin Credential Login
                else -> {
                    LuxuryCard(highlightGold = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Admin Credential Challenge", style = MaterialTheme.typography.titleLarge, color = PureWhite)
                                Text("${adminUser.displayName} • ${adminUser.email}", style = MaterialTheme.typography.bodySmall, color = LightGold)
                            }
                            StatusPill(text = adminUser.role, color = AmberOrange)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            adminUser.permissions.forEach { perm ->
                                StatusPill(text = perm, color = RoyalGold)
                            }
                        }

                        HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 12.dp))

                        if (isLockedOut) {
                            AdminAlertBox(
                                message = "Security Lockout Active: Too many failed credential attempts. Retry in ${remainingLockSeconds}s.",
                                isError = true,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        } else if (failedAttempts > 0) {
                            AdminAlertBox(
                                message = "Failed admin verification attempts: $failedAttempts / 5 (all attempts are audit-logged).",
                                isError = false,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = operatorIdInput,
                            onValueChange = {
                                onClearAuthError()
                                operatorIdInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }.take(32)
                            },
                            label = { Text("Unique Admin Operator ID") },
                            placeholder = { Text("e.g. $defaultOpId") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = AmberOrange) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            singleLine = true,
                            enabled = !isLockedOut && !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_operator_id_input"),
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = passcodeInput,
                            onValueChange = {
                                onClearAuthError()
                                passcodeInput = it.take(32)
                            },
                            label = { Text("Admin Security Passcode") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RoyalGold) },
                            trailingIcon = {
                                IconButton(onClick = { showPasscode = !showPasscode }) {
                                    Icon(
                                        if (showPasscode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Passcode Visibility",
                                    )
                                }
                            },
                            visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            enabled = !isLockedOut && !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_passcode_input"),
                        )

                        if (!authErrorMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AdminAlertBox(message = authErrorMessage, isError = true)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OrangeActionButton(
                            text = if (isBusy) "Verifying Credentials..." else "Unlock Admin Console",
                            enabled = !isBusy && !isLockedOut,
                            icon = Icons.Default.AdminPanelSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_login_submit_button"),
                            onClick = { onAuthenticateCredentials(operatorIdInput, passcodeInput) },
                        )

                        if (adminUser.biometricAdminEnabled && biometricCapability.isUsable) {
                            Spacer(modifier = Modifier.height(10.dp))
                            GoldOutlinedButton(
                                text = "Unlock with Biometric 2FA",
                                enabled = !isBusy && !isLockedOut,
                                icon = Icons.Default.Fingerprint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_biometric_login_button"),
                                onClick = {
                                    onLaunchBiometricAdmin(
                                        { onBiometricAdminUnlocked() },
                                        { },
                                    )
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = {
                                    if (operatorIdInput.isBlank()) operatorIdInput = defaultOpId
                                },
                            ) {
                                Text("Fill Assigned ID", color = LightGold)
                            }
                            TextButton(
                                onClick = onSignOutAdmin,
                                modifier = Modifier.testTag("admin_sign_out_button"),
                            ) {
                                Text("Sign Out Session", color = SecondarySilver)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "All administrator logins, failed credential challenges, and role changes are recorded to the immutable Firestore audit log.",
                style = MaterialTheme.typography.labelSmall,
                color = SecondarySilver,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AdminAlertBox(message: String, isError: Boolean) {
    val accent = if (isError) ErrorRed else AmberOrange
    Surface(
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            modifier = Modifier.padding(10.dp),
        )
    }
}
