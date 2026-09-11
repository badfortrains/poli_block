package dev.polisms.filter.deletion

import dev.polisms.filter.libgm.LibgmMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMatcherTest {
    private val matcher = MessageMatcher()
    private val target = DeletionTarget(
        sender = "+1 (415) 555-0123",
        text = "Campaign update. Stop2End",
        timestampMillis = 1_700_000_000_000,
    )

    @Test
    fun `one exact incoming candidate is unique`() {
        val result = matcher.find(target, listOf(candidate()))

        assertTrue(result is MatchResult.Unique)
        assertEquals("message-1", (result as MatchResult.Unique).message.messageId)
    }

    @Test
    fun `duplicate exact candidates are ambiguous`() {
        val result = matcher.find(
            target,
            listOf(candidate("message-1"), candidate("message-2")),
        )

        assertEquals(MatchResult.Ambiguous, result)
    }

    @Test
    fun `text comparison is case sensitive and exact`() {
        val result = matcher.find(target, listOf(candidate(text = "Campaign update. stop2end")))

        assertEquals(MatchResult.None, result)
    }

    @Test
    fun `sender mismatch preserves the message`() {
        val result = matcher.find(target, listOf(candidate(sender = "+1 212 555 0199")))

        assertEquals(MatchResult.None, result)
    }

    @Test
    fun `contact names are normalized conservatively`() {
        val namedTarget = target.copy(sender = "Éxample Sender")
        val result = matcher.find(namedTarget, listOf(candidate(sender = "éxample sender")))

        assertTrue(result is MatchResult.Unique)
    }

    @Test
    fun `timestamp outside two minutes preserves the message`() {
        val tooLate = target.timestampMillis + MessageMatcher.DEFAULT_TIMESTAMP_TOLERANCE_MILLIS + 1
        val result = matcher.find(target, listOf(candidate(timestampMillis = tooLate)))

        assertEquals(MatchResult.None, result)
    }

    @Test
    fun `missing notification sender does not invent a constraint`() {
        val result = matcher.find(target.copy(sender = null), listOf(candidate(sender = "")))

        assertTrue(result is MatchResult.Unique)
    }

    private fun candidate(
        id: String = "message-1",
        sender: String = "+14155550123",
        text: String = target.text,
        timestampMillis: Long = target.timestampMillis,
    ) = LibgmMessage(
        messageId = id,
        conversationId = "conversation-1",
        sender = sender,
        text = text,
        timestampMicros = timestampMillis * 1_000,
    )
}
