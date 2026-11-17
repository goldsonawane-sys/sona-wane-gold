package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.UserProfile
import com.example.data.model.WalletHoldings
import com.example.ui.components.EnvironmentModeBadge
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.KeyValueRow
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
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
import com.example.util.BiometricAuthManager
import com.example.util.Formatters

enum class GoldCalculatorInputMode(val label: String) {
    BY_AMOUNT("By Amount (₹)"),
    BY_WEIGHT("By Weight (g)"),
}

enum class GoldAmountBasis(val label: String) {
    TOTAL_BUDGET("Total Budget (Incl. Taxes)"),
    BASE_VALUE("Net Gold Value (+ Taxes)"),
}

enum class GoldPurchaseFormat(
    val title: String,
    val subtitle: String,
    val makingChargePercent: Double,
) {
    DIGITAL_VAULT("Digital Vault", "0% Making Fee", 0.0),
    MINTED_BAR("Insured Mint Bar", "1.5% Minting & Custody", 1.5),
}

data class GoldPurchaseQuote(
    val purity: String,
    val ratePerGram: Double,
    val grams: Double,
    val baseAmountInr: Double,
    val chargesAmountInr: Double,
    val gstPercent: Double,
    val gstAmountInr: Double,
    val totalPayableInr: Double,
)

object GoldPurchaseCalculator {
    fun resolveGoldRatePerGram(
        purity: String,
        firestoreSummary: FirestoreGoldSummary,
        fallbackRates: LiveMarketRates,
    ): Double {
        val g24 = firestoreSummary.gold24kPerGram.takeIf { it > 0.0 }
            ?: fallbackRates.gold24kBuyPerGram
        return when (purity) {
            "22K" -> firestoreSummary.gold22kPerGram.takeIf { it > 0.0 }
                ?: fallbackRates.gold22kBuyPerGram.takeIf { it > 0.0 }
                ?: (g24 * (22.0 / 24.0))
            "18K" -> firestoreSummary.gold18kPerGram.takeIf { it > 0.0 }
                ?: fallbackRates.gold18kBuyPerGram.takeIf { it > 0.0 }
                ?: (g24 * (18.0 / 24.0))
            else -> g24
        }
    }

    fun calculateQuote(
        purity: String,
        ratePerGram: Double,
        rawInput: Double,
        inputMode: GoldCalculatorInputMode,
        amountBasis: GoldAmountBasis,
        format: GoldPurchaseFormat,
        gstPercent: Double,
    ): GoldPurchaseQuote {
        val cleanInput = rawInput.coerceAtLeast(0.0)
        val cleanRate = ratePerGram.coerceAtLeast(0.0)
        val cleanGstPct = gstPercent.coerceAtLeast(0.0)
        val chargeFactor = format.makingChargePercent / 100.0
        val gstFactor = cleanGstPct / 100.0

        if (cleanInput <= 0.0 || cleanRate <= 0.0) {
            return GoldPurchaseQuote(
                purity = purity,
                ratePerGram = cleanRate,
                grams = 0.0,
                baseAmountInr = 0.0,
                chargesAmountInr = 0.0,
                gstPercent = cleanGstPct,
                gstAmountInr = 0.0,
                totalPayableInr = 0.0,
            )
        }

        return when (inputMode) {
            GoldCalculatorInputMode.BY_WEIGHT -> {
                val grams = cleanInput
                val base = grams * cleanRate
                val charges = base * chargeFactor
                val gst = (base + charges) * gstFactor
                val total = base + charges + gst
                GoldPurchaseQuote(
                    purity = purity,
                    ratePerGram = cleanRate,
                    grams = grams,
                    baseAmountInr = base,
                    chargesAmountInr = charges,
                    gstPercent = cleanGstPct,
                    gstAmountInr = gst,
                    totalPayableInr = total,
                )
            }
            GoldCalculatorInputMode.BY_AMOUNT -> {
                when (amountBasis) {
                    GoldAmountBasis.TOTAL_BUDGET -> {
                        val total = cleanInput
                        val preTax = total / (1.0 + gstFactor)
                        val base = preTax / (1.0 + chargeFactor)
                        val charges = preTax - base
                        val gst = total - preTax
                        val grams = base / cleanRate
                        GoldPurchaseQuote(
                            purity = purity,
                            ratePerGram = cleanRate,
                            grams = grams,
                            baseAmountInr = base,
                            chargesAmountInr = charges,
                            gstPercent = cleanGstPct,
                            gstAmountInr = gst,
                            totalPayableInr = total,
                        )
                    }
                    GoldAmountBasis.BASE_VALUE -> {
                        val base = cleanInput
                        val grams = base / cleanRate
                        val charges = base * chargeFactor
                        val gst = (base + charges) * gstFactor
                        val total = base + charges + gst
                        GoldPurchaseQuote(
                            purity = purity,
                            ratePerGram = cleanRate,
                            grams = grams,
                            baseAmountInr = base,
                            chargesAmountInr = charges,
                            gstPercent = cleanGstPct,
                            gstAmountInr = gst,
                            totalPayableInr = total,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoldPurchaseCalculatorScreen(
    profile: UserProfile?,
    wallet: WalletHoldings?,
    firestoreGoldSummary: FirestoreGoldSummary,
    fallbackRates: LiveMarketRates,
    appConfig: AppConfig,
    isBusy: Boolean,
    requireBiometricForTrades: Boolean,
    onBack: () -> Unit,
    onRefreshFirestoreRate: () -> Unit,
    onVerifyMpin: (String) -> Boolean,
    onVerifyBiometric: ((onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit)?,
    onBiometricTradeAuthorized: (String) -> Unit,
    onTriggerPayment: (
        purity: String,
        grams: Double,
        ratePerGram: Double,
        baseAmount: Double,
        gstAmount: Double,
        chargesAmount: Double,
        totalPayable: Double,
        paymentMethod: String,
    ) -> Unit,
) {
    val context = LocalContext.current
    val biometricCapability = remember(context) { BiometricAuthManager.checkCapability(context) }

    var selectedPurity by remember { mutableStateOf("24K") }
    var inputMode by remember { mutableStateOf(GoldCalculatorInputMode.BY_AMOUNT) }
    var amountBasis by remember { mutableStateOf(GoldAmountBasis.TOTAL_BUDGET) }
    var purchaseFormat by remember { mutableStateOf(GoldPurchaseFormat.DIGITAL_VAULT) }
    var inputText by remember { mutableStateOf("5000") }
    var paymentMethod by remember { mutableStateOf("UPI") }
    var showPaymentConfirmDialog by remember { mutableStateOf(false) }
    var mpinInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }

    val activeRatePerGram = remember(selectedPurity, firestoreGoldSummary, fallbackRates) {
        GoldPurchaseCalculator.resolveGoldRatePerGram(selectedPurity, firestoreGoldSummary, fallbackRates)
    }

    val numericInput = inputText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val quote = remember(
        selectedPurity,
        activeRatePerGram,
        numericInput,
        inputMode,
        amountBasis,
        purchaseFormat,
        appConfig.gstPercent,
    ) {
        GoldPurchaseCalculator.calculateQuote(
            purity = selectedPurity,
            ratePerGram = activeRatePerGram,
            rawInput = numericInput,
            inputMode = inputMode,
            amountBasis = amountBasis,
            format = purchaseFormat,
            gstPercent = appConfig.gstPercent,
        )
    }

    val w = wallet ?: WalletHoldings()
    val paymentMethods = listOf("WALLET", "UPI", "PHONEPE", "GPAY", "BHIM", "UPI_QR", "DEBIT_CARD", "CREDIT_CARD", "NET_BANKING")
    val walletSufficient = paymentMethod != "WALLET" || w.availableInr >= quote.totalPayableInr

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            SonaWaneTopBar(
                title = stringResource(R.string.calc_title),
                subtitle = stringResource(R.string.calc_subtitle),
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onRefreshFirestoreRate,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("calc_refresh_rate_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.calc_refresh_cd),
                            tint = RoyalGold,
                        )
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
            // Live Firestore Rate Banner Card
            item {
                LuxuryCard(highlightGold = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = RoyalGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Live $selectedPurity Gold Rate",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LightGold,
                                )
                                Text(
                                    text = if (firestoreGoldSummary.isFromFirestore) {
                                        "Firestore (${firestoreGoldSummary.dateStr.ifBlank { "live" }})"
                                    } else {
                                        "Synced Spot Feed"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondarySilver,
                                )
                            }
                        }
                        StatusPill(
                            text = if (firestoreGoldSummary.isFromFirestore) "FIRESTORE LIVE" else "LIVE FEED",
                            color = if (firestoreGoldSummary.isFromFirestore) SuccessGreen else AmberOrange,
                            icon = if (firestoreGoldSummary.isFromFirestore) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (firestoreGoldSummary.isLoading && activeRatePerGram <= 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = RoyalGold, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fetching live Firestore gold rate...", style = MaterialTheme.typography.bodySmall, color = SecondarySilver)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = if (activeRatePerGram > 0.0) "${Formatters.formatInr(activeRatePerGram)} / g" else "--",
                                style = MaterialTheme.typography.displayMedium,
                                color = PureWhite,
                                modifier = Modifier.testTag("calc_live_rate_text"),
                            )
                            EnvironmentModeBadge(appConfig.paymentMode)
                        }
                    }

                    if (!firestoreGoldSummary.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = firestoreGoldSummary.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                        )
                    }
                }
            }

            // Calculator Inputs Card
            item {
                LuxuryCard {
                    Text("1. Select Gold Purity", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("24K", "22K", "18K").forEach { p ->
                            FilterChip(
                                selected = selectedPurity == p,
                                onClick = { selectedPurity = p },
                                label = { Text("$p Gold") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold,
                                    selectedLabelColor = DeepBlack,
                                ),
                                modifier = Modifier.testTag("calc_purity_$p"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("2. Enter Desired Amount or Weight", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldCalculatorInputMode.entries.forEach { mode ->
                            FilterChip(
                                selected = inputMode == mode,
                                onClick = {
                                    inputMode = mode
                                    inputText = if (mode == GoldCalculatorInputMode.BY_AMOUNT) "5000" else "2.0"
                                },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberOrange,
                                    selectedLabelColor = DeepBlack,
                                ),
                                modifier = Modifier.testTag("calc_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }

                    if (inputMode == GoldCalculatorInputMode.BY_AMOUNT) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            GoldAmountBasis.entries.forEach { basis ->
                                FilterChip(
                                    selected = amountBasis == basis,
                                    onClick = { amountBasis = basis },
                                    label = { Text(basis.label) },
                                    modifier = Modifier.testTag("calc_basis_${basis.name.lowercase()}"),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { next ->
                            if (next.length <= 12 && next.all { it.isDigit() || it == '.' }) {
                                inputText = next
                            }
                        },
                        label = {
                            Text(
                                if (inputMode == GoldCalculatorInputMode.BY_AMOUNT) {
                                    if (amountBasis == GoldAmountBasis.TOTAL_BUDGET) "Total Budget Amount (₹)" else "Base Gold Value (₹)"
                                } else {
                                    "Desired Gold Weight (grams)"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_input_field"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val presets = if (inputMode == GoldCalculatorInputMode.BY_AMOUNT) {
                            listOf("1000", "5000", "10000", "25000", "50000")
                        } else {
                            listOf("0.5", "1.0", "2.0", "5.0", "10.0")
                        }
                        presets.forEach { preset ->
                            FilterChip(
                                selected = inputText == preset,
                                onClick = { inputText = preset },
                                label = {
                                    Text(
                                        if (inputMode == GoldCalculatorInputMode.BY_AMOUNT) "₹$preset" else "${preset}g"
                                    )
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("3. Purchase Format & Vault Charges", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GoldPurchaseFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = purchaseFormat == fmt,
                                onClick = { purchaseFormat = fmt },
                                label = { Text("${fmt.title} (${fmt.subtitle})") },
                                modifier = Modifier.testTag("calc_format_${fmt.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // Taxes, Charges & Payable Summary Card
            item {
                LuxuryCard(
                    highlightGold = true,
                    modifier = Modifier.testTag("calc_breakdown_card"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.calc_tax_breakdown),
                            style = MaterialTheme.typography.titleMedium,
                            color = LightGold,
                        )
                        StatusPill(text = "$selectedPurity • ${purchaseFormat.title}", color = RoyalGold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    KeyValueRow("Live Gold Rate ($selectedPurity)", "${Formatters.formatInr(quote.ratePerGram)} / g")
                    KeyValueRow(
                        label = "Calculated Gold Weight",
                        value = Formatters.formatGrams(quote.grams),
                        valueColor = LightGold,
                        boldValue = true,
                    )
                    KeyValueRow("Base Gold Value", Formatters.formatInr(quote.baseAmountInr))
                    KeyValueRow(
                        label = "Minting / Vault Charges (${purchaseFormat.makingChargePercent}%)",
                        value = Formatters.formatInr(quote.chargesAmountInr),
                    )
                    KeyValueRow(
                        label = "Bullion GST (${quote.gstPercent}%)",
                        value = Formatters.formatInr(quote.gstAmountInr),
                    )

                    HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.calc_total_payable),
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = Formatters.formatInr(quote.totalPayableInr),
                            style = MonoPriceStyle,
                            color = LightGold,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("calc_total_payable_value"),
                        )
                    }
                }
            }

            // Payment Method Selection & Trigger Flow
            item {
                LuxuryCard {
                    Text("4. Select Payment Method", style = MaterialTheme.typography.labelLarge, color = LightGold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        paymentMethods.forEach { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(method.replace("_", " ")) },
                                modifier = Modifier.testTag("calc_method_${method.lowercase()}"),
                            )
                        }
                    }

                    if (paymentMethod == "WALLET") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Available Wallet Balance: ${Formatters.formatInr(w.availableInr)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (walletSufficient) SuccessGreen else ErrorRed,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    GoldPrimaryButton(
                        text = "${stringResource(R.string.calc_proceed_payment)} (${Formatters.formatInr(quote.totalPayableInr)})",
                        icon = Icons.Default.Verified,
                        enabled = !isBusy && quote.grams > 0.0 && quote.totalPayableInr > 0.0 && walletSufficient && !appConfig.maintenanceMode,
                        onClick = {
                            authError = null
                            mpinInput = ""
                            showPaymentConfirmDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_proceed_payment_btn"),
                    )
                    if (appConfig.maintenanceMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Maintenance mode is active. Trading is temporarily paused.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }

    if (showPaymentConfirmDialog) {
        val hardwareReady = biometricCapability.isUsable && onVerifyBiometric != null
        val biometricEnforced = (profile?.biometricEnabled == true) && requireBiometricForTrades && hardwareReady
        AlertDialog(
            onDismissRequest = {
                showPaymentConfirmDialog = false
                authError = null
                mpinInput = ""
            },
            title = { Text("Authorize Gold Payment (${appConfig.paymentMode})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, GoldBorderSubtle),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            KeyValueRow("Purity", "$selectedPurity (${purchaseFormat.title})")
                            KeyValueRow("Gold Weight", Formatters.formatGrams(quote.grams), valueColor = LightGold)
                            KeyValueRow("Base Value", Formatters.formatInr(quote.baseAmountInr))
                            KeyValueRow("Charges", Formatters.formatInr(quote.chargesAmountInr))
                            KeyValueRow("GST (${quote.gstPercent}%)", Formatters.formatInr(quote.gstAmountInr))
                            HorizontalDivider(color = GoldBorderSubtle, modifier = Modifier.padding(vertical = 4.dp))
                            KeyValueRow(
                                label = "Total Payable",
                                value = Formatters.formatInr(quote.totalPayableInr),
                                valueColor = PureWhite,
                                boldValue = true,
                            )
                            KeyValueRow("Payment Method", paymentMethod.replace("_", " "))
                        }
                    }

                    if (appConfig.paymentMode == "TEST") {
                        Text(
                            text = "TEST MODE: Sandbox payment authorization — no real INR deducted.",
                            color = AmberOrange,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    if (biometricEnforced || hardwareReady) {
                        onVerifyBiometric?.let { verifyBio ->
                            GoldPrimaryButton(
                                text = "Authorize with Fingerprint / Face",
                                icon = Icons.Default.Fingerprint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calc_biometric_confirm_btn"),
                                onClick = {
                                    authError = null
                                    verifyBio.invoke(
                                        {
                                            showPaymentConfirmDialog = false
                                            mpinInput = ""
                                            onBiometricTradeAuthorized("CALC_BUY_GOLD_${selectedPurity}_BIOMETRIC")
                                            onTriggerPayment(
                                                selectedPurity,
                                                quote.grams,
                                                quote.ratePerGram,
                                                quote.baseAmountInr,
                                                quote.gstAmountInr,
                                                quote.chargesAmountInr,
                                                quote.totalPayableInr,
                                                paymentMethod,
                                            )
                                        },
                                        { err -> authError = err },
                                    )
                                },
                            )
                        }
                        if (biometricEnforced && !profile?.mpinHash.isNullOrBlank()) {
                            OutlinedTextField(
                                value = mpinInput,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) mpinInput = it },
                                label = { Text("Or enter 4-Digit MPIN fallback") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calc_mpin_fallback_input"),
                            )
                        }
                    }

                    authError?.takeIf { it.isNotBlank() }?.let { errText ->
                        Text(errText, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (biometricEnforced && !profile?.mpinHash.isNullOrBlank()) {
                    TextButton(
                        enabled = mpinInput.length == 4,
                        onClick = {
                            if (onVerifyMpin(mpinInput)) {
                                showPaymentConfirmDialog = false
                                mpinInput = ""
                                authError = null
                                onTriggerPayment(
                                    selectedPurity,
                                    quote.grams,
                                    quote.ratePerGram,
                                    quote.baseAmountInr,
                                    quote.gstAmountInr,
                                    quote.chargesAmountInr,
                                    quote.totalPayableInr,
                                    paymentMethod,
                                )
                            } else {
                                authError = "Incorrect MPIN"
                            }
                        },
                    ) { Text("Verify MPIN & Pay", color = RoyalGold) }
                } else if (!biometricEnforced) {
                    TextButton(onClick = {
                        showPaymentConfirmDialog = false
                        authError = null
                        onTriggerPayment(
                            selectedPurity,
                            quote.grams,
                            quote.ratePerGram,
                            quote.baseAmountInr,
                            quote.gstAmountInr,
                            quote.chargesAmountInr,
                            quote.totalPayableInr,
                            paymentMethod,
                        )
                    }) { Text("Confirm & Pay", color = RoyalGold) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPaymentConfirmDialog = false
                    authError = null
                    mpinInput = ""
                }) { Text("Cancel") }
            },
        )
    }
}
