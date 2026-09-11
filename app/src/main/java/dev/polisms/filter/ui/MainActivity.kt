package dev.polisms.filter.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.work.WorkManager
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.polisms.filter.deletion.DeletionScheduler
import dev.polisms.filter.deletion.DeletionState
import dev.polisms.filter.deletion.DeletionStatusStore
import dev.polisms.filter.deletion.EncryptedPendingDeletionStore
import dev.polisms.filter.filtering.Stop2EndFilter
import dev.polisms.filter.libgm.GoLibgmClientFactory
import dev.polisms.filter.libgm.GoLibgmPairingClientFactory
import dev.polisms.filter.libgm.LibgmPairingClient
import dev.polisms.filter.libgm.LibgmMessage
import dev.polisms.filter.libgm.LibgmOperationLock
import dev.polisms.filter.libgm.LibgmSessionRunner
import dev.polisms.filter.notification.MessageVibrator
import dev.polisms.filter.notification.MessagesNotificationListener
import dev.polisms.filter.security.EncryptedLibgmAuthStore
import dev.polisms.filter.security.QrCredentialPayload
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var notificationAccessStatus: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var automaticDeletionStatus: TextView
    private lateinit var operationStatus: TextView
    private lateinit var importButton: Button
    private lateinit var scanPairButton: Button
    private lateinit var fetchButton: Button
    private lateinit var archiveButton: Button
    private lateinit var clearButton: Button
    private lateinit var messageSpinner: Spinner

    private val executor = Executors.newSingleThreadExecutor()
    private val authStore by lazy { EncryptedLibgmAuthStore(applicationContext) }
    private val clientFactory = GoLibgmClientFactory()
    private val pairingClientFactory = GoLibgmPairingClientFactory()
    private val sessionRunner by lazy { LibgmSessionRunner(authStore, clientFactory) }
    private val deletionStatusStore by lazy { DeletionStatusStore(applicationContext) }
    private var messages: List<LibgmMessage> = emptyList()
    private var busy = false
    @Volatile private var activePairingClient: LibgmPairingClient? = null
    private var pairingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        if (::notificationAccessStatus.isInitialized) refreshStatus()
    }

    override fun onDestroy() {
        activePairingClient?.cancel()
        pairingDialog?.dismiss()
        executor.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android, retained to avoid an AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_SESSION_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let(::importSession)
        }
    }

    private fun buildContent(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }

        content.addView(text("Political SMS Filter", 28f, Color.rgb(25, 38, 53), 0, 16))
        content.addView(text("Milestones 1–6", 14f, Color.rgb(75, 91, 107), 0, 28))

        content.addView(text("Notification access", 18f, Color.BLACK, 0, 6))
        notificationAccessStatus = text("Checking…", 16f, Color.DKGRAY, 0, 10)
        content.addView(notificationAccessStatus)
        content.addView(button("Grant notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        content.addView(text("Google Messages notifications", 18f, Color.BLACK, 26, 6))
        content.addView(text("Set every Google Messages notification channel to silent: no sound and no vibration.", 16f, Color.DKGRAY, 0, 10))
        content.addView(button("Open Google Messages notification settings") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, MessagesNotificationListener.GOOGLE_MESSAGES_PACKAGE)
            })
        })

        content.addView(text("Filter", 18f, Color.BLACK, 26, 6))
        content.addView(text("Messages containing (case-insensitive): ${Stop2EndFilter.KEYWORDS.joinToString()}", 16f, Color.DKGRAY, 0, 16))
        automaticDeletionStatus = text("Checking automatic archive status…", 15f, Color.DKGRAY, 0, 10)
        content.addView(automaticDeletionStatus)

        content.addView(text("Filter vibration", 18f, Color.BLACK, 12, 6))
        content.addView(text("Allowed messages use this vibration while keeping the original Google Messages notification.", 16f, Color.DKGRAY, 0, 10))
        content.addView(button("Test vibration") {
            MessageVibrator(this).vibrate()
        })

        content.addView(text("Google Messages pairing", 18f, Color.BLACK, 26, 6))
        content.addView(text("Scan the offline desktop helper's QR, approve the displayed emoji in Google Messages, and the resulting session will be encrypted with Android Keystore.", 16f, Color.DKGRAY, 0, 10))
        sessionStatus = text("Checking…", 16f, Color.DKGRAY, 0, 10)
        content.addView(sessionStatus)

        scanPairButton = button("Scan credential QR and pair") { scanCredentialQr() }
        content.addView(scanPairButton)

        importButton = button("Import paired session.json (fallback)") { openSessionPicker() }
        content.addView(importButton)

        fetchButton = button("Fetch recent incoming messages") { fetchMessages() }
        content.addView(fetchButton)

        messageSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        }
        content.addView(messageSpinner)

        archiveButton = button("Archive selected test conversation") { confirmSelectedArchive() }
        content.addView(archiveButton)

        clearButton = button("Clear imported credentials") { confirmClearCredentials() }
        content.addView(clearButton)

        operationStatus = text("No libgm operation running.", 15f, Color.DKGRAY, 12, 0)
        content.addView(operationStatus)
        updateMessageChoices(emptyList())
        refreshSessionControls()

        return ScrollView(this).apply { addView(content) }
    }

    private fun openSessionPicker() {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/json", "text/plain", "application/octet-stream"),
                )
            },
            IMPORT_SESSION_REQUEST,
        )
    }

    private fun scanCredentialQr() {
        if (busy) return
        setBusy(true)
        operationStatus.text = "Opening the on-device QR scanner…"
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val rawValue = barcode.rawValue
                setBusy(false)
                if (rawValue == null) {
                    operationStatus.text = "⚠ Scanned QR did not contain text."
                } else {
                    pairFromQr(rawValue.encodeToByteArray())
                }
            }
            .addOnCanceledListener {
                if (isFinishing || isDestroyed) return@addOnCanceledListener
                setBusy(false)
                operationStatus.text = "QR scan canceled."
            }
            .addOnFailureListener { error ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                setBusy(false)
                showError("QR scan failed", error)
            }
    }

    private fun pairFromQr(rawPayload: ByteArray) {
        runOperation("Validating credential QR and starting pairing…") {
            try {
                val cookieData = QrCredentialPayload.decode(rawPayload)
                try {
                    LibgmOperationLock.run {
                        val pairingClient = pairingClientFactory.create(cookieData)
                        activePairingClient = pairingClient
                        try {
                            val emoji = pairingClient.start()
                            showPairingDialog(emoji)
                            val authData = pairingClient.finish()
                            try {
                                authStore.save(authData)
                                deletionStatusStore.setAutomaticDeletionEnabled(true)
                            } finally {
                                authData.fill(0)
                            }
                        } finally {
                            activePairingClient = null
                            pairingClient.disconnect()
                            dismissPairingDialog()
                        }
                    }
                } finally {
                    cookieData.fill(0)
                }
            } finally {
                rawPayload.fill(0)
            }
            runOnUiThreadIfAlive {
                updateMessageChoices(emptyList())
                operationStatus.text = "✓ Google Messages pairing succeeded and the session is encrypted."
            }
        }
    }

    private fun showPairingDialog(emoji: String) {
        runOnUiThreadIfAlive {
            pairingDialog?.dismiss()
            pairingDialog = AlertDialog.Builder(this)
                .setTitle("Approve this emoji")
                .setMessage(
                    "$emoji\n\nOpen Google Messages and approve this exact emoji for the new paired device. " +
                        "This window will close when pairing finishes.",
                )
                .setNegativeButton("Cancel pairing") { _, _ -> activePairingClient?.cancel() }
                .setCancelable(false)
                .show()
        }
    }

    private fun dismissPairingDialog() {
        runOnUiThreadIfAlive {
            pairingDialog?.dismiss()
            pairingDialog = null
        }
    }

    private fun importSession(uri: Uri) {
        runOperation("Validating and encrypting imported session…") {
            val authData = readLimited(uri)
            try {
                val client = clientFactory.create(authData)
                try {
                    authStore.save(authData)
                    deletionStatusStore.setAutomaticDeletionEnabled(true)
                } finally {
                    client.disconnect()
                }
            } finally {
                authData.fill(0)
            }
            runOnUiThreadIfAlive {
                updateMessageChoices(emptyList())
                operationStatus.text = "✓ Paired session imported and encrypted."
            }
        }
    }

    private fun fetchMessages() {
        runOperation("Connecting and fetching recent incoming messages…") {
            val fetched = sessionRunner.withConnectedClient { client ->
                val payload = client.fetchRecentIncomingMessages(
                    conversationCount = RECENT_CONVERSATION_COUNT,
                    messagesPerConversation = RECENT_MESSAGE_COUNT,
                )
                try {
                    LibgmMessage.decode(payload)
                } finally {
                    payload.fill(0)
                }
            }
            runOnUiThreadIfAlive {
                updateMessageChoices(fetched)
                operationStatus.text = if (fetched.isEmpty()) {
                    "No incoming messages found in the recent window."
                } else {
                    "✓ Fetched ${fetched.size} incoming message(s). Select the known test message only."
                }
            }
        }
    }

    private fun confirmSelectedArchive() {
        val selected = messages.getOrNull(messageSpinner.selectedItemPosition) ?: return
        AlertDialog.Builder(this)
            .setTitle("Archive this conversation?")
            .setMessage(
                "This moves the entire conversation containing the selected message out of the Google Messages inbox. " +
                    "Messages in the conversation are not deleted.\n\n" +
                    selected.displayLabel(maxTextLength = 160) +
                    messageTimeLabel(selected) +
                    "\n\nVerify this is the conversation you want to archive before continuing.",
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Archive") { _, _ -> archiveConversation(selected) }
            .show()
    }

    private fun archiveConversation(message: LibgmMessage) {
        runOperation("Archiving the selected conversation…", mutationAttempt = true) {
            sessionRunner.withConnectedClient { client -> client.archiveConversation(message.conversationId) }
            runOnUiThreadIfAlive {
                updateMessageChoices(messages.filterNot { it.conversationId == message.conversationId })
                operationStatus.text = "✓ Selected conversation archived. Confirm it moved out of the inbox."
            }
        }
    }

    private fun confirmClearCredentials() {
        AlertDialog.Builder(this)
            .setTitle("Clear imported credentials?")
            .setMessage("The encrypted paired session, pending archive targets, and their Android Keystore keys will be removed from this app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                clearCredentials()
            }
            .show()
    }

    private fun clearCredentials() {
        runOperation("Canceling pending work and clearing credentials…") {
            deletionStatusStore.setAutomaticDeletionEnabled(false)
            WorkManager.getInstance(this).cancelAllWorkByTag(DeletionScheduler.WORK_TAG)
            LibgmOperationLock.run {
                EncryptedPendingDeletionStore(this).clearAll()
                authStore.clear()
                deletionStatusStore.record(DeletionState.NEVER)
            }
            runOnUiThreadIfAlive {
                updateMessageChoices(emptyList())
                operationStatus.text = "✓ Imported credentials and pending archive data cleared."
                refreshAutomaticDeletionStatus()
            }
        }
    }

    private fun runOperation(
        startingMessage: String,
        mutationAttempt: Boolean = false,
        operation: () -> Unit,
    ) {
        if (busy) return
        setBusy(true)
        operationStatus.text = startingMessage
        executor.execute {
            try {
                operation()
            } catch (error: Throwable) {
                runOnUiThreadIfAlive {
                    val action = if (mutationAttempt) {
                        "Archive may have completed. Check the phone before retrying"
                    } else {
                        "Operation failed"
                    }
                    showError(action, error)
                }
            } finally {
                runOnUiThreadIfAlive { setBusy(false) }
            }
        }
    }

    private fun readLimited(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected document")
        return input.use {
            val storage = ByteArray(MAX_SESSION_BYTES + 1)
            var total = 0
            try {
                while (total < storage.size) {
                    val count = it.read(storage, total, storage.size - total)
                    if (count < 0) break
                    total += count
                }
                require(total in 1..MAX_SESSION_BYTES) {
                    "The selected session must be between 1 byte and 1 MiB"
                }
                storage.copyOf(total)
            } finally {
                storage.fill(0)
            }
        }
    }

    private fun updateMessageChoices(updated: List<LibgmMessage>) {
        messages = updated
        val labels = if (updated.isEmpty()) {
            listOf("Fetch messages to choose a test message")
        } else {
            updated.map { it.displayLabel() + messageTimeLabel(it) }
        }
        messageSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            labels,
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        archiveButton.isEnabled = !busy && updated.isNotEmpty()
    }

    private fun refreshStatus() {
        notificationAccessStatus.text = if (hasNotificationListenerAccess()) "✓ Enabled" else "⚠ Not enabled"
        refreshAutomaticDeletionStatus()
        refreshSessionControls()
    }

    private fun refreshAutomaticDeletionStatus() {
        if (!::automaticDeletionStatus.isInitialized) return
        val status = deletionStatusStore.load()
        val summary = when (status.state) {
            DeletionState.NEVER -> "No automatic archive attempted yet"
            DeletionState.QUEUED -> "Archive queued; waiting for network/work execution"
            DeletionState.RETRYING -> "Transient failure; bounded retry ${status.attempt + 1} of 3 queued"
            DeletionState.ARCHIVED -> "✓ Conversation containing the last uniquely matched message was archived"
            DeletionState.DELETED -> "Legacy result: last blocked message was deleted"
            DeletionState.NO_MATCH -> "Preserved: no high-confidence match"
            DeletionState.AMBIGUOUS -> "Preserved: matching result was ambiguous"
            DeletionState.AUTH_REQUIRED -> "⚠ Pairing needs repair; message was preserved"
            DeletionState.UNCERTAIN -> "⚠ Archive outcome was uncertain; no automatic retry"
            DeletionState.FAILED -> "⚠ Archiving failed safely after bounded attempts"
        }
        val time = if (status.timestampMillis > 0) {
            " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(status.timestampMillis))
        } else {
            ""
        }
        automaticDeletionStatus.text = "$summary$time"
    }

    private fun refreshSessionControls() {
        if (!::sessionStatus.isInitialized) return
        val hasSession = authStore.hasSession()
        sessionStatus.text = if (hasSession) "✓ Encrypted paired session stored" else "⚠ No paired session imported"
        scanPairButton.isEnabled = !busy
        importButton.isEnabled = !busy
        fetchButton.isEnabled = !busy && hasSession
        clearButton.isEnabled = !busy && hasSession
        archiveButton.isEnabled = !busy && messages.isNotEmpty()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        refreshSessionControls()
    }

    private fun showError(prefix: String, error: Throwable) {
        val detail = error.message?.take(240)?.ifBlank { null } ?: error.javaClass.simpleName
        operationStatus.text = "⚠ $prefix: $detail"
    }

    private fun messageTimeLabel(message: LibgmMessage): String =
        if (message.timestampMicros > 0) {
            " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(message.timestampMicros / 1_000))
        } else {
            ""
        }

    private fun runOnUiThreadIfAlive(action: () -> Unit) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) action()
        }
    }

    private fun hasNotificationListenerAccess(): Boolean {
        val component = ComponentName(this, MessagesNotificationListener::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(component)
        }
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
    }

    private fun text(value: String, size: Float, color: Int, top: Int, bottom: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(top)
                bottomMargin = dp(bottom)
            }
        }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val IMPORT_SESSION_REQUEST = 201
        private const val MAX_SESSION_BYTES = 1024 * 1024
        private const val RECENT_CONVERSATION_COUNT = 10
        private const val RECENT_MESSAGE_COUNT = 10
    }
}
