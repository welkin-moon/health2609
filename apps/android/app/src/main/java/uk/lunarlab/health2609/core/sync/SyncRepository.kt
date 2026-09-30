package uk.lunarlab.health2609.core.sync

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uk.lunarlab.health2609.BuildConfig
import java.security.MessageDigest
import java.util.UUID

private val Context.syncDataStore by preferencesDataStore(name = "health2609_sync_store")

data class SyncState(
    val isEnabled: Boolean = false,
    val username: String? = null,
    val userId: String? = null,
    val lastSyncTimestamp: String? = null,
    val deviceFingerprint: String = "unknown"
)

class SyncRepository(private val context: Context) {
    private val dataStore = context.applicationContext.syncDataStore
    private val httpClient = OkHttpClient.Builder().build()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    val syncState: Flow<SyncState> = dataStore.data.map { prefs ->
        SyncState(
            isEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            username = prefs[KEY_SYNC_USERNAME],
            userId = prefs[KEY_SYNC_USER_ID],
            lastSyncTimestamp = prefs[KEY_LAST_SYNC_AT],
            deviceFingerprint = prefs[KEY_DEVICE_FINGERPRINT] ?: getOrCreateDeviceFingerprint()
        )
    }

    private fun getOrCreateDeviceFingerprint(): String {
        return "android_${Build.MANUFACTURER}_${Build.MODEL}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun registerOrLogin(username: String, passkey: String): Result<String> = runCatching {
        val cleanUser = username.trim().lowercase()
        val saltBytes = E2eeCrypto.generateSalt()
        val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
        val passHash = sha256Hex(passkey + saltBase64)

        val masterKey = E2eeCrypto.deriveKey(passkey, saltBytes)
        val masterKeyCheckPayload = E2eeCrypto.encrypt("HEALTH2609_MASTER_OK".toByteArray(Charsets.UTF_8), masterKey)
        val masterKeyEnc = "${masterKeyCheckPayload.ciphertextBase64}:${masterKeyCheckPayload.nonceBase64}"

        val deviceFingerprint = getOrCreateDeviceFingerprint()
        val deviceName = "${Build.BRAND} ${Build.MODEL}"

        val registerJson = JSONObject().apply {
            put("username", cleanUser)
            put("passwordSalt", saltBase64)
            put("passwordHash", passHash)
            put("masterKeyEnc", masterKeyEnc)
            put("deviceFingerprint", deviceFingerprint)
            put("deviceName", deviceName)
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/sync/auth/register")
            .post(registerJson.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        if (response.code == 409) {
            // Already registered, proceed to login
            return@runCatching login(cleanUser, passkey)
        }

        if (!response.isSuccessful) {
            throw IllegalStateException("注册同步账号失败 (${response.code})")
        }

        val resBody = JSONObject(response.body?.string() ?: "{}")
        val userId = resBody.getString("userId")

        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = true
            prefs[KEY_SYNC_USERNAME] = cleanUser
            prefs[KEY_SYNC_USER_ID] = userId
            prefs[KEY_DEVICE_FINGERPRINT] = deviceFingerprint
        }

        "已成功配置端对端加密多端同步"
    }

    private suspend fun login(username: String, passkey: String): String {
        // Fallback login logic
        val deviceFingerprint = getOrCreateDeviceFingerprint()
        val passHash = sha256Hex(passkey + "health2609_static_salt")

        val loginJson = JSONObject().apply {
            put("username", username)
            put("passwordHash", passHash)
            put("deviceFingerprint", deviceFingerprint)
            put("deviceName", "${Build.BRAND} ${Build.MODEL}")
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/sync/auth/login")
            .post(loginJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("验证凭证失败 (${response.code})")
        }

        val resBody = JSONObject(response.body?.string() ?: "{}")
        val userId = resBody.getString("userId")

        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = true
            prefs[KEY_SYNC_USERNAME] = username
            prefs[KEY_SYNC_USER_ID] = userId
            prefs[KEY_DEVICE_FINGERPRINT] = deviceFingerprint
        }

        return "登录同步成功"
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = enabled
        }
    }

    companion object {
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("e2ee_sync_enabled")
        private val KEY_SYNC_USERNAME = stringPreferencesKey("e2ee_sync_username")
        private val KEY_SYNC_USER_ID = stringPreferencesKey("e2ee_sync_user_id")
        private val KEY_LAST_SYNC_AT = stringPreferencesKey("e2ee_last_sync_at")
        private val KEY_DEVICE_FINGERPRINT = stringPreferencesKey("e2ee_device_fingerprint")
    }
}
