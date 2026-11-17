package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.WalletHoldings
import com.example.data.repository.SonaWaneRepository
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
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
import com.example.data.repository.FirestoreService
import com.example.util.Formatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Locale

data class GoldPortfolioValuation(
    val totalGoldGrams: Double = 0.0,
    val gold24kGrams: Double = 0.0,
    val gold22kGrams: Double = 0.0,
    val gold18kGrams: Double = 0.0,
    val rate24kInr: Double = 0.0,
    val rate22kInr: Double = 0.0,
    val rate18kInr: Double = 0.0,
    val totalGoldMarketValueInr: Double = 0.0,
    val totalInvestedGoldInr: Double = 0.0,
    val goldProfitLossInr: Double = 0.0,
    val goldPnlPercent: Double = 0.0,
    val silverGrams: Double = 0.0,
    val silverMarketValueInr: Double = 0.0,
    val combinedPortfolioValueInr: Double = 0.0,
    val isFromFirestore: Boolean = false,
    val rateDateStr: String = "",
)

object PortfolioValuationCalculator {
    fun compute(
        wallet: WalletHoldings?,
        firestoreSummary: FirestoreGoldSummary,
        fallbackRates: LiveMarketRates? = null,
    ): GoldPortfolioValuation {
        val w = wallet ?: WalletHoldings()
        val r24 = when {
            firestoreSummary.gold24kPerGram > 0.0 -> firestoreSummary.gold24kPerGram
            else -> fallbackRates?.gold24kBuyPerGram?.takeIf { it > 0.0 } ?: 0.0
        }
        val r22 = when {
            firestoreSummary.gold22kPerGram > 0.0 -> firestoreSummary.gold22kPerGram
            else -> fallbackRates?.gold22kBuyPerGram?.takeIf { it > 0.0 }
                ?: if (r24 > 0.0) r24 * (22.0 / 24.0) else 0.0
        }
        val r18 = when {
            firestoreSummary.gold18kPerGram > 0.0 -> firestoreSummary.gold18kPerGram
            else -> fallbackRates?.gold18kBuyPerGram?.takeIf { it > 0.0 }
                ?: if (r24 > 0.0) r24 * (18.0 / 24.0) else 0.0
        }

        val goldValInr = (w.gold24kGrams * r24) + (w.gold22kGrams * r22) + (w.gold18kGrams * r18)
        val investedGold = w.totalInvestedGoldInr
        val pnl = goldValInr - investedGold
        val pnlPct = if (investedGold > 1.0) (pnl / investedGold) * 100.0 else 0.0

        val silverRate = fallbackRates?.silver999SellPerGram
            ?.takeIf { it > 0.0 }
            ?: fallbackRates?.silver999BuyPerGram
            ?: 0.0
        val silverValInr = w.silver999Grams * silverRate

        return GoldPortfolioValuation(
            totalGoldGrams = w.totalGoldGrams,
            gold24kGrams = w.gold24kGrams,
            gold22kGrams = w.gold22kGrams,
            gold18kGrams = w.gold18kGrams,
            rate24kInr = r24,
            rate22kInr = r22,
            rate18kInr = r18,
            totalGoldMarketValueInr = goldValInr,
            totalInvestedGoldInr = investedGold,
            goldProfitLossInr = pnl,
            goldPnlPercent = pnlPct,
            silverGrams = w.silver999Grams,
            silverMarketValueInr = silverValInr,
            combinedPortfolioValueInr = goldValInr + silverValInr,
            isFromFirestore = firestoreSummary.isFromFirestore,
            rateDateStr = firestoreSummary.dateStr,
        )
    }
}

/**
 * Self-contained Firestore-connected overload of [PortfolioSummaryCard] that observes the
 * signed-in user's wallet holdings and live Firestore `market_price_history` gold rates.
 */
@Composable
fun PortfolioSummaryCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val currentUid = remember(appContext) { FirestoreService.currentUserOrNull(appContext)?.uid }
    val repository = remember(currentUid) {
        if (currentUid != null) SonaWaneRepository(appContext) else null
    }

    var manualGoldOverride by remember(currentUid) { mutableStateOf<FirestoreGoldSummary?>(null) }

    val walletFlow = remember(repository, currentUid) {
        if (repository != null && currentUid != null) {
            repository.observeWallet(currentUid)
                .catch { err ->
                    val fs = generateSequence(err) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fs == null && err is CancellationException) throw err
                    emit(null)
                }
        } else {
            flowOf(null)
        }
    }
    val goldFlow = remember(repository) {
        repository?.observeLiveGoldSummary()
            ?.catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
                emit(FirestoreGoldSummary(isLoading = false, isFromFirestore = false))
            }
            ?: flowOf(FirestoreGoldSummary(isLoading = false, isFromFirestore = false))
    }

    val wallet by walletFlow.collectAsStateWithLifecycle(initialValue = null)
    val streamedGold by goldFlow.collectAsStateWithLifecycle(
        initialValue = FirestoreGoldSummary(isLoading = currentUid != null),
    )
    val effectiveGold = manualGoldOverride?.takeIf {
        it.isLoading || !streamedGold.isFromFirestore || it.lastUpdatedEpochMs > streamedGold.lastUpdatedEpochMs
    } ?: streamedGold

    PortfolioSummaryCard(
        wallet = wallet,
        firestoreGoldSummary = effectiveGold,
        onRefresh = {
            val repo = repository ?: return@PortfolioSummaryCard
            scope.launch {
                manualGoldOverride = effectiveGold.copy(isLoading = true)
                manualGoldOverride = runCatching { repo.fetchLatestGoldSummaryOnce() }
                    .getOrElse { effectiveGold.copy(isLoading = false) }
            }
        },
        modifier = modifier,
    )
}

/**
 * Displays the user's current gold balance in grams (total and 24K/22K/18K breakdown)
 * alongside real-time market valuation in the device's local currency computed from
 * live Firestore gold rates.
 */
@Composable
fun PortfolioSummaryCard(
    wallet: WalletHoldings?,
    firestoreGoldSummary: FirestoreGoldSummary,
    fallbackRates: LiveMarketRates? = null,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val locale = remember { Locale.getDefault() }
    val localCurrency = remember(locale) { Formatters.localCurrencyCode(locale) }
    val usdToInr = fallbackRates?.usdToInr?.takeIf { it > 1.0 } ?: 84.5

    val valuation = remember(wallet, firestoreGoldSummary, fallbackRates) {
        PortfolioValuationCalculator.compute(wallet, firestoreGoldSummary, fallbackRates)
    }

    val formatCurrency: (Double) -> String = remember(usdToInr, locale) {
        { amountInr -> Formatters.formatLocalCurrency(amountInr, usdToInr = usdToInr, locale = locale) }
    }

    val isPositivePnl = valuation.goldProfitLossInr >= 0.0
    val pnlColor = if (isPositivePnl) SuccessGreen else ErrorRed

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.3.dp, RoyalGold.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .testTag("portfolio_summary_card"),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_swg_hero_vault),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(DeepBlack.copy(alpha = 0.88f), CharcoalBlack.copy(alpha = 0.82f))
                    )
                )
                .padding(18.dp),
        ) {
            // Top Title + Status + Refresh Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = RoyalGold,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.portfolio_summary_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = RoyalGold,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        text = if (valuation.isFromFirestore) "FIRESTORE LIVE" else "LIVE FEED",
                        color = if (valuation.isFromFirestore) SuccessGreen else AmberOrange,
                        icon = if (valuation.isFromFirestore) Icons.Default.CloudDone else Icons.Default.CloudSync,
                    )
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(34.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("portfolio_summary_refresh_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.portfolio_summary_refresh_cd),
                                tint = LightGold,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Hero Row: Total Gold Grams + Local Currency Market Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gold Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                    Text(
                        text = Formatters.formatGrams(valuation.totalGoldGrams),
                        style = MaterialTheme.typography.displayMedium,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("portfolio_gold_grams_text"),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Market Value ($localCurrency): ${formatCurrency(valuation.totalGoldMarketValueInr)}",
                        style = MonoPriceStyle,
                        color = LightGold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("portfolio_gold_value_text"),
                    )
                    if (localCurrency != "INR") {
                        Text(
                            text = "INR Base: ${Formatters.formatInr(valuation.totalGoldMarketValueInr)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = pnlColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, pnlColor.copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isPositivePnl) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = if (isPositivePnl) "Portfolio gain" else "Portfolio loss",
                            tint = pnlColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${if (isPositivePnl) "+" else ""}${"%.2f".format(valuation.goldPnlPercent)}%",
                            style = MonoSmallStyle,
                            color = pnlColor,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Invested: ${formatCurrency(valuation.totalInvestedGoldInr)}  •  P&L: ${formatCurrency(valuation.goldProfitLossInr)}",
                style = MaterialTheme.typography.bodySmall,
                color = SecondarySilver,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Purity Breakdown Row (24K / 22K / 18K)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PurityHoldingMiniTile(
                    purity = "24K",
                    grams = valuation.gold24kGrams,
                    valueFormatted = formatCurrency(valuation.gold24kGrams * valuation.rate24kInr),
                    accent = RoyalGold,
                    modifier = Modifier.weight(1f),
                )
                PurityHoldingMiniTile(
                    purity = "22K",
                    grams = valuation.gold22kGrams,
                    valueFormatted = formatCurrency(valuation.gold22kGrams * valuation.rate22kInr),
                    accent = LightGold,
                    modifier = Modifier.weight(1f),
                )
                PurityHoldingMiniTile(
                    purity = "18K",
                    grams = valuation.gold18kGrams,
                    valueFormatted = formatCurrency(valuation.gold18kGrams * valuation.rate18kInr),
                    accent = AmberOrange,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Combined & 999 Silver Strip
            Surface(
                color = ObsidianCard.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, SilverAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("999 Silver Balance", style = MaterialTheme.typography.labelSmall, color = SilverAccent)
                        Text(
                            text = "${Formatters.formatGrams(valuation.silverGrams)}  (${formatCurrency(valuation.silverMarketValueInr)})",
                            style = MonoSmallStyle,
                            color = PureWhite,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total Bullion ($localCurrency)", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        Text(
                            text = formatCurrency(valuation.combinedPortfolioValueInr),
                            style = MonoSmallStyle,
                            color = LightGold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PurityHoldingMiniTile(
    purity: String,
    grams: Double,
    valueFormatted: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ObsidianCard.copy(alpha = 0.88f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GoldBorderSubtle),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = accent,
                    modifier = Modifier.size(6.dp),
                ) {}
                Spacer(modifier = Modifier.width(5.dp))
                Text(purity, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = Formatters.formatGrams(grams, compact = true),
                style = MonoSmallStyle,
                color = PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = valueFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = SecondarySilver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
