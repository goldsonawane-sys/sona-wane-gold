package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.repository.SonaWaneRepository
import com.example.ui.theme.AmberOrange
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.DarkGold
import com.example.ui.theme.DeepBlack
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldBorderSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class GoldKaratTier(
    val code: String,
    val hallmark: String,
    val subtitle: String,
    val accentColor: Color,
) {
    K24("24K", "999.9 FINE", "Pure 24K Bullion", RoyalGold),
    K22("22K", "916 HALLMARK", "22K Standard Gold", LightGold),
    K18("18K", "750 FINE", "18K Jewellery Gold", AmberOrange),
}

/**
 * Self-contained Firestore-connected overload of [PriceSummaryCard] that observes live
 * 24K, 22K, and 18K gold rates directly from Firestore `market_price_history` when
 * authenticated.
 */
@Composable
fun PriceSummaryCard(
    onBuyGold: () -> Unit,
    onSellGold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val currentUid = remember(appContext) { FirestoreService.currentUserOrNull(appContext)?.uid }
    val repository = remember(currentUid) {
        if (currentUid != null) SonaWaneRepository(appContext) else null
    }

    var manualOverride by remember(currentUid) { mutableStateOf<FirestoreGoldSummary?>(null) }
    val observedFlow = remember(repository) {
        repository?.observeLiveGoldSummary()
            ?.catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
                emit(
                    FirestoreGoldSummary(
                        isLoading = false,
                        isFromFirestore = false,
                        errorMessage = (fs ?: err).localizedMessage ?: "Failed to fetch live gold rates from Firestore",
                    )
                )
            }
            ?: flowOf(
                FirestoreGoldSummary(
                    isLoading = false,
                    isFromFirestore = false,
                    errorMessage = "Sign in to view live Firestore gold rates.",
                )
            )
    }

    val streamed by observedFlow.collectAsStateWithLifecycle(
        initialValue = FirestoreGoldSummary(isLoading = currentUid != null),
    )
    val effective = manualOverride?.takeIf {
        it.isLoading || !streamed.isFromFirestore || it.lastUpdatedEpochMs > streamed.lastUpdatedEpochMs
    } ?: streamed

    PriceSummaryCard(
        goldSummary = effective,
        onRefresh = {
            val repo = repository ?: return@PriceSummaryCard
            scope.launch {
                manualOverride = effective.copy(isLoading = true, errorMessage = null)
                manualOverride = runCatching { repo.fetchLatestGoldSummaryOnce() }
                    .getOrElse { err ->
                        if (err is CancellationException) throw err
                        effective.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Unable to refresh gold rates from Firestore",
                        )
                    }
            }
        },
        onBuyGold = onBuyGold,
        onSellGold = onSellGold,
        modifier = modifier,
    )
}

/**
 * Clean, high-contrast Home Dashboard card displaying live 24K, 22K, and 18K gold rates
 * fetched from Firestore (`market_price_history`) with instant purity switching and buy/sell actions.
 */
@Composable
fun PriceSummaryCard(
    goldSummary: FirestoreGoldSummary,
    onRefresh: () -> Unit,
    onBuyGold: () -> Unit,
    onSellGold: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackRates: LiveMarketRates? = null,
) {
    var selectedTier by remember { mutableStateOf(GoldKaratTier.K24) }

    val resolved24k = when {
        goldSummary.gold24kPerGram > 0.0 -> goldSummary.gold24kPerGram
        else -> fallbackRates?.gold24kBuyPerGram?.takeIf { it > 0.0 } ?: 0.0
    }
    val resolved22k = when {
        goldSummary.gold22kPerGram > 0.0 -> goldSummary.gold22kPerGram
        else -> fallbackRates?.gold22kBuyPerGram?.takeIf { it > 0.0 }
            ?: if (resolved24k > 0.0) resolved24k * (22.0 / 24.0) else 0.0
    }
    val resolved18k = when {
        goldSummary.gold18kPerGram > 0.0 -> goldSummary.gold18kPerGram
        else -> fallbackRates?.gold18kBuyPerGram?.takeIf { it > 0.0 }
            ?: if (resolved24k > 0.0) resolved24k * (18.0 / 24.0) else 0.0
    }

    val hasRates = resolved24k > 0.0
    val resolvedChangePct = if (goldSummary.isFromFirestore && goldSummary.previous24kPerGram > 0.0) {
        goldSummary.changePercent
    } else {
        fallbackRates?.goldChangePct ?: goldSummary.changePercent
    }
    val resolvedEpoch = if (goldSummary.lastUpdatedEpochMs > 0L) {
        goldSummary.lastUpdatedEpochMs
    } else {
        fallbackRates?.lastUpdatedEpochMs ?: 0L
    }
    val resolvedSource = when {
        goldSummary.isFromFirestore -> "Firestore (${goldSummary.dateStr.ifBlank { "latest" }})"
        hasRates && !fallbackRates?.sourceName.isNullOrBlank() -> fallbackRates?.sourceName.orEmpty()
        else -> goldSummary.source
    }

    val selectedRate = when (selectedTier) {
        GoldKaratTier.K24 -> resolved24k
        GoldKaratTier.K22 -> resolved22k
        GoldKaratTier.K18 -> resolved18k
    }

    val borderBrush = remember {
        Brush.linearGradient(
            colors = listOf(RoyalGold, AmberOrange.copy(alpha = 0.85f), LightGold),
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("price_summary_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalBlack),
        border = BorderStroke(1.3.dp, borderBrush),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                RoyalGold.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.9f, size.height * 0.1f),
                            radius = size.width * 0.55f,
                        ),
                        radius = size.width * 0.55f,
                        center = Offset(size.width * 0.9f, size.height * 0.1f),
                    )
                }
                .padding(18.dp),
        ) {
            // Top Header Row
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RoyalGold.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = LightGold,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.price_summary_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = LightGold,
                                fontWeight = FontWeight.Bold,
                            )
                            StatusPill(
                                text = when {
                                    goldSummary.isFromFirestore -> stringResource(R.string.price_summary_firestore_live)
                                    hasRates -> "LIVE FEED"
                                    else -> stringResource(R.string.price_summary_syncing)
                                },
                                color = if (goldSummary.isFromFirestore) SuccessGreen else AmberOrange,
                                icon = if (goldSummary.isFromFirestore) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (resolvedEpoch > 0L) {
                                "Updated ${Formatters.formatEpoch(resolvedEpoch)}"
                            } else {
                                "Connecting to Firestore..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondarySilver,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasRates) {
                        val isPositive = resolvedChangePct >= 0.0
                        val deltaColor = if (isPositive) SuccessGreen else ErrorRed
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = deltaColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = if (isPositive) "Price up" else "Price down",
                                    tint = deltaColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isPositive) "+${"%.2f".format(resolvedChangePct)}%" else "${"%.2f".format(resolvedChangePct)}%",
                                    style = MonoSmallStyle,
                                    color = deltaColor,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("price_summary_refresh_btn"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.price_summary_refresh_cd),
                            tint = RoyalGold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                goldSummary.isLoading && !hasRates -> {
                    Surface(
                        color = DeepBlack,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GoldBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("price_summary_loading"),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                color = RoyalGold,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = stringResource(R.string.price_summary_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                            )
                        }
                    }
                }

                !goldSummary.errorMessage.isNullOrBlank() && !hasRates -> {
                    Surface(
                        color = ErrorRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = goldSummary.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = PureWhite,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("price_summary_retry_btn"),
                            ) {
                                Text(
                                    text = stringResource(R.string.price_summary_retry),
                                    color = LightGold,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                else -> {
                    // High-contrast Primary Featured Karat Hero Container
                    Surface(
                        color = DeepBlack,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, selectedTier.accentColor.copy(alpha = 0.7f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AnimatedContent(
                            targetState = Triple(selectedTier, selectedRate, resolved24k),
                            transitionSpec = {
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                                    fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                            },
                            label = "hero_gold_karat_transition",
                        ) { (tier, rate, _) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${tier.code} • ${tier.hallmark}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tier.accentColor,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (rate > 0.0) Formatters.formatInr(rate) else "--",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 30.sp,
                                        ),
                                        color = PureWhite,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "${tier.subtitle}  •  per gram",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondarySilver,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "10 GRAMS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondarySilver,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (rate > 0.0) Formatters.formatInr(rate * 10.0) else "--",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = LightGold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // High-contrast 3-column Purity Breakdown: 24K, 22K, 18K
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GoldKaratSummaryTile(
                            tier = GoldKaratTier.K24,
                            ratePerGram = resolved24k,
                            selected = selectedTier == GoldKaratTier.K24,
                            onClick = { selectedTier = GoldKaratTier.K24 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("price_summary_24k_tile"),
                        )
                        GoldKaratSummaryTile(
                            tier = GoldKaratTier.K22,
                            ratePerGram = resolved22k,
                            selected = selectedTier == GoldKaratTier.K22,
                            onClick = { selectedTier = GoldKaratTier.K22 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("price_summary_22k_tile"),
                        )
                        GoldKaratSummaryTile(
                            tier = GoldKaratTier.K18,
                            ratePerGram = resolved18k,
                            selected = selectedTier == GoldKaratTier.K18,
                            onClick = { selectedTier = GoldKaratTier.K18 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("price_summary_18k_tile"),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Source: $resolvedSource",
                style = MaterialTheme.typography.labelSmall,
                color = SecondarySilver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GoldPrimaryButton(
                    text = "Buy Gold",
                    onClick = onBuyGold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_buy_gold"),
                )
                GoldOutlinedButton(
                    text = "Sell Gold",
                    onClick = onSellGold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_sell_gold"),
                )
            }
        }
    }
}

@Composable
private fun GoldKaratSummaryTile(
    tier: GoldKaratTier,
    ratePerGram: Double,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) DeepBlack else ObsidianCard,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) tier.accentColor else GoldBorderSubtle,
        ),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tier.accentColor.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = tier.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = tier.accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    text = tier.hallmark.substringBefore(" "),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                    fontSize = 10.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (ratePerGram > 0.0) Formatters.formatInr(ratePerGram) else "--",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
                color = PureWhite,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (ratePerGram > 0.0) "per g • 10g: ₹${"%,.0f".format(ratePerGram * 10.0)}" else "Awaiting rate",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) LightGold else DarkGold,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
