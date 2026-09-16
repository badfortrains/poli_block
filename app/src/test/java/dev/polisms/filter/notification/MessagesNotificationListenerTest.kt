package dev.polisms.filter.notification

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesNotificationListenerTest {
    @Test
    fun `silent notification importance does not vibrate`() {
        assertFalse(shouldVibrateForImportance(NotificationManager.IMPORTANCE_NONE))
        assertFalse(shouldVibrateForImportance(NotificationManager.IMPORTANCE_MIN))
        assertFalse(shouldVibrateForImportance(NotificationManager.IMPORTANCE_LOW))
    }

    @Test
    fun `alerting notification importance vibrates`() {
        assertTrue(shouldVibrateForImportance(NotificationManager.IMPORTANCE_DEFAULT))
        assertTrue(shouldVibrateForImportance(NotificationManager.IMPORTANCE_HIGH))
    }

    @Test
    fun `missing or unspecified ranking retains previous vibration behavior`() {
        assertTrue(shouldVibrateForImportance(null))
        assertTrue(shouldVibrateForImportance(NotificationManager.IMPORTANCE_UNSPECIFIED))
    }
}
