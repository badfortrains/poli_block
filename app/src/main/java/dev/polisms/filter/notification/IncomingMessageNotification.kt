package dev.polisms.filter.notification

data class IncomingMessageNotification(
    val notificationKey: String,
    val sender: String?,
    val text: String,
    val timestamp: Long,
)
