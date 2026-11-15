package com.example.ui.components

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
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.LiveMarketRates
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

enum class TrendChartViewMode(val label: String) {
    BOTH("Gold vs Silver (%)"),
    GOLD_24K("Gold 24K (₹/g)"),
    SILVER_999("Silver 999 (₹/g)"),
}

private val DateLabelsExtraKey = ExtraStore.Key<List<String>>()
private val GoldPricesExtraKey = ExtraStore.Key<List<Double>>()
private val SilverPricesExtraKey = ExtraStore.Key<List<Double>>()
private val ChartModeExtraKey = ExtraStore.Key<String>()

@Composable
fun VicoBullionTrendDashboard(
    trendSeries: DualMetalTrendSeries,
    rates: LiveMarketRates,
    isLoading: Boolean,
    rangeOptions: List<String> = listOf("1W", "30D", "3M", "1Y"),
    onSelectRange: (String) -> Unit,
    onRefreshLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMode by remember { mutableStateOf(TrendChartViewMode.BOTH) }
    var selectedRange by remember(trendSeries.rangeKey) { mutableStateOf(trendSeries.rangeKey.ifBlank { "30D" }) }

    val modelProducer = remember { CartesianChartModelProducer() }

    val hasData = trendSeries.goldPoints.size >= 2 && trendSeries.silverPoints.size >= 2

    val minMaxBounds = remember(trendSeries, selectedMode) {
        when (selectedMode) {
            TrendChartViewMode.BOTH -> {
                val combined = (trendSeries.goldNormalizedPct + trendSeries.silverNormalizedPct).map { it.price }
                val lo = (combined.minOrNull() ?: -2.0) - 0.5
                val hi = (combined.maxOrNull() ?: 2.0) + 0.5
                lo to hi
            }
            TrendChartViewMode.GOLD_24K -> {
                val vals = trendSeries.goldPoints.map { it.price }
                val minP = vals.minOrNull() ?: 0.0
                val maxP = vals.maxOrNull() ?: 100.0
                val pad = ((maxP - minP) * 0.12).coerceAtLeast(minP * 0.002)
                (minP - pad).coerceAtLeast(0.0) to (maxP + pad)
            }
            TrendChartViewMode.SILVER_999 -> {
                val vals = trendSeries.silverPoints.map { it.price }
                val minP = vals.minOrNull() ?: 0.0
                val maxP = vals.maxOrNull() ?: 10.0
                val pad = ((maxP - minP) * 0.12).coerceAtLeast(minP * 0.003)
                (minP - pad).coerceAtLeast(0.0) to (maxP + pad)
            }
        }
    }

    LaunchedEffect(trendSeries, selectedMode) {
        if (!hasData) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                when (selectedMode) {
                    TrendChartViewMode.BOTH -> {
                        series(trendSeries.goldNormalizedPct.map { it.price })
                        series(trendSeries.silverNormalizedPct.map { it.price })
                    }
                    TrendChartViewMode.GOLD_24K -> {
                        series(trendSeries.goldPoints.map { it.price })
                    }
                    TrendChartViewMode.SILVER_999 -> {
                        series(trendSeries.silverPoints.map { it.price })
                    }
                }
            }
            extras { store ->
                store[DateLabelsExtraKey] = trendSeries.goldPoints.map { it.label }
                store[GoldPricesExtraKey] = trendSeries.goldPoints.map { it.price }
                store[SilverPricesExtraKey] = trendSeries.silverPoints.map { it.price }
                store[ChartModeExtraKey] = selectedMode.name
            }
        }
    }

    LuxuryCard(
        highlightGold = true,
        modifier = modifier.testTag("vico_trend_chart_card"),
    ) {
        // Header row with real-time indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AutoGraph, contentDescription = null, tint = RoyalGold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$selectedRange Real-Time Price Trends",
                        style = MaterialTheme.typography.titleMedium,
                        color = LightGold,
                    )
                    Text(
                        text = if (rates.isLive) "Live sync • ${Formatters.formatEpoch(rates.lastUpdatedEpochMs)}"
                        else "Cached spot • Tap refresh for live sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (rates.isLive) "LIVE 20s" else "DELAYED",
                    color = if (rates.isLive) SuccessGreen else AmberOrange,
                )
                IconButton(
                    onClick = onRefreshLive,
                    modifier = Modifier.testTag("vico_refresh_btn"),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync live trends", tint = RoyalGold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chart View Mode Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrendChartViewMode.entries.forEach { mode ->
                val selected = selectedMode == mode
                val chipColor = when (mode) {
                    TrendChartViewMode.BOTH -> AmberOrange
                    TrendChartViewMode.GOLD_24K -> RoyalGold
                    TrendChartViewMode.SILVER_999 -> SilverAccent
                }
                FilterChip(
                    selected = selected,
                    onClick = { selectedMode = mode },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor,
                        selectedLabelColor = DeepBlack,
                    ),
                    modifier = Modifier.testTag("chart_mode_${mode.name.lowercase()}"),
                )
            }
        }

        // Range selector pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rangeOptions.forEach { rng ->
                FilterChip(
                    selected = selectedRange == rng,
                    onClick = {
                        selectedRange = rng
                        onSelectRange(rng)
                    },
                    label = { Text(rng) },
                    modifier = Modifier.testTag("chart_range_$rng"),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Interactive Legend Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedMode == TrendChartViewMode.BOTH || selectedMode == TrendChartViewMode.GOLD_24K) {
                LegendBadge(
                    color = RoyalGold,
                    label = "Gold 24K",
                    pct = trendSeries.gold30dChangePct,
                )
            }
            if (selectedMode == TrendChartViewMode.BOTH || selectedMode == TrendChartViewMode.SILVER_999) {
                LegendBadge(
                    color = SilverAccent,
                    label = "Silver 999",
                    pct = trendSeries.silver30dChangePct,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Vico Interactive Chart Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ElevatedObsidian)
                .border(1.dp, GoldBorderSubtle, RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!hasData) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoading) {
                        CircularProgressIndicator(color = RoyalGold, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = if (isLoading) "Loading $selectedRange Gold & Silver trend..."
                        else "Tap refresh to sync real-time $selectedRange market chart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondarySilver,
                    )
                }
            } else {
                val rangeProvider = remember(minMaxBounds) {
                    CartesianLayerRangeProvider.fixed(
                        minY = minMaxBounds.first,
                        maxY = minMaxBounds.second,
                    )
                }

                val goldLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(RoyalGold)),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        fill(
                            ShaderProvider.verticalGradient(
                                arrayOf(RoyalGold.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                    ),
                    pointConnector = LineCartesianLayer.PointConnector.cubic(0.45f),
                )

                val silverLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(SilverAccent)),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        fill(
                            ShaderProvider.verticalGradient(
                                arrayOf(SilverAccent.copy(alpha = 0.22f), Color.Transparent)
                            )
                        )
                    ),
                    pointConnector = LineCartesianLayer.PointConnector.cubic(0.45f),
                )

                val activeLines = remember(selectedMode, goldLine, silverLine) {
                    when (selectedMode) {
                        TrendChartViewMode.BOTH -> listOf(goldLine, silverLine)
                        TrendChartViewMode.GOLD_24K -> listOf(goldLine)
                        TrendChartViewMode.SILVER_999 -> listOf(silverLine)
                    }
                }

                val axisLabel = rememberTextComponent(
                    color = SecondarySilver,
                    textSize = MaterialTheme.typography.labelSmall.fontSize,
                )

                val guideline = rememberLineComponent(
                    fill = fill(Color.White.copy(alpha = 0.08f)),
                    thickness = 1.dp,
                )

                val yFormatter = remember(selectedMode) {
                    CartesianValueFormatter { _, value, _ ->
                        when (selectedMode) {
                            TrendChartViewMode.BOTH -> {
                                val sign = if (value >= 0) "+" else ""
                                "$sign${"%.1f".format(value)}%"
                            }
                            TrendChartViewMode.GOLD_24K -> "₹${value.roundToInt()}"
                            TrendChartViewMode.SILVER_999 -> "₹${"%.1f".format(value)}"
                        }
                    }
                }

                val xFormatter = remember {
                    CartesianValueFormatter { context, x, _ ->
                        val labels = context.model.extraStore.getOrNull(DateLabelsExtraKey)
                        val idx = x.roundToInt()
                        if (labels != null && idx in labels.indices) {
                            labels[idx]
                        } else {
                            ""
                        }
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
                        val labels = context.model.extraStore.getOrNull(DateLabelsExtraKey).orEmpty()
                        val goldList = context.model.extraStore.getOrNull(GoldPricesExtraKey).orEmpty()
                        val silverList = context.model.extraStore.getOrNull(SilverPricesExtraKey).orEmpty()
                        val modeName = context.model.extraStore.getOrNull(ChartModeExtraKey)
                        val dateLabel = labels.getOrElse(idx) { "" }
                        val gPrice = goldList.getOrNull(idx)
                        val sPrice = silverList.getOrNull(idx)

                        when (modeName) {
                            TrendChartViewMode.GOLD_24K.name ->
                                "$dateLabel • 24K: ${gPrice?.let { Formatters.formatInr(it) } ?: "--"}/g"
                            TrendChartViewMode.SILVER_999.name ->
                                "$dateLabel • 999 Ag: ${sPrice?.let { Formatters.formatInr(it) } ?: "--"}/g"
                            else ->
                                "$dateLabel | Au: ${gPrice?.let { Formatters.formatInr(it) } ?: "--"} | Ag: ${sPrice?.let { Formatters.formatInr(it) } ?: "--"}"
                        }
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
                            lineProvider = LineCartesianLayer.LineProvider.series(activeLines),
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
                            itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 5 }) },
                        ),
                        marker = marker,
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .testTag("vico_cartesian_chart"),
                )
            }
        }

        if (hasData) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tap or drag across the chart to inspect daily Gold & Silver spot points.",
                style = MaterialTheme.typography.labelSmall,
                color = SecondarySilver,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetalPeriodStatCard(
                    title = "Gold 24K ($selectedRange)",
                    accentColor = RoyalGold,
                    high = trendSeries.gold30dHigh,
                    low = trendSeries.gold30dLow,
                    avg = trendSeries.gold30dAvg,
                    changePct = trendSeries.gold30dChangePct,
                    modifier = Modifier.weight(1f),
                )
                MetalPeriodStatCard(
                    title = "Silver 999 ($selectedRange)",
                    accentColor = SilverAccent,
                    high = trendSeries.silver30dHigh,
                    low = trendSeries.silver30dLow,
                    avg = trendSeries.silver30dAvg,
                    changePct = trendSeries.silver30dChangePct,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LegendBadge(
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
private fun MetalPeriodStatCard(
    title: String,
    accentColor: Color,
    high: Double,
    low: Double,
    avg: Double,
    changePct: Double,
    modifier: Modifier = Modifier,
) {
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
                    text = "${if (changePct >= 0) "+" else ""}${"%.2f".format(changePct)}%",
                    style = MonoSmallStyle,
                    color = if (changePct >= 0) SuccessGreen else ErrorRed,
                )
            }
            Text("High: ${Formatters.formatInr(high)}/g", style = MonoSmallStyle, color = PureWhite)
            Text("Low:  ${Formatters.formatInr(low)}/g", style = MonoSmallStyle, color = SecondarySilver)
            Text("Avg:  ${Formatters.formatInr(avg)}/g", style = MonoSmallStyle, color = SecondarySilver)
        }
    }
}
