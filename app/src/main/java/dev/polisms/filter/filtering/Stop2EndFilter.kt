package dev.polisms.filter.filtering

import dev.polisms.filter.notification.IncomingMessageNotification

class Stop2EndFilter : MessageFilter {
    override fun shouldBlock(message: IncomingMessageNotification): Boolean =
        message.text.length == TRUNCATED_NOTIFICATION_LENGTH || matchesFullText(message.text)

    fun matchesFullText(text: String): Boolean =
        KEYWORDS.any { keyword -> text.contains(keyword, ignoreCase = true) }

    companion object {
        const val TRUNCATED_NOTIFICATION_LENGTH = 1_024
        val KEYWORDS = listOf("Stop2End", "STOP to quit", "Stop to End", "End2End")
    }
}
