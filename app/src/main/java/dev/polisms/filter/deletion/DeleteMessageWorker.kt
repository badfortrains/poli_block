package dev.polisms.filter.deletion

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
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

    override fun doWork(): Result {
        val targetId = inputData.getString(TARGET_ID_KEY)
            ?.takeIf { it.matches(TARGET_ID_PATTERN) }
            ?: return Result.failure()
        val target = try {
            pendingStore.load(targetId)
        } catch (_: Throwable) {
            return terminal(targetId, DeletionState.FAILED, Result.failure())
        } ?: return Result.success()

        if (!statusStore.isAutomaticDeletionEnabled()) {
            return terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
        }

        if (!authStore.hasSession()) {
            Log.w(TAG, "Deletion stopped: Google Messages pairing is required")
            return terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
        }

        var deleteIssued = false
        return try {
            val match = sessionRunner.withConnectedClient { client ->
                val encoded = client.fetchRecentIncomingMessages(
                    conversationCount = RECENT_CONVERSATION_COUNT,
                    messagesPerConversation = RECENT_MESSAGE_COUNT,
                )
                val recent = try {
                    LibgmMessage.decode(encoded)
                } finally {
                    encoded.fill(0)
                }
                val result = matcher.find(target, recent)
                when (result) {
                    is MatchResult.Unique -> {
                        if (!statusStore.isAutomaticDeletionEnabled()) {
                            throw AutomaticDeletionDisabledException()
                        }
                        deleteIssued = true
                        try {
                            client.deleteMessage(result.message.messageId)
                        } catch (error: Throwable) {
                            throw DeletionRequestException(error)
                        }
                    }
                    MatchResult.None,
                    MatchResult.Ambiguous,
                    -> Unit
                }
                result
            }
            when (match) {
                is MatchResult.Unique -> {
                    Log.i(TAG, "Unique message match deleted")
                    terminal(targetId, DeletionState.DELETED, Result.success())
                }
                MatchResult.None -> {
                    Log.i(TAG, "No high-confidence message match; nothing deleted")
                    terminal(targetId, DeletionState.NO_MATCH, Result.success())
                }
                MatchResult.Ambiguous -> {
                    Log.i(TAG, "Ambiguous message match; nothing deleted")
                    terminal(targetId, DeletionState.AMBIGUOUS, Result.success())
                }
            }
        } catch (_: AutomaticDeletionDisabledException) {
            terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
        } catch (_: DeletionRequestException) {
            Log.w(TAG, "Delete response was uncertain; the exact message ID will not be retried")
            terminal(targetId, DeletionState.UNCERTAIN, Result.failure())
        } catch (error: SessionPersistenceException) {
            val state = if (deleteIssued && error.operationCompleted) {
                DeletionState.UNCERTAIN
            } else {
                DeletionState.FAILED
            }
            Log.w(TAG, "Refreshed libgm authentication could not be persisted")
            terminal(targetId, state, Result.failure())
        } catch (error: Throwable) {
            if (DeletionFailureClassifier.isAuthenticationFailure(error)) {
                Log.w(TAG, "Deletion stopped due to an authentication failure")
                terminal(targetId, DeletionState.AUTH_REQUIRED, Result.failure())
            } else if (runAttemptCount + 1 < MAX_ATTEMPTS) {
                Log.w(TAG, "Transient deletion failure; scheduling a bounded retry")
                statusStore.record(DeletionState.RETRYING, runAttemptCount + 1)
                Result.retry()
            } else {
                Log.w(TAG, "Deletion failed after the final retry")
                terminal(targetId, DeletionState.FAILED, Result.failure())
            }
        }
    }

    private fun terminal(targetId: String, state: DeletionState, result: Result): Result {
        runCatching { pendingStore.remove(targetId) }
        statusStore.record(state, runAttemptCount + 1)
        return result
    }

    private class DeletionRequestException(cause: Throwable) : Exception(cause)

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
