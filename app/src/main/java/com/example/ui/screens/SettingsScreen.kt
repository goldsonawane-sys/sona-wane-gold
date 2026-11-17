package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BgDropMonitorState
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.UserProfile
import com.example.service.GoldPriceMonitorService
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
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
import com.example.util.BullionNotificationHelper
import com.example.util.Formatters

@Composable
fun SettingsScreen(
    profile: UserProfile?,
    biometricAppAccess: Boolean,
    biometricTxConfirm: Boolean,
    walletBiometricLock: Boolean,
    rates: LiveMarketRates = LiveMarketRates(),
    firestoreGoldSummary: FirestoreGoldSummary = FirestoreGoldSummary(),
    bgDropMonitor: BgDropMonitorState = BgDropMonitorState(),
    onConfigureGoldMonitor: (enabled: Boolean, thresholdInr: Double, purity: String) -> Unit = { _, _, _ -> },
    onRunGoldMonitorCheckNow: () -> Unit = {},
    onToggleBiometricMaster: (Boolean) -> Unit,
    onToggleBiometricAppAccess: (Boolean) -> Unit,
    onToggleBiometricTxConfirm: (Boolean) -> Unit,
    onToggleWalletBiometricLock: (Boolean) -> Unit,
    onUpdateNotificationPrefs: (sound: Boolean, vibration: Boolean, priceAlerts: Boolean, announcements: Boolean) -> Unit,
    onChangeMpin: () -> Unit,
    onLockVaultNow: () -> Unit,
    onOpenPriceAlerts: () -> Unit,
    onOpenBiometricAuth: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val biometricCapability = remember(context) { BiometricAuthManager.checkCapability(context) }

    val masterBiometricEnabled = biometricAppAccess || biometricTxConfirm || (profile?.biometricEnabled == true)
    val notifSound = profile?.notifSoundEnabled ?: true
    val notifVibration = profile?.notifVibrationEnabled ?: true
    val notifAlerts = profile?.notifPriceAlerts ?: true
    val notifAnnouncements = profile?.notifAnnouncements ?: true

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = "Biometrics, DataStore & Alerts",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Biometric Authentication Settings Card (Local DataStore)
            item {
                LuxuryCard(
                    highlightGold = true,
                    modifier = Modifier.testTag("settings_biometric_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_biometric_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LightGold,
                                )
                                Text(
                                    text = biometricCapability.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondarySilver,
                                )
                            }
                        }
                        StatusPill(
                            text = if (biometricCapability.isUsable) "HW READY" else "UNAVAILABLE",
                            color = if (biometricCapability.isUsable) SuccessGreen else AmberOrange,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // DataStore persistence badge
                    Surface(
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, GoldBorderSubtle),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = RoyalGold,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saved locally in Jetpack DataStore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondarySilver,
                                )
                            }
                            StatusPill(
                                text = stringResource(R.string.settings_datastore_badge),
                                color = RoyalGold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Master Biometric Authentication Toggle
                    SettingsToggleRow(
                        icon = Icons.Default.Security,
                        title = "Enable Biometric Authentication",
                        subtitle = "Master toggle for app access & transaction verification",
                        checked = masterBiometricEnabled,
                        enabled = biometricCapability.isUsable || masterBiometricEnabled,
                        onCheckedChange = onToggleBiometricMaster,
                        switchTestTag = "settings_biometric_master_switch",
                    )

                    HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 8.dp))

                    // App Access Toggle (DataStore BIOMETRIC_APP_ACCESS)
                    SettingsToggleRow(
                        icon = Icons.Default.VerifiedUser,
                        title = stringResource(R.string.settings_biometric_app_access),
                        subtitle = "Require Fingerprint or Face ID when opening SONA-WANE GOLD",
                        checked = biometricAppAccess,
                        enabled = biometricCapability.isUsable || biometricAppAccess,
                        onCheckedChange = onToggleBiometricAppAccess,
                        switchTestTag = "settings_biometric_app_access_switch",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Transaction Confirmation Toggle (DataStore TRADE_BIOMETRIC_CONFIRM)
                    SettingsToggleRow(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.settings_biometric_tx_confirm),
                        subtitle = "Require biometrics before Buy, Sell, Calculator & Withdrawal payments",
                        checked = biometricTxConfirm,
                        enabled = biometricCapability.isUsable || biometricTxConfirm,
                        onCheckedChange = onToggleBiometricTxConfirm,
                        switchTestTag = "settings_biometric_tx_confirm_switch",
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Wallet Balance Lock Toggle (DataStore WALLET_BIOMETRIC_LOCK)
                    SettingsToggleRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "Biometric Wallet Vault Lock",
                        subtitle = "Protect INR & bullion wallet balances with Fingerprint / Face ID",
                        checked = walletBiometricLock,
                        enabled = biometricCapability.isUsable || walletBiometricLock,
                        onCheckedChange = onToggleWalletBiometricLock,
                        switchTestTag = "settings_wallet_biometric_switch",
                    )

                    if (biometricCapability == BiometricCapability.NOT_ENROLLED) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No biometrics enrolled on this device. Enroll Fingerprint or Face Unlock in Android Settings to activate hardware prompts.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberOrange,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "DataStore State: App Access=${if (biometricAppAccess) "ON" else "OFF"} • Tx Confirm=${if (biometricTxConfirm) "ON" else "OFF"} • Wallet=${if (walletBiometricLock) "ON" else "OFF"}",
                        style = MonoSmallStyle,
                        color = LightGold,
                        modifier = Modifier.testTag("settings_datastore_status_text"),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GoldOutlinedButton(
                        text = "Open Biometric Security Shield",
                        icon = Icons.Default.Fingerprint,
                        onClick = onOpenBiometricAuth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_biometric_screen_btn"),
                    )
                }
            }

            // Gold Price Drop Monitor Service (Threshold set in Settings -> Push Notification + In-App Alert)
            item {
                GoldPriceDropMonitorSettingsCard(
                    rates = rates,
                    firestoreGoldSummary = firestoreGoldSummary,
                    bgDropMonitor = bgDropMonitor,
                    onConfigure = onConfigureGoldMonitor,
                    onRunCheckNow = onRunGoldMonitorCheckNow,
                )
            }

            // Notification & Alert Preferences
            item {
                LuxuryCard {
                    Text("Notification & Alert Preferences", style = MaterialTheme.typography.titleMedium, color = LightGold)
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsToggleRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Notification Sound",
                        subtitle = "Play chime for trade confirmations & price alerts",
                        checked = notifSound,
                        onCheckedChange = { onUpdateNotificationPrefs(it, notifVibration, notifAlerts, notifAnnouncements) },
                        switchTestTag = "settings_notif_sound_switch",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        icon = Icons.Default.Vibration,
                        title = "Haptic Vibration",
                        subtitle = "Vibrate on biometric prompt & alert triggers",
                        checked = notifVibration,
                        onCheckedChange = { onUpdateNotificationPrefs(notifSound, it, notifAlerts, notifAnnouncements) },
                        switchTestTag = "settings_notif_vibration_switch",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        icon = Icons.Default.AddAlert,
                        title = "Bullion Price Alerts",
                        subtitle = "Evaluate 24K, 22K, 18K & 999 Silver target notifications",
                        checked = notifAlerts,
                        onCheckedChange = { onUpdateNotificationPrefs(notifSound, notifVibration, it, notifAnnouncements) },
                        switchTestTag = "settings_notif_alerts_switch",
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        icon = Icons.Default.Campaign,
                        title = "Market & Platform Announcements",
                        subtitle = "Receive compliance and rate-window notices",
                        checked = notifAnnouncements,
                        onCheckedChange = { onUpdateNotificationPrefs(notifSound, notifVibration, notifAlerts, it) },
                        switchTestTag = "settings_notif_announcements_switch",
                    )
                }
            }

            // Vault & Security Actions
            item {
                LuxuryCard {
                    Text("Vault Controls", style = MaterialTheme.typography.titleMedium, color = LightGold)
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingsNavigationRow(
                        icon = Icons.Default.Lock,
                        title = if (profile?.mpinHash.isNullOrBlank()) "Set Up 4-Digit MPIN" else "Change 4-Digit MPIN",
                        subtitle = "Hardware-independent backup PIN for trading & recovery",
                        onClick = onChangeMpin,
                    )
                    SettingsNavigationRow(
                        icon = Icons.Default.AddAlert,
                        title = "Price Alerts & Background Monitor",
                        subtitle = "Configure target price alerts & WorkManager drop monitor",
                        onClick = onOpenPriceAlerts,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GoldOutlinedButton(
                        text = "Lock App Vault Now",
                        onClick = onLockVaultNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_lock_vault_btn"),
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureWhite,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTestTag),
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = ObsidianCard,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = PureWhite, fontWeight = FontWeight.Medium)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SecondarySilver)
        }
    }
}

@Composable
private fun GoldPriceDropMonitorSettingsCard(
    rates: LiveMarketRates,
    firestoreGoldSummary: FirestoreGoldSummary,
    bgDropMonitor: BgDropMonitorState,
    onConfigure: (enabled: Boolean, thresholdInr: Double, purity: String) -> Unit,
    onRunCheckNow: () -> Unit,
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(BullionNotificationHelper.hasNotificationPermission(context))
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasNotifPermission = granted || BullionNotificationHelper.hasNotificationPermission(context)
    }

    var selectedPurity by remember(bgDropMonitor.purity) {
        mutableStateOf(bgDropMonitor.purity.ifBlank { "GOLD_24K" })
    }
    val currentSpotForPurity = GoldPriceMonitorService.resolveMonitoredGoldRate(
        purity = selectedPurity,
        liveRates = rates,
        firestoreSummary = firestoreGoldSummary,
    )
    var thresholdText by remember(bgDropMonitor.threshold24kInr, selectedPurity, currentSpotForPurity) {
        val seed = when {
            bgDropMonitor.threshold24kInr > 0.0 && selectedPurity == bgDropMonitor.purity -> bgDropMonitor.threshold24kInr
            currentSpotForPurity > 0.0 -> currentSpotForPurity * 0.99
            else -> 0.0
        }
        mutableStateOf(if (seed > 0.0) String.format(java.util.Locale.US, "%.2f", seed) else "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    LuxuryCard(
        highlightGold = bgDropMonitor.enabled,
        modifier = Modifier.testTag("settings_gold_monitor_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.TrendingDown, contentDescription = null, tint = RoyalGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_gold_monitor_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = LightGold,
                    )
                    Text(
                        text = stringResource(R.string.settings_gold_monitor_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }
            Switch(
                checked = bgDropMonitor.enabled,
                onCheckedChange = { wantEnabled ->
                    if (wantEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val parsed = thresholdText.toDoubleOrNull()
                    if (wantEnabled && (parsed == null || parsed <= 0.0)) {
                        validationError = "Enter a valid positive gold threshold in ₹/g first."
                    } else {
                        validationError = null
                        onConfigure(wantEnabled, parsed ?: bgDropMonitor.threshold24kInr, selectedPurity)
                    }
                },
                modifier = Modifier.testTag("settings_gold_monitor_switch"),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Monitored Gold Purity Selector
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("GOLD_24K" to "24K Gold", "GOLD_22K" to "22K Gold", "GOLD_18K" to "18K Gold").forEach { (key, label) ->
                FilterChip(
                    selected = selectedPurity == key,
                    onClick = {
                        selectedPurity = key
                        validationError = null
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalGold,
                        selectedLabelColor = DeepBlack,
                    ),
                    modifier = Modifier.testTag("settings_gold_monitor_purity_${key.lowercase()}"),
                )
            }
        }

        if (currentSpotForPurity > 0.0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Current ${GoldPriceMonitorService.purityLabel(selectedPurity)} rate: ${Formatters.formatInr(currentSpotForPurity)}/g",
                style = MonoSmallStyle,
                color = LightGold,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (currentSpotForPurity > 0.0) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(-0.5 to "-0.5%", -1.0 to "-1% Dip", -2.0 to "-2% Dip", 0.5 to "+0.5% Test").forEach { (pct, chipLabel) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val preset = currentSpotForPurity * (1.0 + pct / 100.0)
                            thresholdText = String.format(java.util.Locale.US, "%.2f", preset)
                            validationError = null
                        },
                        label = { Text(chipLabel) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = thresholdText,
            onValueChange = {
                thresholdText = it
                validationError = null
            },
            label = { Text(stringResource(R.string.settings_gold_monitor_threshold_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_gold_monitor_threshold_input"),
        )

        validationError?.takeIf { it.isNotBlank() }?.let { errText ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = ObsidianCard,
                border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "System push notifications are currently disabled. In-app drop alerts will still trigger immediately, or grant permission for lock-screen push alerts.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                    GoldOutlinedButton(
                        text = "Allow Push Notifications",
                        onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_grant_push_btn"),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Monitor Status: ${bgDropMonitor.lastStatus}",
                    style = MonoSmallStyle,
                    color = if (bgDropMonitor.enabled) SuccessGreen else SecondarySilver,
                    modifier = Modifier.testTag("settings_gold_monitor_status"),
                )
                if (bgDropMonitor.lastCheckedEpochMs > 0L) {
                    Text(
                        text = "Checked ${Formatters.formatEpoch(bgDropMonitor.lastCheckedEpochMs)} • ${Formatters.formatInr(bgDropMonitor.lastObservedRateInr)}/g",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }
            StatusPill(
                text = if (bgDropMonitor.enabled) "MONITORING" else "PAUSED",
                color = if (bgDropMonitor.enabled) SuccessGreen else SecondarySilver,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GoldPrimaryButton(
                text = if (bgDropMonitor.enabled) "Update Threshold" else stringResource(R.string.settings_gold_monitor_save),
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        validationError = "Enter a valid positive threshold rate in ₹/g."
                    } else {
                        validationError = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onConfigure(true, parsed, selectedPurity)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_gold_monitor_save_btn"),
            )
            GoldOutlinedButton(
                text = stringResource(R.string.settings_gold_monitor_check_now),
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0 && !bgDropMonitor.enabled) {
                        onConfigure(true, parsed, selectedPurity)
                    }
                    onRunCheckNow()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_gold_monitor_check_btn"),
            )
        }
    }
}

