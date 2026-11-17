package com.example.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.data.local.SecurityPreferences
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ProtectedFeature(val displayName: String) {
    APP_LOGIN("App Vault"),
    WALLET_ACCESS("Digital Wallet"),
    TRADING_EXECUTION("Gold & Silver Trading"),
}

data class BiometricAuthPolicy(
    val feature: ProtectedFeature,
    val hardwareAvailable: Boolean,
    val capability: BiometricCapability,
    val requiresAuthentication: Boolean,
    val canPromptBiometric: Boolean,
    val canFallbackToDeviceCredential: Boolean,
    val canFallbackToMpin: Boolean,
)

sealed class BiometricLoginResult {
    data class Success(val feature: ProtectedFeature, val usedCryptoObject: Boolean) : BiometricLoginResult()
    data class Error(val feature: ProtectedFeature, val message: String) : BiometricLoginResult()
    data class Cancelled(val feature: ProtectedFeature) : BiometricLoginResult()
}

/**
 * Coordinates AndroidX BiometricPrompt + BiometricManager authentication specifically
 * for app login, protected wallet vault access, and digital bullion trading execution.
 */
class BiometricLoginManager(
    private val context: Context,
    @Suppress("unused") private val prefs: SecurityPreferences? = null,
) {

    private val _walletSessionAuthorized = MutableStateFlow(false)
    val walletSessionAuthorized: StateFlow<Boolean> = _walletSessionAuthorized.asStateFlow()

    private val _lastTradeAuthorizedAtMs = MutableStateFlow(0L)
    val lastTradeAuthorizedAtMs: StateFlow<Long> = _lastTradeAuthorizedAtMs.asStateFlow()

    fun getCapability(): BiometricCapability = checkBiometricCapability(context)

    fun canAuthenticateWithBiometrics(): Boolean = getCapability().isUsable

    fun hasDeviceCredentialFallback(): Boolean = hasDeviceCredentialSupport(context)

    fun evaluateWalletPolicy(
        userBiometricEnabled: Boolean,
        walletLockEnabled: Boolean,
        hasMpin: Boolean,
    ): BiometricAuthPolicy {
        return buildFeaturePolicy(
            feature = ProtectedFeature.WALLET_ACCESS,
            capability = getCapability(),
            hasDeviceCredential = hasDeviceCredentialFallback(),
            userBiometricEnabled = userBiometricEnabled,
            featureLockEnabled = walletLockEnabled,
            hasMpin = hasMpin,
            alreadyAuthorizedInSession = _walletSessionAuthorized.value,
        )
    }

    fun evaluateTradingPolicy(
        userBiometricEnabled: Boolean,
        tradeConfirmEnabled: Boolean,
        hasMpin: Boolean,
    ): BiometricAuthPolicy {
        return buildFeaturePolicy(
            feature = ProtectedFeature.TRADING_EXECUTION,
            capability = getCapability(),
            hasDeviceCredential = hasDeviceCredentialFallback(),
            userBiometricEnabled = userBiometricEnabled,
            featureLockEnabled = tradeConfirmEnabled,
            hasMpin = hasMpin,
            alreadyAuthorizedInSession = false, // Per-trade confirmation by policy
        )
    }

    fun markWalletAuthorized() {
        _walletSessionAuthorized.value = true
    }

    fun lockWalletSession() {
        _walletSessionAuthorized.value = false
    }

    fun markTradeAuthorized(nowMs: Long = System.currentTimeMillis()) {
        _lastTradeAuthorizedAtMs.value = nowMs
    }

    fun isTradeSessionValid(nowMs: Long = System.currentTimeMillis(), windowMs: Long = TRADE_GRACE_WINDOW_MS): Boolean {
        val last = _lastTradeAuthorizedAtMs.value
        return last > 0L && nowMs >= last && (nowMs - last) <= windowMs
    }

    fun authenticateForAppLogin(
        activity: FragmentActivity,
        title: String = "Unlock SONA-WANE GOLD",
        subtitle: String = "Verify your fingerprint or face to access your account",
        allowDeviceCredentialFallback: Boolean = true,
        forceDeviceCredential: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        authenticateFeature(
            activity = activity,
            feature = ProtectedFeature.APP_LOGIN,
            title = title,
            subtitle = subtitle,
            negativeButtonText = if (allowDeviceCredentialFallback) "Cancel" else "Use MPIN",
            allowDeviceCredentialFallback = allowDeviceCredentialFallback,
            forceDeviceCredential = forceDeviceCredential,
        ) { result ->
            when (result) {
                is BiometricLoginResult.Success -> onSuccess()
                is BiometricLoginResult.Error -> onError(result.message)
                is BiometricLoginResult.Cancelled -> onCancel()
            }
        }
    }

    fun authenticateForWalletAccess(
        activity: FragmentActivity,
        title: String = "Unlock Digital Wallet",
        subtitle: String = "Verify Fingerprint or Face to view INR & bullion balances",
        allowDeviceCredentialFallback: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        authenticateFeature(
            activity = activity,
            feature = ProtectedFeature.WALLET_ACCESS,
            title = title,
            subtitle = subtitle,
            negativeButtonText = "Use MPIN",
            allowDeviceCredentialFallback = allowDeviceCredentialFallback,
        ) { result ->
            when (result) {
                is BiometricLoginResult.Success -> {
                    markWalletAuthorized()
                    onSuccess()
                }
                is BiometricLoginResult.Error -> onError(result.message)
                is BiometricLoginResult.Cancelled -> onCancel()
            }
        }
    }

    fun authenticateForTrading(
        activity: FragmentActivity,
        title: String = "Authorize Bullion Trade",
        subtitle: String = "Confirm Fingerprint or Face to execute your order",
        allowDeviceCredentialFallback: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        authenticateFeature(
            activity = activity,
            feature = ProtectedFeature.TRADING_EXECUTION,
            title = title,
            subtitle = subtitle,
            negativeButtonText = "Use MPIN",
            allowDeviceCredentialFallback = allowDeviceCredentialFallback,
        ) { result ->
            when (result) {
                is BiometricLoginResult.Success -> {
                    markTradeAuthorized()
                    onSuccess()
                }
                is BiometricLoginResult.Error -> onError(result.message)
                is BiometricLoginResult.Cancelled -> onCancel()
            }
        }
    }

    fun authenticateFeature(
        activity: FragmentActivity,
        feature: ProtectedFeature,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use MPIN",
        allowDeviceCredentialFallback: Boolean = false,
        forceDeviceCredential: Boolean = false,
        onResult: (BiometricLoginResult) -> Unit,
    ) {
        val capability = checkBiometricCapability(activity)
        val hasDeviceCred = hasDeviceCredentialSupport(activity)
        val canUseDeviceCred = (allowDeviceCredentialFallback || forceDeviceCredential) && hasDeviceCred

        if (!capability.isUsable && !canUseDeviceCred) {
            onResult(BiometricLoginResult.Error(feature, capability.label))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val useDeviceCred = canUseDeviceCred && (forceDeviceCredential || !capability.isUsable)
        val cryptoObject = if (!useDeviceCred && capability == BiometricCapability.STRONG_READY) {
            createVaultCryptoObject()
        } else {
            null
        }

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val cipher = result.cryptoObject?.cipher
                    if (cryptoObject != null && cipher != null) {
                        try {
                            cipher.doFinal("sonawane-${feature.name.lowercase()}".toByteArray(Charsets.UTF_8))
                        } catch (_: Exception) {
                            onResult(
                                BiometricLoginResult.Error(
                                    feature,
                                    "Biometric cryptographic verification failed for ${feature.displayName}. Please retry or use MPIN.",
                                )
                            )
                            return
                        }
                    }
                    onResult(BiometricLoginResult.Success(feature, cryptoObject != null))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onResult(BiometricLoginResult.Cancelled(feature))
                        BiometricPrompt.ERROR_LOCKOUT -> onResult(
                            BiometricLoginResult.Error(
                                feature,
                                "Biometric sensor temporarily locked. Wait 30s or use MPIN.",
                            )
                        )
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> onResult(
                            BiometricLoginResult.Error(
                                feature,
                                "Biometric sensor locked by system. Unlock device or use MPIN.",
                            )
                        )
                        else -> onResult(BiometricLoginResult.Error(feature, errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    // System biometric dialog reports mismatch natively so user can retry.
                }
            },
        )

        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(true)

        if (useDeviceCred && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            infoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            val authenticators = if (cryptoObject != null) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            infoBuilder
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(authenticators)
        }

        val info = infoBuilder.build()
        if (cryptoObject != null) {
            prompt.authenticate(info, cryptoObject)
        } else {
            prompt.authenticate(info)
        }
    }

    private fun createVaultCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateVaultKey())
            BiometricPrompt.CryptoObject(cipher)
        } catch (_: Exception) {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                keyStore.deleteEntry(KEY_ALIAS)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateVaultKey())
                BiometricPrompt.CryptoObject(cipher)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun getOrCreateVaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "sonawane_biometric_login_key"
        const val TRADE_GRACE_WINDOW_MS = 120_000L

        fun checkBiometricCapability(context: Context): BiometricCapability {
            val manager = BiometricManager.from(context)
            val strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            if (strong == BiometricManager.BIOMETRIC_SUCCESS) {
                return BiometricCapability.STRONG_READY
            }
            return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.WEAK_READY
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
                else -> BiometricCapability.UNAVAILABLE
            }
        }

        fun hasDeviceCredentialSupport(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
            val manager = BiometricManager.from(context)
            return manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }

        fun buildFeaturePolicy(
            feature: ProtectedFeature,
            capability: BiometricCapability,
            hasDeviceCredential: Boolean,
            userBiometricEnabled: Boolean,
            featureLockEnabled: Boolean,
            hasMpin: Boolean,
            alreadyAuthorizedInSession: Boolean,
        ): BiometricAuthPolicy {
            val canPromptBio = capability.isUsable
            val requiresAuth = when (feature) {
                ProtectedFeature.APP_LOGIN ->
                    (userBiometricEnabled || featureLockEnabled) && !alreadyAuthorizedInSession
                ProtectedFeature.WALLET_ACCESS ->
                    userBiometricEnabled && featureLockEnabled && !alreadyAuthorizedInSession
                ProtectedFeature.TRADING_EXECUTION ->
                    userBiometricEnabled && featureLockEnabled
            }
            return BiometricAuthPolicy(
                feature = feature,
                hardwareAvailable = canPromptBio || hasDeviceCredential,
                capability = capability,
                requiresAuthentication = requiresAuth,
                canPromptBiometric = canPromptBio,
                canFallbackToDeviceCredential = hasDeviceCredential,
                canFallbackToMpin = hasMpin,
            )
        }
    }
}
