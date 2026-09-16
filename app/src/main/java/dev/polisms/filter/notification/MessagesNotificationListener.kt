package dev.polisms.filter.notification

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.polisms.filter.deletion.DeletionScheduler
import dev.polisms.filter.filtering.Stop2EndFilter

class MessagesNotificationListener : NotificationListenerService() {
    private val parser: NotificationParser = GoogleMessagesNotificationParser()
    private val filter = Stop2EndFilter()
    private val deduplicator = RecentNotificationDeduplicator()
    private val messageVibrator by lazy { MessageVibrator(this) }
    private val deletionScheduler by lazy { DeletionScheduler(applicationContext) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handleNotification(sbn, rankingFor(sbn, currentRanking))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        handleNotification(sbn, rankingFor(sbn, rankingMap))
    }

    private fun handleNotification(sbn: StatusBarNotification?, ranking: Ranking?) {
        if (sbn == null || sbn.packageName != GOOGLE_MESSAGES_PACKAGE) {
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

        if (blocked) {
            cancelNotification(message.notificationKey)
            if (deletionScheduler.enqueue(message)) {
                Log.i(TAG, "Blocked notification suppressed and archive work queued")
            } else {
                Log.w(TAG, "Blocked notification suppressed but archive work could not be queued")
            }
            return
        }

        if (!shouldVibrateForImportance(ranking?.importance)) {
            Log.i(TAG, "Allowed Google Messages notification retained without vibration because the conversation is silent")
            return
        }
        messageVibrator.vibrate()
        Log.i(TAG, "Allowed Google Messages notification retained and vibration requested")
    }

    private fun rankingFor(sbn: StatusBarNotification?, rankingMap: RankingMap?): Ranking? {
        if (sbn == null || rankingMap == null) return null
        return Ranking().takeIf { rankingMap.getRanking(sbn.key, it) }
    }

    companion object {
        const val GOOGLE_MESSAGES_PACKAGE = "com.google.android.apps.messaging"
        private const val TAG = "PoliticalSmsFilter"
    }
}

internal fun shouldVibrateForImportance(importance: Int?): Boolean = when (importance) {
    NotificationManager.IMPORTANCE_NONE,
    NotificationManager.IMPORTANCE_MIN,
    NotificationManager.IMPORTANCE_LOW,
    -> false

    else -> true
}
