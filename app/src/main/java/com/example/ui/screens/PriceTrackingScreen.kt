package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.remote.MarketRateService
import com.example.data.repository.SonaWaneRepository
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPrimaryButton
import com.example.ui.components.LuxuryCard
import com.example.ui.components.SonaWaneTopBar
import com.example.ui.components.StatusPill
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

enum class PriceTrackingMetal(
    val chipLabel: String,
    val title: String,
    val unitLabel: String,
    val accentColor: Color,
    val isGold: Boolean,
) {
    GOLD_24K("24K Gold", "24K Pure Gold (999.9)", "/ g", RoyalGold, true),
    GOLD_22K("22K Gold", "22K Hallmark Gold (916)", "/ g", LightGold, true),
    GOLD_18K("18K Gold", "18K Jewellery Gold (750)", "/ g", AmberOrange, true),
    SILVER_999("999 Silver", "999 Fine Silver", "/ g", SilverAccent, false),
    COMPARE("Compare", "Gold vs Silver (% Return)", "%", RoyalGold, true),
}

enum class PriceTrackingWindow(
    val code: String,
    val chipLabel: String,
    val maxDays: Int,
) {
    D7("7D", "7 Days", 7),
    D14("14D", "14 Days", 14),
    D30("30D", "30 Days", 30),
    D90("90D", "90 Days", 90),
}

data class MetalTrendStats(
    val latestPrice: Double = 0.0,
    val periodHigh: Double = 0.0,
    val periodLow: Double = 0.0,
    val periodAverage: Double = 0.0,
    val changePct: Double = 0.0,
    val minY: Double = 0.0,
    val maxY: Double = 100.0,
    val dateRangeText: String = "",
    val pointCount: Int = 0,
    val goldSilverRatio: Double = 0.0,
)

object PriceTrackingCalculator {

    fun extractPrice(entry: MarketPriceHistoryEntry, metal: PriceTrackingMetal): Double {
        return when (metal) {
            PriceTrackingMetal.GOLD_24K -> entry.gold24kPerGram
            PriceTrackingMetal.GOLD_22K -> entry.gold22kPerGram.takeIf { it > 0.0 }
                ?: (entry.gold24kPerGram * (22.0 / 24.0))
            PriceTrackingMetal.GOLD_18K -> entry.gold18kPerGram.takeIf { it > 0.0 }
                ?: (entry.gold24kPerGram * (18.0 / 24.0))
            PriceTrackingMetal.SILVER_999 -> entry.silver999PerGram
            PriceTrackingMetal.COMPARE -> entry.gold24kPerGram
        }
    }

    fun sliceWindow(
        entries: List<MarketPriceHistoryEntry>,
        window: PriceTrackingWindow,
    ): List<MarketPriceHistoryEntry> {
        return entries
            .filter { it.gold24kPerGram > 0.0 || it.silver999PerGram > 0.0 }
            .sortedBy { it.timestampEpochMs }
            .takeLast(window.maxDays)
    }

    fun computeStats(
        entries: List<MarketPriceHistoryEntry>,
        metal: PriceTrackingMetal,
    ): MetalTrendStats {
        val sorted = entries.sortedBy { it.timestampEpochMs }
        if (sorted.isEmpty()) return MetalTrendStats()

        val values = sorted.map { extractPrice(it, metal) }.filter { it > 0.0 }
        if (values.isEmpty()) return MetalTrendStats()

        val first = values.first()
        val last = values.last()
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 100.0
        val pad = ((hi - lo) * 0.14).coerceAtLeast(hi * 0.004).coerceAtLeast(0.5)

        val latestEntry = sorted.last()
        val ratio = if (latestEntry.silver999PerGram > 0.0 && latestEntry.gold24kPerGram > 0.0) {
            latestEntry.gold24kPerGram / latestEntry.silver999PerGram
        } else {
            0.0
        }

        val firstLabel = sorted.first().label.ifBlank { sorted.first().dateStr.takeLast(5) }
        val lastLabel = latestEntry.label.ifBlank { latestEntry.dateStr.takeLast(5) }

        return MetalTrendStats(
            latestPrice = last,
            periodHigh = hi,
            periodLow = lo,
            periodAverage = values.average(),
            changePct = if (first > 0.0) ((last - first) / first) * 100.0 else 0.0,
            minY = (lo - pad).coerceAtLeast(0.0),
            maxY = hi + pad,
            dateRangeText = "$firstLabel – $lastLabel",
            pointCount = sorted.size,
            goldSilverRatio = ratio,
        )
    }

    fun buildNormalizedCompareSeries(
        entries: List<MarketPriceHistoryEntry>,
    ): Pair<List<Double>, List<Double>> {
        val sorted = entries.sortedBy { it.timestampEpochMs }
        if (sorted.size < 2) return emptyList<Double>() to emptyList()
        val baseGold = sorted.first().gold24kPerGram.takeIf { it > 0.0 } ?: 1.0
        val baseSilver = sorted.first().silver999PerGram.takeIf { it > 0.0 } ?: 1.0
        val goldPct = sorted.map { ((it.gold24kPerGram - baseGold) / baseGold) * 100.0 }
        val silverPct = sorted.map { ((it.silver999PerGram - baseSilver) / baseSilver) * 100.0 }
        return goldPct to silverPct
    }
}

private val TrackLabelsKey = ExtraStore.Key<List<String>>()
private val TrackDatesKey = ExtraStore.Key<List<String>>()
private val TrackSingleValuesKey = ExtraStore.Key<List<Double>>()
private val TrackGoldPctKey = ExtraStore.Key<List<Double>>()
private val TrackSilverPctKey = ExtraStore.Key<List<Double>>()
private val TrackMetalLabelKey = ExtraStore.Key<String>()

/**
 * Self-contained Firestore-backed [PriceTrackingScreen] that streams `market_price_history`
 * directly from Firestore when authenticated and renders interactive Vico charts.
 */
@Composable
fun FirestorePriceTrackingScreen(
    rates: LiveMarketRates = LiveMarketRates(),
    onBack: () -> Unit,
    onTradeGold: () -> Unit = {},
    onTradeSilver: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
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
        repository?.observeMarketPriceHistory(90)
            ?.catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
                errorMsg = (fs ?: err).localizedMessage ?: "Failed to load Firestore price history"
                emit(emptyList())
            }
            ?: flowOf(emptyList())
    }
    val entries by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    PriceTrackingScreen(
        historyEntries = entries,
        rates = rates,
        isLoading = syncing,
        errorMessage = errorMsg,
        onBack = onBack,
        onRefreshFromFirestore = {
            val repo = repository ?: return@PriceTrackingScreen
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
                    errorMsg = e.localizedMessage ?: "Unable to sync Firestore price history"
                } finally {
                    syncing = false
                }
            }
        },
        onTradeGold = onTradeGold,
        onTradeSilver = onTradeSilver,
        onOpenAlerts = onOpenAlerts,
    )
}

/**
 * Vico-powered Historical Price Tracking Screen displaying daily Gold (24K/22K/18K) and
 * Silver (999) price trends from Firestore `market_price_history`, with normalized dual-metal
 * comparison, period filtering (7D / 14D / 30D / 90D), and daily snapshot log.
 */
@Composable
fun PriceTrackingScreen(
    historyEntries: List<MarketPriceHistoryEntry>,
    rates: LiveMarketRates = LiveMarketRates(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBack: () -> Unit,
    onRefreshFromFirestore: () -> Unit,
    onTradeGold: () -> Unit = {},
    onTradeSilver: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
) {
    var selectedMetal by remember { mutableStateOf(PriceTrackingMetal.GOLD_24K) }
    var selectedWindow by remember { mutableStateOf(PriceTrackingWindow.D30) }

    val windowedEntries = remember(historyEntries, selectedWindow) {
        PriceTrackingCalculator.sliceWindow(historyEntries, selectedWindow)
    }
    val stats = remember(windowedEntries, selectedMetal) {
        PriceTrackingCalculator.computeStats(
            entries = windowedEntries,
            metal = if (selectedMetal == PriceTrackingMetal.COMPARE) PriceTrackingMetal.GOLD_24K else selectedMetal,
        )
    }
    val silverStats = remember(windowedEntries) {
        PriceTrackingCalculator.computeStats(windowedEntries, PriceTrackingMetal.SILVER_999)
    }
    val singleSeriesValues = remember(windowedEntries, selectedMetal) {
        windowedEntries.map { PriceTrackingCalculator.extractPrice(it, selectedMetal) }
    }
    val compareSeries = remember(windowedEntries) {
        PriceTrackingCalculator.buildNormalizedCompareSeries(windowedEntries)
    }

    val singleModelProducer = remember { CartesianChartModelProducer() }
    val compareModelProducer = remember { CartesianChartModelProducer() }
    val hasSeries = windowedEntries.size >= 2

    LaunchedEffect(windowedEntries, selectedMetal, singleSeriesValues, compareSeries) {
        if (windowedEntries.size < 2) return@LaunchedEffect
        val labels = windowedEntries.map { it.label.ifBlank { it.dateStr.takeLast(5) } }
        val dates = windowedEntries.map { it.dateStr }
        if (selectedMetal == PriceTrackingMetal.COMPARE) {
            val (goldPct, silverPct) = compareSeries
            if (goldPct.size >= 2 && silverPct.size >= 2) {
                compareModelProducer.runTransaction {
                    lineSeries {
                        series(goldPct)
                        series(silverPct)
                    }
                    extras { store ->
                        store[TrackLabelsKey] = labels
                        store[TrackDatesKey] = dates
                        store[TrackGoldPctKey] = goldPct
                        store[TrackSilverPctKey] = silverPct
                    }
                }
            }
        } else {
            singleModelProducer.runTransaction {
                lineSeries { series(singleSeriesValues) }
                extras { store ->
                    store[TrackLabelsKey] = labels
                    store[TrackDatesKey] = dates
                    store[TrackSingleValuesKey] = singleSeriesValues
                    store[TrackMetalLabelKey] = selectedMetal.chipLabel
                }
            }
        }
    }

    Scaffold(
        containerColor = DeepBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SonaWaneTopBar(
                title = stringResource(R.string.price_tracking_title),
                subtitle = if (hasSeries) {
                    "Firestore • ${windowedEntries.size} daily snapshots (${stats.dateRangeText})"
                } else {
                    stringResource(R.string.price_tracking_subtitle)
                },
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onOpenAlerts,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("price_tracking_alert_action"),
                    ) {
                        Icon(Icons.Default.AddAlert, contentDescription = "Set price alerts", tint = AmberOrange)
                    }
                    IconButton(
                        onClick = onRefreshFromFirestore,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("price_tracking_refresh_btn"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Firestore price history", tint = RoyalGold)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("price_tracking_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Firestore Sync Status Banner
            item {
                LuxuryCard(highlightGold = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(RoyalGold.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Firestore historical feed",
                                    tint = if (hasSeries) SuccessGreen else RoyalGold,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Firestore Historical Bullion Feed",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = LightGold,
                                )
                                Text(
                                    text = if (hasSeries) {
                                        "Live synced from market_price_history (${windowedEntries.last().source})"
                                    } else {
                                        "Pulling daily Gold & Silver snapshots from Firestore"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondarySilver,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        StatusPill(
                            text = if (hasSeries) "FIRESTORE LIVE" else "SYNCING",
                            color = if (hasSeries) SuccessGreen else AmberOrange,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metal Filter Chips (Horizontal scroll, Robolectric-safe)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("price_tracking_metal_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PriceTrackingMetal.entries.forEach { metal ->
                            val selected = selectedMetal == metal
                            FilterChip(
                                selected = selected,
                                onClick = { selectedMetal = metal },
                                label = { Text(metal.chipLabel) },
                                leadingIcon = if (metal == PriceTrackingMetal.COMPARE) {
                                    {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = metal.accentColor,
                                    selectedLabelColor = DeepBlack,
                                    selectedLeadingIconColor = DeepBlack,
                                    containerColor = ObsidianCard,
                                    labelColor = PureWhite,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("price_tracking_metal_${metal.name}"),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Window Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("price_tracking_window_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PriceTrackingWindow.entries.forEach { win ->
                            val selected = selectedWindow == win
                            FilterChip(
                                selected = selected,
                                onClick = { selectedWindow = win },
                                label = { Text(win.chipLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalGold.copy(alpha = 0.24f),
                                    selectedLabelColor = LightGold,
                                    containerColor = ElevatedObsidian,
                                    labelColor = SecondarySilver,
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("price_tracking_window_${win.code}"),
                            )
                        }
                    }
                }
            }

            // Vico Chart Card
            item {
                LuxuryCard(
                    highlightGold = selectedMetal.isGold,
                    modifier = Modifier.testTag("price_tracking_vico_card"),
                ) {
                    if (!errorMessage.isNullOrBlank()) {
                        Surface(
                            color = ErrorRed.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ErrorRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    if (!hasSeries) {
                        Surface(
                            color = ElevatedObsidian,
                            border = BorderStroke(1.dp, GoldBorderSubtle),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = RoyalGold, modifier = Modifier.size(34.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Loading Firestore gold & silver trends...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondarySilver,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = null,
                                        tint = RoyalGold,
                                        modifier = Modifier.size(34.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Firestore price snapshots available yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PureWhite,
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    GoldOutlinedButton(
                                        text = stringResource(R.string.price_tracking_sync_cta),
                                        onClick = onRefreshFromFirestore,
                                        modifier = Modifier.testTag("price_tracking_empty_sync_btn"),
                                    )
                                }
                            }
                        }
                    } else if (selectedMetal == PriceTrackingMetal.COMPARE) {
                        CompareVicoChartContent(
                            goldStats = stats,
                            silverStats = silverStats,
                            compareSeries = compareSeries,
                            windowCode = selectedWindow.code,
                            modelProducer = compareModelProducer,
                        )
                    } else {
                        SingleMetalVicoChartContent(
                            metal = selectedMetal,
                            stats = stats,
                            windowCode = selectedWindow.code,
                            modelProducer = singleModelProducer,
                        )
                    }
                }
            }

            // Period Analytics Summary
            if (hasSeries) {
                item {
                    LuxuryCard {
                        Text(
                            text = "${selectedWindow.chipLabel} ${if (selectedMetal == PriceTrackingMetal.COMPARE) "Gold & Silver" else selectedMetal.chipLabel} Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            color = LightGold,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PriceMetricTile(
                                label = "${selectedWindow.code} Low",
                                value = Formatters.formatInr(stats.periodLow),
                                accent = SecondarySilver,
                                modifier = Modifier.weight(1f),
                            )
                            PriceMetricTile(
                                label = "${selectedWindow.code} Avg",
                                value = Formatters.formatInr(stats.periodAverage),
                                accent = LightGold,
                                modifier = Modifier.weight(1f),
                            )
                            PriceMetricTile(
                                label = "${selectedWindow.code} High",
                                value = Formatters.formatInr(stats.periodHigh),
                                accent = selectedMetal.accentColor,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PriceMetricTile(
                                label = "Silver Latest",
                                value = "${Formatters.formatInr(silverStats.latestPrice)}/g",
                                accent = SilverAccent,
                                modifier = Modifier.weight(1f),
                            )
                            PriceMetricTile(
                                label = "Gold/Silver Ratio",
                                value = if (stats.goldSilverRatio > 0.0) "${"%.1f".format(stats.goldSilverRatio)}x" else "--",
                                accent = RoyalGold,
                                modifier = Modifier.weight(1f),
                            )
                            PriceMetricTile(
                                label = "Live Spot 24K",
                                value = if (rates.gold24kBuyPerGram > 0.0) Formatters.formatInr(rates.gold24kBuyPerGram) else Formatters.formatInr(stats.latestPrice),
                                accent = SuccessGreen,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Quick Trade & Alert Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GoldPrimaryButton(
                        text = if (selectedMetal == PriceTrackingMetal.SILVER_999) "Trade 999 Silver" else "Trade 24K Gold",
                        onClick = if (selectedMetal == PriceTrackingMetal.SILVER_999) onTradeSilver else onTradeGold,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("price_tracking_trade_btn"),
                    )
                    GoldOutlinedButton(
                        text = "Set Target Alert",
                        icon = Icons.Default.AddAlert,
                        onClick = onOpenAlerts,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("price_tracking_alert_btn"),
                    )
                }
            }

            // Firestore Historical Snapshot Log
            if (windowedEntries.isNotEmpty()) {
                item {
                    Text(
                        text = "Firestore Daily Price Log (${windowedEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                    )
                }

                items(windowedEntries.asReversed().take(15), key = { it.dateStr.ifBlank { it.timestampEpochMs.toString() } }) { entry ->
                    Surface(
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, GoldBorderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_tracking_row_${entry.dateStr}"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.dateStr,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PureWhite,
                                )
                                Text(
                                    text = entry.source.ifBlank { "FIRESTORE" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondarySilver,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Au ${Formatters.formatInr(entry.gold24kPerGram)}/g",
                                    style = MonoSmallStyle,
                                    color = LightGold,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Ag ${Formatters.formatInr(entry.silver999PerGram)}/g",
                                    style = MonoSmallStyle,
                                    color = SilverAccent,
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SingleMetalVicoChartContent(
    metal: PriceTrackingMetal,
    stats: MetalTrendStats,
    windowCode: String,
    modelProducer: CartesianChartModelProducer,
) {
    val lineColor = metal.accentColor

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = metal.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )
                Text(
                    text = "${Formatters.formatInr(stats.latestPrice)} ${metal.unitLabel}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                )
            }
            DeltaPill(changePct = stats.changePct, windowCode = windowCode)
        }

        Surface(
            color = ElevatedObsidian,
            border = BorderStroke(1.dp, lineColor.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
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
                        if (value >= 1000.0) "₹${value.roundToInt()}" else "₹${"%.1f".format(value)}"
                    }
                }
                val xFormatter = remember {
                    CartesianValueFormatter { context, x, _ ->
                        val labels = context.model.extraStore.getOrNull(TrackLabelsKey)
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
                        val dates = context.model.extraStore.getOrNull(TrackDatesKey).orEmpty()
                        val prices = context.model.extraStore.getOrNull(TrackSingleValuesKey).orEmpty()
                        val label = context.model.extraStore.getOrNull(TrackMetalLabelKey) ?: "Bullion"
                        val date = dates.getOrElse(idx) { "" }
                        val price = prices.getOrNull(idx)
                        "$date • $label: ${price?.let { Formatters.formatInr(it) } ?: "--"}/g"
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
                        .height(225.dp)
                        .testTag("price_tracking_vico_single_chart"),
                )
            }
        }
    }
}

@Composable
private fun CompareVicoChartContent(
    goldStats: MetalTrendStats,
    silverStats: MetalTrendStats,
    compareSeries: Pair<List<Double>, List<Double>>,
    windowCode: String,
    modelProducer: CartesianChartModelProducer,
) {
    val allPct = compareSeries.first + compareSeries.second
    val minPct = (allPct.minOrNull() ?: -5.0) - 0.5
    val maxPct = (allPct.maxOrNull() ?: 5.0) + 0.5

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("24K Gold vs 999 Silver ($windowCode)", style = MaterialTheme.typography.titleSmall, color = LightGold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RoyalGold))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Gold ${if (goldStats.changePct >= 0) "+" else ""}${"%.2f".format(goldStats.changePct)}%",
                            style = MonoSmallStyle,
                            color = LightGold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SilverAccent))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Silver ${if (silverStats.changePct >= 0) "+" else ""}${"%.2f".format(silverStats.changePct)}%",
                            style = MonoSmallStyle,
                            color = SilverAccent,
                        )
                    }
                }
            }
            DeltaPill(changePct = goldStats.changePct, windowCode = windowCode)
        }

        Surface(
            color = ElevatedObsidian,
            border = BorderStroke(1.dp, GoldBorderSubtle),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                val rangeProvider = remember(minPct, maxPct) {
                    CartesianLayerRangeProvider.fixed(minY = minPct, maxY = maxPct)
                }
                val goldLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(RoyalGold)),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        fill(ShaderProvider.verticalGradient(arrayOf(RoyalGold.copy(alpha = 0.24f), Color.Transparent)))
                    ),
                    pointConnector = LineCartesianLayer.PointConnector.cubic(0.42f),
                )
                val silverLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(SilverAccent)),
                    areaFill = LineCartesianLayer.AreaFill.single(
                        fill(ShaderProvider.verticalGradient(arrayOf(SilverAccent.copy(alpha = 0.20f), Color.Transparent)))
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
                        "${if (value >= 0) "+" else ""}${"%.1f".format(value)}%"
                    }
                }
                val xFormatter = remember {
                    CartesianValueFormatter { context, x, _ ->
                        val labels = context.model.extraStore.getOrNull(TrackLabelsKey)
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
                val markerFormatter = remember {
                    DefaultCartesianMarker.ValueFormatter { context, targets ->
                        val lineTarget = targets.filterIsInstance<LineCartesianLayerMarkerTarget>().firstOrNull()
                        val idx = lineTarget?.points?.firstOrNull()?.entry?.x?.roundToInt() ?: 0
                        val dates = context.model.extraStore.getOrNull(TrackDatesKey).orEmpty()
                        val gp = context.model.extraStore.getOrNull(TrackGoldPctKey).orEmpty().getOrNull(idx)
                        val sp = context.model.extraStore.getOrNull(TrackSilverPctKey).orEmpty().getOrNull(idx)
                        val date = dates.getOrElse(idx) { "" }
                        val gTxt = gp?.let { "${if (it >= 0) "+" else ""}${"%.2f".format(it)}%" } ?: "--"
                        val sTxt = sp?.let { "${if (it >= 0) "+" else ""}${"%.2f".format(it)}%" } ?: "--"
                        "$date • Au $gTxt | Ag $sTxt"
                    }
                }
                val marker = rememberDefaultCartesianMarker(
                    label = markerLabel,
                    valueFormatter = markerFormatter,
                    guideline = rememberLineComponent(fill = fill(RoyalGold.copy(alpha = 0.6f)), thickness = 1.5.dp),
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
                        .height(225.dp)
                        .testTag("price_tracking_vico_compare_chart"),
                )
            }
        }
    }
}

@Composable
private fun DeltaPill(changePct: Double, windowCode: String) {
    val positive = changePct >= 0.0
    val color = if (positive) SuccessGreen else ErrorRed
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (positive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = if (positive) "Price trend up" else "Price trend down",
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${if (positive) "+" else ""}${"%.2f".format(changePct)}% ($windowCode)",
                style = MonoSmallStyle,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun PriceMetricTile(
    label: String,
    value: String,
    accent: Color,
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
                color = accent,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
