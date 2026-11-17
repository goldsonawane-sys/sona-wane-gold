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
    val walletAddress: String = "",
    val displayName: String,
    val issuedAtEpochSec: Long,
    val nonce: String,
    val encryptedToken: String,
    val requestedAsset: String = "",
    val requestedGrams: Double = 0.0,
)

data class QrTransferPrefill(
    val userId: String,
    val walletAddress: String,
    val displayName: String = "",
    val requestedAsset: String = "",
    val requestedGrams: Double = 0.0,
    val isEncryptedVerified: Boolean = false,
)

/**
 * Modular ZXing + AES-256-GCM encrypted QR code generator and verifier
 * for sharing SONA-WANE GOLD wallet addresses securely in peer-to-peer gold and silver transfers.
 */
object BullionQrModule {

    private const val TOKEN_PREFIX = "SWGQR2."
    private const val WALLET_ADDRESS_PREFIX = "SWG-VAULT-"
    private const val KEY_DERIVATION_CONTEXT = "SONA-WANE-GOLD:P2P-QR:V2:AES-256-GCM"
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()

    private val sharedAesKey: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(KEY_DERIVATION_CONTEXT.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    fun formatWalletAddress(customUserId: String): String {
        val cleanId = customUserId.trim().uppercase().removePrefix(WALLET_ADDRESS_PREFIX)
        return if (cleanId.isBlank()) "${WALLET_ADDRESS_PREFIX}SWG000000" else "$WALLET_ADDRESS_PREFIX$cleanId"
    }

    /**
     * Encrypts [customUserId], optional [displayName], and optional gold transfer request ([requestedAsset],
     * [requestedGrams]) with a fresh random 96-bit IV and nonce using AES-256-GCM authenticated encryption,
     * producing a unique QR token per call.
     */
    fun generateEncryptedPayload(
        customUserId: String,
        displayName: String = "",
        epochSec: Long = System.currentTimeMillis() / 1000L,
        requestedAsset: String = "",
        requestedGrams: Double = 0.0,
    ): EncryptedQrEnvelope {
        val cleanId = customUserId.trim().uppercase().removePrefix(WALLET_ADDRESS_PREFIX).replace("|", "").take(32)
        val walletAddr = formatWalletAddress(cleanId)
        val cleanName = displayName.trim().replace("|", " ").take(24)
        val cleanAsset = requestedAsset.trim().uppercase().replace("|", "").take(16)
        val safeGrams = requestedGrams.takeIf { it > 0.0 && it.isFinite() } ?: 0.0
        val nonce = generateNonceHex(6)
        val plainText = buildString {
            append("$cleanId|$epochSec|$nonce|$cleanName")
            if (cleanAsset.isNotBlank() || safeGrams > 0.0) {
                append("|")
                append(cleanAsset.ifBlank { "GOLD_24K" })
                append("|")
                append(if (safeGrams > 0.0) String.format(java.util.Locale.US, "%.4f", safeGrams).trimEnd('0').trimEnd('.') else "0")
            }
        }

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
            walletAddress = walletAddr,
            displayName = cleanName,
            issuedAtEpochSec = epochSec,
            nonce = nonce,
            encryptedToken = token,
            requestedAsset = cleanAsset,
            requestedGrams = safeGrams,
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
            val uid = parts[0].trim().removePrefix(WALLET_ADDRESS_PREFIX)
            if (uid.isBlank()) return null
            val issuedSec = parts[1].toLongOrNull() ?: 0L
            val nonce = parts[2].trim()
            val name = parts.getOrNull(3)?.trim().orEmpty()
            val reqAsset = parts.getOrNull(4)?.trim().orEmpty()
            val reqGrams = parts.getOrNull(5)?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0

            EncryptedQrEnvelope(
                userId = uid,
                walletAddress = formatWalletAddress(uid),
                displayName = name,
                issuedAtEpochSec = issuedSec,
                nonce = nonce,
                encryptedToken = token,
                requestedAsset = reqAsset,
                requestedGrams = reqGrams,
            )
        }.getOrNull()
    }

    /**
     * Resolves an extracted User ID from an encrypted `SWGQR2.` token, wallet address (`SWG-VAULT-...`),
     * legacy deep link, or direct User ID. Returns empty string if a token claims to be `SWGQR2.`
     * but fails GCM authentication (tampered).
     */
    fun resolveUserIdOrEmpty(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith(TOKEN_PREFIX) || trimmed.contains("token=$TOKEN_PREFIX")) {
            return decryptQrPayload(trimmed)?.userId.orEmpty()
        }
        if (trimmed.startsWith("sonawanegold://transfer")) {
            val idParam = trimmed.substringAfter("id=", "").substringBefore("&")
            if (idParam.isNotBlank()) return idParam.trim().uppercase().removePrefix(WALLET_ADDRESS_PREFIX)
        }
        if (trimmed.uppercase().startsWith(WALLET_ADDRESS_PREFIX)) {
            return trimmed.uppercase().removePrefix(WALLET_ADDRESS_PREFIX).trim()
        }
        return trimmed
    }

    fun resolveTransferPrefill(raw: String): QrTransferPrefill? {
        val trimmed = raw.trim()
        if (trimmed.startsWith(TOKEN_PREFIX) || trimmed.contains("token=$TOKEN_PREFIX")) {
            val env = decryptQrPayload(trimmed) ?: return null
            return QrTransferPrefill(
                userId = env.userId,
                walletAddress = env.walletAddress,
                displayName = env.displayName,
                requestedAsset = env.requestedAsset,
                requestedGrams = env.requestedGrams,
                isEncryptedVerified = true,
            )
        }
        val uid = resolveUserIdOrEmpty(trimmed)
        if (uid.isBlank()) return null
        val asset = if (trimmed.contains("asset=")) trimmed.substringAfter("asset=", "").substringBefore("&") else ""
        val grams = if (trimmed.contains("grams=")) trimmed.substringAfter("grams=", "").substringBefore("&").toDoubleOrNull() ?: 0.0 else 0.0
        return QrTransferPrefill(
            userId = uid,
            walletAddress = formatWalletAddress(uid),
            requestedAsset = asset,
            requestedGrams = grams,
            isEncryptedVerified = false,
        )
    }

    fun formatAssetLabel(asset: String): String = when (asset.trim().uppercase()) {
        "GOLD_24K" -> "24K Gold"
        "GOLD_22K" -> "22K Gold"
        "GOLD_18K" -> "18K Gold"
        "SILVER_999" -> "999 Silver"
        "INR" -> "INR Wallet"
        else -> "Gold & Silver"
    }

    /**
     * Uses ZXing [QRCodeWriter] with medium error correction to encode [content] into a crisp QR bitmap.
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 560,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
    ): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 4,
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

    /**
     * Uses ZXing [MultiFormatReader] + [RGBLuminanceSource] + [HybridBinarizer] / [com.google.zxing.common.GlobalHistogramBinarizer]
     * to decode a QR code from a [Bitmap] (such as a gallery photo picked via Android Photo Picker or an in-app QR graphic).
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 0) return null
        val targetBitmap = if (maxSide > 1200) {
            val scale = 1200.0f / maxSide.toFloat()
            val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val w = targetBitmap.width
        val h = targetBitmap.height
        val pixels = IntArray(w * h)
        targetBitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val hints = mapOf(
            com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            com.google.zxing.DecodeHintType.TRY_HARDER to true,
        )
        val reader = com.google.zxing.MultiFormatReader().apply { setHints(hints) }
        val source = com.google.zxing.RGBLuminanceSource(w, h, pixels)

        val candidates = listOf(
            com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)),
            com.google.zxing.BinaryBitmap(com.google.zxing.common.GlobalHistogramBinarizer(source)),
            com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source.invert())),
        )
        for (candidate in candidates) {
            val decoded = runCatching { reader.decodeWithState(candidate).text }.getOrNull()
            reader.reset()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return null
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
