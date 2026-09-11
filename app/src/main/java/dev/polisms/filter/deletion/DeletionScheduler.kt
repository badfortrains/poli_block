package dev.polisms.filter.deletion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import dev.polisms.filter.notification.IncomingMessageNotification
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class DeletionScheduler(private val context: Context) {
    private val pendingStore = EncryptedPendingDeletionStore(context)
    private val statusStore = DeletionStatusStore(context)

    fun enqueue(notification: IncomingMessageNotification): Boolean {
        val id = deletionFingerprint(notification)
        return try {
            pendingStore.save(id, DeletionTarget.from(notification))
            val request = OneTimeWorkRequest.Builder(DeleteMessageWorker::class.java)
                .setInputData(Data.Builder().putString(DeleteMessageWorker.TARGET_ID_KEY, id).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_WORK_PREFIX$id",
                ExistingWorkPolicy.KEEP,
                request,
            )
            statusStore.record(DeletionState.QUEUED)
            true
        } catch (_: Throwable) {
            runCatching { pendingStore.remove(id) }
            statusStore.record(DeletionState.FAILED)
            false
        }
    }

    companion object {
        private const val UNIQUE_WORK_PREFIX = "delete-message-"
        const val WORK_TAG = "political-sms-deletion"
    }
}

internal fun deletionFingerprint(notification: IncomingMessageNotification): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(notification.notificationKey.encodeToByteArray())
    digest.update(0.toByte())
    digest.update(notification.timestamp.toString().encodeToByteArray())
    digest.update(0.toByte())
    digest.update(notification.text.encodeToByteArray())
    return digest.digest().joinToString("") { "%02x".format(it) }
}
