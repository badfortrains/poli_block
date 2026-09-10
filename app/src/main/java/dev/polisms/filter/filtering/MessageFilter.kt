package dev.polisms.filter.filtering

import dev.polisms.filter.notification.IncomingMessageNotification

interface MessageFilter {
    fun shouldBlock(message: IncomingMessageNotification): Boolean
}
