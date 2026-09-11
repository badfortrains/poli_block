package dev.polisms.filter.libgm

import dev.polisms.libgm.mobilebridge.Mobilebridge

interface LibgmClient {
    fun connect()

    fun fetchRecentIncomingMessages(
        conversationCount: Int,
        messagesPerConversation: Int,
    ): ByteArray

    fun archiveConversation(conversationId: String)

    fun updatedAuthData(): ByteArray

    fun disconnect()
}

fun interface LibgmClientFactory {
    fun create(authData: ByteArray): LibgmClient
}

class GoLibgmClientFactory : LibgmClientFactory {
    override fun create(authData: ByteArray): LibgmClient =
        GoLibgmClient(Mobilebridge.newClient(authData))
}

private class GoLibgmClient(
    private val client: dev.polisms.libgm.mobilebridge.Client,
) : LibgmClient {
    override fun connect() = client.connect()

    override fun fetchRecentIncomingMessages(
        conversationCount: Int,
        messagesPerConversation: Int,
    ): ByteArray = client.fetchRecentIncomingMessages(
        conversationCount.toLong(),
        messagesPerConversation.toLong(),
    )

    override fun archiveConversation(conversationId: String) = client.archiveConversation(conversationId)

    override fun updatedAuthData(): ByteArray = client.updatedAuthData()

    override fun disconnect() = client.disconnect()
}
