package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.BankAccount
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPoint
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.PriceAlert
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.WalletHoldings
import com.example.ui.components.EnvironmentModeBadge
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPriceHistoryChart
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LuxuryCard
import com.example.ui.components.MarketTrendsDashboard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
import com.example.ui.components.VicoBullionTrendDashboard
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
import com.example.util.BiometricAuthManager
import com.example.util.Formatters

@Composable
fun BullionTradeScreen(
    isGold: Boolean,
    initialSellTab: Boolean = false,
    profile: UserProfile?,
    wallet: WalletHoldings?,
    rates: LiveMarketRates,
    appConfig: AppConfig,
    bankAccounts: List<BankAccount>,
    transactions: List<TransactionRecord>,
    isBusy: Boolean,
    requireBiometricForTrades: Boolean = true,
    onBiometricTradeAuthorized: (String) -> Unit = {},
    onBuy: (purity: String, grams: Double, ratePerGram: Double, base: Double, gst: Double, charges: Double, total: Double, method: String) -> Unit,
    onSell: (purity: String, grams: Double, sellRatePerGram: Double, deductions: Double, creditToWallet: Boolean, bank: BankAccount?) -> Unit,
    onVerifyMpin: (String) -> Boolean,
    onVerifyBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onSetupMpin: () -> Unit,
    onViewReceipt: (TransactionRecord) -> Unit,
) {
    val context = LocalContext.current
    val biometricCapability = remember { BiometricAuthManager.checkCapability(context) }
    var selectedTab by remember(initialSellTab) { mutableIntStateOf(if (initialSellTab) 1 else 0) }
    var purity by remember(isGold) { mutableStateOf(if (isGold) "24K" else "999") }
    var buyByRupees by remember { mutableStateOf(true) }
    var amountInput by remember { mutableStateOf("1000") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var sellCreditToWallet by remember { mutableStateOf(true) }
    var selectedBankId by remember { mutableStateOf(bankAccounts.firstOrNull { it.isPrimary }?.id ?: bankAccounts.firstOrNull()?.id.orEmpty()) }
    var showConfirmOrder by remember { mutableStateOf(false) }
    var buyMpinInput by remember { mutableStateOf("") }
    var buyAuthError by remember { mutableStateOf<String?>(null) }
    var showMpinDialog by remember { mutableStateOf(false) }
    var mpinInput by remember { mutableStateOf("") }
    var mpinError by remember { mutableStateOf<String?>(null) }

    val w = wallet ?: WalletHoldings()
    val buyRatePerGram = when {
        !isGold -> rates.silver999BuyPerGram
        purity == "22K" -> rates.gold22kBuyPerGram
        purity == "18K" -> rates.gold18kBuyPerGram
        else -> rates.gold24kBuyPerGram
    }
    val sellRatePerGram = when {
        !isGold -> rates.silver999SellPerGram
        purity == "22K" -> rates.gold24kSellPerGram * (22.0 / 24.0)
        purity == "18K" -> rates.gold24kSellPerGram * (18.0 / 24.0)
        else -> rates.gold24kSellPerGram
    }
    val ownedGrams = when {
        !isGold -> w.silver999Grams
        purity == "22K" -> w.gold22kGrams
        purity == "18K" -> w.gold18kGrams
        else -> w.gold24kGrams
    }

    val numericInput = amountInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val gstRate = appConfig.gstPercent / 100.0

    // Buy calculation: if user enters ₹1,000 total payable, base = total / (1 + gstRate)
    val buyTotalAmount = if (buyByRupees) numericInput else (numericInput * buyRatePerGram) * (1.0 + gstRate)
    val buyBaseAmount = if (buyByRupees) numericInput / (1.0 + gstRate) else numericInput * buyRatePerGram
    val buyGstAmount = buyTotalAmount - buyBaseAmount
    val buyGrams = if (buyRatePerGram > 0.0) buyBaseAmount / buyRatePerGram else 0.0

    // Sell calculation
    val sellGrams = if (buyByRupees) {
        if (sellRatePerGram > 0.0) numericInput / sellRatePerGram else 0.0
    } else numericInput
    val sellGross = sellGrams * sellRatePerGram
    val sellDeductions = 0.0
    val sellNet = (sellGross - sellDeductions).coerceAtLeast(0.0)

    val paymentMethods = listOf("WALLET", "UPI", "PHONEPE", "GPAY", "BHIM", "UPI_QR", "DEBIT_CARD", "CREDIT_CARD", "NET_BANKING")
    val metalHistory = transactions.filter {
        if (isGold) it.category == "GOLD_BUY" || it.category == "GOLD_SELL"
        else it.category == "SILVER_BUY" || it.category == "SILVER_SELL"
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
                Text(
                    text = if (isGold) "Digital Gold Vault" else "999 Pure Silver Vault",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (isGold) LightGold else SilverAccent,
                )
                EnvironmentModeBadge(appConfig.paymentMode)
            }
        }

        // Visual Bullion Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, if (isGold) RoyalGold.copy(alpha = 0.5f) else SilverAccent.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            ) {
                Image(
                    painter = painterResource(id = if (isGold) R.drawable.img_swg_gold_bullion else R.drawable.img_swg_silver_bullion),
                    contentDescription = if (isGold) "24K Gold Bullion" else "999 Silver Bullion",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(DeepBlack.copy(alpha = 0.90f), DeepBlack.copy(alpha = 0.3f))))
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Text(
                            text = if (isGold) "Your Gold Holdings ($purity)" else "Your 999 Silver Holdings",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isGold) RoyalGold else SilverAccent,
                        )
                        Text(Formatters.formatGrams(ownedGrams), style = MaterialTheme.typography.headlineLarge, color = PureWhite)
                        Text(
                            text = "Value: ${Formatters.formatInr(ownedGrams * sellRatePerGram)}",
                            style = MonoSmallStyle,
                            color = LightGold,
                        )
                    }
                }
            }
        }

        // Buy / Sell Tabs
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ObsidianCard,
                contentColor = RoyalGold,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (isGold) "Buy Gold" else "Buy Silver", fontWeight = FontWeight.Bold) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(if (isGold) "Sell Gold" else "Sell Silver", fontWeight = FontWeight.Bold) },
                )
            }
        }

        // Purity Selector (Gold only)
        if (isGold) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("24K", "22K", "18K").forEach { p ->
                        FilterChip(
                            selected = purity == p,
                            onClick = { purity = p },
                            label = { Text("$p Gold") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalGold,
                                selectedLabelColor = DeepBlack,
                            ),
                        )
                    }
                }
            }
        }

        // Order Builder Card
        item {
            LuxuryCard(highlightGold = isGold) {
                val activeRate = if (selectedTab == 0) buyRatePerGram else sellRatePerGram
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedTab == 0) "Verified Buy Rate ($purity)" else "Verified Sell Rate ($purity)",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                    Text(
                        text = if (activeRate > 0.0) "${Formatters.formatInr(activeRate)} / g" else "Feed offline",
                        style = MonoPriceStyle,
                        color = if (isGold) LightGold else SilverAccent,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = buyByRupees,
                        onClick = {
                            buyByRupees = true
                            amountInput = "1000"
                        },
                        label = { Text("By Amount (₹)") },
                    )
                    FilterChip(
                        selected = !buyByRupees,
                        onClick = {
                            buyByRupees = false
                            amountInput = "1.0"
                        },
                        label = { Text("By Grams (g)") },
                    )
                    if (selectedTab == 1 && ownedGrams > 0.0) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                buyByRupees = false
                                amountInput = "%.4f".format(ownedGrams)
                            },
                            label = { Text("Sell Max") },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(if (buyByRupees) "Enter Amount (₹)" else "Enter Quantity (grams)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bullion_amount_input"),
                )

                if (selectedTab == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presets = if (buyByRupees) listOf("500", "1000", "5000", "10000") else listOf("0.5", "1.0", "5.0", "10.0")
                        presets.forEach { p ->
                            FilterChip(selected = amountInput == p, onClick = { amountInput = p }, label = { Text(if (buyByRupees) "₹$p" else "${p}g") })
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = GoldBorderSubtle)
                    Spacer(modifier = Modifier.height(10.dp))
                    KeyValueRow("Purity", purity)
                    KeyValueRow("Estimated Quantity", Formatters.formatGrams(buyGrams), valueColor = LightGold, boldValue = true)
                    KeyValueRow("Base Value", Formatters.formatInr(buyBaseAmount))
                    KeyValueRow("GST (${appConfig.gstPercent}%)", Formatters.formatInr(buyGstAmount))
                    KeyValueRow("Provider Charges", Formatters.formatInr(0.0))
                    KeyValueRow("Total Payable", Formatters.formatInr(buyTotalAmount), valueColor = PureWhite, boldValue = true)

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Select Payment Method", style = MaterialTheme.typography.labelLarge, color = PureWhite)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        paymentMethods.forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(method.replace("_", " ")) },
                            )
                        }
                    }
                    if (paymentMethod == "WALLET") {
                        Text(
                            text = "Available Wallet Balance: ${Formatters.formatInr(w.availableInr)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (w.availableInr >= buyTotalAmount) SuccessGreen else ErrorRed,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    GoldPrimaryButton(
                        text = "Proceed to Buy (${Formatters.formatInr(buyTotalAmount)})",
                        enabled = !isBusy && buyGrams > 0.0 && buyRatePerGram > 0.0 && !appConfig.maintenanceMode,
                        modifier = Modifier.fillMaxWidth().testTag("proceed_buy_button"),
                        onClick = { showConfirmOrder = true },
                    )
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = GoldBorderSubtle)
                    Spacer(modifier = Modifier.height(10.dp))
                    KeyValueRow("Available $purity Balance", Formatters.formatGrams(ownedGrams))
                    KeyValueRow("Quantity to Sell", Formatters.formatGrams(sellGrams), valueColor = LightGold, boldValue = true)
                    KeyValueRow("Gross Proceeds", Formatters.formatInr(sellGross))
                    KeyValueRow("Deductions", Formatters.formatInr(sellDeductions))
                    KeyValueRow("Net Receivable", Formatters.formatInr(sellNet), valueColor = SuccessGreen, boldValue = true)

                    if (sellGrams > ownedGrams + 1e-6) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Quantity exceeds available $purity balance.", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Credit Proceeds To", style = MaterialTheme.typography.labelLarge, color = PureWhite)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sellCreditToWallet, onClick = { sellCreditToWallet = true })
                        Text("SONA-WANE Wallet (Instant)", color = PureWhite, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !sellCreditToWallet, onClick = { sellCreditToWallet = false })
                        Text("Verified Bank Account Payout", color = PureWhite, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (!sellCreditToWallet) {
                        if (bankAccounts.isEmpty()) {
                            Text("Add a verified bank account in Wallet -> Bank Accounts first.", color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                        } else {
                            bankAccounts.forEach { bank ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedBankId == bank.id, onClick = { selectedBankId = bank.id })
                                    Text("${bank.bankName} (${bank.accountNumberMasked})", color = SecondarySilver, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OrangeActionButton(
                        text = "Confirm Sale (${Formatters.formatInr(sellNet)})",
                        enabled = !isBusy && sellGrams > 0.0 && sellGrams <= ownedGrams + 1e-6 && sellRatePerGram > 0.0 && (sellCreditToWallet || bankAccounts.any { it.id == selectedBankId }),
                        modifier = Modifier.fillMaxWidth().testTag("proceed_sell_button"),
                        onClick = {
                            if (profile?.mpinHash.isNullOrBlank()) {
                                onSetupMpin()
                            } else {
                                showMpinDialog = true
                            }
                        },
                    )
                }
            }
        }

        // Metal-specific recent transactions
        item {
            Text(
                text = if (isGold) "Recent Gold Transactions" else "Recent Silver Transactions",
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
            )
        }
        if (metalHistory.isEmpty()) {
            item {
                LuxuryCard {
                    Text("No ${if (isGold) "gold" else "silver"} trades recorded yet.", style = MaterialTheme.typography.bodyMedium, color = SecondarySilver)
                }
            }
        } else {
            items(metalHistory.take(15), key = { it.id }) { tx ->
                LuxuryCard(onClick = { onViewReceipt(tx) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${tx.category.replace("_", " ")} (${tx.metalPurity})", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                            Text("${Formatters.formatGrams(tx.grams)} • ${tx.txCode}", style = MonoSmallStyle, color = SecondarySilver)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Formatters.formatInr(tx.totalAmount), style = MonoSmallStyle, color = LightGold)
                            StatusPill(text = tx.status, color = com.example.ui.components.statusColorFor(tx.status))
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showConfirmOrder) {
        val hardwareReady = biometricCapability.isUsable && onVerifyBiometric != null
        val biometricEnforced = profile?.biometricEnabled == true && requireBiometricForTrades && hardwareReady
        AlertDialog(
            onDismissRequest = {
                showConfirmOrder = false
                buyAuthError = null
                buyMpinInput = ""
            },
            title = { Text("Confirm ${if (isGold) "Gold" else "Silver"} Order (${appConfig.paymentMode})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Purity: $purity")
                    Text("Quantity: ${Formatters.formatGrams(buyGrams)}")
                    Text("Rate: ${Formatters.formatInr(buyRatePerGram)}/g")
                    Text("Base Amount: ${Formatters.formatInr(buyBaseAmount)}")
                    Text("GST (${appConfig.gstPercent}%): ${Formatters.formatInr(buyGstAmount)}")
                    Text("Final Payable: ${Formatters.formatInr(buyTotalAmount)}", fontWeight = FontWeight.Bold)
                    Text("Payment Method: ${paymentMethod.replace("_", " ")}")
                    if (appConfig.paymentMode == "TEST") {
                        Text("TEST MODE: Sandbox authorization — no real money charged.", color = AmberOrange, style = MaterialTheme.typography.labelSmall)
                    }
                    if (biometricEnforced || hardwareReady) {
                        HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = if (biometricEnforced) "Biometric Trade Confirmation Required" else "Biometric Trade Confirmation Available",
                            style = MaterialTheme.typography.labelMedium,
                            color = LightGold,
                        )
                        GoldPrimaryButton(
                            text = "Confirm Trade with Fingerprint / Face",
                            icon = Icons.Default.Fingerprint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("buy_biometric_confirm_btn"),
                            onClick = {
                                buyAuthError = null
                                onVerifyBiometric!!.invoke(
                                    {
                                        showConfirmOrder = false
                                        buyMpinInput = ""
                                        onBiometricTradeAuthorized("BUY_${if (isGold) "GOLD" else "SILVER"}_${purity}_BIOMETRIC")
                                        onBuy(purity, buyGrams, buyRatePerGram, buyBaseAmount, buyGstAmount, 0.0, buyTotalAmount, paymentMethod)
                                    },
                                    { err -> buyAuthError = err },
                                )
                            },
                        )
                        if (biometricEnforced && !profile?.mpinHash.isNullOrBlank()) {
                            OutlinedTextField(
                                value = buyMpinInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) buyMpinInput = it },
                                label = { Text("Or enter 4-Digit MPIN fallback") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("buy_mpin_confirm_input"),
                            )
                        }
                    }
                    if (!buyAuthError.isNullOrBlank()) {
                        Text(buyAuthError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (biometricEnforced && !profile?.mpinHash.isNullOrBlank()) {
                    TextButton(
                        enabled = buyMpinInput.length == 4,
                        onClick = {
                            if (onVerifyMpin(buyMpinInput)) {
                                showConfirmOrder = false
                                buyMpinInput = ""
                                buyAuthError = null
                                onBuy(purity, buyGrams, buyRatePerGram, buyBaseAmount, buyGstAmount, 0.0, buyTotalAmount, paymentMethod)
                            } else {
                                buyAuthError = "Incorrect MPIN"
                            }
                        },
                    ) { Text("Verify via MPIN", color = RoyalGold) }
                } else if (!biometricEnforced) {
                    TextButton(onClick = {
                        showConfirmOrder = false
                        buyAuthError = null
                        onBuy(purity, buyGrams, buyRatePerGram, buyBaseAmount, buyGstAmount, 0.0, buyTotalAmount, paymentMethod)
                    }) { Text("Authorize & Pay", color = RoyalGold) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmOrder = false
                    buyAuthError = null
                    buyMpinInput = ""
                }) { Text("Cancel") }
            },
        )
    }

    if (showMpinDialog) {
        val biometricAllowed = profile?.biometricEnabled == true && requireBiometricForTrades && onVerifyBiometric != null
        AlertDialog(
            onDismissRequest = { showMpinDialog = false },
            title = { Text("Authorize Sale (${Formatters.formatGrams(sellGrams)})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Verify with your 4-digit MPIN${if (biometricAllowed || biometricCapability.isUsable) " or Fingerprint / Face" else ""} to authorize selling $purity bullion.")
                    OutlinedTextField(
                        value = mpinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) mpinInput = it },
                        label = { Text("4-Digit MPIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if ((biometricAllowed || biometricCapability.isUsable) && onVerifyBiometric != null) {
                        GoldPrimaryButton(
                            text = "Confirm Sale with Fingerprint / Face",
                            icon = Icons.Default.Fingerprint,
                            onClick = {
                                onVerifyBiometric(
                                    {
                                        showMpinDialog = false
                                        mpinInput = ""
                                        mpinError = null
                                        onBiometricTradeAuthorized("SELL_${if (isGold) "GOLD" else "SILVER"}_${purity}_BIOMETRIC")
                                        val bank = bankAccounts.firstOrNull { it.id == selectedBankId }
                                        onSell(purity, sellGrams, sellRatePerGram, sellDeductions, sellCreditToWallet, bank)
                                    },
                                    { err -> mpinError = err },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sell_biometric_confirm_btn"),
                        )
                    }
                    if (!mpinError.isNullOrBlank()) {
                        Text(mpinError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onVerifyMpin(mpinInput)) {
                        showMpinDialog = false
                        mpinInput = ""
                        mpinError = null
                        val bank = bankAccounts.firstOrNull { it.id == selectedBankId }
                        onSell(purity, sellGrams, sellRatePerGram, sellDeductions, sellCreditToWallet, bank)
                    } else {
                        mpinError = "Incorrect MPIN"
                    }
                }) { Text("Confirm Sale", color = AmberOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showMpinDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun MarketScreen(
    rates: LiveMarketRates,
    chartPoints: List<MarketPoint>,
    dualTrendSeries: DualMetalTrendSeries = DualMetalTrendSeries(),
    marketHistory30d: List<MarketPriceHistoryEntry> = emptyList(),
    chartLoading: Boolean = false,
    marketHistoryError: String? = null,
    alerts: List<PriceAlert>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadChart: (Boolean, String) -> Unit,
    onSelectTrendRange: (String) -> Unit = {},
    onSyncFirestoreHistory: () -> Unit = onRefresh,
    onCreateAlert: (metal: String, target: Double, direction: String, frequency: String, notes: String) -> Unit,
    onToggleAlert: (PriceAlert, Boolean) -> Unit,
    onDeleteAlert: (String) -> Unit,
    onOpenAlertsCenter: () -> Unit,
) {
    var alertMetal by remember { mutableStateOf("GOLD_24K") }
    var alertTarget by remember { mutableStateOf("") }
    var alertDirection by remember { mutableStateOf("BELOW") }
    var alertFrequency by remember { mutableStateOf("ONCE") }
    var alertNotes by remember { mutableStateOf("") }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = "Live Bullion Market",
                subtitle = if (rates.isLive) "Verified Spot Feed" else "Delayed / Cached",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenAlertsCenter) {
                        Icon(Icons.Default.AddAlert, contentDescription = "Manage all alerts", tint = AmberOrange)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh rates", tint = RoyalGold)
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
            item {
                LuxuryCard(highlightGold = true) {
                    Text("Bullion Spot Summary", style = MaterialTheme.typography.titleMedium, color = LightGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    KeyValueRow("24K Gold (Buy / Sell)", "${Formatters.formatInr(rates.gold24kBuyPerGram)} / ${Formatters.formatInr(rates.gold24kSellPerGram)}", valueColor = LightGold)
                    KeyValueRow("22K Gold / g", Formatters.formatInr(rates.gold22kBuyPerGram))
                    KeyValueRow("18K Gold / g", Formatters.formatInr(rates.gold18kBuyPerGram))
                    KeyValueRow("999 Silver (Buy / Sell)", "${Formatters.formatInr(rates.silver999BuyPerGram)} / ${Formatters.formatInr(rates.silver999SellPerGram)}", valueColor = SilverAccent)
                    KeyValueRow("Day High / Low (Gold)", "${Formatters.formatInr(rates.goldDayHigh)} / ${Formatters.formatInr(rates.goldDayLow)}")
                    KeyValueRow("Data Source", rates.sourceName)
                    KeyValueRow("Last Updated", Formatters.formatEpoch(rates.lastUpdatedEpochMs))
                }
            }

            item {
                GoldPriceHistoryChart(
                    historyEntries = marketHistory30d,
                    isLoading = chartLoading,
                    errorMessage = marketHistoryError,
                    onRefreshFromFirestore = onSyncFirestoreHistory,
                )
            }

            item {
                MarketTrendsDashboard(
                    historyEntries = marketHistory30d,
                    rates = rates,
                    isLoading = chartLoading,
                    errorMessage = marketHistoryError,
                    onSyncHistory = onSyncFirestoreHistory,
                )
            }

            item {
                VicoBullionTrendDashboard(
                    trendSeries = dualTrendSeries,
                    rates = rates,
                    isLoading = chartLoading,
                    rangeOptions = listOf("1W", "30D", "3M", "6M", "1Y", "ALL"),
                    onSelectRange = { rng ->
                        onSelectTrendRange(rng)
                        onLoadChart(true, rng)
                    },
                    onRefreshLive = onRefresh,
                )
            }

            item {
                LuxuryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddAlert, contentDescription = null, tint = AmberOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Price Alert", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                        }
                        TextButton(onClick = onOpenAlertsCenter) {
                            Text("Manage All (${alerts.size})", color = RoyalGold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999").forEach { m ->
                            FilterChip(selected = alertMetal == m, onClick = { alertMetal = m }, label = { Text(m.replace("_", " ")) })
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = alertDirection == "BELOW", onClick = { alertDirection = "BELOW" }, label = { Text("Drops Below") })
                        FilterChip(selected = alertDirection == "ABOVE", onClick = { alertDirection = "ABOVE" }, label = { Text("Rises Above") })
                        FilterChip(selected = alertDirection == "CROSSES", onClick = { alertDirection = "CROSSES" }, label = { Text("Crosses") })
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("ONCE" to "Once", "EVERY_HOUR" to "Hourly", "DAILY" to "Daily", "ALWAYS" to "Always").forEach { (freq, label) ->
                            FilterChip(selected = alertFrequency == freq, onClick = { alertFrequency = freq }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(
                        value = alertTarget,
                        onValueChange = { alertTarget = it },
                        label = { Text("Target Price per gram (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = alertNotes,
                        onValueChange = { if (it.length <= 150) alertNotes = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GoldPrimaryButton(
                        text = "Arm Price Alert",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (alertTarget.toDoubleOrNull() ?: 0.0) > 0.0,
                        onClick = {
                            alertTarget.toDoubleOrNull()?.let {
                                onCreateAlert(alertMetal, it, alertDirection, alertFrequency, alertNotes)
                                alertTarget = ""
                                alertNotes = ""
                            }
                        },
                    )
                }
            }

            items(alerts, key = { it.id }) { alert ->
                LuxuryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(alert.metal.replace("_", " "), style = MaterialTheme.typography.titleMedium, color = LightGold)
                                StatusPill(text = alert.frequency.replace("_", " "), color = RoyalGold)
                            }
                            Text("${alert.direction} ${Formatters.formatInr(alert.targetPriceInr)} / g", style = MonoSmallStyle, color = PureWhite)
                            if (alert.triggerCount > 0) {
                                Text("Triggered ${alert.triggerCount}x", style = MaterialTheme.typography.labelSmall, color = SuccessGreen)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = alert.isActive,
                                onCheckedChange = { onToggleAlert(alert, it) },
                            )
                            IconButton(onClick = { onDeleteAlert(alert.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete price alert", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
