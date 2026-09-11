package dev.polisms.filter.deletion

import dev.polisms.filter.notification.IncomingMessageNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeletionSchedulerTest {
    @Test
    fun `duplicate notification updates share a work identity`() {
        val message = notification("same", 100)

        assertEquals(deletionFingerprint(message), deletionFingerprint(message.copy()))
    }

    @Test
    fun `new message in the same conversation gets separate work`() {
        val first = notification("first Stop2End", 100)
        val second = notification("second Stop2End", 101)

        assertNotEquals(deletionFingerprint(first), deletionFingerprint(second))
    }

    private fun notification(text: String, timestamp: Long) = IncomingMessageNotification(
        notificationKey = "shared-conversation-key",
        sender = "Example",
        text = text,
        timestamp = timestamp,
    )
}
