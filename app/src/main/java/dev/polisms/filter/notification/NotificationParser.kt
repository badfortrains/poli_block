package dev.polisms.filter.notification

import android.service.notification.StatusBarNotification

interface NotificationParser {
    fun parse(notification: StatusBarNotification): IncomingMessageNotification?
}
