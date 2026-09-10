package dev.polisms.filter.notification

import android.os.SystemClock

class RecentNotificationDeduplicator(
    private val retentionMillis: Long = 5 * 60 * 1000L,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val fingerprints = LinkedHashMap<String, Long>()

    @Synchronized
    fun firstSeen(message: IncomingMessageNotification): Boolean {
        val current = now()
        fingerprints.entries.removeAll { current - it.value > retentionMillis }
        val fingerprint = buildString {
            append(message.notificationKey)
            append('\u0000')
            append(message.timestamp)
            append('\u0000')
            append(message.text)
        }
        if (fingerprints.containsKey(fingerprint)) return false
        fingerprints[fingerprint] = current
        return true
    }
}
