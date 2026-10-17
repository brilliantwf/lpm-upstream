package cx.lpm.mobile.core.data.mac

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.di.CredentialsDataStore
import cx.lpm.mobile.core.model.string
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The iOS Keychain items: each Mac's device credential (`device-credential.<localId>`)
 * and certificate pin (`cert-pin.<localId>`), the phone's push key (`push-key`) and
 * this install's id (`phone-id`).
 * Values are sealed by [SecretCipher] and kept in their own DataStore. [load] once at
 * startup; reads are then synchronous and safe from any thread (the TLS pin check
 * reads a pin off the main thread), writes reach disk asynchronously.
 */
@Singleton
class CredentialStore @Inject constructor(
    @param:CredentialsDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val secrets = ConcurrentHashMap<String, String>()

    suspend fun load() {
        val stored = dataStore.data.first().asMap()
        for ((key, value) in stored) {
            val sealed = (value as? String)?.let(::decodeBase64) ?: continue
            cipher.open(sealed)?.let { secrets[key.name] = it.decodeToString() }
        }
    }

    fun save(deviceId: String, token: String, localId: String) {
        val json = buildJsonObject {
            put("deviceId", deviceId)
            put("token", token)
        }
        write(credentialAccount(localId), json.toString())
    }

    fun load(localId: String): LpmClient.Credential? {
        val text = secrets[credentialAccount(localId)] ?: return null
        val o = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return null
        val deviceId = o.string("deviceId") ?: return null
        val token = o.string("token") ?: return null
        return LpmClient.Credential(deviceId, token)
    }

    /** Drops the Mac's credential and its pin. */
    fun delete(localId: String) {
        write(credentialAccount(localId), null)
        write(pinAccount(localId), null)
    }

    /** The pinned leaf-cert fingerprint; null means "not pinned yet". */
    fun loadPin(localId: String): String? = secrets[pinAccount(localId)]

    fun savePin(fingerprint: String, localId: String) = write(pinAccount(localId), fingerprint)

    fun deletePin(localId: String) = write(pinAccount(localId), null)

    /** 32 random bytes, one per phone, registered with every Mac for push decryption. */
    fun loadOrCreatePushKey(): ByteArray {
        secrets[PUSH_KEY]?.let(::decodeBase64)?.takeIf {
            it.size == PUSH_KEY_BYTES
        }?.let { return it }
        val key = ByteArray(PUSH_KEY_BYTES).also(SecureRandom()::nextBytes)
        write(PUSH_KEY, Base64.getEncoder().encodeToString(key))
        return key
    }

    /**
     * This install's own id, sent in every connection's first frame so a Mac knows the
     * phone behind any credential, its own or another Mac's. A lowercase UUID minted
     * once and never rotated.
     */
    val phoneId: String
        get() = secrets[PHONE_ID] ?: synchronized(this) {
            secrets[PHONE_ID] ?: UUID.randomUUID().toString().also { write(PHONE_ID, it) }
        }

    private fun write(account: String, value: String?) {
        if (value == null) secrets.remove(account) else secrets[account] = value
        val sealed = value?.let {
            Base64.getEncoder().encodeToString(cipher.seal(it.encodeToByteArray()))
        }
        val key = stringPreferencesKey(account)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dataStore.edit { if (sealed == null) it.remove(key) else it[key] = sealed }
        }
    }

    private fun decodeBase64(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        const val PUSH_KEY = "push-key"
        const val PHONE_ID = "phone-id"
        private const val PUSH_KEY_BYTES = 32

        fun credentialAccount(localId: String): String = "device-credential.$localId"

        fun pinAccount(localId: String): String = "cert-pin.$localId"
    }
}
