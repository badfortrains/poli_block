package dev.polisms.filter.filtering

import dev.polisms.filter.notification.IncomingMessageNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stop2EndFilterTest {
    private val filter = Stop2EndFilter()

    @Test
    fun blocksExactKeyword() {
        assertTrue(filter.shouldBlock(message("Donate today. Stop2End")))
    }

    @Test
    fun blocksKeywordCaseInsensitively() {
        assertTrue(filter.shouldBlock(message("Reply STOP2END to unsubscribe")))
    }

    @Test
    fun allowsOrdinaryMessage() {
        assertFalse(filter.shouldBlock(message("Hey, want to get dinner tomorrow?")))
    }

    @Test
    fun doesNotBlockSimilarButDifferentText() {
        assertFalse(filter.shouldBlock(message("Stop to end")))
    }

    private fun message(text: String) = IncomingMessageNotification(
        notificationKey = "test",
        sender = null,
        text = text,
        timestamp = 0,
        contentIntent = null,
        category = null,
        channelId = null,
    )
}
