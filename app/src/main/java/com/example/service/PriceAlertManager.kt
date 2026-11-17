package com.example.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.PriceAlertTarget
import com.example.data.local.SecurityPreferences
import com.example.data.model.LiveMarketRates
import com.example.data.model.PriceAlert
import com.example.data.remote.MarketRateService
import com.example.data.repository.FirestoreService
import com.example.data.repository.SonaWaneRepository
import com.example.util.BullionNotificationHelper
import com.example.util.Formatters
import com.example.worker.PriceAlertCheckWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.math.abs

data class FirestoreThresholdSnapshot(
    val authenticatedUid: String? = null,
    val alerts: List<PriceAlert> = emptyList(),
    val latestMarketRates: LiveMarketRates? = null,
    val priceAlertsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
)

interface FirestoreThresholdDataSource {
    suspend fun fetchSnapshot(): FirestoreThresholdSnapshot
    suspend fun onAlertTriggered(uid: String, alert: PriceAlert, outcome: TargetEvaluationOutcome)
    suspend fun onAlertObserved(uid: String, alertId: String, observedPriceInr: Double)
}

class DefaultFirestoreThresholdDataSource(
    private val context: Context,
) : FirestoreThresholdDataSource {

    override suspend fun fetchSnapshot(): FirestoreThresholdSnapshot {
        val uid = FirestoreService.currentUserOrNull(context.applicationContext)?.uid
            ?: return FirestoreThresholdSnapshot(authenticatedUid = null)
        val repo = SonaWaneRepository(context.applicationContext)
        val alerts = try {
            repo.fetchPriceAlertsOnce(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val latestRates = try {
            repo.fetchLatestMarketRatesOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val (alertsEnabled, soundEnabled, vibrationEnabled) = try {
            repo.fetchUserNotificationPrefsOnce(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Triple(true, true, true)
        }
        return FirestoreThresholdSnapshot(
            authenticatedUid = uid,
            alerts = alerts,
            latestMarketRates = latestRates,
            priceAlertsEnabled = alertsEnabled,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
        )
    }

    override suspend fun onAlertTriggered(
        uid: String,
        alert: PriceAlert,
        outcome: TargetEvaluationOutcome,
    ) {
        val repo = SonaWaneRepository(context.applicationContext)
        try {
            repo.markPriceAlertTriggered(uid, alert, outcome.observedPriceInr)
            repo.sendNotification(
                uid = uid,
                type = "PRICE_ALERT",
                title = outcome.notificationTitle,
                body = outcome.notificationBody,
                referenceId = alert.id,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort Firestore write-back; local state still prevents duplicate push bursts
        }
    }

    override suspend fun onAlertObserved(
        uid: String,
        alertId: String,
        observedPriceInr: Double,
    ) {
        val repo = SonaWaneRepository(context.applicationContext)
        try {
            repo.updateAlertObservedPrice(uid, alertId, observedPriceInr)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

data class TargetEvaluationOutcome(
    val target: PriceAlertTarget,
    val observedPriceInr: Double,
    val thresholdReached: Boolean,
    val shouldNotify: Boolean,
    val status: String,
    val notificationTitle: String = "",
    val notificationBody: String = "",
)

data class PriceAlertBatchResult(
    val evaluatedCount: Int,
    val triggeredCount: Int,
    val notificationsDispatched: Int,
    val shouldRetry: Boolean,
    val outcomes: List<TargetEvaluationOutcome>,
    val usedFirestoreSource: Boolean = false,
    val observedGold24kInr: Double = 0.0,
    val observedSilver999Inr: Double = 0.0,
)

/**
 * Manages user-configured Gold (24K/22K/18K) and Silver (999) target price alerts,
 * synchronizes Firestore `users/{uid}/price_alerts` and `market_price_history`,
 * schedules background WorkManager checks, and fires Android local push notifications
 * when targets are reached.
 */
class PriceAlertManager(
    private val context: Context,
    private val prefs: SecurityPreferences = SecurityPreferences(context.applicationContext),
    private val marketService: MarketRateService = MarketRateService(),
    private val firestoreSource: FirestoreThresholdDataSource = DefaultFirestoreThresholdDataSource(context.applicationContext),
) {

    val targetsFlow: Flow<List<PriceAlertTarget>> = prefs.priceAlertTargetsFlow

    suspend fun setTargetPriceAlert(
        metal: String,
        targetPriceInr: Double,
        direction: String = "BELOW",
        frequency: String = "ONCE",
        notes: String = "",
        enabled: Boolean = true,
        id: String = "pa_${UUID.randomUUID().toString().take(12)}",
    ): PriceAlertTarget {
        require(targetPriceInr > 0.0) { "Target price must be greater than zero" }
        val normalizedMetal = normalizeMetal(metal)
        val normalizedDir = normalizeDirection(direction)
        val normalizedFreq = normalizeFrequency(frequency)
        val target = PriceAlertTarget(
            id = id,
            metal = normalizedMetal,
            targetPriceInr = targetPriceInr,
            direction = normalizedDir,
            frequency = normalizedFreq,
            enabled = enabled,
            notes = notes.trim(),
        )
        val updated = prefs.upsertPriceAlertTarget(target)
        reconcileWorkSchedule(updated)
        return target
    }

    suspend fun updateTargetPriceAlert(
        id: String,
        targetPriceInr: Double,
        direction: String,
        frequency: String,
        enabled: Boolean,
        notes: String,
    ) {
        val current = prefs.priceAlertTargetsFlow.first().firstOrNull { it.id == id } ?: return
        val updated = prefs.upsertPriceAlertTarget(
            current.copy(
                targetPriceInr = targetPriceInr,
                direction = normalizeDirection(direction),
                frequency = normalizeFrequency(frequency),
                enabled = enabled,
                notes = notes.trim(),
            )
        )
        reconcileWorkSchedule(updated)
    }

    suspend fun toggleTargetPriceAlert(id: String, enabled: Boolean) {
        val current = prefs.priceAlertTargetsFlow.first().firstOrNull { it.id == id } ?: return
        val updated = prefs.upsertPriceAlertTarget(current.copy(enabled = enabled))
        reconcileWorkSchedule(updated)
    }

    suspend fun removeTargetPriceAlert(id: String) {
        val updated = prefs.removePriceAlertTarget(id)
        reconcileWorkSchedule(updated)
    }

    /**
     * Mirrors active Firestore [PriceAlert] records into local DataStore targets so WorkManager
     * can continue monitoring both Gold and Silver targets even while the app is backgrounded.
     */
    suspend fun syncFromFirestoreAlerts(alerts: List<PriceAlert>) {
        val mapped = alerts.map { mapFirestoreAlertToTarget(it) }
        prefs.savePriceAlertTargets(mapped)
        reconcileWorkSchedule(mapped)
    }

    suspend fun reconcileWorkSchedule(targets: List<PriceAlertTarget>) {
        val cfg = runCatching { prefs.priceAlertWorkerConfigFlow.first() }.getOrNull()
        val masterEnabled = cfg?.masterAlertsEnabled ?: true
        val interval = cfg?.intervalMinutes ?: 15L
        val preferFirestore = cfg?.preferFirestoreRates ?: true
        if (masterEnabled && targets.any { it.enabled }) {
            schedulePeriodicMonitoring(intervalMinutes = interval, preferFirestoreRates = preferFirestore)
        } else {
            cancelMonitoring()
        }
    }

    fun schedulePeriodicMonitoring(
        intervalMinutes: Long = 15L,
        preferFirestoreRates: Boolean = true,
    ) {
        val safeMinutes = intervalMinutes.coerceAtLeast(15L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<PriceAlertCheckWorker>(
            repeatInterval = safeMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setInputData(workDataOf(PriceAlertCheckWorker.KEY_PREFER_FIRESTORE_RATES to preferFirestoreRates))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }
    }

    fun runImmediateCheck(preferFirestoreRates: Boolean = true) {
        val request = OneTimeWorkRequestBuilder<PriceAlertCheckWorker>()
            .setInputData(
                workDataOf(
                    PriceAlertCheckWorker.KEY_MANUAL_CHECK to true,
                    PriceAlertCheckWorker.KEY_PREFER_FIRESTORE_RATES to preferFirestoreRates,
                )
            )
            .build()

        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }

    fun cancelMonitoring() {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
        }
    }

    suspend fun checkAllTargetsAndNotify(
        ratesOverride: LiveMarketRates? = null,
        forceNotify: Boolean = false,
        playSound: Boolean = true,
        vibrate: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
        preferFirestoreRates: Boolean = true,
        syncAlertsFromFirestore: Boolean = true,
    ): PriceAlertBatchResult {
        val fsSnapshot = if (syncAlertsFromFirestore) {
            try {
                firestoreSource.fetchSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val fsUid = fsSnapshot?.authenticatedUid
        val fsAlertById = fsSnapshot?.alerts?.associateBy { it.id }.orEmpty()
        val effectivePlaySound = playSound && (fsSnapshot?.soundEnabled ?: true)
        val effectiveVibrate = vibrate && (fsSnapshot?.vibrationEnabled ?: true)

        if (fsSnapshot != null && fsUid != null && !fsSnapshot.priceAlertsEnabled && !forceNotify) {
            return PriceAlertBatchResult(
                evaluatedCount = 0,
                triggeredCount = 0,
                notificationsDispatched = 0,
                shouldRetry = false,
                outcomes = emptyList(),
                usedFirestoreSource = fsSnapshot.latestMarketRates != null || fsSnapshot.alerts.isNotEmpty(),
            )
        }

        val targets: List<PriceAlertTarget> = when {
            fsSnapshot != null && fsUid != null && fsSnapshot.alerts.isNotEmpty() -> {
                val mapped = fsSnapshot.alerts.map { mapFirestoreAlertToTarget(it) }
                prefs.savePriceAlertTargets(mapped)
                mapped
            }
            else -> prefs.priceAlertTargetsFlow.first()
        }

        val activeTargets = targets.filter { it.enabled }
        if (activeTargets.isEmpty()) {
            return PriceAlertBatchResult(
                evaluatedCount = 0,
                triggeredCount = 0,
                notificationsDispatched = 0,
                shouldRetry = false,
                outcomes = emptyList(),
                usedFirestoreSource = fsSnapshot?.latestMarketRates != null || fsSnapshot?.alerts?.isNotEmpty() == true,
            )
        }

        val fsRates = fsSnapshot?.latestMarketRates
        val resolvedRates: LiveMarketRates
        var usedFirestoreRates = false

        when {
            ratesOverride != null -> {
                resolvedRates = ratesOverride
            }
            preferFirestoreRates && fsRates != null &&
                (fsRates.gold24kBuyPerGram > 0.0 || fsRates.silver999BuyPerGram > 0.0) -> {
                usedFirestoreRates = true
                // Merge any missing metal rate from MarketRateService if Firestore entry lacks it
                resolvedRates = if (fsRates.gold24kBuyPerGram > 0.0 && fsRates.silver999BuyPerGram > 0.0) {
                    fsRates
                } else {
                    val live = runCatching { marketService.fetchLiveRates() }.getOrElse { LiveMarketRates() }
                    fsRates.copy(
                        gold24kBuyPerGram = fsRates.gold24kBuyPerGram.takeIf { it > 0.0 } ?: live.gold24kBuyPerGram,
                        gold22kBuyPerGram = fsRates.gold22kBuyPerGram.takeIf { it > 0.0 } ?: live.gold22kBuyPerGram,
                        gold18kBuyPerGram = fsRates.gold18kBuyPerGram.takeIf { it > 0.0 } ?: live.gold18kBuyPerGram,
                        silver999BuyPerGram = fsRates.silver999BuyPerGram.takeIf { it > 0.0 } ?: live.silver999BuyPerGram,
                    )
                }
            }
            else -> {
                val live = runCatching { marketService.fetchLiveRates() }.getOrElse { LiveMarketRates() }
                if (live.gold24kBuyPerGram > 0.0 || live.silver999BuyPerGram > 0.0) {
                    resolvedRates = live
                } else if (fsRates != null && (fsRates.gold24kBuyPerGram > 0.0 || fsRates.silver999BuyPerGram > 0.0)) {
                    usedFirestoreRates = true
                    resolvedRates = fsRates
                } else {
                    resolvedRates = LiveMarketRates()
                }
            }
        }

        if (resolvedRates.gold24kBuyPerGram <= 0.0 && resolvedRates.silver999BuyPerGram <= 0.0) {
            return PriceAlertBatchResult(
                evaluatedCount = 0,
                triggeredCount = 0,
                notificationsDispatched = 0,
                shouldRetry = true,
                outcomes = emptyList(),
                usedFirestoreSource = fsUid != null,
            )
        }

        if (resolvedRates.isLive && resolvedRates.gold24kBuyPerGram > 0.0) {
            prefs.cacheRates(
                gold24k = resolvedRates.gold24kBuyPerGram,
                silver999 = resolvedRates.silver999BuyPerGram,
                source = resolvedRates.sourceName,
                timestampMs = resolvedRates.lastUpdatedEpochMs,
            )
        }

        val outcomes = mutableListOf<TargetEvaluationOutcome>()
        val updatedTargets = targets.toMutableList()
        var triggeredCount = 0
        var dispatchedCount = 0

        for (index in updatedTargets.indices) {
            val target = updatedTargets[index]
            if (!target.enabled) continue

            val outcome = evaluateTarget(
                target = target,
                rates = resolvedRates,
                nowMs = nowMs,
                forceNotify = forceNotify,
            )
            outcomes.add(outcome)

            if (outcome.thresholdReached) {
                triggeredCount++
            }

            val fsAlert = fsAlertById[target.id]
            if (outcome.shouldNotify) {
                val hasPerm = BullionNotificationHelper.hasNotificationPermission(context)
                if (hasPerm) {
                    BullionNotificationHelper.postPriceAlertPush(
                        context = context,
                        title = outcome.notificationTitle,
                        body = outcome.notificationBody,
                        playSound = effectivePlaySound,
                        vibrate = effectiveVibrate,
                        notificationId = target.id.hashCode(),
                    )
                    dispatchedCount++
                }
                if (fsUid != null && fsAlert != null) {
                    firestoreSource.onAlertTriggered(fsUid, fsAlert, outcome)
                }
                updatedTargets[index] = target.copy(
                    lastObservedPrice = outcome.observedPriceInr,
                    lastNotifiedEpochMs = nowMs,
                    triggerCount = target.triggerCount + 1,
                    enabled = target.frequency != "ONCE",
                )
            } else if (outcome.observedPriceInr > 0.0) {
                if (fsUid != null && fsAlert != null &&
                    abs(outcome.observedPriceInr - fsAlert.lastObservedPrice) >= 0.5
                ) {
                    firestoreSource.onAlertObserved(fsUid, fsAlert.id, outcome.observedPriceInr)
                }
                updatedTargets[index] = target.copy(lastObservedPrice = outcome.observedPriceInr)
            }
        }

        prefs.savePriceAlertTargets(updatedTargets)
        reconcileWorkSchedule(updatedTargets)

        return PriceAlertBatchResult(
            evaluatedCount = outcomes.size,
            triggeredCount = triggeredCount,
            notificationsDispatched = dispatchedCount,
            shouldRetry = false,
            outcomes = outcomes,
            usedFirestoreSource = usedFirestoreRates || fsUid != null,
            observedGold24kInr = resolvedRates.gold24kBuyPerGram,
            observedSilver999Inr = resolvedRates.silver999BuyPerGram,
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "sonawane_price_alert_manager_periodic"
        const val UNIQUE_IMMEDIATE_WORK_NAME = "sonawane_price_alert_manager_immediate"
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 86_400_000L
        private const val ALWAYS_COOLDOWN_MS = 300_000L

        fun mapFirestoreAlertToTarget(a: PriceAlert): PriceAlertTarget = PriceAlertTarget(
            id = a.id.ifBlank { "${a.metal}_${a.targetPriceInr}" },
            metal = normalizeMetal(a.metal),
            targetPriceInr = a.targetPriceInr,
            direction = normalizeDirection(a.direction),
            frequency = normalizeFrequency(a.frequency),
            enabled = a.isActive,
            notes = a.notes,
            lastObservedPrice = a.lastObservedPrice,
            lastNotifiedEpochMs = a.triggeredAt?.toDate()?.time ?: 0L,
            triggerCount = a.triggerCount,
        )

        fun normalizeMetal(metal: String): String = when (metal.trim().uppercase()) {
            "SILVER", "SILVER_999", "999_SILVER", "AG" -> "SILVER_999"
            "GOLD_22K", "22K" -> "GOLD_22K"
            "GOLD_18K", "18K" -> "GOLD_18K"
            else -> "GOLD_24K"
        }

        fun normalizeDirection(direction: String): String = when (direction.trim().uppercase()) {
            "ABOVE", "OVER", "HIGH" -> "ABOVE"
            "CROSSES", "CROSS", "EXACT" -> "CROSSES"
            else -> "BELOW"
        }

        fun normalizeFrequency(frequency: String): String = when (frequency.trim().uppercase()) {
            "EVERY_HOUR", "HOURLY" -> "EVERY_HOUR"
            "DAILY" -> "DAILY"
            "ALWAYS" -> "ALWAYS"
            else -> "ONCE"
        }

        fun metalDisplayName(metal: String): String = when (normalizeMetal(metal)) {
            "SILVER_999" -> "999 Silver"
            "GOLD_22K" -> "22K Gold"
            "GOLD_18K" -> "18K Gold"
            else -> "24K Gold"
        }

        fun resolveMetalPrice(rates: LiveMarketRates, metal: String): Double = when (normalizeMetal(metal)) {
            "SILVER_999" -> rates.silver999BuyPerGram
            "GOLD_22K" -> if (rates.gold22kBuyPerGram > 0.0) rates.gold22kBuyPerGram else rates.gold24kBuyPerGram * (22.0 / 24.0)
            "GOLD_18K" -> if (rates.gold18kBuyPerGram > 0.0) rates.gold18kBuyPerGram else rates.gold24kBuyPerGram * (18.0 / 24.0)
            else -> rates.gold24kBuyPerGram
        }

        fun isThresholdReached(
            currentPrice: Double,
            targetPrice: Double,
            direction: String,
            previousObserved: Double = 0.0,
        ): Boolean {
            if (currentPrice <= 0.0 || targetPrice <= 0.0) return false
            return when (normalizeDirection(direction)) {
                "ABOVE" -> currentPrice >= targetPrice
                "BELOW" -> currentPrice <= targetPrice
                "CROSSES" -> {
                    val prev = previousObserved.takeIf { it > 0.0 } ?: currentPrice
                    val crossedUp = prev < targetPrice && currentPrice >= targetPrice
                    val crossedDown = prev > targetPrice && currentPrice <= targetPrice
                    val nearTarget = abs(currentPrice - targetPrice) <= (targetPrice * 0.0008)
                    crossedUp || crossedDown || nearTarget
                }
                else -> false
            }
        }

        fun isCooldownReady(
            frequency: String,
            triggerCount: Int,
            lastNotifiedEpochMs: Long,
            nowMs: Long,
        ): Boolean = when (normalizeFrequency(frequency)) {
            "ONCE" -> triggerCount == 0
            "EVERY_HOUR" -> lastNotifiedEpochMs <= 0L || (nowMs - lastNotifiedEpochMs) >= HOUR_MS
            "DAILY" -> lastNotifiedEpochMs <= 0L || (nowMs - lastNotifiedEpochMs) >= DAY_MS
            else -> lastNotifiedEpochMs <= 0L || (nowMs - lastNotifiedEpochMs) >= ALWAYS_COOLDOWN_MS
        }

        fun evaluateTarget(
            target: PriceAlertTarget,
            rates: LiveMarketRates,
            nowMs: Long = System.currentTimeMillis(),
            forceNotify: Boolean = false,
        ): TargetEvaluationOutcome {
            if (!target.enabled) {
                return TargetEvaluationOutcome(
                    target = target,
                    observedPriceInr = target.lastObservedPrice,
                    thresholdReached = false,
                    shouldNotify = false,
                    status = "PAUSED",
                )
            }

            val spot = resolveMetalPrice(rates, target.metal)
            if (spot <= 0.0) {
                return TargetEvaluationOutcome(
                    target = target,
                    observedPriceInr = 0.0,
                    thresholdReached = false,
                    shouldNotify = false,
                    status = "NO_RATE",
                )
            }

            val reached = isThresholdReached(
                currentPrice = spot,
                targetPrice = target.targetPriceInr,
                direction = target.direction,
                previousObserved = target.lastObservedPrice,
            )
            if (!reached) {
                return TargetEvaluationOutcome(
                    target = target,
                    observedPriceInr = spot,
                    thresholdReached = false,
                    shouldNotify = false,
                    status = "MONITORING",
                )
            }

            val ready = forceNotify || isCooldownReady(
                frequency = target.frequency,
                triggerCount = target.triggerCount,
                lastNotifiedEpochMs = target.lastNotifiedEpochMs,
                nowMs = nowMs,
            )
            if (!ready) {
                return TargetEvaluationOutcome(
                    target = target,
                    observedPriceInr = spot,
                    thresholdReached = true,
                    shouldNotify = false,
                    status = "COOLDOWN",
                )
            }

            val label = metalDisplayName(target.metal)
            val verb = when (normalizeDirection(target.direction)) {
                "ABOVE" -> "rose above"
                "BELOW" -> "dropped below"
                else -> "crossed"
            }
            val notePart = if (target.notes.isNotBlank()) " (${target.notes})" else ""
            val title = "$label Target Reached!"
            val body = "$label $verb ${Formatters.formatInr(target.targetPriceInr)}/g — Live price: ${Formatters.formatInr(spot)}/g$notePart."

            return TargetEvaluationOutcome(
                target = target,
                observedPriceInr = spot,
                thresholdReached = true,
                shouldNotify = true,
                status = "TRIGGERED",
                notificationTitle = title,
                notificationBody = body,
            )
        }
    }
}
