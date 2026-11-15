package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedQrEnvelope(
    val userId: String,
    val displayName: String,
    val issuedAtEpochSec: Long,
    val nonce: String,
    val encryptedToken: String,
)

/**
 * Modular ZXing + AES-256-GCM encrypted QR code generator and verifier
 * for sharing SONA-WANE GOLD User IDs securely in peer-to-peer gold and silver transfers.
 */
object BullionQrModule {

    private const val TOKEN_PREFIX = "SWGQR2."
    private const val KEY_DERIVATION_CONTEXT = "SONA-WANE-GOLD:P2P-QR:V2:AES-256-GCM"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()

    private val sharedAesKey: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(KEY_DERIVATION_CONTEXT.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    /**
     * Encrypts [customUserId] and truncated [displayName] with a fresh random 96-bit IV and nonce
     * using AES-256-GCM authenticated encryption, producing a unique QR token per call.
     */
    fun generateEncryptedPayload(
        customUserId: String,
        displayName: String = "",
        epochSec: Long = System.currentTimeMillis() / 1000L,
    ): EncryptedQrEnvelope {
        val cleanId = customUserId.trim().replace("|", "").take(32)
        val cleanName = displayName.trim().replace("|", " ").take(24)
        val nonce = generateNonceHex(6)
        val plainText = "$cleanId|$epochSec|$nonce|$cleanName"

        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedAesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        val token = TOKEN_PREFIX + encodeBase64Url(combined)
        return EncryptedQrEnvelope(
            userId = cleanId,
            displayName = cleanName,
            issuedAtEpochSec = epochSec,
            nonce = nonce,
            encryptedToken = token,
        )
    }

    /**
     * Decrypts and verifies an `SWGQR2.` AES-256-GCM token scanned from ZXing or pasted by user.
     * Returns null if authentication fails or payload is malformed/tampered.
     */
    fun decryptQrPayload(raw: String): EncryptedQrEnvelope? {
        val trimmed = raw.trim()
        val token = when {
            trimmed.startsWith(TOKEN_PREFIX) -> trimmed
            trimmed.contains("token=$TOKEN_PREFIX") ->
                trimmed.substringAfter("token=", "").substringBefore("&")
            else -> return null
        }
        if (!token.startsWith(TOKEN_PREFIX)) return null

        return runCatching {
            val encoded = token.removePrefix(TOKEN_PREFIX)
            val combined = decodeBase64Url(encoded) ?: return null
            if (combined.size <= GCM_IV_BYTES + 16) return null

            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_BYTES, combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, sharedAesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plainText = String(cipher.doFinal(cipherBytes), Charsets.UTF_8)

            val parts = plainText.split("|")
            if (parts.size < 3) return null
            val uid = parts[0].trim()
            if (uid.isBlank()) return null
            val issuedSec = parts[1].toLongOrNull() ?: 0L
            val nonce = parts[2].trim()
            val name = parts.getOrNull(3)?.trim().orEmpty()

            EncryptedQrEnvelope(
                userId = uid,
                displayName = name,
                issuedAtEpochSec = issuedSec,
                nonce = nonce,
                encryptedToken = token,
            )
        }.getOrNull()
    }

    /**
     * Resolves an extracted User ID from an encrypted `SWGQR2.` token, legacy deep link, or direct User ID.
     * Returns empty string if a token claims to be `SWGQR2.` but fails GCM authentication (tampered).
     */
    fun resolveUserIdOrEmpty(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith(TOKEN_PREFIX) || trimmed.contains("token=$TOKEN_PREFIX")) {
            return decryptQrPayload(trimmed)?.userId.orEmpty()
        }
        if (trimmed.startsWith("sonawanegold://transfer")) {
            val idParam = trimmed.substringAfter("id=", "").substringBefore("&")
            if (idParam.isNotBlank()) return idParam.trim()
        }
        return trimmed
    }

    /**
     * Uses ZXing [QRCodeWriter] with medium error correction to encode [content] into a crisp QR bitmap.
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 560,
        foregroundColor: Int = Color.parseColor("#080808"),
        backgroundColor: Int = Color.WHITE,
    ): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1,
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val w = matrix.width
            val h = matrix.height
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val rowOffset = y * w
                for (x in 0 until w) {
                    pixels[rowOffset + x] = if (matrix[x, y]) foregroundColor else backgroundColor
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareEncryptedQr(context: Context, encryptedPayload: String, customUserId: String) {
        val bitmap = generateQrBitmap(encryptedPayload, 760) ?: return
        val dir = File(context.cacheDir, "qr").apply { mkdirs() }
        val file = File(dir, "sonawane_encrypted_qr_$customUserId.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "Scan this encrypted SONA-WANE GOLD QR code in the app to send Gold, Silver, or Wallet transfers securely. Verified ID: $customUserId",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Encrypted Bullion QR"))
    }

    private fun generateNonceHex(charCount: Int): String {
        val bytes = ByteArray((charCount + 1) / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }.take(charCount)
    }

    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun encodeBase64Url(input: ByteArray): String {
        val out = StringBuilder((input.size * 4 + 2) / 3)
        var i = 0
        while (i < input.size) {
            val b0 = input[i].toInt() and 0xFF
            val hasB1 = i + 1 < input.size
            val hasB2 = i + 2 < input.size
            val b1 = if (hasB1) input[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) input[i + 2].toInt() and 0xFF else 0

            val triple = (b0 shl 16) or (b1 shl 8) or b2
            out.append(BASE64_URL_ALPHABET[(triple ushr 18) and 0x3F])
            out.append(BASE64_URL_ALPHABET[(triple ushr 12) and 0x3F])
            if (hasB1) out.append(BASE64_URL_ALPHABET[(triple ushr 6) and 0x3F])
            if (hasB2) out.append(BASE64_URL_ALPHABET[triple and 0x3F])
            i += 3
        }
        return out.toString()
    }

    private fun decodeBase64Url(input: String): ByteArray? {
        if (input.isEmpty()) return ByteArray(0)
        val clean = input.trimEnd('=')
        val mod = clean.length % 4
        if (mod == 1) return null

        val outLen = (clean.length * 3) / 4
        val result = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = decodeChar(clean[i]) ?: return null
            val c1 = if (i + 1 < clean.length) decodeChar(clean[i + 1]) ?: return null else 0
            val hasC2 = i + 2 < clean.length
            val hasC3 = i + 3 < clean.length
            val c2 = if (hasC2) decodeChar(clean[i + 2]) ?: return null else 0
            val c3 = if (hasC3) decodeChar(clean[i + 3]) ?: return null else 0

            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIdx < outLen) result[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
            if (hasC2 && outIdx < outLen) result[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
            if (hasC3 && outIdx < outLen) result[outIdx++] = (triple and 0xFF).toByte()
            i += 4
        }
        return result
    }

    private fun decodeChar(c: Char): Int? = when (c) {
        in 'A'..'Z' -> c - 'A'
        in 'a'..'z' -> c - 'a' + 26
        in '0'..'9' -> c - '0' + 52
        '-', '+' -> 62
        '_', '/' -> 63
        else -> null
    }
}
