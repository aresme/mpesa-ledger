package com.ares.mpesaledger.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ares.mpesaledger.MainActivity
import com.ares.mpesaledger.R

/**
 * Two channels: "Budgets" (80% / 100% alerts) and "Transactions" (one line per live SMS,
 * only when switched on in Settings). Content is hidden on the lock screen: amounts are
 * personal, so the public version just says something happened.
 */
object Notifications {
    const val CHANNEL_BUDGETS = "budgets"
    const val CHANNEL_TRANSACTIONS = "transactions"
    const val CHANNEL_BILLS = "bills"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGETS, "Budgets", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a category reaches 80% or 100% of its monthly budget"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BILLS, "Bills", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A reminder two days before a repeat payment is usually due"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRANSACTIONS, "Transactions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "One line for each new M-Pesa transaction (off unless enabled in Settings)"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, channel: String, id: Int, title: String, text: String, publicTitle: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(publicTitle)
            .build()
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4DFF7A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }
}
