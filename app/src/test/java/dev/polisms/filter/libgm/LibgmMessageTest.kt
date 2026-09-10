package dev.polisms.filter.libgm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibgmMessageTest {
    @Test
    fun `decode maps bridge response`() {
        val messages = LibgmMessage.decode(
            """[{"messageId":"m1","conversationId":"c1","sender":"Example","text":"hello","timestampMicros":123}]"""
                .encodeToByteArray(),
        )

        assertEquals(
            LibgmMessage("m1", "c1", "Example", "hello", 123),
            messages.single(),
        )
    }

    @Test
    fun `display label normalizes and truncates message text`() {
        val message = LibgmMessage("m1", "c1", "", "one\n\n two three", 123)

        assertEquals("Unknown sender — one two…", message.displayLabel(maxTextLength = 8))
    }

    @Test
    fun `decode rejects a missing message id`() {
        val error = runCatching {
            LibgmMessage.decode(
                """[{"messageId":"","conversationId":"c1","text":"hello","timestampMicros":123}]"""
                    .encodeToByteArray(),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
