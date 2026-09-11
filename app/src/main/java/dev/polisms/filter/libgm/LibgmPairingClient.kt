package dev.polisms.filter.libgm

import dev.polisms.libgm.mobilebridge.Mobilebridge

interface LibgmPairingClient {
    fun start(): String

    fun finish(): ByteArray

    fun cancel()

    fun disconnect()
}

fun interface LibgmPairingClientFactory {
    fun create(cookieData: ByteArray): LibgmPairingClient
}

class GoLibgmPairingClientFactory : LibgmPairingClientFactory {
    override fun create(cookieData: ByteArray): LibgmPairingClient =
        GoLibgmPairingClient(Mobilebridge.newPairingClient(cookieData))
}

private class GoLibgmPairingClient(
    private val client: dev.polisms.libgm.mobilebridge.PairingClient,
) : LibgmPairingClient {
    override fun start(): String = client.start()

    override fun finish(): ByteArray = client.finish()

    override fun cancel() = client.cancel()

    override fun disconnect() = client.disconnect()
}
