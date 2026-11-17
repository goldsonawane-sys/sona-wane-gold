package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.remote.MarketRateService
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ElevatedObsidian
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
import com.example.util.Formatters
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.insets
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.roundToInt

private enum class MarketTrendsLayoutMode(val label: String) {
    SPLIT("Gold & Silver Graphs"),
    COMPARE("Normalized (%)"),
    GOLD_ONLY("Gold Focus"),
    SILVER_ONLY("Silver Focus"),
}

data class MarketTrendChartPoint(
    val label: String,
    val dateStr: String,
    val gold24kPerGram: Double,
    val silver999PerGram: Double,
    val timestampEpochMs: Long,
    val source: String,
)

/**
 * Normalizes real-time external financial API trend series (XAU/XAG + Frankfurter FX)
 * and fallback cached history entries into aligned Gold & Silver chart points.
 */
object MarketTrendsApiSeriesBuilder {

    fun buildUnifiedSeries(
        externalTrend: DualMetalTrendSeries?,
        historyEntries: List<MarketPriceHistoryEntry>,
        liveRates: LiveMarketRates,
        windowDays: Int,
    ): List<MarketTrendChartPoint> {
        val basePoints: List<MarketTrendChartPoint> = if (externalTrend != null &&
            externalTrend.goldPoints.size >= 2 &&
            externalTrend.silverPoints.size >= 2
        ) {
            val count = minOf(externalTrend.goldPoints.size, externalTrend.silverPoints.size)
            (0 until count).map { idx ->
                val gPt = externalTrend.goldPoints[idx]
                val sPt = externalTrend.silverPoints[idx]
                val cleanLabel = gPt.label.ifBlank { "#${idx + 1}" }
                MarketTrendChartPoint(
                    label = cleanLabel,
                    dateStr = cleanLabel,
                    gold24kPerGram = gPt.price,
                    silver999PerGram = sPt.price,
                    timestampEpochMs = idx.toLong(),
                    source = "External Financial API (XAU/XAG + FX)",
                )
            }
        } else {
            historyEntries.sortedBy { it.timestampEpochMs }.map { entry ->
                MarketTrendChartPoint(
                    label = entry.label.ifBlank { entry.dateStr.takeLast(5) },
                    dateStr = entry.dateStr,
                    gold24kPerGram = entry.gold24kPerGram,
                    silver999PerGram = entry.silver999PerGram,
                    timestampEpochMs = entry.timestampEpochMs,
                    source = entry.source.ifBlank { "Cached Market Snapshot" },
                )
            }
        }

        if (basePoints.isEmpty()) return emptyList()

        // Anchor tail to the freshest live external financial API spot tick when available
        val merged = if (liveRates.gold24kBuyPerGram > 0.0 && liveRates.silver999BuyPerGram > 0.0) {
            val mutable = basePoints.toMutableList()
            val last = mutable.last()
            if (last.label.startsWith("LIVE", ignoreCase = true)) {
                mutable[mutable.lastIndex] = last.copy(
                    gold24kPerGram = liveRates.gold24kBuyPerGram,
                    silver999PerGram = liveRates.silver999BuyPerGram,
                    source = liveRates.sourceName.ifBlank { last.source },
                )
            } else {
                mutable.add(
                    MarketTrendChartPoint(
                        label = "LIVE",
                        dateStr = "LIVE Spot",
                        gold24kPerGram = liveRates.gold24kBuyPerGram,
                        silver999PerGram = liveRates.silver999BuyPerGram,
                        timestampEpochMs = liveRates.lastUpdatedEpochMs.coerceAtLeast(last.timestampEpochMs + 1L),
                        source = liveRates.sourceName.ifBlank { "External Financial API" },
                    )
                )
            }
            mutable
        } else {
            basePoints
        }

        return if (merged.size > windowDays) merged.takeLast(windowDays) else merged
    }
}

private val HistoryLabelsKey = ExtraStore.Key<List<String>>()
private val HistoryDatesKey = ExtraStore.Key<List<String>>()
private val HistoryGoldKey = ExtraStore.Key<List<Double>>()
private val HistorySilverKey = ExtraStore.Key<List<Double>>()

@Composable
fun MarketTrendsDashboard(
    historyEntries: List<MarketPriceHistoryEntry>,
    rates: LiveMarketRates,
    isLoading: Boolean,
    errorMessage: String? = null,
    onSyncHistory: () -> Unit,
    modifier: Modifier = Modifier,
    externalTrendSeries: DualMetalTrendSeries? = null,
    marketRateService: MarketRateService? = null,
    autoFetchExternalApi: Boolean = true,
    onRefreshExternalRates: (() -> Unit)? = null,
) {
    var layoutMode by remember { mutableStateOf(MarketTrendsLayoutMode.SPLIT) }
    var windowDays by remember { mutableIntStateOf(30) }
    var refreshNonce by remember { mutableIntStateOf(0) }

    var fetchedApiRates by remember { mutableStateOf<LiveMarketRates?>(null) }
    var fetchedApiTrend by remember { mutableStateOf<DualMetalTrendSeries?>(null) }
    var apiLoading by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<String?>(null) }

    val rangeKey = when (windowDays) {
        7 -> "1W"
        14 -> "14D"
        30 -> "30D"
        else -> "3M"
    }

    val serviceInstance = remember(marketRateService, autoFetchExternalApi) {
        marketRateService ?: if (autoFetchExternalApi) MarketRateService() else null
    }

    // Fetch real-time Gold & Silver spot rates and trend curves from external financial APIs
    LaunchedEffect(autoFetchExternalApi, externalTrendSeries, rangeKey, refreshNonce) {
        if (!autoFetchExternalApi || externalTrendSeries != null || serviceInstance == null) return@LaunchedEffect
        apiLoading = true
        apiError = null
        try {
            val latestRates = if (refreshNonce > 0 || !rates.isLive || rates.gold24kBuyPerGram <= 0.0) {
                serviceInstance.fetchLiveRates()
            } else {
                rates
            }
            if (latestRates.isLive && latestRates.gold24kBuyPerGram > 0.0 && latestRates.silver999BuyPerGram > 0.0) {
                fetchedApiRates = latestRates
                val series = serviceInstance.fetchDualMetalTrend(
                    goldCurrentPerGram = latestRates.gold24kBuyPerGram,
                    silverCurrentPerGram = latestRates.silver999BuyPerGram,
                    rangeKey = rangeKey,
                )
                if (series.goldPoints.size >= 2 && series.silverPoints.size >= 2) {
                    fetchedApiTrend = series
                } else {
                    apiError = "External trend curve temporarily unavailable; showing cached snapshots."
                }
            } else if (!latestRates.errorMessage.isNullOrBlank()) {
                apiError = latestRates.errorMessage
            }
        } catch (e: Exception) {
            apiError = e.localizedMessage ?: "External financial API unreachable"
        } finally {
            apiLoading = false
        }
    }

    val effectiveRates = remember(rates, fetchedApiRates) {
        fetchedApiRates?.takeIf { it.isLive && it.gold24kBuyPerGram > 0.0 } ?: rates
    }
    val activeTrend = externalTrendSeries ?: fetchedApiTrend
    val slicedPoints = remember(activeTrend, historyEntries, effectiveRates, windowDays) {
        MarketTrendsApiSeriesBuilder.buildUnifiedSeries(
            externalTrend = activeTrend,
            historyEntries = historyEntries,
            liveRates = effectiveRates,
            windowDays = windowDays,
        )
    }

    val goldProducer = remember { CartesianChartModelProducer() }
    val silverProducer = remember { CartesianChartModelProducer() }
    val compareProducer = remember { CartesianChartModelProducer() }

    val hasSeries = slicedPoints.size >= 2
    val usingExternalApi = (activeTrend != null && activeTrend.goldPoints.size >= 2) || effectiveRates.isLive

    val goldStats = remember(slicedPoints) {
        computeMetalWindowStats(slicedPoints.map { it.gold24kPerGram })
    }
    val silverStats = remember(slicedPoints) {
        computeMetalWindowStats(slicedPoints.map { it.silver999PerGram })
    }

    val effectiveLoading = isLoading || apiLoading
    val combinedError = apiError?.takeIf { !hasSeries } ?: errorMessage

    LaunchedEffect(slicedPoints, layoutMode) {
        if (slicedPoints.size < 2) return@LaunchedEffect
        val labels = slicedPoints.map { it.label }
        val dates = slicedPoints.map { it.dateStr }
        val goldPrices = slicedPoints.map { it.gold24kPerGram }
        val silverPrices = slicedPoints.map { it.silver999PerGram }

        if (layoutMode == MarketTrendsLayoutMode.SPLIT || layoutMode == MarketTrendsLayoutMode.GOLD_ONLY) {
            goldProducer.runTransaction {
                lineSeries { series(goldPrices) }
                extras { store ->
                    store[HistoryLabelsKey] = labels
                    store[HistoryDatesKey] = dates
                    store[HistoryGoldKey] = goldPrices
                }
            }
        }
        if (layoutMode == MarketTrendsLayoutMode.SPLIT || layoutMode == MarketTrendsLayoutMode.SILVER_ONLY) {
            silverProducer.runTransaction {
                lineSeries { series(silverPrices) }
                extras { store ->
                    store[HistoryLabelsKey] = labels
                    store[HistoryDatesKey] = dates
                    store[HistorySilverKey] = silverPrices
                }
            }
        }
        if (layoutMode == MarketTrendsLayoutMode.COMPARE) {
            val gBase = goldPrices.firstOrNull()?.takeIf { it > 0.0 } ?: 1.0
            val sBase = silverPrices.firstOrNull()?.takeIf { it > 0.0 } ?: 1.0
            val gNorm = goldPrices.map { ((it - gBase) / gBase) * 100.0 }
            val sNorm = silverPrices.map { ((it - sBase) / sBase) * 100.0 }
            compareProducer.runTransaction {
                lineSeries {
                    series(gNorm)
                    series(sNorm)
                }
                extras { store ->
                    store[HistoryLabelsKey] = labels
                    store[HistoryDatesKey] = dates
                    store[HistoryGoldKey] = goldPrices
                    store[HistorySilverKey] = silverPrices
                }
            }
        }
    }

    LuxuryCard(
        highlightGold = true,
        modifier = modifier.testTag("market_trends_dashboard"),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = RoyalGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${windowDays}-Day Gold & Silver Trends",
                        style = MaterialTheme.typography.titleMedium,
                        color = LightGold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "External financial API feed",
                            tint = if (usingExternalApi) SuccessGreen else SecondarySilver,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                usingExternalApi && hasSeries ->
                                    "External Financial API • ${slicedPoints.size} pts (XAU/XAG + FX)"
                                hasSeries ->
                                    "Cached Snapshot • ${slicedPoints.size} pts"
                                else ->
                                    "External Financial API • Fetching live trends"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (effectiveRates.isLive) "LIVE API" else "${windowDays}D FEED",
                    color = if (effectiveRates.isLive) SuccessGreen else AmberOrange,
                )
                IconButton(
                    onClick = {
                        refreshNonce += 1
                        onRefreshExternalRates?.invoke()
                        onSyncHistory()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("market_trends_sync_btn"),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh real-time financial API trends",
                        tint = RoyalGold,
                    )
                }
            }
        }

        // Live Spot Summary Ticker Strip from External Financial API
        if (effectiveRates.gold24kBuyPerGram > 0.0 || effectiveRates.silver999BuyPerGram > 0.0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedObsidian,
                    border = BorderStroke(1.dp, RoyalGold.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("24K Gold Spot", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        Text(
                            text = "${Formatters.formatInr(effectiveRates.gold24kBuyPerGram)}/g",
                            style = MonoSmallStyle,
                            color = LightGold,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElevatedObsidian,
                    border = BorderStroke(1.dp, SilverAccent.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text("999 Silver Spot", style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
                        Text(
                            text = "${Formatters.formatInr(effectiveRates.silver999BuyPerGram)}/g",
                            style = MonoSmallStyle,
                            color = SilverAccent,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Layout mode chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarketTrendsLayoutMode.entries.forEach { mode ->
                val selected = layoutMode == mode
                FilterChip(
                    selected = selected,
                    onClick = { layoutMode = mode },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (mode) {
                            MarketTrendsLayoutMode.SILVER_ONLY -> SilverAccent
                            MarketTrendsLayoutMode.COMPARE -> AmberOrange
                            else -> RoyalGold
                        },
                        selectedLabelColor = DeepBlack,
                    ),
                    modifier = Modifier.testTag("trends_mode_${mode.name.lowercase()}"),
                )
            }
        }

        // Timeframe slice chips (7D, 14D, 30D, 90D)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(7 to "7D", 14 to "14D", 30 to "30D", 90 to "90D").forEach { (days, label) ->
                FilterChip(
                    selected = windowDays == days,
                    onClick = { windowDays = days },
                    label = { Text(label) },
                    modifier = Modifier.testTag("trends_window_$label"),
                )
            }
        }

        AnimatedVisibility(visible = !combinedError.isNullOrBlank()) {
            Surface(
                color = ErrorRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ErrorRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = combinedError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f),
                    )
                    GoldOutlinedButton(
                        text = "Retry",
                        onClick = {
                            refreshNonce += 1
                            onRefreshExternalRates?.invoke()
                            onSyncHistory()
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (!hasSeries) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ElevatedObsidian)
                    .border(1.dp, GoldBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (effectiveLoading) {
                        CircularProgressIndicator(color = RoyalGold, modifier = Modifier.size(28.dp))
                        Text(
                            text = "Fetching live Gold & Silver trends from financial API...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "No price trend data yet",
                            tint = RoyalGold,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = "External price trend feed not loaded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                        GoldOutlinedButton(
                            text = "Fetch Live Market Trends",
                            onClick = {
                                refreshNonce += 1
                                onRefreshExternalRates?.invoke()
                                onSyncHistory()
                            },
                        )
                    }
                }
            }
        } else {
            when (layoutMode) {
                MarketTrendsLayoutMode.SPLIT -> {
                    SingleMetalVicoGraphCard(
                        title = "Gold 24K • ${windowDays}-Day Trend (₹/g)",
                        lineColor = RoyalGold,
                        stats = goldStats,
                        unitPrefix = "₹",
                        decimals = 0,
                        modelProducer = goldProducer,
                        isGold = true,
                        chartTestTag = "vico_gold_30d_chart",
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SingleMetalVicoGraphCard(
                        title = "Silver 999 • ${windowDays}-Day Trend (₹/g)",
                        lineColor = SilverAccent,
                        stats = silverStats,
                        unitPrefix = "₹",
                        decimals = 1,
                        modelProducer = silverProducer,
                        isGold = false,
                        chartTestTag = "vico_silver_30d_chart",
                    )
                }

                MarketTrendsLayoutMode.GOLD_ONLY -> {
                    SingleMetalVicoGraphCard(
                        title = "Gold 24K • ${windowDays}-Day Trend (₹/g)",
                        lineColor = RoyalGold,
                        stats = goldStats,
                        unitPrefix = "₹",
                        decimals = 0,
                        modelProducer = goldProducer,
                        isGold = true,
                        chartHeight = 230.dp,
                        chartTestTag = "vico_gold_30d_chart",
                    )
                }

                MarketTrendsLayoutMode.SILVER_ONLY -> {
                    SingleMetalVicoGraphCard(
                        title = "Silver 999 • ${windowDays}-Day Trend (₹/g)",
                        lineColor = SilverAccent,
                        stats = silverStats,
                        unitPrefix = "₹",
                        decimals = 1,
                        modelProducer = silverProducer,
                        isGold = false,
                        chartHeight = 230.dp,
                        chartTestTag = "vico_silver_30d_chart",
                    )
                }

                MarketTrendsLayoutMode.COMPARE -> {
                    CompareNormalizedVicoCard(
                        points = slicedPoints,
                        goldChangePct = goldStats.changePct,
                        silverChangePct = silverStats.changePct,
                        modelProducer = compareProducer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetalHistoryStatTile(
                    title = "Gold 24K (${windowDays}D)",
                    accentColor = RoyalGold,
                    stats = goldStats,
                    subtitle = "22K: ${Formatters.formatInr(goldStats.latest * 22.0 / 24.0)}/g",
                    modifier = Modifier.weight(1f),
                )
                MetalHistoryStatTile(
                    title = "Silver 999 (${windowDays}D)",
                    accentColor = SilverAccent,
                    stats = silverStats,
                    subtitle = "1 kg: ${Formatters.formatInr(silverStats.latest * 1000.0)}",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class MetalWindowStats(
    val latest: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val avg: Double = 0.0,
    val changePct: Double = 0.0,
    val minBound: Double = 0.0,
    val maxBound: Double = 100.0,
)

private fun computeMetalWindowStats(values: List<Double>): MetalWindowStats {
    if (values.isEmpty()) return MetalWindowStats()
    val first = values.first().takeIf { it > 0.0 } ?: 1.0
    val last = values.last()
    val minP = values.minOrNull() ?: 0.0
    val maxP = values.maxOrNull() ?: 100.0
    val pad = ((maxP - minP) * 0.14).coerceAtLeast(maxP * 0.004)
    return MetalWindowStats(
        latest = last,
        high = maxP,
        low = minP,
        avg = values.average(),
        changePct = ((last - first) / first) * 100.0,
        minBound = (minP - pad).coerceAtLeast(0.0),
        maxBound = maxP + pad,
    )
}

@Composable
private fun SingleMetalVicoGraphCard(
    title: String,
    lineColor: Color,
    stats: MetalWindowStats,
    unitPrefix: String,
    decimals: Int,
    modelProducer: CartesianChartModelProducer,
    isGold: Boolean,
    chartHeight: androidx.compose.ui.unit.Dp = 175.dp,
    chartTestTag: String,
) {
    val positive = stats.changePct >= 0.0
    Surface(
        color = ElevatedObsidian,
        border = BorderStroke(1.dp, lineColor.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(lineColor),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.labelLarge, color = PureWhite)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Formatters.formatInr(stats.latest) + "/g",
                        style = MonoSmallStyle,
                        color = lineColor,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${if (positive) "+" else ""}${"%.2f".format(stats.changePct)}%",
                        style = MonoSmallStyle,
                        color = if (positive) SuccessGreen else ErrorRed,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val rangeProvider = remember(stats.minBound, stats.maxBound) {
                CartesianLayerRangeProvider.fixed(minY = stats.minBound, maxY = stats.maxBound)
            }
            val line = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    fill(
                        ShaderProvider.verticalGradient(
                            arrayOf(lineColor.copy(alpha = 0.30f), Color.Transparent)
                        )
                    )
                ),
                pointConnector = LineCartesianLayer.PointConnector.cubic(0.4f),
            )
            val axisLabel = rememberTextComponent(
                color = SecondarySilver,
                textSize = MaterialTheme.typography.labelSmall.fontSize,
            )
            val guideline = rememberLineComponent(
                fill = fill(Color.White.copy(alpha = 0.07f)),
                thickness = 1.dp,
            )
            val yFormatter = remember(unitPrefix, decimals) {
                CartesianValueFormatter { _, value, _ ->
                    if (decimals == 0) "$unitPrefix${value.roundToInt()}"
                    else "$unitPrefix${"%.1f".format(value)}"
                }
            }
            val xFormatter = remember {
                CartesianValueFormatter { context, x, _ ->
                    val labels = context.model.extraStore.getOrNull(HistoryLabelsKey)
                    val idx = x.roundToInt()
                    if (labels != null && idx in labels.indices) labels[idx] else ""
                }
            }
            val markerLabel = rememberTextComponent(
                color = PureWhite,
                textSize = MaterialTheme.typography.labelSmall.fontSize,
                background = rememberShapeComponent(
                    fill = fill(ObsidianCard),
                    shape = CorneredShape.rounded(allPercent = 25),
                    strokeFill = fill(lineColor),
                    strokeThickness = 1.dp,
                ),
                padding = insets(horizontal = 10.dp, vertical = 6.dp),
            )
            val markerGuideline = rememberLineComponent(
                fill = fill(lineColor.copy(alpha = 0.65f)),
                thickness = 1.5.dp,
            )
            val markerFormatter = remember(isGold) {
                DefaultCartesianMarker.ValueFormatter { context, targets ->
                    val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
                    val idx = lineTarget?.points?.firstOrNull()?.entry?.x?.roundToInt() ?: 0
                    val dates = context.model.extraStore.getOrNull(HistoryDatesKey).orEmpty()
                    val prices = context.model.extraStore.getOrNull(
                        if (isGold) HistoryGoldKey else HistorySilverKey
                    ).orEmpty()
                    val date = dates.getOrElse(idx) { "" }
                    val price = prices.getOrNull(idx)
                    val metalLabel = if (isGold) "24K Au" else "999 Ag"
                    "$date • $metalLabel: ${price?.let { Formatters.formatInr(it) } ?: "--"}/g"
                }
            }
            val marker = rememberDefaultCartesianMarker(
                label = markerLabel,
                valueFormatter = markerFormatter,
                guideline = markerGuideline,
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(listOf(line)),
                        rangeProvider = rangeProvider,
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        guideline = guideline,
                        valueFormatter = yFormatter,
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = axisLabel,
                        guideline = null,
                        valueFormatter = xFormatter,
                        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 6 }) },
                    ),
                    marker = marker,
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .testTag(chartTestTag),
            )
        }
    }
}

@Composable
private fun CompareNormalizedVicoCard(
    points: List<MarketTrendChartPoint>,
    goldChangePct: Double,
    silverChangePct: Double,
    modelProducer: CartesianChartModelProducer,
) {
    val minMax = remember(points) {
        val gBase = points.firstOrNull()?.gold24kPerGram?.takeIf { it > 0.0 } ?: 1.0
        val sBase = points.firstOrNull()?.silver999PerGram?.takeIf { it > 0.0 } ?: 1.0
        val combined = points.map { ((it.gold24kPerGram - gBase) / gBase) * 100.0 } +
            points.map { ((it.silver999PerGram - sBase) / sBase) * 100.0 }
        val lo = (combined.minOrNull() ?: -2.0) - 0.5
        val hi = (combined.maxOrNull() ?: 2.0) + 0.5
        lo to hi
    }

    Surface(
        color = ElevatedObsidian,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompareLegendItem(RoyalGold, "Gold 24K", goldChangePct)
                CompareLegendItem(SilverAccent, "Silver 999", silverChangePct)
            }
            Spacer(modifier = Modifier.height(8.dp))

            val rangeProvider = remember(minMax) {
                CartesianLayerRangeProvider.fixed(minY = minMax.first, maxY = minMax.second)
            }
            val goldLine = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(RoyalGold)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    fill(ShaderProvider.verticalGradient(arrayOf(RoyalGold.copy(alpha = 0.25f), Color.Transparent)))
                ),
                pointConnector = LineCartesianLayer.PointConnector.cubic(0.4f),
            )
            val silverLine = LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(fill(SilverAccent)),
                areaFill = LineCartesianLayer.AreaFill.single(
                    fill(ShaderProvider.verticalGradient(arrayOf(SilverAccent.copy(alpha = 0.20f), Color.Transparent)))
                ),
                pointConnector = LineCartesianLayer.PointConnector.cubic(0.4f),
            )
            val axisLabel = rememberTextComponent(
                color = SecondarySilver,
                textSize = MaterialTheme.typography.labelSmall.fontSize,
            )
            val guideline = rememberLineComponent(
                fill = fill(Color.White.copy(alpha = 0.07f)),
                thickness = 1.dp,
            )
            val yFormatter = remember {
                CartesianValueFormatter { _, value, _ ->
                    val sign = if (value >= 0.0) "+" else ""
                    "$sign${"%.1f".format(value)}%"
                }
            }
            val xFormatter = remember {
                CartesianValueFormatter { context, x, _ ->
                    val labels = context.model.extraStore.getOrNull(HistoryLabelsKey)
                    val idx = x.roundToInt()
                    if (labels != null && idx in labels.indices) labels[idx] else ""
                }
            }
            val markerLabel = rememberTextComponent(
                color = PureWhite,
                textSize = MaterialTheme.typography.labelSmall.fontSize,
                background = rememberShapeComponent(
                    fill = fill(ObsidianCard),
                    shape = CorneredShape.rounded(allPercent = 25),
                    strokeFill = fill(RoyalGold),
                    strokeThickness = 1.dp,
                ),
                padding = insets(horizontal = 10.dp, vertical = 6.dp),
            )
            val markerGuideline = rememberLineComponent(
                fill = fill(AmberOrange.copy(alpha = 0.65f)),
                thickness = 1.5.dp,
            )
            val markerFormatter = remember {
                DefaultCartesianMarker.ValueFormatter { context, targets ->
                    val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
                    val idx = lineTarget?.points?.firstOrNull()?.entry?.x?.roundToInt() ?: 0
                    val dates = context.model.extraStore.getOrNull(HistoryDatesKey).orEmpty()
                    val gList = context.model.extraStore.getOrNull(HistoryGoldKey).orEmpty()
                    val sList = context.model.extraStore.getOrNull(HistorySilverKey).orEmpty()
                    val date = dates.getOrElse(idx) { "" }
                    val g = gList.getOrNull(idx)
                    val s = sList.getOrNull(idx)
                    "$date | Au: ${g?.let { Formatters.formatInr(it) } ?: "--"} | Ag: ${s?.let { Formatters.formatInr(it) } ?: "--"}"
                }
            }
            val marker = rememberDefaultCartesianMarker(
                label = markerLabel,
                valueFormatter = markerFormatter,
                guideline = markerGuideline,
            )

            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(listOf(goldLine, silverLine)),
                        rangeProvider = rangeProvider,
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        label = axisLabel,
                        guideline = guideline,
                        valueFormatter = yFormatter,
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = axisLabel,
                        guideline = null,
                        valueFormatter = xFormatter,
                        itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 6 }) },
                    ),
                    marker = marker,
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .testTag("vico_compare_30d_chart"),
            )
        }
    }
}

@Composable
private fun CompareLegendItem(
    color: Color,
    label: String,
    pct: Double,
) {
    val positive = pct >= 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = PureWhite)
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (positive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = null,
            tint = if (positive) SuccessGreen else ErrorRed,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "${if (positive) "+" else ""}${"%.2f".format(pct)}%",
            style = MonoSmallStyle,
            color = if (positive) SuccessGreen else ErrorRed,
        )
    }
}

@Composable
private fun MetalHistoryStatTile(
    title: String,
    accentColor: Color,
    stats: MetalWindowStats,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val positive = stats.changePct >= 0.0
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = accentColor, fontWeight = FontWeight.Bold)
                Text(
                    text = "${if (positive) "+" else ""}${"%.2f".format(stats.changePct)}%",
                    style = MonoSmallStyle,
                    color = if (positive) SuccessGreen else ErrorRed,
                )
            }
            Text("High: ${Formatters.formatInr(stats.high)}/g", style = MonoSmallStyle, color = PureWhite)
            Text("Low:  ${Formatters.formatInr(stats.low)}/g", style = MonoSmallStyle, color = SecondarySilver)
            Text("Avg:  ${Formatters.formatInr(stats.avg)}/g", style = MonoSmallStyle, color = SecondarySilver)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LightGold)
        }
    }
}
