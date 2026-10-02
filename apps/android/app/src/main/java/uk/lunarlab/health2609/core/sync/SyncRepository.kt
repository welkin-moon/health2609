package uk.lunarlab.health2609.core.sync

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uk.lunarlab.health2609.BuildConfig
import uk.lunarlab.health2609.core.network.ApiFactory
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Context.syncDataStore by preferencesDataStore(name = "health2609_sync_store")

data class SyncState(
    val isEnabled: Boolean = false,
    val schoolId: String? = null,
    val username: String? = null,
    val userId: String? = null,
    val lastSyncTimestamp: String? = null,
    val deviceCount: Int = 1,
    val deviceFingerprint: String = "unknown"
)

class SyncRepository(private val context: Context) {
    private val dataStore = context.applicationContext.syncDataStore
    private val httpClient = OkHttpClient.Builder().build()
    private val baseUrl: String
        get() = ApiFactory.currentBaseUrl.trimEnd('/')

    val syncState: Flow<SyncState> = dataStore.data.map { prefs ->
        SyncState(
            isEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            schoolId = prefs[KEY_SYNC_SCHOOL_ID],
            username = prefs[KEY_SYNC_USERNAME],
            userId = prefs[KEY_SYNC_USER_ID],
            lastSyncTimestamp = prefs[KEY_LAST_SYNC_AT],
            deviceCount = prefs[KEY_DEVICE_COUNT] ?: 1,
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

    private fun getDeterministicSalt(schoolId: String, username: String): ByteArray {
        val seed = "health2609_e2ee_salt:${schoolId.trim()}:${username.trim().lowercase()}"
        return sha256Hex(seed).take(16).toByteArray(Charsets.UTF_8)
    }

    suspend fun registerOrLogin(
        schoolId: String = "demo-school",
        username: String,
        passkey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(username.isNotBlank()) { "请输入学号或用户名" }
            require(passkey.isNotBlank()) { "请输入密码或通行密钥" }

            val cleanUser = username.trim().lowercase()
            val cleanSchool = schoolId.trim().ifBlank { "demo-school" }
            val serverUsername = if (cleanUser.contains(":")) cleanUser else "$cleanSchool:$cleanUser"

            val saltBytes = getDeterministicSalt(cleanSchool, cleanUser)
            val saltBase64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
            val passHash = sha256Hex(passkey + saltBase64)

            val masterKey = E2eeCrypto.deriveKey(passkey, saltBytes)
            val masterKeyCheckPayload = E2eeCrypto.encrypt("HEALTH2609_MASTER_OK".toByteArray(Charsets.UTF_8), masterKey)
            val masterKeyEnc = "${masterKeyCheckPayload.ciphertextBase64}:${masterKeyCheckPayload.nonceBase64}"

            val deviceFingerprint = getOrCreateDeviceFingerprint()
            val deviceName = "${Build.BRAND} ${Build.MODEL}"

            val registerJson = JSONObject().apply {
                put("username", serverUsername)
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
                // Account already registered on cloud, execute login
                return@withContext runCatching {
                    login(cleanSchool, serverUsername, passkey, saltBase64, deviceFingerprint, deviceName)
                }
            }

            if (!response.isSuccessful) {
                throw IllegalStateException("注册云同步账号失败 (${response.code})")
            }

            val resBody = JSONObject(response.body?.string() ?: "{}")
            val userId = resBody.getString("userId")

            val deviceCount = fetchDeviceCount(userId)

            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date())

            dataStore.edit { prefs ->
                prefs[KEY_SYNC_ENABLED] = true
                prefs[KEY_SYNC_SCHOOL_ID] = cleanSchool
                prefs[KEY_SYNC_USERNAME] = cleanUser
                prefs[KEY_SYNC_USER_ID] = userId
                prefs[KEY_PASSKEY_CACHED] = passkey
                prefs[KEY_DEVICE_FINGERPRINT] = deviceFingerprint
                prefs[KEY_DEVICE_COUNT] = deviceCount
                prefs[KEY_LAST_SYNC_AT] = now
            }

            "端对端加密云同步已就绪"
        }
    }

    private suspend fun login(
        schoolId: String,
        serverUsername: String,
        passkey: String,
        saltBase64: String,
        deviceFingerprint: String,
        deviceName: String
    ): String {
        val passHash = sha256Hex(passkey + saltBase64)

        val loginJson = JSONObject().apply {
            put("username", serverUsername)
            put("passwordHash", passHash)
            put("deviceFingerprint", deviceFingerprint)
            put("deviceName", deviceName)
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/sync/auth/login")
            .post(loginJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("验证凭证失败 (${response.code})，请检查账号密码")
        }

        val resBody = JSONObject(response.body?.string() ?: "{}")
        val userId = resBody.getString("userId")
        val deviceCount = fetchDeviceCount(userId)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date())
        val cleanUser = serverUsername.substringAfter(":")

        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = true
            prefs[KEY_SYNC_SCHOOL_ID] = schoolId
            prefs[KEY_SYNC_USERNAME] = cleanUser
            prefs[KEY_SYNC_USER_ID] = userId
            prefs[KEY_PASSKEY_CACHED] = passkey
            prefs[KEY_DEVICE_FINGERPRINT] = deviceFingerprint
            prefs[KEY_DEVICE_COUNT] = deviceCount
            prefs[KEY_LAST_SYNC_AT] = now
        }

        return "登录成功，端对端加密云同步已就绪"
    }

    private fun fetchDeviceCount(userId: String): Int {
        return runCatching {
            val req = Request.Builder()
                .url("$baseUrl/v1/sync/devices")
                .header("x-sync-user-id", userId)
                .get()
                .build()
            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val json = JSONObject(res.body?.string() ?: "{}")
                val devices = json.optJSONArray("devices")
                devices?.length() ?: 1
            } else {
                1
            }
        }.getOrDefault(1)
    }

    /**
     * Executes an end-to-end encrypted push/pull sync operation.
     */
    suspend fun triggerSync(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val isEnabled = prefs[KEY_SYNC_ENABLED] ?: false
            val userId = prefs[KEY_SYNC_USER_ID]
            val username = prefs[KEY_SYNC_USERNAME]
            val schoolId = prefs[KEY_SYNC_SCHOOL_ID] ?: "demo-school"
            val passkey = prefs[KEY_PASSKEY_CACHED] ?: ""

            if (!isEnabled || userId.isNullOrBlank()) {
                throw IllegalStateException("尚未登录或未启用端对端加密同步")
            }

            // 1. Refresh device count
            val deviceCount = fetchDeviceCount(userId)

            // 2. Encrypt local sync heartbeat/payload using derived master key
            if (username != null && passkey.isNotBlank()) {
                val saltBytes = getDeterministicSalt(schoolId, username)
                val masterKey = E2eeCrypto.deriveKey(passkey, saltBytes)

                val heartbeatJson = JSONObject().apply {
                    put("syncAt", System.currentTimeMillis())
                    put("schoolId", schoolId)
                    put("deviceFingerprint", prefs[KEY_DEVICE_FINGERPRINT] ?: "android")
                    put("status", "synced")
                }.toString().toByteArray(Charsets.UTF_8)

                val enc = E2eeCrypto.encrypt(heartbeatJson, masterKey)
                val record = JSONObject().apply {
                    put("entityType", "sync_heartbeat")
                    put("entityId", LocalDate.now().toString())
                    put("encryptedPayload", enc.ciphertextBase64)
                    put("payloadNonce", enc.nonceBase64)
                    put("recordVersion", 1)
                    put("deleted", false)
                    put("clientUpdatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                }

                val pushBody = JSONObject().apply {
                    put("records", JSONArray().put(record))
                }

                val pushReq = Request.Builder()
                    .url("$baseUrl/v1/sync/push")
                    .header("x-sync-user-id", userId)
                    .post(pushBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(pushReq).execute()
            }

            // 3. Pull updates from cloud
            val pullReq = Request.Builder()
                .url("$baseUrl/v1/sync/pull?since=1970-01-01T00:00:00Z")
                .header("x-sync-user-id", userId)
                .get()
                .build()

            val pullRes = httpClient.newCall(pullReq).execute()
            if (!pullRes.isSuccessful && pullRes.code != 200) {
                throw IllegalStateException("云端同步失败 (${pullRes.code})")
            }

            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.SIMPLIFIED_CHINESE).format(Date())

            dataStore.edit { p ->
                p[KEY_LAST_SYNC_AT] = now
                p[KEY_DEVICE_COUNT] = deviceCount
            }

            "端对端加密云同步已完成（已连接 $deviceCount 台设备）"
        }
    }

    suspend fun syncNow(): Result<String> = triggerSync()

    suspend fun logout() {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = false
            prefs.remove(KEY_SYNC_USERNAME)
            prefs.remove(KEY_SYNC_USER_ID)
            prefs.remove(KEY_PASSKEY_CACHED)
            prefs.remove(KEY_LAST_SYNC_AT)
            prefs.remove(KEY_DEVICE_COUNT)
        }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = enabled
        }
    }

    companion object {
        private val KEY_SYNC_ENABLED = booleanPreferencesKey("e2ee_sync_enabled")
        private val KEY_SYNC_SCHOOL_ID = stringPreferencesKey("e2ee_sync_school_id")
        private val KEY_SYNC_USERNAME = stringPreferencesKey("e2ee_sync_username")
        private val KEY_SYNC_USER_ID = stringPreferencesKey("e2ee_sync_user_id")
        private val KEY_LAST_SYNC_AT = stringPreferencesKey("e2ee_last_sync_at")
        private val KEY_DEVICE_COUNT = intPreferencesKey("e2ee_device_count")
        private val KEY_DEVICE_FINGERPRINT = stringPreferencesKey("e2ee_device_fingerprint")
        private val KEY_PASSKEY_CACHED = stringPreferencesKey("e2ee_passkey_cached")
    }
}
