package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SecurityPreferences
import com.example.data.model.AdminUser
import com.example.data.model.AppConfig
import com.example.data.model.AuditLog
import com.example.data.model.KycRecord
import com.example.data.model.ReferralRecord
import com.example.data.model.SupportTicket
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserTransfer
import com.example.data.repository.AdminRepository
import com.example.data.repository.AdminRole
import com.example.data.repository.AuditLogManager
import com.example.data.repository.SonaWaneRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val adminRepo: AdminRepository,
    private val userRepo: SonaWaneRepository,
    private val prefs: SecurityPreferences,
) : ViewModel() {

    private val _adminUser = MutableStateFlow<AdminUser?>(null)
    val adminUser: StateFlow<AdminUser?> = _adminUser.asStateFlow()

    private val _eligibilityChecked = MutableStateFlow(false)
    val eligibilityChecked: StateFlow<Boolean> = _eligibilityChecked.asStateFlow()

    private val _adminVerified = MutableStateFlow(false)
    val adminVerified: StateFlow<Boolean> = _adminVerified.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val adminRateLimit: StateFlow<Pair<Int, Long>> = prefs.adminRateLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0 to 0L)

    private fun <T> safeAdminFlow(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> {
        return flow
            .catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), initial)
    }

    val allUsers: StateFlow<List<UserProfile>> = safeAdminFlow(adminRepo.observeAllUsers(), emptyList())
    val allTransactions: StateFlow<List<TransactionRecord>> = safeAdminFlow(adminRepo.observeAllTransactions(), emptyList())
    val allKyc: StateFlow<List<KycRecord>> = safeAdminFlow(adminRepo.observeAllKyc(), emptyList())
    val allTickets: StateFlow<List<SupportTicket>> = safeAdminFlow(adminRepo.observeAllTickets(), emptyList())
    val allTransfers: StateFlow<List<UserTransfer>> = safeAdminFlow(adminRepo.observeAllTransfers(), emptyList())
    val admins: StateFlow<List<AdminUser>> = safeAdminFlow(adminRepo.observeAdmins(), emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = safeAdminFlow(adminRepo.observeAuditLogs(), emptyList())
    val referrals: StateFlow<List<ReferralRecord>> = safeAdminFlow(adminRepo.observeReferrals(), emptyList())
    val appConfig: StateFlow<AppConfig> = safeAdminFlow(userRepo.observeAppConfig(), AppConfig())

    init {
        checkEligibility()
    }

    fun checkEligibility() {
        viewModelScope.launch {
            _eligibilityChecked.value = false
            _adminUser.value = adminRepo.verifyAdminEligibility()
            _eligibilityChecked.value = true
        }
    }

    fun enrollAdminCredentials(
        operatorId: String,
        passcode: String,
        confirmPasscode: String,
        biometricEnabled: Boolean,
    ) {
        val eligible = _adminUser.value ?: return
        val cleanOpId = operatorId.trim().uppercase()
        if (cleanOpId.length < 4) {
            _authErrorMessage.value = "Operator ID must be at least 4 characters (e.g. SWG-ADM-ROOT)."
            return
        }
        if (passcode.length < 6) {
            _authErrorMessage.value = "Admin Security Passcode must be at least 6 characters."
            return
        }
        if (passcode != confirmPasscode) {
            _authErrorMessage.value = "Passcode confirmation does not match."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _authErrorMessage.value = null
            runCatching {
                adminRepo.enrollAdminCredentials(eligible, cleanOpId, passcode, biometricEnabled)
            }.onSuccess { updated ->
                prefs.resetAdminFailures()
                _adminUser.value = updated
                _adminVerified.value = true
                _statusMessage.value = "Unique admin credentials enrolled for ${updated.operatorId} (${updated.role})."
            }.onFailure { err ->
                _authErrorMessage.value = err.localizedMessage ?: "Failed to enroll admin credentials."
            }
            _isBusy.value = false
        }
    }

    fun authenticateAdminCredentials(operatorId: String, passcode: String) {
        val eligible = _adminUser.value ?: run {
            _authErrorMessage.value = "No active admin authorization found for this account."
            return
        }
        val (attempts, lockoutUntil) = adminRateLimit.value
        val now = System.currentTimeMillis()
        if (lockoutUntil > now) {
            val remainingSec = ((lockoutUntil - now) / 1000L).coerceAtLeast(1L)
            _authErrorMessage.value = "Admin console locked for ${remainingSec}s due to failed attempts."
            return
        }
        if (operatorId.isBlank() || passcode.isBlank()) {
            _authErrorMessage.value = "Enter both your unique Admin Operator ID and Admin Passcode."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _authErrorMessage.value = null
            val ok = runCatching {
                adminRepo.verifyAndRecordAdminLogin(eligible, operatorId, passcode)
            }.getOrDefault(false)
            if (ok) {
                prefs.resetAdminFailures()
                _adminVerified.value = true
                _statusMessage.value = "Admin Console unlocked • ${eligible.role} (${eligible.operatorId})"
            } else {
                val (newAttempts, newLockout) = prefs.recordAdminFailure()
                _authErrorMessage.value = if (newLockout > System.currentTimeMillis()) {
                    "5 failed admin login attempts. Console locked for 90 seconds."
                } else {
                    "Invalid Admin Operator ID or Security Passcode (Attempt $newAttempts/5)."
                }
            }
            _isBusy.value = false
        }
    }

    fun unlockAdminWithBiometric() {
        val eligible = _adminUser.value ?: return
        if (!eligible.biometricAdminEnabled || eligible.adminPinHash.isNullOrBlank()) {
            _authErrorMessage.value = "Biometric unlock requires enrolled admin credentials and Biometric 2FA enabled."
            return
        }
        val (_, lockoutUntil) = adminRateLimit.value
        if (lockoutUntil > System.currentTimeMillis()) {
            val remainingSec = ((lockoutUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
            _authErrorMessage.value = "Admin console locked for ${remainingSec}s."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _authErrorMessage.value = null
            runCatching {
                adminRepo.recordBiometricAdminUnlock(eligible)
            }.onSuccess {
                prefs.resetAdminFailures()
                _adminVerified.value = true
                _statusMessage.value = "Biometric 2FA verified • ${eligible.role}"
            }.onFailure { err ->
                _authErrorMessage.value = err.localizedMessage ?: "Biometric unlock failed."
            }
            _isBusy.value = false
        }
    }

    fun rotateOwnAdminCredentials(
        operatorId: String,
        currentPasscode: String,
        newPasscode: String,
        biometricEnabled: Boolean,
    ) {
        val current = _adminUser.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val verified = runCatching {
                adminRepo.verifyAndRecordAdminLogin(
                    current,
                    current.operatorId.ifBlank { operatorId },
                    currentPasscode,
                )
            }.getOrDefault(false)
            if (!verified) {
                _statusMessage.value = "Current Admin Passcode verification failed."
                _isBusy.value = false
                return@launch
            }
            runCatching {
                adminRepo.enrollAdminCredentials(current, operatorId, newPasscode, biometricEnabled)
            }.onSuccess { updated ->
                _adminUser.value = updated
                _statusMessage.value = "Your Admin Operator ID & Passcode were rotated."
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Credential rotation failed."
            }
            _isBusy.value = false
        }
    }

    fun updateOwnBiometricSetting(enabled: Boolean) {
        val current = _adminUser.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                adminRepo.updateOwnAdminBiometric(current, enabled)
            }.onSuccess { updated ->
                _adminUser.value = updated
                _statusMessage.value = if (enabled) "Admin Biometric 2FA enabled." else "Admin Biometric 2FA disabled."
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to update biometric preference."
            }
            _isBusy.value = false
        }
    }

    fun lockAdminPortal() {
        val current = _adminUser.value
        val wasVerified = _adminVerified.value
        _adminVerified.value = false
        _authErrorMessage.value = null
        if (wasVerified && current != null) {
            viewModelScope.launch {
                runCatching { adminRepo.recordAdminPortalLock(current) }
            }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    private fun requirePermission(permission: String): AdminUser? {
        val admin = _adminUser.value
        if (admin == null || !_adminVerified.value) {
            _statusMessage.value = "Admin authentication required."
            return null
        }
        if (!admin.hasPermission(permission)) {
            _statusMessage.value = "Access denied: Role ${admin.role} lacks '$permission' permission."
            viewModelScope.launch {
                runCatching { adminRepo.recordPermissionDenied(admin, permission) }
            }
            return null
        }
        return admin
    }

    fun updateUserStatus(target: UserProfile, newStatus: String, reason: String) {
        val admin = requirePermission("USERS") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.updateUserAccountStatus(admin, target, newStatus, reason) }
                .onSuccess { _statusMessage.value = "${target.fullName} marked $newStatus" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Action failed" }
            _isBusy.value = false
        }
    }

    fun reviewKyc(kyc: KycRecord, newStatus: String, reason: String) {
        val admin = requirePermission("KYC") ?: return
        val cleanReason = reason.trim()
        if ((newStatus == "REJECTED" || newStatus == "INFO_REQUIRED") && cleanReason.isBlank()) {
            _statusMessage.value = "Rejection reason is mandatory before submitting."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.reviewKyc(admin, kyc, newStatus, cleanReason) }
                .onSuccess { _statusMessage.value = "KYC for ${kyc.userName} -> $newStatus" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "KYC review failed" }
            _isBusy.value = false
        }
    }

    fun processWithdrawal(tx: TransactionRecord, approve: Boolean, payoutRef: String, reason: String) {
        val admin = requirePermission("WITHDRAWALS") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.processWithdrawal(admin, tx, approve, payoutRef, reason) }
                .onSuccess { _statusMessage.value = "Withdrawal ${tx.txCode} processed." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Payout update failed" }
            _isBusy.value = false
        }
    }

    fun replyTicket(ticket: SupportTicket, replyText: String, newStatus: String) {
        val admin = requirePermission("SUPPORT") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.replyAndUpdateTicket(admin, ticket, replyText, newStatus) }
                .onSuccess { _statusMessage.value = "Ticket ${ticket.ticketCode} updated." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Ticket update failed" }
            _isBusy.value = false
        }
    }

    fun publishAnnouncement(title: String, message: String, priority: String) {
        val admin = requirePermission("SETTINGS") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.publishAnnouncement(admin, title, message, priority) }
                .onSuccess { _statusMessage.value = "Announcement broadcast live." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Broadcast failed" }
            _isBusy.value = false
        }
    }

    fun updateAppConfig(config: AppConfig, reason: String) {
        val admin = requirePermission("SETTINGS") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.updateAppConfig(admin, config, reason) }
                .onSuccess { _statusMessage.value = "Platform settings saved & audited." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Save failed" }
            _isBusy.value = false
        }
    }

    fun upsertAdminRole(
        uid: String,
        email: String,
        name: String,
        role: AdminRole,
        permissions: List<String>,
        operatorId: String,
        initialPasscode: String?,
        isActive: Boolean,
    ) {
        val admin = requirePermission("ROLES") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                adminRepo.upsertAdminRole(
                    superAdmin = admin,
                    targetUid = uid,
                    email = email,
                    displayName = name,
                    role = role,
                    permissions = permissions,
                    operatorId = operatorId,
                    initialPasscode = initialPasscode,
                    isActive = isActive,
                )
            }
                .onSuccess { _statusMessage.value = "Role ${role.label} saved for $email" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "RBAC update failed" }
            _isBusy.value = false
        }
    }

    fun resetAdminCredentials(target: AdminUser, newOperatorId: String, newPasscode: String) {
        val admin = requirePermission("ROLES") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching {
                adminRepo.resetAdminCredentials(admin, target, newOperatorId, newPasscode)
            }
                .onSuccess { _statusMessage.value = "Credentials rotated for ${target.email}" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Credential reset failed" }
            _isBusy.value = false
        }
    }

    fun deleteAdminRole(target: AdminUser) {
        val admin = requirePermission("ROLES") ?: return
        viewModelScope.launch {
            _isBusy.value = true
            runCatching { adminRepo.deleteAdminRole(admin, target) }
                .onSuccess { _statusMessage.value = "Revoked admin access for ${target.email}" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Revoke failed" }
            _isBusy.value = false
        }
    }

    fun buildTransactionsCsv(filterCategory: String? = null): String {
        val admin = _adminUser.value ?: return ""
        if (!admin.hasPermission("REPORTS") && !admin.hasPermission("FINANCE")) return ""
        val list = if (filterCategory == null) allTransactions.value else allTransactions.value.filter { it.category == filterCategory }
        val header = "TxCode,UserId,Category,Purity,Grams,RatePerGram,Base,GST,Charges,Total,Method,Status,Mode,GatewayRef\n"
        val rows = list.joinToString("\n") { tx ->
            "${tx.txCode},${tx.userId},${tx.category},${tx.metalPurity},${tx.grams},${tx.ratePerGram},${tx.baseAmount},${tx.gstAmount},${tx.chargesAmount},${tx.totalAmount},${tx.paymentMethod},${tx.status},${tx.mode},${tx.gatewayRef}"
        }
        viewModelScope.launch {
            runCatching {
                adminRepo.recordComplianceExport(
                    admin = admin,
                    exportType = filterCategory ?: "ALL_TRANSACTIONS_CSV",
                    rowCount = list.size,
                )
            }
        }
        return header + rows
    }

    fun buildAuditLogsCsv(filteredLogs: List<AuditLog> = auditLogs.value): String {
        val admin = _adminUser.value ?: return ""
        if (!admin.hasPermission("REPORTS") && !admin.hasPermission("ROLES")) return ""
        val csv = AuditLogManager.buildAuditComplianceCsv(filteredLogs)
        viewModelScope.launch {
            runCatching {
                adminRepo.recordComplianceExport(
                    admin = admin,
                    exportType = "AUDIT_LOGS_CSV",
                    rowCount = filteredLogs.size,
                )
            }
        }
        return csv
    }
}
