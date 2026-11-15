package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BgDropMonitorState
import com.example.data.local.GoldDropInAppAlert
import com.example.data.local.SecurityPreferences
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.AppNotification
import com.example.data.model.BankAccount
import com.example.data.model.DeviceSession
import com.example.data.model.DirectoryEntry
import com.example.data.model.DualMetalTrendSeries
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.KycRecord
import com.example.data.model.LiveMarketRates
import com.example.data.model.LoginHistoryEntry
import com.example.data.model.MarketPoint
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.PriceAlert
import com.example.data.model.ReferralRecord
import com.example.data.model.SupportTicket
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserTransfer
import com.example.data.model.WalletHoldings
import com.example.data.model.WalletLedgerEntry
import com.example.data.remote.MarketRateService
import com.example.data.repository.SonaWaneRepository
import com.example.service.GoldPriceMonitorService
import com.example.util.BullionNotificationHelper
import com.example.util.CryptoUtils
import com.example.util.Formatters
import com.example.worker.GoldPriceMonitorWorker
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.math.abs

data class ActionBanner(
    val message: String,
    val isError: Boolean = false,
    val receiptTx: TransactionRecord? = null,
)

data class SecurityOtpState(
    val isActive: Boolean = false,
    val purpose: String = "",
    val expiresAtMs: Long = 0L,
    val resendAvailableAtMs: Long = 0L,
    val attemptsRemaining: Int = 5,
    val sandboxOtpHint: String? = null,
    val errorMessage: String? = null,
)

class MainViewModel(
    private val repository: SonaWaneRepository,
    private val marketService: MarketRateService,
    private val prefs: SecurityPreferences,
    val currentUserId: String,
    private val currentEmail: String?,
    private val currentDisplayName: String?,
    private val appContext: Context? = null,
) : ViewModel() {

    private var activeOtpCode: String? = null
    private var previousRates: LiveMarketRates? = null

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    private val _deviceId = MutableStateFlow("dev_default")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _marketRates = MutableStateFlow(LiveMarketRates())
    val marketRates: StateFlow<LiveMarketRates> = _marketRates.asStateFlow()

    private val _chartPoints = MutableStateFlow<List<MarketPoint>>(emptyList())
    val chartPoints: StateFlow<List<MarketPoint>> = _chartPoints.asStateFlow()

    private val _dualTrendSeries = MutableStateFlow(DualMetalTrendSeries())
    val dualTrendSeries: StateFlow<DualMetalTrendSeries> = _dualTrendSeries.asStateFlow()

    private val _chartLoading = MutableStateFlow(false)
    val chartLoading: StateFlow<Boolean> = _chartLoading.asStateFlow()

    private val _marketHistoryLoading = MutableStateFlow(false)
    val marketHistoryLoading: StateFlow<Boolean> = _marketHistoryLoading.asStateFlow()

    private val _marketHistoryError = MutableStateFlow<String?>(null)
    val marketHistoryError: StateFlow<String?> = _marketHistoryError.asStateFlow()

    private val _mpinUnlocked = MutableStateFlow(false)
    val mpinUnlocked: StateFlow<Boolean> = _mpinUnlocked.asStateFlow()

    private val _walletUnlocked = MutableStateFlow(false)
    val walletUnlocked: StateFlow<Boolean> = _walletUnlocked.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionBanner = MutableStateFlow<ActionBanner?>(null)
    val actionBanner: StateFlow<ActionBanner?> = _actionBanner.asStateFlow()

    private val _recipientLookup = MutableStateFlow<DirectoryEntry?>(null)
    val recipientLookup: StateFlow<DirectoryEntry?> = _recipientLookup.asStateFlow()

    private val _otpState = MutableStateFlow(SecurityOtpState())
    val otpState: StateFlow<SecurityOtpState> = _otpState.asStateFlow()

    val mpinRateLimit: StateFlow<Pair<Int, Long>> = prefs.mpinRateLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0 to 0L)

    val walletBiometricLock: StateFlow<Boolean> = prefs.walletBiometricLockFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val tradeBiometricConfirm: StateFlow<Boolean> = prefs.tradeBiometricConfirmFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    val biometricAppAccess: StateFlow<Boolean> = prefs.biometricAppAccessFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val bgDropMonitor: StateFlow<BgDropMonitorState> = prefs.bgDropMonitorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BgDropMonitorState())

    private fun <T> observeSafe(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> {
        return flow
            .catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = initial,
            )
    }

    val profile: StateFlow<UserProfile?> = observeSafe(repository.observeProfile(currentUserId), null)
    val wallet: StateFlow<WalletHoldings?> = observeSafe(repository.observeWallet(currentUserId), null)
    val ledger: StateFlow<List<WalletLedgerEntry>> = observeSafe(repository.observeLedger(currentUserId), emptyList())
    val transactions: StateFlow<List<TransactionRecord>> = observeSafe(repository.observeTransactions(currentUserId), emptyList())
    val bankAccounts: StateFlow<List<BankAccount>> = observeSafe(repository.observeBankAccounts(currentUserId), emptyList())
    val kycRecord: StateFlow<KycRecord?> = observeSafe(repository.observeKycRecord(currentUserId), null)
    val devices: StateFlow<List<DeviceSession>> = observeSafe(repository.observeDevices(currentUserId), emptyList())
    val loginHistory: StateFlow<List<LoginHistoryEntry>> = observeSafe(repository.observeLoginHistory(currentUserId), emptyList())
    val priceAlerts: StateFlow<List<PriceAlert>> = observeSafe(repository.observePriceAlerts(currentUserId), emptyList())
    val notifications: StateFlow<List<AppNotification>> = observeSafe(repository.observeNotifications(currentUserId), emptyList())
    val announcements: StateFlow<List<Announcement>> = observeSafe(repository.observeAnnouncements(), emptyList())
    val supportTickets: StateFlow<List<SupportTicket>> = observeSafe(repository.observeSupportTickets(currentUserId), emptyList())
    val referrals: StateFlow<List<ReferralRecord>> = observeSafe(repository.observeReferrals(currentUserId), emptyList())
    val outgoingTransfers: StateFlow<List<UserTransfer>> = observeSafe(repository.observeTransfers(currentUserId), emptyList())
    val incomingTransfers: StateFlow<List<UserTransfer>> = observeSafe(repository.observeIncomingTransfers(currentUserId), emptyList())
    val appConfig: StateFlow<AppConfig> = observeSafe(repository.observeAppConfig(), AppConfig())
    val marketHistory30d: StateFlow<List<MarketPriceHistoryEntry>> = repository.observeMarketPriceHistory(30)
        .catch { err ->
            val fs = generateSequence(err) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fs == null && err is CancellationException) throw err
            _marketHistoryError.value = (fs ?: err).localizedMessage ?: "Failed to load Firestore price history"
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _manualGoldSummaryRefresh = MutableStateFlow<FirestoreGoldSummary?>(null)

    val firestoreGoldSummary: StateFlow<FirestoreGoldSummary> = combine(
        repository.observeLiveGoldSummary()
            .catch { err ->
                val fs = generateSequence(err) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fs == null && err is CancellationException) throw err
                emit(
                    FirestoreGoldSummary(
                        isLoading = false,
                        isFromFirestore = false,
                        errorMessage = (fs ?: err).localizedMessage ?: "Failed to fetch live gold rates from Firestore",
                    )
                )
            },
        _manualGoldSummaryRefresh,
    ) { streamSummary, manualSummary ->
        if (manualSummary != null &&
            (!streamSummary.isFromFirestore || manualSummary.lastUpdatedEpochMs > streamSummary.lastUpdatedEpochMs)
        ) {
            manualSummary
        } else {
            streamSummary
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FirestoreGoldSummary(isLoading = true))

    val onboardingSeen: StateFlow<Boolean> = prefs.onboardingSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), true)

    init {
        viewModelScope.launch {
            try {
                val devId = prefs.getOrCreateDeviceId()
                _deviceId.value = devId
                repository.ensureUserProvisioned(currentUserId, currentEmail, currentDisplayName, devId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Failed to sync account", isError = true)
            } finally {
                _isInitializing.value = false
            }
        }

        // Auto-claim incoming P2P transfers atomically
        viewModelScope.launch {
            incomingTransfers.collectLatest { list ->
                val mode = appConfig.value.paymentMode
                list.filter { !it.recipientCredited }.forEach { transfer ->
                    repository.claimIncomingTransfer(transfer, mode)
                }
            }
        }

        // Live bullion feed polling every 20s for real-time trend ticks
        viewModelScope.launch {
            while (isActive) {
                refreshMarketRates()
                delay(20_000L)
            }
        }

        // Re-arm periodic WorkManager gold drop monitor on startup & monitor Firestore gold rate drops in real time
        viewModelScope.launch {
            val savedMonitor = prefs.bgDropMonitorFlow.first()
            if (savedMonitor.enabled && savedMonitor.threshold24kInr > 0.0 && appContext != null) {
                GoldPriceMonitorWorker.schedulePeriodicMonitor(appContext)
            }
        }

        viewModelScope.launch {
            firestoreGoldSummary.collectLatest { summary ->
                if (summary.isFromFirestore && summary.gold24kPerGram > 0.0) {
                    evaluateSettingsDropThreshold(
                        liveRates = _marketRates.value,
                        firestoreSummary = summary,
                        preferFirestore = true,
                        forceNotify = false,
                    )
                }
            }
        }
    }

    fun dismissBanner() {
        _actionBanner.value = null
    }

    fun markOnboardingSeen() {
        viewModelScope.launch { prefs.setOnboardingSeen(true) }
    }

    fun unlockMpinSession(viaBiometric: Boolean = false) {
        _mpinUnlocked.value = true
        viewModelScope.launch {
            prefs.resetMpinFailures()
            repository.recordLoginEvent(
                uid = currentUserId,
                method = if (viaBiometric) "BIOMETRIC_UNLOCK" else "MPIN_UNLOCK",
                status = "SUCCESS",
            )
        }
    }

    fun lockSession() {
        _mpinUnlocked.value = false
        _walletUnlocked.value = false
    }

    fun unlockWalletSession(viaBiometric: Boolean) {
        _walletUnlocked.value = true
        viewModelScope.launch {
            prefs.resetMpinFailures()
            repository.recordLoginEvent(
                uid = currentUserId,
                method = if (viaBiometric) "WALLET_BIOMETRIC_UNLOCK" else "WALLET_MPIN_UNLOCK",
                status = "SUCCESS",
            )
        }
    }

    fun lockWalletSession() {
        _walletUnlocked.value = false
    }

    fun setWalletBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setWalletBiometricLock(enabled)
            if (!enabled) _walletUnlocked.value = true
        }
    }

    fun setTradeBiometricConfirm(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setTradeBiometricConfirm(enabled)
            _actionBanner.value = ActionBanner(
                if (enabled) "Biometric transaction confirmation saved to DataStore."
                else "Biometric transaction confirmation disabled in DataStore."
            )
        }
    }

    fun setBiometricAppAccess(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricAppAccess(enabled)
            prefs.setBiometricVaultUid(if (enabled) currentUserId else null)
            runCatching { repository.updateSecurityAndNotifPrefs(currentUserId, biometricEnabled = enabled) }
            repository.recordLoginEvent(
                uid = currentUserId,
                method = if (enabled) "BIOMETRIC_APP_ACCESS_ON" else "BIOMETRIC_APP_ACCESS_OFF",
                status = "SUCCESS",
            )
            _actionBanner.value = ActionBanner(
                if (enabled) "Biometric app access enabled & saved to DataStore."
                else "Biometric app access disabled in DataStore."
            )
        }
    }

    fun setBiometricAuthenticationMaster(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBiometricAuthenticationSettings(appAccessEnabled = enabled, txConfirmEnabled = enabled)
            prefs.setWalletBiometricLock(enabled)
            prefs.setBiometricVaultUid(if (enabled) currentUserId else null)
            runCatching { repository.updateSecurityAndNotifPrefs(currentUserId, biometricEnabled = enabled) }
            repository.recordLoginEvent(
                uid = currentUserId,
                method = if (enabled) "BIOMETRIC_ENABLED" else "BIOMETRIC_DISABLED",
                status = "SUCCESS",
            )
            _actionBanner.value = ActionBanner(
                if (enabled) "Biometric authentication enabled for app access & transactions."
                else "Biometric authentication disabled in DataStore."
            )
        }
    }

    fun recordTradeBiometricAuth(tradeAction: String) {
        viewModelScope.launch {
            repository.recordLoginEvent(
                uid = currentUserId,
                method = tradeAction.take(38),
                status = "SUCCESS",
            )
        }
    }

    fun verifyMpin(inputPin: String): Boolean {
        val prof = profile.value ?: return false
        if (prof.mpinHash.isBlank() || prof.mpinSalt.isBlank()) return false
        val (_, lockoutUntil) = mpinRateLimit.value
        if (lockoutUntil > System.currentTimeMillis()) return false
        val ok = CryptoUtils.verifySecret(inputPin, prof.mpinSalt, prof.mpinHash)
        viewModelScope.launch {
            if (ok) {
                prefs.resetMpinFailures()
            } else {
                prefs.recordMpinFailure()
            }
        }
        return ok
    }

    fun requestSecurityOtp(purpose: String) {
        val now = System.currentTimeMillis()
        val current = _otpState.value
        if (current.isActive && now < current.resendAvailableAtMs) {
            val waitSec = ((current.resendAvailableAtMs - now) / 1000L).coerceAtLeast(1L)
            _otpState.value = current.copy(errorMessage = "Wait ${waitSec}s before requesting another OTP.")
            return
        }
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        activeOtpCode = code
        val mode = appConfig.value.paymentMode
        val hint = if (mode == "TEST") code else null
        _otpState.value = SecurityOtpState(
            isActive = true,
            purpose = purpose,
            expiresAtMs = now + 120_000L,
            resendAvailableAtMs = now + 30_000L,
            attemptsRemaining = 5,
            sandboxOtpHint = hint,
            errorMessage = null,
        )
        viewModelScope.launch {
            val body = "Your 6-digit SONA-WANE GOLD verification OTP for $purpose is $code. Valid for 2 minutes."
            repository.sendNotification(
                uid = currentUserId,
                type = "SECURITY_OTP",
                title = "Security OTP Verification ($mode)",
                body = body,
            )
            appContext?.let { ctx ->
                BullionNotificationHelper.postSecurityPush(
                    context = ctx,
                    title = "SONA-WANE Security OTP",
                    body = "Your verification code for $purpose: $code (expires in 2 mins).",
                )
            }
        }
    }

    fun verifySecurityOtp(inputCode: String): Boolean {
        val now = System.currentTimeMillis()
        val st = _otpState.value
        if (!st.isActive || activeOtpCode == null) {
            _otpState.value = st.copy(errorMessage = "Request a new OTP first.")
            return false
        }
        if (now > st.expiresAtMs) {
            activeOtpCode = null
            _otpState.value = st.copy(isActive = false, errorMessage = "OTP expired. Please resend a fresh code.")
            return false
        }
        if (st.attemptsRemaining <= 0) {
            activeOtpCode = null
            _otpState.value = st.copy(isActive = false, errorMessage = "Too many failed OTP attempts. Request a new OTP.")
            return false
        }
        if (inputCode.trim() == activeOtpCode) {
            activeOtpCode = null
            _otpState.value = SecurityOtpState()
            viewModelScope.launch {
                prefs.resetMpinFailures()
                repository.recordLoginEvent(currentUserId, "OTP_VERIFIED", "SUCCESS")
            }
            return true
        }
        val left = st.attemptsRemaining - 1
        _otpState.value = st.copy(
            attemptsRemaining = left,
            errorMessage = if (left > 0) "Invalid OTP. $left attempts remaining." else "OTP locked. Request a new OTP.",
            isActive = left > 0,
        )
        if (left <= 0) activeOtpCode = null
        return false
    }

    fun cancelSecurityOtp() {
        activeOtpCode = null
        _otpState.value = SecurityOtpState()
    }

    fun setupMpin(newPin: String, onDone: () -> Unit = {}) {
        if (newPin.length != 4 || newPin.any { !it.isDigit() }) {
            _actionBanner.value = ActionBanner("MPIN must be 4 digits", isError = true)
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.saveMpin(currentUserId, newPin)
                prefs.resetMpinFailures()
                _mpinUnlocked.value = true
                repository.recordLoginEvent(currentUserId, "MPIN_CONFIGURED", "SUCCESS")
                _actionBanner.value = ActionBanner("4-digit MPIN configured securely")
                onDone()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Failed to save MPIN", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun refreshMarketRates() {
        viewModelScope.launch {
            val cfg = appConfig.value
            val live = marketService.fetchLiveRates(cfg.goldSpreadPercent, cfg.silverSpreadPercent)
            if (live.isLive) {
                val prev = previousRates ?: _marketRates.value.takeIf { it.gold24kBuyPerGram > 0.0 }
                _marketRates.value = live
                prefs.cacheRates(live.gold24kBuyPerGram, live.silver999BuyPerGram, live.sourceName, live.lastUpdatedEpochMs)
                evaluatePriceAlerts(live, prev)
                evaluateSettingsDropThreshold(
                    liveRates = live,
                    firestoreSummary = firestoreGoldSummary.value,
                    preferFirestore = false,
                    forceNotify = false,
                )
                previousRates = live
                if (_chartPoints.value.isEmpty()) {
                    loadMarketChart(isGold = true, range = "30D")
                }
                if (_dualTrendSeries.value.goldPoints.isEmpty()) {
                    loadDualTrend(_dualTrendSeries.value.rangeKey)
                } else {
                    _dualTrendSeries.value = marketService.updateDualSeriesWithLiveTick(
                        existing = _dualTrendSeries.value,
                        goldSpot = live.gold24kBuyPerGram,
                        silverSpot = live.silver999BuyPerGram,
                    )
                }
                if (marketHistory30d.value.size < 30) {
                    syncFirestoreMarketHistory(force = false)
                }
            } else {
                val (cachedG, cachedS, cachedTs) = prefs.cachedRatesFlow.first()
                if (cachedG > 0.0 && cachedS > 0.0) {
                    _marketRates.value = live.copy(
                        gold24kBuyPerGram = cachedG,
                        gold24kSellPerGram = cachedG * 0.975,
                        gold22kBuyPerGram = cachedG * (22.0 / 24.0),
                        gold18kBuyPerGram = cachedG * (18.0 / 24.0),
                        silver999BuyPerGram = cachedS,
                        silver999SellPerGram = cachedS * 0.97,
                        isLive = false,
                        isDelayed = true,
                        sourceName = "Delayed / Cached Verified Snapshot",
                        lastUpdatedEpochMs = cachedTs,
                    )
                    if (marketHistory30d.value.size < 30) {
                        syncFirestoreMarketHistory(force = false)
                    }
                } else {
                    _marketRates.value = live
                }
            }
        }
    }

    fun syncFirestoreMarketHistory(force: Boolean = true) {
        viewModelScope.launch {
            if (_marketHistoryLoading.value) return@launch
            _marketHistoryLoading.value = true
            _marketHistoryError.value = null
            try {
                var rates = _marketRates.value
                if (rates.gold24kBuyPerGram <= 0.0 || rates.silver999BuyPerGram <= 0.0) {
                    val cfg = appConfig.value
                    val fetched = marketService.fetchLiveRates(cfg.goldSpreadPercent, cfg.silverSpreadPercent)
                    if (fetched.isLive) {
                        _marketRates.value = fetched
                        rates = fetched
                    }
                }
                if (rates.gold24kBuyPerGram > 0.0 && rates.silver999BuyPerGram > 0.0 &&
                    (force || marketHistory30d.value.size < 30)
                ) {
                    val snapshots = marketService.build30DayHistorySnapshots(
                        goldCurrentPerGram = rates.gold24kBuyPerGram,
                        silverCurrentPerGram = rates.silver999BuyPerGram,
                    )
                    repository.seedMissingMarketHistory(snapshots)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _marketHistoryError.value = e.localizedMessage ?: "Unable to sync 30-day Firestore history"
            } finally {
                _marketHistoryLoading.value = false
            }
        }
    }

    fun refreshFirestoreGoldSummary() {
        viewModelScope.launch {
            _manualGoldSummaryRefresh.value = _manualGoldSummaryRefresh.value?.copy(isLoading = true, errorMessage = null)
                ?: firestoreGoldSummary.value.copy(isLoading = true, errorMessage = null)
            try {
                refreshMarketRates()
                val fetched = repository.fetchLatestGoldSummaryOnce()
                _manualGoldSummaryRefresh.value = fetched
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val existing = firestoreGoldSummary.value
                _manualGoldSummaryRefresh.value = existing.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Failed to fetch gold rates from Firestore",
                )
            }
        }
    }

    fun loadMarketChart(isGold: Boolean, range: String) {
        viewModelScope.launch {
            _chartLoading.value = true
            val currentRate = if (isGold) _marketRates.value.gold24kBuyPerGram else _marketRates.value.silver999BuyPerGram
            _chartPoints.value = marketService.fetchHistoricalTrend(currentRate, range)
            val rates = _marketRates.value
            if (rates.gold24kBuyPerGram > 0.0 && rates.silver999BuyPerGram > 0.0) {
                _dualTrendSeries.value = marketService.fetchDualMetalTrend(
                    goldCurrentPerGram = rates.gold24kBuyPerGram,
                    silverCurrentPerGram = rates.silver999BuyPerGram,
                    rangeKey = range,
                )
            }
            _chartLoading.value = false
        }
    }

    fun loadDualTrend(range: String = _dualTrendSeries.value.rangeKey) {
        viewModelScope.launch {
            val rates = _marketRates.value
            if (rates.gold24kBuyPerGram > 0.0 && rates.silver999BuyPerGram > 0.0) {
                _chartLoading.value = true
                _dualTrendSeries.value = marketService.fetchDualMetalTrend(
                    goldCurrentPerGram = rates.gold24kBuyPerGram,
                    silverCurrentPerGram = rates.silver999BuyPerGram,
                    rangeKey = range,
                )
                _chartLoading.value = false
            }
        }
    }

    fun spotPriceForMetal(rates: LiveMarketRates, metal: String): Double {
        return when (metal) {
            "GOLD_24K" -> rates.gold24kBuyPerGram
            "GOLD_22K" -> rates.gold22kBuyPerGram
            "GOLD_18K" -> rates.gold18kBuyPerGram
            else -> rates.silver999BuyPerGram
        }
    }

    fun checkAlertsNow() {
        viewModelScope.launch {
            val current = _marketRates.value
            if (current.gold24kBuyPerGram <= 0.0 && current.silver999BuyPerGram <= 0.0) {
                refreshMarketRates()
            } else {
                val triggered = evaluatePriceAlerts(current, previousRates)
                _actionBanner.value = ActionBanner(
                    if (triggered > 0) "$triggered price alert(s) triggered & pushed!" else "All active alerts checked against live spot rates."
                )
            }
        }
    }

    fun configureBackgroundDropMonitor(
        enabled: Boolean,
        threshold24kInr: Double,
        purity: String = "GOLD_24K",
    ) {
        viewModelScope.launch {
            prefs.setBgDropMonitorConfig(enabled, threshold24kInr, purity)
            val ctx = appContext
            if (ctx != null) {
                if (enabled && threshold24kInr > 0.0) {
                    GoldPriceMonitorWorker.schedulePeriodicMonitor(ctx)
                } else {
                    GoldPriceMonitorWorker.cancelPeriodicMonitor(ctx)
                }
            }
            val label = GoldPriceMonitorService.purityLabel(purity)
            _actionBanner.value = ActionBanner(
                if (enabled) "$label monitor armed below ${Formatters.formatInr(threshold24kInr)}/g."
                else "Gold price drop monitor paused."
            )
            if (enabled && threshold24kInr > 0.0) {
                evaluateSettingsDropThreshold(
                    liveRates = _marketRates.value,
                    firestoreSummary = firestoreGoldSummary.value,
                    preferFirestore = false,
                    forceNotify = false,
                )
            }
        }
    }

    fun runBackgroundDropCheckNow() {
        viewModelScope.launch {
            val ctx = appContext
            if (ctx != null) {
                GoldPriceMonitorWorker.runImmediateCheck(ctx)
                val outcome = GoldPriceMonitorService(ctx, prefs, marketService)
                    .performBackgroundCheck(
                        isManual = true,
                        playSound = profile.value?.notifSoundEnabled ?: true,
                        vibrate = profile.value?.notifVibrationEnabled ?: true,
                    )
                if (outcome.outcome.shouldNotify) {
                    runCatching {
                        repository.sendNotification(
                            uid = currentUserId,
                            type = "PRICE_ALERT",
                            title = outcome.outcome.alertTitle,
                            body = outcome.outcome.alertMessage,
                            referenceId = "settings_drop_monitor",
                        )
                    }
                    _actionBanner.value = ActionBanner(outcome.outcome.alertMessage)
                } else if (outcome.outcome.liveRateInr > 0.0 && outcome.outcome.thresholdInr > 0.0) {
                    val label = GoldPriceMonitorService.purityLabel(outcome.outcome.purity)
                    _actionBanner.value = ActionBanner(
                        "Checked $label: ${Formatters.formatInr(outcome.outcome.liveRateInr)}/g (threshold ${Formatters.formatInr(outcome.outcome.thresholdInr)}/g)."
                    )
                } else {
                    _actionBanner.value = ActionBanner("Set a positive gold threshold in Settings first.", isError = true)
                }
            } else {
                evaluateSettingsDropThreshold(
                    liveRates = _marketRates.value,
                    firestoreSummary = firestoreGoldSummary.value,
                    preferFirestore = false,
                    forceNotify = true,
                )
            }
        }
    }

    fun dismissInAppGoldDropAlert() {
        viewModelScope.launch {
            prefs.dismissPendingDropAlert()
        }
    }

    private suspend fun evaluateSettingsDropThreshold(
        liveRates: LiveMarketRates,
        firestoreSummary: FirestoreGoldSummary?,
        preferFirestore: Boolean = false,
        forceNotify: Boolean = false,
    ) {
        val config = prefs.bgDropMonitorFlow.first()
        if (!config.enabled && !forceNotify) return
        if (config.threshold24kInr <= 0.0) return

        val observedRate = GoldPriceMonitorService.resolveMonitoredGoldRate(
            purity = config.purity,
            liveRates = liveRates,
            firestoreSummary = firestoreSummary,
            preferFirestore = preferFirestore,
        )
        if (observedRate <= 0.0) return

        val prof = profile.value
        val sound = prof?.notifSoundEnabled ?: true
        val vibrate = prof?.notifVibrationEnabled ?: true
        val ctx = appContext ?: return
        val result = GoldPriceMonitorService(ctx, prefs, marketService).evaluateAndDispatch(
            config = config,
            observedRateInr = observedRate,
            playSound = sound,
            vibrate = vibrate,
            forceNotify = forceNotify,
        )
        if (result.outcome.shouldNotify) {
            runCatching {
                repository.sendNotification(
                    uid = currentUserId,
                    type = "PRICE_ALERT",
                    title = result.outcome.alertTitle,
                    body = result.outcome.alertMessage,
                    referenceId = "settings_drop_monitor",
                )
            }
        }
    }

    private suspend fun evaluatePriceAlerts(rates: LiveMarketRates, prevRates: LiveMarketRates?): Int {
        val prof = profile.value
        if (prof != null && !prof.notifPriceAlerts) return 0
        val nowMs = System.currentTimeMillis()
        val alerts = priceAlerts.value.filter { it.isActive }
        var firedCount = 0

        for (alert in alerts) {
            val current = spotPriceForMetal(rates, alert.metal)
            if (current <= 0.0) continue

            val prevPrice = prevRates?.let { spotPriceForMetal(it, alert.metal) }
                ?.takeIf { it > 0.0 }
                ?: alert.lastObservedPrice.takeIf { it > 0.0 }
                ?: current

            val conditionMet = when (alert.direction) {
                "ABOVE" -> current >= alert.targetPriceInr
                "BELOW" -> current <= alert.targetPriceInr
                "CROSSES" -> {
                    val crossedUp = prevPrice < alert.targetPriceInr && current >= alert.targetPriceInr
                    val crossedDown = prevPrice > alert.targetPriceInr && current <= alert.targetPriceInr
                    val nearTarget = abs(current - alert.targetPriceInr) <= (alert.targetPriceInr * 0.0008)
                    crossedUp || crossedDown || nearTarget
                }
                else -> false
            }

            if (!conditionMet) {
                if (abs(current - alert.lastObservedPrice) > 0.01) {
                    repository.updateAlertObservedPrice(currentUserId, alert.id, current)
                }
                continue
            }

            val lastTriggeredMs = alert.triggeredAt?.toDate()?.time ?: 0L
            val cooldownOk = when (alert.frequency) {
                "ONCE" -> alert.triggerCount == 0
                "EVERY_HOUR" -> lastTriggeredMs == 0L || (nowMs - lastTriggeredMs) >= 3_600_000L
                "DAILY" -> lastTriggeredMs == 0L || (nowMs - lastTriggeredMs) >= 86_400_000L
                "ALWAYS" -> lastTriggeredMs == 0L || (nowMs - lastTriggeredMs) >= 300_000L
                else -> true
            }

            if (cooldownOk) {
                val metalLabel = alert.metal.replace("_", " ")
                val dirLabel = when (alert.direction) {
                    "ABOVE" -> "rose above"
                    "BELOW" -> "dropped below"
                    else -> "crossed"
                }
                val noteSuffix = if (alert.notes.isNotBlank()) " • Note: ${alert.notes}" else ""
                val title = "$metalLabel Target Hit!"
                val body = "$metalLabel $dirLabel ${Formatters.formatInr(alert.targetPriceInr)}/g — Live spot is ${Formatters.formatInr(current)}/g.$noteSuffix"

                repository.sendNotification(
                    uid = currentUserId,
                    type = "PRICE_ALERT",
                    title = title,
                    body = body,
                    referenceId = alert.id,
                )
                appContext?.let { ctx ->
                    BullionNotificationHelper.postPriceAlertPush(
                        context = ctx,
                        title = title,
                        body = body,
                        playSound = prof?.notifSoundEnabled ?: true,
                        vibrate = prof?.notifVibrationEnabled ?: true,
                        notificationId = alert.id.hashCode(),
                    )
                }
                repository.markPriceAlertTriggered(currentUserId, alert, current)
                firedCount++
            }
        }
        return firedCount
    }

    fun depositMoney(amountInr: Double, method: String, simulateFailure: Boolean = false) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val tx = repository.depositMoney(amountInr, method, appConfig.value.paymentMode, simulateFailure)
                if (tx.status == "FAILED") {
                    _actionBanner.value = ActionBanner("Payment failed in ${tx.mode} mode.", isError = true, receiptTx = tx)
                } else {
                    _actionBanner.value = ActionBanner("${Formatters.formatInr(amountInr)} credited to wallet.", receiptTx = tx)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Deposit failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun buyBullion(
        isGold: Boolean,
        purity: String,
        grams: Double,
        ratePerGram: Double,
        baseAmount: Double,
        gstAmount: Double,
        chargesAmount: Double,
        totalAmount: Double,
        paymentMethod: String,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val tx = repository.buyMetal(
                    isGold = isGold,
                    purity = purity,
                    grams = grams,
                    ratePerGram = ratePerGram,
                    baseAmount = baseAmount,
                    gstAmount = gstAmount,
                    chargesAmount = chargesAmount,
                    totalAmount = totalAmount,
                    paymentMethod = paymentMethod,
                    mode = appConfig.value.paymentMode,
                )
                _actionBanner.value = ActionBanner(
                    "Bought ${Formatters.formatGrams(grams)} $purity ${if (isGold) "Gold" else "Silver"}!",
                    receiptTx = tx,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Purchase failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun sellBullion(
        isGold: Boolean,
        purity: String,
        grams: Double,
        sellRatePerGram: Double,
        deductionsInr: Double,
        creditToWallet: Boolean,
        bankAccount: BankAccount?,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val tx = repository.sellMetal(
                    isGold = isGold,
                    purity = purity,
                    grams = grams,
                    sellRatePerGram = sellRatePerGram,
                    deductionsInr = deductionsInr,
                    creditToWallet = creditToWallet,
                    bankAccount = bankAccount,
                    mode = appConfig.value.paymentMode,
                )
                _actionBanner.value = ActionBanner(
                    "Sold ${Formatters.formatGrams(grams)} $purity for ${Formatters.formatInr(tx.totalAmount)}",
                    receiptTx = tx,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Sale failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun addBankAccount(holderName: String, bankName: String, accNo: String, ifsc: String, isPrimary: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.addBankAccount(currentUserId, holderName, bankName, accNo, ifsc, isPrimary)
                _actionBanner.value = ActionBanner("Bank account verified & linked.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Failed to add bank account", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun setPrimaryBank(bankId: String) {
        viewModelScope.launch {
            runCatching { repository.setPrimaryBankAccount(currentUserId, bankId) }
                .onSuccess { _actionBanner.value = ActionBanner("Primary bank account updated.") }
                .onFailure { _actionBanner.value = ActionBanner(it.localizedMessage ?: "Update failed", isError = true) }
        }
    }

    fun removeBank(bankId: String) {
        viewModelScope.launch {
            runCatching { repository.removeBankAccount(currentUserId, bankId) }
                .onSuccess { _actionBanner.value = ActionBanner("Bank account removed.") }
                .onFailure { _actionBanner.value = ActionBanner(it.localizedMessage ?: "Removal failed", isError = true) }
        }
    }

    fun requestWithdrawal(amountInr: Double, bankAccount: BankAccount) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val tx = repository.requestWithdrawal(
                    amountInr = amountInr,
                    feeInr = appConfig.value.withdrawalFeeInr,
                    bankAccount = bankAccount,
                    mode = appConfig.value.paymentMode,
                )
                _actionBanner.value = ActionBanner("Withdrawal request submitted (${tx.txCode}).", receiptTx = tx)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Withdrawal failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun lookupRecipient(query: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val found = repository.lookupRecipient(query)
                _recipientLookup.value = found
                if (found == null) {
                    _actionBanner.value = ActionBanner("No verified user found for '$query'", isError = true)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Lookup error", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun clearRecipient() {
        _recipientLookup.value = null
    }

    fun executeTransfer(recipient: DirectoryEntry, assetType: String, quantity: Double) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val senderName = profile.value?.fullName ?: "Sona-Wane User"
                val fee = if (assetType == "INR") quantity * (appConfig.value.transferFeePercent / 100.0) else 0.0
                val trf = repository.executeTransfer(
                    senderName = senderName,
                    recipient = recipient,
                    assetType = assetType,
                    quantity = quantity,
                    feeInr = fee,
                    mode = appConfig.value.paymentMode,
                )
                _recipientLookup.value = null
                _actionBanner.value = ActionBanner("Transfer ${trf.transferCode} sent to ${recipient.displayName}!")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Transfer failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateProfile(
        fullName: String,
        mobile: String,
        countryCode: String,
        email: String,
        dob: String,
        address: String,
        state: String,
        city: String,
        pincode: String,
        pan: String,
        photoUri: String,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.updateProfileDetails(
                    currentUserId, fullName, mobile, countryCode, email, dob, address, state, city, pincode, pan, photoUri
                )
                _actionBanner.value = ActionBanner("Profile details updated.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Profile update failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun submitKyc(
        fullName: String,
        mobile: String,
        pan: String,
        aadhaarLast4: String,
        docType: String,
        docUri: String?,
        address: String,
        state: String,
        city: String,
        pincode: String,
        documentFrontBase64: String? = null,
        documentBackBase64: String? = null,
        captureMethod: String = "CAMERAX_SCAN",
        scanQualityScore: Int = 0,
        declarationAccepted: Boolean = true,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.submitKyc(
                    uid = currentUserId,
                    userName = fullName,
                    mobileNumber = mobile,
                    panNumber = pan,
                    aadhaarLast4 = aadhaarLast4,
                    documentType = docType,
                    documentRefUri = docUri,
                    addressLine = address,
                    state = state,
                    city = city,
                    pincode = pincode,
                    documentFrontBase64 = documentFrontBase64,
                    documentBackBase64 = documentBackBase64,
                    captureMethod = captureMethod,
                    scanQualityScore = scanQualityScore,
                    declarationAccepted = declarationAccepted,
                )
                _actionBanner.value = ActionBanner("KYC & ID scan submitted to Firestore for admin review.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "KYC submission failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updatePreferences(
        biometric: Boolean? = null,
        sound: Boolean? = null,
        vibration: Boolean? = null,
        priceAlerts: Boolean? = null,
        announcements: Boolean? = null,
    ) {
        viewModelScope.launch {
            repository.updateSecurityAndNotifPrefs(currentUserId, biometric, sound, vibration, priceAlerts, announcements)
            if (biometric != null) {
                prefs.setBiometricAppAccess(biometric)
                prefs.setBiometricVaultUid(if (biometric) currentUserId else null)
                repository.recordLoginEvent(
                    uid = currentUserId,
                    method = if (biometric) "BIOMETRIC_ENABLED" else "BIOMETRIC_DISABLED",
                    status = "SUCCESS",
                )
                _actionBanner.value = ActionBanner(
                    if (biometric) "Biometric login (Fingerprint / Face) enabled." else "Biometric login disabled."
                )
            }
        }
    }

    fun revokeOtherDevices() {
        viewModelScope.launch {
            runCatching { repository.revokeOtherDevices(currentUserId, _deviceId.value) }
                .onSuccess { _actionBanner.value = ActionBanner("All other devices logged out.") }
                .onFailure { _actionBanner.value = ActionBanner(it.localizedMessage ?: "Failed", isError = true) }
        }
    }

    fun createPriceAlert(
        metal: String,
        targetPrice: Double,
        direction: String,
        frequency: String = "ONCE",
        notes: String = "",
    ) {
        viewModelScope.launch {
            val currentSpot = spotPriceForMetal(_marketRates.value, metal)
            runCatching {
                repository.createPriceAlert(
                    uid = currentUserId,
                    metal = metal,
                    targetPriceInr = targetPrice,
                    direction = direction,
                    frequency = frequency,
                    notes = notes,
                    currentObservedPrice = currentSpot,
                )
            }
                .onSuccess {
                    _actionBanner.value = ActionBanner("Price alert armed (${metal.replace("_", " ")} • $frequency).")
                }
                .onFailure {
                    _actionBanner.value = ActionBanner(it.localizedMessage ?: "Failed to create alert", isError = true)
                }
        }
    }

    fun updatePriceAlert(
        alertId: String,
        targetPrice: Double,
        direction: String,
        frequency: String,
        isActive: Boolean,
        notes: String,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.updatePriceAlert(currentUserId, alertId, targetPrice, direction, frequency, isActive, notes)
            }
                .onSuccess { _actionBanner.value = ActionBanner("Price alert updated.") }
                .onFailure { _actionBanner.value = ActionBanner(it.localizedMessage ?: "Update failed", isError = true) }
        }
    }

    fun togglePriceAlert(alert: PriceAlert, active: Boolean) {
        viewModelScope.launch {
            runCatching { repository.togglePriceAlert(currentUserId, alert.id, active) }
                .onSuccess {
                    _actionBanner.value = ActionBanner(if (active) "Alert resumed." else "Alert paused.")
                }
                .onFailure { _actionBanner.value = ActionBanner(it.localizedMessage ?: "Failed", isError = true) }
        }
    }

    fun deletePriceAlert(alertId: String) {
        viewModelScope.launch {
            runCatching { repository.deletePriceAlert(currentUserId, alertId) }
                .onSuccess { _actionBanner.value = ActionBanner("Price alert deleted.") }
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch { repository.markNotificationRead(currentUserId, notifId) }
    }

    fun deleteNotification(notifId: String) {
        viewModelScope.launch { repository.deleteNotification(currentUserId, notifId) }
    }

    fun clearNotifications() {
        viewModelScope.launch { repository.clearAllNotifications(currentUserId) }
    }

    fun createSupportTicket(category: String, subject: String, description: String, attachmentUri: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val code = repository.createSupportTicket(
                    currentUserId, profile.value?.fullName ?: "Customer", category, subject, description, attachmentUri
                )
                _actionBanner.value = ActionBanner("Support ticket $code created.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Ticket creation failed", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun replySupportTicket(ticket: SupportTicket, text: String, closeTicket: Boolean = false) {
        viewModelScope.launch {
            runCatching {
                repository.replyToTicket(ticket, "USER", profile.value?.fullName ?: "Customer", text, closeTicket)
            }.onFailure {
                _actionBanner.value = ActionBanner(it.localizedMessage ?: "Reply failed", isError = true)
            }
        }
    }

    fun applyReferralCode(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                repository.applyReferralCode(
                    currentUserId,
                    profile.value?.fullName ?: "Customer",
                    code,
                    appConfig.value.referralRewardInr,
                )
                _actionBanner.value = ActionBanner("Referral code linked!")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _actionBanner.value = ActionBanner(e.localizedMessage ?: "Invalid referral code", isError = true)
            } finally {
                _isBusy.value = false
            }
        }
    }
}
