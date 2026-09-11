package dev.polisms.filter.deletion

import android.content.Context

enum class DeletionState {
    NEVER,
    QUEUED,
    RETRYING,
    DELETED,
    NO_MATCH,
    AMBIGUOUS,
    AUTH_REQUIRED,
    UNCERTAIN,
    FAILED,
}

data class DeletionStatus(
    val state: DeletionState,
    val timestampMillis: Long,
    val attempt: Int,
)

class DeletionStatusStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun record(state: DeletionState, attempt: Int = 0) {
        preferences.edit()
            .putString(STATE_KEY, state.name)
            .putLong(TIMESTAMP_KEY, System.currentTimeMillis())
            .putInt(ATTEMPT_KEY, attempt)
            .apply()
    }

    fun load(): DeletionStatus {
        val name = preferences.getString(STATE_KEY, null)
        val state = name?.let { runCatching { DeletionState.valueOf(it) }.getOrNull() }
            ?: DeletionState.NEVER
        return DeletionStatus(
            state = state,
            timestampMillis = preferences.getLong(TIMESTAMP_KEY, 0),
            attempt = preferences.getInt(ATTEMPT_KEY, 0),
        )
    }

    fun setAutomaticDeletionEnabled(enabled: Boolean) {
        check(preferences.edit().putBoolean(ENABLED_KEY, enabled).commit()) {
            "Could not update automatic deletion state"
        }
    }

    fun isAutomaticDeletionEnabled(): Boolean = preferences.getBoolean(ENABLED_KEY, true)

    companion object {
        private const val PREFERENCES_NAME = "deletion_status"
        private const val STATE_KEY = "state"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val ATTEMPT_KEY = "attempt"
        private const val ENABLED_KEY = "enabled"
    }
}
