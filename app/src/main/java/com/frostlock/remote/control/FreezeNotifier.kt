package com.frostlock.remote.control

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
import com.frostlock.remote.MainActivity
import com.frostlock.remote.R
import com.frostlock.remote.data.FreezeSnapshot
import com.frostlock.remote.data.PcProfile

/**
 * Keeps one ongoing notification while a freeze is active, with an inline Unlock action
 * and a system-drawn countdown, so a freeze started from anywhere can be released
 * without hunting for the app.
 */
internal object FreezeNotifier {
    private const val CHANNEL_ID = "freeze"
    private const val NOTIFICATION_ID = 1101

    fun show(context: Context, profile: PcProfile, snapshot: FreezeSnapshot) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        createChannel(context, manager)
        if (!canPost(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val unlock = PendingIntent.getActivity(
            context,
            1,
            ConfirmUnlockActivity.intent(context, profile.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val details = listOfNotNull(
            snapshot.mode?.let { "$it mode" },
            if (!snapshot.remoteReleaseAllowed) "release blocked by the PC" else null,
        ).joinToString(", ").ifBlank { "Frozen by FrostLock" }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_lock)
            .setContentTitle("Freeze active on ${profile.name}")
            .setContentText(details)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val remaining = snapshot.remainingSeconds ?: 0L
        if (remaining > 0) {
            // The system renders the countdown, so no periodic refresh is needed.
            builder.setWhen(System.currentTimeMillis() + remaining * 1000L)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        }
        if (snapshot.remoteReleaseAllowed) {
            builder.addAction(R.drawable.ic_notification_lock, "Unlock", unlock)
        }

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context, manager: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.freeze_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.freeze_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
