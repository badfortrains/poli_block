package dev.polisms.filter.libgm

import dev.polisms.filter.security.LibgmAuthStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibgmSessionRunnerTest {
    @Test
    fun `successful operation persists refreshed auth and disconnects`() {
        val store = MemoryAuthStore(byteArrayOf(1, 2, 3))
        val client = FakeClient(updatedAuth = byteArrayOf(4, 5, 6))
        val runner = LibgmSessionRunner(store) { client }

        val result = runner.withConnectedClient { "done" }

        assertEquals("done", result)
        assertTrue(client.connected)
        assertTrue(client.disconnected)
        assertArrayEquals(byteArrayOf(4, 5, 6), store.value)
    }

    @Test
    fun `failed operation still persists refreshed auth and disconnects`() {
        val store = MemoryAuthStore(byteArrayOf(1, 2, 3))
        val client = FakeClient(updatedAuth = byteArrayOf(7, 8, 9))
        val runner = LibgmSessionRunner(store) { client }

        val error = runCatching {
            runner.withConnectedClient<Unit> { throw IllegalStateException("request failed") }
        }.exceptionOrNull()

        assertEquals("request failed", error?.message)
        assertTrue(client.disconnected)
        assertArrayEquals(byteArrayOf(7, 8, 9), store.value)
    }

    @Test
    fun `missing auth does not create a client`() {
        val store = MemoryAuthStore(null)
        var factoryCalled = false
        val runner = LibgmSessionRunner(store) {
            factoryCalled = true
            FakeClient(byteArrayOf())
        }

        val error = runCatching { runner.withConnectedClient { } }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(!factoryCalled)
    }

    @Test
    fun `auth save failure still disconnects and is reported`() {
        val store = MemoryAuthStore(byteArrayOf(1, 2, 3)).apply { failSave = true }
        val client = FakeClient(updatedAuth = byteArrayOf(4, 5, 6))
        val runner = LibgmSessionRunner(store) { client }

        val error = runCatching { runner.withConnectedClient { "completed" } }.exceptionOrNull()

        assertTrue(error is SessionPersistenceException)
        assertTrue((error as SessionPersistenceException).operationCompleted)
        assertEquals("save failed", error.cause?.message)
        assertTrue(client.disconnected)
    }

    private class MemoryAuthStore(initial: ByteArray?) : LibgmAuthStore {
        var value: ByteArray? = initial?.copyOf()
        var failSave = false

        override fun hasSession(): Boolean = value != null

        override fun load(): ByteArray? = value?.copyOf()

        override fun save(authData: ByteArray) {
            if (failSave) throw IllegalStateException("save failed")
            value = authData.copyOf()
        }

        override fun clear() {
            value = null
        }
    }

    private class FakeClient(
        private val updatedAuth: ByteArray,
    ) : LibgmClient {
        var connected = false
        var disconnected = false

        override fun connect() {
            connected = true
        }

        override fun fetchRecentIncomingMessages(
            conversationCount: Int,
            messagesPerConversation: Int,
        ): ByteArray = byteArrayOf()

        override fun deleteMessage(messageId: String) = Unit

        override fun updatedAuthData(): ByteArray = updatedAuth.copyOf()

        override fun disconnect() {
            disconnected = true
        }
    }
}
