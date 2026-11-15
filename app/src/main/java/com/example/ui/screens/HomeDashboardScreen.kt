package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPoint
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.WalletHoldings
import com.example.ui.components.EnvironmentModeBadge
import com.example.ui.components.GoldOutlinedButton
import com.example.ui.components.GoldPriceHistoryChart
import com.example.ui.components.LuxuryCard
import com.example.ui.components.MarketTrendsDashboard
import com.example.ui.components.OrangeActionButton
import com.example.ui.components.PortfolioSummaryCard
import com.example.ui.components.PriceSummaryCard
import com.example.ui.components.StatusPill
import com.example.ui.components.statusColorFor
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
import com.example.util.Formatters

@Composable
fun HomeDashboardScreen(
    profile: UserProfile?,
    wallet: WalletHoldings?,
    rates: LiveMarketRates,
    firestoreGoldSummary: FirestoreGoldSummary = FirestoreGoldSummary(),
    chartPoints: List<MarketPoint>,
    dualTrendSeries: DualMetalTrendSeries = DualMetalTrendSeries(),
    marketHistory30d: List<MarketPriceHistoryEntry> = emptyList(),
    chartLoading: Boolean = false,
    marketHistoryError: String? = null,
    announcements: List<Announcement>,
    appConfig: AppConfig,
    unreadNotifications: Int,
    onRefreshRates: () -> Unit,
    onRefreshFirestoreGold: () -> Unit = onRefreshRates,
    onSelectChart: (Boolean, String) -> Unit,
    onSelectTrendRange: (String) -> Unit = {},
    onSyncFirestoreHistory: () -> Unit = onRefreshRates,
    onBuyGold: () -> Unit,
    onSellGold: () -> Unit,
    onBuySilver: () -> Unit,
    onSellSilver: () -> Unit,
    onAddMoney: () -> Unit,
    onWithdrawMoney: () -> Unit,
    onTransfer: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenAlerts: () -> Unit = {},
    onOpenCalculator: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    transactions: List<TransactionRecord> = emptyList(),
    onViewReceipt: (TransactionRecord) -> Unit = {},
) {
    val w = wallet ?: WalletHoldings()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = R.drawable.swg_launcher_emblem),
                        contentDescription = "SONA-WANE GOLD",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, RoyalGold, CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("SONA-WANE GOLD", style = MaterialTheme.typography.labelSmall, color = RoyalGold)
                        Text(
                            text = "Namaste, ${profile?.fullName?.split(" ")?.firstOrNull() ?: "Investor"}",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ObsidianCard,
                        border = BorderStroke(1.dp, GoldBorderSubtle),
                        modifier = Modifier.clickable(onClick = onAddMoney),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Formatters.formatInr(w.availableInr),
                                style = MonoSmallStyle,
                                color = LightGold,
                            )
                        }
                    }
                    IconButton(onClick = onOpenNotifications, modifier = Modifier.testTag("notifications_bell")) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge(containerColor = AmberOrange, contentColor = DeepBlack) {
                                        Text("$unreadNotifications")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = PureWhite)
                        }
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("header_settings_btn")) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = PureWhite)
                    }
                    IconButton(onClick = onOpenProfile, modifier = Modifier.testTag("header_profile_btn")) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = RoyalGold)
                    }
                }
            }
        }

        // Mode & Maintenance Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EnvironmentModeBadge(appConfig.paymentMode)
                StatusPill(
                    text = "KYC: ${profile?.kycStatus?.replace("_", " ") ?: "NOT STARTED"}",
                    color = statusColorFor(profile?.kycStatus ?: "NOT_STARTED"),
                )
            }
            if (appConfig.maintenanceMode) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ErrorRed.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ErrorRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Scheduled Maintenance Active: New trades may be restricted by Administrator.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Announcements Ticker
        if (announcements.isNotEmpty()) {
            item {
                val topAnn = announcements.first()
                Surface(
                    color = AmberOrange.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, AmberOrange.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = AmberOrange)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(topAnn.title, style = MaterialTheme.typography.labelLarge, color = LightGold)
                            Text(topAnn.message, style = MaterialTheme.typography.bodySmall, color = PureWhite)
                        }
                    }
                }
            }
        }

        // Portfolio Summary Card (Firestore live gold rates & local currency valuation)
        item {
            PortfolioSummaryCard(
                wallet = wallet,
                firestoreGoldSummary = firestoreGoldSummary,
                fallbackRates = rates,
                onRefresh = onRefreshFirestoreGold,
            )
        }

        // Quick Actions Strip
        item {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = PureWhite)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickActionChip("Buy Gold", Icons.Default.AddCircle, RoyalGold, onBuyGold)
                QuickActionChip("Gold Calc", Icons.Default.Calculate, LightGold, onOpenCalculator)
                QuickActionChip("Sell Gold", Icons.Default.ArrowUpward, AmberOrange, onSellGold)
                QuickActionChip("Buy Silver", Icons.Default.AddCircle, SilverAccent, onBuySilver)
                QuickActionChip("Sell Silver", Icons.Default.ArrowUpward, SecondarySilver, onSellSilver)
                QuickActionChip("Add Money", Icons.Default.CurrencyRupee, SuccessGreen, onAddMoney)
                QuickActionChip("Withdraw", Icons.Default.ArrowDownward, AmberOrange, onWithdrawMoney)
                QuickActionChip("Transfer", Icons.Default.Send, RoyalGold, onTransfer)
                QuickActionChip("My / Scan QR", Icons.Default.QrCode2, LightGold, onOpenQr)
                QuickActionChip("Price Alerts", Icons.Default.AddAlert, AmberOrange, onOpenAlerts)
                QuickActionChip("History", Icons.Default.History, SecondarySilver, onOpenHistory)
                QuickActionChip("Market", Icons.Default.ShowChart, RoyalGold, onOpenMarket)
            }
        }

        // LIVE FIRESTORE GOLD PRICE SUMMARY CARD (24K, 22K, 18K)
        item {
            PriceSummaryCard(
                goldSummary = firestoreGoldSummary,
                fallbackRates = rates,
                onRefresh = onRefreshFirestoreGold,
                onBuyGold = onBuyGold,
                onSellGold = onSellGold,
            )
        }

        // SILVER RATE CARD
        item {
            LuxuryCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("999 PURE SILVER RATE", style = MaterialTheme.typography.labelMedium, color = SilverAccent)
                        Text(
                            text = if (rates.silver999BuyPerGram > 0.0) Formatters.formatInr(rates.silver999BuyPerGram) + " / g" else "--",
                            style = MaterialTheme.typography.headlineLarge,
                            color = PureWhite,
                        )
                        Text(
                            text = if (rates.silver999BuyPerGram > 0.0) "1 kg: ${Formatters.formatInr(rates.silver999BuyPerGram * 1000.0)}" else "Tap refresh to load feed",
                            style = MonoSmallStyle,
                            color = SecondarySilver,
                        )
                    }
                    StatusPill(text = "+${rates.silverChangePct}%", color = SuccessGreen)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OrangeActionButton(
                        text = "Buy Silver",
                        onClick = onBuySilver,
                        modifier = Modifier.weight(1f).testTag("home_buy_silver"),
                    )
                    GoldOutlinedButton(
                        text = "Sell Silver",
                        onClick = onSellSilver,
                        modifier = Modifier.weight(1f).testTag("home_sell_silver"),
                    )
                }
            }
        }

        // MARKET OVERVIEW CHART (Vico Interactive 30-Day Gold & Silver Trends from Firestore)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Market Overview", style = MaterialTheme.typography.titleLarge, color = PureWhite)
                    TextButton(onClick = onOpenMarket) {
                        Text("Full Market & Alerts", color = AmberOrange)
                    }
                }
                GoldPriceHistoryChart(
                    historyEntries = marketHistory30d,
                    isLoading = chartLoading,
                    errorMessage = marketHistoryError,
                    onRefreshFromFirestore = onSyncFirestoreHistory,
                )
                MarketTrendsDashboard(
                    historyEntries = marketHistory30d,
                    rates = rates,
                    isLoading = chartLoading,
                    errorMessage = marketHistoryError,
                    onSyncHistory = onSyncFirestoreHistory,
                )
                Text(
                    text = "Source: Firestore market_price_history • ${rates.sourceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondarySilver,
                )
            }
        }

        // RECENT GOLD & SILVER TRADES (Firestore Live History Component)
        item {
            RecentBullionTradesSection(
                transactions = transactions,
                onSelectTransaction = onViewReceipt,
                onViewAllHistory = onOpenHistory,
            )
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianCard,
        border = BorderStroke(1.dp, GoldBorderSubtle),
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = PureWhite, maxLines = 1)
        }
    }
}
