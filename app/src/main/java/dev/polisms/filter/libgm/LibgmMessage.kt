package dev.polisms.filter.libgm

import org.json.JSONArray

data class LibgmMessage(
    val messageId: String,
    val conversationId: String,
    val sender: String,
    val text: String,
    val timestampMicros: Long,
) {
    fun displayLabel(maxTextLength: Int = 80): String {
        val normalizedText = text.replace(Regex("\\s+"), " ").trim()
        val preview = if (normalizedText.length <= maxTextLength) {
            normalizedText
        } else {
            normalizedText.take(maxTextLength - 1) + "…"
        }
        val senderLabel = sender.ifBlank { "Unknown sender" }
        return if (preview.isBlank()) senderLabel else "$senderLabel — $preview"
    }

    companion object {
        fun decode(json: ByteArray): List<LibgmMessage> {
            val array = JSONArray(json.decodeToString())
            return buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val messageId = item.getString("messageId")
                    require(messageId.isNotBlank()) { "libgm returned a message with no ID" }
                    add(
                        LibgmMessage(
                            messageId = messageId,
                            conversationId = item.getString("conversationId"),
                            sender = item.optString("sender"),
                            text = item.optString("text"),
                            timestampMicros = item.getLong("timestampMicros"),
                        ),
                    )
                }
            }
        }
    }
}
