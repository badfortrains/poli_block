package dev.polisms.filter.deletion

import android.util.Log
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
    private val diagnosticsLogger: (String) -> Unit = { message -> Log.i(TAG, message) },
) {
    fun find(target: DeletionTarget, messages: List<LibgmMessage>): MatchResult {
        val textCandidates = messages.filter { candidate ->
            textMatches(target.text, candidate.text)
        }
        val senderCandidates = textCandidates.filter { candidate ->
            senderMatches(target.sender, candidate.sender)
        }
        val candidates = senderCandidates.filter { candidate ->
            timestampMatches(target.timestampMillis, candidate.timestampMicros)
        }
        val result = when (candidates.size) {
            0 -> MatchResult.None
            1 -> MatchResult.Unique(candidates.single())
            else -> MatchResult.Ambiguous
        }
        diagnosticsLogger(
            buildDiagnostics(target, messages, textCandidates, senderCandidates, result),
        )
        return result
    }

    private fun buildDiagnostics(
        target: DeletionTarget,
        messages: List<LibgmMessage>,
        textCandidates: List<LibgmMessage>,
        senderCandidates: List<LibgmMessage>,
        result: MatchResult,
    ): String {
        val targetPrefix = normalizedText(target.text).take(TEXT_PREFIX_LENGTH)
        val bestTextCandidate = messages
            .asSequence()
            .map { candidate ->
                val normalizedCandidate = normalizedText(candidate.text)
                TextSimilarity(
                    commonPrefixChars = commonPrefixLength(targetPrefix, normalizedCandidate),
                    candidateTextChars = normalizedCandidate.length,
                )
            }
            .maxByOrNull { it.commonPrefixChars }
        val timestampDeltas = senderCandidates.mapNotNull { candidate ->
            timestampDifferenceMillis(target.timestampMillis, candidate.timestampMicros)
        }
        val completeMatches = senderCandidates.count { candidate ->
            timestampMatches(target.timestampMillis, candidate.timestampMicros)
        }
        val resultName = when (result) {
            is MatchResult.Unique -> "UNIQUE"
            MatchResult.None -> "NONE"
            MatchResult.Ambiguous -> "AMBIGUOUS"
        }

        return buildString {
            append("Message match diagnostics: fetched=")
            append(messages.size)
            append(", targetTextChars=")
            append(normalizedText(target.text).length)
            append(", targetPrefixChars=")
            append(targetPrefix.length)
            append(", targetHasSender=")
            append(!target.sender.isNullOrBlank())
            append(", targetTimestampValid=")
            append(target.timestampMillis > 0)
            append(", textPrefixMatches=")
            append(textCandidates.size)
            append(", textAndSenderMatches=")
            append(senderCandidates.size)
            append(", completeMatches=")
            append(completeMatches)
            append(", bestCommonPrefixChars=")
            append(bestTextCandidate?.commonPrefixChars ?: 0)
            append(", bestCandidateTextChars=")
            append(bestTextCandidate?.candidateTextChars ?: "none")
            append(", closestTimestampDeltaMs=")
            append(timestampDeltas.minOrNull() ?: "none")
            append(", timestampToleranceMs=")
            append(timestampToleranceMillis)
            append(", result=")
            append(resultName)
        }
    }

    private fun textMatches(notificationText: String, messageText: String): Boolean {
        val notificationPrefix = normalizedText(notificationText).take(TEXT_PREFIX_LENGTH)
        val normalizedMessage = normalizedText(messageText)
        return normalizedMessage.startsWith(notificationPrefix)
    }

    private fun normalizedText(value: String): String = value.replace("\r\n", "\n")

    private fun commonPrefixLength(first: String, second: String): Int = first
        .zip(second)
        .takeWhile { (left, right) -> left == right }
        .size

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
        val difference = timestampDifferenceMillis(notificationMillis, messageMicros) ?: return false
        return difference <= timestampToleranceMillis
    }

    private fun timestampDifferenceMillis(notificationMillis: Long, messageMicros: Long): Long? {
        if (notificationMillis <= 0 || messageMicros <= 0) return null
        val messageMillis = messageMicros / 1_000
        return if (notificationMillis >= messageMillis) {
            notificationMillis - messageMillis
        } else {
            messageMillis - notificationMillis
        }
    }

    private data class TextSimilarity(
        val commonPrefixChars: Int,
        val candidateTextChars: Int,
    )

    companion object {
        private const val TAG = "PoliticalSmsFilter"
        const val TEXT_PREFIX_LENGTH = 100
        const val DEFAULT_TIMESTAMP_TOLERANCE_MILLIS = 2 * 60 * 1_000L
    }
}
