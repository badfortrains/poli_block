package dev.polisms.filter.security

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

object QrCredentialPayload {
    val requiredCookies = listOf("SID", "HSID", "SSID", "OSID", "APISID", "SAPISID")
    private val allowedCookies = requiredCookies + "__Secure-1PSIDTS"

    fun decode(rawPayload: ByteArray): ByteArray {
        require(rawPayload.size in 5..MAX_QR_BYTES) { "Credential QR has an invalid size" }
        val encoded = rawPayload.decodeToString()
        require(encoded.startsWith(PREFIX)) { "This is not a Political SMS Filter credential QR" }
        val compressed = Base64.getUrlDecoder().decode(encoded.substring(PREFIX.length))
        require(compressed.isNotEmpty()) { "Credential QR payload is empty" }
        val decoded = try {
            gunzipLimited(compressed)
        } finally {
            compressed.fill(0)
        }
        return try {
            val root = JSONObject(decoded.decodeToString())
            require(root.getString("type") == "gmessages-auth") { "Credential QR has the wrong type" }
            require(root.getInt("version") == 1) { "Credential QR version is not supported" }
            val imported = root.getJSONObject("cookies")
            val minimized = JSONObject()
            for (name in allowedCookies) {
                if (imported.has(name) && !imported.isNull(name)) {
                    val value = imported.getString(name)
                    require(value.isNotEmpty()) { "Cookie $name is empty" }
                    require(value.length <= MAX_COOKIE_LENGTH) { "Cookie $name is unexpectedly large" }
                    minimized.put(name, value)
                }
            }
            val missing = requiredCookies.filter { minimized.optString(it).isEmpty() }
            require(missing.isEmpty()) { "Credential QR is missing required cookies: ${missing.joinToString()}" }
            JSONObject().put("cookies", minimized).toString().encodeToByteArray()
        } finally {
            decoded.fill(0)
        }
    }

    private fun gunzipLimited(compressed: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            val storage = ByteArray(MAX_DECOMPRESSED_BYTES + 1)
            var total = 0
            try {
                while (total < storage.size) {
                    val count = input.read(storage, total, storage.size - total)
                    if (count < 0) break
                    total += count
                }
                require(total in 1..MAX_DECOMPRESSED_BYTES) { "Credential QR expands beyond the safe limit" }
                storage.copyOf(total)
            } finally {
                storage.fill(0)
            }
        }

    private const val PREFIX = "GM1:"
    private const val MAX_QR_BYTES = 4_096
    private const val MAX_DECOMPRESSED_BYTES = 64 * 1_024
    private const val MAX_COOKIE_LENGTH = 8_192
}
