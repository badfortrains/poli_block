package dev.polisms.filter.deletion

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.polisms.filter.filtering.Stop2EndFilter
import dev.polisms.filter.libgm.GoLibgmClientFactory
import dev.polisms.filter.libgm.LibgmMessage
import dev.polisms.filter.libgm.LibgmSessionRunner
import dev.polisms.filter.libgm.SessionPersistenceException
import dev.polisms.filter.security.EncryptedLibgmAuthStore

class DeleteMessageWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : Worker(appContext, workerParameters) {
    private val pendingStore = EncryptedPendingDeletionStore(appContext)
    private val statusStore = DeletionStatusStore(appContext)
    private val authStore = EncryptedLibgmAuthStore(appContext)
    private val sessionRunner = LibgmSessionRunner(authStore, GoLibgmClientFactory())
    private val matcher = MessageMatcher()
    private val filter = Stop2EndFilter()

    override fun doWork(): Result {
        val targetId = inputData.getString(TARGET_ID_KEY)
            ?.takeIf { it.matches(TARGET_ID_PATTERN) }
            ?: run {
                Log.w(TAG, "Archive work has a missing or invalid target ID")
                return Result.failure()
            }
        val target = try {
            pendingStore.load(targetId)
        } catch (error: Throwable) {
            Log.w(TAG, "Could not load the encrypted archive target", error)
            return terminal(targetId, DeletionState.FAILED, Result.failure())
        } ?: run {
            Log.i(TAG, "Archive target is no longer pending; nothing to do")
            return Result.success()
        }

        if (!statusStore.isAutomaticDeletionEnabled()) {
            return terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
        }

        if (!authStore.hasSession()) {
            Log.w(TAG, "Archiving stopped: Google Messages pairing is required")
            return terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
        }

        var archiveIssued = false
        var fullTextRejected = false
        var stage = "connecting to Google Messages"
        return try {
            val match = sessionRunner.withConnectedClient { client ->
                stage = "fetching recent conversations"
                val encoded = client.fetchRecentIncomingMessages(
                    conversationCount = RECENT_CONVERSATION_COUNT,
                    messagesPerConversation = RECENT_MESSAGE_COUNT,
                )
                stage = "decoding recent conversations"
                val recent = try {
                    LibgmMessage.decode(encoded)
                } finally {
                    encoded.fill(0)
                }
                stage = "matching the notification to a conversation"
                val result = matcher.find(target, recent)
                when (result) {
                    is MatchResult.Unique -> {
                        if (!filter.matchesFullText(result.message.text)) {
                            fullTextRejected = true
                            MatchResult.None
                        } else {
                            if (!statusStore.isAutomaticDeletionEnabled()) {
                                throw AutomaticDeletionDisabledException()
                            }
                            archiveIssued = true
                            stage = "sending the archive request"
                            try {
                                client.archiveConversation(result.message.conversationId)
                            } catch (error: Throwable) {
                                throw ArchiveRequestException(error)
                            }
                            result
                        }
                    }
                    MatchResult.None,
                    MatchResult.Ambiguous,
                    -> result
                }
            }
            when (match) {
                is MatchResult.Unique -> {
                    Log.i(TAG, "Conversation containing unique message match archived")
                    terminal(targetId, DeletionState.ARCHIVED, Result.success())
                }
                MatchResult.None -> {
                    if (fullTextRejected) {
                        Log.i(TAG, "Matched message did not pass the full-text filter; nothing archived")
                    } else {
                        Log.i(TAG, "No high-confidence message match; nothing archived")
                    }
                    terminal(targetId, DeletionState.NO_MATCH, Result.success())
                }
                MatchResult.Ambiguous -> {
                    Log.i(TAG, "Ambiguous message match; nothing archived")
                    terminal(targetId, DeletionState.AMBIGUOUS, Result.success())
                }
            }
        } catch (_: AutomaticDeletionDisabledException) {
            terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
        } catch (error: ArchiveRequestException) {
            Log.w(
                TAG,
                "Archive request failed after being issued; outcome is uncertain and will not be retried",
                error,
            )
            terminal(targetId, DeletionState.UNCERTAIN, Result.failure())
        } catch (error: SessionPersistenceException) {
            val state = if (archiveIssued && error.operationCompleted) {
                DeletionState.UNCERTAIN
            } else {
                DeletionState.FAILED
            }
            Log.w(TAG, "Refreshed libgm authentication could not be persisted", error)
            terminal(targetId, state, Result.failure())
        } catch (error: Throwable) {
            val attempt = runAttemptCount + 1
            if (DeletionFailureClassifier.isAuthenticationFailure(error)) {
                Log.w(TAG, "Archive work failed during $stage due to an authentication failure", error)
                terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
            } else if (attempt < MAX_ATTEMPTS) {
                Log.w(TAG, "Archive work failed during $stage on attempt $attempt; scheduling a retry", error)
                statusStore.record(DeletionState.RETRYING, attempt)
                Result.retry()
            } else {
                Log.w(TAG, "Archive work failed during $stage on final attempt $attempt", error)
                terminal(targetId, DeletionState.FAILED, Result.failure())
            }
        }
    }

    private fun terminal(targetId: String, state: DeletionState, result: Result): Result {
        runCatching { pendingStore.remove(targetId) }
        statusStore.record(state, runAttemptCount + 1)
        return result
    }

    private class ArchiveRequestException(cause: Throwable) : Exception(cause)

    private class AutomaticDeletionDisabledException : Exception()

    companion object {
        const val TARGET_ID_KEY = "target_id"
        private const val TAG = "PoliticalSmsFilter"
        private const val MAX_ATTEMPTS = 3
        private const val RECENT_CONVERSATION_COUNT = 20
        private const val RECENT_MESSAGE_COUNT = 20
        private val TARGET_ID_PATTERN = Regex("[a-f0-9]{64}")
    }
}
