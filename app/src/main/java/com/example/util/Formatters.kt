package com.example.util

import com.google.firebase.Timestamp
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val inrLocale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val currencyFormat = NumberFormat.getCurrencyInstance(inrLocale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }
    private val gramFormat = DecimalFormat("0.0000")
    private val compactGramFormat = DecimalFormat("0.000")
    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun formatInr(amount: Double): String {
        return currencyFormat.format(amount)
    }

    /**
     * Converts an INR amount to the target [locale]'s local currency using [usdToInr]
     * (defaulting to 84.5 INR/USD if live FX is unavailable) and returns formatted currency string.
     */
    fun localCurrencyCode(locale: Locale = Locale.getDefault()): String {
        return runCatching { java.util.Currency.getInstance(locale).currencyCode }
            .getOrElse { "INR" }
            .ifBlank { "INR" }
    }

    fun convertInrToLocalCurrency(
        amountInr: Double,
        usdToInr: Double = 84.5,
        locale: Locale = Locale.getDefault(),
    ): Double {
        val code = localCurrencyCode(locale)
        if (code == "INR") return amountInr
        val effectiveUsdInr = if (usdToInr > 1.0) usdToInr else 84.5
        val amountUsd = amountInr / effectiveUsdInr
        // Approximate USD -> major local currency factor when non-INR locale is active
        val usdToTarget = when (code) {
            "USD" -> 1.0
            "EUR" -> 0.92
            "GBP" -> 0.79
            "AED" -> 3.6725
            "SGD" -> 1.34
            "AUD" -> 1.53
            "CAD" -> 1.39
            "JPY" -> 153.0
            "CHF" -> 0.88
            else -> return amountInr
        }
        return amountUsd * usdToTarget
    }

    fun formatLocalCurrency(
        amountInr: Double,
        usdToInr: Double = 84.5,
        locale: Locale = Locale.getDefault(),
    ): String {
        val code = localCurrencyCode(locale)
        val supportedForeign = setOf("USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY", "CHF")
        if (code == "INR" || code !in supportedForeign) {
            return formatInr(amountInr)
        }
        val converted = convertInrToLocalCurrency(amountInr, usdToInr, locale)
        return runCatching {
            val nf = NumberFormat.getCurrencyInstance(locale)
            if (code == "JPY") {
                nf.maximumFractionDigits = 0
                nf.minimumFractionDigits = 0
            } else {
                nf.maximumFractionDigits = 2
                nf.minimumFractionDigits = 2
            }
            nf.format(converted)
        }.getOrElse { formatInr(amountInr) }
    }

    fun formatGrams(grams: Double, compact: Boolean = false): String {
        return "${(if (compact) compactGramFormat else gramFormat).format(grams)} g"
    }

    fun formatTimestamp(ts: Timestamp?): String {
        val date = ts?.toDate() ?: Date()
        return dateTimeFormat.format(date)
    }

    fun formatEpoch(epochMillis: Long): String {
        if (epochMillis <= 0L) return "Not synced yet"
        return dateTimeFormat.format(Date(epochMillis))
    }

    fun formatShortDate(ts: Timestamp?): String {
        val date = ts?.toDate() ?: Date()
        return shortDateFormat.format(date)
    }

    fun maskAccount(accountNumber: String): String {
        val clean = accountNumber.filter { it.isDigit() }
        if (clean.length <= 4) return "XXXX$clean"
        return "XXXX XXXX ${clean.takeLast(4)}"
    }

    fun maskPan(pan: String): String {
        val clean = pan.trim().uppercase()
        if (clean.length != 10) return clean
        return "${clean.take(2)}XXXX${clean.takeLast(4)}"
    }
}
