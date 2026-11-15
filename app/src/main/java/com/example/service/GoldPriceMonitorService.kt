package com.example.service

import android.content.Context
import com.example.data.local.BgDropMonitorState
import com.example.data.local.GoldDropInAppAlert
import com.example.data.local.SecurityPreferences
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.LiveMarketRates
import com.example.data.remote.MarketRateService
import com.example.data.repository.SonaWaneRepository
import com.example.util.BullionNotificationHelper
import com.example.util.Formatters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first

data class GoldDropEvaluationResult(
    val purity: String,
    val liveRateInr: Double,
    val thresholdInr: Double,
    val droppedBelowThreshold: Boolean,
    val shouldNotify: Boolean,
    val dropAmountInr: Double,
    val dropPercent: Double,
    val status: String,
    val alertTitle: String = "",
    val alertMessage: String = "",
)

data class BackgroundMonitorCheckResult(
    val outcome: GoldDropEvaluationResult,
    val pushDispatched: Boolean,
    val inAppAlertSaved: Boolean,
    val shouldRetry: Boolean = false,
)

/**
 * Monitors live and Firestore gold prices against the user-defined Settings threshold
 * and dispatches both system push notifications and persistent in-app alerts.
 */
class GoldPriceMonitorService(
    private val context: Context,
    private val prefs: SecurityPreferences = SecurityPreferences(context.applicationContext),
    private val marketService: MarketRateService = MarketRateService(),
) {

    suspend fun performBackgroundCheck(
        isManual: Boolean,
        playSound: Boolean = true,
        vibrate: Boolean = true,
    ): BackgroundMonitorCheckResult {
        val config = prefs.bgDropMonitorFlow.first()
        if (!config.enabled && !isManual) {
            return BackgroundMonitorCheckResult(
                outcome = evaluateThresholdDrop(
                    purity = config.purity,
                    liveRateInr = config.lastObservedRateInr,
                    thresholdInr = config.threshold24kInr,
                    enabled = false,
                    lastNotifiedEpochMs = config.lastNotifiedEpochMs,
                    nowMs = System.currentTimeMillis(),
                    forceNotify = false,
                ),
                pushDispatched = false,
                inAppAlertSaved = false,
            )
        }

        val nowMs = System.currentTimeMillis()
        if (config.threshold24kInr <= 0.0) {
            prefs.recordBgDropCheck(
                observedRateInr = config.lastObservedRateInr,
                status = "NO_THRESHOLD",
                checkedAtMs = nowMs,
            )
            return BackgroundMonitorCheckResult(
                outcome = evaluateThresholdDrop(
                    purity = config.purity,
                    liveRateInr = config.lastObservedRateInr,
                    thresholdInr = config.threshold24kInr,
                    enabled = config.enabled,
                    lastNotifiedEpochMs = config.lastNotifiedEpochMs,
                    nowMs = nowMs,
                    forceNotify = isManual,
                ),
                pushDispatched = false,
                inAppAlertSaved = false,
            )
        }

        val liveRates = runCatching { marketService.fetchLiveRates() }.getOrElse { LiveMarketRates() }
        val firestoreSummary = if (FirebaseAuth.getInstance().currentUser != null) {
            runCatching { SonaWaneRepository(context.applicationContext).fetchLatestGoldSummaryOnce() }.getOrNull()
        } else {
            null
        }

        val resolvedRate = resolveMonitoredGoldRate(
            purity = config.purity,
            liveRates = liveRates,
            firestoreSummary = firestoreSummary,
        )

        if (resolvedRate <= 0.0) {
            prefs.recordBgDropCheck(
                observedRateInr = config.lastObservedRateInr,
                status = "NETWORK_RETRY",
                checkedAtMs = nowMs,
            )
            return BackgroundMonitorCheckResult(
                outcome = evaluateThresholdDrop(
                    purity = config.purity,
                    liveRateInr = 0.0,
                    thresholdInr = config.threshold24kInr,
                    enabled = config.enabled,
                    lastNotifiedEpochMs = config.lastNotifiedEpochMs,
                    nowMs = nowMs,
                    forceNotify = isManual,
                ),
                pushDispatched = false,
                inAppAlertSaved = false,
                shouldRetry = true,
            )
        }

        if (liveRates.gold24kBuyPerGram > 0.0) {
            prefs.cacheRates(
                gold24k = liveRates.gold24kBuyPerGram,
                silver999 = liveRates.silver999BuyPerGram,
                source = liveRates.sourceName,
                timestampMs = liveRates.lastUpdatedEpochMs,
            )
        }

        return evaluateAndDispatch(
            config = config,
            observedRateInr = resolvedRate,
            playSound = playSound,
            vibrate = vibrate,
            forceNotify = isManual,
            nowMs = nowMs,
        )
    }

    suspend fun evaluateAndDispatch(
        config: BgDropMonitorState,
        observedRateInr: Double,
        playSound: Boolean = true,
        vibrate: Boolean = true,
        forceNotify: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): BackgroundMonitorCheckResult {
        val result = evaluateThresholdDrop(
            purity = config.purity,
            liveRateInr = observedRateInr,
            thresholdInr = config.threshold24kInr,
            enabled = config.enabled,
            lastNotifiedEpochMs = config.lastNotifiedEpochMs,
            nowMs = nowMs,
            forceNotify = forceNotify,
        )

        var pushDispatched = false
        var inAppSaved = false

        when (result.status) {
            "DROP_TRIGGERED" -> {
                val hasPushPerm = BullionNotificationHelper.hasNotificationPermission(context)
                if (hasPushPerm) {
                    BullionNotificationHelper.postPriceAlertPush(
                        context = context,
                        title = result.alertTitle,
                        body = result.alertMessage,
                        playSound = playSound,
                        vibrate = vibrate,
                        notificationId = DROP_NOTIFICATION_ID,
                    )
                    pushDispatched = true
                }
                prefs.recordInAppDropAlert(
                    GoldDropInAppAlert(
                        purity = result.purity,
                        observedRateInr = result.liveRateInr,
                        thresholdInr = result.thresholdInr,
                        triggeredAtMs = nowMs,
                        message = result.alertMessage,
                    )
                )
                inAppSaved = true
                prefs.recordBgDropCheck(
                    observedRateInr = result.liveRateInr,
                    status = result.status,
                    checkedAtMs = nowMs,
                    notifiedAtMs = nowMs,
                )
            }
            "PAUSED" -> Unit
            else -> {
                prefs.recordBgDropCheck(
                    observedRateInr = result.liveRateInr.takeIf { it > 0.0 } ?: config.lastObservedRateInr,
                    status = result.status,
                    checkedAtMs = nowMs,
                )
            }
        }

        return BackgroundMonitorCheckResult(
            outcome = result,
            pushDispatched = pushDispatched,
            inAppAlertSaved = inAppSaved,
            shouldRetry = result.status == "NETWORK_RETRY",
        )
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 30L * 60L * 1000L
        const val DROP_NOTIFICATION_ID = 7724

        fun purityLabel(purity: String): String = when (purity.uppercase()) {
            "GOLD_22K", "22K" -> "22K Gold"
            "GOLD_18K", "18K" -> "18K Gold"
            else -> "24K Gold"
        }

        fun rateForPurity(rates: LiveMarketRates, purity: String): Double = when (purity.uppercase()) {
            "GOLD_22K", "22K" -> rates.gold22kBuyPerGram
            "GOLD_18K", "18K" -> rates.gold18kBuyPerGram
            else -> rates.gold24kBuyPerGram
        }

        fun rateForPurity(summary: FirestoreGoldSummary?, purity: String): Double {
            if (summary == null || !summary.isFromFirestore) return 0.0
            return when (purity.uppercase()) {
                "GOLD_22K", "22K" -> summary.gold22kPerGram
                "GOLD_18K", "18K" -> summary.gold18kPerGram
                else -> summary.gold24kPerGram
            }
        }

        fun resolveMonitoredGoldRate(
            purity: String,
            liveRates: LiveMarketRates,
            firestoreSummary: FirestoreGoldSummary?,
            preferFirestore: Boolean = false,
        ): Double {
            val fsRate = rateForPurity(firestoreSummary, purity)
            val spotRate = rateForPurity(liveRates, purity)
            return if (preferFirestore) {
                if (fsRate > 0.0) fsRate else spotRate
            } else {
                when {
                    liveRates.isLive && spotRate > 0.0 -> spotRate
                    fsRate > 0.0 -> fsRate
                    else -> spotRate
                }
            }
        }

        fun evaluateThresholdDrop(
            purity: String = "GOLD_24K",
            liveRateInr: Double,
            thresholdInr: Double,
            enabled: Boolean,
            lastNotifiedEpochMs: Long,
            nowMs: Long,
            cooldownMs: Long = DEFAULT_COOLDOWN_MS,
            forceNotify: Boolean = false,
        ): GoldDropEvaluationResult {
            val normalizedPurity = when (purity.uppercase()) {
                "GOLD_22K", "22K" -> "GOLD_22K"
                "GOLD_18K", "18K" -> "GOLD_18K"
                else -> "GOLD_24K"
            }

            if (!enabled && !forceNotify) {
                return GoldDropEvaluationResult(
                    purity = normalizedPurity,
                    liveRateInr = liveRateInr,
                    thresholdInr = thresholdInr,
                    droppedBelowThreshold = false,
                    shouldNotify = false,
                    dropAmountInr = 0.0,
                    dropPercent = 0.0,
                    status = "PAUSED",
                )
            }

            if (thresholdInr <= 0.0) {
                return GoldDropEvaluationResult(
                    purity = normalizedPurity,
                    liveRateInr = liveRateInr,
                    thresholdInr = thresholdInr,
                    droppedBelowThreshold = false,
                    shouldNotify = false,
                    dropAmountInr = 0.0,
                    dropPercent = 0.0,
                    status = "NO_THRESHOLD",
                )
            }

            if (liveRateInr <= 0.0) {
                return GoldDropEvaluationResult(
                    purity = normalizedPurity,
                    liveRateInr = 0.0,
                    thresholdInr = thresholdInr,
                    droppedBelowThreshold = false,
                    shouldNotify = false,
                    dropAmountInr = 0.0,
                    dropPercent = 0.0,
                    status = "NETWORK_RETRY",
                )
            }

            val droppedBelow = liveRateInr < thresholdInr
            val dropAmt = (thresholdInr - liveRateInr).coerceAtLeast(0.0)
            val dropPct = if (droppedBelow && thresholdInr > 0.0) (dropAmt / thresholdInr) * 100.0 else 0.0
            val cooldownElapsed = lastNotifiedEpochMs <= 0L || (nowMs - lastNotifiedEpochMs) >= cooldownMs

            if (droppedBelow && (cooldownElapsed || forceNotify)) {
                val label = purityLabel(normalizedPurity)
                val title = "$label Drop Alert: ${Formatters.formatInr(liveRateInr)}/g"
                val message = "$label dropped to ${Formatters.formatInr(liveRateInr)}/g — ${Formatters.formatInr(dropAmt)}/g (${String.format(java.util.Locale.US, "%.2f", dropPct)}%) below your ${Formatters.formatInr(thresholdInr)}/g Settings threshold."
                return GoldDropEvaluationResult(
                    purity = normalizedPurity,
                    liveRateInr = liveRateInr,
                    thresholdInr = thresholdInr,
                    droppedBelowThreshold = true,
                    shouldNotify = true,
                    dropAmountInr = dropAmt,
                    dropPercent = dropPct,
                    status = "DROP_TRIGGERED",
                    alertTitle = title,
                    alertMessage = message,
                )
            }

            if (droppedBelow) {
                return GoldDropEvaluationResult(
                    purity = normalizedPurity,
                    liveRateInr = liveRateInr,
                    thresholdInr = thresholdInr,
                    droppedBelowThreshold = true,
                    shouldNotify = false,
                    dropAmountInr = dropAmt,
                    dropPercent = dropPct,
                    status = "COOLDOWN_ACTIVE",
                )
            }

            return GoldDropEvaluationResult(
                purity = normalizedPurity,
                liveRateInr = liveRateInr,
                thresholdInr = thresholdInr,
                droppedBelowThreshold = false,
                shouldNotify = false,
                dropAmountInr = 0.0,
                dropPercent = 0.0,
                status = "MONITORING_OK",
            )
        }
    }
}
