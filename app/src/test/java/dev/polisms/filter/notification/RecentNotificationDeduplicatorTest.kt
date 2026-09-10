package dev.polisms.filter.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentNotificationDeduplicatorTest {
    private var now = 1_000L
    private val deduplicator = RecentNotificationDeduplicator(
        retentionMillis = 100,
        now = { now },
    )

    @Test
    fun rejectsRepeatedUpdateOfSameMessage() {
        val message = message(text = "hello", timestamp = 10)
        assertTrue(deduplicator.firstSeen(message))
        assertFalse(deduplicator.firstSeen(message))
    }

    @Test
    fun acceptsNewMessageOnSameConversationNotification() {
        assertTrue(deduplicator.firstSeen(message(text = "one", timestamp = 10)))
        assertTrue(deduplicator.firstSeen(message(text = "two", timestamp = 20)))
    }

    @Test
    fun acceptsFingerprintAfterRetentionWindow() {
        val message = message(text = "hello", timestamp = 10)
        assertTrue(deduplicator.firstSeen(message))
        now += 101
        assertTrue(deduplicator.firstSeen(message))
    }

    private fun message(text: String, timestamp: Long) = IncomingMessageNotification(
        notificationKey = "conversation-key",
        sender = null,
        text = text,
        timestamp = timestamp,
        contentIntent = null,
        category = null,
        channelId = null,
    )
}
