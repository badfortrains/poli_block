package dev.polisms.filter.deletion

import dev.polisms.filter.notification.IncomingMessageNotification
import org.json.JSONObject

data class DeletionTarget(
    val sender: String?,
    val text: String,
    val timestampMillis: Long,
) {
    fun encode(): ByteArray = JSONObject()
        .put("sender", sender)
        .put("text", text)
        .put("timestampMillis", timestampMillis)
        .toString()
        .encodeToByteArray()

    companion object {
        fun from(notification: IncomingMessageNotification): DeletionTarget = DeletionTarget(
            sender = notification.sender,
            text = notification.text,
            timestampMillis = notification.timestamp,
        )

        fun decode(encoded: ByteArray): DeletionTarget {
            val value = JSONObject(encoded.decodeToString())
            return DeletionTarget(
                sender = value.optString("sender").takeIf { !value.isNull("sender") && it.isNotBlank() },
                text = value.getString("text"),
                timestampMillis = value.getLong("timestampMillis"),
            )
        }
    }
}
