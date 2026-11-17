package com.example.data.repository

import android.content.Context
import com.example.data.model.AdminUser
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.AuditLog
import com.example.data.model.KycRecord
import com.example.data.model.ReferralRecord
import com.example.data.model.SupportTicket
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserTransfer
import com.example.data.repository.FirestoreService.toObjectSafely
import com.example.data.repository.FirestoreService.toObjectsSafely
import com.example.util.CryptoUtils
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

enum class AdminRole(val label: String, val permissions: List<String>) {
    SUPER_ADMIN("Super Admin", listOf("ALL", "USERS", "KYC", "FINANCE", "WITHDRAWALS", "SUPPORT", "SETTINGS", "ROLES", "REPORTS")),
    ADMIN("Admin", listOf("USERS", "KYC", "FINANCE", "WITHDRAWALS", "SUPPORT", "SETTINGS", "REPORTS")),
    FINANCE_MANAGER("Finance Manager", listOf("FINANCE", "WITHDRAWALS", "REPORTS")),
    KYC_MANAGER("KYC Manager", listOf("KYC", "USERS")),
    CUSTOMER_SUPPORT("Customer Support", listOf("SUPPORT", "USERS")),
    READ_ONLY_AUDITOR("Read-Only Auditor", listOf("REPORTS")),
}

val ALL_ADMIN_PERMISSIONS: List<Pair<String, String>> = listOf(
    "USERS" to "User Directory & Account Status",
    "KYC" to "KYC & PAN Verification Queue",
    "FINANCE" to "Orders & Ledger Visibility",
    "WITHDRAWALS" to "Bank Payout Settlement",
    "SUPPORT" to "Customer Support Tickets",
    "SETTINGS" to "Platform Config & Broadcasts",
    "ROLES" to "RBAC & Admin Credentials",
    "REPORTS" to "Analytics, Audit Trail & CSV",
)

sealed class RbacCheckResult {
    data object Checking : RbacCheckResult()
    data class Authorized(val admin: AdminUser) : RbacCheckResult()
    data class AccessDenied(val reason: String, val email: String = "") : RbacCheckResult()
    data class Error(val message: String, val isPermissionDenied: Boolean = false) : RbacCheckResult()
}

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirestoreService.getAuth(),
    val auditLogManager: AuditLogManager = AuditLogManager(db, auth),
) {
    constructor(context: Context) : this(
        db = FirestoreService.getDatabase(context),
        auth = FirestoreService.getAuth(context),
    )

    companion object {
        const val BOOTSTRAPPED_SUPER_ADMIN_EMAIL = "goldsonawane@gmail.com"

        fun defaultOperatorId(uid: String, email: String): String {
            return if (email.equals(BOOTSTRAPPED_SUPER_ADMIN_EMAIL, ignoreCase = true)) {
                "SWG-ADM-ROOT"
            } else {
                "SWG-ADM-${uid.take(6).uppercase().ifBlank { "OP01" }}"
            }
        }

        private fun safeTimestamp(raw: Any?): Timestamp? = when (raw) {
            is Timestamp -> raw
            is Date -> Timestamp(raw)
            is Number -> runCatching { Timestamp(Date(raw.toLong())) }.getOrNull()
            else -> null
        }

        fun parseAdminMapSafely(docId: String, data: Map<String, Any?>): AdminUser {
            val uid = (data["uid"] as? String)?.trim().orEmpty().ifBlank { docId }
            val email = (data["email"] as? String)?.trim().orEmpty()
            val displayName = (data["displayName"] as? String)?.trim().orEmpty()
                .ifBlank { email.substringBefore("@").ifBlank { "Admin Operator" } }
            val operatorId = (data["operatorId"] as? String)?.trim().orEmpty()
            val role = (data["role"] as? String)?.trim().orEmpty()
            val permissions = (data["permissions"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { p -> p.isNotEmpty() } }
                ?: emptyList()
            val isActive = (data["isActive"] as? Boolean)
                ?: (data["active"] as? Boolean)
                ?: false
            val adminPinHash = (data["adminPinHash"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val biometricAdminEnabled = (data["biometricAdminEnabled"] as? Boolean) ?: false

            return AdminUser(
                id = docId,
                uid = uid,
                email = email,
                displayName = displayName,
                operatorId = operatorId,
                role = role,
                permissions = permissions,
                isActive = isActive,
                adminPinHash = adminPinHash,
                biometricAdminEnabled = biometricAdminEnabled,
                lastLoginAt = safeTimestamp(data["lastLoginAt"]),
                createdAt = safeTimestamp(data["createdAt"]),
                updatedAt = safeTimestamp(data["updatedAt"]),
            )
        }

        fun parseAdminSafely(doc: DocumentSnapshot?): AdminUser? {
            if (doc == null || !doc.exists()) return null
            val data = try {
                doc.data
            } catch (_: Exception) {
                null
            } ?: return null
            return try {
                parseAdminMapSafely(doc.id, data)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun verifyAdminEligibilityDetailed(): RbacCheckResult {
        val user = try {
            auth.currentUser
        } catch (_: Exception) {
            null
        } ?: return RbacCheckResult.AccessDenied(
            reason = "No active authenticated session found. Please sign in with Google first.",
        )

        val uid = user.uid.trim()
        val userEmail = user.email?.trim().orEmpty()
        var firestoreFailure: FirebaseFirestoreException? = null
        var genericFailure: Exception? = null

        return try {
            var matchedDoc: DocumentSnapshot? = null

            // 1. Primary lookup: admins/{uid}
            if (uid.isNotBlank()) {
                try {
                    val uidDoc = db.collection("admins").document(uid).get().await()
                    if (uidDoc.exists()) {
                        matchedDoc = uidDoc
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseFirestoreException) {
                    firestoreFailure = e
                    handleFirestoreError(e, OperationType.GET, "admins/$uid")
                } catch (e: Exception) {
                    genericFailure = e
                }
            }

            // 2. Fallback lookup: query where email == current user's email
            if (matchedDoc == null && userEmail.isNotBlank()) {
                try {
                    val emailSnap = db.collection("admins")
                        .whereEqualTo("email", userEmail)
                        .limit(1)
                        .get()
                        .await()
                    matchedDoc = emailSnap.documents.firstOrNull { it.exists() }
                    if (matchedDoc == null) {
                        val lowerEmail = userEmail.lowercase()
                        if (lowerEmail != userEmail) {
                            val lowerSnap = db.collection("admins")
                                .whereEqualTo("email", lowerEmail)
                                .limit(1)
                                .get()
                                .await()
                            matchedDoc = lowerSnap.documents.firstOrNull { it.exists() }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseFirestoreException) {
                    if (firestoreFailure == null) firestoreFailure = e
                    handleFirestoreError(e, OperationType.LIST, "admins")
                } catch (e: Exception) {
                    if (genericFailure == null) genericFailure = e
                }
            }

            if (matchedDoc != null) {
                val parsed = parseAdminSafely(matchedDoc)
                    ?: return RbacCheckResult.Error(
                        message = "Admin record exists but could not be read safely. Please contact the Super Admin.",
                    )
                val resolvedEmail = parsed.email.ifBlank { userEmail }
                val isBootstrapped = user.isEmailVerified &&
                    userEmail.equals(BOOTSTRAPPED_SUPER_ADMIN_EMAIL, ignoreCase = true)

                if (!parsed.isActive && !isBootstrapped) {
                    return RbacCheckResult.AccessDenied(
                        reason = "Your administrator account is currently inactive (isActive = false).",
                        email = resolvedEmail,
                    )
                }
                if (parsed.role.isBlank()) {
                    return RbacCheckResult.AccessDenied(
                        reason = "Administrator role is missing or unassigned in Firestore RBAC.",
                        email = resolvedEmail,
                    )
                }

                val resolvedUid = parsed.uid.ifBlank { uid }
                val resolvedOpId = parsed.operatorId.ifBlank { defaultOperatorId(resolvedUid, resolvedEmail) }
                val resolvedPerms = parsed.permissions.ifEmpty {
                    if (parsed.role == AdminRole.SUPER_ADMIN.name) AdminRole.SUPER_ADMIN.permissions else emptyList()
                }
                return RbacCheckResult.Authorized(
                    parsed.copy(
                        uid = resolvedUid,
                        email = resolvedEmail,
                        operatorId = resolvedOpId,
                        permissions = resolvedPerms,
                        isActive = true,
                    )
                )
            }

            // 3. Bootstrapped Super Admin fallback when document has not yet been provisioned
            if (user.isEmailVerified && userEmail.equals(BOOTSTRAPPED_SUPER_ADMIN_EMAIL, ignoreCase = true)) {
                val opId = defaultOperatorId(uid, userEmail)
                val superAdmin = AdminUser(
                    id = uid,
                    uid = uid,
                    email = userEmail,
                    displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Super Admin",
                    operatorId = opId,
                    role = AdminRole.SUPER_ADMIN.name,
                    permissions = AdminRole.SUPER_ADMIN.permissions,
                    isActive = true,
                )
                try {
                    db.collection("admins").document(uid).set(
                        mapOf(
                            "uid" to uid,
                            "email" to superAdmin.email,
                            "displayName" to superAdmin.displayName,
                            "operatorId" to opId,
                            "role" to superAdmin.role,
                            "permissions" to superAdmin.permissions,
                            "isActive" to true,
                            "biometricAdminEnabled" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                return RbacCheckResult.Authorized(superAdmin)
            }

            if (firestoreFailure != null) {
                val isPerm = firestoreFailure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                val msg = if (isPerm) {
                    "Firestore permission denied while verifying RBAC access (${firestoreFailure.localizedMessage ?: "PERMISSION_DENIED"})."
                } else {
                    "Unable to reach Firestore RBAC service: ${firestoreFailure.localizedMessage ?: firestoreFailure.code.name}."
                }
                return RbacCheckResult.Error(message = msg, isPermissionDenied = isPerm)
            }

            if (genericFailure != null) {
                return RbacCheckResult.Error(
                    message = genericFailure.localizedMessage ?: "Unexpected network error during RBAC verification.",
                )
            }

            RbacCheckResult.AccessDenied(
                reason = "This account does not have an active role in Firestore RBAC. Contact the Super Admin to assign a role and Operator ID.",
                email = userEmail,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.GET, "admins/$uid")
            val isPerm = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            RbacCheckResult.Error(
                message = e.localizedMessage ?: "Firestore RBAC check failed (${e.code.name}).",
                isPermissionDenied = isPerm,
            )
        } catch (e: Exception) {
            RbacCheckResult.Error(
                message = e.localizedMessage ?: "Unexpected error while verifying RBAC authorization.",
            )
        }
    }

    suspend fun verifyAdminEligibility(): AdminUser? {
        return (verifyAdminEligibilityDetailed() as? RbacCheckResult.Authorized)?.admin
    }

    private fun hashAdminPasscode(passcode: String): String {
        val salt = CryptoUtils.generateSalt()
        val digest = CryptoUtils.hashSecret(passcode, salt)
        return "$salt:$digest"
    }

    private fun verifyAdminPasscode(passcode: String, storedHash: String?): Boolean {
        if (storedHash.isNullOrBlank()) return false
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        return CryptoUtils.verifySecret(passcode, parts[0], parts[1])
    }

    suspend fun enrollAdminCredentials(
        admin: AdminUser,
        operatorId: String,
        passcode: String,
        biometricEnabled: Boolean,
    ): AdminUser {
        val normalizedOpId = operatorId.trim().uppercase().take(32)
        require(normalizedOpId.length >= 4) { "Operator ID must be at least 4 characters." }
        require(passcode.length >= 6) { "Admin passcode must be at least 6 characters." }
        val encodedHash = hashAdminPasscode(passcode)
        val targetDocId = admin.id.ifBlank { admin.uid }
        val ref = db.collection("admins").document(targetDocId)
        try {
            val existingSnap = runCatching { ref.get().await() }.getOrNull()
            val needsSchemaBackfill = existingSnap == null ||
                !existingSnap.exists() ||
                existingSnap.getString("displayName").isNullOrBlank() ||
                existingSnap.getTimestamp("createdAt") == null

            if (needsSchemaBackfill) {
                val mergePayload = mutableMapOf<String, Any>(
                    "uid" to admin.uid.ifBlank { targetDocId },
                    "email" to admin.email,
                    "displayName" to admin.displayName.ifBlank { admin.email.substringBefore("@").ifBlank { "Admin Operator" } },
                    "operatorId" to normalizedOpId,
                    "role" to admin.role.ifBlank { AdminRole.ADMIN.name },
                    "permissions" to admin.permissions.ifEmpty { AdminRole.ADMIN.permissions },
                    "isActive" to true,
                    "adminPinHash" to encodedHash,
                    "biometricAdminEnabled" to biometricEnabled,
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "createdAt" to (existingSnap?.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                ref.set(mergePayload, SetOptions.merge()).await()
            } else {
                ref.update(
                    mapOf(
                        "operatorId" to normalizedOpId,
                        "adminPinHash" to encodedHash,
                        "biometricAdminEnabled" to biometricEnabled,
                        "lastLoginAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            auditLogManager.captureRoleAssignment(
                actingAdmin = admin,
                action = "ADMIN_CREDENTIALS_ENROLLED",
                targetUid = admin.uid,
                targetEmail = admin.email,
                previousState = admin.operatorId.ifBlank { "UNSET" },
                newState = "$normalizedOpId (biometric=$biometricEnabled)",
                reason = "Admin unique credential enrollment / rotation",
            )
            return parseAdminSafely(ref.get().await())
                ?: admin.copy(
                    operatorId = normalizedOpId,
                    adminPinHash = encodedHash,
                    biometricAdminEnabled = biometricEnabled,
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun verifyAndRecordAdminLogin(
        admin: AdminUser,
        enteredOperatorId: String,
        enteredPasscode: String,
    ): Boolean {
        val expectedOpId = admin.operatorId.ifBlank { defaultOperatorId(admin.uid, admin.email) }
        val idMatches = enteredOperatorId.trim().equals(expectedOpId, ignoreCase = true)
        val passcodeMatches = verifyAdminPasscode(enteredPasscode, admin.adminPinHash)
        if (!idMatches || !passcodeMatches) {
            auditLogManager.captureSecurityEvent(
                admin = admin,
                action = "ADMIN_LOGIN_FAILED",
                previousValue = expectedOpId,
                newValue = "AttemptedOpId=${enteredOperatorId.trim().take(24)}",
                reason = "Invalid unique admin Operator ID or passcode",
                bestEffort = true,
            )
            return false
        }
        val ref = db.collection("admins").document(admin.uid)
        try {
            ref.update(
                mapOf(
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureSecurityEvent(
                admin = admin,
                action = "ADMIN_PORTAL_LOGIN",
                previousValue = "LOCKED",
                newValue = "opId=$expectedOpId role=${admin.role}",
                reason = "Verified isolated admin login with unique credentials",
                bestEffort = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
        return true
    }

    suspend fun recordBiometricAdminUnlock(admin: AdminUser) {
        val ref = db.collection("admins").document(admin.uid)
        try {
            ref.update(
                mapOf(
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureSecurityEvent(
                admin = admin,
                action = "ADMIN_BIOMETRIC_UNLOCK",
                previousValue = "LOCKED",
                newValue = "opId=${admin.operatorId} role=${admin.role}",
                reason = "Biometric MFA admin portal unlock",
                bestEffort = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }

    suspend fun updateOwnAdminBiometric(admin: AdminUser, enabled: Boolean): AdminUser {
        val ref = db.collection("admins").document(admin.uid)
        try {
            ref.update(
                mapOf(
                    "biometricAdminEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureSecurityEvent(
                admin = admin,
                action = "ADMIN_BIOMETRIC_SETTING",
                previousValue = admin.biometricAdminEnabled.toString(),
                newValue = enabled.toString(),
                reason = "Admin biometric 2FA preference updated",
            )
            return admin.copy(biometricAdminEnabled = enabled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun recordAdminPortalLock(admin: AdminUser) {
        auditLogManager.captureSecurityEvent(
            admin = admin,
            action = "ADMIN_PORTAL_LOCKED",
            previousValue = "UNLOCKED (${admin.operatorId})",
            newValue = "LOCKED",
            reason = "Admin operator locked or exited the Admin Console",
            bestEffort = true,
        )
    }

    suspend fun recordPermissionDenied(admin: AdminUser, requiredPermission: String) {
        auditLogManager.captureSecurityEvent(
            admin = admin,
            action = "PERMISSION_DENIED_$requiredPermission",
            previousValue = admin.permissions.joinToString(","),
            newValue = "BLOCKED:$requiredPermission",
            reason = "Role ${admin.role} attempted restricted action requiring '$requiredPermission'",
            bestEffort = true,
        )
    }

    suspend fun recordComplianceExport(admin: AdminUser, exportType: String, rowCount: Int) {
        auditLogManager.captureActionBestEffort(
            admin = admin,
            action = "EXPORT_${exportType.uppercase()}",
            affectedCollection = if (exportType.contains("AUDIT", ignoreCase = true)) "audit_logs" else "transactions",
            affectedRecordId = "CSV_${rowCount}_ROWS",
            previousValue = "NONE",
            newValue = "$exportType ($rowCount records)",
            reason = "Compliance CSV export triggered by ${admin.email}",
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        emitAll(
            db.collection("users")
                .snapshots()
                .map { it.toObjectsSafely<UserProfile>() }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "users")
                    throw e
                }
        )
    }

    fun observeAllTransactions(): Flow<List<TransactionRecord>> = flow {
        emitAll(
            db.collection("transactions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(250)
                .snapshots()
                .map { it.toObjectsSafely<TransactionRecord>() }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "transactions")
                    throw e
                }
        )
    }

    fun observeAllKyc(): Flow<List<KycRecord>> = flow {
        emitAll(
            db.collection("kyc_records")
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<KycRecord>()
                        .sortedWith(
                            compareBy<KycRecord>(
                                {
                                    when (it.status) {
                                        "PENDING", "UNDER_REVIEW" -> 0
                                        "INFO_REQUIRED" -> 1
                                        "REJECTED" -> 2
                                        else -> 3
                                    }
                                },
                                { -(it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L) },
                            )
                        )
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "kyc_records")
                    throw e
                }
        )
    }

    fun observeAllTickets(): Flow<List<SupportTicket>> = flow {
        emitAll(
            db.collection("support_tickets")
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<SupportTicket>()
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "support_tickets")
                    throw e
                }
        )
    }

    fun observeAllTransfers(): Flow<List<UserTransfer>> = flow {
        emitAll(
            db.collection("transfers")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.toObjectsSafely<UserTransfer>() }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "transfers")
                    throw e
                }
        )
    }

    fun observeAdmins(): Flow<List<AdminUser>> = flow {
        emitAll(
            db.collection("admins")
                .snapshots()
                .map { snap -> snap.documents.mapNotNull { parseAdminSafely(it) } }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "admins")
                    throw e
                }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = auditLogManager.observeAuditLogs()

    fun observeReferrals(): Flow<List<ReferralRecord>> = flow {
        emitAll(
            db.collection("referrals")
                .snapshots()
                .map { it.toObjectsSafely<ReferralRecord>() }
                .catch { e ->
                    if (e is CancellationException) throw e
                    handleFirestoreError(e, OperationType.LIST, "referrals")
                    throw e
                }
        )
    }

    // --- ADMIN ACTIONS ---

    suspend fun updateUserAccountStatus(admin: AdminUser, user: UserProfile, newStatus: String, reason: String) {
        val ref = db.collection("users").document(user.userId)
        try {
            ref.update(mapOf("accountStatus" to newStatus, "updatedAt" to FieldValue.serverTimestamp())).await()
            auditLogManager.captureUserApproval(
                admin = admin,
                isKycDecision = false,
                targetUserId = user.userId,
                targetLabel = user.fullName.ifBlank { user.email },
                previousStatus = user.accountStatus,
                newStatus = newStatus,
                reason = reason,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun reviewKyc(admin: AdminUser, kyc: KycRecord, newStatus: String, reason: String) {
        val trimmedReason = reason.trim()
        if (newStatus == "REJECTED" || newStatus == "INFO_REQUIRED") {
            require(trimmedReason.isNotBlank()) {
                "A rejection reason is mandatory when rejecting or requesting KYC resubmission."
            }
        }
        val kycRef = db.collection("kyc_records").document(kyc.userId)
        val userRef = db.collection("users").document(kyc.userId)
        try {
            val kycUpdate = mutableMapOf<String, Any>(
                "status" to newStatus,
                "reviewedByAdminId" to admin.uid,
                "rejectionReason" to if (newStatus == "APPROVED") "" else trimmedReason.take(290),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            kycRef.update(kycUpdate).await()
            userRef.update(mapOf("kycStatus" to newStatus, "updatedAt" to FieldValue.serverTimestamp())).await()

            val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val notifBody = when (newStatus) {
                "APPROVED" -> "Your KYC & PAN verification is approved. Full trading limits unlocked."
                "INFO_REQUIRED" -> "Additional KYC info requested: $trimmedReason"
                else -> "KYC Rejected: $trimmedReason. Please resubmit clear documents."
            }
            db.collection("users").document(kyc.userId).collection("notifications").document(notifId).set(
                mapOf(
                    "userId" to kyc.userId,
                    "type" to if (newStatus == "APPROVED") "KYC_APPROVED" else "KYC_REJECTED",
                    "title" to "KYC Status: ${newStatus.replace("_", " ")}",
                    "body" to notifBody.take(490),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            auditLogManager.captureUserApproval(
                admin = admin,
                isKycDecision = true,
                targetUserId = kyc.userId,
                targetLabel = kyc.userName.ifBlank { kyc.panNumber },
                previousStatus = kyc.status,
                newStatus = newStatus,
                reason = trimmedReason.ifBlank { "Verified ID documents & PAN details" },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, kycRef.path)
            throw e
        }
    }

    suspend fun processWithdrawal(
        admin: AdminUser,
        tx: TransactionRecord,
        approve: Boolean,
        payoutRef: String,
        reason: String,
    ) {
        val txRef = db.collection("transactions").document(tx.id)
        val walletRef = db.collection("users").document(tx.userId).collection("wallets").document("primary")
        val ledgerRef = db.collection("users").document(tx.userId).collection("ledger").document("led_adm_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        val newStatus = if (approve) "SUCCESSFUL" else "REVERSED"

        try {
            db.runTransaction { tr ->
                val wSnap = tr.get(walletRef)
                val avail = wSnap.getDouble("availableInr") ?: 0.0
                val locked = wSnap.getDouble("lockedInr") ?: 0.0
                val newLocked = (locked - tx.totalAmount).coerceAtLeast(0.0)
                val newAvail = if (approve) avail else avail + tx.totalAmount

                tr.update(
                    walletRef,
                    mapOf(
                        "availableInr" to newAvail,
                        "lockedInr" to newLocked,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.update(
                    txRef,
                    mapOf(
                        "status" to newStatus,
                        "gatewayRef" to if (approve) payoutRef.ifBlank { CryptoUtils.generateCode("UTR") } else tx.gatewayRef,
                        "rejectionReason" to if (approve) "" else reason,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.set(
                    ledgerRef,
                    mapOf(
                        "userId" to tx.userId,
                        "entryType" to if (approve) "WITHDRAWAL_COMPLETE" else "REFUND",
                        "assetType" to "INR",
                        "deltaAmount" to if (approve) 0.0 else tx.totalAmount,
                        "balanceAfter" to newAvail,
                        "referenceId" to tx.txCode,
                        "idempotencyKey" to "adm_${tx.id}",
                        "description" to if (approve) "Withdrawal paid out ($payoutRef)" else "Withdrawal reversed: $reason",
                        "mode" to tx.mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()

            val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            db.collection("users").document(tx.userId).collection("notifications").document(notifId).set(
                mapOf(
                    "userId" to tx.userId,
                    "type" to if (approve) "WITHDRAWAL_COMPLETED" else "PAYMENT_FAILED",
                    "title" to if (approve) "Withdrawal Completed" else "Withdrawal Reversed",
                    "body" to if (approve) "Payout of ${tx.txCode} completed. UTR: $payoutRef" else "Withdrawal ${tx.txCode} reversed: $reason",
                    "isRead" to false,
                    "referenceId" to tx.id,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            auditLogManager.captureOperationsEvent(
                admin = admin,
                action = "WITHDRAWAL_$newStatus",
                affectedCollection = "transactions",
                affectedRecordId = tx.id,
                previousValue = tx.status,
                newValue = if (approve) "$newStatus ($payoutRef)" else newStatus,
                reason = reason,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, txRef.path)
            throw e
        }
    }

    suspend fun replyAndUpdateTicket(admin: AdminUser, ticket: SupportTicket, replyText: String, newStatus: String) {
        val ref = db.collection("support_tickets").document(ticket.id)
        val updatedMessages = if (replyText.isNotBlank()) {
            (ticket.messages + mapOf(
                "senderRole" to "ADMIN",
                "senderName" to admin.displayName,
                "text" to replyText.trim(),
                "timestampMillis" to System.currentTimeMillis(),
            )).takeLast(50)
        } else {
            ticket.messages
        }
        try {
            ref.update(
                mapOf(
                    "messages" to updatedMessages,
                    "status" to newStatus,
                    "assignedAdminId" to admin.uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureOperationsEvent(
                admin = admin,
                action = "TICKET_$newStatus",
                affectedCollection = "support_tickets",
                affectedRecordId = ticket.id,
                previousValue = ticket.status,
                newValue = newStatus,
                reason = replyText.ifBlank { "Ticket status transitioned to $newStatus" },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun publishAnnouncement(admin: AdminUser, title: String, message: String, priority: String) {
        val id = "ann_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val ref = db.collection("announcements").document(id)
        try {
            ref.set(
                mapOf(
                    "title" to title.trim(),
                    "message" to message.trim(),
                    "priority" to priority,
                    "createdByUid" to admin.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureOperationsEvent(
                admin = admin,
                action = "PUBLISH_ANNOUNCEMENT",
                affectedCollection = "announcements",
                affectedRecordId = id,
                previousValue = "NONE",
                newValue = "${title.trim()} [$priority]",
                reason = message.trim().take(200),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun updateAppConfig(admin: AdminUser, config: AppConfig, reason: String) {
        val ref = db.collection("app_config").document("settings")
        try {
            val existingSnap = ref.get().await()
            val previousConfig = if (existingSnap.exists()) {
                existingSnap.toObjectSafely<AppConfig>()
            } else {
                null
            }
            ref.set(
                mapOf(
                    "appName" to config.appName,
                    "tagline" to config.tagline,
                    "maintenanceMode" to config.maintenanceMode,
                    "paymentMode" to config.paymentMode,
                    "paymentGatewayName" to config.paymentGatewayName,
                    "goldProviderName" to config.goldProviderName,
                    "gstPercent" to config.gstPercent,
                    "goldSpreadPercent" to config.goldSpreadPercent,
                    "silverSpreadPercent" to config.silverSpreadPercent,
                    "withdrawalFeeInr" to config.withdrawalFeeInr,
                    "transferFeePercent" to config.transferFeePercent,
                    "referralRewardInr" to config.referralRewardInr,
                    "referralEnabled" to config.referralEnabled,
                    "termsText" to config.termsText,
                    "privacyText" to config.privacyText,
                    "supportEmail" to config.supportEmail,
                    "supportPhone" to config.supportPhone,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureConfigChange(
                admin = admin,
                previousConfig = previousConfig,
                newConfig = config,
                reason = reason,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun upsertAdminRole(
        superAdmin: AdminUser,
        targetUid: String,
        email: String,
        displayName: String,
        role: AdminRole,
        permissions: List<String> = role.permissions,
        operatorId: String = "",
        initialPasscode: String? = null,
        isActive: Boolean,
    ) {
        val ref = db.collection("admins").document(targetUid)
        try {
            val snap = ref.get().await()
            val prevRole = snap.getString("role").orEmpty()
            val prevOpId = snap.getString("operatorId").orEmpty()
            val prevActive = snap.getBoolean("isActive")
            val resolvedOpId = operatorId.trim().uppercase().take(32)
                .ifBlank { prevOpId }
                .ifBlank { defaultOperatorId(targetUid, email.trim()) }
            val resolvedPerms = if (role == AdminRole.SUPER_ADMIN) {
                AdminRole.SUPER_ADMIN.permissions
            } else {
                permissions.distinct().ifEmpty { role.permissions }
            }
            val existingPinHash = snap.getString("adminPinHash")
            val resolvedPinHash = if (!initialPasscode.isNullOrBlank()) {
                require(initialPasscode.length >= 6) { "Admin passcode must be at least 6 characters." }
                hashAdminPasscode(initialPasscode)
            } else {
                existingPinHash
            }

            val payload = mutableMapOf<String, Any>(
                "uid" to targetUid,
                "email" to email.trim(),
                "displayName" to displayName.trim(),
                "operatorId" to resolvedOpId,
                "role" to role.name,
                "permissions" to resolvedPerms,
                "isActive" to isActive,
                "biometricAdminEnabled" to (snap.getBoolean("biometricAdminEnabled") ?: false),
                "createdAt" to (snap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!resolvedPinHash.isNullOrBlank()) {
                payload["adminPinHash"] = resolvedPinHash
            }
            snap.getTimestamp("lastLoginAt")?.let { payload["lastLoginAt"] = it }

            ref.set(payload).await()
            val prevDesc = if (prevRole.isBlank()) {
                "NONE"
            } else {
                "$prevRole opId=${prevOpId.ifBlank { "UNSET" }} active=${prevActive ?: true}"
            }
            auditLogManager.captureRoleAssignment(
                actingAdmin = superAdmin,
                action = "UPSERT_ADMIN_ROLE",
                targetUid = targetUid,
                targetEmail = email.trim(),
                previousState = prevDesc,
                newState = "${role.name} opId=$resolvedOpId perms=${resolvedPerms.joinToString(",")} active=$isActive",
                reason = "RBAC role & credential configuration",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun resetAdminCredentials(
        superAdmin: AdminUser,
        targetAdmin: AdminUser,
        newOperatorId: String,
        newPasscode: String,
    ) {
        val normalizedOpId = newOperatorId.trim().uppercase().take(32)
            .ifBlank { targetAdmin.operatorId.ifBlank { defaultOperatorId(targetAdmin.uid, targetAdmin.email) } }
        require(normalizedOpId.length >= 4) { "Operator ID must be at least 4 characters." }
        require(newPasscode.length >= 6) { "New passcode must be at least 6 characters." }
        val encodedHash = hashAdminPasscode(newPasscode)
        val ref = db.collection("admins").document(targetAdmin.uid)
        try {
            ref.update(
                mapOf(
                    "operatorId" to normalizedOpId,
                    "adminPinHash" to encodedHash,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            auditLogManager.captureRoleAssignment(
                actingAdmin = superAdmin,
                action = "RESET_ADMIN_CREDENTIALS",
                targetUid = targetAdmin.uid,
                targetEmail = targetAdmin.email,
                previousState = targetAdmin.operatorId.ifBlank { "UNSET" },
                newState = normalizedOpId,
                reason = "Super admin rotated Operator ID / Passcode for ${targetAdmin.email}",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun deleteAdminRole(superAdmin: AdminUser, targetAdmin: AdminUser) {
        require(!targetAdmin.email.equals(BOOTSTRAPPED_SUPER_ADMIN_EMAIL, ignoreCase = true)) {
            "Primary Super Admin cannot be revoked."
        }
        require(targetAdmin.uid != superAdmin.uid) {
            "You cannot revoke your own active admin session."
        }
        val ref = db.collection("admins").document(targetAdmin.uid)
        try {
            ref.delete().await()
            auditLogManager.captureRoleAssignment(
                actingAdmin = superAdmin,
                action = "REVOKE_ADMIN_ACCESS",
                targetUid = targetAdmin.uid,
                targetEmail = targetAdmin.email,
                previousState = "${targetAdmin.role} (${targetAdmin.operatorId.ifBlank { targetAdmin.email }})",
                newState = "REVOKED",
                reason = "Admin access permanently revoked by ${superAdmin.email}",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }
}
