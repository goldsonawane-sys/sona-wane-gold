package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object CryptoUtils {

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashSecret(secret: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$salt:$secret".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifySecret(secret: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank()) return false
        return hashSecret(secret, salt).equals(expectedHash, ignoreCase = true)
    }

    fun generateCustomUserId(uid: String): String {
        val hash = uid.hashCode().let { if (it < 0) -it else it } % 900000 + 100000
        return "SWG$hash"
    }

    fun generateReferralCode(fullName: String, uid: String): String {
        val prefix = fullName.uppercase().filter { it in 'A'..'Z' }.take(4).padEnd(4, 'G')
        val suffix = (uid.hashCode().let { if (it < 0) -it else it } % 9000 + 1000).toString()
        return "$prefix$suffix"
    }

    fun generateCode(prefix: String): String {
        val token = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        return "$prefix-$token"
    }

    fun buildQrPayload(customUserId: String, displayName: String): String {
        return BullionQrModule.generateEncryptedPayload(customUserId, displayName).encryptedToken
    }

    fun parseQrUserId(raw: String): String {
        return BullionQrModule.resolveUserIdOrEmpty(raw)
    }
}
