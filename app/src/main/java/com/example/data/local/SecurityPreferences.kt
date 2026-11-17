package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "sonawane_prefs")

data class PriceAlertTarget(
    val id: String,
    val metal: String = "GOLD_24K",
    val targetPriceInr: Double = 0.0,
    val direction: String = "BELOW",
    val frequency: String = "ONCE",
    val enabled: Boolean = true,
    val notes: String = "",
    val lastObservedPrice: Double = 0.0,
    val lastNotifiedEpochMs: Long = 0L,
    val triggerCount: Int = 0,
)

data class GoldDropInAppAlert(
    val purity: String = "GOLD_24K",
    val observedRateInr: Double = 0.0,
    val thresholdInr: Double = 0.0,
    val triggeredAtMs: Long = 0L,
    val message: String = "",
)

data class BgDropMonitorState(
    val enabled: Boolean = false,
    val purity: String = "GOLD_24K",
    val threshold24kInr: Double = 0.0,
    val lastCheckedEpochMs: Long = 0L,
    val lastObservedRateInr: Double = 0.0,
    val lastNotifiedEpochMs: Long = 0L,
    val lastStatus: String = "IDLE",
    val pendingInAppAlert: GoldDropInAppAlert? = null,
)

data class PriceAlertWorkerConfig(
    val intervalMinutes: Long = 15L,
    val preferFirestoreRates: Boolean = true,
    val masterAlertsEnabled: Boolean = true,
)

class SecurityPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LAST_ACTIVE_EPOCH = longPreferencesKey("last_active_epoch")
        val CACHED_GOLD_24K = stringPreferencesKey("cached_gold_24k")
        val CACHED_SILVER_999 = stringPreferencesKey("cached_silver_999")
        val CACHED_RATE_SOURCE = stringPreferencesKey("cached_rate_source")
        val CACHED_RATE_TIME = longPreferencesKey("cached_rate_time")
        val BIOMETRIC_VAULT_UID = stringPreferencesKey("biometric_vault_uid")
        val MPIN_FAILED_ATTEMPTS = intPreferencesKey("mpin_failed_attempts")
        val MPIN_LOCKOUT_UNTIL_MS = longPreferencesKey("mpin_lockout_until_ms")
        val ADMIN_PORTAL_MODE = booleanPreferencesKey("admin_portal_mode")
        val ADMIN_FAILED_ATTEMPTS = intPreferencesKey("admin_failed_attempts")
        val ADMIN_LOCKOUT_UNTIL_MS = longPreferencesKey("admin_lockout_until_ms")
        val WALLET_BIOMETRIC_LOCK = booleanPreferencesKey("wallet_biometric_lock")
        val TRADE_BIOMETRIC_CONFIRM = booleanPreferencesKey("trade_biometric_confirm")
        val BIOMETRIC_APP_ACCESS = booleanPreferencesKey("biometric_app_access")
        val BG_DROP_MONITOR_ENABLED = booleanPreferencesKey("bg_drop_monitor_enabled")
        val BG_DROP_PURITY = stringPreferencesKey("bg_drop_purity")
        val BG_DROP_THRESHOLD_24K = stringPreferencesKey("bg_drop_threshold_24k")
        val BG_DROP_LAST_CHECK_MS = longPreferencesKey("bg_drop_last_check_ms")
        val BG_DROP_LAST_RATE_INR = stringPreferencesKey("bg_drop_last_rate_inr")
        val BG_DROP_LAST_NOTIFIED_MS = longPreferencesKey("bg_drop_last_notified_ms")
        val BG_DROP_LAST_STATUS = stringPreferencesKey("bg_drop_last_status")
        val BG_DROP_IN_APP_PENDING = booleanPreferencesKey("bg_drop_in_app_pending")
        val BG_DROP_IN_APP_RATE = stringPreferencesKey("bg_drop_in_app_rate")
        val BG_DROP_IN_APP_THRESHOLD = stringPreferencesKey("bg_drop_in_app_threshold")
        val BG_DROP_IN_APP_PURITY = stringPreferencesKey("bg_drop_in_app_purity")
        val BG_DROP_IN_APP_TIME_MS = longPreferencesKey("bg_drop_in_app_time_ms")
        val BG_DROP_IN_APP_MESSAGE = stringPreferencesKey("bg_drop_in_app_message")
        val PRICE_ALERT_TARGETS_JSON = stringPreferencesKey("price_alert_targets_json")
        val WORKER_INTERVAL_MINUTES = longPreferencesKey("worker_interval_minutes")
        val WORKER_PREFER_FIRESTORE = booleanPreferencesKey("worker_prefer_firestore")
        val WORKER_MASTER_ENABLED = booleanPreferencesKey("worker_master_enabled")
    }

    val onboardingSeen: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDING_SEEN] ?: false }
    val deviceIdFlow: Flow<String?> = context.dataStore.data.map { it[Keys.DEVICE_ID] }
    val lastActiveEpochFlow: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_ACTIVE_EPOCH] ?: 0L }
    val biometricVaultUidFlow: Flow<String?> = context.dataStore.data.map { it[Keys.BIOMETRIC_VAULT_UID] }
    val mpinRateLimitFlow: Flow<Pair<Int, Long>> = context.dataStore.data.map { prefs ->
        (prefs[Keys.MPIN_FAILED_ATTEMPTS] ?: 0) to (prefs[Keys.MPIN_LOCKOUT_UNTIL_MS] ?: 0L)
    }
    val adminPortalModeFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.ADMIN_PORTAL_MODE] ?: false }
    val adminRateLimitFlow: Flow<Pair<Int, Long>> = context.dataStore.data.map { prefs ->
        (prefs[Keys.ADMIN_FAILED_ATTEMPTS] ?: 0) to (prefs[Keys.ADMIN_LOCKOUT_UNTIL_MS] ?: 0L)
    }
    val walletBiometricLockFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.WALLET_BIOMETRIC_LOCK] ?: true }
    val tradeBiometricConfirmFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.TRADE_BIOMETRIC_CONFIRM] ?: true }
    val biometricAppAccessFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_APP_ACCESS] ?: false }
    val bgDropMonitorFlow: Flow<BgDropMonitorState> = context.dataStore.data.map { prefs ->
        val pending = prefs[Keys.BG_DROP_IN_APP_PENDING] == true
        val inAppAlert = if (pending) {
            GoldDropInAppAlert(
                purity = prefs[Keys.BG_DROP_IN_APP_PURITY] ?: "GOLD_24K",
                observedRateInr = prefs[Keys.BG_DROP_IN_APP_RATE]?.toDoubleOrNull() ?: 0.0,
                thresholdInr = prefs[Keys.BG_DROP_IN_APP_THRESHOLD]?.toDoubleOrNull() ?: 0.0,
                triggeredAtMs = prefs[Keys.BG_DROP_IN_APP_TIME_MS] ?: 0L,
                message = prefs[Keys.BG_DROP_IN_APP_MESSAGE] ?: "",
            )
        } else {
            null
        }
        BgDropMonitorState(
            enabled = prefs[Keys.BG_DROP_MONITOR_ENABLED] ?: false,
            purity = prefs[Keys.BG_DROP_PURITY] ?: "GOLD_24K",
            threshold24kInr = prefs[Keys.BG_DROP_THRESHOLD_24K]?.toDoubleOrNull() ?: 0.0,
            lastCheckedEpochMs = prefs[Keys.BG_DROP_LAST_CHECK_MS] ?: 0L,
            lastObservedRateInr = prefs[Keys.BG_DROP_LAST_RATE_INR]?.toDoubleOrNull() ?: 0.0,
            lastNotifiedEpochMs = prefs[Keys.BG_DROP_LAST_NOTIFIED_MS] ?: 0L,
            lastStatus = prefs[Keys.BG_DROP_LAST_STATUS] ?: "IDLE",
            pendingInAppAlert = inAppAlert,
        )
    }

    val priceAlertTargetsFlow: Flow<List<PriceAlertTarget>> = context.dataStore.data.map { prefs ->
        decodePriceAlertTargets(prefs[Keys.PRICE_ALERT_TARGETS_JSON].orEmpty())
    }

    val priceAlertWorkerConfigFlow: Flow<PriceAlertWorkerConfig> = context.dataStore.data.map { prefs ->
        PriceAlertWorkerConfig(
            intervalMinutes = (prefs[Keys.WORKER_INTERVAL_MINUTES] ?: 15L).coerceAtLeast(15L),
            preferFirestoreRates = prefs[Keys.WORKER_PREFER_FIRESTORE] ?: true,
            masterAlertsEnabled = prefs[Keys.WORKER_MASTER_ENABLED] ?: true,
        )
    }

    suspend fun setOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_SEEN] = seen }
    }

    suspend fun setAdminPortalMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ADMIN_PORTAL_MODE] = enabled }
    }

    suspend fun setWalletBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WALLET_BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setTradeBiometricConfirm(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TRADE_BIOMETRIC_CONFIRM] = enabled }
    }

    suspend fun setBiometricAppAccess(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_APP_ACCESS] = enabled }
    }

    suspend fun setBiometricAuthenticationSettings(appAccessEnabled: Boolean, txConfirmEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_APP_ACCESS] = appAccessEnabled
            prefs[Keys.TRADE_BIOMETRIC_CONFIRM] = txConfirmEnabled
        }
    }

    suspend fun setBgDropMonitorConfig(
        enabled: Boolean,
        threshold24kInr: Double,
        purity: String = "GOLD_24K",
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BG_DROP_MONITOR_ENABLED] = enabled
            prefs[Keys.BG_DROP_PURITY] = purity
            prefs[Keys.BG_DROP_THRESHOLD_24K] = threshold24kInr.toString()
            prefs[Keys.BG_DROP_LAST_STATUS] = if (enabled) "ARMED" else "PAUSED"
            // Reset cooldown when user changes threshold or re-enables monitoring in Settings
            prefs[Keys.BG_DROP_LAST_NOTIFIED_MS] = 0L
            if (!enabled) {
                prefs[Keys.BG_DROP_IN_APP_PENDING] = false
            }
        }
    }

    suspend fun recordBgDropCheck(
        observedRateInr: Double,
        status: String,
        checkedAtMs: Long,
        notifiedAtMs: Long? = null,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BG_DROP_LAST_CHECK_MS] = checkedAtMs
            prefs[Keys.BG_DROP_LAST_RATE_INR] = observedRateInr.toString()
            prefs[Keys.BG_DROP_LAST_STATUS] = status
            if (notifiedAtMs != null) {
                prefs[Keys.BG_DROP_LAST_NOTIFIED_MS] = notifiedAtMs
            }
        }
    }

    suspend fun recordInAppDropAlert(alert: GoldDropInAppAlert) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BG_DROP_IN_APP_PENDING] = true
            prefs[Keys.BG_DROP_IN_APP_PURITY] = alert.purity
            prefs[Keys.BG_DROP_IN_APP_RATE] = alert.observedRateInr.toString()
            prefs[Keys.BG_DROP_IN_APP_THRESHOLD] = alert.thresholdInr.toString()
            prefs[Keys.BG_DROP_IN_APP_TIME_MS] = alert.triggeredAtMs
            prefs[Keys.BG_DROP_IN_APP_MESSAGE] = alert.message
        }
    }

    suspend fun dismissPendingDropAlert() {
        context.dataStore.edit { prefs ->
            prefs[Keys.BG_DROP_IN_APP_PENDING] = false
        }
    }

    suspend fun savePriceAlertTargets(targets: List<PriceAlertTarget>) {
        val encoded = encodePriceAlertTargets(targets)
        context.dataStore.edit { prefs ->
            prefs[Keys.PRICE_ALERT_TARGETS_JSON] = encoded
        }
    }

    suspend fun upsertPriceAlertTarget(target: PriceAlertTarget): List<PriceAlertTarget> {
        var updatedList: List<PriceAlertTarget> = emptyList()
        context.dataStore.edit { prefs ->
            val current = decodePriceAlertTargets(prefs[Keys.PRICE_ALERT_TARGETS_JSON].orEmpty()).toMutableList()
            val idx = current.indexOfFirst { it.id == target.id }
            if (idx >= 0) {
                current[idx] = target
            } else {
                current.add(0, target)
            }
            updatedList = current
            prefs[Keys.PRICE_ALERT_TARGETS_JSON] = encodePriceAlertTargets(current)
        }
        return updatedList
    }

    suspend fun removePriceAlertTarget(targetId: String): List<PriceAlertTarget> {
        var updatedList: List<PriceAlertTarget> = emptyList()
        context.dataStore.edit { prefs ->
            val filtered = decodePriceAlertTargets(prefs[Keys.PRICE_ALERT_TARGETS_JSON].orEmpty())
                .filterNot { it.id == targetId }
            updatedList = filtered
            prefs[Keys.PRICE_ALERT_TARGETS_JSON] = encodePriceAlertTargets(filtered)
        }
        return updatedList
    }

    suspend fun setPriceAlertWorkerConfig(
        intervalMinutes: Long,
        preferFirestoreRates: Boolean,
        masterAlertsEnabled: Boolean,
    ) {
        val safeInterval = intervalMinutes.coerceAtLeast(15L)
        context.dataStore.edit { prefs ->
            prefs[Keys.WORKER_INTERVAL_MINUTES] = safeInterval
            prefs[Keys.WORKER_PREFER_FIRESTORE] = preferFirestoreRates
            prefs[Keys.WORKER_MASTER_ENABLED] = masterAlertsEnabled
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        var id: String? = null
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.DEVICE_ID]
            if (existing.isNullOrBlank()) {
                val created = "dev_${java.util.UUID.randomUUID().toString().take(12)}"
                prefs[Keys.DEVICE_ID] = created
                id = created
            } else {
                id = existing
            }
        }
        return id ?: "dev_default"
    }

    suspend fun updateLastActiveEpoch(epochMs: Long) {
        context.dataStore.edit { it[Keys.LAST_ACTIVE_EPOCH] = epochMs }
    }

    suspend fun setBiometricVaultUid(uid: String?) {
        context.dataStore.edit { prefs ->
            if (uid.isNullOrBlank()) {
                prefs.remove(Keys.BIOMETRIC_VAULT_UID)
            } else {
                prefs[Keys.BIOMETRIC_VAULT_UID] = uid
            }
        }
    }

    suspend fun recordMpinFailure(): Pair<Int, Long> {
        var attempts = 0
        var lockoutUntil = 0L
        context.dataStore.edit { prefs ->
            attempts = (prefs[Keys.MPIN_FAILED_ATTEMPTS] ?: 0) + 1
            if (attempts >= 5) {
                lockoutUntil = System.currentTimeMillis() + 60_000L
                prefs[Keys.MPIN_LOCKOUT_UNTIL_MS] = lockoutUntil
            }
            prefs[Keys.MPIN_FAILED_ATTEMPTS] = attempts
        }
        return attempts to lockoutUntil
    }

    suspend fun resetMpinFailures() {
        context.dataStore.edit { prefs ->
            prefs[Keys.MPIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.MPIN_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun recordAdminFailure(): Pair<Int, Long> {
        var attempts = 0
        var lockoutUntil = 0L
        context.dataStore.edit { prefs ->
            attempts = (prefs[Keys.ADMIN_FAILED_ATTEMPTS] ?: 0) + 1
            if (attempts >= 5) {
                lockoutUntil = System.currentTimeMillis() + 90_000L
                prefs[Keys.ADMIN_LOCKOUT_UNTIL_MS] = lockoutUntil
            }
            prefs[Keys.ADMIN_FAILED_ATTEMPTS] = attempts
        }
        return attempts to lockoutUntil
    }

    suspend fun resetAdminFailures() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ADMIN_FAILED_ATTEMPTS] = 0
            prefs[Keys.ADMIN_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun cacheRates(gold24k: Double, silver999: Double, source: String, timestampMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHED_GOLD_24K] = gold24k.toString()
            prefs[Keys.CACHED_SILVER_999] = silver999.toString()
            prefs[Keys.CACHED_RATE_SOURCE] = source
            prefs[Keys.CACHED_RATE_TIME] = timestampMs
        }
    }

    val cachedRatesFlow: Flow<Triple<Double, Double, Long>> = context.dataStore.data.map { prefs ->
        val g = prefs[Keys.CACHED_GOLD_24K]?.toDoubleOrNull() ?: 0.0
        val s = prefs[Keys.CACHED_SILVER_999]?.toDoubleOrNull() ?: 0.0
        val t = prefs[Keys.CACHED_RATE_TIME] ?: 0L
        Triple(g, s, t)
    }

    private fun encodePriceAlertTargets(targets: List<PriceAlertTarget>): String {
        val arr = JSONArray()
        targets.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("metal", t.metal)
                put("targetPriceInr", t.targetPriceInr)
                put("direction", t.direction)
                put("frequency", t.frequency)
                put("enabled", t.enabled)
                put("notes", t.notes)
                put("lastObservedPrice", t.lastObservedPrice)
                put("lastNotifiedEpochMs", t.lastNotifiedEpochMs)
                put("triggerCount", t.triggerCount)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodePriceAlertTargets(json: String): List<PriceAlertTarget> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PriceAlertTarget(
                    id = id,
                    metal = obj.optString("metal", "GOLD_24K"),
                    targetPriceInr = obj.optDouble("targetPriceInr", 0.0),
                    direction = obj.optString("direction", "BELOW"),
                    frequency = obj.optString("frequency", "ONCE"),
                    enabled = obj.optBoolean("enabled", true),
                    notes = obj.optString("notes", ""),
                    lastObservedPrice = obj.optDouble("lastObservedPrice", 0.0),
                    lastNotifiedEpochMs = obj.optLong("lastNotifiedEpochMs", 0L),
                    triggerCount = obj.optInt("triggerCount", 0),
                )
            }
        }.getOrDefault(emptyList())
    }
}
