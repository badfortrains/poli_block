package dev.polisms.filter.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface LibgmAuthStore {
    fun hasSession(): Boolean

    fun load(): ByteArray?

    fun save(authData: ByteArray)

    fun clear()
}

class EncryptedLibgmAuthStore(context: Context) : LibgmAuthStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hasSession(): Boolean = preferences.contains(PAYLOAD_KEY)

    override fun load(): ByteArray? {
        val payload = preferences.getString(PAYLOAD_KEY, null) ?: return null
        val parts = payload.split(':')
        require(parts.size == 3 && parts[0] == FORMAT_VERSION) {
            "Stored libgm session has an unsupported format"
        }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        require(iv.isNotEmpty() && ciphertext.isNotEmpty()) { "Stored libgm session is corrupt" }

        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(AUTHENTICATED_CONTEXT)
            doFinal(ciphertext)
        }
    }

    override fun save(authData: ByteArray) {
        require(authData.isNotEmpty()) { "Cannot save empty libgm auth data" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, existingKeyOrCreate())
            updateAAD(AUTHENTICATED_CONTEXT)
        }
        val ciphertext = cipher.doFinal(authData)
        val payload = listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(":")
        check(preferences.edit().putString(PAYLOAD_KEY, payload).commit()) {
            "Could not persist the encrypted libgm session"
        }
    }

    override fun clear() {
        check(preferences.edit().remove(PAYLOAD_KEY).commit()) {
            "Could not clear the encrypted libgm session"
        }
        keyStore().apply {
            if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
        }
    }

    private fun existingKey(): SecretKey =
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw IllegalStateException("The libgm encryption key is missing; import the session again")

    private fun existingKeyOrCreate(): SecretKey {
        val store = keyStore()
        return (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        private const val PREFERENCES_NAME = "libgm_credentials"
        private const val PAYLOAD_KEY = "encrypted_auth"
        private const val FORMAT_VERSION = "v1"
        private const val KEY_ALIAS = "dev.polisms.filter.libgm.auth.v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private val AUTHENTICATED_CONTEXT = "political-sms-filter/libgm-auth/v1".encodeToByteArray()
    }
}
