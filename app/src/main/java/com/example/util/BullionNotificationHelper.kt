package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object BullionNotificationHelper {

    private const val CHANNEL_PRICE_ALERTS = "sonawane_price_alerts"
    private const val CHANNEL_SECURITY = "sonawane_security_alerts"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alertChannel = NotificationChannel(
            CHANNEL_PRICE_ALERTS,
            "Bullion Price Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications when 24K, 22K, 18K gold or 999 silver meets or crosses your price target."
            enableLights(true)
            lightColor = Color.parseColor("#D4AF37")
            enableVibration(true)
        }

        val securityChannel = NotificationChannel(
            CHANNEL_SECURITY,
            "Vault Security & OTP",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "OTP codes, biometric sign-in alerts, and account security notices."
            enableVibration(true)
        }

        nm.createNotificationChannel(alertChannel)
        nm.createNotificationChannel(securityChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun postPriceAlertPush(
        context: Context,
        title: String,
        body: String,
        playSound: Boolean,
        vibrate: Boolean,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PRICE_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(Color.parseColor("#D4AF37"))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        var defaults = NotificationCompat.DEFAULT_LIGHTS
        if (playSound) defaults = defaults or NotificationCompat.DEFAULT_SOUND
        if (vibrate) defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        builder.setDefaults(defaults)
        if (!playSound) builder.setSilent(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked concurrently; in-app inbox still records alert
        }
    }

    fun postSecurityPush(
        context: Context,
        title: String,
        body: String,
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_SECURITY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setColor(Color.parseColor("#FF8C00"))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(9001, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
