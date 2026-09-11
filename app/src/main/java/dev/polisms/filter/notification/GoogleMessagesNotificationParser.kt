package dev.polisms.filter.notification

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification

class GoogleMessagesNotificationParser : NotificationParser {
    private data class ParsedMessage(
        val text: CharSequence,
        val timestamp: Long,
        val sender: CharSequence?,
    )

    override fun parse(notification: StatusBarNotification): IncomingMessageNotification? {
        val source = notification.notification
        val extras = source.extras ?: Bundle.EMPTY
        val messagingMessage = newestMessagingStyleMessage(extras)

        val text = messagingMessage?.text?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: return null

        val sender = messagingMessage?.sender?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }

        return IncomingMessageNotification(
            notificationKey = notification.key,
            sender = sender,
            text = text,
            timestamp = messagingMessage?.timestamp?.takeIf { it > 0 } ?: notification.postTime,
        )
    }

    private fun newestMessagingStyleMessage(extras: Bundle): ParsedMessage? {
        val bundles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        } ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles)
                .asSequence()
                .filter { !it.text.isNullOrBlank() }
                .map { ParsedMessage(it.text, it.timestamp, senderName(it)) }
                .maxByOrNull { it.timestamp }
        } else {
            // EXTRA_MESSAGES has contained framework Message bundles since API 24,
            // but the public array decoder was only exposed in API 30.
            bundles.asSequence()
                .filterIsInstance<Bundle>()
                .mapNotNull(::parseLegacyMessageBundle)
                .maxByOrNull { it.timestamp }
        }
    }

    private fun parseLegacyMessageBundle(bundle: Bundle): ParsedMessage? {
        val text = bundle.getCharSequence("text")?.takeIf { it.isNotBlank() } ?: return null
        if (!bundle.containsKey("time")) return null
        return ParsedMessage(
            text = text,
            timestamp = bundle.getLong("time"),
            sender = bundle.getCharSequence("sender"),
        )
    }

    private fun senderName(message: Notification.MessagingStyle.Message): String? {
        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message.senderPerson?.name
        } else {
            @Suppress("DEPRECATION")
            message.sender
        }
        return sender?.toString()?.takeIf { it.isNotBlank() }
    }
}
