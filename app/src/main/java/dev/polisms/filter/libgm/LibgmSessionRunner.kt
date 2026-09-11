package dev.polisms.filter.libgm

import dev.polisms.filter.security.LibgmAuthStore

class SessionPersistenceException(
    val operationCompleted: Boolean,
    cause: Throwable,
) : Exception(
    if (operationCompleted) {
        "The libgm operation completed but refreshed authentication could not be saved"
    } else {
        "Refreshed libgm authentication could not be saved"
    },
    cause,
)

class LibgmSessionRunner(
    private val authStore: LibgmAuthStore,
    private val clientFactory: LibgmClientFactory,
) {
    fun <T> withConnectedClient(block: (LibgmClient) -> T): T =
        LibgmOperationLock.run { runConnected(block) }

    private fun <T> runConnected(block: (LibgmClient) -> T): T {
        val authData = authStore.load()
            ?: throw IllegalStateException("Import a paired libgm session first")
        var client: LibgmClient? = null
        var operationError: Throwable? = null
        var operationCompleted = false

        try {
            client = clientFactory.create(authData)
            client.connect()
            val result = block(client)
            operationCompleted = true
            return result
        } catch (error: Throwable) {
            operationError = error
            throw error
        } finally {
            authData.fill(0)
            client?.let { connectedClient ->
                try {
                    val updatedAuth = connectedClient.updatedAuthData()
                    try {
                        authStore.save(updatedAuth)
                    } finally {
                        updatedAuth.fill(0)
                    }
                } catch (saveError: Throwable) {
                    if (operationError != null) {
                        operationError.addSuppressed(saveError)
                    } else {
                        throw SessionPersistenceException(operationCompleted, saveError)
                    }
                } finally {
                    connectedClient.disconnect()
                }
            }
        }
    }
}
