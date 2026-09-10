package dev.polisms.filter.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
import dev.polisms.filter.R

class ReplacementNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.replacement_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.replacement_channel_description)
            enableVibration(true)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun post(message: IncomingMessageNotification) {
        ensureChannel()
        val notificationId = notificationId(message.notificationKey)
        val contentIntent = message.contentIntent ?: fallbackContentIntent(notificationId)
        val replacement = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.sender ?: context.getString(R.string.unknown_sender))
            .setContentText(message.text)
            .setStyle(Notification.BigTextStyle().bigText(message.text))
            .setWhen(message.timestamp)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(notificationId, replacement)
    }

    fun postTest() {
        post(
            IncomingMessageNotification(
                notificationKey = "manual-test-${System.currentTimeMillis()}",
                sender = "Notification test",
                text = "Allowed messages will alert through this channel.",
                timestamp = System.currentTimeMillis(),
                contentIntent = null,
                category = Notification.CATEGORY_MESSAGE,
                channelId = CHANNEL_ID,
            ),
        )
    }

    private fun fallbackContentIntent(requestCode: Int): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(GOOGLE_MESSAGES_PACKAGE)
            ?: return null
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationId(key: String): Int = key.hashCode() and Int.MAX_VALUE

    companion object {
        const val CHANNEL_ID = "replacement_messages"
        const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
    }
}
