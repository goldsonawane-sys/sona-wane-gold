package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminUser
import com.example.data.model.AppConfig
import com.example.data.model.AuditLog
import com.example.data.model.KycRecord
import com.example.data.model.SupportTicket
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserTransfer
import com.example.data.repository.ALL_ADMIN_PERMISSIONS
import com.example.data.repository.AdminRepository
import com.example.data.repository.AdminRole
import com.example.data.repository.AuditLogManager
import com.example.ui.components.EnvironmentModeBadge
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LuxuryCard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.SonaWaneTopBar
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
import com.example.util.Formatters
import com.example.util.ReceiptPdfGenerator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminControlPanelScreen(
    adminUser: AdminUser?,
    adminVerified: Boolean,
    statusMessage: String?,
    isBusy: Boolean,
    allUsers: List<UserProfile>,
    allTransactions: List<TransactionRecord>,
    allKyc: List<KycRecord>,
    allTickets: List<SupportTicket>,
    allTransfers: List<UserTransfer>,
    admins: List<AdminUser>,
    auditLogs: List<AuditLog>,
    appConfig: AppConfig,
    onLockAdmin: () -> Unit,
    onSignOutAdmin: () -> Unit,
    onClearStatus: () -> Unit,
    onUpdateUserStatus: (UserProfile, String, String) -> Unit,
    onReviewKyc: (KycRecord, String, String) -> Unit,
    onProcessWithdrawal: (TransactionRecord, Boolean, String, String) -> Unit,
    onReplyTicket: (SupportTicket, String, String) -> Unit,
    onPublishAnnouncement: (String, String, String) -> Unit,
    onUpdateAppConfig: (AppConfig, String) -> Unit,
    onUpsertAdminRole: (
        uid: String,
        email: String,
        name: String,
        role: AdminRole,
        permissions: List<String>,
        operatorId: String,
        initialPasscode: String?,
        isActive: Boolean,
    ) -> Unit,
    onResetAdminCredentials: (target: AdminUser, newOperatorId: String, newPasscode: String) -> Unit,
    onDeleteAdminRole: (target: AdminUser) -> Unit,
    onRotateOwnCredentials: (operatorId: String, currentPasscode: String, newPasscode: String, biometricEnabled: Boolean) -> Unit,
    onUpdateOwnBiometric: (Boolean) -> Unit,
    onBuildCsv: (String?) -> String,
    onBuildAuditCsv: (List<AuditLog>) -> String = { logs -> AuditLogManager.buildAuditComplianceCsv(logs) },
    onOpenKycReviewScreen: () -> Unit = {},
    onBackToCustomerApp: () -> Unit,
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf("OVERVIEW") }
    var userSearch by remember { mutableStateOf("") }
    var auditSearch by remember { mutableStateOf("") }
    var auditCategory by remember { mutableStateOf(AuditLogManager.AuditCategory.ALL) }

    // Dialog states
    var userActionTarget by remember { mutableStateOf<Pair<UserProfile, String>?>(null) }
    var reasonInput by remember { mutableStateOf("") }
    var payoutTarget by remember { mutableStateOf<Pair<TransactionRecord, Boolean>?>(null) }
    var payoutUtrInput by remember { mutableStateOf("") }
    var ticketTarget by remember { mutableStateOf<SupportTicket?>(null) }
    var ticketReplyText by remember { mutableStateOf("") }
    var ticketNewStatus by remember { mutableStateOf("IN_PROGRESS") }
    var showRotateSelfDialog by remember { mutableStateOf(false) }

    val availableSections = remember(adminUser) {
        if (adminUser == null) {
            emptyList()
        } else {
            buildList {
                add("OVERVIEW")
                if (adminUser.hasPermission("USERS")) add("USERS")
                if (adminUser.hasPermission("KYC")) add("KYC")
                if (adminUser.hasPermission("WITHDRAWALS")) add("PAYOUTS")
                if (adminUser.hasPermission("FINANCE") || adminUser.hasPermission("REPORTS")) add("LEDGER")
                if (adminUser.hasPermission("SUPPORT")) add("SUPPORT")
                if (adminUser.hasPermission("SETTINGS")) add("SETTINGS")
                if (adminUser.hasPermission("ROLES") || adminUser.hasPermission("REPORTS")) add("RBAC & AUDIT")
            }
        }
    }

    LaunchedEffect(availableSections) {
        if (availableSections.isNotEmpty() && selectedSection !in availableSections) {
            selectedSection = availableSections.first()
        }
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = "SONA-WANE Admin Console",
                subtitle = adminUser?.let {
                    "${it.role} • ${it.operatorId.ifBlank { it.email }}"
                } ?: "Restricted Access",
                onBack = onBackToCustomerApp,
                actions = {
                    if (adminVerified) {
                        IconButton(
                            onClick = onLockAdmin,
                            modifier = Modifier.testTag("lock_admin_console_button"),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Admin Console", tint = AmberOrange)
                        }
                        IconButton(
                            onClick = onSignOutAdmin,
                            modifier = Modifier.testTag("sign_out_admin_console_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out Admin", tint = SecondarySilver)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (adminUser == null || !adminVerified) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                LuxuryCard(highlightGold = true) {
                    Text(
                        text = if (adminUser == null) "Access Denied" else "Admin Session Locked",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ErrorRed,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Authenticate with your unique Admin Operator ID and Security Passcode in the isolated Admin Portal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondarySilver,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OrangeActionButton(
                        text = "Open Admin Login",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLockAdmin,
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Active RBAC Session Strip
            Surface(
                color = ObsidianCard,
                border = BorderStroke(1.dp, GoldBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${adminUser.displayName} (${adminUser.operatorId.ifBlank { "OP-ADMIN" }})",
                                style = MaterialTheme.typography.titleSmall,
                                color = LightGold,
                            )
                            Text(adminUser.email, style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                        }
                        StatusPill(text = adminUser.role, color = AmberOrange)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        adminUser.permissions.forEach { perm ->
                            StatusPill(text = perm, color = RoyalGold)
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                Surface(
                    color = RoyalGold.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, RoyalGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = LightGold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearStatus) { Text("Dismiss", color = AmberOrange) }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableSections.forEach { sec ->
                    FilterChip(
                        selected = selectedSection == sec,
                        onClick = { selectedSection = sec },
                        label = { Text(sec) },
                    )
                }
            }

            if (selectedSection == "KYC") {
                AdminKycReviewQueueContent(
                    allKyc = allKyc,
                    isBusy = isBusy,
                    onReviewKyc = onReviewKyc,
                    modifier = Modifier.fillMaxSize(),
                    onLaunchFullScreen = onOpenKycReviewScreen,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (selectedSection) {
                        "OVERVIEW" -> {
                            val pendingKyc = allKyc.count { it.status == "PENDING" || it.status == "UNDER_REVIEW" }
                            val pendingWithdrawals = allTransactions.count { it.category == "WITHDRAWAL" && it.status == "PENDING" }
                            val openTickets = allTickets.count { it.status != "RESOLVED" && it.status != "CLOSED" }
                            val totalVolume = allTransactions.filter { it.status == "SUCCESSFUL" || it.status == "COMPLETED" }.sumOf { it.totalAmount }

                            item {
                                LuxuryCard(highlightGold = true) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Platform Operational Snapshot", style = MaterialTheme.typography.titleMedium, color = LightGold)
                                        EnvironmentModeBadge(appConfig.paymentMode)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    KeyValueRow("Registered Users", "${allUsers.size}", boldValue = true)
                                    KeyValueRow("Pending KYC Reviews", "$pendingKyc", valueColor = if (pendingKyc > 0) AmberOrange else SuccessGreen, boldValue = true)
                                    KeyValueRow("Pending Bank Withdrawals", "$pendingWithdrawals", valueColor = if (pendingWithdrawals > 0) AmberOrange else SuccessGreen, boldValue = true)
                                    KeyValueRow("Open Support Tickets", "$openTickets", valueColor = if (openTickets > 0) AmberOrange else SuccessGreen)
                                    KeyValueRow("Settled Order Volume", Formatters.formatInr(totalVolume), valueColor = LightGold, boldValue = true)
                                    KeyValueRow("P2P Bullion Transfers", "${allTransfers.size}")
                                    KeyValueRow("Maintenance Mode", if (appConfig.maintenanceMode) "ENABLED" else "OFF", valueColor = if (appConfig.maintenanceMode) ErrorRed else SuccessGreen)
                                    if (adminUser.hasPermission("KYC")) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OrangeActionButton(
                                            text = "Review Pending KYC Submissions ($pendingKyc)",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("open_pending_kyc_queue_button"),
                                            onClick = onOpenKycReviewScreen,
                                        )
                                    }
                                }
                            }
                        if (adminUser.hasPermission("REPORTS") || adminUser.hasPermission("FINANCE")) {
                            item {
                                GoldPrimaryButton(
                                    text = "Export Full Transactions CSV",
                                    icon = Icons.Default.Download,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        ReceiptPdfGenerator.exportCsvReport(context, "sonawane_full_ledger.csv", onBuildCsv(null))
                                    },
                                )
                            }
                        }
                        item {
                            AdminOwnSecurityCard(
                                adminUser = adminUser,
                                isBusy = isBusy,
                                onToggleBiometric = onUpdateOwnBiometric,
                                onOpenRotateCredentials = { showRotateSelfDialog = true },
                            )
                        }
                    }

                    "USERS" -> {
                        item {
                            OutlinedTextField(
                                value = userSearch,
                                onValueChange = { userSearch = it },
                                label = { Text("Search name, User ID, email or mobile") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val filteredUsers = allUsers.filter { u ->
                            userSearch.isBlank() ||
                                u.fullName.contains(userSearch, ignoreCase = true) ||
                                u.customUserId.contains(userSearch, ignoreCase = true) ||
                                u.email.contains(userSearch, ignoreCase = true) ||
                                u.mobileNumber.contains(userSearch, ignoreCase = true)
                        }
                        items(filteredUsers, key = { it.userId }) { u ->
                            LuxuryCard {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(u.fullName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                        Text("${u.customUserId} • ${u.email}", style = MonoSmallStyle, color = LightGold)
                                        Text("Mobile: ${u.countryCode} ${u.mobileNumber.ifBlank { "-" }} • KYC: ${u.kycStatus}", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                                    }
                                    StatusPill(text = u.accountStatus, color = statusColorFor(u.accountStatus))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (u.accountStatus != "ACTIVE") {
                                        GoldOutlinedButton(
                                            text = "Activate",
                                            onClick = { reasonInput = ""; userActionTarget = u to "ACTIVE" },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (u.accountStatus != "SUSPENDED") {
                                        GoldOutlinedButton(
                                            text = "Suspend",
                                            onClick = { reasonInput = ""; userActionTarget = u to "SUSPENDED" },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (u.accountStatus != "BLOCKED") {
                                        TextButton(onClick = { reasonInput = ""; userActionTarget = u to "BLOCKED" }) {
                                            Text("Block", color = ErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "PAYOUTS" -> {
                        val withdrawals = allTransactions.filter { it.category == "WITHDRAWAL" }
                        if (withdrawals.isEmpty()) {
                            item { LuxuryCard { Text("No withdrawal requests yet.", color = SecondarySilver) } }
                        } else {
                            items(withdrawals, key = { it.id }) { tx ->
                                LuxuryCard(highlightGold = tx.status == "PENDING") {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${tx.txCode} • ${tx.bankNameMasked ?: "Bank"}", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                            Text("User: ${tx.userId.take(10)}... • Fee: ${Formatters.formatInr(tx.chargesAmount)}", style = MonoSmallStyle, color = SecondarySilver)
                                            Text("Net Payout: ${Formatters.formatInr(tx.baseAmount)} | Locked: ${Formatters.formatInr(tx.totalAmount)}", style = MonoSmallStyle, color = LightGold)
                                        }
                                        StatusPill(text = tx.status, color = statusColorFor(tx.status))
                                    }
                                    if (tx.status == "PENDING") {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GoldPrimaryButton(
                                                text = "Mark Paid (UTR)",
                                                enabled = !isBusy,
                                                onClick = { payoutUtrInput = ""; reasonInput = "Bank payout settled"; payoutTarget = tx to true },
                                                modifier = Modifier.weight(1f),
                                            )
                                            OrangeActionButton(
                                                text = "Reverse & Refund",
                                                enabled = !isBusy,
                                                onClick = { payoutUtrInput = ""; reasonInput = ""; payoutTarget = tx to false },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "LEDGER" -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoldOutlinedButton(
                                    text = "Export Gold Orders CSV",
                                    onClick = { ReceiptPdfGenerator.exportCsvReport(context, "gold_orders.csv", onBuildCsv("GOLD_BUY")) },
                                    modifier = Modifier.weight(1f),
                                )
                                GoldOutlinedButton(
                                    text = "Export Silver CSV",
                                    onClick = { ReceiptPdfGenerator.exportCsvReport(context, "silver_orders.csv", onBuildCsv("SILVER_BUY")) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        items(allTransactions.take(60), key = { it.id }) { tx ->
                            LuxuryCard {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${tx.category.replace("_", " ")} (${tx.mode})", style = MaterialTheme.typography.titleSmall, color = PureWhite)
                                        Text("${tx.txCode} • Ref: ${tx.gatewayRef}", style = MonoSmallStyle, color = SecondarySilver)
                                        if (tx.grams > 0.0) {
                                            Text("${tx.metalPurity}: ${Formatters.formatGrams(tx.grams)} @ ${Formatters.formatInr(tx.ratePerGram)}/g", style = MonoSmallStyle, color = LightGold)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Formatters.formatInr(tx.totalAmount), style = MonoSmallStyle, color = LightGold)
                                        StatusPill(text = tx.status, color = statusColorFor(tx.status))
                                    }
                                }
                            }
                        }
                    }

                    "SUPPORT" -> {
                        if (allTickets.isEmpty()) {
                            item { LuxuryCard { Text("No support tickets in queue.", color = SecondarySilver) } }
                        } else {
                            items(allTickets, key = { it.id }) { t ->
                                LuxuryCard(
                                    highlightGold = t.status == "OPEN",
                                    onClick = {
                                        ticketTarget = t
                                        ticketNewStatus = if (t.status == "OPEN") "IN_PROGRESS" else t.status
                                        ticketReplyText = ""
                                    },
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(t.subject, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                            Text("${t.ticketCode} • ${t.userName} • ${t.category}", style = MonoSmallStyle, color = SecondarySilver)
                                        }
                                        StatusPill(text = t.status, color = statusColorFor(t.status))
                                    }
                                }
                            }
                        }
                    }

                    "SETTINGS" -> {
                        item {
                            AnnouncementBroadcasterCard(
                                isBusy = isBusy,
                                onPublish = onPublishAnnouncement,
                            )
                        }
                        item {
                            PlatformSettingsEditorCard(
                                appConfig = appConfig,
                                isBusy = isBusy,
                                onSave = onUpdateAppConfig,
                            )
                        }
                    }

                    "RBAC & AUDIT" -> {
                        item {
                            RbacManagerCard(
                                currentAdmin = adminUser,
                                admins = admins,
                                allUsers = allUsers,
                                isBusy = isBusy,
                                onUpsertRole = onUpsertAdminRole,
                                onResetCredentials = onResetAdminCredentials,
                                onDeleteAdmin = onDeleteAdminRole,
                            )
                        }
                        val filteredAuditLogs = auditLogs.filter { log ->
                            val matchesCat = auditCategory == AuditLogManager.AuditCategory.ALL ||
                                AuditLogManager.classifyAction(log.action) == auditCategory
                            val matchesQuery = auditSearch.isBlank() ||
                                log.action.contains(auditSearch, ignoreCase = true) ||
                                log.adminEmail.contains(auditSearch, ignoreCase = true) ||
                                log.affectedCollection.contains(auditSearch, ignoreCase = true) ||
                                log.affectedRecordId.contains(auditSearch, ignoreCase = true) ||
                                log.reason.contains(auditSearch, ignoreCase = true) ||
                                log.previousValue.contains(auditSearch, ignoreCase = true) ||
                                log.newValue.contains(auditSearch, ignoreCase = true)
                            matchesCat && matchesQuery
                        }
                        item {
                            LuxuryCard(highlightGold = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Compliance Audit Trail (${filteredAuditLogs.size}/${auditLogs.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = LightGold,
                                        )
                                        Text(
                                            text = "Append-only Firestore audit_logs managed by AuditLogManager",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SecondarySilver,
                                        )
                                    }
                                    StatusPill(text = "IMMUTABLE", color = SuccessGreen)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = auditSearch,
                                    onValueChange = { auditSearch = it },
                                    label = { Text("Search action, admin, collection or reason") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("audit_search_input"),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AuditLogManager.AuditCategory.entries.forEach { cat ->
                                        FilterChip(
                                            selected = auditCategory == cat,
                                            onClick = { auditCategory = cat },
                                            label = { Text(cat.label) },
                                            modifier = Modifier.testTag("audit_filter_${cat.name.lowercase()}"),
                                        )
                                    }
                                }
                                if (adminUser.hasPermission("REPORTS") || adminUser.hasPermission("ROLES")) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    GoldOutlinedButton(
                                        text = "Export Filtered Audit Trail CSV (${filteredAuditLogs.size})",
                                        icon = Icons.Default.Download,
                                        enabled = filteredAuditLogs.isNotEmpty(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("export_audit_csv_button"),
                                        onClick = {
                                            val csv = onBuildAuditCsv(filteredAuditLogs)
                                            if (csv.isNotBlank()) {
                                                ReceiptPdfGenerator.exportCsvReport(
                                                    context = context,
                                                    fileName = "sonawane_audit_trail.csv",
                                                    csvContent = csv,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        if (filteredAuditLogs.isEmpty()) {
                            item {
                                LuxuryCard {
                                    Text(
                                        text = "No audit entries match the current filter.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SecondarySilver,
                                    )
                                }
                            }
                        } else {
                            items(filteredAuditLogs.take(80), key = { it.id }) { log ->
                                val badgeColor = when {
                                    log.action.contains("FAIL", ignoreCase = true) ||
                                        log.action.contains("REVOKE", ignoreCase = true) ||
                                        log.action.contains("REJECT", ignoreCase = true) ||
                                        log.action.contains("DENIED", ignoreCase = true) ||
                                        log.action.contains("BLOCKED", ignoreCase = true) ||
                                        log.action.contains("REVERSED", ignoreCase = true) -> ErrorRed
                                    log.action.contains("APPROV", ignoreCase = true) ||
                                        log.action.contains("ACTIVE", ignoreCase = true) ||
                                        log.action.contains("LOGIN", ignoreCase = true) ||
                                        log.action.contains("UNLOCK", ignoreCase = true) -> SuccessGreen
                                    log.action.contains("CONFIG", ignoreCase = true) ||
                                        log.action.contains("ROLE", ignoreCase = true) ||
                                        log.action.contains("CREDENTIAL", ignoreCase = true) -> AmberOrange
                                    else -> RoyalGold
                                }
                                LuxuryCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = log.action,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = LightGold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        StatusPill(
                                            text = AuditLogManager.classifyAction(log.action).label,
                                            color = badgeColor,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Actor: ${log.adminEmail} (${log.adminRole})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PureWhite,
                                    )
                                    Text(
                                        text = "Target: ${log.affectedCollection}/${log.affectedRecordId}",
                                        style = MonoSmallStyle,
                                        color = AmberOrange,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = DeepBlack.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, GoldBorderSubtle),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Prev: ${log.previousValue}", style = MonoSmallStyle, color = SecondarySilver)
                                            Text("New:  ${log.newValue}", style = MonoSmallStyle, color = PureWhite)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Reason: ${log.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondarySilver,
                                    )
                                    Text(
                                        text = "${Formatters.formatTimestamp(log.createdAt)} • ${log.deviceInfo}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondarySilver,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    userActionTarget?.let { (target, newStatus) ->
        AlertDialog(
            onDismissRequest = { userActionTarget = null },
            title = { Text("Mark ${target.fullName} as $newStatus?") },
            text = {
                OutlinedTextField(
                    value = reasonInput,
                    onValueChange = { reasonInput = it },
                    label = { Text("Audit Reason *") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = reasonInput.isNotBlank(),
                    onClick = {
                        onUpdateUserStatus(target, newStatus, reasonInput)
                        userActionTarget = null
                    },
                ) { Text("Confirm", color = RoyalGold) }
            },
            dismissButton = { TextButton(onClick = { userActionTarget = null }) { Text("Cancel") } },
        )
    }

    payoutTarget?.let { (tx, approve) ->
        AlertDialog(
            onDismissRequest = { payoutTarget = null },
            title = { Text(if (approve) "Confirm Payout (${tx.txCode})" else "Reverse Withdrawal (${tx.txCode})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (approve) {
                        OutlinedTextField(
                            value = payoutUtrInput,
                            onValueChange = { payoutUtrInput = it },
                            label = { Text("Bank UTR / Reference Number") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Audit Notes *") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reasonInput.isNotBlank(),
                    onClick = {
                        onProcessWithdrawal(tx, approve, payoutUtrInput, reasonInput)
                        payoutTarget = null
                    },
                ) { Text(if (approve) "Mark Paid" else "Reverse & Refund", color = if (approve) RoyalGold else ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { payoutTarget = null }) { Text("Cancel") } },
        )
    }

    ticketTarget?.let { current ->
        AlertDialog(
            onDismissRequest = { ticketTarget = null },
            title = { Text("Ticket ${current.ticketCode}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(current.subject, fontWeight = FontWeight.Bold, color = LightGold)
                    current.messages.forEach { m ->
                        Text("${m["senderName"]}: ${m["text"]}", style = MaterialTheme.typography.bodySmall, color = PureWhite)
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("OPEN", "IN_PROGRESS", "WAITING_FOR_USER", "RESOLVED", "CLOSED").forEach { s ->
                            FilterChip(selected = ticketNewStatus == s, onClick = { ticketNewStatus = s }, label = { Text(s.replace("_", " ")) })
                        }
                    }
                    OutlinedTextField(
                        value = ticketReplyText,
                        onValueChange = { ticketReplyText = it },
                        label = { Text("Official Support Reply") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onReplyTicket(current, ticketReplyText, ticketNewStatus)
                    ticketTarget = null
                }) { Text("Save & Send", color = RoyalGold) }
            },
            dismissButton = { TextButton(onClick = { ticketTarget = null }) { Text("Cancel") } },
        )
    }

    if (showRotateSelfDialog && adminUser != null) {
        RotateOwnCredentialsDialog(
            adminUser = adminUser,
            onDismiss = { showRotateSelfDialog = false },
            onSave = { opId, currentPin, newPin, bio ->
                onRotateOwnCredentials(opId, currentPin, newPin, bio)
                showRotateSelfDialog = false
            },
        )
    }
}

@Composable
private fun AnnouncementBroadcasterCard(
    isBusy: Boolean,
    onPublish: (String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("INFO") }

    LuxuryCard {
        Text("Broadcast Official Announcement", style = MaterialTheme.typography.titleMedium, color = LightGold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(value = msg, onValueChange = { msg = it }, label = { Text("Message Body") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("INFO", "IMPORTANT", "URGENT").forEach { p ->
                FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p) })
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        GoldPrimaryButton(
            text = "Broadcast Now",
            enabled = !isBusy && title.isNotBlank() && msg.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onPublish(title, msg, priority)
                title = ""; msg = ""
            },
        )
    }
}

@Composable
private fun PlatformSettingsEditorCard(
    appConfig: AppConfig,
    isBusy: Boolean,
    onSave: (AppConfig, String) -> Unit,
) {
    var maintenance by remember(appConfig) { mutableStateOf(appConfig.maintenanceMode) }
    var paymentMode by remember(appConfig) { mutableStateOf(appConfig.paymentMode) }
    var gatewayName by remember(appConfig) { mutableStateOf(appConfig.paymentGatewayName) }
    var providerName by remember(appConfig) { mutableStateOf(appConfig.goldProviderName) }
    var gst by remember(appConfig) { mutableStateOf(appConfig.gstPercent.toString()) }
    var goldSpread by remember(appConfig) { mutableStateOf(appConfig.goldSpreadPercent.toString()) }
    var silverSpread by remember(appConfig) { mutableStateOf(appConfig.silverSpreadPercent.toString()) }
    var withdrawalFee by remember(appConfig) { mutableStateOf(appConfig.withdrawalFeeInr.toString()) }
    var transferFee by remember(appConfig) { mutableStateOf(appConfig.transferFeePercent.toString()) }
    var referralEnabled by remember(appConfig) { mutableStateOf(appConfig.referralEnabled) }
    var referralReward by remember(appConfig) { mutableStateOf(appConfig.referralRewardInr.toString()) }
    var reason by remember { mutableStateOf("Admin config update") }

    LuxuryCard(highlightGold = true) {
        Text("Environment, Taxes & Platform Config", style = MaterialTheme.typography.titleMedium, color = LightGold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Maintenance Mode", color = PureWhite)
            Switch(checked = maintenance, onCheckedChange = { maintenance = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = paymentMode == "TEST", onClick = { paymentMode = "TEST" }, label = { Text("TEST / SANDBOX") })
            FilterChip(selected = paymentMode == "LIVE", onClick = { paymentMode = "LIVE" }, label = { Text("LIVE MODE") })
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(value = gatewayName, onValueChange = { gatewayName = it }, label = { Text("Payment Gateway") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(value = providerName, onValueChange = { providerName = it }, label = { Text("Bullion Provider") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = gst, onValueChange = { gst = it }, label = { Text("GST %") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = goldSpread, onValueChange = { goldSpread = it }, label = { Text("Gold Spread %") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = silverSpread, onValueChange = { silverSpread = it }, label = { Text("Silver Spread %") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(value = withdrawalFee, onValueChange = { withdrawalFee = it }, label = { Text("Withdraw Fee ₹") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = transferFee, onValueChange = { transferFee = it }, label = { Text("Transfer Fee %") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Referral Program Active", color = PureWhite)
            Switch(checked = referralEnabled, onCheckedChange = { referralEnabled = it })
        }
        OutlinedTextField(value = referralReward, onValueChange = { referralReward = it }, label = { Text("Referral Reward ₹") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Audit Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(12.dp))
        GoldPrimaryButton(
            text = "Save Platform Configuration",
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(
                    appConfig.copy(
                        maintenanceMode = maintenance,
                        paymentMode = paymentMode,
                        paymentGatewayName = gatewayName,
                        goldProviderName = providerName,
                        gstPercent = gst.toDoubleOrNull() ?: appConfig.gstPercent,
                        goldSpreadPercent = goldSpread.toDoubleOrNull() ?: appConfig.goldSpreadPercent,
                        silverSpreadPercent = silverSpread.toDoubleOrNull() ?: appConfig.silverSpreadPercent,
                        withdrawalFeeInr = withdrawalFee.toDoubleOrNull() ?: appConfig.withdrawalFeeInr,
                        transferFeePercent = transferFee.toDoubleOrNull() ?: appConfig.transferFeePercent,
                        referralEnabled = referralEnabled,
                        referralRewardInr = referralReward.toDoubleOrNull() ?: appConfig.referralRewardInr,
                    ),
                    reason,
                )
            },
        )
    }
}

@Composable
private fun AdminOwnSecurityCard(
    adminUser: AdminUser,
    isBusy: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    onOpenRotateCredentials: () -> Unit,
) {
    LuxuryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = AmberOrange)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Admin Account Security", style = MaterialTheme.typography.titleMedium, color = LightGold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        KeyValueRow("Operator ID", adminUser.operatorId.ifBlank { AdminRepository.defaultOperatorId(adminUser.uid, adminUser.email) }, valueColor = PureWhite, boldValue = true)
        KeyValueRow("Assigned Role", adminUser.role, valueColor = AmberOrange)
        KeyValueRow("Unique Passcode", if (adminUser.adminPinHash.isNullOrBlank()) "Not Enrolled" else "Salted SHA-256 Active", valueColor = if (adminUser.adminPinHash.isNullOrBlank()) ErrorRed else SuccessGreen)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = RoyalGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Biometric 2FA Admin Unlock", style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                    Text("Use Fingerprint / Face on future admin logins", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                }
            }
            Switch(
                checked = adminUser.biometricAdminEnabled,
                enabled = !isBusy && !adminUser.adminPinHash.isNullOrBlank(),
                onCheckedChange = onToggleBiometric,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        GoldOutlinedButton(
            text = "Rotate My Operator ID & Passcode",
            icon = Icons.Default.Key,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenRotateCredentials,
        )
    }
}

@Composable
private fun RotateOwnCredentialsDialog(
    adminUser: AdminUser,
    onDismiss: () -> Unit,
    onSave: (operatorId: String, currentPasscode: String, newPasscode: String, biometricEnabled: Boolean) -> Unit,
) {
    var opId by remember {
        mutableStateOf(adminUser.operatorId.ifBlank { AdminRepository.defaultOperatorId(adminUser.uid, adminUser.email) })
    }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(adminUser.biometricAdminEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotate Admin Credentials") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = opId,
                    onValueChange = { opId = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }.take(32) },
                    label = { Text("Operator ID *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.take(32) },
                    label = { Text("Current Admin Passcode *") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.take(32) },
                    label = { Text("New Admin Passcode (min 6 chars) *") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Keep Biometric 2FA Enabled", style = MaterialTheme.typography.bodySmall, color = PureWhite)
                    Switch(checked = bio, onCheckedChange = { bio = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = opId.length >= 4 && currentPin.isNotBlank() && newPin.length >= 6,
                onClick = { onSave(opId, currentPin, newPin, bio) },
            ) { Text("Update Credentials", color = RoyalGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RbacManagerCard(
    currentAdmin: AdminUser,
    admins: List<AdminUser>,
    allUsers: List<UserProfile>,
    isBusy: Boolean,
    onUpsertRole: (
        uid: String,
        email: String,
        name: String,
        role: AdminRole,
        permissions: List<String>,
        operatorId: String,
        initialPasscode: String?,
        isActive: Boolean,
    ) -> Unit,
    onResetCredentials: (target: AdminUser, newOperatorId: String, newPasscode: String) -> Unit,
    onDeleteAdmin: (target: AdminUser) -> Unit,
) {
    val canManageRoles = currentAdmin.hasPermission("ROLES")
    var uidInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var operatorIdInput by remember { mutableStateOf("") }
    var initialPasscodeInput by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(AdminRole.FINANCE_MANAGER) }
    var selectedPermissions by remember { mutableStateOf(AdminRole.FINANCE_MANAGER.permissions) }
    var active by remember { mutableStateOf(true) }

    var resetTarget by remember { mutableStateOf<AdminUser?>(null) }
    var resetOpId by remember { mutableStateOf("") }
    var resetPasscode by remember { mutableStateOf("") }
    var revokeTarget by remember { mutableStateOf<AdminUser?>(null) }

    LuxuryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = RoyalGold)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Role-Based Admin Access (${admins.size})", style = MaterialTheme.typography.titleMedium, color = LightGold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        admins.forEach { a ->
            val isPrimaryRoot = a.email.equals(AdminRepository.BOOTSTRAPPED_SUPER_ADMIN_EMAIL, ignoreCase = true)
            Surface(
                color = DeepBlack.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, GoldBorderSubtle),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${a.displayName} (${a.email})", style = MaterialTheme.typography.bodyMedium, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            Text(
                                "OpID: ${a.operatorId.ifBlank { AdminRepository.defaultOperatorId(a.uid, a.email) }} • ${a.role}",
                                style = MonoSmallStyle,
                                color = AmberOrange,
                            )
                        }
                        StatusPill(text = if (a.isActive) "ACTIVE" else "DISABLED", color = if (a.isActive) SuccessGreen else ErrorRed)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        a.permissions.forEach { perm ->
                            StatusPill(text = perm, color = RoyalGold)
                        }
                        StatusPill(
                            text = if (a.adminPinHash.isNullOrBlank()) "PASSCODE PENDING" else "CREDENTIAL SET",
                            color = if (a.adminPinHash.isNullOrBlank()) AmberOrange else SuccessGreen,
                        )
                    }
                    if (canManageRoles) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    uidInput = a.uid
                                    emailInput = a.email
                                    nameInput = a.displayName
                                    operatorIdInput = a.operatorId.ifBlank { AdminRepository.defaultOperatorId(a.uid, a.email) }
                                    val matchedRole = AdminRole.entries.firstOrNull { it.name == a.role } ?: AdminRole.ADMIN
                                    role = matchedRole
                                    selectedPermissions = a.permissions.ifEmpty { matchedRole.permissions }
                                    active = a.isActive
                                    initialPasscodeInput = ""
                                },
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Role", color = LightGold)
                            }
                            TextButton(
                                onClick = {
                                    resetTarget = a
                                    resetOpId = a.operatorId.ifBlank { AdminRepository.defaultOperatorId(a.uid, a.email) }
                                    resetPasscode = ""
                                },
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Creds", color = AmberOrange)
                            }
                            if (!isPrimaryRoot && a.uid != currentAdmin.uid) {
                                TextButton(onClick = { revokeTarget = a }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Revoke", color = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (canManageRoles) {
            HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AmberOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant / Customize Admin Role & Credentials", style = MaterialTheme.typography.titleSmall, color = PureWhite)
            }

            if (allUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Quick-Pick Registered Account:", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allUsers.take(15).forEach { u ->
                        FilterChip(
                            selected = uidInput == u.userId,
                            onClick = {
                                uidInput = u.userId
                                emailInput = u.email
                                nameInput = u.fullName
                                operatorIdInput = AdminRepository.defaultOperatorId(u.userId, u.email)
                            },
                            label = { Text(u.email.ifBlank { u.fullName }) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = uidInput,
                onValueChange = { uidInput = it },
                label = { Text("Firebase User UID *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rbac_uid_input"),
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Admin Corporate Email *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rbac_email_input"),
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Admin Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = operatorIdInput,
                    onValueChange = {
                        operatorIdInput = it.uppercase().filter { ch -> ch.isLetterOrDigit() || ch == '-' || ch == '_' }.take(32)
                    },
                    label = { Text("Unique Operator ID") },
                    placeholder = { Text("SWG-ADM-...") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = AmberOrange) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = initialPasscodeInput,
                    onValueChange = { initialPasscodeInput = it.take(32) },
                    label = { Text("Passcode (opt, 6+)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Role Preset:", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdminRole.entries.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = {
                            role = r
                            selectedPermissions = r.permissions
                        },
                        label = { Text(r.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Granular Permissions:", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ALL_ADMIN_PERMISSIONS.forEach { (permKey, _) ->
                    val granted = role == AdminRole.SUPER_ADMIN || permKey in selectedPermissions
                    FilterChip(
                        selected = granted,
                        enabled = role != AdminRole.SUPER_ADMIN,
                        onClick = {
                            selectedPermissions = if (permKey in selectedPermissions) {
                                selectedPermissions - permKey
                            } else {
                                selectedPermissions + permKey
                            }
                        },
                        label = { Text(permKey) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Role Active", color = PureWhite)
                Switch(checked = active, onCheckedChange = { active = it })
            }
            GoldPrimaryButton(
                text = "Save Admin Role & Permissions",
                enabled = !isBusy && uidInput.isNotBlank() && emailInput.isNotBlank() &&
                    (initialPasscodeInput.isBlank() || initialPasscodeInput.length >= 6),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_admin_role_button"),
                onClick = {
                    onUpsertRole(
                        uidInput.trim(),
                        emailInput.trim(),
                        nameInput.ifBlank { emailInput },
                        role,
                        selectedPermissions,
                        operatorIdInput.trim(),
                        initialPasscodeInput.takeIf { it.isNotBlank() },
                        active,
                    )
                    uidInput = ""
                    emailInput = ""
                    nameInput = ""
                    operatorIdInput = ""
                    initialPasscodeInput = ""
                },
            )
        }
    }

    resetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text("Reset Admin Credentials") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a new unique Operator ID and Passcode for ${target.email}", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                    OutlinedTextField(
                        value = resetOpId,
                        onValueChange = { resetOpId = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }.take(32) },
                        label = { Text("New Operator ID *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = resetPasscode,
                        onValueChange = { resetPasscode = it.take(32) },
                        label = { Text("New Admin Passcode (min 6 chars) *") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = resetOpId.length >= 4 && resetPasscode.length >= 6,
                    onClick = {
                        onResetCredentials(target, resetOpId, resetPasscode)
                        resetTarget = null
                    },
                ) { Text("Reset Credentials", color = AmberOrange) }
            },
            dismissButton = { TextButton(onClick = { resetTarget = null }) { Text("Cancel") } },
        )
    }

    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("Revoke Admin Access?") },
            text = { Text("Remove ${target.displayName} (${target.email}) from Firestore RBAC? They will immediately lose admin portal access.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAdmin(target)
                        revokeTarget = null
                    },
                ) { Text("Revoke Access", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { revokeTarget = null }) { Text("Cancel") } },
        )
    }
}
