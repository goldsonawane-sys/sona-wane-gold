package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.service.GoldPriceMonitorService
import java.util.concurrent.TimeUnit

class GoldPriceMonitorWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val isManual = inputData.getBoolean(KEY_MANUAL_TRIGGER, false)
        val service = GoldPriceMonitorService(applicationContext)
        val result = service.performBackgroundCheck(isManual = isManual)

        if (result.shouldRetry) {
            return Result.retry()
        }

        return Result.success(
            workDataOf(
                "observed_gold_inr" to result.outcome.liveRateInr,
                "threshold_gold_inr" to result.outcome.thresholdInr,
                "purity" to result.outcome.purity,
                "triggered" to result.outcome.droppedBelowThreshold,
                "push_sent" to result.pushDispatched,
                "in_app_saved" to result.inAppAlertSaved,
            )
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "sonawane_gold_drop_monitor"
        const val UNIQUE_IMMEDIATE_NAME = "sonawane_gold_drop_immediate"
        private const val KEY_MANUAL_TRIGGER = "manual_trigger"

        fun schedulePeriodicMonitor(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<GoldPriceMonitorWorker>(
                repeatInterval = 15L,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5L, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
        }

        fun cancelPeriodicMonitor(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_PERIODIC_NAME)
        }

        fun runImmediateCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<GoldPriceMonitorWorker>()
                .setInputData(workDataOf(KEY_MANUAL_TRIGGER to true))
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_IMMEDIATE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}
