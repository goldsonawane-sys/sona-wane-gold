package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customUserId: String = "",
    val fullName: String = "",
    val mobileNumber: String = "",
    val countryCode: String = "+91",
    val email: String = "",
    val dob: String = "",
    val address: String = "",
    val state: String = "",
    val city: String = "",
    val pincode: String = "",
    val panMasked: String = "",
    val photoUri: String = "",
    val kycStatus: String = "NOT_STARTED",
    val accountStatus: String = "ACTIVE",
    val mpinHash: String = "",
    val mpinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val referralCode: String = "",
    val referredByCode: String = "",
    val notifSoundEnabled: Boolean = true,
    val notifVibrationEnabled: Boolean = true,
    val notifPriceAlerts: Boolean = true,
    val notifAnnouncements: Boolean = true,
    val qrToken: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "customUserId" to customUserId,
        "fullName" to fullName.ifBlank { "Gold Investor" },
        "mobileNumber" to mobileNumber,
        "countryCode" to countryCode,
        "email" to email,
        "dob" to dob,
        "address" to address,
        "state" to state,
        "city" to city,
        "pincode" to pincode,
        "panMasked" to panMasked,
        "photoUri" to photoUri,
        "kycStatus" to kycStatus,
        "accountStatus" to accountStatus,
        "mpinHash" to mpinHash,
        "mpinSalt" to mpinSalt,
        "biometricEnabled" to biometricEnabled,
        "referralCode" to referralCode,
        "referredByCode" to referredByCode,
        "notifSoundEnabled" to notifSoundEnabled,
        "notifVibrationEnabled" to notifVibrationEnabled,
        "notifPriceAlerts" to notifPriceAlerts,
        "notifAnnouncements" to notifAnnouncements,
        "qrToken" to qrToken,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@IgnoreExtraProperties
data class DirectoryEntry(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val customUserId: String = "",
    val mobileLast4: String = "",
    val kycApproved: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WalletHoldings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val availableInr: Double = 0.0,
    val pendingInr: Double = 0.0,
    val lockedInr: Double = 0.0,
    val gold24kGrams: Double = 0.0,
    val gold22kGrams: Double = 0.0,
    val gold18kGrams: Double = 0.0,
    val silver999Grams: Double = 0.0,
    val totalInvestedGoldInr: Double = 0.0,
    val totalInvestedSilverInr: Double = 0.0,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val totalGoldGrams: Double get() = gold24kGrams + gold22kGrams + gold18kGrams
}

@IgnoreExtraProperties
data class WalletLedgerEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val entryType: String = "",
    val assetType: String = "",
    val deltaAmount: Double = 0.0,
    val balanceAfter: Double = 0.0,
    val referenceId: String = "",
    val idempotencyKey: String = "",
    val description: String = "",
    val mode: String = "TEST",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class TransactionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val counterpartyUid: String? = null,
    val counterpartyName: String? = null,
    val txCode: String = "",
    val category: String = "",
    val metalPurity: String = "NONE",
    val grams: Double = 0.0,
    val ratePerGram: Double = 0.0,
    val baseAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val chargesAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMethod: String = "WALLET",
    val gatewayOrderId: String = "",
    val gatewayRef: String = "",
    val status: String = "PENDING",
    val mode: String = "TEST",
    val bankAccountId: String? = null,
    val bankNameMasked: String? = null,
    val rejectionReason: String? = null,
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "counterpartyUid" to counterpartyUid,
        "counterpartyName" to counterpartyName,
        "txCode" to txCode,
        "category" to category,
        "metalPurity" to metalPurity,
        "grams" to grams,
        "ratePerGram" to ratePerGram,
        "baseAmount" to baseAmount,
        "gstAmount" to gstAmount,
        "chargesAmount" to chargesAmount,
        "totalAmount" to totalAmount,
        "paymentMethod" to paymentMethod,
        "gatewayOrderId" to gatewayOrderId,
        "gatewayRef" to gatewayRef,
        "status" to status,
        "mode" to mode,
        "bankAccountId" to bankAccountId,
        "bankNameMasked" to bankNameMasked,
        "rejectionReason" to rejectionReason,
        "idempotencyKey" to idempotencyKey,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    ).filterValues { it != null }.mapValues { it.value!! }
}

@IgnoreExtraProperties
data class BankAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val accountHolderName: String = "",
    val bankName: String = "",
    val accountNumberLast4: String = "",
    val accountNumberMasked: String = "",
    val ifscCode: String = "",
    val verificationStatus: String = "VERIFIED",
    @get:PropertyName("isPrimary")
    val isPrimary: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class KycRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val mobileNumber: String = "",
    val panNumber: String = "",
    val aadhaarLast4: String = "",
    val documentType: String = "PAN_CARD",
    val documentRefUri: String? = null,
    val documentFrontBase64: String? = null,
    val documentBackBase64: String? = null,
    val captureMethod: String = "CAMERAX_SCAN",
    val scanQualityScore: Int = 0,
    val verificationStep: Int = 3,
    val declarationAccepted: Boolean = false,
    val addressLine: String = "",
    val state: String = "",
    val city: String = "",
    val pincode: String = "",
    val status: String = "NOT_STARTED",
    val rejectionReason: String? = null,
    val reviewedByAdminId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DeviceSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val sessionActive: Boolean = true,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LoginHistoryEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val status: String = "SUCCESS",
    val method: String = "GOOGLE_SIGN_IN",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PriceAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val metal: String = "GOLD_24K",
    val targetPriceInr: Double = 0.0,
    val direction: String = "BELOW",
    val frequency: String = "ONCE",
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    val notes: String = "",
    val triggerCount: Int = 0,
    val lastTriggeredPrice: Double = 0.0,
    val lastObservedPrice: Double = 0.0,
    val triggeredAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "ANNOUNCEMENT",
    val title: String = "",
    val body: String = "",
    @get:PropertyName("isRead")
    val isRead: Boolean = false,
    val referenceId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class UserTransfer(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val transferCode: String = "",
    val assetType: String = "GOLD_24K",
    val quantity: Double = 0.0,
    val feeInr: Double = 0.0,
    val status: String = "COMPLETED",
    val recipientCredited: Boolean = false,
    val idempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val ticketCode: String = "",
    val category: String = "",
    val subject: String = "",
    val description: String = "",
    val attachmentUri: String? = null,
    val status: String = "OPEN",
    val assignedAdminId: String? = null,
    val messages: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ReferralRecord(
    @DocumentId val id: String = "",
    val referrerUid: String = "",
    val referredUid: String = "",
    val referredName: String = "",
    val referralCode: String = "",
    val rewardAmountInr: Double = 0.0,
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AdminUser(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val operatorId: String = "",
    val role: String = "ADMIN",
    val permissions: List<String> = emptyList(),
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    val adminPinHash: String? = null,
    val biometricAdminEnabled: Boolean = false,
    val lastLoginAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun hasPermission(permission: String): Boolean {
        if (!isActive) return false
        return "ALL" in permissions || permission in permissions
    }
}

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val adminRole: String = "",
    val action: String = "",
    val affectedCollection: String = "",
    val affectedRecordId: String = "",
    val previousValue: String = "",
    val newValue: String = "",
    val reason: String = "",
    val deviceInfo: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "",
    val appName: String = "SONA-WANE GOLD",
    val tagline: String = "TRUST • GOLD • GROWTH",
    val maintenanceMode: Boolean = false,
    val paymentMode: String = "TEST",
    val paymentGatewayName: String = "Razorpay Sandbox",
    val goldProviderName: String = "Augmont / MMTC-PAMP Sandbox",
    val gstPercent: Double = 3.0,
    val goldSpreadPercent: Double = 1.5,
    val silverSpreadPercent: Double = 2.0,
    val withdrawalFeeInr: Double = 0.0,
    val transferFeePercent: Double = 0.0,
    val referralRewardInr: Double = 101.0,
    val referralEnabled: Boolean = true,
    val termsText: String = "All digital gold and silver trades are governed by applicable Indian bullion compliance, 3% GST regulations, and KYC verification norms.",
    val privacyText: String = "SONA-WANE GOLD encrypts sensitive banking & KYC identifiers and never stores card PINs, CVVs, or UPI PINs.",
    val supportEmail: String = "support@sonawanegold.in",
    val supportPhone: String = "+91 1800-103-GOLD",
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val priority: String = "INFO",
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class LiveMarketRates(
    val gold24kBuyPerGram: Double = 0.0,
    val gold24kSellPerGram: Double = 0.0,
    val gold22kBuyPerGram: Double = 0.0,
    val gold18kBuyPerGram: Double = 0.0,
    val silver999BuyPerGram: Double = 0.0,
    val silver999SellPerGram: Double = 0.0,
    val usdToInr: Double = 0.0,
    val xauUsdPerOz: Double = 0.0,
    val xagUsdPerOz: Double = 0.0,
    val goldChangePct: Double = 0.0,
    val silverChangePct: Double = 0.0,
    val sourceName: String = "Connecting...",
    val isLive: Boolean = false,
    val isDelayed: Boolean = false,
    val errorMessage: String? = null,
    val lastUpdatedEpochMs: Long = 0L,
    val goldDayHigh: Double = 0.0,
    val goldDayLow: Double = 0.0,
    val silverDayHigh: Double = 0.0,
    val silverDayLow: Double = 0.0,
)

data class MarketPoint(
    val label: String,
    val price: Double,
)

data class DualMetalTrendSeries(
    val goldPoints: List<MarketPoint> = emptyList(),
    val silverPoints: List<MarketPoint> = emptyList(),
    val goldNormalizedPct: List<MarketPoint> = emptyList(),
    val silverNormalizedPct: List<MarketPoint> = emptyList(),
    val gold30dHigh: Double = 0.0,
    val gold30dLow: Double = 0.0,
    val gold30dAvg: Double = 0.0,
    val gold30dChangePct: Double = 0.0,
    val silver30dHigh: Double = 0.0,
    val silver30dLow: Double = 0.0,
    val silver30dAvg: Double = 0.0,
    val silver30dChangePct: Double = 0.0,
    val rangeKey: String = "30D",
    val lastSyncedEpochMs: Long = 0L,
)

@IgnoreExtraProperties
data class MarketPriceHistoryEntry(
    @DocumentId val id: String = "",
    val dateStr: String = "",
    val label: String = "",
    val gold24kPerGram: Double = 0.0,
    val gold22kPerGram: Double = 0.0,
    val gold18kPerGram: Double = 0.0,
    val silver999PerGram: Double = 0.0,
    val source: String = "VERIFIED_SPOT_SNAPSHOT",
    val timestampEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class FirestoreGoldSummary(
    val gold24kPerGram: Double = 0.0,
    val gold22kPerGram: Double = 0.0,
    val gold18kPerGram: Double = 0.0,
    val previous24kPerGram: Double = 0.0,
    val changePercent: Double = 0.0,
    val dateStr: String = "",
    val source: String = "Firestore market_price_history",
    val lastUpdatedEpochMs: Long = 0L,
    val isLoading: Boolean = true,
    val isFromFirestore: Boolean = false,
    val errorMessage: String? = null,
)
