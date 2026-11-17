package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.TransactionRecord
import com.example.data.repository.SonaWaneRepository
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
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
import com.example.ui.theme.WarningAmber
import com.example.data.repository.FirestoreService
import com.example.util.Formatters
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class TradeStatusTier {
    COMPLETED,
    PENDING,
    FAILED,
}

data class TradeStatusDisplay(
    val label: String,
    val rawStatus: String,
    val tier: TradeStatusTier,
    val color: Color,
    val icon: ImageVector,
)

object TradeStatusResolver {
    val BULLION_TRADE_CATEGORIES = setOf("GOLD_BUY", "GOLD_SELL", "SILVER_BUY", "SILVER_SELL")

    fun resolveStatus(status: String?): TradeStatusDisplay {
        val normalized = status?.trim()?.uppercase().orEmpty()
        return when (normalized) {
            "SUCCESSFUL", "COMPLETED", "APPROVED", "CREDITED", "SETTLED" -> TradeStatusDisplay(
                label = "Completed",
                rawStatus = normalized,
                tier = TradeStatusTier.COMPLETED,
                color = SuccessGreen,
                icon = Icons.Default.CheckCircle,
            )
            "PENDING", "PROCESSING", "IN_PROGRESS", "INITIATED", "UNDER_REVIEW", "WAITING_FOR_USER" -> TradeStatusDisplay(
                label = "Pending",
                rawStatus = normalized,
                tier = TradeStatusTier.PENDING,
                color = WarningAmber,
                icon = Icons.Default.Schedule,
            )
            "FAILED", "REJECTED", "REVERSED", "CANCELLED", "DECLINED", "EXPIRED" -> TradeStatusDisplay(
                label = "Failed",
                rawStatus = normalized,
                tier = TradeStatusTier.FAILED,
                color = ErrorRed,
                icon = Icons.Default.Cancel,
            )
            else -> TradeStatusDisplay(
                label = if (normalized.isBlank()) "Pending" else normalized.lowercase().replaceFirstChar { it.uppercase() },
                rawStatus = normalized.ifBlank { "PENDING" },
                tier = TradeStatusTier.PENDING,
                color = WarningAmber,
                icon = Icons.Default.Schedule,
            )
        }
    }

    fun filterTrades(
        transactions: List<TransactionRecord>,
        metalFilter: String = "ALL",
        statusFilter: String = "ALL",
        maxItems: Int = 6,
        tradeSideFilter: String = "ALL",
    ): List<TransactionRecord> {
        return transactions
            .filter { it.category in BULLION_TRADE_CATEGORIES }
            .filter { tx ->
                when (metalFilter.uppercase()) {
                    "GOLD" -> tx.category.startsWith("GOLD_")
                    "SILVER" -> tx.category.startsWith("SILVER_")
                    else -> true
                }
            }
            .filter { tx ->
                when (tradeSideFilter.uppercase()) {
                    "BUY" -> tx.category.endsWith("_BUY")
                    "SELL" -> tx.category.endsWith("_SELL")
                    else -> true
                }
            }
            .filter { tx ->
                when (statusFilter.uppercase()) {
                    "COMPLETED" -> resolveStatus(tx.status).tier == TradeStatusTier.COMPLETED
                    "PENDING" -> resolveStatus(tx.status).tier == TradeStatusTier.PENDING
                    "FAILED" -> resolveStatus(tx.status).tier == TradeStatusTier.FAILED
                    else -> true
                }
            }
            .take(maxItems.coerceAtLeast(1))
    }
}

/**
 * Self-contained component that retrieves and displays recent Gold & Silver trades
 * from Firestore (`transactions` collection scoped by `userId`), including clear visual
 * status indicators for **Completed**, **Pending**, and **Failed** transactions.
 *
 * When [transactions] is provided, displays the live upstream list while still supporting
 * direct Firestore manual refresh; when [transactions] is null, subscribes directly to
 * `SonaWaneRepository.observeRecentBullionTrades(uid)`.
 */
@Composable
fun TransactionHistoryView(
    transactions: List<TransactionRecord>? = null,
    onSelectTransaction: (TransactionRecord) -> Unit = {},
    onViewAllHistory: () -> Unit = {},
    maxItems: Int = 6,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUid = remember(transactions, context) {
        if (transactions != null) {
            null
        } else {
            FirestoreService.currentUserOrNull(context.applicationContext)?.uid
        }
    }
    val repository = remember(context, currentUid, transactions) {
        if (transactions == null && currentUid != null) SonaWaneRepository(context) else null
    }

    var directError by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var manuallyFetched by remember { mutableStateOf<List<TransactionRecord>?>(null) }

    val observedTrades by remember(repository, currentUid, transactions) {
        if (transactions != null || repository == null || currentUid == null) {
            flowOf(emptyList())
        } else {
            repository.observeRecentBullionTrades(currentUid, limit = 25)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    directError = (fsErr ?: error).localizedMessage ?: "Failed to load Firestore trades."
                    emit(emptyList())
                }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Initial direct Firestore sync when standalone (transactions == null)
    LaunchedEffect(repository, currentUid) {
        if (transactions == null && repository != null && currentUid != null) {
            isRefreshing = true
            runCatching { repository.fetchRecentBullionTrades(currentUid, limit = 25) }
                .onSuccess { fetched ->
                    manuallyFetched = fetched
                    directError = null
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    directError = err.localizedMessage ?: "Unable to fetch trades from Firestore."
                }
            isRefreshing = false
        }
    }

    val sourceTransactions = remember(transactions, observedTrades, manuallyFetched) {
        manuallyFetched ?: transactions ?: observedTrades
    }

    val allBullionTrades = remember(sourceTransactions) {
        sourceTransactions.filter { it.category in TradeStatusResolver.BULLION_TRADE_CATEGORIES }
    }

    var metalFilter by remember { mutableStateOf("ALL") } // ALL | GOLD | SILVER
    var tradeSideFilter by remember { mutableStateOf("ALL") } // ALL | BUY | SELL
    var statusFilter by remember { mutableStateOf("ALL") } // ALL | COMPLETED | PENDING | FAILED

    val completedCount = remember(allBullionTrades) {
        allBullionTrades.count { TradeStatusResolver.resolveStatus(it.status).tier == TradeStatusTier.COMPLETED }
    }
    val pendingCount = remember(allBullionTrades) {
        allBullionTrades.count { TradeStatusResolver.resolveStatus(it.status).tier == TradeStatusTier.PENDING }
    }
    val failedCount = remember(allBullionTrades) {
        allBullionTrades.count { TradeStatusResolver.resolveStatus(it.status).tier == TradeStatusTier.FAILED }
    }

    val visibleTrades = remember(allBullionTrades, metalFilter, tradeSideFilter, statusFilter, maxItems) {
        TradeStatusResolver.filterTrades(
            transactions = allBullionTrades,
            metalFilter = metalFilter,
            statusFilter = statusFilter,
            maxItems = maxItems,
            tradeSideFilter = tradeSideFilter,
        )
    }

    val triggerFirestoreRefresh: () -> Unit = {
        val uid = FirestoreService.currentUserOrNull(context.applicationContext)?.uid
        if (uid != null && !isRefreshing) {
            scope.launch {
                isRefreshing = true
                directError = null
                runCatching { SonaWaneRepository(context).fetchRecentBullionTrades(uid, limit = 25) }
                    .onSuccess { fetched ->
                        manuallyFetched = fetched
                        directError = null
                    }
                    .onFailure { err ->
                        if (err is CancellationException) throw err
                        directError = err.localizedMessage ?: "Refresh failed."
                    }
                isRefreshing = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CharcoalBlack,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("transaction_history_view"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tx_history_view_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isRefreshing) Icons.Default.CloudSync else Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = if (isRefreshing) AmberOrange else SuccessGreen,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRefreshing) {
                                "Syncing trades from Firestore..."
                            } else {
                                stringResource(R.string.tx_history_view_subtitle)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = triggerFirestoreRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tx_history_refresh_btn"),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = RoyalGold,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.tx_history_refresh_cd),
                                tint = RoyalGold,
                            )
                        }
                    }
                    TextButton(
                        onClick = onViewAllHistory,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tx_history_view_all_btn"),
                    ) {
                        Text("View All", color = AmberOrange)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View all trades",
                            tint = AmberOrange,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            // Status count strip (Completed, Pending, Failed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusPill(
                    text = "Completed ($completedCount)",
                    color = SuccessGreen,
                    icon = Icons.Default.CheckCircle,
                )
                StatusPill(
                    text = "Pending ($pendingCount)",
                    color = WarningAmber,
                    icon = Icons.Default.Schedule,
                )
                StatusPill(
                    text = "Failed ($failedCount)",
                    color = ErrorRed,
                    icon = Icons.Default.Cancel,
                )
            }

            // Metal & Status Filter Chips
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "ALL" to "All Metals",
                        "GOLD" to "Gold",
                        "SILVER" to "Silver",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = metalFilter == key,
                            onClick = { metalFilter = key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("tx_history_metal_chip_${key.lowercase()}"),
                        )
                    }
                    listOf(
                        "BUY" to "Buy",
                        "SELL" to "Sell",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = tradeSideFilter == key,
                            onClick = { tradeSideFilter = if (tradeSideFilter == key) "ALL" else key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("tx_history_side_chip_${key.lowercase()}"),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "ALL" to "All Status",
                        "COMPLETED" to "Completed",
                        "PENDING" to "Pending",
                        "FAILED" to "Failed",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = if (statusFilter == key && key != "ALL") "ALL" else key },
                            label = { Text(label) },
                            modifier = Modifier.testTag("tx_history_status_chip_${key.lowercase()}"),
                        )
                    }
                }
            }

            if (!directError.isNullOrBlank()) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = directError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = triggerFirestoreRefresh) {
                            Text("Retry", color = RoyalGold)
                        }
                    }
                }
            }

            if (isRefreshing && visibleTrades.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = RoyalGold,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Fetching gold & silver trades from Firestore...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondarySilver,
                        )
                    }
                }
            } else if (visibleTrades.isEmpty()) {
                Surface(
                    color = ObsidianCard,
                    border = BorderStroke(1.dp, GoldBorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_history_empty_state"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = RoyalGold.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (allBullionTrades.isEmpty()) {
                                "No gold or silver trades recorded in Firestore yet."
                            } else {
                                "No trades match the selected metal or status filter."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondarySilver,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleTrades.forEach { tx ->
                        TradeHistoryItemRow(
                            tx = tx,
                            onClick = { onSelectTransaction(tx) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TradeHistoryItemRow(
    tx: TransactionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBuy = tx.category.endsWith("_BUY")
    val isGold = tx.category.startsWith("GOLD_")
    val statusDisplay = remember(tx.status) { TradeStatusResolver.resolveStatus(tx.status) }
    val tradeTitle = when (tx.category) {
        "GOLD_BUY" -> "Buy Gold"
        "GOLD_SELL" -> "Sell Gold"
        "SILVER_BUY" -> "Buy Silver"
        "SILVER_SELL" -> "Sell Silver"
        else -> tx.category.replace("_", " ")
    }
    val metalTint = if (isGold) RoyalGold else SilverAccent
    val directionTint = if (isBuy) SuccessGreen else AmberOrange

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, statusDisplay.color.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("trade_history_row_${tx.id.ifBlank { tx.txCode }}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(metalTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isBuy) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = tradeTitle,
                        tint = directionTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = tradeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                        )
                        if (tx.metalPurity != "NONE" && tx.metalPurity.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = metalTint.copy(alpha = 0.16f),
                            ) {
                                Text(
                                    text = tx.metalPurity,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = metalTint,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (tx.grams > 0.0) {
                        Text(
                            text = "${Formatters.formatGrams(tx.grams)} • @ ${Formatters.formatInr(tx.ratePerGram)}/g",
                            style = MonoSmallStyle,
                            color = LightGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = Formatters.formatTimestamp(tx.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondarySilver,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (tx.txCode.isNotBlank() || tx.id.isNotBlank()) {
                        Text(
                            text = "Ref: ${tx.txCode.ifBlank { tx.id }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (statusDisplay.tier == TradeStatusTier.FAILED && !tx.rejectionReason.isNullOrBlank()) {
                        Text(
                            text = tx.rejectionReason.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = Formatters.formatInr(tx.totalAmount),
                    style = MonoPriceStyle,
                    color = PureWhite,
                )
                StatusPill(
                    text = statusDisplay.label,
                    color = statusDisplay.color,
                    icon = statusDisplay.icon,
                )
            }
        }
    }
}
