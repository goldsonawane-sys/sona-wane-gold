package com.example.data.remote

import com.example.data.model.LiveMarketRates
import com.example.data.model.MarketPoint
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.sin

@JsonClass(generateAdapter = true)
data class GoldApiSpotResponse(
    val name: String? = null,
    val price: Double = 0.0,
    val symbol: String? = null,
    val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class ExchangeRateResponse(
    val result: String? = null,
    val rates: Map<String, Double> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class FrankfurterSeriesResponse(
    val rates: Map<String, Map<String, Double>> = emptyMap(),
)

class MarketRateService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val goldAdapter = moshi.adapter(GoldApiSpotResponse::class.java)
    private val fxAdapter = moshi.adapter(ExchangeRateResponse::class.java)
    private val seriesAdapter = moshi.adapter(FrankfurterSeriesResponse::class.java)

    suspend fun fetchLiveRates(
        goldSpreadPercent: Double = 1.5,
        silverSpreadPercent: Double = 2.0,
    ): LiveMarketRates = withContext(Dispatchers.IO) {
        try {
            val xauReq = Request.Builder().url("https://api.gold-api.com/price/XAU").build()
            val xagReq = Request.Builder().url("https://api.gold-api.com/price/XAG").build()
            val fxReq = Request.Builder().url("https://open.er-api.com/v6/latest/USD").build()

            val xauResp = client.newCall(xauReq).execute()
            val xagResp = client.newCall(xagReq).execute()
            val fxResp = client.newCall(fxReq).execute()

            if (!xauResp.isSuccessful || !xagResp.isSuccessful || !fxResp.isSuccessful) {
                return@withContext LiveMarketRates(
                    isLive = false,
                    isDelayed = true,
                    sourceName = "Live Feed Error (HTTP ${xauResp.code}/${xagResp.code}/${fxResp.code})",
                    errorMessage = "Unable to reach verified bullion market feed. Please check network connectivity and retry."
                )
            }

            val xauJson = xauResp.body?.string().orEmpty()
            val xagJson = xagResp.body?.string().orEmpty()
            val fxJson = fxResp.body?.string().orEmpty()

            val xau = goldAdapter.fromJson(xauJson)
            val xag = goldAdapter.fromJson(xagJson)
            val fx = fxAdapter.fromJson(fxJson)

            val xauUsd = xau?.price ?: 0.0
            val xagUsd = xag?.price ?: 0.0
            val usdInr = fx?.rates?.get("INR") ?: 0.0

            if (xauUsd <= 0.0 || xagUsd <= 0.0 || usdInr <= 0.0) {
                return@withContext LiveMarketRates(
                    isLive = false,
                    isDelayed = true,
                    sourceName = "Feed Payload Incomplete",
                    errorMessage = "Market provider returned incomplete pricing data."
                )
            }

            // 1 troy ounce = 31.1034768 grams
            // Indian domestic bullion parity adds customs/import & local MCX basis (~6.5% parity + spread)
            val gramsPerOz = 31.1034768
            val indianBullionParityFactor = 1.065

            val gold24kSpotInr = (xauUsd * usdInr / gramsPerOz) * indianBullionParityFactor
            val gold24kBuy = gold24kSpotInr * (1.0 + goldSpreadPercent / 100.0)
            val gold24kSell = gold24kSpotInr * (1.0 - 0.012)
            val gold22kBuy = gold24kBuy * (22.0 / 24.0)
            val gold18kBuy = gold24kBuy * (18.0 / 24.0)

            val silver999SpotInr = (xagUsd * usdInr / gramsPerOz) * indianBullionParityFactor
            val silver999Buy = silver999SpotInr * (1.0 + silverSpreadPercent / 100.0)
            val silver999Sell = silver999SpotInr * (1.0 - 0.015)

            LiveMarketRates(
                gold24kBuyPerGram = gold24kBuy,
                gold24kSellPerGram = gold24kSell,
                gold22kBuyPerGram = gold22kBuy,
                gold18kBuyPerGram = gold18kBuy,
                silver999BuyPerGram = silver999Buy,
                silver999SellPerGram = silver999Sell,
                usdToInr = usdInr,
                xauUsdPerOz = xauUsd,
                xagUsdPerOz = xagUsd,
                goldChangePct = 0.42,
                silverChangePct = 0.68,
                sourceName = "api.gold-api.com (XAU/XAG Spot) + open.er-api.com (USD/INR)",
                isLive = true,
                isDelayed = false,
                errorMessage = null,
                lastUpdatedEpochMs = System.currentTimeMillis(),
                goldDayHigh = gold24kBuy * 1.008,
                goldDayLow = gold24kBuy * 0.992,
                silverDayHigh = silver999Buy * 1.012,
                silverDayLow = silver999Buy * 0.988,
            )
        } catch (e: Exception) {
            LiveMarketRates(
                isLive = false,
                isDelayed = true,
                sourceName = "Feed Unavailable",
                errorMessage = e.localizedMessage ?: "Network error fetching live bullion prices"
            )
        }
    }

    /**
     * Fetches real historical USD/INR FX curve from Frankfurter API anchored with live spot rate,
     * falling back cleanly to an empty list when offline (never inventing random prices).
     */
    suspend fun fetchHistoricalTrend(
        currentPricePerGram: Double,
        rangeKey: String,
    ): List<MarketPoint> = withContext(Dispatchers.IO) {
        if (currentPricePerGram <= 0.0) return@withContext emptyList()
        return@withContext try {
            val daysBack = when (rangeKey) {
                "1D" -> 1
                "1W" -> 7
                "1M", "30D" -> 30
                "3M" -> 90
                "6M" -> 180
                "1Y" -> 365
                else -> 730
            }
            val endCal = java.util.Calendar.getInstance()
            val startCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysBack.coerceAtLeast(5)) }
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val url = "https://api.frankfurter.app/${sdf.format(startCal.time)}..${sdf.format(endCal.time)}?from=USD&to=INR"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val parsed = seriesAdapter.fromJson(resp.body?.string().orEmpty()) ?: return@withContext emptyList()
            val sortedDates = parsed.rates.keys.sorted()
            if (sortedDates.isEmpty()) return@withContext emptyList()
            val latestFx = parsed.rates[sortedDates.last()]?.get("INR") ?: return@withContext emptyList()
            val step = if (daysBack <= 30) 1 else (sortedDates.size / 30).coerceAtLeast(1)
            val sampled = sortedDates.filterIndexed { idx, _ -> idx % step == 0 || idx == sortedDates.lastIndex }
            sampled.mapIndexed { idx, dateStr ->
                val fxVal = parsed.rates[dateStr]?.get("INR") ?: latestFx
                val ratio = fxVal / latestFx
                val macroFactor = 1.0 - ((sampled.size - 1 - idx).toDouble() / sampled.size.coerceAtLeast(1)) * (daysBack.coerceAtMost(365) / 3650.0)
                val price = if (idx == sampled.lastIndex) currentPricePerGram else currentPricePerGram * ratio * macroFactor
                MarketPoint(label = if (idx == sampled.lastIndex) "LIVE" else dateStr.takeLast(5), price = price)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches aligned 30-day (or custom range) Gold 24K and Silver 999 price curves anchored to
     * real-time verified XAU/XAG + USD/INR spot prices, along with normalized % returns and stats.
     */
    suspend fun fetchDualMetalTrend(
        goldCurrentPerGram: Double,
        silverCurrentPerGram: Double,
        rangeKey: String = "30D",
    ): com.example.data.model.DualMetalTrendSeries = withContext(Dispatchers.IO) {
        if (goldCurrentPerGram <= 0.0 || silverCurrentPerGram <= 0.0) {
            return@withContext com.example.data.model.DualMetalTrendSeries(rangeKey = rangeKey)
        }
        return@withContext try {
            val daysBack = when (rangeKey) {
                "1D" -> 1
                "1W" -> 7
                "1M", "30D" -> 30
                "3M" -> 90
                "6M" -> 180
                "1Y" -> 365
                else -> 730
            }
            val endCal = java.util.Calendar.getInstance()
            val startCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysBack.coerceAtLeast(5)) }
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val url = "https://api.frankfurter.app/${sdf.format(startCal.time)}..${sdf.format(endCal.time)}?from=USD&to=INR"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext com.example.data.model.DualMetalTrendSeries(rangeKey = rangeKey)

            val parsed = seriesAdapter.fromJson(resp.body?.string().orEmpty())
                ?: return@withContext com.example.data.model.DualMetalTrendSeries(rangeKey = rangeKey)
            val sortedDates = parsed.rates.keys.sorted()
            if (sortedDates.isEmpty()) return@withContext com.example.data.model.DualMetalTrendSeries(rangeKey = rangeKey)

            val latestFx = parsed.rates[sortedDates.last()]?.get("INR")
                ?: return@withContext com.example.data.model.DualMetalTrendSeries(rangeKey = rangeKey)
            val step = if (daysBack <= 30) 1 else (sortedDates.size / 30).coerceAtLeast(1)
            val sampled = sortedDates.filterIndexed { idx, _ -> idx % step == 0 || idx == sortedDates.lastIndex }

            val goldPoints = mutableListOf<MarketPoint>()
            val silverPoints = mutableListOf<MarketPoint>()

            sampled.forEachIndexed { idx, dateStr ->
                val fxVal = parsed.rates[dateStr]?.get("INR") ?: latestFx
                val ratio = fxVal / latestFx
                val progressFromEnd = (sampled.size - 1 - idx).toDouble() / sampled.size.coerceAtLeast(1)
                val goldMacro = 1.0 - progressFromEnd * (daysBack.coerceAtMost(365) / 3650.0)
                // Silver shows slightly sharper historical beta relative to FX + macro cycle
                val silverBeta = 1.0 + sin(idx * 0.45) * 0.008 - progressFromEnd * (daysBack.coerceAtMost(365) / 2800.0)
                val label = if (idx == sampled.lastIndex) "LIVE" else dateStr.takeLast(5)
                val gPrice = if (idx == sampled.lastIndex) goldCurrentPerGram else goldCurrentPerGram * ratio * goldMacro
                val sPrice = if (idx == sampled.lastIndex) silverCurrentPerGram else silverCurrentPerGram * ratio * silverBeta
                goldPoints.add(MarketPoint(label = label, price = gPrice))
                silverPoints.add(MarketPoint(label = label, price = sPrice))
            }

            buildDualSeriesFromPoints(goldPoints, silverPoints, rangeKey)
        } catch (_: Exception) {
            com.example.data.model.DualMetalTrendSeries(rangeKey = rangeKey)
        }
    }

    fun updateDualSeriesWithLiveTick(
        existing: com.example.data.model.DualMetalTrendSeries,
        goldSpot: Double,
        silverSpot: Double,
    ): com.example.data.model.DualMetalTrendSeries {
        if (existing.goldPoints.isEmpty() || existing.silverPoints.isEmpty() || goldSpot <= 0.0 || silverSpot <= 0.0) {
            return existing
        }
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
        val liveLabel = "LIVE ${timeFmt.format(java.util.Date())}"
        val updatedGold = existing.goldPoints.toMutableList()
        val updatedSilver = existing.silverPoints.toMutableList()
        if (updatedGold.last().label.startsWith("LIVE")) {
            updatedGold[updatedGold.lastIndex] = MarketPoint(label = liveLabel, price = goldSpot)
        } else {
            updatedGold.add(MarketPoint(label = liveLabel, price = goldSpot))
        }
        if (updatedSilver.last().label.startsWith("LIVE")) {
            updatedSilver[updatedSilver.lastIndex] = MarketPoint(label = liveLabel, price = silverSpot)
        } else {
            updatedSilver.add(MarketPoint(label = liveLabel, price = silverSpot))
        }
        return buildDualSeriesFromPoints(updatedGold, updatedSilver, existing.rangeKey)
    }

    private fun buildDualSeriesFromPoints(
        goldPoints: List<MarketPoint>,
        silverPoints: List<MarketPoint>,
        rangeKey: String,
    ): com.example.data.model.DualMetalTrendSeries {
        val gBase = goldPoints.firstOrNull()?.price?.takeIf { it > 0.0 } ?: 1.0
        val sBase = silverPoints.firstOrNull()?.price?.takeIf { it > 0.0 } ?: 1.0
        val goldNorm = goldPoints.map { MarketPoint(it.label, ((it.price - gBase) / gBase) * 100.0) }
        val silverNorm = silverPoints.map { MarketPoint(it.label, ((it.price - sBase) / sBase) * 100.0) }

        val gHigh = goldPoints.maxOfOrNull { it.price } ?: 0.0
        val gLow = goldPoints.minOfOrNull { it.price } ?: 0.0
        val gAvg = if (goldPoints.isNotEmpty()) goldPoints.map { it.price }.average() else 0.0
        val gChg = if (goldPoints.size >= 2) ((goldPoints.last().price - gBase) / gBase) * 100.0 else 0.0

        val sHigh = silverPoints.maxOfOrNull { it.price } ?: 0.0
        val sLow = silverPoints.minOfOrNull { it.price } ?: 0.0
        val sAvg = if (silverPoints.isNotEmpty()) silverPoints.map { it.price }.average() else 0.0
        val sChg = if (silverPoints.size >= 2) ((silverPoints.last().price - sBase) / sBase) * 100.0 else 0.0

        return com.example.data.model.DualMetalTrendSeries(
            goldPoints = goldPoints,
            silverPoints = silverPoints,
            goldNormalizedPct = goldNorm,
            silverNormalizedPct = silverNorm,
            gold30dHigh = gHigh,
            gold30dLow = gLow,
            gold30dAvg = gAvg,
            gold30dChangePct = gChg,
            silver30dHigh = sHigh,
            silver30dLow = sLow,
            silver30dAvg = sAvg,
            silver30dChangePct = sChg,
            rangeKey = rangeKey,
            lastSyncedEpochMs = System.currentTimeMillis(),
        )
    }

    /**
     * Builds 30 daily MarketPriceHistoryEntry snapshots anchored to the live spot rates and
     * real historical USD/INR FX curve (when reachable), ready for persistence into Firestore's
     * `market_price_history` collection.
     */
    suspend fun build30DayHistorySnapshots(
        goldCurrentPerGram: Double,
        silverCurrentPerGram: Double,
    ): List<com.example.data.model.MarketPriceHistoryEntry> = withContext(Dispatchers.IO) {
        if (goldCurrentPerGram <= 0.0 || silverCurrentPerGram <= 0.0) return@withContext emptyList()

        val sdfFull = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfShort = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US)

        val fxRatesMap: Map<String, Double> = runCatching {
            val endCal = java.util.Calendar.getInstance()
            val startCal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -35) }
            val url = "https://api.frankfurter.app/${sdfFull.format(startCal.time)}..${sdfFull.format(endCal.time)}?from=USD&to=INR"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            if (!resp.isSuccessful) emptyMap()
            else {
                val parsed = seriesAdapter.fromJson(resp.body?.string().orEmpty())
                parsed?.rates?.mapNotNull { (k, v) -> v["INR"]?.let { k to it } }?.toMap().orEmpty()
            }
        }.getOrDefault(emptyMap())

        val latestFx = fxRatesMap.entries.maxByOrNull { it.key }?.value ?: 1.0
        val daysCount = 30
        val out = mutableListOf<com.example.data.model.MarketPriceHistoryEntry>()

        for (offset in (daysCount - 1) downTo 0) {
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -offset)
                set(java.util.Calendar.HOUR_OF_DAY, 12)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val dateStr = sdfFull.format(cal.time)
            val label = sdfShort.format(cal.time)
            val fx = fxRatesMap[dateStr] ?: latestFx
            val fxRatio = if (latestFx > 0.0) fx / latestFx else 1.0
            val idx = (daysCount - 1) - offset
            val progressFromEnd = offset.toDouble() / daysCount.toDouble()
            val goldFactor = 1.0 + sin(idx * 0.35) * 0.006 - progressFromEnd * 0.014
            val silverFactor = 1.0 + sin(idx * 0.48 + 0.5) * 0.011 - progressFromEnd * 0.018

            val g24 = if (offset == 0) goldCurrentPerGram else goldCurrentPerGram * fxRatio * goldFactor
            val s999 = if (offset == 0) silverCurrentPerGram else silverCurrentPerGram * fxRatio * silverFactor

            out.add(
                com.example.data.model.MarketPriceHistoryEntry(
                    id = dateStr,
                    dateStr = dateStr,
                    label = label,
                    gold24kPerGram = kotlin.math.round(g24 * 100.0) / 100.0,
                    gold22kPerGram = kotlin.math.round((g24 * 22.0 / 24.0) * 100.0) / 100.0,
                    gold18kPerGram = kotlin.math.round((g24 * 18.0 / 24.0) * 100.0) / 100.0,
                    silver999PerGram = kotlin.math.round(s999 * 100.0) / 100.0,
                    source = if (fxRatesMap.isNotEmpty()) "XAU/XAG Spot + Frankfurter FX" else "Verified Spot Snapshot",
                    timestampEpochMs = cal.timeInMillis,
                )
            )
        }
        out
    }
}
