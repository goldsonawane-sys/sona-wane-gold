package com.example.util

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

enum class BiometricCapability(val label: String, val isUsable: Boolean) {
    STRONG_READY("Fingerprint / Class-3 Biometric Ready", true),
    WEAK_READY("Face / Biometric Unlock Ready", true),
    NOT_ENROLLED("No Biometrics Enrolled on Device", false),
    NO_HARDWARE("No Biometric Hardware Detected", false),
    UNAVAILABLE("Biometric Sensor Temporarily Unavailable", false),
}

object BiometricAuthManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "sonawane_biometric_vault_key"
    const val DEFAULT_BACKGROUND_LOCK_GRACE_MS = 15_000L

    fun checkCapability(context: Context): BiometricCapability {
        val manager = BiometricManager.from(context)
        val strongStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strongStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricCapability.STRONG_READY
        }
        val weakStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return when (weakStatus) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.WEAK_READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    fun hasDeviceCredentialFallback(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun shouldAutoLockAfterBackground(
        requiresAppLock: Boolean,
        isCurrentlyUnlocked: Boolean,
        lastBackgroundedAtMs: Long,
        resumedAtMs: Long,
        biometricPromptInFlight: Boolean,
        gracePeriodMs: Long = DEFAULT_BACKGROUND_LOCK_GRACE_MS,
    ): Boolean {
        if (!requiresAppLock || !isCurrentlyUnlocked || biometricPromptInFlight) return false
        if (lastBackgroundedAtMs <= 0L || resumedAtMs <= lastBackgroundedAtMs) return false
        return (resumedAtMs - lastBackgroundedAtMs) >= gracePeriodMs
    }

    private fun getOrCreateKey(): SecretKey {
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

    private fun createCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            BiometricPrompt.CryptoObject(cipher)
        } catch (_: Exception) {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                keyStore.deleteEntry(KEY_ALIAS)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                BiometricPrompt.CryptoObject(cipher)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun promptBiometric(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use MPIN",
        allowDeviceCredentialFallback: Boolean = false,
        forceDeviceCredential: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val cap = checkCapability(activity)
        val canUseDeviceCred = (allowDeviceCredentialFallback || forceDeviceCredential) && hasDeviceCredentialFallback(activity)
        if (!cap.isUsable && !canUseDeviceCred) {
            onError(cap.label)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val useDeviceCred = canUseDeviceCred && (forceDeviceCredential || !cap.isUsable)
        val cryptoObject = if (!useDeviceCred && cap == BiometricCapability.STRONG_READY) {
            createCryptoObject()
        } else {
            null
        }

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticatedCipher = result.cryptoObject?.cipher
                    if (cryptoObject != null && authenticatedCipher != null) {
                        try {
                            authenticatedCipher.doFinal("sonawane-vault-auth".toByteArray(Charsets.UTF_8))
                        } catch (_: Exception) {
                            onError("Biometric cryptographic verification failed. Please retry or use fallback.")
                            return
                        }
                    }
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onCancel()
                        BiometricPrompt.ERROR_LOCKOUT ->
                            onError("Biometric sensor temporarily locked after too many attempts. Wait 30s or use fallback.")
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            onError("Biometric sensor locked by Android. Unlock your device with PIN/Pattern or use MPIN.")
                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Handled natively by system prompt UI; user can retry or tap negative button
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
}
