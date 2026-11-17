package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.DeviceSession
import com.example.data.model.KycRecord
import com.example.data.model.LoginHistoryEntry
import com.example.data.model.UserProfile
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.KycStatusIndicatorCard
import com.example.ui.components.KycStatusIndicatorPill
import com.example.ui.components.LuxuryCard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
import com.example.ui.theme.AmberOrange
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
import com.example.util.BiometricCapability
import com.example.util.Formatters

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    kycRecord: KycRecord?,
    devices: List<DeviceSession>,
    loginHistory: List<LoginHistoryEntry>,
    appConfig: AppConfig,
    isAdminEligible: Boolean,
    isBusy: Boolean,
    onUpdateProfile: (
        fullName: String,
        mobile: String,
        countryCode: String,
        email: String,
        dob: String,
        address: String,
        state: String,
        city: String,
        pincode: String,
        pan: String,
        photoUri: String,
    ) -> Unit,
    onSubmitKyc: (
        fullName: String,
        mobile: String,
        pan: String,
        aadhaarLast4: String,
        docType: String,
        docUri: String?,
        address: String,
        state: String,
        city: String,
        pincode: String,
    ) -> Unit,
    onVerifyMpin: (String) -> Boolean = { false },
    onToggleBiometric: (Boolean) -> Unit,
    walletBiometricLock: Boolean = true,
    tradeBiometricConfirm: Boolean = true,
    onToggleWalletBiometricLock: (Boolean) -> Unit = {},
    onToggleTradeBiometricConfirm: (Boolean) -> Unit = {},
    onChangeMpin: () -> Unit,
    onLockSession: () -> Unit,
    onRevokeOtherDevices: () -> Unit,
    onOpenBankAccounts: () -> Unit,
    onOpenReferrals: () -> Unit,
    onOpenMyQr: () -> Unit,
    onOpenMarketAlerts: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenSupport: () -> Unit,
    onRequestAccountDeletion: () -> Unit,
    onOpenKycFlow: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenBiometricAuth: () -> Unit = {},
    onOpenAdminPortal: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val biometricCapability = remember(context) { BiometricAuthManager.checkCapability(context) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showKycDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showVerifyMpinForBioDialog by remember { mutableStateOf(false) }
    var bioMpinInput by remember { mutableStateOf("") }
    var bioMpinErr by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && profile != null) {
                onUpdateProfile(
                    profile.fullName,
                    profile.mobileNumber,
                    profile.countryCode,
                    profile.email,
                    profile.dob,
                    profile.address,
                    profile.state,
                    profile.city,
                    profile.pincode,
                    profile.panMasked,
                    uri.toString(),
                )
            }
        },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Profile Identity Header
        item {
            LuxuryCard(highlightGold = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, RoyalGold, CircleShape)
                            .clickable {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                    ) {
                        if (!profile?.photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = profile?.photoUri,
                                contentDescription = "Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.swg_launcher_emblem),
                                contentDescription = "Default Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = DeepBlack.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = RoyalGold, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile?.fullName?.ifBlank { "Gold Investor" } ?: "Loading...",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PureWhite,
                        )
                        Text(
                            text = "ID: ${profile?.customUserId ?: "-"}",
                            style = MonoSmallStyle,
                            color = LightGold,
                        )
                        if (!profile?.mobileNumber.isNullOrBlank()) {
                            Text(
                                text = "${profile?.countryCode ?: "+91"} ${profile?.mobileNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondarySilver,
                            )
                        } else {
                            Text(
                                text = profile?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondarySilver,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KycStatusIndicatorPill(
                                profile = profile,
                                kycRecord = kycRecord,
                                prefix = "KYC: ",
                            )
                            val accStat = profile?.accountStatus ?: "ACTIVE"
                            if (accStat != "ACTIVE") {
                                StatusPill(text = accStat, color = ErrorRed)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    GoldOutlinedButton(
                        text = "Edit Profile & Mobile",
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_profile_button"),
                    )
                    GoldPrimaryButton(
                        text = if (profile?.kycStatus == "APPROVED") "View KYC" else "Complete KYC",
                        onClick = onOpenKycFlow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_kyc_button"),
                    )
                }
            }
        }

        // Admin Control Panel Card (Strictly gated for authorized admins)
        if (isAdminEligible) {
            item {
                LuxuryCard(highlightGold = true, onClick = onOpenAdminPortal) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_admin_portal_card"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = AmberOrange.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = AmberOrange)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Admin Control Panel", style = MaterialTheme.typography.titleMedium, color = LightGold)
                                Text("KYC Queue, Payouts, RBAC, Config & Audit Logs", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = RoyalGold)
                    }
                }
            }
        }

        // Dedicated Firestore-backed KYC Status Indicator Card
        item {
            KycStatusIndicatorCard(
                profile = profile,
                kycRecord = kycRecord,
                onActionClick = onOpenKycFlow,
            )
        }

        // Security & Vault Access Card
        item {
            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Security & Vault Access", style = MaterialTheme.typography.titleMedium, color = LightGold)
                    StatusPill(
                        text = if (biometricCapability.isUsable) "HW READY" else "UNAVAILABLE",
                        color = if (biometricCapability.isUsable) SuccessGreen else AmberOrange,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                ProfileMenuRow(
                    icon = Icons.Default.Settings,
                    title = "App Settings & Biometric Authentication",
                    subtitle = "Configure Biometric App Access & Tx Confirmation (DataStore)",
                    onClick = onOpenSettings,
                )
                ProfileMenuRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric Security Shield",
                    subtitle = "Authorize wallet vault, gold investments & payouts via BiometricPrompt",
                    onClick = onOpenBiometricAuth,
                )
                HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = RoyalGold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Biometric Login & Authorizations", style = MaterialTheme.typography.bodyLarge, color = PureWhite)
                            Text(biometricCapability.label, style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        }
                    }
                    Switch(
                        checked = profile?.biometricEnabled ?: false,
                        enabled = biometricCapability.isUsable || (profile?.biometricEnabled == true),
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (profile?.mpinHash.isNullOrBlank()) {
                                    onChangeMpin()
                                } else {
                                    bioMpinInput = ""
                                    bioMpinErr = null
                                    showVerifyMpinForBioDialog = true
                                }
                            } else {
                                onToggleBiometric(false)
                            }
                        },
                        modifier = Modifier.testTag("biometric_toggle"),
                    )
                }
                if (biometricCapability == BiometricCapability.NOT_ENROLLED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enroll Fingerprint or Face Unlock in Android Settings to activate biometric login.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberOrange,
                    )
                }
                if (profile?.biometricEnabled == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Lock Digital Wallet with Biometrics", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                            Text("Require Fingerprint / Face to view balances & ledger", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        }
                        Switch(
                            checked = walletBiometricLock,
                            onCheckedChange = onToggleWalletBiometricLock,
                            modifier = Modifier.testTag("profile_wallet_biometric_switch"),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Confirm Bullion Trades with Biometrics", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                            Text("Require Fingerprint / Face for Buy & Sell execution", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        }
                        Switch(
                            checked = tradeBiometricConfirm,
                            onCheckedChange = onToggleTradeBiometricConfirm,
                            modifier = Modifier.testTag("profile_trade_biometric_switch"),
                        )
                    }
                }
                HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 8.dp))
                ProfileMenuRow(
                    icon = Icons.Default.Lock,
                    title = if (profile?.mpinHash.isNullOrBlank()) "Set Up 4-Digit MPIN" else "Change / Reset MPIN",
                    subtitle = if (profile?.mpinHash.isNullOrBlank()) "Required for selling, withdrawals & transfers" else "Salted SHA-256 PIN + OTP recovery active",
                    onClick = onChangeMpin,
                )
                ProfileMenuRow(
                    icon = Icons.Default.Devices,
                    title = "Active Devices & Login History (${devices.count { it.sessionActive }})",
                    subtitle = "Manage devices & logout remotely",
                    onClick = { showDevicesDialog = true },
                )
                ProfileMenuRow(
                    icon = Icons.Default.VerifiedUser,
                    title = "Lock App Vault Now",
                    subtitle = "Require MPIN or Biometric immediately",
                    onClick = onLockSession,
                )
            }
        }

        // Services & Compliance Card
        item {
            LuxuryCard {
                Text("Account & Services", style = MaterialTheme.typography.titleMedium, color = LightGold)
                Spacer(modifier = Modifier.height(6.dp))
                ProfileMenuRow(Icons.Default.AccountBalance, "Linked Bank Accounts", "Manage verified payout accounts", onOpenBankAccounts)
                ProfileMenuRow(Icons.Default.AddAlert, "Bullion Price Alerts", "24K, 22K, 18K Gold & 999 Silver targets", onOpenMarketAlerts)
                ProfileMenuRow(Icons.Default.QrCode2, "My Bullion QR Code", "Receive Gold, Silver & Wallet transfers", onOpenMyQr)
                ProfileMenuRow(Icons.Default.CardGiftcard, "Referrals & Rewards", "Invite friends & earn program rewards", onOpenReferrals)
                ProfileMenuRow(Icons.Default.Notifications, "Notifications & Announcements", "Inbox & push notification preferences", onOpenNotifications)
                ProfileMenuRow(Icons.Default.SupportAgent, "Help & Support Centre", "Guides, FAQs & priority tickets", onOpenSupport)
            }
        }

        // Privacy, Legal & Sign Out
        item {
            LuxuryCard {
                Text("Privacy & Compliance", style = MaterialTheme.typography.titleMedium, color = LightGold)
                Spacer(modifier = Modifier.height(6.dp))
                ProfileMenuRow(
                    icon = Icons.Default.Download,
                    title = "Export Personal Data Summary",
                    subtitle = "Download your profile & KYC metadata",
                    onClick = {
                        val export = buildString {
                            appendLine("SONA-WANE GOLD PERSONAL DATA EXPORT")
                            appendLine("Name: ${profile?.fullName}")
                            appendLine("User ID: ${profile?.customUserId}")
                            appendLine("Mobile: ${profile?.countryCode} ${profile?.mobileNumber}")
                            appendLine("Email: ${profile?.email}")
                            appendLine("City/State: ${profile?.city}, ${profile?.state} - ${profile?.pincode}")
                            appendLine("KYC Status: ${profile?.kycStatus}")
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "SONA-WANE GOLD Data Export")
                            putExtra(Intent.EXTRA_TEXT, export)
                        }
                        context.startActivity(Intent.createChooser(intent, "Save or Share Data Export"))
                    },
                )
                ProfileMenuRow(Icons.Default.Policy, "Terms & Conditions", "Bullion & GST trading terms", { showPolicyDialog = "TERMS" })
                ProfileMenuRow(Icons.Default.Badge, "Privacy Policy", "Data protection & encryption policy", { showPolicyDialog = "PRIVACY" })
                ProfileMenuRow(Icons.Default.DeleteForever, "Request Account Deletion", "Submit compliance closure ticket", { showDeleteConfirm = true }, tint = ErrorRed)
            }
        }

        item {
            OrangeActionButton(
                text = "Sign Out",
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sign_out_button"),
                onClick = onSignOut,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            profile = profile,
            isBusy = isBusy,
            onDismiss = { showEditProfileDialog = false },
            onSave = { fn, mob, cc, em, dob, addr, st, city, pin, pan ->
                onUpdateProfile(fn, mob, cc, em, dob, addr, st, city, pin, pan, profile?.photoUri.orEmpty())
                showEditProfileDialog = false
            },
        )
    }

    if (showKycDialog) {
        KycSubmissionDialog(
            profile = profile,
            kycRecord = kycRecord,
            isBusy = isBusy,
            onDismiss = { showKycDialog = false },
            onSubmit = { fn, mob, pan, aadhaar, docType, docUri, addr, st, city, pin ->
                onSubmitKyc(fn, mob, pan, aadhaar, docType, docUri, addr, st, city, pin)
                showKycDialog = false
            },
        )
    }

    if (showDevicesDialog) {
        AlertDialog(
            onDismissRequest = { showDevicesDialog = false },
            title = { Text("Devices & Login History") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Registered Devices", style = MaterialTheme.typography.titleSmall, color = LightGold)
                    devices.forEach { d ->
                        Surface(color = ObsidianCard, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(d.deviceName, fontWeight = FontWeight.Bold, color = PureWhite)
                                    StatusPill(text = if (d.sessionActive) "ACTIVE" else "REVOKED", color = if (d.sessionActive) SuccessGreen else ErrorRed)
                                }
                                Text("${d.deviceModel} • Android ${d.osVersion}", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent Logins", style = MaterialTheme.typography.titleSmall, color = LightGold)
                    loginHistory.take(8).forEach { h ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${h.deviceName} (${h.method})", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                            Text(Formatters.formatTimestamp(h.createdAt), style = MonoSmallStyle, color = PureWhite)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRevokeOtherDevices()
                    showDevicesDialog = false
                }) { Text("Logout All Other Devices", color = AmberOrange) }
            },
            dismissButton = { TextButton(onClick = { showDevicesDialog = false }) { Text("Close") } },
        )
    }

    if (showPolicyDialog != null) {
        val isTerms = showPolicyDialog == "TERMS"
        AlertDialog(
            onDismissRequest = { showPolicyDialog = null },
            title = { Text(if (isTerms) "Terms & Conditions" else "Privacy Policy") },
            text = {
                Text(
                    text = if (isTerms) appConfig.termsText else appConfig.privacyText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { showPolicyDialog = null }) { Text("Understood") } },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Request Account Deletion?") },
            text = {
                Text("Indian bullion compliance requires auditing open holdings and INR balances before closing a vault. Submitting will create a priority closure request.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onRequestAccountDeletion()
                    showDeleteConfirm = false
                }) { Text("Submit Request", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showVerifyMpinForBioDialog) {
        AlertDialog(
            onDismissRequest = { showVerifyMpinForBioDialog = false },
            title = { Text("Verify MPIN to Enable Biometrics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your 4-digit MPIN first, then confirm your Fingerprint or Face ID to link device biometrics to your vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                    OutlinedTextField(
                        value = bioMpinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) bioMpinInput = it },
                        label = { Text("4-Digit MPIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bio_enable_mpin_input"),
                    )
                    bioMpinErr?.takeIf { it.isNotBlank() }?.let { errText ->
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = bioMpinInput.length == 4,
                    onClick = {
                        if (onVerifyMpin(bioMpinInput)) {
                            showVerifyMpinForBioDialog = false
                            bioMpinInput = ""
                            bioMpinErr = null
                            onToggleBiometric(true)
                        } else {
                            bioMpinErr = "Incorrect MPIN. Try again."
                        }
                    },
                ) { Text("Verify & Scan Biometric", color = RoyalGold) }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyMpinForBioDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = RoyalGold,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = PureWhite)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SecondarySilver)
    }
}

@Composable
private fun EditProfileDialog(
    profile: UserProfile?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String, String, String) -> Unit,
) {
    var fullName by remember { mutableStateOf(profile?.fullName.orEmpty()) }
    var countryCode by remember { mutableStateOf(profile?.countryCode?.ifBlank { "+91" } ?: "+91") }
    var mobile by remember { mutableStateOf(profile?.mobileNumber.orEmpty()) }
    var email by remember { mutableStateOf(profile?.email.orEmpty()) }
    var dob by remember { mutableStateOf(profile?.dob.orEmpty()) }
    var address by remember { mutableStateOf(profile?.address.orEmpty()) }
    var city by remember { mutableStateOf(profile?.city.orEmpty()) }
    var state by remember { mutableStateOf(profile?.state.orEmpty()) }
    var pincode by remember { mutableStateOf(profile?.pincode.orEmpty()) }
    var pan by remember { mutableStateOf(profile?.panMasked.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registration & Profile Details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = countryCode, onValueChange = { countryCode = it }, label = { Text("Code") }, singleLine = true, modifier = Modifier.width(80.dp))
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                        label = { Text("Mobile (10 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional)") }, singleLine = true)
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Date of Birth (DD/MM/YYYY)") }, singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = pincode,
                        onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("PIN Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = pan,
                        onValueChange = { pan = it.uppercase().take(10) },
                        label = { Text("PAN") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                err?.let { errText ->
                    Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy,
                onClick = {
                    if (fullName.isBlank()) {
                        err = "Full name is required."
                        return@TextButton
                    }
                    if (mobile.isNotBlank() && mobile.length != 10) {
                        err = "Enter a valid 10-digit mobile number."
                        return@TextButton
                    }
                    onSave(fullName, mobile, countryCode, email, dob, address, state, city, pincode, pan)
                },
            ) { Text("Save Profile", color = RoyalGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun KycSubmissionDialog(
    profile: UserProfile?,
    kycRecord: KycRecord?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String?, String, String, String, String) -> Unit,
) {
    var fullName by remember { mutableStateOf(kycRecord?.userName?.ifBlank { profile?.fullName.orEmpty() } ?: profile?.fullName.orEmpty()) }
    var mobile by remember { mutableStateOf(kycRecord?.mobileNumber?.ifBlank { profile?.mobileNumber.orEmpty() } ?: profile?.mobileNumber.orEmpty()) }
    var pan by remember { mutableStateOf(kycRecord?.panNumber.orEmpty()) }
    var aadhaarLast4 by remember { mutableStateOf(kycRecord?.aadhaarLast4.orEmpty()) }
    var docType by remember { mutableStateOf(kycRecord?.documentType ?: "PAN_CARD") }
    var docUri by remember { mutableStateOf(kycRecord?.documentRefUri) }
    var address by remember { mutableStateOf(kycRecord?.addressLine?.ifBlank { profile?.address.orEmpty() } ?: profile?.address.orEmpty()) }
    var state by remember { mutableStateOf(kycRecord?.state?.ifBlank { profile?.state.orEmpty() } ?: profile?.state.orEmpty()) }
    var city by remember { mutableStateOf(kycRecord?.city?.ifBlank { profile?.city.orEmpty() } ?: profile?.city.orEmpty()) }
    var pincode by remember { mutableStateOf(kycRecord?.pincode?.ifBlank { profile?.pincode.orEmpty() } ?: profile?.pincode.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) docUri = uri.toString() },
    )

    val isApproved = profile?.kycStatus == "APPROVED"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("KYC & PAN Verification") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KeyValueRow("Current Status", profile?.kycStatus ?: "NOT_STARTED", valueColor = statusColorFor(profile?.kycStatus ?: "NOT_STARTED"))
                if (!isApproved) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Legal Name (as on PAN) *") }, singleLine = true)
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it.filter { c -> c.isDigit() }.take(10) },
                        label = { Text("Registered Mobile *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pan,
                        onValueChange = { pan = it.uppercase().take(10) },
                        label = { Text("PAN Number (e.g. ABCDE1234F) *") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = aadhaarLast4,
                        onValueChange = { aadhaarLast4 = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Aadhaar Last 4 Digits *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PAN_CARD", "AADHAAR", "PASSPORT").forEach { d ->
                            FilterChip(selected = docType == d, onClick = { docType = d }, label = { Text(d.replace("_", " ")) })
                        }
                    }
                    GoldOutlinedButton(
                        text = if (docUri == null) "Upload ID Proof (Photo Picker)" else "ID Document Selected",
                        onClick = { docPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Residential Address *") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City *") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State *") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(
                        value = pincode,
                        onValueChange = { pincode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("PIN Code (6 digits) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    err?.let { errText -> Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                } else {
                    KeyValueRow("Verified Name", kycRecord?.userName ?: profile?.fullName.orEmpty())
                    KeyValueRow("PAN", kycRecord?.panNumber ?: profile?.panMasked.orEmpty())
                    KeyValueRow("Document", kycRecord?.documentType ?: "PAN_CARD")
                    KeyValueRow("Location", "${kycRecord?.city}, ${kycRecord?.state}")
                }
            }
        },
        confirmButton = {
            if (!isApproved) {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        if (fullName.isBlank() || pan.length != 10 || aadhaarLast4.length != 4 || pincode.length != 6) {
                            err = "Enter valid Name, 10-char PAN, 4-digit Aadhaar suffix, and 6-digit PIN."
                            return@TextButton
                        }
                        onSubmit(fullName, mobile, pan, aadhaarLast4, docType, docUri, address, state, city, pincode)
                    },
                ) { Text("Submit KYC", color = RoyalGold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
