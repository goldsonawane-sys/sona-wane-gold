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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
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
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPriceHistoryEntry
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
) {
    var layoutMode by remember { mutableStateOf(MarketTrendsLayoutMode.SPLIT) }
    var windowDays by remember { mutableStateOf(30) }

    val slicedHistory = remember(historyEntries, windowDays) {
        val sorted = historyEntries.sortedBy { it.timestampEpochMs }
        if (sorted.size > windowDays) sorted.takeLast(windowDays) else sorted
    }

    val goldProducer = remember { CartesianChartModelProducer() }
    val silverProducer = remember { CartesianChartModelProducer() }
    val compareProducer = remember { CartesianChartModelProducer() }

    val hasSeries = slicedHistory.size >= 2

    val goldStats = remember(slicedHistory) {
        computeMetalWindowStats(slicedHistory.map { it.gold24kPerGram })
    }
    val silverStats = remember(slicedHistory) {
        computeMetalWindowStats(slicedHistory.map { it.silver999PerGram })
    }

    LaunchedEffect(slicedHistory, layoutMode) {
        if (slicedHistory.size < 2) return@LaunchedEffect
        val labels = slicedHistory.map { it.label.ifBlank { it.dateStr.takeLast(5) } }
        val dates = slicedHistory.map { it.dateStr }
        val goldPrices = slicedHistory.map { it.gold24kPerGram }
        val silverPrices = slicedHistory.map { it.silver999PerGram }

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
                Icon(Icons.Default.ShowChart, contentDescription = null, tint = RoyalGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${windowDays}-Day Firestore Price Trends",
                        style = MaterialTheme.typography.titleMedium,
                        color = LightGold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Firestore historical feed",
                            tint = if (hasSeries) SuccessGreen else SecondarySilver,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasSeries) {
                                "Firestore • ${slicedHistory.size} daily snapshots"
                            } else {
                                "Firestore • Syncing market_price_history"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (rates.isLive) "LIVE + 30D" else "${windowDays}D DB",
                    color = if (rates.isLive) SuccessGreen else AmberOrange,
                )
                IconButton(
                    onClick = onSyncHistory,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("market_trends_sync_btn"),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Sync 30-day Firestore price history",
                        tint = RoyalGold,
                    )
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

        // Timeframe slice chips (7D, 14D, 30D)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(7 to "7D", 14 to "14D", 30 to "30D").forEach { (days, label) ->
                FilterChip(
                    selected = windowDays == days,
                    onClick = { windowDays = days },
                    label = { Text(label) },
                    modifier = Modifier.testTag("trends_window_$label"),
                )
            }
        }

        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
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
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f),
                    )
                    GoldOutlinedButton(
                        text = "Retry",
                        onClick = onSyncHistory,
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
                    if (isLoading) {
                        CircularProgressIndicator(color = RoyalGold, modifier = Modifier.size(28.dp))
                        Text(
                            text = "Fetching 30-day Gold & Silver history from Firestore...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                    } else {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = "No price history yet",
                            tint = RoyalGold,
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = "No historical snapshots cached in Firestore yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                        GoldOutlinedButton(
                            text = "Sync 30-Day History",
                            onClick = onSyncHistory,
                        )
                    }
                }
            }
        } else {
            when (layoutMode) {
                MarketTrendsLayoutMode.SPLIT -> {
                    SingleMetalVicoGraphCard(
                        title = "Gold 24K • ${windowDays}-Day History (₹/g)",
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
                        title = "Silver 999 • ${windowDays}-Day History (₹/g)",
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
                        title = "Gold 24K • ${windowDays}-Day History (₹/g)",
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
                        title = "Silver 999 • ${windowDays}-Day History (₹/g)",
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
                        historyEntries = slicedHistory,
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
    historyEntries: List<MarketPriceHistoryEntry>,
    goldChangePct: Double,
    silverChangePct: Double,
    modelProducer: CartesianChartModelProducer,
) {
    val minMax = remember(historyEntries) {
        val gBase = historyEntries.firstOrNull()?.gold24kPerGram?.takeIf { it > 0.0 } ?: 1.0
        val sBase = historyEntries.firstOrNull()?.silver999PerGram?.takeIf { it > 0.0 } ?: 1.0
        val combined = historyEntries.map { ((it.gold24kPerGram - gBase) / gBase) * 100.0 } +
            historyEntries.map { ((it.silver999PerGram - sBase) / sBase) * 100.0 }
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
            imageVector = if (positive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
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
