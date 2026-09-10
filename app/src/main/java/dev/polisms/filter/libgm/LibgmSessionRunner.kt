package dev.polisms.filter.libgm

import dev.polisms.filter.security.LibgmAuthStore

class LibgmSessionRunner(
    private val authStore: LibgmAuthStore,
    private val clientFactory: LibgmClientFactory,
) {
    fun <T> withConnectedClient(block: (LibgmClient) -> T): T {
        val authData = authStore.load()
            ?: throw IllegalStateException("Import a paired libgm session first")
        var client: LibgmClient? = null
        var operationError: Throwable? = null

        try {
            client = clientFactory.create(authData)
            client.connect()
            return block(client)
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
                        throw saveError
                    }
                } finally {
                    connectedClient.disconnect()
                }
            }
        }
    }
}
