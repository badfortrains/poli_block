package dev.polisms.filter.filtering

import dev.polisms.filter.notification.IncomingMessageNotification

class Stop2EndFilter : MessageFilter {
    override fun shouldBlock(message: IncomingMessageNotification): Boolean =
        message.text.contains(KEYWORD, ignoreCase = true)

    companion object {
        const val KEYWORD = "Stop2End"
    }
}
