package dev.shortblocker.app.domain

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.shortblocker.app.MainActivity
import dev.shortblocker.app.R
import dev.shortblocker.app.data.PendingIntervention

class ShortblockerNotificationController(private val context: Context) {
    fun showIntervention(pending: PendingIntervention) {
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortblocker)
            .setContentTitle("${pending.appName} / score ${pending.score}")
            .setContentText(pending.dialogue)
            .setStyle(NotificationCompat.BigTextStyle().bigText(pending.dialogue))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(goalIntent())
            .addAction(
                0,
                context.getString(R.string.notification_stop),
                actionIntent(NotificationActionReceiver.ACTION_STOP),
            )
            .addAction(
                0,
                context.getString(R.string.notification_extend),
                actionIntent(NotificationActionReceiver.ACTION_EXTEND),
            )
            .addAction(
                0,
                context.getString(R.string.notification_ignore),
                actionIntent(NotificationActionReceiver.ACTION_IGNORE),
            )
            .addAction(
                0,
                context.getString(R.string.notification_goal),
                goalIntent(),
            )
            .build()

        NotificationManagerCompat.from(context).notify(INTERVENTION_ID, notification)
    }

    fun dismissIntervention() {
        NotificationManagerCompat.from(context).cancel(INTERVENTION_ID)
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun goalIntent(): PendingIntent {
        val intent = MainActivity.goalIntent(context)
        return PendingIntent.getActivity(
            context,
            4096,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "shortblocker_intervention"
        const val INTERVENTION_ID = 1001
    }
}
