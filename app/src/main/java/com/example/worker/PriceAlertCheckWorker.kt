package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.service.PriceAlertManager
import kotlinx.coroutines.CancellationException

/**
 * WorkManager background worker managed by [PriceAlertManager] that checks Firestore
 * (`users/{uid}/price_alerts` and `market_price_history`) plus live bullion feeds for
 * Gold and Silver price thresholds and dispatches push notifications when reached.
 */
class PriceAlertCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(KEY_MANUAL_CHECK, false)
        val preferFirestore = inputData.getBoolean(KEY_PREFER_FIRESTORE_RATES, true)

        return try {
            val manager = testManagerFactory?.invoke(applicationContext)
                ?: PriceAlertManager(applicationContext)
            val batch = manager.checkAllTargetsAndNotify(
                forceNotify = manual,
                preferFirestoreRates = preferFirestore,
                syncAlertsFromFirestore = true,
            )

            if (batch.shouldRetry) {
                Result.retry()
            } else {
                Result.success(
                    workDataOf(
                        KEY_OUT_EVALUATED_COUNT to batch.evaluatedCount,
                        KEY_OUT_TRIGGERED_COUNT to batch.triggeredCount,
                        KEY_OUT_NOTIFICATIONS_DISPATCHED to batch.notificationsDispatched,
                        KEY_OUT_FIRESTORE_SOURCE to batch.usedFirestoreSource,
                        KEY_OUT_GOLD_24K_INR to batch.observedGold24kInr,
                        KEY_OUT_SILVER_999_INR to batch.observedSilver999Inr,
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_MANUAL_CHECK = "manual_price_alert_check"
        const val KEY_PREFER_FIRESTORE_RATES = "prefer_firestore_rates"

        const val KEY_OUT_EVALUATED_COUNT = "evaluated_count"
        const val KEY_OUT_TRIGGERED_COUNT = "triggered_count"
        const val KEY_OUT_NOTIFICATIONS_DISPATCHED = "notifications_dispatched"
        const val KEY_OUT_FIRESTORE_SOURCE = "firestore_source"
        const val KEY_OUT_GOLD_24K_INR = "gold_24k_inr"
        const val KEY_OUT_SILVER_999_INR = "silver_999_inr"

        @Volatile
        internal var testManagerFactory: ((Context) -> PriceAlertManager)? = null
    }
}
