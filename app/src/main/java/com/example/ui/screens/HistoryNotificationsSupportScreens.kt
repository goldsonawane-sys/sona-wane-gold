package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.AppNotification
import com.example.data.model.ReferralRecord
import com.example.data.model.SupportTicket
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.ui.components.EnvironmentModeBadge
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldBorderSubtle
import com.example.ui.theme.LightGold
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SuccessGreen
import com.example.util.Formatters
import com.example.util.ReceiptPdfGenerator
import java.util.Calendar

@Composable
fun TransactionHistoryScreen(
    transactions: List<TransactionRecord>,
    userName: String,
    onBack: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    var categoryFilter by remember { mutableStateOf("BULLION_TRADES") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var dateFilter by remember { mutableStateOf("ALL") }
    var selectedTx by remember { mutableStateOf<TransactionRecord?>(null) }

    val nowMs = System.currentTimeMillis()
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val filtered = transactions.filter { tx ->
        val catOk = when (categoryFilter) {
            "ALL" -> true
            "BULLION_TRADES" -> tx.category in setOf("GOLD_BUY", "GOLD_SELL", "SILVER_BUY", "SILVER_SELL")
            else -> tx.category == categoryFilter
        }
        val stOk = statusFilter == "ALL" || tx.status == statusFilter
        val tsMs = tx.createdAt?.toDate()?.time ?: nowMs
        val dateOk = when (dateFilter) {
            "TODAY" -> tsMs >= startOfToday
            "YESTERDAY" -> tsMs in (startOfToday - 86_400_000L)..startOfToday
            "7D" -> tsMs >= nowMs - 7L * 86_400_000L
            "30D" -> tsMs >= nowMs - 30L * 86_400_000L
            else -> true
        }
        catOk && stOk && dateOk
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "Transaction History", subtitle = "${filtered.size} Firestore records", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Category", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BULLION_TRADES", "ALL", "GOLD_BUY", "GOLD_SELL", "SILVER_BUY", "SILVER_SELL", "DEPOSIT", "WITHDRAWAL", "TRANSFER_SENT", "TRANSFER_RECEIVED", "REFUND").forEach { c ->
                        FilterChip(
                            selected = categoryFilter == c,
                            onClick = { categoryFilter = c },
                            label = { Text(if (c == "BULLION_TRADES") "Gold & Silver Trades" else c.replace("_", " ")) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Timeframe & Status", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "TODAY", "YESTERDAY", "7D", "30D").forEach { d ->
                        FilterChip(selected = dateFilter == d, onClick = { dateFilter = d }, label = { Text(d) })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ALL", "SUCCESSFUL", "PENDING", "PROCESSING", "FAILED", "REVERSED", "REFUNDED").forEach { s ->
                        FilterChip(selected = statusFilter == s, onClick = { statusFilter = s }, label = { Text(s) })
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    LuxuryCard {
                        Text("No matching transactions for the selected filters.", style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                    }
                }
            } else {
                items(filtered, key = { it.id }) { tx ->
                    BullionTransactionHistoryRowCard(tx = tx, onClick = { selectedTx = tx })
                }
            }
        }
    }

    if (selectedTx != null) {
        TransactionDetailDialog(
            tx = selectedTx!!,
            userName = userName,
            onDismiss = { selectedTx = null },
            onContactSupport = {
                selectedTx = null
                onOpenSupport()
            },
        )
    }
}

@Composable
fun RecentBullionTradesSection(
    transactions: List<TransactionRecord>,
    onSelectTransaction: (TransactionRecord) -> Unit,
    onViewAllHistory: () -> Unit,
) {
    var filterType by remember { mutableStateOf("ALL") } // ALL | BUY | SELL
    val bullionTrades = remember(transactions, filterType) {
        transactions
            .filter { it.category in setOf("GOLD_BUY", "GOLD_SELL", "SILVER_BUY", "SILVER_SELL") }
            .filter {
                when (filterType) {
                    "BUY" -> it.category.endsWith("_BUY")
                    "SELL" -> it.category.endsWith("_SELL")
                    else -> true
                }
            }
            .take(6)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Recent Gold & Silver Trades", style = MaterialTheme.typography.titleLarge, color = PureWhite)
                Text("Live Firestore trade ledger • Date, Type, Qty & Status", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            }
            TextButton(onClick = onViewAllHistory) {
                Text("View All", color = AmberOrange)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "All Trades", "BUY" to "Buy Only", "SELL" to "Sell Only").forEach { (key, label) ->
                FilterChip(
                    selected = filterType == key,
                    onClick = { filterType = key },
                    label = { Text(label) },
                )
            }
        }

        if (bullionTrades.isEmpty()) {
            LuxuryCard {
                Text(
                    text = "No recent gold or silver buy/sell trades recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondarySilver,
                )
            }
        } else {
            bullionTrades.forEach { tx ->
                BullionTransactionHistoryRowCard(tx = tx, onClick = { onSelectTransaction(tx) })
            }
        }
    }
}

@Composable
fun BullionTransactionHistoryRowCard(
    tx: TransactionRecord,
    onClick: () -> Unit,
) {
    val isBullion = tx.category in setOf("GOLD_BUY", "GOLD_SELL", "SILVER_BUY", "SILVER_SELL")
    val isBuy = tx.category.endsWith("_BUY")
    val typeBadgeText = when {
        tx.category == "GOLD_BUY" -> "BUY GOLD"
        tx.category == "GOLD_SELL" -> "SELL GOLD"
        tx.category == "SILVER_BUY" -> "BUY SILVER"
        tx.category == "SILVER_SELL" -> "SELL SILVER"
        else -> tx.category.replace("_", " ")
    }
    val typeBadgeColor = when {
        tx.category.endsWith("_BUY") -> SuccessGreen
        tx.category.endsWith("_SELL") -> AmberOrange
        else -> RoyalGold
    }

    LuxuryCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(text = typeBadgeText, color = typeBadgeColor)
                    if (isBullion && tx.metalPurity != "NONE") {
                        Text(
                            text = tx.metalPurity,
                            style = MaterialTheme.typography.labelMedium,
                            color = LightGold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (isBullion && tx.grams > 0.0) {
                    Text(
                        text = "Qty: ${Formatters.formatGrams(tx.grams)} • ${if (isBuy) "Buy" else "Sell"} @ ${Formatters.formatInr(tx.ratePerGram)}/g",
                        style = MonoSmallStyle,
                        color = PureWhite,
                    )
                } else {
                    Text(
                        text = "${tx.txCode} • ${tx.paymentMethod}",
                        style = MonoSmallStyle,
                        color = SecondarySilver,
                    )
                }
                Text(
                    text = Formatters.formatTimestamp(tx.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Formatters.formatInr(tx.totalAmount), style = MonoPriceStyle, color = LightGold)
                StatusPill(text = tx.status, color = statusColorFor(tx.status))
            }
        }
    }
}

@Composable
fun TransactionDetailDialog(
    tx: TransactionRecord,
    userName: String,
    onDismiss: () -> Unit,
    onContactSupport: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(tx.category.replace("_", " "), color = LightGold)
                Spacer(modifier = Modifier.height(4.dp))
                EnvironmentModeBadge(tx.mode)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeyValueRow("Transaction ID", tx.txCode)
                if (tx.grams > 0.0) KeyValueRow("Quantity (${tx.metalPurity})", Formatters.formatGrams(tx.grams))
                if (tx.ratePerGram > 0.0) KeyValueRow("Verified Rate / g", Formatters.formatInr(tx.ratePerGram))
                KeyValueRow("Base Amount", Formatters.formatInr(tx.baseAmount))
                KeyValueRow("GST", Formatters.formatInr(tx.gstAmount))
                KeyValueRow("Charges", Formatters.formatInr(tx.chargesAmount))
                KeyValueRow("Total", Formatters.formatInr(tx.totalAmount), valueColor = LightGold, boldValue = true)
                KeyValueRow("Method", tx.paymentMethod)
                KeyValueRow("Gateway Ref", tx.gatewayRef)
                KeyValueRow("Status", tx.status, valueColor = statusColorFor(tx.status))
                KeyValueRow("Timestamp", Formatters.formatTimestamp(tx.createdAt))
                if (!tx.rejectionReason.isNullOrBlank()) {
                    Text("Note: ${tx.rejectionReason}", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Transaction ID", tx.txCode))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy ID")
                    }
                    TextButton(onClick = { ReceiptPdfGenerator.shareReceiptPdf(context, tx, userName) }) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF Receipt", color = RoyalGold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onContactSupport) { Text("Need Help?", color = AmberOrange) }
        },
    )
}

@Composable
fun NotificationsScreen(
    profile: UserProfile?,
    notifications: List<AppNotification>,
    announcements: List<Announcement>,
    onMarkRead: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    onUpdatePrefs: (sound: Boolean?, vibration: Boolean?, priceAlerts: Boolean?) -> Unit,
    onBack: () -> Unit,
) {
    var activeTab by remember { mutableIntStateOf(0) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = "Notifications & Alerts",
                onBack = onBack,
                actions = {
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all", tint = AmberOrange)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = activeTab, containerColor = ObsidianCard, contentColor = RoyalGold) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Inbox (${notifications.count { !it.isRead }})") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Announcements") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("Preferences") })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (activeTab) {
                    0 -> {
                        if (notifications.isEmpty()) {
                            item { LuxuryCard { Text("Your notification inbox is empty.", color = SecondarySilver) } }
                        } else {
                            items(notifications, key = { it.id }) { n ->
                                LuxuryCard(
                                    highlightGold = !n.isRead,
                                    onClick = { if (!n.isRead) onMarkRead(n.id) },
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                StatusPill(text = n.type.replace("_", " "), color = if (!n.isRead) RoyalGold else SecondarySilver)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (!n.isRead) StatusPill(text = "UNREAD", color = AmberOrange)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(n.title, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                            Text(n.body, style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                                            Text(Formatters.formatTimestamp(n.createdAt), style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                                        }
                                        IconButton(onClick = { onDelete(n.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete notification", tint = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        if (announcements.isEmpty()) {
                            item { LuxuryCard { Text("No official announcements posted.", color = SecondarySilver) } }
                        } else {
                            items(announcements, key = { it.id }) { ann ->
                                LuxuryCard(highlightGold = ann.priority != "INFO") {
                                    StatusPill(text = ann.priority, color = if (ann.priority == "URGENT") ErrorRed else AmberOrange)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(ann.title, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                    Text(ann.message, style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                                    Text(Formatters.formatTimestamp(ann.createdAt), style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                                }
                            }
                        }
                    }
                    else -> {
                        item {
                            LuxuryCard {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    GoldOutlinedButton(
                                        text = "Enable Android Push Notifications",
                                        onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                PrefSwitchRow("Notification Sound", profile?.notifSoundEnabled ?: true) {
                                    onUpdatePrefs(it, null, null)
                                }
                                PrefSwitchRow("Vibration Feedback", profile?.notifVibrationEnabled ?: true) {
                                    onUpdatePrefs(null, it, null)
                                }
                                PrefSwitchRow("Gold & Silver Price Alerts", profile?.notifPriceAlerts ?: true) {
                                    onUpdatePrefs(null, null, it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = PureWhite)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun HelpSupportScreen(
    appConfig: AppConfig,
    tickets: List<SupportTicket>,
    isBusy: Boolean,
    onCreateTicket: (category: String, subject: String, description: String, attachmentUri: String?) -> Unit,
    onReplyTicket: (SupportTicket, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf("Gold / Silver Order") }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<String?>(null) }
    var activeTicket by remember { mutableStateOf<SupportTicket?>(null) }
    var replyText by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> attachmentUri = uri?.toString() },
    )

    val guides = listOf(
        "Gold Buying Guide" to "Select 24K, 22K, or 18K purity, enter INR or grams, review 3% GST and live rate, and pay via Wallet, UPI, Card, or NetBanking.",
        "Gold Selling Guide" to "Sell any partial or full holding at the verified sell rate. Confirm with your 4-digit MPIN to credit your Wallet or Bank Account.",
        "Silver 999 Guide" to "Silver holdings remain completely segregated from gold. Track 999 purity per gram and per kg with independent P&L.",
        "Wallet & Payments" to "Deposits via UPI/Cards update your immutable ledger atomically. TEST MODE transactions carry clear SANDBOX labels.",
        "Bank Withdrawals" to "Link a verified bank account with IFSC, confirm via MPIN, and track payout status & bank UTR reference.",
        "KYC & Account Security" to "Submit PAN, Aadhaar last 4 digits, and address for compliance review. Protect your vault with MPIN & Biometric lock.",
    )

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "Help & Support Centre", subtitle = appConfig.supportEmail, onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = activeTab, containerColor = ObsidianCard, contentColor = RoyalGold) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Guides & FAQs") })
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("New Ticket") })
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }, text = { Text("My Tickets (${tickets.size})") })
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (activeTab) {
                    0 -> {
                        item {
                            LuxuryCard(highlightGold = true) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = RoyalGold)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("SONA-WANE Priority Desk", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                        Text("${appConfig.supportEmail} • ${appConfig.supportPhone}", style = MaterialTheme.typography.bodySmall, color = LightGold)
                                    }
                                }
                            }
                        }
                        items(guides) { (title, body) ->
                            var expanded by remember { mutableStateOf(false) }
                            LuxuryCard(onClick = { expanded = !expanded }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(title, style = MaterialTheme.typography.titleMedium, color = LightGold)
                                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = RoyalGold)
                                }
                                if (expanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(body, style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                                }
                            }
                        }
                    }
                    1 -> {
                        item {
                            LuxuryCard {
                                Text("Raise a Support Ticket", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("Gold / Silver Order", "Payment / Deposit", "Withdrawal", "KYC", "Account Security").forEach { c ->
                                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Describe your issue in detail") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                                Spacer(modifier = Modifier.height(8.dp))
                                GoldOutlinedButton(
                                    text = if (attachmentUri == null) "Attach Screenshot (Photo Picker)" else "Screenshot Attached",
                                    onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                GoldPrimaryButton(
                                    text = "Submit Support Ticket",
                                    enabled = !isBusy && subject.isNotBlank() && description.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        onCreateTicket(category, subject, description, attachmentUri)
                                        subject = ""; description = ""; attachmentUri = null; activeTab = 2
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        if (tickets.isEmpty()) {
                            item { LuxuryCard { Text("No support tickets raised yet.", color = SecondarySilver) } }
                        } else {
                            items(tickets, key = { it.id }) { t ->
                                LuxuryCard(onClick = { activeTicket = t }) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(t.subject, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                            Text("${t.ticketCode} • ${t.category}", style = MonoSmallStyle, color = SecondarySilver)
                                        }
                                        StatusPill(text = t.status.replace("_", " "), color = statusColorFor(t.status))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (activeTicket != null) {
        val current = tickets.firstOrNull { it.id == activeTicket!!.id } ?: activeTicket!!
        AlertDialog(
            onDismissRequest = { activeTicket = null },
            title = { Text("${current.ticketCode} (${current.status})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.subject, fontWeight = FontWeight.Bold, color = LightGold)
                    current.messages.forEach { msg ->
                        val role = msg["senderRole"]?.toString() ?: "USER"
                        val sender = msg["senderName"]?.toString() ?: role
                        val txt = msg["text"]?.toString().orEmpty()
                        Surface(
                            color = if (role == "ADMIN") RoyalGold.copy(alpha = 0.14f) else ObsidianCard,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("$sender ($role)", style = MaterialTheme.typography.labelSmall, color = AmberOrange)
                                Text(txt, style = MaterialTheme.typography.bodySmall, color = PureWhite)
                            }
                        }
                    }
                    if (current.status != "CLOSED") {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            label = { Text("Reply to Support") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                if (current.status != "CLOSED") {
                    TextButton(onClick = {
                        if (replyText.isNotBlank()) {
                            onReplyTicket(current, replyText, false)
                            replyText = ""
                        }
                    }) { Text("Send Reply", color = RoyalGold) }
                }
            },
            dismissButton = {
                Row {
                    if (current.status != "CLOSED") {
                        TextButton(onClick = {
                            onReplyTicket(current, "Ticket closed by user.", true)
                            activeTicket = null
                        }) { Text("Close Ticket", color = ErrorRed) }
                    }
                    TextButton(onClick = { activeTicket = null }) { Text("Done") }
                }
            },
        )
    }
}

@Composable
fun ReferralsScreen(
    profile: UserProfile?,
    appConfig: AppConfig,
    referrals: List<ReferralRecord>,
    isBusy: Boolean,
    onApplyCode: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var codeInput by remember { mutableStateOf("") }
    val myCode = profile?.customUserId ?: profile?.referralCode ?: "SWG"

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "Referrals & Rewards", subtitle = "Admin-Controlled Program", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LuxuryCard(highlightGold = true) {
                    Text("Your Unique Referral Code", style = MaterialTheme.typography.labelMedium, color = RoyalGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(myCode, style = MaterialTheme.typography.displayMedium, color = LightGold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (appConfig.referralEnabled)
                            "Eligible Reward Pool: ${Formatters.formatInr(appConfig.referralRewardInr)} per verified referral (subject to KYC & admin program rules; no guaranteed investment returns)."
                        else "Referral rewards are currently paused by Administrator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    GoldPrimaryButton(
                        text = "Share Referral Invite",
                        icon = Icons.Default.Share,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join SONA-WANE GOLD for 24K digital gold & 999 silver! Use my referral ID: $myCode")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Referral"))
                        },
                    )
                }
            }

            if (profile?.referredByCode.isNullOrBlank()) {
                item {
                    LuxuryCard {
                        Text("Have a Friend's Referral User ID?", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase() },
                            label = { Text("Enter Referral User ID (e.g. SWG...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldOutlinedButton(
                            text = "Apply Referral Code",
                            enabled = !isBusy && codeInput.isNotBlank() && appConfig.referralEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onApplyCode(codeInput) },
                        )
                    }
                }
            }

            item { Text("Referral History (${referrals.size})", style = MaterialTheme.typography.titleMedium, color = PureWhite) }
            if (referrals.isEmpty()) {
                item { LuxuryCard { Text("No referrals tracked yet.", color = SecondarySilver) } }
            } else {
                items(referrals, key = { it.id }) { ref ->
                    LuxuryCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(ref.referredName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                Text("Code: ${ref.referralCode}", style = MonoSmallStyle, color = SecondarySilver)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Formatters.formatInr(ref.rewardAmountInr), style = MonoSmallStyle, color = LightGold)
                                StatusPill(text = ref.status, color = statusColorFor(ref.status))
                            }
                        }
                    }
                }
            }
        }
    }
}
