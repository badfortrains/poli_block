package dev.polisms.filter.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleMessagesNotificationParserTest {
    private val parser = GoogleMessagesNotificationParser()

    @Test
    fun `image placeholder falls back to regular text`() {
        val selected = parser.selectText(
            messagingText = "Image",
            bigText = null,
            regularText = "Full political message. Stop2End",
        )

        assertEquals("Full political message. Stop2End" to "EXTRA_TEXT", selected)
    }

    @Test
    fun `image placeholder prefers big text when available`() {
        val selected = parser.selectText(
            messagingText = "image",
            bigText = "Expanded message",
            regularText = "Regular message",
        )

        assertEquals("Expanded message" to "EXTRA_BIG_TEXT", selected)
    }

    @Test
    fun `normal messaging text retains precedence`() {
        val selected = parser.selectText(
            messagingText = "Normal message",
            bigText = "Longer expanded message",
            regularText = "Longer regular message",
        )

        assertEquals("Normal message" to "MessagingStyle", selected)
    }

    @Test
    fun `image placeholder remains usable without fallback text`() {
        val selected = parser.selectText(
            messagingText = "Image",
            bigText = null,
            regularText = null,
        )

        assertEquals("Image" to "MessagingStyle", selected)
    }
}
