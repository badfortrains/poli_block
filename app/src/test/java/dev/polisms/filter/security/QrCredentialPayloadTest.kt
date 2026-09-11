package dev.polisms.filter.security

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class QrCredentialPayloadTest {
    @Test
    fun `node gzip fixture decodes compatibly`() {
        val fixture = "GM1:H4sIAAAAAAAAEz3KMQqEQBBE0bv8uA1MJxMMNFrBEwxro4O4I7YKi3h3kRGzesU_WP-z4ugnNfO9Wua3dUDYdbEQf7hc-MY4BjXcQVuXOCx0CFXCXbfPj_BJMyIUTZ3g7-SVec7zAqrAki56AAAA"

        val decoded = JSONObject(QrCredentialPayload.decode(fixture.encodeToByteArray()).decodeToString())

        assertEquals("sid", decoded.getJSONObject("cookies").getString("SID"))
    }

    @Test
    fun `valid versioned payload returns only supported cookies`() {
        val cookies = JSONObject().put("UNRELATED", "discard-me")
        QrCredentialPayload.requiredCookies.forEach { cookies.put(it, "value-$it") }
        cookies.put("__Secure-1PSIDTS", "optional")

        val decoded = JSONObject(QrCredentialPayload.decode(payload(cookies)).decodeToString())
            .getJSONObject("cookies")

        assertFalse(decoded.has("UNRELATED"))
        assertEquals("value-SID", decoded.getString("SID"))
        assertEquals("optional", decoded.getString("__Secure-1PSIDTS"))
    }

    @Test
    fun `wrong prefix is rejected`() {
        val error = runCatching { QrCredentialPayload.decode("https://example.com".encodeToByteArray()) }
            .exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `missing required cookie is rejected`() {
        val cookies = JSONObject()
        QrCredentialPayload.requiredCookies.dropLast(1).forEach { cookies.put(it, "value") }

        val error = runCatching { QrCredentialPayload.decode(payload(cookies)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("missing required cookies"))
    }

    @Test
    fun `unsupported version is rejected`() {
        val cookies = JSONObject()
        QrCredentialPayload.requiredCookies.forEach { cookies.put(it, "value") }

        val error = runCatching { QrCredentialPayload.decode(payload(cookies, version = 2)) }
            .exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun payload(cookies: JSONObject, version: Int = 1): ByteArray {
        val json = JSONObject()
            .put("type", "gmessages-auth")
            .put("version", version)
            .put("cookies", cookies)
            .toString()
            .encodeToByteArray()
        val compressed = ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(json) }
            output.toByteArray()
        }
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        return "GM1:$encoded".encodeToByteArray()
    }
}
