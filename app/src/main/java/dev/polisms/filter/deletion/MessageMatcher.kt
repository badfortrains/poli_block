package dev.polisms.filter.deletion

import dev.polisms.filter.libgm.LibgmMessage
import java.text.Normalizer
import java.util.Locale

sealed interface MatchResult {
    data class Unique(val message: LibgmMessage) : MatchResult

    data object None : MatchResult

    data object Ambiguous : MatchResult
}

class MessageMatcher(
    private val timestampToleranceMillis: Long = DEFAULT_TIMESTAMP_TOLERANCE_MILLIS,
) {
    fun find(target: DeletionTarget, messages: List<LibgmMessage>): MatchResult {
        val candidates = messages.filter { candidate ->
            exactText(target.text, candidate.text) &&
                senderMatches(target.sender, candidate.sender) &&
                timestampMatches(target.timestampMillis, candidate.timestampMicros)
        }
        return when (candidates.size) {
            0 -> MatchResult.None
            1 -> MatchResult.Unique(candidates.single())
            else -> MatchResult.Ambiguous
        }
    }

    private fun exactText(notificationText: String, messageText: String): Boolean =
        notificationText.replace("\r\n", "\n") == messageText.replace("\r\n", "\n")

    private fun senderMatches(notificationSender: String?, messageSender: String): Boolean {
        val expected = notificationSender?.takeIf { it.isNotBlank() } ?: return true
        if (messageSender.isBlank()) return false

        val expectedDigits = expected.filter(Char::isDigit)
        val actualDigits = messageSender.filter(Char::isDigit)
        if (expectedDigits.length >= 10 && actualDigits.length >= 10) {
            return expectedDigits.takeLast(10) == actualDigits.takeLast(10)
        }
        return normalizeName(expected) == normalizeName(messageSender)
    }

    private fun normalizeName(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private fun timestampMatches(notificationMillis: Long, messageMicros: Long): Boolean {
        if (notificationMillis <= 0 || messageMicros <= 0) return false
        val messageMillis = messageMicros / 1_000
        val difference = if (notificationMillis >= messageMillis) {
            notificationMillis - messageMillis
        } else {
            messageMillis - notificationMillis
        }
        return difference <= timestampToleranceMillis
    }

    companion object {
        const val DEFAULT_TIMESTAMP_TOLERANCE_MILLIS = 2 * 60 * 1_000L
    }
}
