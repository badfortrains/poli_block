package dev.polisms.filter.notification

import android.app.PendingIntent

data class IncomingMessageNotification(
    val notificationKey: String,
    val sender: String?,
    val text: String,
    val timestamp: Long,
    val contentIntent: PendingIntent?,
    val category: String?,
    val channelId: String?,
)
