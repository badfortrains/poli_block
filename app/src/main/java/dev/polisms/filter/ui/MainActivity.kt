package dev.polisms.filter.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.polisms.filter.filtering.Stop2EndFilter
import dev.polisms.filter.notification.MessagesNotificationListener
import dev.polisms.filter.notification.ReplacementNotificationManager

class MainActivity : Activity() {
    private lateinit var notificationAccessStatus: TextView
    private lateinit var appNotificationsStatus: TextView

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

    private fun buildContent(): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
            setBackgroundColor(Color.rgb(247, 248, 250))
        }

        content.addView(text("Political SMS Filter", 28f, Color.rgb(25, 38, 53), 0, 16))
        content.addView(text("Milestones 1–2", 14f, Color.rgb(75, 91, 107), 0, 28))

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

        content.addView(text("libgm", 18f, Color.BLACK, 26, 6))
        content.addView(text("The independent desktop proof is in libgm-proof. Android integration begins at milestone 4.", 16f, Color.DKGRAY, 0, 0))

        return ScrollView(this).apply { addView(content) }
    }

    private fun refreshStatus() {
        notificationAccessStatus.text = if (hasNotificationListenerAccess()) "✓ Enabled" else "⚠ Not enabled"
        val enabled = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        appNotificationsStatus.text = if (enabled) "✓ Enabled" else "⚠ Not enabled"
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
}
