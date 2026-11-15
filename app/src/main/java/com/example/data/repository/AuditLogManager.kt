package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.data.model.AdminUser
import com.example.data.model.AppConfig
import com.example.data.model.AuditLog
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Dedicated compliance and security auditing service for SONA-WANE GOLD.
 *
 * Captures every sensitive admin mutation (KYC/user approvals, RBAC role assignments,
 * credential rotations, platform configuration diffs, payout settlements, compliance exports,
 * and authentication/permission-denied security events) into the immutable append-only
 * Firestore `audit_logs` collection.
 */
class AuditLogManager(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    enum class AuditCategory(val label: String) {
        ALL("All Actions"),
        KYC_USERS("KYC & Users"),
        ROLES_AUTH("Roles & Security"),
        CONFIG("Platform Config"),
        FINANCE("Payouts & Compliance"),
    }

    companion object {
        const val COLLECTION_AUDIT_LOGS = "audit_logs"

        private val SENSITIVE_KYC_USER_PREFIXES = listOf("KYC_", "USER_STATUS_")
        private val ROLES_AUTH_PREFIXES = listOf(
            "UPSERT_ADMIN_ROLE",
            "RESET_ADMIN_CREDENTIALS",
            "REVOKE_ADMIN_ACCESS",
            "ADMIN_",
            "PERMISSION_",
        )
        private val CONFIG_PREFIXES = listOf("UPDATE_APP_CONFIG", "PUBLISH_ANNOUNCEMENT")
        private val FINANCE_PREFIXES = listOf("WITHDRAWAL_", "TICKET_", "EXPORT_")

        fun classifyAction(action: String): AuditCategory {
            val normalized = action.trim().uppercase(Locale.US)
            return when {
                SENSITIVE_KYC_USER_PREFIXES.any { normalized.startsWith(it) } -> AuditCategory.KYC_USERS
                ROLES_AUTH_PREFIXES.any { normalized.startsWith(it) } -> AuditCategory.ROLES_AUTH
                CONFIG_PREFIXES.any { normalized.startsWith(it) } -> AuditCategory.CONFIG
                FINANCE_PREFIXES.any { normalized.startsWith(it) } -> AuditCategory.FINANCE
                else -> AuditCategory.ROLES_AUTH
            }
        }

        fun sanitizeField(
            value: String,
            maxLength: Int,
            fallback: String = "",
            minLength: Int = 0,
        ): String {
            val collapsed = value
                .replace(Regex("[\\r\\n\\t]+"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
            val candidate = collapsed.ifBlank { fallback }
            val padded = if (candidate.length < minLength && fallback.isNotBlank()) {
                fallback
            } else {
                candidate
            }
            return padded.take(maxLength)
        }

        fun buildDeviceDescriptor(admin: AdminUser): String {
            val opId = admin.operatorId.ifBlank {
                AdminRepository.defaultOperatorId(admin.uid, admin.email)
            }.take(32)
            val hardware = "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()}".trim()
                .ifBlank { "AndroidDevice" }
            return sanitizeField(
                "$hardware [SDK ${Build.VERSION.SDK_INT}] [$opId]",
                maxLength = 148,
                fallback = "AndroidDevice [$opId]",
            )
        }

        /**
         * Computes a concise, rule-bounded (`<= 500` chars) field-level diff between two
         * [AppConfig] snapshots so audit logs capture exact previous and updated values.
         */
        fun buildConfigDiff(previous: AppConfig?, updated: AppConfig): Pair<String, String> {
            if (previous == null) {
                val initSummary = buildString {
                    append("mode=${updated.paymentMode}")
                    append(", maint=${updated.maintenanceMode}")
                    append(", gst=${updated.gstPercent}%")
                    append(", goldSpread=${updated.goldSpreadPercent}%")
                    append(", silverSpread=${updated.silverSpreadPercent}%")
                    append(", wFee=${updated.withdrawalFeeInr}")
                    append(", tFee=${updated.transferFeePercent}%")
                    append(", ref=${updated.referralEnabled}:${updated.referralRewardInr}")
                }
                return "UNINITIALIZED" to sanitizeField(initSummary, 490, "CONFIG_CREATED")
            }

            val prevChanges = mutableListOf<String>()
            val nextChanges = mutableListOf<String>()

            fun <T> track(key: String, oldVal: T, newVal: T) {
                if (oldVal != newVal) {
                    prevChanges += "$key=$oldVal"
                    nextChanges += "$key=$newVal"
                }
            }

            track("maint", previous.maintenanceMode, updated.maintenanceMode)
            track("mode", previous.paymentMode, updated.paymentMode)
            track("gateway", previous.paymentGatewayName, updated.paymentGatewayName)
            track("provider", previous.goldProviderName, updated.goldProviderName)
            track("gst", previous.gstPercent, updated.gstPercent)
            track("goldSpread", previous.goldSpreadPercent, updated.goldSpreadPercent)
            track("silverSpread", previous.silverSpreadPercent, updated.silverSpreadPercent)
            track("withdrawFee", previous.withdrawalFeeInr, updated.withdrawalFeeInr)
            track("transferFee", previous.transferFeePercent, updated.transferFeePercent)
            track("refEnabled", previous.referralEnabled, updated.referralEnabled)
            track("refReward", previous.referralRewardInr, updated.referralRewardInr)
            track("appName", previous.appName, updated.appName)
            track("tagline", previous.tagline, updated.tagline)
            track("supportEmail", previous.supportEmail, updated.supportEmail)
            track("supportPhone", previous.supportPhone, updated.supportPhone)
            if (previous.termsText != updated.termsText) {
                prevChanges += "terms(len=${previous.termsText.length})"
                nextChanges += "terms(len=${updated.termsText.length})"
            }
            if (previous.privacyText != updated.privacyText) {
                prevChanges += "privacy(len=${previous.privacyText.length})"
                nextChanges += "privacy(len=${updated.privacyText.length})"
            }

            if (prevChanges.isEmpty()) {
                return "NO_FIELD_DELTA (mode=${previous.paymentMode})" to
                    "NO_FIELD_DELTA (mode=${updated.paymentMode})"
            }

            return sanitizeField(prevChanges.joinToString(", "), 490, "PREV_CONFIG") to
                sanitizeField(nextChanges.joinToString(", "), 490, "NEW_CONFIG")
        }

        private fun escapeCsv(value: String): String {
            val clean = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
            return if (clean.contains(',') || clean.contains('"')) {
                "\"${clean.replace("\"", "\"\"")}\""
            } else {
                clean
            }
        }

        /**
         * Formats audit trail entries into an RFC-4180 CSV compliance export.
         */
        fun buildAuditComplianceCsv(logs: List<AuditLog>): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
            val header = "LogId,Timestamp,AdminUid,AdminEmail,AdminRole,Action,AffectedCollection,AffectedRecordId,PreviousValue,NewValue,Reason,DeviceInfo\n"
            val rows = logs.joinToString("\n") { log ->
                val ts = log.createdAt?.toDate()?.let { sdf.format(it) } ?: "PENDING_SERVER_TS"
                listOf(
                    log.id,
                    ts,
                    log.adminUid,
                    log.adminEmail,
                    log.adminRole,
                    log.action,
                    log.affectedCollection,
                    log.affectedRecordId,
                    log.previousValue,
                    log.newValue,
                    log.reason,
                    log.deviceInfo,
                ).joinToString(",") { escapeCsv(it) }
            }
            return header + rows
        }
    }

    /**
     * Records a sensitive admin action in `/audit_logs/{logId}`.
     * Enforces Firestore rule schema & field size limits and rethrows on persistence errors
     * after logging structured diagnostics.
     */
    suspend fun captureAction(
        admin: AdminUser,
        action: String,
        affectedCollection: String,
        affectedRecordId: String,
        previousValue: String,
        newValue: String,
        reason: String,
    ): AuditLog {
        val currentAuthUid = auth.currentUser?.uid
        val effectiveUid = admin.uid.trim().ifBlank { currentAuthUid.orEmpty() }
        require(effectiveUid.isNotBlank()) { "Authenticated admin UID is required to record audit logs." }
        if (currentAuthUid != null) {
            require(effectiveUid == currentAuthUid) {
                "Audit log adminUid must match the currently signed-in admin."
            }
        }

        val logId = "aud_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection(COLLECTION_AUDIT_LOGS).document(logId)

        val cleanEmail = sanitizeField(admin.email, maxLength = 118, fallback = "admin@sonawanegold.in")
        val cleanRole = sanitizeField(admin.role, maxLength = 38, fallback = "ADMIN")
        val cleanAction = sanitizeField(action.uppercase(Locale.US), maxLength = 78, fallback = "ADMIN_ACTION", minLength = 2)
        val cleanCollection = sanitizeField(affectedCollection, maxLength = 58, fallback = "admins", minLength = 2)
        val cleanRecordId = sanitizeField(affectedRecordId, maxLength = 126, fallback = "N/A", minLength = 1)
        val cleanPrev = sanitizeField(previousValue, maxLength = 490, fallback = "NONE")
        val cleanNext = sanitizeField(newValue, maxLength = 490, fallback = "NONE")
        val cleanReason = sanitizeField(reason, maxLength = 290, fallback = "Admin dashboard compliance action")
        val deviceInfo = buildDeviceDescriptor(admin)

        val payload = mapOf(
            "adminUid" to effectiveUid.take(128),
            "adminEmail" to cleanEmail,
            "adminRole" to cleanRole,
            "action" to cleanAction,
            "affectedCollection" to cleanCollection,
            "affectedRecordId" to cleanRecordId,
            "previousValue" to cleanPrev,
            "newValue" to cleanNext,
            "reason" to cleanReason,
            "deviceInfo" to deviceInfo,
            "createdAt" to FieldValue.serverTimestamp(),
        )

        try {
            ref.set(payload).await()
            return AuditLog(
                id = logId,
                adminUid = effectiveUid.take(128),
                adminEmail = cleanEmail,
                adminRole = cleanRole,
                action = cleanAction,
                affectedCollection = cleanCollection,
                affectedRecordId = cleanRecordId,
                previousValue = cleanPrev,
                newValue = cleanNext,
                reason = cleanReason,
                deviceInfo = deviceInfo,
                createdAt = Timestamp(Date()),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    /**
     * Non-fatal variant for security telemetry events (e.g., failed login or permission denial)
     * where logging should not mask the primary UI error if network/auth state is unavailable.
     */
    suspend fun captureActionBestEffort(
        admin: AdminUser,
        action: String,
        affectedCollection: String,
        affectedRecordId: String,
        previousValue: String,
        newValue: String,
        reason: String,
    ): Result<AuditLog> = runCatching {
        captureAction(
            admin = admin,
            action = action,
            affectedCollection = affectedCollection,
            affectedRecordId = affectedRecordId,
            previousValue = previousValue,
            newValue = newValue,
            reason = reason,
        )
    }

    /**
     * Captures user status changes (`ACTIVE`, `SUSPENDED`, `BLOCKED`) or KYC review decisions
     * (`APPROVED`, `REJECTED`, `INFO_REQUIRED`).
     */
    suspend fun captureUserApproval(
        admin: AdminUser,
        isKycDecision: Boolean,
        targetUserId: String,
        targetLabel: String,
        previousStatus: String,
        newStatus: String,
        reason: String,
    ): AuditLog {
        val normalizedStatus = newStatus.trim().uppercase(Locale.US)
        val action = if (isKycDecision) "KYC_$normalizedStatus" else "USER_STATUS_$normalizedStatus"
        val collection = if (isKycDecision) "kyc_records" else "users"
        val defaultReason = if (isKycDecision) {
            "KYC review decision ($normalizedStatus) for ${targetLabel.ifBlank { targetUserId }}"
        } else {
            "User account status updated for ${targetLabel.ifBlank { targetUserId }}"
        }
        return captureAction(
            admin = admin,
            action = action,
            affectedCollection = collection,
            affectedRecordId = targetUserId,
            previousValue = previousStatus.ifBlank { "UNKNOWN" },
            newValue = "$normalizedStatus (${targetLabel.take(40)})".trim(),
            reason = reason.ifBlank { defaultReason },
        )
    }

    /**
     * Captures RBAC role creation, updates, permission assignments, credential rotations,
     * and admin access revocation.
     */
    suspend fun captureRoleAssignment(
        actingAdmin: AdminUser,
        action: String,
        targetUid: String,
        targetEmail: String,
        previousState: String,
        newState: String,
        reason: String,
    ): AuditLog {
        return captureAction(
            admin = actingAdmin,
            action = action,
            affectedCollection = "admins",
            affectedRecordId = targetUid,
            previousValue = previousState.ifBlank { "NONE" },
            newValue = "$newState [$targetEmail]",
            reason = reason.ifBlank { "RBAC & operator credential update" },
        )
    }

    /**
     * Captures platform configuration updates with an explicit field-level diff between
     * [previousConfig] and [newConfig].
     */
    suspend fun captureConfigChange(
        admin: AdminUser,
        previousConfig: AppConfig?,
        newConfig: AppConfig,
        reason: String,
    ): AuditLog {
        val (prevSummary, nextSummary) = buildConfigDiff(previousConfig, newConfig)
        return captureAction(
            admin = admin,
            action = "UPDATE_APP_CONFIG",
            affectedCollection = "app_config",
            affectedRecordId = "settings",
            previousValue = prevSummary,
            newValue = nextSummary,
            reason = reason.ifBlank { "Platform settings updated" },
        )
    }

    /**
     * Captures admin authentication, biometric 2FA, lock, and unauthorized permission denial events.
     */
    suspend fun captureSecurityEvent(
        admin: AdminUser,
        action: String,
        previousValue: String,
        newValue: String,
        reason: String,
        bestEffort: Boolean = false,
    ): Result<AuditLog> {
        val block: suspend () -> AuditLog = {
            captureAction(
                admin = admin,
                action = action,
                affectedCollection = "admins",
                affectedRecordId = admin.uid,
                previousValue = previousValue,
                newValue = newValue,
                reason = reason,
            )
        }
        return if (bestEffort) {
            runCatching { block() }
        } else {
            Result.success(block())
        }
    }

    /**
     * Captures withdrawal settlements, support ticket moderation, announcements, and CSV exports.
     */
    suspend fun captureOperationsEvent(
        admin: AdminUser,
        action: String,
        affectedCollection: String,
        affectedRecordId: String,
        previousValue: String,
        newValue: String,
        reason: String,
    ): AuditLog {
        return captureAction(
            admin = admin,
            action = action,
            affectedCollection = affectedCollection,
            affectedRecordId = affectedRecordId,
            previousValue = previousValue,
            newValue = newValue,
            reason = reason,
        )
    }

    fun observeAuditLogs(limit: Int = 200): Flow<List<AuditLog>> = flow {
        emitAll(
            db.collection(COLLECTION_AUDIT_LOGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.coerceIn(1, 500).toLong())
                .snapshots()
                .map { it.toObjects(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, COLLECTION_AUDIT_LOGS)
                    throw e
                }
        )
    }

    suspend fun fetchAuditLogsOnce(limit: Int = 200): List<AuditLog> {
        return try {
            db.collection(COLLECTION_AUDIT_LOGS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.coerceIn(1, 500).toLong())
                .get()
                .await()
                .toObjects(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, COLLECTION_AUDIT_LOGS)
            throw e
        }
    }
}
