package dev.polisms.filter.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
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
import dev.polisms.filter.filtering.Stop2EndFilter
import dev.polisms.filter.libgm.GoLibgmClientFactory
import dev.polisms.filter.libgm.LibgmMessage
import dev.polisms.filter.libgm.LibgmSessionRunner
import dev.polisms.filter.notification.MessagesNotificationListener
import dev.polisms.filter.notification.ReplacementNotificationManager
import dev.polisms.filter.security.EncryptedLibgmAuthStore
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var notificationAccessStatus: TextView
    private lateinit var appNotificationsStatus: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var operationStatus: TextView
    private lateinit var importButton: Button
    private lateinit var fetchButton: Button
    private lateinit var deleteButton: Button
    private lateinit var clearButton: Button
    private lateinit var messageSpinner: Spinner

    private val executor = Executors.newSingleThreadExecutor()
    private val authStore by lazy { EncryptedLibgmAuthStore(applicationContext) }
    private val clientFactory = GoLibgmClientFactory()
    private val sessionRunner by lazy { LibgmSessionRunner(authStore, clientFactory) }
    private var messages: List<LibgmMessage> = emptyList()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReplacementNotificationManager(this).ensureChannel()
        setContentView(buildContent())
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::notificationAccessStatus.isInitialized) refreshStatus()
    }

    override fun onDestroy() {
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
        content.addView(text("Milestones 1–4", 14f, Color.rgb(75, 91, 107), 0, 28))

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
                putExtra(Settings.EXTRA_APP_PACKAGE, ReplacementNotificationManager.GOOGLE_MESSAGES_PACKAGE)
            })
        })

        content.addView(text("Filter", 18f, Color.BLACK, 26, 6))
        content.addView(text("Messages containing (case-insensitive): ${Stop2EndFilter.KEYWORD}", 16f, Color.DKGRAY, 0, 16))

        content.addView(text("Filter app notifications", 18f, Color.BLACK, 12, 6))
        appNotificationsStatus = text("Checking…", 16f, Color.DKGRAY, 0, 10)
        content.addView(appNotificationsStatus)
        content.addView(button("Test replacement notification") {
            requestNotificationPermissionIfNeeded()
            ReplacementNotificationManager(this).postTest()
        })

        content.addView(text("Manual libgm deletion proof", 18f, Color.BLACK, 26, 6))
        content.addView(text("Import the paired session.json created by libgm-proof. It is encrypted with an app-only Android Keystore key.", 16f, Color.DKGRAY, 0, 10))
        sessionStatus = text("Checking…", 16f, Color.DKGRAY, 0, 10)
        content.addView(sessionStatus)

        importButton = button("Import paired session.json") { openSessionPicker() }
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

        deleteButton = button("Delete selected test message") { confirmSelectedDeletion() }
        content.addView(deleteButton)

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

    private fun importSession(uri: Uri) {
        runOperation("Validating and encrypting imported session…") {
            val authData = readLimited(uri)
            try {
                val client = clientFactory.create(authData)
                try {
                    authStore.save(authData)
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

    private fun confirmSelectedDeletion() {
        val selected = messages.getOrNull(messageSpinner.selectedItemPosition) ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete this message?")
            .setMessage(
                "This permanently deletes the selected message from Google Messages and its synced devices.\n\n" +
                    selected.displayLabel(maxTextLength = 160) +
                    messageTimeLabel(selected) +
                    "\n\nVerify this is your disposable test message before continuing.",
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteMessage(selected) }
            .show()
    }

    private fun deleteMessage(message: LibgmMessage) {
        runOperation("Deleting the selected message…", deletionAttempt = true) {
            sessionRunner.withConnectedClient { client -> client.deleteMessage(message.messageId) }
            runOnUiThreadIfAlive {
                updateMessageChoices(messages.filterNot { it.messageId == message.messageId })
                operationStatus.text = "✓ Selected message deleted. Confirm it disappeared from the phone."
            }
        }
    }

    private fun confirmClearCredentials() {
        AlertDialog.Builder(this)
            .setTitle("Clear imported credentials?")
            .setMessage("The encrypted paired session and its Android Keystore key will be removed from this app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                try {
                    authStore.clear()
                    updateMessageChoices(emptyList())
                    operationStatus.text = "✓ Imported credentials cleared."
                    refreshSessionControls()
                } catch (error: Throwable) {
                    showError("Could not clear credentials", error)
                }
            }
            .show()
    }

    private fun runOperation(
        startingMessage: String,
        deletionAttempt: Boolean = false,
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
                    val action = if (deletionAttempt) {
                        "Delete may have completed. Check the phone before retrying"
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
        deleteButton.isEnabled = !busy && updated.isNotEmpty()
    }

    private fun refreshStatus() {
        notificationAccessStatus.text = if (hasNotificationListenerAccess()) "✓ Enabled" else "⚠ Not enabled"
        val enabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        appNotificationsStatus.text = if (enabled) "✓ Enabled" else "⚠ Not enabled"
        refreshSessionControls()
    }

    private fun refreshSessionControls() {
        if (!::sessionStatus.isInitialized) return
        val hasSession = authStore.hasSession()
        sessionStatus.text = if (hasSession) "✓ Encrypted paired session stored" else "⚠ No paired session imported"
        importButton.isEnabled = !busy
        fetchButton.isEnabled = !busy && hasSession
        clearButton.isEnabled = !busy && hasSession
        deleteButton.isEnabled = !busy && messages.isNotEmpty()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
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
