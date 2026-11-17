package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.AppConfig
import com.example.data.model.BankAccount
import com.example.data.model.LiveMarketRates
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.WalletHoldings
import com.example.data.model.WalletLedgerEntry
import com.example.util.BiometricAuthManager
import com.example.util.BiometricLoginManager
import com.example.util.BullionQrModule
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
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.MonoSmallStyle
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.PureWhite
import com.example.ui.theme.RoyalGold
import com.example.ui.theme.SecondarySilver
import com.example.ui.theme.SilverAccent
import com.example.ui.theme.SuccessGreen
import com.example.util.Formatters

@Composable
fun WalletScreen(
    profile: UserProfile?,
    wallet: WalletHoldings?,
    ledger: List<WalletLedgerEntry>,
    transactions: List<TransactionRecord>,
    bankAccounts: List<BankAccount>,
    rates: LiveMarketRates,
    appConfig: AppConfig,
    isBusy: Boolean,
    initialDialog: String? = null, // "ADD" or "WITHDRAW"
    walletUnlocked: Boolean = true,
    walletBiometricLock: Boolean = true,
    biometricAppAccess: Boolean = false,
    biometricCapabilityOverride: com.example.util.BiometricCapability? = null,
    onUnlockWalletWithBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onUnlockWalletWithDeviceCredential: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onUnlockWalletWithMpin: () -> Unit = {},
    onLockWallet: () -> Unit = {},
    onToggleWalletBiometricLock: (Boolean) -> Unit = {},
    onEnableAccountBiometrics: () -> Unit = {},
    onDeposit: (amount: Double, method: String, simulateFailure: Boolean) -> Unit,
    onWithdraw: (amount: Double, bank: BankAccount) -> Unit,
    onVerifyMpin: (String) -> Boolean,
    onVerifyBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onSetupMpin: () -> Unit,
    onOpenBankAccounts: () -> Unit,
    onOpenTransfer: () -> Unit,
    onOpenReceiveQr: () -> Unit = {},
    onOpenFullHistory: () -> Unit,
    onViewReceipt: (TransactionRecord) -> Unit,
) {
    val context = LocalContext.current
    val biometricCapability = biometricCapabilityOverride
        ?: remember { BiometricLoginManager.checkBiometricCapability(context) }
    val hasDeviceCredential = remember { BiometricAuthManager.hasDeviceCredentialFallback(context) }
    val w = wallet ?: WalletHoldings()
    val totalWalletInr = w.availableInr + w.pendingInr + w.lockedInr
    val goldVal = (w.gold24kGrams * rates.gold24kSellPerGram) +
        (w.gold22kGrams * rates.gold24kSellPerGram * 22.0 / 24.0) +
        (w.gold18kGrams * rates.gold24kSellPerGram * 18.0 / 24.0)
    val silverVal = w.silver999Grams * rates.silver999SellPerGram
    val totalPortfolio = totalWalletInr + goldVal + silverVal

    var activeTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember(initialDialog) { mutableStateOf(initialDialog == "ADD") }
    var showWithdrawDialog by remember(initialDialog) { mutableStateOf(initialDialog == "WITHDRAW") }
    var walletGateErr by remember { mutableStateOf<String?>(null) }

    val gateRequired = (profile?.biometricEnabled == true || biometricAppAccess) && walletBiometricLock && !walletUnlocked
    if (gateRequired) {
        BiometricAuthenticationScreen(
            scope = SensitiveActionScope.WALLET_VAULT,
            actionSummary = "INR Wallet & Bullion Holdings Vault",
            userDisplayName = profile?.fullName.orEmpty(),
            biometricCapability = biometricCapability,
            hasDeviceCredentialFallback = hasDeviceCredential && onUnlockWalletWithDeviceCredential != null,
            hasMpinFallback = !profile?.mpinHash.isNullOrBlank(),
            walletLockEnabled = walletBiometricLock,
            isWalletAuthorized = false,
            errorMessage = walletGateErr,
            showTopBar = false,
            allowScopeSwitching = false,
            autoPromptOnLaunch = true,
            rootTestTag = "wallet_biometric_gate_card",
            promptButtonTestTag = "wallet_biometric_unlock_btn",
            mpinInputTestTag = "wallet_mpin_unlock_input",
            mpinButtonTestTag = "wallet_mpin_unlock_btn",
            onAuthenticateBiometric = {
                walletGateErr = null
                onUnlockWalletWithBiometric?.invoke(
                    { },
                    { err -> walletGateErr = err },
                )
            },
            onAuthenticateDeviceCredential = if (onUnlockWalletWithDeviceCredential != null) {
                {
                    walletGateErr = null
                    onUnlockWalletWithDeviceCredential(
                        { },
                        { err -> walletGateErr = err },
                    )
                }
            } else {
                null
            },
            onVerifyMpin = onVerifyMpin,
            onMpinSuccess = {
                walletGateErr = null
                onUnlockWalletWithMpin()
            },
            onSetupMpin = onSetupMpin,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Digital Bullion Wallet", style = MaterialTheme.typography.headlineLarge, color = LightGold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (profile?.biometricEnabled == true && walletBiometricLock) {
                        IconButton(
                            onClick = onLockWallet,
                            modifier = Modifier.testTag("lock_wallet_btn"),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock Wallet Vault", tint = AmberOrange)
                        }
                    }
                    EnvironmentModeBadge(appConfig.paymentMode)
                }
            }
        }

        item {
            LuxuryCard(highlightGold = true) {
                Text("TOTAL PORTFOLIO VALUE (WALLET + BULLION)", style = MaterialTheme.typography.labelSmall, color = RoyalGold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(Formatters.formatInr(totalPortfolio), style = MaterialTheme.typography.displayMedium, color = PureWhite)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = GoldBorderSubtle)
                Spacer(modifier = Modifier.height(10.dp))
                KeyValueRow("Total Wallet Balance", Formatters.formatInr(totalWalletInr), valueColor = LightGold, boldValue = true)
                KeyValueRow("Available INR", Formatters.formatInr(w.availableInr), valueColor = SuccessGreen)
                KeyValueRow("Pending Settlement", Formatters.formatInr(w.pendingInr))
                KeyValueRow("Locked (Withdrawals)", Formatters.formatInr(w.lockedInr), valueColor = AmberOrange)
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("Gold Holdings (24K/22K/18K)", "${Formatters.formatGrams(w.totalGoldGrams)} (${Formatters.formatInr(goldVal)})", valueColor = LightGold)
                KeyValueRow("Silver Holdings (999)", "${Formatters.formatGrams(w.silver999Grams)} (${Formatters.formatInr(silverVal)})", valueColor = SilverAccent)

                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GoldPrimaryButton(
                        text = "Add Money",
                        icon = Icons.Default.Add,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).testTag("wallet_add_money_btn"),
                        onClick = { showAddDialog = true },
                    )
                    OrangeActionButton(
                        text = "Withdraw",
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).testTag("wallet_withdraw_btn"),
                        onClick = { showWithdrawDialog = true },
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GoldOutlinedButton(
                        text = "Bank Accounts (${bankAccounts.size})",
                        onClick = onOpenBankAccounts,
                        modifier = Modifier.weight(1f),
                    )
                    GoldOutlinedButton(
                        text = "Transfer",
                        onClick = onOpenTransfer,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                val walletAddr = BullionQrModule.formatWalletAddress(profile?.customUserId ?: "SWG000000")
                GoldOutlinedButton(
                    text = "Receive Gold QR ($walletAddr)",
                    icon = Icons.Default.QrCode2,
                    onClick = onOpenReceiveQr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_receive_qr_btn"),
                )
            }
        }

        // Biometric Wallet Security Card
        item {
            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (profile?.biometricEnabled == true) Icons.Default.VerifiedUser else Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (profile?.biometricEnabled == true) SuccessGreen else RoyalGold,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Biometric Wallet Access", style = MaterialTheme.typography.titleSmall, color = PureWhite)
                            Text(
                                text = if (profile?.biometricEnabled == true) {
                                    "Require Fingerprint / Face when entering Wallet"
                                } else {
                                    biometricCapability.label
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondarySilver,
                            )
                        }
                    }
                    if (profile?.biometricEnabled == true) {
                        Switch(
                            checked = walletBiometricLock,
                            onCheckedChange = onToggleWalletBiometricLock,
                            modifier = Modifier.testTag("wallet_biometric_lock_switch"),
                        )
                    } else if (biometricCapability.isUsable) {
                        TextButton(onClick = onEnableAccountBiometrics) {
                            Text("Activate", color = RoyalGold)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = ObsidianCard,
                    contentColor = RoyalGold,
                    modifier = Modifier.weight(1f),
                ) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Transactions") })
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Immutable Ledger") })
                }
                TextButton(onClick = onOpenFullHistory) {
                    Text("All Filters", color = AmberOrange)
                }
            }
        }

        if (activeTab == 0) {
            if (transactions.isEmpty()) {
                item {
                    LuxuryCard {
                        Text("No wallet transactions yet. Add money in Test Mode to start investing.", style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                    }
                }
            } else {
                items(transactions.take(30), key = { it.id }) { tx ->
                    LuxuryCard(onClick = { onViewReceipt(tx) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.category.replace("_", " "), style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                Text("${tx.txCode} • ${tx.paymentMethod} (${tx.mode})", style = MonoSmallStyle, color = SecondarySilver)
                                Text(Formatters.formatTimestamp(tx.createdAt), style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Formatters.formatInr(tx.totalAmount), style = MonoPriceStyle, color = LightGold)
                                StatusPill(text = tx.status, color = statusColorFor(tx.status))
                            }
                        }
                    }
                }
            }
        } else {
            if (ledger.isEmpty()) {
                item {
                    LuxuryCard {
                        Text("Ledger entries appear automatically for every atomic balance change.", style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                    }
                }
            } else {
                items(ledger, key = { it.id }) { entry ->
                    LuxuryCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${entry.entryType} (${entry.assetType})", style = MaterialTheme.typography.labelLarge, color = LightGold)
                                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = PureWhite)
                                Text("Ref: ${entry.referenceId} • ${Formatters.formatTimestamp(entry.createdAt)}", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (entry.deltaAmount >= 0) "+" else ""}${"%.4f".format(entry.deltaAmount)}",
                                    style = MonoSmallStyle,
                                    color = if (entry.deltaAmount >= 0) SuccessGreen else AmberOrange,
                                )
                                Text("Bal: ${"%.2f".format(entry.balanceAfter)}", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showAddDialog) {
        AddMoneyDialog(
            paymentMode = appConfig.paymentMode,
            gatewayName = appConfig.paymentGatewayName,
            onDismiss = { showAddDialog = false },
            onSubmit = { amt, method, simFail ->
                showAddDialog = false
                onDeposit(amt, method, simFail)
            },
        )
    }

    if (showWithdrawDialog) {
        WithdrawDialog(
            availableInr = w.availableInr,
            feeInr = appConfig.withdrawalFeeInr,
            bankAccounts = bankAccounts,
            hasMpin = !profile?.mpinHash.isNullOrBlank(),
            biometricEnabled = profile?.biometricEnabled == true,
            onVerifyMpin = onVerifyMpin,
            onVerifyBiometric = onVerifyBiometric,
            onSetupMpin = {
                showWithdrawDialog = false
                onSetupMpin()
            },
            onManageBanks = {
                showWithdrawDialog = false
                onOpenBankAccounts()
            },
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { amt, bank ->
                showWithdrawDialog = false
                onWithdraw(amt, bank)
            },
        )
    }
}

@Composable
private fun AddMoneyDialog(
    paymentMode: String,
    gatewayName: String,
    onDismiss: () -> Unit,
    onSubmit: (Double, String, Boolean) -> Unit,
) {
    var amountText by remember { mutableStateOf("5000") }
    var method by remember { mutableStateOf("UPI") }
    var simulateFailure by remember { mutableStateOf(false) }
    val methods = listOf("UPI", "PHONEPE", "GPAY", "BHIM", "UPI_QR", "DEBIT_CARD", "CREDIT_CARD", "NET_BANKING")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Money ($paymentMode MODE)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gateway: $gatewayName", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    methods.forEach { m ->
                        FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m.replace("_", " ")) })
                    }
                }
                if (paymentMode == "TEST") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = simulateFailure, onCheckedChange = { simulateFailure = it })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Gateway Failure (Test)", style = MaterialTheme.typography.bodySmall, color = AmberOrange)
                    }
                }
            }
        },
        confirmButton = {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            TextButton(
                enabled = amt > 0.0,
                onClick = { onSubmit(amt, method, simulateFailure) },
            ) { Text("Pay ${Formatters.formatInr(amt)}", color = RoyalGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WithdrawDialog(
    availableInr: Double,
    feeInr: Double,
    bankAccounts: List<BankAccount>,
    hasMpin: Boolean,
    biometricEnabled: Boolean,
    onVerifyMpin: (String) -> Boolean,
    onVerifyBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)?,
    onSetupMpin: () -> Unit,
    onManageBanks: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Double, BankAccount) -> Unit,
) {
    var amountText by remember { mutableStateOf("1000") }
    var selectedBankId by remember { mutableStateOf(bankAccounts.firstOrNull { it.isPrimary }?.id ?: bankAccounts.firstOrNull()?.id.orEmpty()) }
    var mpin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val amtVal = amountText.toDoubleOrNull() ?: 0.0
    val selectedBank = bankAccounts.firstOrNull { it.id == selectedBankId }
    val amountValid = selectedBank != null && hasMpin && amtVal > 0.0 && (amtVal + feeInr) <= availableInr

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Withdraw to Bank Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Available: ${Formatters.formatInr(availableInr)} • Fee: ${Formatters.formatInr(feeInr)}", style = MaterialTheme.typography.bodySmall, color = LightGold)
                if (bankAccounts.isEmpty()) {
                    Text("No verified bank account linked yet.", color = ErrorRed)
                    TextButton(onClick = onManageBanks) { Text("Add Bank Account", color = RoyalGold) }
                } else {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Withdrawal Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    bankAccounts.forEach { bank ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedBankId == bank.id, onClick = { selectedBankId = bank.id })
                            Text("${bank.bankName} (${bank.accountNumberMasked})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!hasMpin) {
                        Text("Please configure a 4-digit MPIN before withdrawing.", color = AmberOrange, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onSetupMpin) { Text("Setup MPIN Now", color = RoyalGold) }
                    } else {
                        OutlinedTextField(
                            value = mpin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) mpin = it },
                            label = { Text("Enter 4-Digit MPIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (biometricEnabled && onVerifyBiometric != null) {
                            GoldOutlinedButton(
                                text = "Authorize with Fingerprint / Face",
                                icon = Icons.Default.Fingerprint,
                                enabled = amountValid,
                                onClick = {
                                    val bank = selectedBank
                                    if (bank == null) {
                                        error = "Select a destination bank account."
                                    } else {
                                        onVerifyBiometric(
                                            { onConfirm(amtVal, bank) },
                                            { err -> error = err },
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("withdraw_biometric_confirm_btn"),
                            )
                        }
                    }
                    error?.takeIf { it.isNotBlank() }?.let { errText ->
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountValid,
                onClick = {
                    val bank = selectedBank
                    if (bank == null) {
                        error = "Select a destination bank account."
                    } else if (onVerifyMpin(mpin)) {
                        onConfirm(amtVal, bank)
                    } else {
                        error = "Invalid MPIN"
                    }
                },
            ) { Text("Request Payout", color = AmberOrange) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun BankAccountsScreen(
    bankAccounts: List<BankAccount>,
    hasMpin: Boolean,
    biometricEnabled: Boolean = false,
    onVerifyMpin: (String) -> Boolean,
    onVerifyBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onAddBank: (holder: String, bankName: String, accNo: String, ifsc: String, isPrimary: Boolean) -> Unit,
    onSetPrimary: (String) -> Unit,
    onRemoveBank: (String) -> Unit,
    onBack: () -> Unit,
) {
    var holderName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accNo by remember { mutableStateOf("") }
    var confirmAccNo by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    var makePrimary by remember { mutableStateOf(bankAccounts.isEmpty()) }
    var formError by remember { mutableStateOf<String?>(null) }

    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var mpinCheck by remember { mutableStateOf("") }
    var mpinErr by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = DeepBlack,
        topBar = { SonaWaneTopBar(title = "Linked Bank Accounts", subtitle = "Verified Payout Destinations", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(bankAccounts, key = { it.id }) { bank ->
                LuxuryCard(highlightGold = bank.isPrimary) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(bank.bankName, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                                Text(bank.accountHolderName, style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                                Text("${bank.accountNumberMasked} • IFSC: ${bank.ifscCode}", style = MonoSmallStyle, color = LightGold)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            StatusPill(text = bank.verificationStatus, color = statusColorFor(bank.verificationStatus))
                            if (bank.isPrimary) {
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusPill(text = "PRIMARY", color = RoyalGold, icon = Icons.Default.CheckCircle)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        if (!bank.isPrimary) {
                            TextButton(onClick = { onSetPrimary(bank.id) }) {
                                Text("Set as Primary", color = RoyalGold)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        IconButton(onClick = { pendingDeleteId = bank.id }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove bank account", tint = ErrorRed)
                        }
                    }
                }
            }

            item {
                LuxuryCard {
                    Text("Add New Bank Account", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(value = holderName, onValueChange = { holderName = it }, label = { Text("Account Holder Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name (e.g., HDFC Bank)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = accNo, onValueChange = { accNo = it.filter { c -> c.isDigit() } }, label = { Text("Account Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = confirmAccNo, onValueChange = { confirmAccNo = it.filter { c -> c.isDigit() } }, label = { Text("Confirm Account Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = ifsc, onValueChange = { if (it.length <= 11) ifsc = it.uppercase() }, label = { Text("11-Character IFSC Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = makePrimary, onCheckedChange = { makePrimary = it })
                        Text("Set as Primary Payout Account", color = SecondarySilver, style = MaterialTheme.typography.bodySmall)
                    }
                    formError?.takeIf { it.isNotBlank() }?.let { errText ->
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GoldPrimaryButton(
                        text = "Verify & Link Bank Account",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            when {
                                holderName.isBlank() || bankName.isBlank() -> formError = "Enter holder and bank name"
                                accNo.length < 8 -> formError = "Enter a valid account number"
                                accNo != confirmAccNo -> formError = "Account numbers do not match"
                                ifsc.length != 11 -> formError = "IFSC code must be 11 characters"
                                else -> {
                                    formError = null
                                    onAddBank(holderName, bankName, accNo, ifsc, makePrimary)
                                    holderName = ""; bankName = ""; accNo = ""; confirmAccNo = ""; ifsc = ""
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { deleteBankId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Verify Removal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasMpin) "Enter your 4-digit MPIN or verify Biometrics to remove this bank account." else "Confirm removing this bank account.")
                    if (hasMpin) {
                        OutlinedTextField(
                            value = mpinCheck,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) mpinCheck = it },
                            label = { Text("MPIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (biometricEnabled && onVerifyBiometric != null) {
                            GoldOutlinedButton(
                                text = "Use Fingerprint / Face",
                                onClick = {
                                    onVerifyBiometric(
                                        {
                                            onRemoveBank(deleteBankId)
                                            pendingDeleteId = null
                                            mpinCheck = ""
                                            mpinErr = null
                                        },
                                        { err -> mpinErr = err },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    mpinErr?.takeIf { it.isNotBlank() }?.let { errText ->
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!hasMpin || onVerifyMpin(mpinCheck)) {
                        onRemoveBank(deleteBankId)
                        pendingDeleteId = null
                        mpinCheck = ""
                        mpinErr = null
                    } else {
                        mpinErr = "Invalid MPIN"
                    }
                }) { Text("Remove", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
        )
    }
}
