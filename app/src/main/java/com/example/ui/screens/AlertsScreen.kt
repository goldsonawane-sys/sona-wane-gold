package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.R
import com.example.data.local.BgDropMonitorState
import com.example.data.local.PriceAlertWorkerConfig
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.PriceAlert
import com.example.data.model.UserProfile
import com.example.service.GoldPriceMonitorService
import com.example.service.PriceAlertManager
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
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
import com.example.ui.theme.SilverAccent
import com.example.ui.theme.SuccessGreen
import com.example.util.BullionNotificationHelper
import com.example.util.Formatters
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import kotlin.math.abs

private val ALERT_METALS = listOf(
    "GOLD_24K" to "24K Gold",
    "GOLD_22K" to "22K Gold",
    "GOLD_18K" to "18K Gold",
    "SILVER_999" to "999 Silver",
)

private val ALERT_FREQUENCIES = listOf(
    "ONCE" to "Once",
    "EVERY_HOUR" to "Hourly",
    "DAILY" to "Daily",
    "ALWAYS" to "Always",
)

private val WORKER_INTERVALS = listOf(
    15L to "15m",
    30L to "30m",
    60L to "1h",
    360L to "6h",
)

/**
 * Dedicated Alerts & WorkManager Threshold Settings Screen.
 * Allows investors to manage background WorkManager polling frequency, feed preference,
 * notification channels, 24K/22K/18K gold dip monitors, and per-metal price thresholds.
 */
@Composable
fun AlertsScreen(
    profile: UserProfile?,
    rates: LiveMarketRates,
    firestoreGoldSummary: FirestoreGoldSummary = FirestoreGoldSummary(),
    alerts: List<PriceAlert>,
    bgDropMonitor: BgDropMonitorState = BgDropMonitorState(),
    workerConfig: PriceAlertWorkerConfig = PriceAlertWorkerConfig(),
    onBack: () -> Unit,
    onRefreshRates: () -> Unit,
    onCheckAlertsNow: () -> Unit,
    onUpdateWorkerConfig: (intervalMinutes: Long, preferFirestoreRates: Boolean, masterEnabled: Boolean) -> Unit,
    onUpdateSoundVibration: (sound: Boolean, vibration: Boolean) -> Unit = { _, _ -> },
    onConfigureBgDropMonitor: (enabled: Boolean, thresholdInr: Double, purity: String) -> Unit = { _, _, _ -> },
    onRunBgDropCheckNow: () -> Unit = {},
    onCreateAlert: (metal: String, target: Double, direction: String, frequency: String, notes: String) -> Unit,
    onUpdateAlert: (alertId: String, target: Double, direction: String, frequency: String, isActive: Boolean, notes: String) -> Unit,
    onToggleAlert: (PriceAlert, Boolean) -> Unit,
    onDeleteAlert: (String) -> Unit,
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(BullionNotificationHelper.hasNotificationPermission(context))
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotifPermission = granted },
    )

    val periodicWorkFlow = remember(context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .getWorkInfosForUniqueWorkFlow(PriceAlertManager.UNIQUE_PERIODIC_WORK_NAME)
        }.getOrElse { flowOf(emptyList()) }
    }
    val periodicWorkInfos by periodicWorkFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val workerStateLabel = remember(periodicWorkInfos, workerConfig.masterAlertsEnabled, alerts) {
        val activeState = periodicWorkInfos.firstOrNull()?.state
        when {
            !workerConfig.masterAlertsEnabled -> "PAUSED"
            activeState == WorkInfo.State.RUNNING -> "RUNNING"
            activeState == WorkInfo.State.ENQUEUED -> "ARMED"
            alerts.any { it.isActive } -> "SCHEDULED"
            else -> "IDLE"
        }
    }

    val spotByMetal = remember(rates, firestoreGoldSummary) {
        val r24 = rates.gold24kBuyPerGram.takeIf { it > 0.0 } ?: firestoreGoldSummary.gold24kPerGram
        val r22 = rates.gold22kBuyPerGram.takeIf { it > 0.0 } ?: firestoreGoldSummary.gold22kPerGram
        val r18 = rates.gold18kBuyPerGram.takeIf { it > 0.0 } ?: firestoreGoldSummary.gold18kPerGram
        mapOf(
            "GOLD_24K" to r24,
            "GOLD_22K" to r22,
            "GOLD_18K" to r18,
            "SILVER_999" to rates.silver999BuyPerGram,
        )
    }

    var selectedMetal by remember { mutableStateOf("GOLD_24K") }
    val activeSpot = spotByMetal[selectedMetal] ?: 0.0
    var targetPriceText by remember(selectedMetal, activeSpot) {
        mutableStateOf(if (activeSpot > 0.0) String.format(Locale.US, "%.2f", activeSpot * 0.99) else "")
    }
    var selectedDirection by remember { mutableStateOf("BELOW") }
    var selectedFrequency by remember { mutableStateOf("ONCE") }
    var notesInput by remember { mutableStateOf("") }
    var inlineFormError by remember { mutableStateOf<String?>(null) }

    var statusTab by remember { mutableIntStateOf(0) }
    var metalFilter by remember { mutableStateOf("ALL") }
    var editingAlert by remember { mutableStateOf<PriceAlert?>(null) }

    val filteredAlerts = remember(alerts, statusTab, metalFilter) {
        alerts.filter { alert ->
            val tabOk = when (statusTab) {
                1 -> alert.isActive
                2 -> !alert.isActive || alert.triggerCount > 0
                else -> true
            }
            val metalOk = metalFilter == "ALL" || alert.metal == metalFilter
            tabOk && metalOk
        }
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = stringResource(R.string.alerts_screen_title),
                subtitle = "${alerts.count { it.isActive }} armed • ${workerConfig.intervalMinutes}m worker • Push ${if (hasNotifPermission && workerConfig.masterAlertsEnabled) "ON" else "OFF"}",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onRefreshRates,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("alerts_refresh_rates_btn"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh live bullion rates", tint = RoyalGold)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("alerts_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Surface(
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, AmberOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("alerts_permission_banner"),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = AmberOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Allow Notification Permission", style = MaterialTheme.typography.titleSmall, color = LightGold)
                            }
                            Text(
                                "WorkManager threshold alerts require Android notification permission so price triggers appear on your lock screen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PureWhite,
                            )
                            GoldPrimaryButton(
                                text = "Enable Notifications",
                                onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier.fillMaxWidth().testTag("alerts_grant_notif_btn"),
                            )
                        }
                    }
                }
            }

            // 1. WorkManager Notification Worker Control Card
            item {
                WorkManagerSettingsCard(
                    profile = profile,
                    workerConfig = workerConfig,
                    workerStateLabel = workerStateLabel,
                    onUpdateWorkerConfig = onUpdateWorkerConfig,
                    onUpdateSoundVibration = onUpdateSoundVibration,
                    onRunCheckNow = onCheckAlertsNow,
                )
            }

            // 2. Quick Gold Dip Monitor (GoldPriceMonitorWorker)
            item {
                QuickGoldDipWorkerCard(
                    rates = rates,
                    firestoreGoldSummary = firestoreGoldSummary,
                    bgDropMonitor = bgDropMonitor,
                    onConfigure = onConfigureBgDropMonitor,
                    onRunNow = onRunBgDropCheckNow,
                )
            }

            // 3A. Price Threshold Composer Step 1 (Metal & Target Price)
            item {
                LuxuryCard(highlightGold = true, modifier = Modifier.testTag("alerts_composer_card")) {
                    Text("🎯 1. Select Bullion & Target Price", style = MaterialTheme.typography.titleMedium, color = LightGold)
                    Text(
                        text = "Checked automatically by WorkManager against Firestore & live feeds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2x2 Purity Grid (avoids FlowRow crashes)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ALERT_METALS.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEach { (key, label) ->
                                    val selected = selectedMetal == key
                                    val spot = spotByMetal[key] ?: 0.0
                                    val accent = if (key == "SILVER_999") SilverAccent else LightGold
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) CharcoalBlack else ObsidianCard,
                                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) RoyalGold else GoldBorderSubtle),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedMetal = key
                                                inlineFormError = null
                                            }
                                            .testTag("alerts_metal_$key"),
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) RoyalGold else SecondarySilver, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (spot > 0.0) "${Formatters.formatInr(spot)}/g" else "--",
                                                style = MonoSmallStyle,
                                                color = accent,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    if (activeSpot > 0.0) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(-3.0 to "-3%", -2.0 to "-2%", -1.0 to "-1%", 0.0 to "Spot", 1.0 to "+1%", 2.0 to "+2%").forEach { (pct, chipLabel) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        targetPriceText = String.format(Locale.US, "%.2f", activeSpot * (1.0 + pct / 100.0))
                                        if (pct < 0) selectedDirection = "BELOW" else if (pct > 0) selectedDirection = "ABOVE"
                                        inlineFormError = null
                                    },
                                    label = { Text(chipLabel) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    OutlinedTextField(
                        value = targetPriceText,
                        onValueChange = {
                            targetPriceText = it
                            inlineFormError = null
                        },
                        label = { Text("Target Price (₹ / gram) *") },
                        prefix = { Text("₹ ", color = LightGold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alerts_target_price_input"),
                    )
                }
            }

            // 3B. Price Threshold Composer Step 2 (Condition, Cooldown, Note & Arm CTA)
            item {
                LuxuryCard(highlightGold = true) {
                    Text("Condition", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("BELOW" to "Drops Below", "ABOVE" to "Rises Above", "CROSSES" to "Crosses").forEach { (code, label) ->
                            FilterChip(
                                selected = selectedDirection == code,
                                onClick = { selectedDirection = code },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                ),
                                modifier = Modifier.testTag("alerts_dir_$code"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Notification Cooldown", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ALERT_FREQUENCIES.forEach { (code, label) ->
                            FilterChip(
                                selected = selectedFrequency == code,
                                onClick = { selectedFrequency = code },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                ),
                                modifier = Modifier.testTag("alerts_freq_$code"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { if (it.length <= 120) notesInput = it },
                        label = { Text("Alert Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("alerts_note_input"),
                    )

                    inlineFormError?.takeIf { it.isNotBlank() }?.let { errText ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    GoldPrimaryButton(
                        text = "Arm ${ALERT_METALS.first { it.first == selectedMetal }.second} Threshold",
                        icon = Icons.Default.AddAlert,
                        modifier = Modifier.fillMaxWidth().testTag("alerts_create_threshold_btn"),
                        onClick = {
                            val parsed = targetPriceText.toDoubleOrNull()
                            if (parsed == null || parsed <= 0.0) {
                                inlineFormError = "Please enter a valid positive target price in ₹/g."
                            } else {
                                inlineFormError = null
                                onCreateAlert(
                                    selectedMetal,
                                    parsed,
                                    selectedDirection,
                                    selectedFrequency,
                                    notesInput.trim(),
                                )
                                notesInput = ""
                            }
                        },
                    )
                }
            }

            // 4. Saved Thresholds Filter & List
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved Price Thresholds (${alerts.size})", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    TabRow(
                        selectedTabIndex = statusTab,
                        containerColor = ObsidianCard,
                        contentColor = RoyalGold,
                    ) {
                        Tab(selected = statusTab == 0, onClick = { statusTab = 0 }, text = { Text("All (${alerts.size})") })
                        Tab(selected = statusTab == 1, onClick = { statusTab = 1 }, text = { Text("Active (${alerts.count { it.isActive }})") })
                        Tab(selected = statusTab == 2, onClick = { statusTab = 2 }, text = { Text("Triggered (${alerts.count { it.triggerCount > 0 }})") })
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("ALL", "GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999").forEach { m ->
                            FilterChip(
                                selected = metalFilter == m,
                                onClick = { metalFilter = m },
                                label = { Text(if (m == "ALL") "All" else m.replace("_", " ")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                ),
                            )
                        }
                    }
                }
            }

            if (filteredAlerts.isEmpty()) {
                item {
                    LuxuryCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(32.dp))
                            Text("No price thresholds configured", style = MaterialTheme.typography.titleSmall, color = PureWhite)
                            Text(
                                "Use Add Bullion Price Threshold above so WorkManager notifies you automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondarySilver,
                            )
                        }
                    }
                }
            } else {
                items(filteredAlerts, key = { it.id }) { alert ->
                    val currentSpot = spotByMetal[alert.metal] ?: 0.0
                    val diffPct = if (currentSpot > 0.0) ((alert.targetPriceInr - currentSpot) / currentSpot) * 100.0 else 0.0
                    val dirLabel = when (alert.direction) {
                        "ABOVE" -> "Rises Above"
                        "BELOW" -> "Drops Below"
                        else -> "Crosses"
                    }

                    LuxuryCard(highlightGold = alert.isActive, modifier = Modifier.testTag("alerts_item_${alert.id}")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = alert.metal.replace("_", " "),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (alert.metal == "SILVER_999") SilverAccent else LightGold,
                                    )
                                    StatusPill(
                                        text = alert.frequency.replace("_", " "),
                                        color = if (alert.isActive) RoyalGold else SecondarySilver,
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "$dirLabel ${Formatters.formatInr(alert.targetPriceInr)}/g",
                                    style = MonoSmallStyle,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (currentSpot > 0.0) {
                                    val sign = if (diffPct >= 0) "+" else ""
                                    Text(
                                        text = "Spot: ${Formatters.formatInr(currentSpot)}/g ($sign${String.format(Locale.US, "%.2f", diffPct)}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondarySilver,
                                    )
                                }
                            }
                            Switch(
                                checked = alert.isActive,
                                onCheckedChange = { onToggleAlert(alert, it) },
                                modifier = Modifier.testTag("alerts_toggle_${alert.id}"),
                            )
                        }
                        if (alert.notes.isNotBlank() || alert.triggerCount > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = GoldBorderSubtle)
                            Spacer(modifier = Modifier.height(6.dp))
                            if (alert.notes.isNotBlank()) {
                                Text(alert.notes, style = MaterialTheme.typography.bodySmall, color = LightGold)
                            }
                            if (alert.triggerCount > 0) {
                                Text(
                                    text = "Triggered ${alert.triggerCount}x • Last: ${Formatters.formatInr(alert.lastTriggeredPrice)}/g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = { editingAlert = alert },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("alerts_edit_${alert.id}"),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit threshold", modifier = Modifier.size(16.dp), tint = RoyalGold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", color = RoyalGold)
                            }
                            TextButton(
                                onClick = { onDeleteAlert(alert.id) },
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("alerts_delete_${alert.id}"),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete threshold", modifier = Modifier.size(16.dp), tint = ErrorRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", color = ErrorRed)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    editingAlert?.let { current ->
        AlertThresholdEditorDialog(
            initialAlert = current,
            onDismiss = { editingAlert = null },
            onSave = { target, dir, freq, active, notes ->
                onUpdateAlert(current.id, target, dir, freq, active, notes)
                editingAlert = null
            },
        )
    }
}

@Composable
private fun WorkManagerSettingsCard(
    profile: UserProfile?,
    workerConfig: PriceAlertWorkerConfig,
    workerStateLabel: String,
    onUpdateWorkerConfig: (intervalMinutes: Long, preferFirestoreRates: Boolean, masterEnabled: Boolean) -> Unit,
    onUpdateSoundVibration: (sound: Boolean, vibration: Boolean) -> Unit,
    onRunCheckNow: () -> Unit,
) {
    val soundEnabled = profile?.notifSoundEnabled ?: true
    val vibrationEnabled = profile?.notifVibrationEnabled ?: true

    LuxuryCard(highlightGold = workerConfig.masterAlertsEnabled, modifier = Modifier.testTag("alerts_worker_settings_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = RoyalGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.alerts_worker_section_title), style = MaterialTheme.typography.titleMedium, color = LightGold)
                    Text(stringResource(R.string.alerts_worker_section_subtitle), style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                }
            }
            Switch(
                checked = workerConfig.masterAlertsEnabled,
                onCheckedChange = { enabled ->
                    onUpdateWorkerConfig(workerConfig.intervalMinutes, workerConfig.preferFirestoreRates, enabled)
                },
                modifier = Modifier.testTag("alerts_master_switch"),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Background Worker Schedule", style = MaterialTheme.typography.labelLarge, color = PureWhite)
            StatusPill(
                text = workerStateLabel,
                color = if (workerConfig.masterAlertsEnabled) SuccessGreen else SecondarySilver,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WORKER_INTERVALS.forEach { (mins, label) ->
                FilterChip(
                    selected = workerConfig.intervalMinutes == mins,
                    onClick = {
                        onUpdateWorkerConfig(mins, workerConfig.preferFirestoreRates, workerConfig.masterAlertsEnabled)
                    },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalGold,
                        selectedLabelColor = DeepBlack,
                    ),
                    modifier = Modifier.weight(1f).testTag("alerts_interval_$mins"),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Prefer Firestore Spot Feed", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                Text("Reads Firestore market_price_history first, falls back to Live API", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            }
            Switch(
                checked = workerConfig.preferFirestoreRates,
                onCheckedChange = { preferFs ->
                    onUpdateWorkerConfig(workerConfig.intervalMinutes, preferFs, workerConfig.masterAlertsEnabled)
                },
                modifier = Modifier.testTag("alerts_prefer_firestore_switch"),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alert Sound", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
            Switch(
                checked = soundEnabled,
                onCheckedChange = { onUpdateSoundVibration(it, vibrationEnabled) },
                modifier = Modifier.testTag("alerts_sound_switch"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Vibration", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = { onUpdateSoundVibration(soundEnabled, it) },
                modifier = Modifier.testTag("alerts_vibration_switch"),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        GoldOutlinedButton(
            text = "Run Worker Check Now",
            onClick = onRunCheckNow,
            modifier = Modifier.fillMaxWidth().testTag("alerts_run_worker_now_btn"),
        )
    }
}

@Composable
private fun QuickGoldDipWorkerCard(
    rates: LiveMarketRates,
    firestoreGoldSummary: FirestoreGoldSummary,
    bgDropMonitor: BgDropMonitorState,
    onConfigure: (enabled: Boolean, thresholdInr: Double, purity: String) -> Unit,
    onRunNow: () -> Unit,
) {
    var purity by remember(bgDropMonitor.purity) { mutableStateOf(bgDropMonitor.purity.ifBlank { "GOLD_24K" }) }
    val spot = GoldPriceMonitorService.resolveMonitoredGoldRate(purity, rates, firestoreGoldSummary)
    var thresholdText by remember(bgDropMonitor.threshold24kInr, purity, spot) {
        val seed = when {
            bgDropMonitor.threshold24kInr > 0.0 && purity == bgDropMonitor.purity -> bgDropMonitor.threshold24kInr
            spot > 0.0 -> spot * 0.99
            else -> 0.0
        }
        mutableStateOf(if (seed > 0.0) String.format(Locale.US, "%.2f", seed) else "")
    }
    var err by remember { mutableStateOf<String?>(null) }

    LuxuryCard(modifier = Modifier.testTag("alerts_bg_drop_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("⚡ Quick Gold Dip Monitor", style = MaterialTheme.typography.titleMedium, color = LightGold)
                Text(
                    text = "Background GoldPriceMonitorWorker alert if spot drops below threshold",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )
            }
            Switch(
                checked = bgDropMonitor.enabled,
                onCheckedChange = { want ->
                    val parsed = thresholdText.toDoubleOrNull()
                    if (want && (parsed == null || parsed <= 0.0)) {
                        err = "Enter a valid positive ₹/g threshold."
                    } else {
                        err = null
                        onConfigure(want, parsed ?: bgDropMonitor.threshold24kInr, purity)
                    }
                },
                modifier = Modifier.testTag("alerts_bg_drop_switch"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("GOLD_24K" to "24K", "GOLD_22K" to "22K", "GOLD_18K" to "18K").forEach { (key, label) ->
                FilterChip(
                    selected = purity == key,
                    onClick = { purity = key; err = null },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RoyalGold, selectedLabelColor = DeepBlack),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it; err = null },
            label = { Text("Notify if ${GoldPriceMonitorService.purityLabel(purity)} drops below (₹/g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("alerts_bg_drop_threshold_input"),
        )
        err?.takeIf { it.isNotBlank() }?.let { errText ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GoldPrimaryButton(
                text = if (bgDropMonitor.enabled) "Save Threshold" else "Start Dip Monitor",
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        err = "Enter a valid positive ₹/g threshold."
                    } else {
                        err = null
                        onConfigure(true, parsed, purity)
                    }
                },
                modifier = Modifier.weight(1f).testTag("alerts_bg_drop_save_btn"),
            )
            GoldOutlinedButton(
                text = "Check Dip",
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) onConfigure(true, parsed, purity)
                    onRunNow()
                },
                modifier = Modifier.weight(1f).testTag("alerts_bg_drop_run_btn"),
            )
        }
    }
}

@Composable
private fun AlertThresholdEditorDialog(
    initialAlert: PriceAlert,
    onDismiss: () -> Unit,
    onSave: (target: Double, direction: String, frequency: String, isActive: Boolean, notes: String) -> Unit,
) {
    var targetText by remember(initialAlert) { mutableStateOf(String.format(Locale.US, "%.2f", initialAlert.targetPriceInr)) }
    var direction by remember(initialAlert) { mutableStateOf(initialAlert.direction) }
    var frequency by remember(initialAlert) { mutableStateOf(initialAlert.frequency) }
    var isActive by remember(initialAlert) { mutableStateOf(initialAlert.isActive) }
    var notes by remember(initialAlert) { mutableStateOf(initialAlert.notes) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Edit ${initialAlert.metal.replace("_", " ")} Threshold", color = LightGold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it; errMsg = null },
                    label = { Text("Target Price (₹/g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("alerts_edit_dialog_price_input"),
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BELOW" to "Below", "ABOVE" to "Above", "CROSSES" to "Crosses").forEach { (code, label) ->
                        FilterChip(selected = direction == code, onClick = { direction = code }, label = { Text(label) })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ALERT_FREQUENCIES.forEach { (code, label) ->
                        FilterChip(selected = frequency == code, onClick = { frequency = code }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 120) notes = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Armed & Active", color = PureWhite)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
                errMsg?.takeIf { it.isNotBlank() }?.let { errText ->
                    Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = targetText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        errMsg = "Enter a valid positive ₹/g price."
                    } else {
                        onSave(parsed, direction, frequency, isActive, notes.trim())
                    }
                },
                modifier = Modifier.testTag("alerts_edit_dialog_save_btn"),
            ) {
                Text("Save", color = RoyalGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SecondarySilver) }
        },
    )
}
