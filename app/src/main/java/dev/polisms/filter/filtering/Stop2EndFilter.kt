package dev.polisms.filter.filtering

import dev.polisms.filter.notification.IncomingMessageNotification

class Stop2EndFilter : MessageFilter {
    override fun shouldBlock(message: IncomingMessageNotification): Boolean =
        KEYWORDS.any { keyword -> message.text.contains(keyword, ignoreCase = true) }

    companion object {
        val KEYWORDS = listOf("Stop2End", "STOP to quit", "Stop to End")
    }
}
