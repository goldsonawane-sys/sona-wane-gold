package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BankAccount
import com.example.data.model.LiveMarketRates
import com.example.data.model.WalletHoldings
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ElevatedObsidian
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
import com.example.util.BiometricLoginManager
import com.example.util.Formatters
import java.util.Locale
import kotlin.math.round

enum class GoldTradeSide(val label: String) {
    BUY("Buy Gold"),
    SELL("Sell Gold"),
}

enum class GoldTradeInputFocus {
    AMOUNT,
    WEIGHT,
}

data class GoldTradeQuote(
    val side: GoldTradeSide,
    val purity: String,
    val grams: Double,
    val ratePerGram: Double,
    val baseAmountInr: Double,
    val gstAmountInr: Double,
    val totalAmountInr: Double,
    val ownedGramsForPurity: Double,
    val exceedsHoldings: Boolean,
    val insufficientWallet: Boolean,
)

/**
 * Pure calculation & validation helper keeping Amount (₹) and Weight (grams)
 * bidirectionally synchronized for Buy and Sell digital gold trades.
 */
object GoldTradingPanelCalculator {

    fun buyRateForPurity(rates: LiveMarketRates, purity: String): Double {
        return when (purity) {
            "22K" -> if (rates.gold22kBuyPerGram > 0.0) rates.gold22kBuyPerGram else rates.gold24kBuyPerGram * (22.0 / 24.0)
            "18K" -> if (rates.gold18kBuyPerGram > 0.0) rates.gold18kBuyPerGram else rates.gold24kBuyPerGram * (18.0 / 24.0)
            else -> rates.gold24kBuyPerGram
        }
    }

    fun sellRateForPurity(rates: LiveMarketRates, purity: String): Double {
        return when (purity) {
            "22K" -> rates.gold24kSellPerGram * (22.0 / 24.0)
            "18K" -> rates.gold24kSellPerGram * (18.0 / 24.0)
            else -> rates.gold24kSellPerGram
        }
    }

    fun ownedGramsForPurity(wallet: WalletHoldings?, purity: String): Double {
        val w = wallet ?: return 0.0
        return when (purity) {
            "22K" -> w.gold22kGrams
            "18K" -> w.gold18kGrams
            else -> w.gold24kGrams
        }
    }

    fun weightFromAmount(
        amountInr: Double,
        ratePerGram: Double,
        gstPercent: Double,
        side: GoldTradeSide,
    ): Double {
        if (amountInr <= 0.0 || ratePerGram <= 0.0) return 0.0
        val taxMultiplier = if (side == GoldTradeSide.BUY) 1.0 + (gstPercent.coerceAtLeast(0.0) / 100.0) else 1.0
        val base = amountInr / taxMultiplier
        return base / ratePerGram
    }

    fun amountFromWeight(
        grams: Double,
        ratePerGram: Double,
        gstPercent: Double,
        side: GoldTradeSide,
    ): Double {
        if (grams <= 0.0 || ratePerGram <= 0.0) return 0.0
        val base = grams * ratePerGram
        val taxMultiplier = if (side == GoldTradeSide.BUY) 1.0 + (gstPercent.coerceAtLeast(0.0) / 100.0) else 1.0
        return base * taxMultiplier
    }

    fun buildQuote(
        side: GoldTradeSide,
        purity: String,
        rawAmountText: String,
        rawWeightText: String,
        lastEdited: GoldTradeInputFocus,
        rates: LiveMarketRates,
        wallet: WalletHoldings?,
        gstPercent: Double,
        paymentMethod: String,
    ): GoldTradeQuote {
        val rate = if (side == GoldTradeSide.BUY) {
            buyRateForPurity(rates, purity)
        } else {
            sellRateForPurity(rates, purity)
        }
        val owned = ownedGramsForPurity(wallet, purity)
        val taxRate = if (side == GoldTradeSide.BUY) gstPercent.coerceAtLeast(0.0) / 100.0 else 0.0

        val grams: Double
        val base: Double
        val gst: Double
        val total: Double

        if (lastEdited == GoldTradeInputFocus.AMOUNT) {
            val amount = rawAmountText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
            total = amount
            base = if (side == GoldTradeSide.BUY) amount / (1.0 + taxRate) else amount
            gst = (total - base).coerceAtLeast(0.0)
            grams = if (rate > 0.0) base / rate else 0.0
        } else {
            grams = rawWeightText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
            base = grams * rate
            gst = if (side == GoldTradeSide.BUY) base * taxRate else 0.0
            total = base + gst
        }

        val exceedsHoldings = side == GoldTradeSide.SELL && grams > owned + 1e-6
        val insufficientWallet = side == GoldTradeSide.BUY &&
            paymentMethod == "WALLET" &&
            total > (wallet?.availableInr ?: 0.0) + 0.01

        return GoldTradeQuote(
            side = side,
            purity = purity,
            grams = grams,
            ratePerGram = rate,
            baseAmountInr = base,
            gstAmountInr = gst,
            totalAmountInr = total,
            ownedGramsForPurity = owned,
            exceedsHoldings = exceedsHoldings,
            insufficientWallet = insufficientWallet,
        )
    }

    fun formatWeightInput(grams: Double): String {
        if (grams <= 0.0) return ""
        return String.format(Locale.US, "%.4f", round(grams * 10_000.0) / 10_000.0)
    }

    fun formatAmountInput(amountInr: Double): String {
        if (amountInr <= 0.0) return ""
        return String.format(Locale.US, "%.2f", round(amountInr * 100.0) / 100.0)
    }
}

/**
 * Clean, interactive panel for buying and selling digital gold (24K/22K/18K)
 * with synchronized Amount (₹) and Weight (grams) inputs and optional Biometric/MPIN confirmation.
 */
@Composable
fun GoldTradingPanel(
    wallet: WalletHoldings?,
    rates: LiveMarketRates,
    gstPercent: Double = 3.0,
    paymentMode: String = "TEST",
    isBusy: Boolean = false,
    maintenanceMode: Boolean = false,
    initialSide: GoldTradeSide = GoldTradeSide.BUY,
    bankAccounts: List<BankAccount> = emptyList(),
    biometricRequired: Boolean = false,
    hasMpin: Boolean = false,
    onAuthenticateBiometric: ((title: String, subtitle: String, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)? = null,
    onVerifyMpin: ((String) -> Boolean)? = null,
    onSetupMpin: (() -> Unit)? = null,
    onBuyGold: (purity: String, grams: Double, ratePerGram: Double, baseAmount: Double, gstAmount: Double, chargesAmount: Double, totalAmount: Double, paymentMethod: String) -> Unit,
    onSellGold: (purity: String, grams: Double, sellRatePerGram: Double, deductionsInr: Double, creditToWallet: Boolean, bankAccount: BankAccount?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val biometricCapability = remember(context) { BiometricLoginManager.checkBiometricCapability(context) }

    var activeSide by remember(initialSide) { mutableStateOf(initialSide) }
    var purity by remember { mutableStateOf("24K") }
    var lastEdited by remember { mutableStateOf(GoldTradeInputFocus.AMOUNT) }

    val initialRate = GoldTradingPanelCalculator.buyRateForPurity(rates, "24K")
    var amountText by remember { mutableStateOf("1000") }
    var weightText by remember {
        mutableStateOf(
            GoldTradingPanelCalculator.formatWeightInput(
                GoldTradingPanelCalculator.weightFromAmount(1000.0, initialRate, gstPercent, GoldTradeSide.BUY)
            )
        )
    }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var sellCreditToWallet by remember { mutableStateOf(true) }
    var selectedBankId by remember(bankAccounts) {
        mutableStateOf(bankAccounts.firstOrNull { it.isPrimary }?.id ?: bankAccounts.firstOrNull()?.id.orEmpty())
    }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var mpinInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }

    val quote = remember(activeSide, purity, amountText, weightText, lastEdited, rates, wallet, gstPercent, paymentMethod) {
        GoldTradingPanelCalculator.buildQuote(
            side = activeSide,
            purity = purity,
            rawAmountText = amountText,
            rawWeightText = weightText,
            lastEdited = lastEdited,
            rates = rates,
            wallet = wallet,
            gstPercent = gstPercent,
            paymentMethod = paymentMethod,
        )
    }

    // Re-sync the derived field whenever side, purity, or live rates update
    LaunchedEffect(activeSide, purity, quote.ratePerGram, gstPercent) {
        if (quote.ratePerGram <= 0.0) return@LaunchedEffect
        if (lastEdited == GoldTradeInputFocus.AMOUNT) {
            val amt = amountText.toDoubleOrNull() ?: 0.0
            val w = GoldTradingPanelCalculator.weightFromAmount(amt, quote.ratePerGram, gstPercent, activeSide)
            weightText = if (amt > 0.0) GoldTradingPanelCalculator.formatWeightInput(w) else ""
        } else {
            val g = weightText.toDoubleOrNull() ?: 0.0
            val a = GoldTradingPanelCalculator.amountFromWeight(g, quote.ratePerGram, gstPercent, activeSide)
            amountText = if (g > 0.0) GoldTradingPanelCalculator.formatAmountInput(a) else ""
        }
    }

    fun updateFromAmount(raw: String) {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        if (cleaned.count { it == '.' } > 1) return
        lastEdited = GoldTradeInputFocus.AMOUNT
        amountText = cleaned
        val amt = cleaned.toDoubleOrNull() ?: 0.0
        val w = GoldTradingPanelCalculator.weightFromAmount(amt, quote.ratePerGram, gstPercent, activeSide)
        weightText = if (amt > 0.0 && quote.ratePerGram > 0.0) GoldTradingPanelCalculator.formatWeightInput(w) else ""
    }

    fun updateFromWeight(raw: String) {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        if (cleaned.count { it == '.' } > 1) return
        lastEdited = GoldTradeInputFocus.WEIGHT
        weightText = cleaned
        val g = cleaned.toDoubleOrNull() ?: 0.0
        val a = GoldTradingPanelCalculator.amountFromWeight(g, quote.ratePerGram, gstPercent, activeSide)
        amountText = if (g > 0.0 && quote.ratePerGram > 0.0) GoldTradingPanelCalculator.formatAmountInput(a) else ""
    }

    fun executeTradeNow() {
        if (activeSide == GoldTradeSide.BUY) {
            onBuyGold(
                quote.purity,
                quote.grams,
                quote.ratePerGram,
                quote.baseAmountInr,
                quote.gstAmountInr,
                0.0,
                quote.totalAmountInr,
                paymentMethod,
            )
        } else {
            val bank = bankAccounts.firstOrNull { it.id == selectedBankId }
            onSellGold(
                quote.purity,
                quote.grams,
                quote.ratePerGram,
                0.0,
                sellCreditToWallet,
                bank,
            )
        }
    }

    val isBuy = activeSide == GoldTradeSide.BUY
    val walletInr = wallet?.availableInr ?: 0.0
    val bankPayoutReady = sellCreditToWallet || bankAccounts.any { it.id == selectedBankId }
    val canSubmit = !isBusy &&
        !maintenanceMode &&
        quote.grams > 0.0 &&
        quote.ratePerGram > 0.0 &&
        !quote.exceedsHoldings &&
        !quote.insufficientWallet &&
        (isBuy || bankPayoutReady)

    LuxuryCard(
        highlightGold = true,
        modifier = modifier.testTag("gold_trading_panel"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Digital Gold Trade", style = MaterialTheme.typography.titleMedium, color = LightGold)
                Text(
                    text = if (quote.ratePerGram > 0.0) {
                        "${if (isBuy) "Buy" else "Sell"} $purity: ${Formatters.formatInr(quote.ratePerGram)}/g"
                    } else {
                        "Live feed connecting..."
                    },
                    style = MonoPriceStyle,
                    color = PureWhite,
                )
            }
            EnvironmentModeBadge(paymentMode)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Buy / Sell Segmented Tabs
        TabRow(
            selectedTabIndex = if (isBuy) 0 else 1,
            containerColor = ElevatedObsidian,
            contentColor = RoyalGold,
        ) {
            Tab(
                selected = isBuy,
                onClick = { activeSide = GoldTradeSide.BUY },
                text = { Text("Buy Gold", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("gold_panel_buy_tab"),
            )
            Tab(
                selected = !isBuy,
                onClick = { activeSide = GoldTradeSide.SELL },
                text = { Text("Sell Gold", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("gold_panel_sell_tab"),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Purity chips + owned holdings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("24K", "22K", "18K").forEach { p ->
                    FilterChip(
                        selected = purity == p,
                        onClick = { purity = p },
                        label = { Text(p) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalGold,
                            selectedLabelColor = DeepBlack,
                        ),
                        modifier = Modifier.testTag("gold_panel_purity_$p"),
                    )
                }
            }
            Text(
                text = "Vault: ${Formatters.formatGrams(quote.ownedGramsForPurity)}",
                style = MonoSmallStyle,
                color = SecondarySilver,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Synchronized Amount (₹) and Weight (g) inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { updateFromAmount(it) },
                label = { Text("Amount (₹)") },
                placeholder = { Text("e.g. 1000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gold_panel_amount_input"),
            )
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Synchronized amount and weight conversion",
                tint = RoyalGold,
                modifier = Modifier.size(20.dp),
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = { updateFromWeight(it) },
                label = { Text("Weight (g)") },
                placeholder = { Text("e.g. 1.0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gold_panel_weight_input"),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick preset chips for both Amount & Weight
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("500" to "₹500", "1000" to "₹1,000", "5000" to "₹5,000").forEach { (amt, label) ->
                FilterChip(
                    selected = lastEdited == GoldTradeInputFocus.AMOUNT && amountText == amt,
                    onClick = { updateFromAmount(amt) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("gold_panel_preset_inr_$amt"),
                )
            }
            listOf("0.5" to "0.5g", "1.0" to "1g", "5.0" to "5g").forEach { (g, label) ->
                FilterChip(
                    selected = lastEdited == GoldTradeInputFocus.WEIGHT && weightText == g,
                    onClick = { updateFromWeight(g) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("gold_panel_preset_g_$g"),
                )
            }
            if (!isBuy && quote.ownedGramsForPurity > 0.0) {
                FilterChip(
                    selected = false,
                    onClick = { updateFromWeight(GoldTradingPanelCalculator.formatWeightInput(quote.ownedGramsForPurity)) },
                    label = { Text("Sell Max") },
                    modifier = Modifier.testTag("gold_panel_sell_max"),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = GoldBorderSubtle)
        Spacer(modifier = Modifier.height(8.dp))

        // Live Order Breakdown
        Surface(
            color = ElevatedObsidian,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GoldBorderSubtle),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KeyValueRow("Purity & Metal", "$purity Digital Gold", valueColor = LightGold)
                KeyValueRow("Gold Quantity", Formatters.formatGrams(quote.grams), valueColor = LightGold, boldValue = true)
                KeyValueRow(
                    if (isBuy) "Gold Base Value" else "Gross Sale Value",
                    Formatters.formatInr(quote.baseAmountInr),
                )
                if (isBuy) {
                    KeyValueRow("GST (${gstPercent}%)", Formatters.formatInr(quote.gstAmountInr))
                    KeyValueRow("Total Payable", Formatters.formatInr(quote.totalAmountInr), valueColor = PureWhite, boldValue = true)
                } else {
                    KeyValueRow("Net Receivable", Formatters.formatInr(quote.totalAmountInr), valueColor = SuccessGreen, boldValue = true)
                }
            }
        }

        AnimatedVisibility(visible = quote.exceedsHoldings) {
            Text(
                text = "Weight exceeds your available $purity vault balance (${Formatters.formatGrams(quote.ownedGramsForPurity)}).",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        AnimatedVisibility(visible = quote.insufficientWallet) {
            Text(
                text = "Insufficient wallet balance (${Formatters.formatInr(walletInr)} available).",
                style = MaterialTheme.typography.bodySmall,
                color = ErrorRed,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isBuy) {
            Text("Payment Method", style = MaterialTheme.typography.labelLarge, color = PureWhite)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("WALLET", "UPI", "PHONEPE", "GPAY", "DEBIT_CARD", "NET_BANKING").forEach { method ->
                    FilterChip(
                        selected = paymentMethod == method,
                        onClick = { paymentMethod = method },
                        label = { Text(method.replace("_", " ")) },
                    )
                }
            }
            if (paymentMethod == "WALLET") {
                Text(
                    text = "Wallet Balance: ${Formatters.formatInr(walletInr)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (walletInr >= quote.totalAmountInr) SuccessGreen else ErrorRed,
                )
            }
        } else {
            Text("Credit Sale Proceeds To", style = MaterialTheme.typography.labelLarge, color = PureWhite)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = sellCreditToWallet, onClick = { sellCreditToWallet = true })
                Text("SONA-WANE Wallet (Instant)", color = PureWhite, style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !sellCreditToWallet, onClick = { sellCreditToWallet = false })
                Text("Verified Bank Account", color = PureWhite, style = MaterialTheme.typography.bodyMedium)
            }
            if (!sellCreditToWallet) {
                if (bankAccounts.isEmpty()) {
                    Text(
                        text = "Link a verified bank account in Wallet first.",
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    bankAccounts.forEach { bank ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedBankId == bank.id, onClick = { selectedBankId = bank.id })
                            Text("${bank.bankName} (${bank.accountNumberMasked})", color = SecondarySilver, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isBuy) {
            GoldPrimaryButton(
                text = "Buy Digital Gold (${Formatters.formatInr(quote.totalAmountInr)})",
                icon = if (biometricRequired) Icons.Default.Lock else null,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gold_panel_execute_btn"),
                onClick = {
                    val needsAuthPrompt = biometricRequired || (onAuthenticateBiometric != null && biometricCapability.isUsable)
                    if (needsAuthPrompt && (onAuthenticateBiometric != null || onVerifyMpin != null)) {
                        authError = null
                        mpinInput = ""
                        showConfirmDialog = true
                    } else {
                        executeTradeNow()
                    }
                },
            )
        } else {
            OrangeActionButton(
                text = "Sell Digital Gold (${Formatters.formatInr(quote.totalAmountInr)})",
                icon = if (biometricRequired || hasMpin) Icons.Default.Lock else null,
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gold_panel_execute_btn"),
                onClick = {
                    if (!hasMpin && onSetupMpin != null && !biometricCapability.isUsable) {
                        onSetupMpin()
                    } else if (onAuthenticateBiometric != null || onVerifyMpin != null) {
                        authError = null
                        mpinInput = ""
                        showConfirmDialog = true
                    } else {
                        executeTradeNow()
                    }
                },
            )
        }
    }

    if (showConfirmDialog) {
        val bioAvailable = biometricCapability.isUsable && onAuthenticateBiometric != null
        val actionTitle = if (isBuy) "Confirm Gold Purchase ($purity)" else "Authorize Gold Sale ($purity)"
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                authError = null
                mpinInput = ""
            },
            title = { Text(actionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Weight: ${Formatters.formatGrams(quote.grams)} ($purity)")
                    Text("Rate: ${Formatters.formatInr(quote.ratePerGram)}/g")
                    Text(
                        text = if (isBuy) "Payable: ${Formatters.formatInr(quote.totalAmountInr)}" else "Net Proceeds: ${Formatters.formatInr(quote.totalAmountInr)}",
                        fontWeight = FontWeight.Bold,
                    )
                    if (bioAvailable && onAuthenticateBiometric != null) {
                        GoldPrimaryButton(
                            text = "Confirm with Fingerprint / Face",
                            icon = Icons.Default.Fingerprint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gold_panel_biometric_confirm_btn"),
                            onClick = {
                                authError = null
                                onAuthenticateBiometric.invoke(
                                    actionTitle,
                                    "Authenticate via BiometricLoginManager to execute ${Formatters.formatGrams(quote.grams)} gold order",
                                    {
                                        showConfirmDialog = false
                                        mpinInput = ""
                                        executeTradeNow()
                                    },
                                    { err -> authError = err },
                                )
                            },
                        )
                    }
                    if (hasMpin && onVerifyMpin != null) {
                        OutlinedTextField(
                            value = mpinInput,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) mpinInput = it },
                            label = { Text(if (bioAvailable) "Or enter 4-Digit MPIN" else "Enter 4-Digit MPIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gold_panel_mpin_input"),
                        )
                    }
                    authError?.takeIf { it.isNotBlank() }?.let { errMsg ->
                        Text(errMsg, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (hasMpin && onVerifyMpin != null) {
                    TextButton(
                        enabled = mpinInput.length == 4,
                        onClick = {
                            if (onVerifyMpin(mpinInput)) {
                                showConfirmDialog = false
                                mpinInput = ""
                                authError = null
                                executeTradeNow()
                            } else {
                                authError = "Incorrect MPIN"
                            }
                        },
                    ) { Text(if (isBuy) "Pay via MPIN" else "Sell via MPIN", color = RoyalGold) }
                } else if (!biometricRequired) {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            authError = null
                            executeTradeNow()
                        },
                    ) { Text(if (isBuy) "Authorize & Buy" else "Confirm Sale", color = RoyalGold) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        authError = null
                        mpinInput = ""
                    },
                ) { Text("Cancel") }
            },
        )
    }
}
