package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.WalletHoldings
import com.example.data.repository.SonaWaneRepository
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

data class PortfolioSummarySnapshot(
    val totalHoldingsValueInr: Double = 0.0,
    val availableWalletInr: Double = 0.0,
    val totalNetWorthWithCashInr: Double = 0.0,
    val totalGoldGrams: Double = 0.0,
    val gold24kGrams: Double = 0.0,
    val gold22kGrams: Double = 0.0,
    val gold18kGrams: Double = 0.0,
    val gold24kValueInr: Double = 0.0,
    val gold22kValueInr: Double = 0.0,
    val gold18kValueInr: Double = 0.0,
    val totalGoldValueInr: Double = 0.0,
    val silver999Grams: Double = 0.0,
    val silverRatePerGramInr: Double = 0.0,
    val totalSilverValueInr: Double = 0.0,
    val totalInvestedBullionInr: Double = 0.0,
    val unrealizedPnlInr: Double = 0.0,
    val unrealizedPnlPercent: Double = 0.0,
    val goldSharePercent: Float = 0f,
    val silverSharePercent: Float = 0f,
    val isFromFirestore: Boolean = false,
    val hasLiveRates: Boolean = false,
)

object PortfolioSummaryCalculator {
    fun calculate(
        wallet: WalletHoldings?,
        firestoreGoldSummary: FirestoreGoldSummary,
        fallbackRates: LiveMarketRates? = null,
        marketHistory30d: List<MarketPriceHistoryEntry> = emptyList(),
    ): PortfolioSummarySnapshot {
        val w = wallet ?: WalletHoldings()
        val goldValuation = PortfolioValuationCalculator.compute(
            wallet = w,
            firestoreSummary = firestoreGoldSummary,
            fallbackRates = fallbackRates,
        )

        val latestHistorySilver = marketHistory30d.lastOrNull { it.silver999PerGram > 0.0 }?.silver999PerGram ?: 0.0
        val effectiveSilverRate = fallbackRates?.silver999SellPerGram?.takeIf { it > 0.0 }
            ?: fallbackRates?.silver999BuyPerGram?.takeIf { it > 0.0 }
            ?: latestHistorySilver

        val silverValInr = w.silver999Grams * effectiveSilverRate
        val goldValInr = goldValuation.totalGoldMarketValueInr
        val totalBullionInr = goldValInr + silverValInr
        val cashInr = w.availableInr + w.pendingInr + w.lockedInr

        val totalInvested = w.totalInvestedGoldInr + w.totalInvestedSilverInr
        val pnlInr = if (totalInvested > 1.0) totalBullionInr - totalInvested else 0.0
        val pnlPct = if (totalInvested > 1.0) (pnlInr / totalInvested) * 100.0 else 0.0

        val (goldShare, silverShare) = if (totalBullionInr > 0.0) {
            val gPct = ((goldValInr / totalBullionInr) * 100.0).toFloat().coerceIn(0f, 100f)
            gPct to (100f - gPct)
        } else {
            0f to 0f
        }

        val hasRates = goldValuation.rate24kInr > 0.0 || effectiveSilverRate > 0.0

        return PortfolioSummarySnapshot(
            totalHoldingsValueInr = totalBullionInr,
            availableWalletInr = cashInr,
            totalNetWorthWithCashInr = totalBullionInr + cashInr,
            totalGoldGrams = w.totalGoldGrams,
            gold24kGrams = w.gold24kGrams,
            gold22kGrams = w.gold22kGrams,
            gold18kGrams = w.gold18kGrams,
            gold24kValueInr = w.gold24kGrams * goldValuation.rate24kInr,
            gold22kValueInr = w.gold22kGrams * goldValuation.rate22kInr,
            gold18kValueInr = w.gold18kGrams * goldValuation.rate18kInr,
            totalGoldValueInr = goldValInr,
            silver999Grams = w.silver999Grams,
            silverRatePerGramInr = effectiveSilverRate,
            totalSilverValueInr = silverValInr,
            totalInvestedBullionInr = totalInvested,
            unrealizedPnlInr = pnlInr,
            unrealizedPnlPercent = pnlPct,
            goldSharePercent = goldShare,
            silverSharePercent = silverShare,
            isFromFirestore = goldValuation.isFromFirestore,
            hasLiveRates = hasRates,
        )
    }
}

/**
 * Self-contained Firestore-connected overload of [PortfolioSummaryWidget] that observes the
 * signed-in user's wallet holdings and live Firestore bullion rates.
 */
@Composable
fun PortfolioSummaryWidget(
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
    var manualRatesOverride by remember(currentUid) { mutableStateOf<LiveMarketRates?>(null) }

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

    val historyFlow = remember(repository) {
        repository?.observeMarketPriceHistory(30)
            ?.catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
                emit(emptyList())
            }
            ?: flowOf(emptyList())
    }

    val wallet by walletFlow.collectAsStateWithLifecycle(initialValue = null)
    val marketHistory by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val streamedGold by goldFlow.collectAsStateWithLifecycle(
        initialValue = FirestoreGoldSummary(isLoading = currentUid != null),
    )
    val effectiveGold = manualGoldOverride?.takeIf {
        it.isLoading || !streamedGold.isFromFirestore || it.lastUpdatedEpochMs > streamedGold.lastUpdatedEpochMs
    } ?: streamedGold

    PortfolioSummaryWidget(
        wallet = wallet,
        firestoreGoldSummary = effectiveGold,
        fallbackRates = manualRatesOverride,
        marketHistory30d = marketHistory,
        onRefresh = {
            val repo = repository ?: return@PortfolioSummaryWidget
            scope.launch {
                manualGoldOverride = effectiveGold.copy(isLoading = true)
                manualGoldOverride = runCatching { repo.fetchLatestGoldSummaryOnce() }
                    .getOrElse { effectiveGold.copy(isLoading = false) }
                manualRatesOverride = runCatching { repo.fetchLatestMarketRatesOnce() }.getOrNull()
            }
        },
        modifier = modifier,
    )
}

/**
 * Main dashboard Portfolio Summary Widget calculating and displaying the combined INR total value
 * of the user's 24K/22K/18K Gold and 999 Silver holdings in a solid black & gold card layout.
 */
@Composable
fun PortfolioSummaryWidget(
    wallet: WalletHoldings?,
    firestoreGoldSummary: FirestoreGoldSummary,
    fallbackRates: LiveMarketRates? = null,
    marketHistory30d: List<MarketPriceHistoryEntry> = emptyList(),
    onRefresh: (() -> Unit)? = null,
    onBuyGold: (() -> Unit)? = null,
    onBuySilver: (() -> Unit)? = null,
    onOpenVault: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val summary = remember(wallet, firestoreGoldSummary, fallbackRates, marketHistory30d) {
        PortfolioSummaryCalculator.calculate(
            wallet = wallet,
            firestoreGoldSummary = firestoreGoldSummary,
            fallbackRates = fallbackRates,
            marketHistory30d = marketHistory30d,
        )
    }

    val isPositivePnl = summary.unrealizedPnlInr >= 0.0
    val pnlColor = if (isPositivePnl) SuccessGreen else ErrorRed
    val hasHoldings = summary.totalGoldGrams > 0.0 || summary.silver999Grams > 0.0

    Surface(
        color = DeepBlack,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, RoyalGold),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("portfolio_summary_widget"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            // Top Title + Status Pill + Refresh Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "🏛️",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.portfolio_widget_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = RoyalGold,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(
                        text = when {
                            summary.isFromFirestore -> "FIRESTORE LIVE"
                            summary.hasLiveRates -> "LIVE FEED"
                            else -> "SYNCING"
                        },
                        color = when {
                            summary.isFromFirestore -> SuccessGreen
                            summary.hasLiveRates -> AmberOrange
                            else -> SecondarySilver
                        },
                        icon = if (summary.isFromFirestore) Icons.Default.CloudDone else Icons.Default.CloudSync,
                    )
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("portfolio_summary_refresh_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.portfolio_widget_refresh_cd),
                                tint = LightGold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hero Total Value Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.portfolio_widget_total_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Formatters.formatInr(summary.totalHoldingsValueInr),
                        style = MaterialTheme.typography.displayMedium,
                        color = LightGold,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("portfolio_summary_total_value"),
                    )
                }

                if (summary.totalInvestedBullionInr > 1.0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, pnlColor),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (isPositivePnl) {
                                    Icons.AutoMirrored.Filled.TrendingUp
                                } else {
                                    Icons.AutoMirrored.Filled.TrendingDown
                                },
                                contentDescription = if (isPositivePnl) "Portfolio gain" else "Portfolio loss",
                                tint = pnlColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${if (isPositivePnl) "+" else ""}${
                                    String.format(Locale.US, "%.2f", summary.unrealizedPnlPercent)
                                }%",
                                style = MonoSmallStyle,
                                color = pnlColor,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (summary.totalInvestedBullionInr > 1.0 || summary.availableWalletInr > 0.0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (summary.totalInvestedBullionInr > 1.0) {
                        Text(
                            text = "Invested: ${Formatters.formatInr(summary.totalInvestedBullionInr)} • P&L: ${
                                Formatters.formatInr(summary.unrealizedPnlInr)
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (summary.availableWalletInr > 0.0) {
                        Text(
                            text = "Cash: ${Formatters.formatInr(summary.availableWalletInr)}",
                            style = MonoSmallStyle,
                            color = PureWhite,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Metal Allocation Bar (Solid Gold + Solid Silver, zero gradients)
            if (summary.totalHoldingsValueInr > 0.0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "🥇 Gold ${String.format(Locale.US, "%.1f", summary.goldSharePercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoyalGold,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "🥈 Silver ${String.format(Locale.US, "%.1f", summary.silverSharePercent)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = SilverAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ObsidianCard),
                ) {
                    val goldWeight = summary.goldSharePercent.coerceAtLeast(0f)
                    val silverWeight = summary.silverSharePercent.coerceAtLeast(0f)
                    if (goldWeight > 0.2f) {
                        Box(
                            modifier = Modifier
                                .weight(goldWeight)
                                .fillMaxHeight()
                                .background(RoyalGold),
                        )
                    }
                    if (silverWeight > 0.2f) {
                        Box(
                            modifier = Modifier
                                .weight(silverWeight)
                                .fillMaxHeight()
                                .background(SilverAccent),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gold & Silver Holdings Cards Side by Side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Gold Holdings Card (24K + 22K + 18K)
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("portfolio_widget_gold_card"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = RoyalGold,
                                modifier = Modifier.size(8.dp),
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GOLD HOLDINGS",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoyalGold,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatInr(summary.totalGoldValueInr),
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("portfolio_summary_gold_value"),
                        )
                        Text(
                            text = Formatters.formatGrams(summary.totalGoldGrams),
                            style = MonoSmallStyle,
                            color = LightGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("portfolio_summary_gold_grams"),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "24K ${Formatters.formatGrams(summary.gold24kGrams, compact = true)} • 22K ${
                                Formatters.formatGrams(summary.gold22kGrams, compact = true)
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (summary.gold18kGrams > 0.0) {
                            Text(
                                text = "18K ${Formatters.formatGrams(summary.gold18kGrams, compact = true)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondarySilver,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Silver Holdings Card (999 Silver)
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SilverAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("portfolio_widget_silver_card"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = SilverAccent,
                                modifier = Modifier.size(8.dp),
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "999 SILVER",
                                style = MaterialTheme.typography.labelSmall,
                                color = SilverAccent,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = Formatters.formatInr(summary.totalSilverValueInr),
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("portfolio_summary_silver_value"),
                        )
                        Text(
                            text = Formatters.formatGrams(summary.silver999Grams),
                            style = MonoSmallStyle,
                            color = SilverAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("portfolio_summary_silver_grams"),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (summary.silverRatePerGramInr > 0.0) {
                                "Rate: ${Formatters.formatInr(summary.silverRatePerGramInr)}/g"
                            } else {
                                "Live silver rate syncing..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (!hasHoldings) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GoldBorderSubtle),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "✨ Aapka Bullion Vault khali hai. ₹101 se 24K Gold ya 999 Silver invest karna shuru karein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            if (onBuyGold != null || onBuySilver != null || onOpenVault != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBuyGold != null) {
                        OutlinedButton(
                            onClick = onBuyGold,
                            border = BorderStroke(1.dp, RoyalGold),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LightGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("portfolio_widget_buy_gold_btn"),
                        ) {
                            Text("Buy Gold", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onBuySilver != null) {
                        OutlinedButton(
                            onClick = onBuySilver,
                            border = BorderStroke(1.dp, SilverAccent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SilverAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("portfolio_widget_buy_silver_btn"),
                        ) {
                            Text("Buy Silver", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onOpenVault != null) {
                        TextButton(
                            onClick = onOpenVault,
                            colors = ButtonDefaults.textButtonColors(contentColor = RoyalGold),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("portfolio_widget_open_vault_btn"),
                        ) {
                            Text("Vault →", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
