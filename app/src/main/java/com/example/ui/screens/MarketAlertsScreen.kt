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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.BgDropMonitorState
import com.example.data.model.LiveMarketRates
import com.example.data.model.PriceAlert
import com.example.data.model.UserProfile
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
import kotlin.math.abs

private val SUPPORTED_ALERT_METALS = listOf(
    "GOLD_24K" to "24K Gold",
    "GOLD_22K" to "22K Gold",
    "GOLD_18K" to "18K Gold",
    "SILVER_999" to "999 Silver",
)

private val ALERT_FREQUENCY_OPTIONS = listOf(
    "ONCE" to "Once",
    "EVERY_HOUR" to "Every Hour",
    "DAILY" to "Daily",
    "ALWAYS" to "Always",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarketAlertsScreen(
    profile: UserProfile?,
    rates: LiveMarketRates,
    alerts: List<PriceAlert>,
    bgDropMonitor: BgDropMonitorState = BgDropMonitorState(),
    onBack: () -> Unit,
    onRefreshRates: () -> Unit,
    onCheckAlertsNow: () -> Unit,
    onConfigureBgDropMonitor: (enabled: Boolean, threshold24kInr: Double) -> Unit = { _, _ -> },
    onRunBgDropCheckNow: () -> Unit = {},
    onCreateAlert: (metal: String, target: Double, direction: String, frequency: String, notes: String) -> Unit,
    onUpdateAlert: (alertId: String, target: Double, direction: String, frequency: String, isActive: Boolean, notes: String) -> Unit,
    onToggleAlert: (PriceAlert, Boolean) -> Unit,
    onDeleteAlert: (String) -> Unit,
    onEnablePriceAlertPrefs: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(BullionNotificationHelper.hasNotificationPermission(context))
    }
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotifPermission = granted },
    )

    // Inline Set-Alert Form state
    var selectedMetal by remember { mutableStateOf("GOLD_24K") }
    val spotByMetal = remember(rates) {
        mapOf(
            "GOLD_24K" to rates.gold24kBuyPerGram,
            "GOLD_22K" to rates.gold22kBuyPerGram,
            "GOLD_18K" to rates.gold18kBuyPerGram,
            "SILVER_999" to rates.silver999BuyPerGram,
        )
    }
    val activeSpot = spotByMetal[selectedMetal] ?: 0.0
    var targetPriceText by remember(selectedMetal, activeSpot) {
        mutableStateOf(if (activeSpot > 0.0) "%.2f".format(activeSpot * 0.99) else "")
    }
    var selectedDirection by remember { mutableStateOf("BELOW") }
    var selectedFrequency by remember { mutableStateOf("ONCE") }
    var notesInput by remember { mutableStateOf("") }
    var inlineFormError by remember { mutableStateOf<String?>(null) }

    // Management Filter & Edit states
    var statusTab by remember { mutableIntStateOf(0) } // 0: All, 1: Active, 2: Triggered
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
                title = "Price Alerts & Targets",
                subtitle = "${alerts.count { it.isActive }} armed • Push ${if (hasNotifPermission) "enabled" else "disabled"}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefreshRates) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh live rates", tint = RoyalGold)
                    }
                },
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
            if (!hasNotifPermission || profile?.notifPriceAlerts == false) {
                item {
                    Surface(
                        color = AmberOrange.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AmberOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = AmberOrange)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Push Alerts Require Activation", style = MaterialTheme.typography.titleSmall, color = LightGold)
                            }
                            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Text(
                                    "Grant Android notification permission so target-hit alerts appear immediately on your device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureWhite,
                                )
                                GoldPrimaryButton(
                                    text = "Allow Push Notifications",
                                    onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                    modifier = Modifier.fillMaxWidth().testTag("grant_notif_perm_btn"),
                                )
                            }
                            if (profile?.notifPriceAlerts == false) {
                                Text(
                                    "Price Alerts are currently muted in your account notification preferences.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureWhite,
                                )
                                GoldOutlinedButton(
                                    text = "Re-enable Price Alerts",
                                    onClick = { onEnablePriceAlertPrefs(true) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            // Live Spot Prices Reference Strip (tap any metal to prefill composer)
            item {
                LuxuryCard(highlightGold = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Live Bullion Spot Rates", style = MaterialTheme.typography.titleMedium, color = LightGold)
                        StatusPill(
                            text = if (rates.isLive) "LIVE" else "CACHED",
                            color = if (rates.isLive) SuccessGreen else AmberOrange,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SUPPORTED_ALERT_METALS.forEach { (key, label) ->
                            val price = spotByMetal[key] ?: 0.0
                            val isSelected = selectedMetal == key
                            val accent = if (key == "SILVER_999") SilverAccent else LightGold
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) RoyalGold.copy(alpha = 0.16f) else ObsidianCard,
                                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) RoyalGold else GoldBorderSubtle),
                                modifier = Modifier
                                    .clickable {
                                        selectedMetal = key
                                        inlineFormError = null
                                    }
                                    .testTag("spot_chip_$key"),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (isSelected) LightGold else SecondarySilver)
                                    Text(
                                        text = if (price > 0.0) "${Formatters.formatInr(price)}/g" else "--",
                                        style = MonoSmallStyle,
                                        color = accent,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dedicated On-Screen Target Price & Alert Frequency Composer
            item {
                LuxuryCard(highlightGold = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Set Price Alert Target", style = MaterialTheme.typography.titleMedium, color = LightGold)
                            Text(
                                text = "Checks Firestore & live Gold/Silver targets via WorkManager (15m checks) and pushes alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondarySilver,
                            )
                        }
                        GoldOutlinedButton(
                            text = "Check Now",
                            onClick = onCheckAlertsNow,
                            modifier = Modifier.testTag("check_alerts_now_btn"),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("1. Bullion Purity", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SUPPORTED_ALERT_METALS.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedMetal == key,
                                onClick = {
                                    selectedMetal = key
                                    inlineFormError = null
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                ),
                                modifier = Modifier.testTag("alert_metal_$key"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("2. Enter Target Price (₹ / gram)", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    if (activeSpot > 0.0) {
                        Text(
                            text = "Current ${SUPPORTED_ALERT_METALS.first { it.first == selectedMetal }.second} spot: ${Formatters.formatInr(activeSpot)}/g",
                            style = MonoSmallStyle,
                            color = SecondarySilver,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(-3.0 to "-3%", -2.0 to "-2%", -1.0 to "-1%", 0.0 to "Spot", 1.0 to "+1%", 2.0 to "+2%", 3.0 to "+3%").forEach { (pct, label) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        targetPriceText = "%.2f".format(activeSpot * (1.0 + pct / 100.0))
                                        if (pct < 0) selectedDirection = "BELOW" else if (pct > 0) selectedDirection = "ABOVE"
                                        inlineFormError = null
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val parsedTarget = targetPriceText.toDoubleOrNull()
                    OutlinedTextField(
                        value = targetPriceText,
                        onValueChange = {
                            targetPriceText = it
                            inlineFormError = null
                        },
                        label = { Text("Target Price (₹ / gram) *") },
                        prefix = { Text("₹ ", color = LightGold) },
                        supportingText = {
                            if (parsedTarget != null && parsedTarget > 0.0 && activeSpot > 0.0) {
                                val pctGap = ((parsedTarget - activeSpot) / activeSpot) * 100.0
                                val relation = if (pctGap >= 0) "above live spot" else "below live spot"
                                Text(
                                    text = "${"%.2f".format(abs(pctGap))}% $relation",
                                    color = if (abs(pctGap) <= 0.25) SuccessGreen else LightGold,
                                )
                            } else {
                                Text("Enter your exact target price per gram", color = SecondarySilver)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_target_price_input"),
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("3. Trigger Condition", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("BELOW" to "Drops Below", "ABOVE" to "Rises Above", "CROSSES" to "Crosses Target").forEach { (code, label) ->
                            FilterChip(
                                selected = selectedDirection == code,
                                onClick = { selectedDirection = code },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold.copy(alpha = 0.24f),
                                    selectedLabelColor = LightGold,
                                ),
                                modifier = Modifier.testTag("alert_dir_$code"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("4. Alert Frequency", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ALERT_FREQUENCY_OPTIONS.forEach { (code, label) ->
                            FilterChip(
                                selected = selectedFrequency == code,
                                onClick = { selectedFrequency = code },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                ),
                                modifier = Modifier.testTag("alert_freq_$code"),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = CharcoalBlack,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GoldBorderSubtle),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = frequencyDescription(selectedFrequency),
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { if (it.length <= 150) notesInput = it },
                        label = { Text("Alert Label / Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    inlineFormError?.takeIf { it.isNotBlank() }?.let { errMsg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errMsg, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    GoldPrimaryButton(
                        text = "Arm ${SUPPORTED_ALERT_METALS.first { it.first == selectedMetal }.second} Alert",
                        icon = Icons.Default.AddAlert,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_alert_btn"),
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

            // WorkManager Background 24K Gold Drop Monitor Card
            item {
                BackgroundGoldDropMonitorCard(
                    rates = rates,
                    bgDropMonitor = bgDropMonitor,
                    onConfigure = onConfigureBgDropMonitor,
                    onRunNow = onRunBgDropCheckNow,
                )
            }

            // Alert Management Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manage Saved Price Alerts (${alerts.size})", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    TabRow(
                        selectedTabIndex = statusTab,
                        containerColor = ObsidianCard,
                        contentColor = RoyalGold,
                    ) {
                        Tab(selected = statusTab == 0, onClick = { statusTab = 0 }, text = { Text("All (${alerts.size})") })
                        Tab(selected = statusTab == 1, onClick = { statusTab = 1 }, text = { Text("Active (${alerts.count { it.isActive }})") })
                        Tab(selected = statusTab == 2, onClick = { statusTab = 2 }, text = { Text("Triggered (${alerts.count { it.triggerCount > 0 || !it.isActive }})") })
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("ALL", "GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999").forEach { m ->
                            FilterChip(
                                selected = metalFilter == m,
                                onClick = { metalFilter = m },
                                label = { Text(if (m == "ALL") "All Metals" else m.replace("_", " ")) },
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(36.dp))
                            Text("No matching price alerts", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                            Text(
                                "Use the Set Price Alert card above to arm targets for 24K Gold, 22K Gold, 18K Gold, or 999 Silver.",
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
                        else -> "Crosses Target"
                    }
                    val freqLabel = when (alert.frequency) {
                        "ONCE" -> "Once"
                        "EVERY_HOUR" -> "Every Hour"
                        "DAILY" -> "Daily"
                        else -> "Always"
                    }

                    LuxuryCard(highlightGold = alert.isActive) {
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
                                        text = freqLabel,
                                        color = if (alert.isActive) RoyalGold else SecondarySilver,
                                    )
                                    if (!alert.isActive) {
                                        StatusPill(text = "PAUSED", color = SecondarySilver)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$dirLabel ${Formatters.formatInr(alert.targetPriceInr)} / g",
                                    style = MonoSmallStyle,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (currentSpot > 0.0) {
                                    val sign = if (diffPct >= 0) "+" else ""
                                    Text(
                                        text = "Live spot: ${Formatters.formatInr(currentSpot)}/g (${sign}${"%.2f".format(diffPct)}% away)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (abs(diffPct) < 0.2) SuccessGreen else SecondarySilver,
                                    )
                                }
                            }
                            Switch(
                                checked = alert.isActive,
                                onCheckedChange = { onToggleAlert(alert, it) },
                                modifier = Modifier.testTag("toggle_alert_${alert.id}"),
                            )
                        }

                        if (alert.notes.isNotBlank() || alert.triggerCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = GoldBorderSubtle)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (alert.notes.isNotBlank()) {
                                Text("Note: ${alert.notes}", style = MaterialTheme.typography.bodySmall, color = LightGold)
                            }
                            if (alert.triggerCount > 0) {
                                Text(
                                    text = "Triggered ${alert.triggerCount}x • Last hit: ${Formatters.formatInr(alert.lastTriggeredPrice)}/g • ${Formatters.formatTimestamp(alert.triggeredAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SuccessGreen,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { editingAlert = alert },
                                modifier = Modifier.testTag("edit_alert_${alert.id}"),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = RoyalGold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", color = RoyalGold)
                            }
                            TextButton(
                                onClick = { onDeleteAlert(alert.id) },
                                modifier = Modifier.testTag("delete_alert_${alert.id}"),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = ErrorRed)
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

    editingAlert?.let { currentEditing ->
        AlertEditorDialog(
            initialAlert = currentEditing,
            spotByMetal = spotByMetal,
            onDismiss = { editingAlert = null },
            onSave = { target, direction, frequency, isActive, notes ->
                onUpdateAlert(currentEditing.id, target, direction, frequency, isActive, notes)
                editingAlert = null
            },
        )
    }
}

private fun frequencyDescription(frequency: String): String {
    return when (frequency) {
        "ONCE" -> "Fires once when the target price is met or crossed, then pauses automatically."
        "EVERY_HOUR" -> "Sends at most 1 push alert per hour while the price condition holds."
        "DAILY" -> "Sends a daily summary push when the target condition triggers."
        else -> "Triggers on every spot check cycle (5-minute anti-spam cooldown)."
    }
}

@Composable
private fun BackgroundGoldDropMonitorCard(
    rates: LiveMarketRates,
    bgDropMonitor: BgDropMonitorState,
    onConfigure: (enabled: Boolean, threshold24kInr: Double) -> Unit,
    onRunNow: () -> Unit,
) {
    val live24k = rates.gold24kBuyPerGram
    var thresholdText by remember(bgDropMonitor.threshold24kInr, live24k) {
        val seed = when {
            bgDropMonitor.threshold24kInr > 0.0 -> bgDropMonitor.threshold24kInr
            live24k > 0.0 -> live24k * 0.99
            else -> 0.0
        }
        mutableStateOf(if (seed > 0.0) "%.2f".format(seed) else "")
    }
    var validationErr by remember { mutableStateOf<String?>(null) }

    LuxuryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Background Gold Drop Monitor", style = MaterialTheme.typography.titleMedium, color = LightGold)
                Text(
                    text = "WorkManager periodic 24K gold watcher (15m interval)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )
            }
            Switch(
                checked = bgDropMonitor.enabled,
                onCheckedChange = { wantEnabled ->
                    val parsed = thresholdText.toDoubleOrNull()
                    if (wantEnabled && (parsed == null || parsed <= 0.0)) {
                        validationErr = "Enter a valid positive 24K threshold in ₹/g first."
                    } else {
                        validationErr = null
                        onConfigure(wantEnabled, parsed ?: bgDropMonitor.threshold24kInr)
                    }
                },
                modifier = Modifier.testTag("workmanager_monitor_switch"),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (live24k > 0.0) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(-1.0 to "-1% Dip", -2.0 to "-2% Dip", -3.0 to "-3% Dip", 0.5 to "Test Above Spot").forEach { (pct, label) ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            thresholdText = "%.2f".format(live24k * (1.0 + pct / 100.0))
                            validationErr = null
                        },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = thresholdText,
            onValueChange = {
                thresholdText = it
                validationErr = null
            },
            label = { Text("Notify if 24K Gold drops below (₹/g)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("workmanager_threshold_input"),
        )

        validationErr?.takeIf { it.isNotBlank() }?.let { errText ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Status: ${bgDropMonitor.lastStatus}",
                    style = MonoSmallStyle,
                    color = if (bgDropMonitor.enabled) SuccessGreen else SecondarySilver,
                )
                if (bgDropMonitor.lastCheckedEpochMs > 0L) {
                    Text(
                        text = "Last check: ${Formatters.formatEpoch(bgDropMonitor.lastCheckedEpochMs)} • ${Formatters.formatInr(bgDropMonitor.lastObservedRateInr)}/g",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }
            StatusPill(
                text = if (bgDropMonitor.enabled) "ACTIVE" else "PAUSED",
                color = if (bgDropMonitor.enabled) SuccessGreen else SecondarySilver,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GoldPrimaryButton(
                text = if (bgDropMonitor.enabled) "Update Threshold" else "Start Monitor",
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        validationErr = "Enter a valid positive 24K threshold in ₹/g."
                    } else {
                        validationErr = null
                        onConfigure(true, parsed)
                    }
                },
                modifier = Modifier.weight(1f).testTag("workmanager_save_btn"),
            )
            GoldOutlinedButton(
                text = "Run Check Now",
                onClick = {
                    val parsed = thresholdText.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        onConfigure(true, parsed)
                    }
                    onRunNow()
                },
                modifier = Modifier.weight(1f).testTag("workmanager_run_now_btn"),
            )
        }
    }
}

@Composable
private fun AlertEditorDialog(
    initialAlert: PriceAlert,
    spotByMetal: Map<String, Double>,
    onDismiss: () -> Unit,
    onSave: (target: Double, direction: String, frequency: String, isActive: Boolean, notes: String) -> Unit,
) {
    val currentSpot = spotByMetal[initialAlert.metal] ?: 0.0
    var targetText by remember(initialAlert) { mutableStateOf("%.2f".format(initialAlert.targetPriceInr)) }
    var direction by remember(initialAlert) { mutableStateOf(initialAlert.direction) }
    var frequency by remember(initialAlert) { mutableStateOf(initialAlert.frequency) }
    var isActive by remember(initialAlert) { mutableStateOf(initialAlert.isActive) }
    var notes by remember(initialAlert) { mutableStateOf(initialAlert.notes) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("Edit ${initialAlert.metal.replace("_", " ")} Alert", color = LightGold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (currentSpot > 0.0) {
                    Text(
                        text = "Current Live Spot: ${Formatters.formatInr(currentSpot)} / g",
                        style = MonoSmallStyle,
                        color = LightGold,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(-2.0 to "-2%", -1.0 to "-1%", 0.0 to "Spot", 1.0 to "+1%", 2.0 to "+2%").forEach { (pct, label) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    targetText = "%.2f".format(currentSpot * (1.0 + pct / 100.0))
                                    if (pct < 0) direction = "BELOW" else if (pct > 0) direction = "ABOVE"
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = targetText,
                    onValueChange = {
                        targetText = it
                        errMsg = null
                    },
                    label = { Text("Target Price (₹ / gram) *") },
                    prefix = { Text("₹ ", color = LightGold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_alert_target_price_input"),
                )

                Text("Trigger Condition", style = MaterialTheme.typography.labelLarge, color = LightGold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("BELOW" to "Drops Below", "ABOVE" to "Rises Above", "CROSSES" to "Crosses Target").forEach { (code, label) ->
                        FilterChip(selected = direction == code, onClick = { direction = code }, label = { Text(label) })
                    }
                }

                Text("Alert Frequency", style = MaterialTheme.typography.labelLarge, color = LightGold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ALERT_FREQUENCY_OPTIONS.forEach { (code, label) ->
                        FilterChip(selected = frequency == code, onClick = { frequency = code }, label = { Text(label) })
                    }
                }
                Text(
                    text = frequencyDescription(frequency),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { if (it.length <= 150) notes = it },
                    label = { Text("Optional Note (e.g. Buy dip target)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Alert Active", color = PureWhite)
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
                    val target = targetText.toDoubleOrNull()
                    if (target == null || target <= 0.0) {
                        errMsg = "Enter a valid positive target price in ₹/g."
                        return@TextButton
                    }
                    onSave(target, direction, frequency, isActive, notes.trim())
                },
                modifier = Modifier.testTag("save_alert_confirm_btn"),
            ) {
                Text("Save Changes", color = RoyalGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = SecondarySilver) }
        },
    )
}
