package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.model.Announcement
import com.example.data.model.AppConfig
import com.example.data.model.AppNotification
import com.example.data.model.BankAccount
import com.example.data.model.DeviceSession
import com.example.data.model.DirectoryEntry
import com.example.data.model.FirestoreGoldSummary
import com.example.data.model.KycRecord
import com.example.data.model.LiveMarketRates
import com.example.data.model.LoginHistoryEntry
import com.example.data.model.MarketPriceHistoryEntry
import com.example.data.model.PriceAlert
import com.example.data.model.ReferralRecord
import com.example.data.model.SupportTicket
import com.example.data.model.TransactionRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserTransfer
import com.example.data.model.WalletHoldings
import com.example.data.model.WalletLedgerEntry
import com.example.data.repository.FirestoreService.toObjectSafely
import com.example.data.repository.FirestoreService.toObjectsSafely
import com.example.util.CryptoUtils
import com.example.util.Formatters
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SonaWaneRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirestoreService.getAuth(),
) {
    constructor(context: Context) : this(
        db = FirestoreService.getDatabase(context),
        auth = FirestoreService.getAuth(context),
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.withFirestoreErrorLogging(op: OperationType, path: String, uid: String): Flow<T> {
        return catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }
    }

    // --- BOOTSTRAP & PROFILE ---

    suspend fun ensureUserProvisioned(
        uid: String,
        email: String?,
        displayName: String?,
        deviceId: String,
    ): UserProfile {
        val userRef = db.collection("users").document(uid)
        val walletRef = userRef.collection("wallets").document("primary")
        return try {
            val snap = userRef.get().await()
            val profile = if (!snap.exists()) {
                val customId = CryptoUtils.generateCustomUserId(uid)
                val name = displayName?.takeIf { it.isNotBlank() } ?: "Gold Investor"
                val refCode = CryptoUtils.generateReferralCode(name, uid)
                val qrToken = CryptoUtils.buildQrPayload(customId, name)
                val created = UserProfile(
                    userId = uid,
                    customUserId = customId,
                    fullName = name,
                    email = email.orEmpty(),
                    kycStatus = "NOT_STARTED",
                    accountStatus = "ACTIVE",
                    referralCode = refCode,
                    qrToken = qrToken,
                )
                userRef.set(created.toCreateMap()).await()
                val walletPayload = mapOf(
                    "userId" to uid,
                    "availableInr" to 0.0,
                    "pendingInr" to 0.0,
                    "lockedInr" to 0.0,
                    "gold24kGrams" to 0.0,
                    "gold22kGrams" to 0.0,
                    "gold18kGrams" to 0.0,
                    "silver999Grams" to 0.0,
                    "totalInvestedGoldInr" to 0.0,
                    "totalInvestedSilverInr" to 0.0,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                walletRef.set(walletPayload).await()
                publishDirectoryEntry(uid, name, customId, "", false)
                sendNotification(
                    uid = uid,
                    type = "ANNOUNCEMENT",
                    title = "Welcome to SONA-WANE GOLD",
                    body = "Your digital bullion vault ($customId) is ready. Complete KYC & set your MPIN to unlock full trading.",
                )
                userRef.get().await().toObjectSafely<UserProfile>() ?: created
            } else {
                val existing = snap.toObjectSafely<UserProfile>() ?: UserProfile(
                    id = snap.id,
                    userId = snap.getString("userId").orEmpty().ifBlank { uid },
                    customUserId = snap.getString("customUserId").orEmpty().ifBlank { CryptoUtils.generateCustomUserId(uid) },
                    fullName = snap.getString("fullName").orEmpty().ifBlank { displayName?.takeIf { it.isNotBlank() } ?: "Gold Investor" },
                    email = snap.getString("email").orEmpty().ifBlank { email.orEmpty() },
                    kycStatus = snap.getString("kycStatus").orEmpty().ifBlank { "NOT_STARTED" },
                    accountStatus = snap.getString("accountStatus").orEmpty().ifBlank { "ACTIVE" },
                )
                if (!walletRef.get().await().exists()) {
                    walletRef.set(
                        mapOf(
                            "userId" to uid,
                            "availableInr" to 0.0,
                            "pendingInr" to 0.0,
                            "lockedInr" to 0.0,
                            "gold24kGrams" to 0.0,
                            "gold22kGrams" to 0.0,
                            "gold18kGrams" to 0.0,
                            "silver999Grams" to 0.0,
                            "totalInvestedGoldInr" to 0.0,
                            "totalInvestedSilverInr" to 0.0,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                }
                existing
            }

            // Record device & login history
            recordDeviceAndLogin(uid, deviceId)
            profile
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            throw e
        }
    }

    private suspend fun publishDirectoryEntry(
        uid: String,
        displayName: String,
        customUserId: String,
        mobileNumber: String,
        kycApproved: Boolean,
    ) {
        val entry = mapOf(
            "uid" to uid,
            "displayName" to displayName.ifBlank { "Gold Investor" },
            "customUserId" to customUserId,
            "mobileLast4" to mobileNumber.filter { it.isDigit() }.takeLast(4),
            "kycApproved" to kycApproved,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        db.collection("user_directory").document(customUserId).set(entry).await()
        val cleanMobile = mobileNumber.filter { it.isDigit() }
        if (cleanMobile.length >= 10) {
            db.collection("user_directory").document("MOB_$cleanMobile").set(entry).await()
        }
    }

    private suspend fun recordDeviceAndLogin(uid: String, deviceId: String) {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(90)
        val os = "Android ${Build.VERSION.RELEASE}".take(38)
        val devRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        val devSnap = devRef.get().await()
        val devMap = mutableMapOf<String, Any>(
            "userId" to uid,
            "deviceName" to model,
            "deviceModel" to Build.MODEL.take(90),
            "osVersion" to os,
            "sessionActive" to true,
            "lastActiveAt" to FieldValue.serverTimestamp(),
        )
        if (!devSnap.exists()) {
            devMap["createdAt"] = FieldValue.serverTimestamp()
        } else {
            devMap["createdAt"] = devSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        }
        devRef.set(devMap).await()

        val loginId = "login_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        db.collection("users").document(uid).collection("login_history").document(loginId).set(
            mapOf(
                "userId" to uid,
                "deviceName" to model,
                "status" to "SUCCESS",
                "method" to "GOOGLE_SIGN_IN",
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun recordLoginEvent(uid: String, method: String, status: String = "SUCCESS") {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(90).ifBlank { "Android Device" }
        val loginId = "login_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("users").document(uid).collection("login_history").document(loginId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "deviceName" to model,
                    "status" to status,
                    "method" to method,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    fun observeProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObjectSafely<UserProfile>() }
                .withFirestoreErrorLogging(OperationType.GET, path, uid)
        )
    }

    suspend fun updateProfileDetails(
        uid: String,
        fullName: String,
        mobileNumber: String,
        countryCode: String,
        email: String,
        dob: String,
        address: String,
        state: String,
        city: String,
        pincode: String,
        panNumber: String,
        photoUri: String,
    ) {
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            val customUserId = snap.getString("customUserId") ?: CryptoUtils.generateCustomUserId(uid)
            val kycApproved = snap.getString("kycStatus") == "APPROVED"
            val updates = mutableMapOf<String, Any>(
                "fullName" to fullName.trim(),
                "mobileNumber" to mobileNumber.trim(),
                "countryCode" to countryCode.trim().ifBlank { "+91" },
                "email" to email.trim(),
                "dob" to dob.trim(),
                "address" to address.trim(),
                "state" to state.trim(),
                "city" to city.trim(),
                "pincode" to pincode.trim(),
                "qrToken" to CryptoUtils.buildQrPayload(customUserId, fullName.trim()),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (panNumber.isNotBlank()) {
                updates["panMasked"] = Formatters.maskPan(panNumber)
            }
            if (photoUri.isNotBlank()) {
                updates["photoUri"] = photoUri
            }
            docRef.update(updates).await()
            publishDirectoryEntry(uid, fullName.trim(), customUserId, mobileNumber.trim(), kycApproved)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun saveMpin(uid: String, rawMpin: String) {
        val salt = CryptoUtils.generateSalt()
        val hash = CryptoUtils.hashSecret(rawMpin, salt)
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "mpinHash" to hash,
                    "mpinSalt" to salt,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            sendNotification(uid, "SECURITY_ALERT", "MPIN Updated", "Your 4-digit transaction MPIN was updated securely.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateSecurityAndNotifPrefs(
        uid: String,
        biometricEnabled: Boolean? = null,
        soundEnabled: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        priceAlertsEnabled: Boolean? = null,
        announcementsEnabled: Boolean? = null,
    ) {
        val docRef = db.collection("users").document(uid)
        try {
            val map = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            biometricEnabled?.let { map["biometricEnabled"] = it }
            soundEnabled?.let { map["notifSoundEnabled"] = it }
            vibrationEnabled?.let { map["notifVibrationEnabled"] = it }
            priceAlertsEnabled?.let { map["notifPriceAlerts"] = it }
            announcementsEnabled?.let { map["notifAnnouncements"] = it }
            docRef.update(map).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- WALLET & LEDGER ---

    fun observeWallet(uid: String): Flow<WalletHoldings?> = flow {
        val path = "users/$uid/wallets/primary"
        emitAll(
            db.collection("users").document(uid).collection("wallets").document("primary")
                .snapshots()
                .map { it.toObjectSafely<WalletHoldings>() }
                .withFirestoreErrorLogging(OperationType.GET, path, uid)
        )
    }

    fun observeLedger(uid: String): Flow<List<WalletLedgerEntry>> = flow {
        val path = "users/$uid/ledger"
        emitAll(
            db.collection("users").document(uid).collection("ledger")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { it.toObjectsSafely<WalletLedgerEntry>() }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeTransactions(uid: String): Flow<List<TransactionRecord>> = flow {
        val path = "transactions"
        emitAll(
            db.collection("transactions")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<TransactionRecord>()
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeRecentBullionTrades(uid: String, limit: Int = 25): Flow<List<TransactionRecord>> =
        observeTransactions(uid).map { all -> filterRecentBullionTrades(all, limit) }

    suspend fun fetchRecentBullionTrades(uid: String, limit: Int = 25): List<TransactionRecord> {
        val path = "transactions"
        return try {
            val snap = db.collection("transactions")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            val items = snap.toObjectsSafely<TransactionRecord>()
            filterRecentBullionTrades(items, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    fun observeAppConfig(): Flow<AppConfig> = flow {
        val uid = requireUserId()
        val path = "app_config/settings"
        emitAll(
            db.collection("app_config").document("settings")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObjectSafely<AppConfig>() ?: AppConfig()
                    } else {
                        AppConfig()
                    }
                }
                .withFirestoreErrorLogging(OperationType.GET, path, uid)
        )
    }

    // --- ATOMIC WALLET DEPOSIT ---

    suspend fun depositMoney(
        amountInr: Double,
        paymentMethod: String,
        mode: String,
        simulateFailure: Boolean = false,
    ): TransactionRecord {
        val uid = requireUserId()
        require(amountInr > 0.0) { "Amount must be greater than zero" }
        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val txCode = CryptoUtils.generateCode("SWG-DEP")
        val orderId = CryptoUtils.generateCode("ORD")
        val refId = CryptoUtils.generateCode(if (mode == "TEST") "TESTPAY" else "LIVEPAY")
        val idemKey = "dep_${UUID.randomUUID().toString().take(12)}"

        if (simulateFailure) {
            val failedTx = TransactionRecord(
                userId = uid,
                txCode = txCode,
                category = "DEPOSIT",
                metalPurity = "NONE",
                grams = 0.0,
                ratePerGram = 0.0,
                baseAmount = amountInr,
                gstAmount = 0.0,
                chargesAmount = 0.0,
                totalAmount = amountInr,
                paymentMethod = paymentMethod,
                gatewayOrderId = orderId,
                gatewayRef = refId,
                status = "FAILED",
                mode = mode,
                rejectionReason = "Payment declined by gateway sandbox",
                idempotencyKey = idemKey,
            )
            try {
                db.collection("transactions").document(txId).set(failedTx.toCreateMap()).await()
                sendNotification(uid, "PAYMENT_FAILED", "Deposit Failed", "Deposit of ${Formatters.formatInr(amountInr)} via $paymentMethod was declined.")
                return failedTx.copy(id = txId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, "transactions/$txId")
                throw e
            }
        }

        val walletRef = db.collection("users").document(uid).collection("wallets").document("primary")
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document("led_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        val txRef = db.collection("transactions").document(txId)

        val txRecord = TransactionRecord(
            id = txId,
            userId = uid,
            txCode = txCode,
            category = "DEPOSIT",
            metalPurity = "NONE",
            grams = 0.0,
            ratePerGram = 0.0,
            baseAmount = amountInr,
            gstAmount = 0.0,
            chargesAmount = 0.0,
            totalAmount = amountInr,
            paymentMethod = paymentMethod,
            gatewayOrderId = orderId,
            gatewayRef = refId,
            status = "SUCCESSFUL",
            mode = mode,
            idempotencyKey = idemKey,
        )

        try {
            db.runTransaction { tr ->
                val walletSnap = tr.get(walletRef)
                val currentAvail = walletSnap.getDouble("availableInr") ?: 0.0
                val newAvail = currentAvail + amountInr
                val walletUpdate = mapOf(
                    "userId" to uid,
                    "availableInr" to newAvail,
                    "pendingInr" to (walletSnap.getDouble("pendingInr") ?: 0.0),
                    "lockedInr" to (walletSnap.getDouble("lockedInr") ?: 0.0),
                    "gold24kGrams" to (walletSnap.getDouble("gold24kGrams") ?: 0.0),
                    "gold22kGrams" to (walletSnap.getDouble("gold22kGrams") ?: 0.0),
                    "gold18kGrams" to (walletSnap.getDouble("gold18kGrams") ?: 0.0),
                    "silver999Grams" to (walletSnap.getDouble("silver999Grams") ?: 0.0),
                    "totalInvestedGoldInr" to (walletSnap.getDouble("totalInvestedGoldInr") ?: 0.0),
                    "totalInvestedSilverInr" to (walletSnap.getDouble("totalInvestedSilverInr") ?: 0.0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                tr.set(walletRef, walletUpdate)
                tr.set(txRef, txRecord.toCreateMap())
                tr.set(
                    ledgerRef,
                    mapOf(
                        "userId" to uid,
                        "entryType" to "CREDIT_INR",
                        "assetType" to "INR",
                        "deltaAmount" to amountInr,
                        "balanceAfter" to newAvail,
                        "referenceId" to txCode,
                        "idempotencyKey" to idemKey,
                        "description" to "Wallet deposit via $paymentMethod ($mode)",
                        "mode" to mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()

            sendNotification(
                uid = uid,
                type = "PAYMENT_SUCCESS",
                title = "Wallet Credited",
                body = "${Formatters.formatInr(amountInr)} added via $paymentMethod ($mode MODE). Ref: $txCode",
                referenceId = txId,
            )
            return txRecord
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            throw e
        }
    }

    // --- ATOMIC BUY GOLD / SILVER ---

    suspend fun buyMetal(
        isGold: Boolean,
        purity: String, // "24K", "22K", "18K", or "999"
        grams: Double,
        ratePerGram: Double,
        baseAmount: Double,
        gstAmount: Double,
        chargesAmount: Double,
        totalAmount: Double,
        paymentMethod: String,
        mode: String,
    ): TransactionRecord {
        val uid = requireUserId()
        require(grams > 0.0 && totalAmount > 0.0 && ratePerGram > 0.0) { "Invalid order quantities" }

        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val txCode = CryptoUtils.generateCode(if (isGold) "SWG-GB" else "SWG-SB")
        val orderId = CryptoUtils.generateCode("ORD")
        val refId = CryptoUtils.generateCode(if (mode == "TEST") "TESTBULL" else "LIVEBULL")
        val idemKey = "buy_${UUID.randomUUID().toString().take(12)}"
        val category = if (isGold) "GOLD_BUY" else "SILVER_BUY"
        val assetType = when {
            !isGold -> "SILVER_999"
            purity == "22K" -> "GOLD_22K"
            purity == "18K" -> "GOLD_18K"
            else -> "GOLD_24K"
        }

        val walletRef = db.collection("users").document(uid).collection("wallets").document("primary")
        val txRef = db.collection("transactions").document(txId)
        val ledgerMetalRef = db.collection("users").document(uid).collection("ledger").document("led_m_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        val ledgerInrRef = db.collection("users").document(uid).collection("ledger").document("led_i_${UUID.randomUUID().toString().replace("-", "").take(14)}")

        val txRecord = TransactionRecord(
            id = txId,
            userId = uid,
            txCode = txCode,
            category = category,
            metalPurity = purity,
            grams = grams,
            ratePerGram = ratePerGram,
            baseAmount = baseAmount,
            gstAmount = gstAmount,
            chargesAmount = chargesAmount,
            totalAmount = totalAmount,
            paymentMethod = paymentMethod,
            gatewayOrderId = orderId,
            gatewayRef = refId,
            status = "SUCCESSFUL",
            mode = mode,
            idempotencyKey = idemKey,
        )

        try {
            db.runTransaction { tr ->
                val wSnap = tr.get(walletRef)
                val availInr = wSnap.getDouble("availableInr") ?: 0.0
                if (paymentMethod == "WALLET" && availInr < totalAmount) {
                    throw IllegalStateException("Insufficient wallet balance. Available: ${Formatters.formatInr(availInr)}")
                }
                val newAvailInr = if (paymentMethod == "WALLET") availInr - totalAmount else availInr

                val g24 = wSnap.getDouble("gold24kGrams") ?: 0.0
                val g22 = wSnap.getDouble("gold22kGrams") ?: 0.0
                val g18 = wSnap.getDouble("gold18kGrams") ?: 0.0
                val s999 = wSnap.getDouble("silver999Grams") ?: 0.0
                val invGold = wSnap.getDouble("totalInvestedGoldInr") ?: 0.0
                val invSilver = wSnap.getDouble("totalInvestedSilverInr") ?: 0.0

                val newG24 = if (assetType == "GOLD_24K") g24 + grams else g24
                val newG22 = if (assetType == "GOLD_22K") g22 + grams else g22
                val newG18 = if (assetType == "GOLD_18K") g18 + grams else g18
                val newS999 = if (assetType == "SILVER_999") s999 + grams else s999
                val newBalanceAfterMetal = when (assetType) {
                    "GOLD_24K" -> newG24
                    "GOLD_22K" -> newG22
                    "GOLD_18K" -> newG18
                    else -> newS999
                }

                tr.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "availableInr" to newAvailInr,
                        "pendingInr" to (wSnap.getDouble("pendingInr") ?: 0.0),
                        "lockedInr" to (wSnap.getDouble("lockedInr") ?: 0.0),
                        "gold24kGrams" to newG24,
                        "gold22kGrams" to newG22,
                        "gold18kGrams" to newG18,
                        "silver999Grams" to newS999,
                        "totalInvestedGoldInr" to if (isGold) invGold + totalAmount else invGold,
                        "totalInvestedSilverInr" to if (!isGold) invSilver + totalAmount else invSilver,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.set(txRef, txRecord.toCreateMap())
                tr.set(
                    ledgerMetalRef,
                    mapOf(
                        "userId" to uid,
                        "entryType" to if (isGold) "BUY_GOLD" else "BUY_SILVER",
                        "assetType" to assetType,
                        "deltaAmount" to grams,
                        "balanceAfter" to newBalanceAfterMetal,
                        "referenceId" to txCode,
                        "idempotencyKey" to idemKey,
                        "description" to "Purchased ${Formatters.formatGrams(grams)} of $purity ${if (isGold) "Gold" else "Silver"}",
                        "mode" to mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                if (paymentMethod == "WALLET") {
                    tr.set(
                        ledgerInrRef,
                        mapOf(
                            "userId" to uid,
                            "entryType" to "DEBIT_INR",
                            "assetType" to "INR",
                            "deltaAmount" to -totalAmount,
                            "balanceAfter" to newAvailInr,
                            "referenceId" to txCode,
                            "idempotencyKey" to "${idemKey}_inr",
                            "description" to "Wallet debit for $txCode",
                            "mode" to mode,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }.await()

            sendNotification(
                uid = uid,
                type = if (isGold) "GOLD_PURCHASED" else "SILVER_PURCHASED",
                title = "${if (isGold) "Gold" else "Silver"} Purchased ($purity)",
                body = "${Formatters.formatGrams(grams)} credited for ${Formatters.formatInr(totalAmount)}. ID: $txCode",
                referenceId = txId,
            )
            return txRecord
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            throw e
        }
    }

    // --- ATOMIC SELL GOLD / SILVER ---

    suspend fun sellMetal(
        isGold: Boolean,
        purity: String,
        grams: Double,
        sellRatePerGram: Double,
        deductionsInr: Double,
        creditToWallet: Boolean,
        bankAccount: BankAccount?,
        mode: String,
    ): TransactionRecord {
        val uid = requireUserId()
        require(grams > 0.0 && sellRatePerGram > 0.0) { "Invalid sell quantity" }

        val grossAmount = grams * sellRatePerGram
        val netProceeds = (grossAmount - deductionsInr).coerceAtLeast(0.0)
        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val txCode = CryptoUtils.generateCode(if (isGold) "SWG-GS" else "SWG-SS")
        val orderId = CryptoUtils.generateCode("SELL")
        val refId = CryptoUtils.generateCode("PAYOUT")
        val idemKey = "sell_${UUID.randomUUID().toString().take(12)}"
        val category = if (isGold) "GOLD_SELL" else "SILVER_SELL"
        val assetType = when {
            !isGold -> "SILVER_999"
            purity == "22K" -> "GOLD_22K"
            purity == "18K" -> "GOLD_18K"
            else -> "GOLD_24K"
        }

        val walletRef = db.collection("users").document(uid).collection("wallets").document("primary")
        val txRef = db.collection("transactions").document(txId)
        val ledgerMetalRef = db.collection("users").document(uid).collection("ledger").document("led_sm_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        val ledgerInrRef = db.collection("users").document(uid).collection("ledger").document("led_si_${UUID.randomUUID().toString().replace("-", "").take(14)}")

        val txRecord = TransactionRecord(
            id = txId,
            userId = uid,
            txCode = txCode,
            category = category,
            metalPurity = purity,
            grams = grams,
            ratePerGram = sellRatePerGram,
            baseAmount = grossAmount,
            gstAmount = 0.0,
            chargesAmount = deductionsInr,
            totalAmount = netProceeds,
            paymentMethod = if (creditToWallet) "WALLET" else "BANK_PAYOUT",
            gatewayOrderId = orderId,
            gatewayRef = refId,
            status = if (creditToWallet) "SUCCESSFUL" else "PENDING",
            mode = mode,
            bankAccountId = bankAccount?.id,
            bankNameMasked = bankAccount?.let { "${it.bankName} ${it.accountNumberMasked}" },
            idempotencyKey = idemKey,
        )

        try {
            db.runTransaction { tr ->
                val wSnap = tr.get(walletRef)
                val g24 = wSnap.getDouble("gold24kGrams") ?: 0.0
                val g22 = wSnap.getDouble("gold22kGrams") ?: 0.0
                val g18 = wSnap.getDouble("gold18kGrams") ?: 0.0
                val s999 = wSnap.getDouble("silver999Grams") ?: 0.0

                val currentMetal = when (assetType) {
                    "GOLD_24K" -> g24
                    "GOLD_22K" -> g22
                    "GOLD_18K" -> g18
                    else -> s999
                }
                if (currentMetal + 1e-6 < grams) {
                    throw IllegalStateException("Insufficient $purity balance. Available: ${Formatters.formatGrams(currentMetal)}")
                }

                val newG24 = if (assetType == "GOLD_24K") (g24 - grams).coerceAtLeast(0.0) else g24
                val newG22 = if (assetType == "GOLD_22K") (g22 - grams).coerceAtLeast(0.0) else g22
                val newG18 = if (assetType == "GOLD_18K") (g18 - grams).coerceAtLeast(0.0) else g18
                val newS999 = if (assetType == "SILVER_999") (s999 - grams).coerceAtLeast(0.0) else s999
                val newMetalBal = when (assetType) {
                    "GOLD_24K" -> newG24
                    "GOLD_22K" -> newG22
                    "GOLD_18K" -> newG18
                    else -> newS999
                }

                val availInr = wSnap.getDouble("availableInr") ?: 0.0
                val pendingInr = wSnap.getDouble("pendingInr") ?: 0.0
                val newAvailInr = if (creditToWallet) availInr + netProceeds else availInr
                val newPendingInr = if (!creditToWallet) pendingInr + netProceeds else pendingInr

                tr.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "availableInr" to newAvailInr,
                        "pendingInr" to newPendingInr,
                        "lockedInr" to (wSnap.getDouble("lockedInr") ?: 0.0),
                        "gold24kGrams" to newG24,
                        "gold22kGrams" to newG22,
                        "gold18kGrams" to newG18,
                        "silver999Grams" to newS999,
                        "totalInvestedGoldInr" to (wSnap.getDouble("totalInvestedGoldInr") ?: 0.0),
                        "totalInvestedSilverInr" to (wSnap.getDouble("totalInvestedSilverInr") ?: 0.0),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.set(txRef, txRecord.toCreateMap())
                tr.set(
                    ledgerMetalRef,
                    mapOf(
                        "userId" to uid,
                        "entryType" to if (isGold) "SELL_GOLD" else "SELL_SILVER",
                        "assetType" to assetType,
                        "deltaAmount" to -grams,
                        "balanceAfter" to newMetalBal,
                        "referenceId" to txCode,
                        "idempotencyKey" to idemKey,
                        "description" to "Sold ${Formatters.formatGrams(grams)} $purity ${if (isGold) "Gold" else "Silver"}",
                        "mode" to mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                if (creditToWallet) {
                    tr.set(
                        ledgerInrRef,
                        mapOf(
                            "userId" to uid,
                            "entryType" to "CREDIT_INR",
                            "assetType" to "INR",
                            "deltaAmount" to netProceeds,
                            "balanceAfter" to newAvailInr,
                            "referenceId" to txCode,
                            "idempotencyKey" to "${idemKey}_inr",
                            "description" to "Sale proceeds credited to wallet",
                            "mode" to mode,
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }
            }.await()

            sendNotification(
                uid = uid,
                type = if (isGold) "GOLD_SOLD" else "SILVER_SOLD",
                title = "${if (isGold) "Gold" else "Silver"} Sold ($purity)",
                body = "${Formatters.formatGrams(grams)} sold for ${Formatters.formatInr(netProceeds)}. ${if (creditToWallet) "Credited to Wallet" else "Payout Pending"}",
                referenceId = txId,
            )
            return txRecord
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            throw e
        }
    }

    // --- BANK ACCOUNTS & WITHDRAWALS ---

    fun observeBankAccounts(uid: String): Flow<List<BankAccount>> = flow {
        val path = "users/$uid/bank_accounts"
        emitAll(
            db.collection("users").document(uid).collection("bank_accounts")
                .snapshots()
                .map { it.toObjectsSafely<BankAccount>() }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun addBankAccount(
        uid: String,
        holderName: String,
        bankName: String,
        accountNumber: String,
        ifscCode: String,
        makePrimary: Boolean,
    ) {
        val cleanAcc = accountNumber.filter { it.isDigit() }
        require(cleanAcc.length >= 8) { "Invalid account number" }
        val cleanIfsc = ifscCode.trim().uppercase()
        require(cleanIfsc.length == 11) { "IFSC code must be 11 characters" }

        val bankId = "bnk_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val bankCol = db.collection("users").document(uid).collection("bank_accounts")
        try {
            if (makePrimary) {
                val existing = bankCol.get().await()
                existing.documents.forEach { doc ->
                    doc.reference.update(mapOf("isPrimary" to false, "updatedAt" to FieldValue.serverTimestamp())).await()
                }
            }
            bankCol.document(bankId).set(
                mapOf(
                    "userId" to uid,
                    "accountHolderName" to holderName.trim(),
                    "bankName" to bankName.trim(),
                    "accountNumberLast4" to cleanAcc.takeLast(4),
                    "accountNumberMasked" to Formatters.maskAccount(cleanAcc),
                    "ifscCode" to cleanIfsc,
                    "verificationStatus" to "VERIFIED",
                    "isPrimary" to makePrimary,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, bankCol.path)
            throw e
        }
    }

    suspend fun setPrimaryBankAccount(uid: String, bankId: String) {
        val bankCol = db.collection("users").document(uid).collection("bank_accounts")
        try {
            val existing = bankCol.get().await()
            existing.documents.forEach { doc ->
                doc.reference.update(mapOf("isPrimary" to (doc.id == bankId), "updatedAt" to FieldValue.serverTimestamp())).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, bankCol.path)
            throw e
        }
    }

    suspend fun removeBankAccount(uid: String, bankId: String) {
        val docRef = db.collection("users").document(uid).collection("bank_accounts").document(bankId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun requestWithdrawal(
        amountInr: Double,
        feeInr: Double,
        bankAccount: BankAccount,
        mode: String,
    ): TransactionRecord {
        val uid = requireUserId()
        val totalDebit = amountInr + feeInr
        require(amountInr > 0.0) { "Withdrawal amount must be positive" }

        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val txCode = CryptoUtils.generateCode("SWG-WDR")
        val orderId = CryptoUtils.generateCode("WDR")
        val refId = "PENDING_BANK_SETTLEMENT"
        val idemKey = "wdr_${UUID.randomUUID().toString().take(12)}"

        val walletRef = db.collection("users").document(uid).collection("wallets").document("primary")
        val txRef = db.collection("transactions").document(txId)
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document("led_w_${UUID.randomUUID().toString().replace("-", "").take(14)}")

        val txRecord = TransactionRecord(
            id = txId,
            userId = uid,
            txCode = txCode,
            category = "WITHDRAWAL",
            metalPurity = "NONE",
            grams = 0.0,
            ratePerGram = 0.0,
            baseAmount = amountInr,
            gstAmount = 0.0,
            chargesAmount = feeInr,
            totalAmount = totalDebit,
            paymentMethod = "BANK_PAYOUT",
            gatewayOrderId = orderId,
            gatewayRef = refId,
            status = "PENDING",
            mode = mode,
            bankAccountId = bankAccount.id,
            bankNameMasked = "${bankAccount.bankName} (${bankAccount.accountNumberMasked})",
            idempotencyKey = idemKey,
        )

        try {
            db.runTransaction { tr ->
                val wSnap = tr.get(walletRef)
                val avail = wSnap.getDouble("availableInr") ?: 0.0
                val locked = wSnap.getDouble("lockedInr") ?: 0.0
                if (avail < totalDebit) {
                    throw IllegalStateException("Insufficient available balance. Available: ${Formatters.formatInr(avail)}")
                }
                val newAvail = avail - totalDebit
                val newLocked = locked + totalDebit
                tr.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "availableInr" to newAvail,
                        "pendingInr" to (wSnap.getDouble("pendingInr") ?: 0.0),
                        "lockedInr" to newLocked,
                        "gold24kGrams" to (wSnap.getDouble("gold24kGrams") ?: 0.0),
                        "gold22kGrams" to (wSnap.getDouble("gold22kGrams") ?: 0.0),
                        "gold18kGrams" to (wSnap.getDouble("gold18kGrams") ?: 0.0),
                        "silver999Grams" to (wSnap.getDouble("silver999Grams") ?: 0.0),
                        "totalInvestedGoldInr" to (wSnap.getDouble("totalInvestedGoldInr") ?: 0.0),
                        "totalInvestedSilverInr" to (wSnap.getDouble("totalInvestedSilverInr") ?: 0.0),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.set(txRef, txRecord.toCreateMap())
                tr.set(
                    ledgerRef,
                    mapOf(
                        "userId" to uid,
                        "entryType" to "WITHDRAWAL_LOCK",
                        "assetType" to "INR",
                        "deltaAmount" to -totalDebit,
                        "balanceAfter" to newAvail,
                        "referenceId" to txCode,
                        "idempotencyKey" to idemKey,
                        "description" to "Withdrawal request to ${bankAccount.bankName} ${bankAccount.accountNumberMasked}",
                        "mode" to mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }.await()

            sendNotification(
                uid = uid,
                type = "WITHDRAWAL_REQUESTED",
                title = "Withdrawal Requested",
                body = "${Formatters.formatInr(amountInr)} queued for ${bankAccount.bankName} (${bankAccount.accountNumberMasked}). Ref: $txCode",
                referenceId = txId,
            )
            return txRecord
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, txRef.path)
            throw e
        }
    }

    // --- USER-TO-USER TRANSFERS ---

    suspend fun lookupRecipient(queryInput: String): DirectoryEntry? {
        val uid = requireUserId()
        val parsed = CryptoUtils.parseQrUserId(queryInput).trim()
        val keysToTry = buildList {
            add(parsed.uppercase())
            val digits = parsed.filter { it.isDigit() }
            if (digits.length >= 10) {
                add("MOB_${digits.takeLast(10)}")
                add("MOB_$digits")
            }
        }
        return try {
            for (k in keysToTry) {
                val snap = db.collection("user_directory").document(k).get().await()
                if (snap.exists()) {
                    val entry = snap.toObjectSafely<DirectoryEntry>()
                    if (entry != null && entry.uid != uid) return entry
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "user_directory/$parsed")
            throw e
        }
    }

    fun observeTransfers(uid: String): Flow<List<UserTransfer>> = flow {
        val path = "transfers"
        emitAll(
            db.collection("transfers")
                .whereEqualTo("senderUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<UserTransfer>()
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeIncomingTransfers(uid: String): Flow<List<UserTransfer>> = flow {
        val path = "transfers"
        emitAll(
            db.collection("transfers")
                .whereEqualTo("recipientUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<UserTransfer>()
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun executeTransfer(
        senderName: String,
        recipient: DirectoryEntry,
        assetType: String, // "INR", "GOLD_24K", "GOLD_22K", "GOLD_18K", "SILVER_999"
        quantity: Double,
        feeInr: Double,
        mode: String,
    ): UserTransfer {
        val uid = requireUserId()
        require(quantity > 0.0) { "Transfer amount/quantity must be greater than zero" }
        require(recipient.uid != uid) { "Cannot transfer to your own account" }

        val transferId = "trf_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val transferCode = CryptoUtils.generateCode("SWG-TRF")
        val idemKey = "trf_${UUID.randomUUID().toString().take(12)}"

        val walletRef = db.collection("users").document(uid).collection("wallets").document("primary")
        val transferRef = db.collection("transfers").document(transferId)
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document("led_t_${UUID.randomUUID().toString().replace("-", "").take(14)}")
        val txRef = db.collection("transactions").document("tx_${UUID.randomUUID().toString().replace("-", "").take(16)}")

        val transfer = UserTransfer(
            id = transferId,
            senderUid = uid,
            senderName = senderName,
            recipientUid = recipient.uid,
            recipientName = recipient.displayName,
            transferCode = transferCode,
            assetType = assetType,
            quantity = quantity,
            feeInr = feeInr,
            status = "COMPLETED",
            recipientCredited = false,
            idempotencyKey = idemKey,
        )

        val purity = when (assetType) {
            "GOLD_24K" -> "24K"
            "GOLD_22K" -> "22K"
            "GOLD_18K" -> "18K"
            "SILVER_999" -> "999"
            else -> "NONE"
        }

        try {
            db.runTransaction { tr ->
                val wSnap = tr.get(walletRef)
                var availInr = wSnap.getDouble("availableInr") ?: 0.0
                var g24 = wSnap.getDouble("gold24kGrams") ?: 0.0
                var g22 = wSnap.getDouble("gold22kGrams") ?: 0.0
                var g18 = wSnap.getDouble("gold18kGrams") ?: 0.0
                var s999 = wSnap.getDouble("silver999Grams") ?: 0.0

                if (availInr < feeInr) {
                    throw IllegalStateException("Insufficient INR wallet balance for transfer fee")
                }
                availInr -= feeInr

                val balanceAfterAsset = when (assetType) {
                    "INR" -> {
                        if (availInr < quantity) throw IllegalStateException("Insufficient INR wallet balance")
                        availInr -= quantity
                        availInr
                    }
                    "GOLD_24K" -> {
                        if (g24 + 1e-6 < quantity) throw IllegalStateException("Insufficient 24K Gold balance")
                        g24 = (g24 - quantity).coerceAtLeast(0.0)
                        g24
                    }
                    "GOLD_22K" -> {
                        if (g22 + 1e-6 < quantity) throw IllegalStateException("Insufficient 22K Gold balance")
                        g22 = (g22 - quantity).coerceAtLeast(0.0)
                        g22
                    }
                    "GOLD_18K" -> {
                        if (g18 + 1e-6 < quantity) throw IllegalStateException("Insufficient 18K Gold balance")
                        g18 = (g18 - quantity).coerceAtLeast(0.0)
                        g18
                    }
                    else -> {
                        if (s999 + 1e-6 < quantity) throw IllegalStateException("Insufficient 999 Silver balance")
                        s999 = (s999 - quantity).coerceAtLeast(0.0)
                        s999
                    }
                }

                tr.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "availableInr" to availInr,
                        "pendingInr" to (wSnap.getDouble("pendingInr") ?: 0.0),
                        "lockedInr" to (wSnap.getDouble("lockedInr") ?: 0.0),
                        "gold24kGrams" to g24,
                        "gold22kGrams" to g22,
                        "gold18kGrams" to g18,
                        "silver999Grams" to s999,
                        "totalInvestedGoldInr" to (wSnap.getDouble("totalInvestedGoldInr") ?: 0.0),
                        "totalInvestedSilverInr" to (wSnap.getDouble("totalInvestedSilverInr") ?: 0.0),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )

                tr.set(
                    transferRef,
                    mapOf(
                        "senderUid" to uid,
                        "senderName" to senderName,
                        "recipientUid" to recipient.uid,
                        "recipientName" to recipient.displayName,
                        "transferCode" to transferCode,
                        "assetType" to assetType,
                        "quantity" to quantity,
                        "feeInr" to feeInr,
                        "status" to "COMPLETED",
                        "recipientCredited" to false,
                        "idempotencyKey" to idemKey,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )

                tr.set(
                    ledgerRef,
                    mapOf(
                        "userId" to uid,
                        "entryType" to "TRANSFER_OUT",
                        "assetType" to assetType,
                        "deltaAmount" to -quantity,
                        "balanceAfter" to balanceAfterAsset,
                        "referenceId" to transferCode,
                        "idempotencyKey" to idemKey,
                        "description" to "Transferred $quantity ($assetType) to ${recipient.displayName}",
                        "mode" to mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )

                tr.set(
                    txRef,
                    TransactionRecord(
                        userId = uid,
                        counterpartyUid = recipient.uid,
                        counterpartyName = recipient.displayName,
                        txCode = transferCode,
                        category = "TRANSFER_SENT",
                        metalPurity = purity,
                        grams = if (assetType == "INR") 0.0 else quantity,
                        ratePerGram = 0.0,
                        baseAmount = if (assetType == "INR") quantity else 0.0,
                        gstAmount = 0.0,
                        chargesAmount = feeInr,
                        totalAmount = if (assetType == "INR") quantity + feeInr else feeInr,
                        paymentMethod = "INTERNAL",
                        gatewayOrderId = transferId,
                        gatewayRef = transferCode,
                        status = "SUCCESSFUL",
                        mode = mode,
                        idempotencyKey = idemKey,
                    ).toCreateMap()
                )
            }.await()

            sendNotification(
                uid = uid,
                type = "TRANSFER_SENT",
                title = "Transfer Sent",
                body = "Sent ${if (assetType == "INR") Formatters.formatInr(quantity) else Formatters.formatGrams(quantity)} ($assetType) to ${recipient.displayName}. Ref: $transferCode",
                referenceId = transferId,
            )
            return transfer
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, transferRef.path)
            throw e
        }
    }

    suspend fun claimIncomingTransfer(transfer: UserTransfer, mode: String) {
        val uid = requireUserId()
        if (transfer.recipientUid != uid || transfer.recipientCredited) return
        val transferRef = db.collection("transfers").document(transfer.id)
        val walletRef = db.collection("users").document(uid).collection("wallets").document("primary")
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document("led_rc_${transfer.id.take(12)}")
        val txRef = db.collection("transactions").document("tx_rc_${transfer.id.take(12)}")

        val purity = when (transfer.assetType) {
            "GOLD_24K" -> "24K"
            "GOLD_22K" -> "22K"
            "GOLD_18K" -> "18K"
            "SILVER_999" -> "999"
            else -> "NONE"
        }

        try {
            db.runTransaction { tr ->
                val tSnap = tr.get(transferRef)
                if (tSnap.getBoolean("recipientCredited") == true) return@runTransaction
                val wSnap = tr.get(walletRef)
                var availInr = wSnap.getDouble("availableInr") ?: 0.0
                var g24 = wSnap.getDouble("gold24kGrams") ?: 0.0
                var g22 = wSnap.getDouble("gold22kGrams") ?: 0.0
                var g18 = wSnap.getDouble("gold18kGrams") ?: 0.0
                var s999 = wSnap.getDouble("silver999Grams") ?: 0.0

                val balanceAfter = when (transfer.assetType) {
                    "INR" -> { availInr += transfer.quantity; availInr }
                    "GOLD_24K" -> { g24 += transfer.quantity; g24 }
                    "GOLD_22K" -> { g22 += transfer.quantity; g22 }
                    "GOLD_18K" -> { g18 += transfer.quantity; g18 }
                    else -> { s999 += transfer.quantity; s999 }
                }

                tr.update(transferRef, mapOf("recipientCredited" to true))
                tr.set(
                    walletRef,
                    mapOf(
                        "userId" to uid,
                        "availableInr" to availInr,
                        "pendingInr" to (wSnap.getDouble("pendingInr") ?: 0.0),
                        "lockedInr" to (wSnap.getDouble("lockedInr") ?: 0.0),
                        "gold24kGrams" to g24,
                        "gold22kGrams" to g22,
                        "gold18kGrams" to g18,
                        "silver999Grams" to s999,
                        "totalInvestedGoldInr" to (wSnap.getDouble("totalInvestedGoldInr") ?: 0.0),
                        "totalInvestedSilverInr" to (wSnap.getDouble("totalInvestedSilverInr") ?: 0.0),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.set(
                    ledgerRef,
                    mapOf(
                        "userId" to uid,
                        "entryType" to "TRANSFER_IN",
                        "assetType" to transfer.assetType,
                        "deltaAmount" to transfer.quantity,
                        "balanceAfter" to balanceAfter,
                        "referenceId" to transfer.transferCode,
                        "idempotencyKey" to "claim_${transfer.id}",
                        "description" to "Received from ${transfer.senderName}",
                        "mode" to mode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                tr.set(
                    txRef,
                    TransactionRecord(
                        userId = uid,
                        counterpartyUid = transfer.senderUid,
                        counterpartyName = transfer.senderName,
                        txCode = transfer.transferCode,
                        category = "TRANSFER_RECEIVED",
                        metalPurity = purity,
                        grams = if (transfer.assetType == "INR") 0.0 else transfer.quantity,
                        ratePerGram = 0.0,
                        baseAmount = if (transfer.assetType == "INR") transfer.quantity else 0.0,
                        gstAmount = 0.0,
                        chargesAmount = 0.0,
                        totalAmount = if (transfer.assetType == "INR") transfer.quantity else 0.0,
                        paymentMethod = "INTERNAL",
                        gatewayOrderId = transfer.id,
                        gatewayRef = transfer.transferCode,
                        status = "SUCCESSFUL",
                        mode = mode,
                        idempotencyKey = "claim_${transfer.id}",
                    ).toCreateMap()
                )
            }.await()

            sendNotification(
                uid = uid,
                type = "TRANSFER_RECEIVED",
                title = "Transfer Received",
                body = "Received ${if (transfer.assetType == "INR") Formatters.formatInr(transfer.quantity) else Formatters.formatGrams(transfer.quantity)} from ${transfer.senderName}",
                referenceId = transfer.id,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, transferRef.path)
        }
    }

    // --- KYC ---

    fun observeKycRecord(uid: String): Flow<KycRecord?> = flow {
        val path = "kyc_records/$uid"
        emitAll(
            db.collection("kyc_records").document(uid)
                .snapshots()
                .map { it.toObjectSafely<KycRecord>() }
                .withFirestoreErrorLogging(OperationType.GET, path, uid)
        )
    }

    suspend fun submitKyc(
        uid: String,
        userName: String,
        mobileNumber: String,
        panNumber: String,
        aadhaarLast4: String,
        documentType: String,
        documentRefUri: String?,
        addressLine: String,
        state: String,
        city: String,
        pincode: String,
        documentFrontBase64: String? = null,
        documentBackBase64: String? = null,
        captureMethod: String = "CAMERAX_SCAN",
        scanQualityScore: Int = 0,
        declarationAccepted: Boolean = true,
    ) {
        val kycRef = db.collection("kyc_records").document(uid)
        val userRef = db.collection("users").document(uid)
        try {
            val existing = kycRef.get().await()
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "userName" to userName.trim(),
                "mobileNumber" to mobileNumber.trim(),
                "panNumber" to panNumber.trim().uppercase(),
                "aadhaarLast4" to aadhaarLast4.filter { it.isDigit() }.takeLast(4),
                "documentType" to documentType,
                "captureMethod" to captureMethod,
                "scanQualityScore" to scanQualityScore.coerceIn(0, 100),
                "verificationStep" to 3,
                "declarationAccepted" to declarationAccepted,
                "addressLine" to addressLine.trim(),
                "state" to state.trim(),
                "city" to city.trim(),
                "pincode" to pincode.trim(),
                "status" to "UNDER_REVIEW",
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (!documentRefUri.isNullOrBlank()) {
                payload["documentRefUri"] = documentRefUri.take(490)
            }
            if (!documentFrontBase64.isNullOrBlank()) {
                payload["documentFrontBase64"] = documentFrontBase64.take(115000)
            }
            if (!documentBackBase64.isNullOrBlank()) {
                payload["documentBackBase64"] = documentBackBase64.take(115000)
            }
            if (existing.exists()) {
                payload["createdAt"] = existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
            } else {
                payload["createdAt"] = FieldValue.serverTimestamp()
            }
            kycRef.set(payload).await()
            userRef.update(
                mapOf(
                    "kycStatus" to "UNDER_REVIEW",
                    "panMasked" to Formatters.maskPan(panNumber),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            sendNotification(uid, "KYC_SUBMITTED", "KYC Under Review", "Your PAN & CameraX ID verification documents have been submitted for compliance review.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, kycRef.path)
            throw e
        }
    }

    // --- DEVICES & LOGIN HISTORY ---

    fun observeDevices(uid: String): Flow<List<DeviceSession>> = flow {
        val path = "users/$uid/devices"
        emitAll(
            db.collection("users").document(uid).collection("devices")
                .snapshots()
                .map { it.toObjectsSafely<DeviceSession>() }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeLoginHistory(uid: String): Flow<List<LoginHistoryEntry>> = flow {
        val path = "users/$uid/login_history"
        emitAll(
            db.collection("users").document(uid).collection("login_history")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(30)
                .snapshots()
                .map { it.toObjectsSafely<LoginHistoryEntry>() }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun revokeOtherDevices(uid: String, currentDeviceId: String) {
        val col = db.collection("users").document(uid).collection("devices")
        try {
            val docs = col.get().await().documents
            docs.filter { it.id != currentDeviceId }.forEach { doc ->
                doc.reference.update(mapOf("sessionActive" to false, "lastActiveAt" to FieldValue.serverTimestamp())).await()
            }
            sendNotification(uid, "SECURITY_ALERT", "Other Sessions Logged Out", "All other device sessions were revoked securely.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, col.path)
            throw e
        }
    }

    // --- PRICE ALERTS & NOTIFICATIONS ---

    fun observePriceAlerts(uid: String): Flow<List<PriceAlert>> = flow {
        val path = "users/$uid/price_alerts"
        emitAll(
            db.collection("users").document(uid).collection("price_alerts")
                .snapshots()
                .map { it.toObjectsSafely<PriceAlert>() }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun fetchPriceAlertsOnce(uid: String): List<PriceAlert> {
        val path = "users/$uid/price_alerts"
        return try {
            db.collection("users").document(uid).collection("price_alerts")
                .get()
                .await()
                .toObjectsSafely<PriceAlert>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun fetchUserNotificationPrefsOnce(uid: String): Triple<Boolean, Boolean, Boolean> {
        val path = "users/$uid"
        return try {
            val snap = db.collection("users").document(uid).get().await()
            val prof = snap.toObjectSafely<UserProfile>()
            Triple(
                prof?.notifPriceAlerts ?: true,
                prof?.notifSoundEnabled ?: true,
                prof?.notifVibrationEnabled ?: true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Triple(true, true, true)
        }
    }

    suspend fun createPriceAlert(
        uid: String,
        metal: String,
        targetPriceInr: Double,
        direction: String,
        frequency: String = "ONCE",
        notes: String = "",
        currentObservedPrice: Double = 0.0,
    ) {
        val id = "alt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val ref = db.collection("users").document(uid).collection("price_alerts").document(id)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "metal" to metal,
                    "targetPriceInr" to targetPriceInr,
                    "direction" to direction,
                    "frequency" to frequency,
                    "isActive" to true,
                    "notes" to notes.trim().take(150),
                    "triggerCount" to 0,
                    "lastTriggeredPrice" to 0.0,
                    "lastObservedPrice" to currentObservedPrice.coerceAtLeast(0.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun updatePriceAlert(
        uid: String,
        alertId: String,
        targetPriceInr: Double,
        direction: String,
        frequency: String,
        isActive: Boolean,
        notes: String,
    ) {
        val ref = db.collection("users").document(uid).collection("price_alerts").document(alertId)
        try {
            ref.update(
                mapOf(
                    "targetPriceInr" to targetPriceInr,
                    "direction" to direction,
                    "frequency" to frequency,
                    "isActive" to isActive,
                    "notes" to notes.trim().take(150),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun togglePriceAlert(uid: String, alertId: String, isActive: Boolean) {
        val ref = db.collection("users").document(uid).collection("price_alerts").document(alertId)
        try {
            ref.update(
                mapOf(
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun markPriceAlertTriggered(uid: String, alert: PriceAlert, currentPrice: Double) {
        val ref = db.collection("users").document(uid).collection("price_alerts").document(alert.id)
        val keepActive = alert.frequency != "ONCE"
        try {
            ref.update(
                mapOf(
                    "isActive" to keepActive,
                    "triggerCount" to (alert.triggerCount + 1),
                    "lastTriggeredPrice" to currentPrice,
                    "lastObservedPrice" to currentPrice,
                    "triggeredAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }

    suspend fun updateAlertObservedPrice(uid: String, alertId: String, observedPrice: Double) {
        val ref = db.collection("users").document(uid).collection("price_alerts").document(alertId)
        try {
            ref.update(
                mapOf(
                    "lastObservedPrice" to observedPrice.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun deletePriceAlert(uid: String, alertId: String) {
        val ref = db.collection("users").document(uid).collection("price_alerts").document(alertId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        val path = "users/$uid/notifications"
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .snapshots()
                .map { it.toObjectsSafely<AppNotification>() }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    fun observeAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("announcements")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { it.toObjectsSafely<Announcement>() }
                .withFirestoreErrorLogging(OperationType.LIST, "announcements", uid)
        )
    }

    suspend fun sendNotification(uid: String, type: String, title: String, body: String, referenceId: String? = null) {
        val id = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val ref = db.collection("users").document(uid).collection("notifications").document(id)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "type" to type,
            "title" to title.take(135),
            "body" to body.take(490),
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (!referenceId.isNullOrBlank()) payload["referenceId"] = referenceId
        try {
            ref.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }

    suspend fun markNotificationRead(uid: String, notifId: String) {
        val ref = db.collection("users").document(uid).collection("notifications").document(notifId)
        try {
            ref.update("isRead", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }

    suspend fun deleteNotification(uid: String, notifId: String) {
        val ref = db.collection("users").document(uid).collection("notifications").document(notifId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
        }
    }

    suspend fun clearAllNotifications(uid: String) {
        val col = db.collection("users").document(uid).collection("notifications")
        try {
            col.get().await().documents.forEach { it.reference.delete().await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, col.path)
        }
    }

    // --- SUPPORT TICKETS ---

    fun observeSupportTickets(uid: String): Flow<List<SupportTicket>> = flow {
        val path = "support_tickets"
        emitAll(
            db.collection("support_tickets")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<SupportTicket>()
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun createSupportTicket(
        uid: String,
        userName: String,
        category: String,
        subject: String,
        description: String,
        attachmentUri: String?,
    ): String {
        val id = "tkt_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val code = CryptoUtils.generateCode("SWG-SUP")
        val ref = db.collection("support_tickets").document(id)
        val initialMessages = listOf(
            mapOf(
                "senderRole" to "USER",
                "senderName" to userName,
                "text" to description.trim(),
                "timestampMillis" to System.currentTimeMillis(),
            )
        )
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "userName" to userName,
            "ticketCode" to code,
            "category" to category,
            "subject" to subject.trim(),
            "description" to description.trim(),
            "status" to "OPEN",
            "messages" to initialMessages,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!attachmentUri.isNullOrBlank()) payload["attachmentUri"] = attachmentUri
        try {
            ref.set(payload).await()
            return code
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun replyToTicket(ticket: SupportTicket, senderRole: String, senderName: String, text: String, closeTicket: Boolean = false) {
        val ref = db.collection("support_tickets").document(ticket.id)
        val updatedMessages = (ticket.messages + mapOf(
            "senderRole" to senderRole,
            "senderName" to senderName,
            "text" to text.trim(),
            "timestampMillis" to System.currentTimeMillis(),
        )).takeLast(50)
        val newStatus = if (closeTicket) "CLOSED" else if (senderRole == "ADMIN") "WAITING_FOR_USER" else "IN_PROGRESS"
        try {
            ref.update(
                mapOf(
                    "messages" to updatedMessages,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    // --- REFERRALS ---

    fun observeReferrals(uid: String): Flow<List<ReferralRecord>> = flow {
        val path = "referrals"
        emitAll(
            db.collection("referrals")
                .whereEqualTo("referrerUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<ReferralRecord>()
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun applyReferralCode(uid: String, userName: String, codeInput: String, rewardInr: Double) {
        val code = codeInput.trim().uppercase()
        require(code.length >= 4) { "Invalid referral code" }
        // Lookup referrer via user_directory or create self-tracking referral if valid
        val refId = "ref_${uid.take(12)}"
        val refDoc = db.collection("referrals").document(refId)
        try {
            if (refDoc.get().await().exists()) {
                throw IllegalStateException("Referral code already applied on this account")
            }
            // Note: In production, referral code mapping resolves referrerUid; here we check if code matches a directory customUserId
            val dirSnap = db.collection("user_directory").document(code).get().await()
            val referrerUid = dirSnap.getString("uid") ?: throw IllegalStateException("Referral User ID not found")
            require(referrerUid != uid) { "Cannot refer yourself" }

            refDoc.set(
                mapOf(
                    "referrerUid" to referrerUid,
                    "referredUid" to uid,
                    "referredName" to userName,
                    "referralCode" to code,
                    "rewardAmountInr" to rewardInr,
                    "status" to "ELIGIBLE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            db.collection("users").document(uid).update(
                mapOf("referredByCode" to code, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, refDoc.path)
            throw e
        }
    }

    // --- MARKET PRICE HISTORY (30-DAY FIRESTORE COLLECTION) ---

    fun observeMarketPriceHistory(daysLimit: Int = 30): Flow<List<MarketPriceHistoryEntry>> = flow {
        val uid = requireUserId()
        val path = "market_price_history"
        emitAll(
            db.collection("market_price_history")
                .orderBy("timestampEpochMs", Query.Direction.DESCENDING)
                .limit(daysLimit.coerceIn(1, 90).toLong())
                .snapshots()
                .map { snap ->
                    snap.toObjectsSafely<MarketPriceHistoryEntry>()
                        .sortedBy { it.timestampEpochMs }
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun seedMissingMarketHistory(entries: List<MarketPriceHistoryEntry>): Int {
        requireUserId()
        if (entries.isEmpty()) return 0
        val col = db.collection("market_price_history")
        return try {
            val existingSnap = col.get().await()
            val existingIds = existingSnap.documents.map { it.id }.toSet()
            var createdCount = 0
            entries.forEach { entry ->
                val docId = entry.dateStr.trim()
                if (docId.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) && !existingIds.contains(docId)) {
                    val payload = mapOf(
                        "dateStr" to docId,
                        "label" to entry.label.ifBlank { docId.takeLast(5) }.take(24),
                        "gold24kPerGram" to entry.gold24kPerGram.coerceIn(1.0, 100000.0),
                        "gold22kPerGram" to entry.gold22kPerGram.coerceIn(1.0, 100000.0),
                        "gold18kPerGram" to entry.gold18kPerGram.coerceIn(1.0, 100000.0),
                        "silver999PerGram" to entry.silver999PerGram.coerceIn(0.5, 10000.0),
                        "source" to entry.source.ifBlank { "VERIFIED_SPOT_SNAPSHOT" }.take(120),
                        "timestampEpochMs" to entry.timestampEpochMs.coerceAtLeast(1L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    col.document(docId).set(payload).await()
                    createdCount++
                }
            }
            createdCount
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            0
        }
    }

    fun observeLiveGoldSummary(): Flow<FirestoreGoldSummary> = flow {
        val uid = requireUserId()
        val path = "market_price_history"
        emitAll(
            db.collection("market_price_history")
                .orderBy("timestampEpochMs", Query.Direction.DESCENDING)
                .limit(2)
                .snapshots()
                .map { snap ->
                    val entries = snap.toObjectsSafely<MarketPriceHistoryEntry>()
                        .sortedByDescending { it.timestampEpochMs }
                    buildGoldSummaryFromEntries(entries)
                }
                .withFirestoreErrorLogging(OperationType.LIST, path, uid)
        )
    }

    suspend fun fetchLatestGoldSummaryOnce(): FirestoreGoldSummary {
        requireUserId()
        val path = "market_price_history"
        return try {
            val snap = db.collection("market_price_history")
                .orderBy("timestampEpochMs", Query.Direction.DESCENDING)
                .limit(2)
                .get()
                .await()
            val entries = snap.toObjectsSafely<MarketPriceHistoryEntry>()
                .sortedByDescending { it.timestampEpochMs }
            buildGoldSummaryFromEntries(entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun fetchLatestMarketRatesOnce(): LiveMarketRates? {
        requireUserId()
        val path = "market_price_history"
        return try {
            val snap = db.collection("market_price_history")
                .orderBy("timestampEpochMs", Query.Direction.DESCENDING)
                .limit(2)
                .get()
                .await()
            val entries = snap.toObjectsSafely<MarketPriceHistoryEntry>()
                .sortedByDescending { it.timestampEpochMs }
            buildLiveMarketRatesFromEntries(entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    companion object {
        fun buildGoldSummaryFromEntries(entries: List<MarketPriceHistoryEntry>): FirestoreGoldSummary {
            val latest = entries.maxByOrNull { it.timestampEpochMs }
                ?: return FirestoreGoldSummary(
                    isLoading = false,
                    isFromFirestore = false,
                    source = "Firestore market_price_history",
                )
            val previous = entries
                .filter { it.id != latest.id || it.dateStr != latest.dateStr }
                .maxByOrNull { it.timestampEpochMs }

            val g24 = latest.gold24kPerGram
            val g22 = latest.gold22kPerGram.takeIf { it > 0.0 } ?: (g24 * (22.0 / 24.0))
            val g18 = latest.gold18kPerGram.takeIf { it > 0.0 } ?: (g24 * (18.0 / 24.0))
            val prev24 = previous?.gold24kPerGram ?: 0.0
            val changePct = if (prev24 > 0.0) {
                ((g24 - prev24) / prev24) * 100.0
            } else {
                0.0
            }

            return FirestoreGoldSummary(
                gold24kPerGram = g24,
                gold22kPerGram = g22,
                gold18kPerGram = g18,
                previous24kPerGram = prev24,
                changePercent = changePct,
                dateStr = latest.dateStr,
                source = latest.source.ifBlank { "Firestore market_price_history" },
                lastUpdatedEpochMs = latest.updatedAt?.toDate()?.time ?: latest.timestampEpochMs,
                isLoading = false,
                isFromFirestore = true,
                errorMessage = null,
            )
        }

        fun buildLiveMarketRatesFromEntries(entries: List<MarketPriceHistoryEntry>): LiveMarketRates? {
            val latest = entries.maxByOrNull { it.timestampEpochMs } ?: return null
            if (latest.gold24kPerGram <= 0.0 && latest.silver999PerGram <= 0.0) return null
            val previous = entries
                .filter { it.id != latest.id || it.dateStr != latest.dateStr }
                .maxByOrNull { it.timestampEpochMs }

            val g24 = latest.gold24kPerGram
            val g22 = latest.gold22kPerGram.takeIf { it > 0.0 } ?: (g24 * (22.0 / 24.0))
            val g18 = latest.gold18kPerGram.takeIf { it > 0.0 } ?: (g24 * (18.0 / 24.0))
            val ag999 = latest.silver999PerGram

            val goldChange = if (previous != null && previous.gold24kPerGram > 0.0 && g24 > 0.0) {
                ((g24 - previous.gold24kPerGram) / previous.gold24kPerGram) * 100.0
            } else {
                0.0
            }
            val silverChange = if (previous != null && previous.silver999PerGram > 0.0 && ag999 > 0.0) {
                ((ag999 - previous.silver999PerGram) / previous.silver999PerGram) * 100.0
            } else {
                0.0
            }

            return LiveMarketRates(
                gold24kBuyPerGram = g24,
                gold24kSellPerGram = g24 * 0.985,
                gold22kBuyPerGram = g22,
                gold18kBuyPerGram = g18,
                silver999BuyPerGram = ag999,
                silver999SellPerGram = ag999 * 0.985,
                goldChangePct = goldChange,
                silverChangePct = silverChange,
                goldDayHigh = maxOf(g24, previous?.gold24kPerGram ?: g24),
                goldDayLow = minOf(g24, previous?.gold24kPerGram?.takeIf { it > 0.0 } ?: g24),
                sourceName = latest.source.ifBlank { "Firestore market_price_history" },
                lastUpdatedEpochMs = latest.updatedAt?.toDate()?.time ?: latest.timestampEpochMs,
                isLive = true,
            )
        }

        val BULLION_TRADE_CATEGORIES = setOf("GOLD_BUY", "GOLD_SELL", "SILVER_BUY", "SILVER_SELL")

        fun filterRecentBullionTrades(
            items: List<TransactionRecord>,
            limit: Int = 25,
        ): List<TransactionRecord> =
            items
                .filter { it.category in BULLION_TRADE_CATEGORIES }
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                .take(limit.coerceAtLeast(1))
    }
}
