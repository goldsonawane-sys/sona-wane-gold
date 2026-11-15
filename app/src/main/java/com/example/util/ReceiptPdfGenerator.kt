package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.TransactionRecord
import java.io.File
import java.io.FileOutputStream

object ReceiptPdfGenerator {

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return BullionQrModule.generateQrBitmap(content, sizePx)
    }

    fun saveQrAndShare(context: Context, content: String, userId: String) {
        BullionQrModule.shareEncryptedQr(context, content, userId)
    }

    fun generateTransactionReceiptPdf(context: Context, tx: TransactionRecord, userName: String): Uri? {
        return try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val bgPaint = Paint().apply { color = Color.parseColor("#080808") }
            canvas.drawRect(0f, 0f, 595f, 130f, bgPaint)

            val goldPaint = Paint().apply {
                color = Color.parseColor("#D4AF37")
                textSize = 24f
                isFakeBoldText = true
            }
            val subPaint = Paint().apply {
                color = Color.parseColor("#FF8C00")
                textSize = 12f
                isFakeBoldText = true
            }
            val whitePaint = Paint().apply {
                color = Color.WHITE
                textSize = 12f
            }
            canvas.drawText("SONA-WANE GOLD", 40f, 54f, goldPaint)
            canvas.drawText("TRUST • GOLD • GROWTH", 40f, 76f, subPaint)
            canvas.drawText("OFFICIAL DIGITAL BULLION RECEIPT (${tx.mode} MODE)", 40f, 104f, whitePaint)

            val bodyTitlePaint = Paint().apply {
                color = Color.parseColor("#171717")
                textSize = 15f
                isFakeBoldText = true
            }
            val labelPaint = Paint().apply {
                color = Color.parseColor("#555555")
                textSize = 12f
            }
            val valuePaint = Paint().apply {
                color = Color.parseColor("#111111")
                textSize = 12f
                isFakeBoldText = true
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#DDDDDD")
                strokeWidth = 1f
            }

            var y = 175f
            canvas.drawText("Transaction Summary", 40f, y, bodyTitlePaint)
            y += 14f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 28f

            val rows = listOf(
                "Customer Name" to userName,
                "Transaction ID" to tx.txCode,
                "Category" to tx.category.replace("_", " "),
                "Metal / Purity" to if (tx.metalPurity == "NONE") "INR Wallet" else tx.metalPurity,
                "Quantity" to if (tx.grams > 0.0) Formatters.formatGrams(tx.grams) else "-",
                "Verified Rate / g" to if (tx.ratePerGram > 0.0) Formatters.formatInr(tx.ratePerGram) else "-",
                "Base Amount" to Formatters.formatInr(tx.baseAmount),
                "GST (3%)" to Formatters.formatInr(tx.gstAmount),
                "Platform Charges" to Formatters.formatInr(tx.chargesAmount),
                "Total Amount" to Formatters.formatInr(tx.totalAmount),
                "Payment Method" to tx.paymentMethod.replace("_", " "),
                "Gateway Order ID" to tx.gatewayOrderId,
                "Reference Number" to tx.gatewayRef,
                "Status" to tx.status,
                "Environment Mode" to if (tx.mode == "TEST") "TEST / SANDBOX (NO REAL MONEY)" else "LIVE PRODUCTION",
                "Timestamp" to Formatters.formatTimestamp(tx.createdAt),
            )

            rows.forEach { (k, v) ->
                canvas.drawText(k, 40f, y, labelPaint)
                canvas.drawText(v, 250f, y, valuePaint)
                y += 26f
            }

            y += 16f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f
            canvas.drawText("This is a system-generated ledger receipt from SONA-WANE GOLD.", 40f, y, labelPaint)

            doc.finishPage(page)

            val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val file = File(dir, "${tx.txCode}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun shareReceiptPdf(context: Context, tx: TransactionRecord, userName: String) {
        val uri = generateTransactionReceiptPdf(context, tx, userName) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SONA-WANE GOLD Receipt ${tx.txCode}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Transaction Receipt"))
    }

    fun exportCsvReport(context: Context, fileName: String, csvContent: String) {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(csvContent)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Report"))
    }
}
