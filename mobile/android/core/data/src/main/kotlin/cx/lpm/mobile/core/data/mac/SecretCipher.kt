package cx.lpm.mobile.core.data.mac

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/** Seals small secrets for storage: the output carries everything needed to open it. */
interface SecretCipher {
    fun seal(plain: ByteArray): ByteArray

    /** Null when the blob can't be opened (the key is gone, or it was tampered with). */
    fun open(sealed: ByteArray): ByteArray?
}

/**
 * AES-256-GCM under a non-exportable Android Keystore key. The key never leaves the
 * device, which is why app data is excluded from backup: a copied blob could not be
 * opened on another phone. Output is `iv (12 bytes) || ciphertext+tag`.
 */
class KeystoreCipher @Inject constructor() : SecretCipher {
    private val key: SecretKey by lazy { existingKey() ?: newKey() }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size <= IV_BYTES) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun newKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cx.lpm.mobile.credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
