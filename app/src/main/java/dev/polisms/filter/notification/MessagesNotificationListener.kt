package dev.polisms.filter.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.polisms.filter.filtering.Stop2EndFilter

class MessagesNotificationListener : NotificationListenerService() {
    private val parser: NotificationParser = GoogleMessagesNotificationParser()
    private val filter = Stop2EndFilter()
    private val deduplicator = RecentNotificationDeduplicator()
    private val replacementManager by lazy { ReplacementNotificationManager(this) }

    override fun onCreate() {
        super.onCreate()
        replacementManager.ensureChannel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != ReplacementNotificationManager.GOOGLE_MESSAGES_PACKAGE) {
            return
        }
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.i(TAG, "Ignoring Google Messages group summary")
            return
        }

        Log.i(TAG, "Received Google Messages notification")
        val message = parser.parse(sbn)
        if (message == null) {
            Log.w(TAG, "Notification could not be parsed; leaving it untouched")
            return
        }
        if (!deduplicator.firstSeen(message)) {
            Log.i(TAG, "Ignoring duplicate notification update")
            return
        }

        val blocked = filter.shouldBlock(message)
        Log.i(TAG, "Filter result: ${if (blocked) "BLOCK" else "ALLOW"}")
        cancelNotification(message.notificationKey)

        if (blocked) {
            Log.i(TAG, "Blocked notification suppressed; deletion is intentionally deferred to milestone 5")
            return
        }
        replacementManager.post(message)
        Log.i(TAG, "Posted replacement notification")
    }

    companion object {
        private const val TAG = "PoliticalSmsFilter"
    }
}
