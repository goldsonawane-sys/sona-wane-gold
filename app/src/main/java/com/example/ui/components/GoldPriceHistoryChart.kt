package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.remote.MarketRateService
import com.example.data.repository.SonaWaneRepository
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
import com.example.ui.theme.SuccessGreen
import com.example.data.repository.FirestoreService
import com.example.util.Formatters
import com.google.firebase.firestore.FirebaseFirestoreException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class GoldHistoryPurity(
    val code: String,
    val label: String,
    val accent: Color,
    val multiplier: Double,
) {
    K24("24K", "24K (999.9)", RoyalGold, 1.0),
    K22("22K", "22K (916)", LightGold, 22.0 / 24.0),
    K18("18K", "18K (750)", AmberOrange, 18.0 / 24.0),
}

private val GoldHistLabelsKey = ExtraStore.Key<List<String>>()
private val GoldHistDatesKey = ExtraStore.Key<List<String>>()
private val GoldHistPricesKey = ExtraStore.Key<List<Double>>()
private val GoldHistPurityKey = ExtraStore.Key<String>()

data class Gold30DayStats(
    val latest: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val avg: Double = 0.0,
    val changePct: Double = 0.0,
    val minY: Double = 0.0,
    val maxY: Double = 100.0,
)

internal fun computeGoldHistoryStats(prices: List<Double>): Gold30DayStats {
    if (prices.isEmpty()) return Gold30DayStats()
    val first = prices.first().takeIf { it > 0.0 } ?: 1.0
    val last = prices.last()
    val lo = prices.minOrNull() ?: 0.0
    val hi = prices.maxOrNull() ?: 100.0
    val pad = ((hi - lo) * 0.14).coerceAtLeast(hi * 0.004)
    return Gold30DayStats(
        latest = last,
        high = hi,
        low = lo,
        avg = prices.average(),
        changePct = ((last - first) / first) * 100.0,
        minY = (lo - pad).coerceAtLeast(0.0),
        maxY = hi + pad,
    )
}

/**
 * Self-contained Firestore-connected overload of [GoldPriceHistoryChart] that streams
 * the last 30 days of `market_price_history` directly from Firestore when signed in.
 */
@Composable
fun GoldPriceHistoryChart(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val currentUid = remember(appContext) { FirestoreService.currentUserOrNull(appContext)?.uid }
    val repository = remember(currentUid) {
        if (currentUid != null) SonaWaneRepository(appContext) else null
    }
    var syncing by remember(currentUid) { mutableStateOf(false) }
    var errorMsg by remember(currentUid) { mutableStateOf<String?>(null) }

    val historyFlow = remember(repository) {
        repository?.observeMarketPriceHistory(30)
            ?.catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
                errorMsg = (fs ?: err).localizedMessage ?: "Failed to load 30-day Firestore gold history"
                emit(emptyList())
            }
            ?: flowOf(emptyList())
    }
    val entries by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    GoldPriceHistoryChart(
        historyEntries = entries,
        isLoading = syncing,
        errorMessage = errorMsg,
        onRefreshFromFirestore = {
            val repo = repository ?: return@GoldPriceHistoryChart
            scope.launch {
                syncing = true
                errorMsg = null
                try {
                    val live = MarketRateService().fetchLiveRates()
                    if (live.gold24kBuyPerGram > 0.0 && live.silver999BuyPerGram > 0.0) {
                        val snapshots = MarketRateService().build30DayHistorySnapshots(
                            goldCurrentPerGram = live.gold24kBuyPerGram,
                            silverCurrentPerGram = live.silver999BuyPerGram,
                        )
                        repo.seedMissingMarketHistory(snapshots)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    errorMsg = e.localizedMessage ?: "Unable to sync 30-day gold history"
                } finally {
                    syncing = false
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Vico-powered 30-day **Gold Price History** chart displaying daily 24K, 22K, and 18K
 * gold rates fetched from Firestore `market_price_history`.
 */
@Composable
fun GoldPriceHistoryChart(
    historyEntries: List<MarketPriceHistoryEntry>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onRefreshFromFirestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPurity by remember { mutableStateOf(GoldHistoryPurity.K24) }

    val last30Entries = remember(historyEntries) {
        historyEntries
            .sortedBy { it.timestampEpochMs }
            .takeLast(30)
    }

    val seriesValues = remember(last30Entries, selectedPurity) {
        last30Entries.map { entry ->
            when (selectedPurity) {
                GoldHistoryPurity.K24 -> entry.gold24kPerGram
                GoldHistoryPurity.K22 -> entry.gold22kPerGram.takeIf { it > 0.0 }
                    ?: (entry.gold24kPerGram * GoldHistoryPurity.K22.multiplier)
                GoldHistoryPurity.K18 -> entry.gold18kPerGram.takeIf { it > 0.0 }
                    ?: (entry.gold24kPerGram * GoldHistoryPurity.K18.multiplier)
            }
        }
    }

    val stats = remember(seriesValues) { computeGoldHistoryStats(seriesValues) }
    val modelProducer = remember { CartesianChartModelProducer() }
    val hasSeries = last30Entries.size >= 2

    LaunchedEffect(last30Entries, seriesValues, selectedPurity) {
        if (last30Entries.size < 2 || seriesValues.size < 2) return@LaunchedEffect
        val labels = last30Entries.map { it.label.ifBlank { it.dateStr.takeLast(5) } }
        val dates = last30Entries.map { it.dateStr }
        modelProducer.runTransaction {
            lineSeries { series(seriesValues) }
            extras { store ->
                store[GoldHistLabelsKey] = labels
                store[GoldHistDatesKey] = dates
                store[GoldHistPricesKey] = seriesValues
                store[GoldHistPurityKey] = selectedPurity.code
            }
        }
    }

    LuxuryCard(
        highlightGold = true,
        modifier = modifier.testTag("gold_price_history_chart_card"),
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RoyalGold.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = RoyalGold,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.gold_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = LightGold,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Firestore 30-day feed",
                            tint = if (hasSeries) SuccessGreen else SecondarySilver,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasSeries) {
                                "Firestore • ${last30Entries.size} days (${last30Entries.first().label} – ${last30Entries.last().label})"
                            } else {
                                "Firestore market_price_history"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = stringResource(R.string.gold_history_firestore_badge),
                    color = if (hasSeries) SuccessGreen else AmberOrange,
                )
                IconButton(
                    onClick = onRefreshFromFirestore,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("gold_history_refresh_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync 30-day Gold Price History from Firestore",
                        tint = RoyalGold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Purity Switcher Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GoldHistoryPurity.entries.forEach { purity ->
                val selected = selectedPurity == purity
                FilterChip(
                    selected = selected,
                    onClick = { selectedPurity = purity },
                    label = { Text(purity.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = purity.accent,
                        selectedLabelColor = DeepBlack,
                    ),
                    modifier = Modifier.testTag("gold_history_purity_${purity.code.lowercase()}"),
                )
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = ErrorRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasSeries) {
            Surface(
                color = ElevatedObsidian,
                border = BorderStroke(1.dp, GoldBorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = RoyalGold, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Fetching 30-day Gold Price History from Firestore...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                    } else {
                        Text(
                            text = "No 30-day Firestore gold snapshots yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        GoldOutlinedButton(
                            text = "Sync 30D Firestore History",
                            onClick = onRefreshFromFirestore,
                            modifier = Modifier.testTag("gold_history_sync_empty_btn"),
                        )
                    }
                }
            }
        } else {
            // Vico Line Chart Surface
            val lineColor = selectedPurity.accent
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
                        Column {
                            Text(
                                text = "Latest ${selectedPurity.code} Spot",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondarySilver,
                            )
                            Text(
                                text = "${Formatters.formatInr(stats.latest)} / g",
                                style = MonoSmallStyle,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Surface(
                            color = (if (positive) SuccessGreen else ErrorRed).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                1.dp,
                                (if (positive) SuccessGreen else ErrorRed).copy(alpha = 0.45f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (positive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = if (positive) "30-day gain" else "30-day drop",
                                    tint = if (positive) SuccessGreen else ErrorRed,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${if (positive) "+" else ""}${"%.2f".format(stats.changePct)}% (30D)",
                                    style = MonoSmallStyle,
                                    color = if (positive) SuccessGreen else ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val rangeProvider = remember(stats.minY, stats.maxY) {
                        CartesianLayerRangeProvider.fixed(minY = stats.minY, maxY = stats.maxY)
                    }
                    val line = LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            fill(
                                ShaderProvider.verticalGradient(
                                    arrayOf(lineColor.copy(alpha = 0.34f), Color.Transparent)
                                )
                            )
                        ),
                        pointConnector = LineCartesianLayer.PointConnector.cubic(0.42f),
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
                            "₹${value.roundToInt()}"
                        }
                    }
                    val xFormatter = remember {
                        CartesianValueFormatter { context, x, _ ->
                            val labels = context.model.extraStore.getOrNull(GoldHistLabelsKey)
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
                    val markerFormatter = remember {
                        DefaultCartesianMarker.ValueFormatter { context, targets ->
                            val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
                            val idx = lineTarget?.points?.firstOrNull()?.entry?.x?.roundToInt() ?: 0
                            val dates = context.model.extraStore.getOrNull(GoldHistDatesKey).orEmpty()
                            val prices = context.model.extraStore.getOrNull(GoldHistPricesKey).orEmpty()
                            val code = context.model.extraStore.getOrNull(GoldHistPurityKey) ?: "24K"
                            val date = dates.getOrElse(idx) { "" }
                            val price = prices.getOrNull(idx)
                            "$date • $code: ${price?.let { Formatters.formatInr(it) } ?: "--"}/g"
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
                            .height(210.dp)
                            .testTag("vico_gold_price_history_chart"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 30D Summary Metrics Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GoldHistoryMetricTile(
                    label = "30D Low",
                    value = Formatters.formatInr(stats.low),
                    color = SecondarySilver,
                    modifier = Modifier.weight(1f),
                )
                GoldHistoryMetricTile(
                    label = "30D Avg",
                    value = Formatters.formatInr(stats.avg),
                    color = LightGold,
                    modifier = Modifier.weight(1f),
                )
                GoldHistoryMetricTile(
                    label = "30D High",
                    value = Formatters.formatInr(stats.high),
                    color = selectedPurity.accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GoldHistoryMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ObsidianCard,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = SecondarySilver)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MonoSmallStyle,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
