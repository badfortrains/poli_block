package dev.polisms.filter.deletion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface PendingDeletionStore {
    fun save(id: String, target: DeletionTarget)

    fun load(id: String): DeletionTarget?

    fun remove(id: String)

    fun clearAll()
}

class EncryptedPendingDeletionStore(context: Context) : PendingDeletionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun save(id: String, target: DeletionTarget) {
        require(id.matches(ID_PATTERN)) { "Invalid pending deletion ID" }
        val plaintext = target.encode()
        require(plaintext.size <= MAX_TARGET_BYTES) { "Pending deletion target is unexpectedly large" }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, existingKeyOrCreate())
                updateAAD(aad(id))
            }
            val ciphertext = cipher.doFinal(plaintext)
            val payload = listOf(
                FORMAT_VERSION,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ).joinToString(":")
            check(preferences.edit().putString(id, payload).commit()) {
                "Could not persist pending deletion"
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override fun load(id: String): DeletionTarget? {
        require(id.matches(ID_PATTERN)) { "Invalid pending deletion ID" }
        val payload = preferences.getString(id, null) ?: return null
        val parts = payload.split(':')
        require(parts.size == 3 && parts[0] == FORMAT_VERSION) {
            "Stored pending deletion has an unsupported format"
        }
        val plaintext = Cipher.getInstance(TRANSFORMATION).run {
            init(
                Cipher.DECRYPT_MODE,
                existingKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[1], Base64.NO_WRAP)),
            )
            updateAAD(aad(id))
            doFinal(Base64.decode(parts[2], Base64.NO_WRAP))
        }
        return try {
            DeletionTarget.decode(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    override fun remove(id: String) {
        require(id.matches(ID_PATTERN)) { "Invalid pending deletion ID" }
        check(preferences.edit().remove(id).commit()) { "Could not remove pending deletion" }
    }

    override fun clearAll() {
        check(preferences.edit().clear().commit()) { "Could not clear pending deletions" }
        keyStore().apply {
            if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
        }
    }

    private fun existingKey(): SecretKey =
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw IllegalStateException("The pending deletion encryption key is missing")

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

    private fun aad(id: String): ByteArray = "political-sms-filter/pending-deletion/v1/$id".encodeToByteArray()

    companion object {
        private const val PREFERENCES_NAME = "pending_deletions"
        private const val FORMAT_VERSION = "v1"
        private const val KEY_ALIAS = "dev.polisms.filter.pending-deletion.v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MAX_TARGET_BYTES = 128 * 1_024
        private val ID_PATTERN = Regex("[a-f0-9]{64}")
    }
}
