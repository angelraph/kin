package app.kin.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kin.MainActivity
import app.kin.R

object Notifier {
    private const val CHANNEL_ID = "circle_updates"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Circle updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Payments due, autopay ready, and payouts"
                },
            )
        }
    }

    /** Posts [alert]. Returns false if notifications are not allowed, so the caller does not mark it as shown. */
    fun post(context: Context, alert: Alert): Boolean {
        if (!canPost(context)) return false
        ensureChannel(context)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("kin://circle/${alert.circle.toBase58()}"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, alert.key.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(alert.key.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            // The user can revoke the permission at any moment, including between the check above and now.
            false
        }
    }
}
