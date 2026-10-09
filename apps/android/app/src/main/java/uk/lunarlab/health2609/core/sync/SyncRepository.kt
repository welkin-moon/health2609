package uk.lunarlab.health2609.core.sync

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey
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
import uk.lunarlab.health2609.core.network.ApiFactory

private val Context.syncDataStoreV2 by preferencesDataStore(name = "health2609_sync_store")

data class SyncState(
    val isEnabled: Boolean = false,
    val schoolId: String? = null,
    val username: String? = null,
    val userId: String? = null,
    val deviceId: String? = null,
    val lastSyncTimestamp: String? = null,
    val deviceCount: Int = 0,
    val deviceFingerprint: String = "unknown",
    val currentKeyEpoch: Int = 0,
    val rotationRequired: Boolean = false,
    val keyRefreshRequired: Boolean = false
)

data class SyncAuthResult(
    val message: String,
    val recoveryPhrase: String? = null
)

data class SyncDevice(
    val id: String,
    val name: String,
    val lastSeenAt: String,
    val revokedAt: String?,
    val current: Boolean
)

data class PrivateSyncRecord(
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val revision: Long,
    val clientUpdatedAt: String,
    val deleted: Boolean
)

class SyncRepository(private val context: Context) {
    private val dataStore = context.applicationContext.syncDataStoreV2
    private val httpClient = OkHttpClient.Builder().build()
    private val localBox = DeviceKeyStoreBox()

    private val baseUrl: String
        get() = ApiFactory.currentBaseUrl.trimEnd('/')

    val syncState: Flow<SyncState> = dataStore.data.map { prefs ->
        SyncState(
            isEnabled = prefs[KEY_SYNC_ENABLED] ?: false,
            schoolId = prefs[KEY_SYNC_SCHOOL_ID],
            username = prefs[KEY_SYNC_USERNAME],
            userId = prefs[KEY_SYNC_USER_ID],
            deviceId = prefs[KEY_DEVICE_ID],
            lastSyncTimestamp = prefs[KEY_LAST_SYNC_AT],
            deviceCount = prefs[KEY_DEVICE_COUNT] ?: 0,
            deviceFingerprint = prefs[KEY_DEVICE_FINGERPRINT] ?: "unknown",
            currentKeyEpoch = prefs[KEY_CURRENT_KEY_EPOCH] ?: 0,
            rotationRequired = prefs[KEY_ROTATION_REQUIRED] ?: false,
            keyRefreshRequired = prefs[KEY_KEY_REFRESH_REQUIRED] ?: false
        )
    }

    private data class Challenge(
        val passwordSalt: ByteArray,
        val recoverySalt: ByteArray,
        val currentKeyEpoch: Int
    )

    private fun serverUsername(schoolId: String, username: String): String {
        val cleanUser = username.trim().lowercase()
        val cleanSchool = schoolId.trim().ifBlank { "demo-school" }
        return if (cleanUser.contains(":")) cleanUser else "$cleanSchool:$cleanUser"
    }

    private fun localUsername(serverUsername: String): String =
        serverUsername.substringAfter(":")

    private fun deviceName(): String =
        listOf(Build.BRAND, Build.MODEL).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "Android device" }

    private suspend fun ensureDeviceFingerprint(): String {
        dataStore.data.first()[KEY_DEVICE_FINGERPRINT]?.let { return it }
        val generated = "android-${UUID.randomUUID()}"
        dataStore.edit { prefs ->
            if (prefs[KEY_DEVICE_FINGERPRINT] == null) prefs[KEY_DEVICE_FINGERPRINT] = generated
        }
        return dataStore.data.first()[KEY_DEVICE_FINGERPRINT] ?: generated
    }

    private fun jsonBody(json: JSONObject) =
        json.toString().toRequestBody("application/json".toMediaType())

    private fun parseResponseBody(raw: String?): JSONObject =
        runCatching { JSONObject(raw ?: "{}") }.getOrElse { JSONObject() }

    private fun serverError(code: Int, body: JSONObject, fallback: String): IllegalStateException {
        val reason = body.optString("error").ifBlank { fallback }
        return IllegalStateException("$reason (HTTP $code)")
    }

    private suspend fun challenge(username: String): Challenge? = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(username, "UTF-8")
        val request = Request.Builder()
            .url("$baseUrl/v1/sync/auth/challenge?username=$encoded")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext null
            val body = parseResponseBody(response.body?.string())
            if (!response.isSuccessful) throw serverError(response.code, body, "challenge_failed")
            Challenge(
                passwordSalt = E2eeCrypto.unbase64(body.getString("passwordSalt")),
                recoverySalt = E2eeCrypto.unbase64(body.getString("recoverySalt")),
                currentKeyEpoch = body.getInt("currentKeyEpoch")
            )
        }
    }

    private fun tokenBox(token: String): String {
        val sealed = localBox.seal(token.toByteArray(Charsets.UTF_8), TOKEN_AAD)
        return JSONObject()
            .put("ciphertext", sealed.ciphertextBase64)
            .put("nonce", sealed.nonceBase64)
            .toString()
    }

    private fun openToken(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            String(
                localBox.open(
                    EncryptedPayload(
                        json.getString("ciphertext"),
                        json.getString("nonce")
                    ),
                    TOKEN_AAD
                ),
                Charsets.UTF_8
            )
        }.getOrNull()
    }

    private fun sealEpochKeys(existing: JSONObject, keys: Map<Int, SecretKey>): String {
        for ((epoch, key) in keys) {
            val aad = "health2609|local-epoch-key|v1|$epoch"
            val sealed = localBox.seal(key.encoded, aad)
            existing.put(
                epoch.toString(),
                JSONObject()
                    .put("ciphertext", sealed.ciphertextBase64)
                    .put("nonce", sealed.nonceBase64)
            )
        }
        return existing.toString()
    }

    private fun openEpochKeys(raw: String?): MutableMap<Int, SecretKey> {
        val out = linkedMapOf<Int, SecretKey>()
        if (raw.isNullOrBlank()) return out
        val json = runCatching { JSONObject(raw) }.getOrElse { return out }
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val name = iterator.next()
            val epoch = name.toIntOrNull() ?: continue
            val item = json.optJSONObject(name) ?: continue
            val aad = "health2609|local-epoch-key|v1|$epoch"
            runCatching {
                val bytes = localBox.open(
                    EncryptedPayload(
                        item.getString("ciphertext"),
                        item.getString("nonce")
                    ),
                    aad
                )
                out[epoch] = javax.crypto.spec.SecretKeySpec(bytes, "AES")
            }
        }
        return out
    }

    private suspend fun persistSession(
        schoolId: String,
        accountName: String,
        userId: String,
        deviceId: String,
        token: String,
        passwordSalt: ByteArray,
        recoverySalt: ByteArray,
        currentKeyEpoch: Int,
        epochKeys: Map<Int, SecretKey>,
        fingerprint: String
    ) {
        dataStore.edit { prefs ->
            val existingKeys = runCatching {
                JSONObject(prefs[KEY_EPOCH_KEYS_JSON] ?: "{}")
            }.getOrElse { JSONObject() }
            prefs[KEY_SYNC_ENABLED] = true
            prefs[KEY_SYNC_SCHOOL_ID] = schoolId
            prefs[KEY_SYNC_USERNAME] = localUsername(accountName)
            prefs[KEY_SERVER_USERNAME] = accountName
            prefs[KEY_SYNC_USER_ID] = userId
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_TOKEN_BOX] = tokenBox(token)
            prefs[KEY_PASSWORD_SALT] = E2eeCrypto.base64(passwordSalt)
            prefs[KEY_RECOVERY_SALT] = E2eeCrypto.base64(recoverySalt)
            prefs[KEY_CURRENT_KEY_EPOCH] = currentKeyEpoch
            prefs[KEY_DEVICE_FINGERPRINT] = fingerprint
            prefs[KEY_EPOCH_KEYS_JSON] = sealEpochKeys(existingKeys, epochKeys)
            prefs[KEY_ROTATION_REQUIRED] = false
            prefs[KEY_KEY_REFRESH_REQUIRED] = false
        }
    }

    suspend fun registerOrLogin(
        schoolId: String = "demo-school",
        username: String,
        passkey: String,
        recoveryPhrase: String = ""
    ): Result<SyncAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(username.isNotBlank()) { "请输入学号或用户名" }
            require(passkey.length >= 8) { "密码至少需要 8 个字符" }

            val cleanSchool = schoolId.trim().ifBlank { "demo-school" }
            val accountName = serverUsername(cleanSchool, username)
            val fingerprint = ensureDeviceFingerprint()
            val known = challenge(accountName)
            if (known == null) {
                register(cleanSchool, accountName, passkey, fingerprint)
            } else {
                login(
                    cleanSchool,
                    accountName,
                    passkey,
                    recoveryPhrase,
                    fingerprint,
                    known
                )
            }
        }
    }

    private suspend fun register(
        schoolId: String,
        accountName: String,
        passkey: String,
        fingerprint: String
    ): SyncAuthResult {
        val passwordSalt = E2eeCrypto.generateSalt()
        val recoverySalt = E2eeCrypto.generateSalt()
        val recoveryPhrase = E2eeCrypto.generateRecoveryPhrase()
        val normalizedRecovery = E2eeCrypto.normalizeRecoveryPhrase(recoveryPhrase)
        val accountKey = E2eeCrypto.generateAccountKey()

        val passwordWrapped = E2eeCrypto.encrypt(
            accountKey.encoded,
            E2eeCrypto.deriveWrappingKey(passkey, passwordSalt, "password"),
            E2eeCrypto.buildKeyAad(accountName, 1, "password")
        )
        val recoveryWrapped = E2eeCrypto.encrypt(
            accountKey.encoded,
            E2eeCrypto.deriveWrappingKey(normalizedRecovery, recoverySalt, "recovery"),
            E2eeCrypto.buildKeyAad(accountName, 1, "recovery")
        )

        val payload = JSONObject()
            .put("username", accountName)
            .put("passwordSalt", E2eeCrypto.base64(passwordSalt))
            .put("passwordVerifier", E2eeCrypto.deriveAuthVerifier(passkey, passwordSalt, "password"))
            .put("recoverySalt", E2eeCrypto.base64(recoverySalt))
            .put(
                "recoveryVerifier",
                E2eeCrypto.deriveAuthVerifier(normalizedRecovery, recoverySalt, "recovery")
            )
            .put("deviceFingerprint", fingerprint)
            .put("deviceName", deviceName())
            .put(
                "keyEnvelope",
                JSONObject()
                    .put("epoch", 1)
                    .put("passwordWrappedKey", passwordWrapped.ciphertextBase64)
                    .put("passwordNonce", passwordWrapped.nonceBase64)
                    .put("recoveryWrappedKey", recoveryWrapped.ciphertextBase64)
                    .put("recoveryNonce", recoveryWrapped.nonceBase64)
            )

        val request = Request.Builder()
            .url("$baseUrl/v1/sync/auth/register")
            .post(jsonBody(payload))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 409) {
                val known = challenge(accountName)
                    ?: throw IllegalStateException("账号状态发生变化，请重试")
                return login(schoolId, accountName, passkey, recoveryPhrase, fingerprint, known)
            }
            val body = parseResponseBody(response.body?.string())
            if (!response.isSuccessful) throw serverError(response.code, body, "register_failed")

            persistSession(
                schoolId,
                accountName,
                body.getString("userId"),
                body.getString("deviceId"),
                body.getString("token"),
                passwordSalt,
                recoverySalt,
                1,
                mapOf(1 to accountKey),
                fingerprint
            )
        }

        return SyncAuthResult(
            message = "端对端加密同步已创建。请立即保存恢复短语；服务器无法替你找回。",
            recoveryPhrase = recoveryPhrase
        )
    }

    private suspend fun login(
        schoolId: String,
        accountName: String,
        passkey: String,
        recoveryPhrase: String,
        fingerprint: String,
        challenge: Challenge
    ): SyncAuthResult {
        val payload = JSONObject()
            .put("username", accountName)
            .put(
                "passwordVerifier",
                E2eeCrypto.deriveAuthVerifier(passkey, challenge.passwordSalt, "password")
            )
            .put("deviceFingerprint", fingerprint)
            .put("deviceName", deviceName())

        if (recoveryPhrase.isNotBlank()) {
            val normalized = E2eeCrypto.normalizeRecoveryPhrase(recoveryPhrase)
            payload.put(
                "recoveryVerifier",
                E2eeCrypto.deriveAuthVerifier(normalized, challenge.recoverySalt, "recovery")
            )
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/sync/auth/login")
            .post(jsonBody(payload))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = parseResponseBody(response.body?.string())
            if (response.code == 403 &&
                body.optString("error") == "device_enrollment_requires_recovery"
            ) {
                throw IllegalStateException("这是新设备或已被吊销的设备，需要恢复短语才能加入账号")
            }
            if (!response.isSuccessful) throw serverError(response.code, body, "login_failed")

            val wrappingKey = E2eeCrypto.deriveWrappingKey(
                passkey,
                challenge.passwordSalt,
                "password"
            )
            val epochKeys = linkedMapOf<Int, SecretKey>()
            val envelopes = body.optJSONArray("keyEnvelopes") ?: JSONArray()
            for (i in 0 until envelopes.length()) {
                val envelope = envelopes.getJSONObject(i)
                val epoch = envelope.getInt("epoch")
                val raw = E2eeCrypto.decrypt(
                    envelope.getString("wrappedKey"),
                    envelope.getString("nonce"),
                    wrappingKey,
                    E2eeCrypto.buildKeyAad(accountName, epoch, "password")
                )
                epochKeys[epoch] = javax.crypto.spec.SecretKeySpec(raw, "AES")
            }
            require(epochKeys.isNotEmpty()) { "账号密钥包为空，无法开启同步" }

            persistSession(
                schoolId,
                accountName,
                body.getString("userId"),
                body.getString("deviceId"),
                body.getString("token"),
                challenge.passwordSalt,
                challenge.recoverySalt,
                body.getInt("currentKeyEpoch"),
                epochKeys,
                fingerprint
            )
        }

        return SyncAuthResult("登录成功，账号密钥仅保存在受系统密钥库保护的本机存储中")
    }

    suspend fun enqueuePrivateRecord(
        entityType: String,
        entityId: String,
        payloadJson: String,
        deleted: Boolean = false
    ) {
        require(entityType.matches(Regex("[a-z0-9_\\\\-]{1,64}"))) { "invalid entityType" }
        require(entityId.length in 1..160) { "invalid entityId" }
        val fingerprint = ensureDeviceFingerprint()
        dataStore.edit { prefs ->
            val root = runCatching {
                JSONObject(prefs[KEY_LOCAL_RECORDS_JSON] ?: "{}")
            }.getOrElse { JSONObject() }
            val recordKey = localRecordKey(entityType, entityId)
            val previous = root.optJSONObject(recordKey)
            val revision = (previous?.optLong("revision", 0L) ?: 0L) + 1L
            val updatedAt = Instant.now().toString()
            val sealed = localBox.seal(
                payloadJson.toByteArray(Charsets.UTF_8),
                localPayloadAad(entityType, entityId, revision, updatedAt)
            )
            root.put(
                recordKey,
                JSONObject()
                    .put("entityType", entityType)
                    .put("entityId", entityId)
                    .put("localCiphertext", sealed.ciphertextBase64)
                    .put("localNonce", sealed.nonceBase64)
                    .put("revision", revision)
                    .put("clientUpdatedAt", updatedAt)
                    .put("deleted", deleted)
                    .put("dirty", true)
                    .put("sourceDeviceId", prefs[KEY_DEVICE_ID] ?: "local:$fingerprint")
            )
            prefs[KEY_LOCAL_RECORDS_JSON] = root.toString()
        }
    }

    suspend fun getPrivateRecord(entityType: String, entityId: String): PrivateSyncRecord? {
        val root = JSONObject(dataStore.data.first()[KEY_LOCAL_RECORDS_JSON] ?: "{}")
        val item = root.optJSONObject(localRecordKey(entityType, entityId)) ?: return null
        return decodeLocalRecord(item)
    }

    suspend fun listPrivateRecords(entityType: String? = null): List<PrivateSyncRecord> {
        val root = JSONObject(dataStore.data.first()[KEY_LOCAL_RECORDS_JSON] ?: "{}")
        val out = mutableListOf<PrivateSyncRecord>()
        val iterator = root.keys()
        while (iterator.hasNext()) {
            val item = root.optJSONObject(iterator.next()) ?: continue
            if (entityType != null && item.optString("entityType") != entityType) continue
            runCatching { decodeLocalRecord(item) }.getOrNull()?.let(out::add)
        }
        return out
    }

    private fun decodeLocalRecord(item: JSONObject): PrivateSyncRecord {
        val entityType = item.getString("entityType")
        val entityId = item.getString("entityId")
        val revision = item.getLong("revision")
        val updatedAt = item.getString("clientUpdatedAt")
        val plaintext = localBox.open(
            EncryptedPayload(
                item.getString("localCiphertext"),
                item.getString("localNonce")
            ),
            localPayloadAad(entityType, entityId, revision, updatedAt)
        )
        return PrivateSyncRecord(
            entityType,
            entityId,
            String(plaintext, Charsets.UTF_8),
            revision,
            updatedAt,
            item.optBoolean("deleted", false)
        )
    }

    private fun localRecordKey(entityType: String, entityId: String) =
        "$entityType\\u001F$entityId"

    private fun localPayloadAad(
        entityType: String,
        entityId: String,
        revision: Long,
        updatedAt: String
    ) = "health2609|local-record|v1|$entityType|$entityId|$revision|$updatedAt"

    private fun authenticatedRequestBuilder(
        prefs: androidx.datastore.preferences.core.Preferences
    ): Request.Builder {
        val token = openToken(prefs[KEY_TOKEN_BOX])
            ?: throw IllegalStateException("本机同步令牌不可用，请重新登录")
        return Request.Builder().header("Authorization", "Bearer $token")
    }

    suspend fun triggerSync(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val initialPrefs = dataStore.data.first()
            if (!(initialPrefs[KEY_SYNC_ENABLED] ?: false)) {
                throw IllegalStateException("尚未登录或未启用端对端加密同步")
            }

            val currentEpoch = initialPrefs[KEY_CURRENT_KEY_EPOCH] ?: 0
            val epochKeys = openEpochKeys(initialPrefs[KEY_EPOCH_KEYS_JSON])
            val currentKey = epochKeys[currentEpoch]
                ?: throw IllegalStateException("缺少当前账号密钥，请重新输入密码刷新密钥")
            val localRoot = JSONObject(initialPrefs[KEY_LOCAL_RECORDS_JSON] ?: "{}")
            val dirtyItems = mutableListOf<Pair<String, JSONObject>>()
            val iterator = localRoot.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val item = localRoot.optJSONObject(key) ?: continue
                if (item.optBoolean("dirty", false)) dirtyItems += key to item
            }

            if (dirtyItems.isNotEmpty()) {
                val records = JSONArray()
                for ((_, item) in dirtyItems) {
                    val local = decodeLocalRecord(item)
                    val aad = E2eeCrypto.buildRecordAad(
                        local.entityType,
                        local.entityId,
                        1,
                        currentEpoch,
                        local.revision,
                        local.clientUpdatedAt
                    )
                    val encrypted = E2eeCrypto.encrypt(
                        local.payloadJson.toByteArray(Charsets.UTF_8),
                        currentKey,
                        aad
                    )
                    records.put(
                        JSONObject()
                            .put("entityType", local.entityType)
                            .put("entityId", local.entityId)
                            .put("ciphertext", encrypted.ciphertextBase64)
                            .put("nonce", encrypted.nonceBase64)
                            .put("aad", aad)
                            .put("envelopeVersion", 1)
                            .put("keyEpoch", currentEpoch)
                            .put("revision", local.revision)
                            .put("deleted", local.deleted)
                            .put("clientUpdatedAt", local.clientUpdatedAt)
                    )
                }

                val request = authenticatedRequestBuilder(initialPrefs)
                    .url("$baseUrl/v1/sync/push")
                    .post(jsonBody(JSONObject().put("records", records)))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = parseResponseBody(response.body?.string())
                    if (response.code == 409 && body.optString("error") == "stale_key_epoch") {
                        dataStore.edit { it[KEY_KEY_REFRESH_REQUIRED] = true }
                        throw IllegalStateException("账号密钥已轮换，请重新登录以刷新密钥")
                    }
                    if (!response.isSuccessful) throw serverError(response.code, body, "sync_push_failed")
                }

                dataStore.edit { prefs ->
                    val root = JSONObject(prefs[KEY_LOCAL_RECORDS_JSON] ?: "{}")
                    for ((key, pushed) in dirtyItems) {
                        val current = root.optJSONObject(key) ?: continue
                        if (current.optLong("revision") == pushed.optLong("revision") &&
                            current.optString("clientUpdatedAt") == pushed.optString("clientUpdatedAt")
                        ) current.put("dirty", false)
                    }
                    prefs[KEY_LOCAL_RECORDS_JSON] = root.toString()
                }
            }

            var cursor = initialPrefs[KEY_PULL_CURSOR] ?: 0L
            var hasMore: Boolean
            do {
                val prefs = dataStore.data.first()
                val request = authenticatedRequestBuilder(prefs)
                    .url("$baseUrl/v1/sync/pull?cursor=$cursor&limit=200")
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = parseResponseBody(response.body?.string())
                    if (!response.isSuccessful) throw serverError(response.code, body, "sync_pull_failed")
                    applyPulledRecords(body.optJSONArray("records") ?: JSONArray())
                    cursor = body.optLong("nextCursor", cursor)
                    hasMore = body.optBoolean("hasMore", false)
                    val serverEpoch = body.optInt("currentKeyEpoch", currentEpoch)
                    dataStore.edit { p ->
                        p[KEY_PULL_CURSOR] = cursor
                        if (serverEpoch > (p[KEY_CURRENT_KEY_EPOCH] ?: 0)) {
                            p[KEY_KEY_REFRESH_REQUIRED] = true
                        }
                    }
                }
            } while (hasMore)

            val devices = fetchDevicesInternal()
            val activeCount = devices.count { it.revokedAt == null }
            dataStore.edit { prefs ->
                prefs[KEY_LAST_SYNC_AT] = Instant.now().toString()
                prefs[KEY_DEVICE_COUNT] = activeCount
            }

            "端对端加密同步已完成（$activeCount 台已授权设备）"
        }
    }

    suspend fun syncNow(): Result<String> = triggerSync()

    private suspend fun applyPulledRecords(records: JSONArray) {
        if (records.length() == 0) return
        dataStore.edit { prefs ->
            val root = JSONObject(prefs[KEY_LOCAL_RECORDS_JSON] ?: "{}")
            val epochKeys = openEpochKeys(prefs[KEY_EPOCH_KEYS_JSON])
            for (i in 0 until records.length()) {
                val remote = records.getJSONObject(i)
                val type = remote.getString("entityType")
                val id = remote.getString("entityId")
                val revision = remote.getLong("revision")
                val updatedAt = remote.getString("clientUpdatedAt")
                val epoch = remote.getInt("keyEpoch")
                val envelopeVersion = remote.getInt("envelopeVersion")
                val expectedAad = E2eeCrypto.buildRecordAad(
                    type, id, envelopeVersion, epoch, revision, updatedAt
                )
                val aad = remote.getString("aad")
                if (aad != expectedAad) throw IllegalStateException("同步记录元数据校验失败")
                val key = epochKeys[epoch]
                if (key == null) {
                    prefs[KEY_KEY_REFRESH_REQUIRED] = true
                    continue
                }
                val plaintext = E2eeCrypto.decrypt(
                    remote.getString("ciphertext"),
                    remote.getString("nonce"),
                    key,
                    aad
                )
                val recordKey = localRecordKey(type, id)
                val local = root.optJSONObject(recordKey)
                val remoteSource = remote.optString("sourceDeviceId")
                val localRevision = local?.optLong("revision", 0L) ?: 0L
                val localUpdatedAt = local?.optString("clientUpdatedAt") ?: ""
                val localSource = local?.optString("sourceDeviceId") ?: ""
                val remoteWins = local == null ||
                    revision > localRevision ||
                    (revision == localRevision && updatedAt > localUpdatedAt) ||
                    (revision == localRevision && updatedAt == localUpdatedAt && remoteSource > localSource)

                if (remoteWins) {
                    val sealed = localBox.seal(
                        plaintext,
                        localPayloadAad(type, id, revision, updatedAt)
                    )
                    root.put(
                        recordKey,
                        JSONObject()
                            .put("entityType", type)
                            .put("entityId", id)
                            .put("localCiphertext", sealed.ciphertextBase64)
                            .put("localNonce", sealed.nonceBase64)
                            .put("revision", revision)
                            .put("clientUpdatedAt", updatedAt)
                            .put("deleted", remote.optBoolean("deleted", false))
                            .put("dirty", false)
                            .put("sourceDeviceId", remoteSource)
                    )
                }
            }
            prefs[KEY_LOCAL_RECORDS_JSON] = root.toString()
        }
    }

    suspend fun devices(): Result<List<SyncDevice>> = withContext(Dispatchers.IO) {
        runCatching { fetchDevicesInternal() }
    }

    private suspend fun fetchDevicesInternal(): List<SyncDevice> {
        val prefs = dataStore.data.first()
        val request = authenticatedRequestBuilder(prefs)
            .url("$baseUrl/v1/sync/devices")
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val body = parseResponseBody(response.body?.string())
            if (!response.isSuccessful) throw serverError(response.code, body, "devices_failed")
            val array = body.optJSONArray("devices") ?: JSONArray()
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SyncDevice(
                    id = item.getString("id"),
                    name = item.getString("deviceName"),
                    lastSeenAt = item.optString("lastSeenAt"),
                    revokedAt = item.optString("revokedAt").takeIf { it.isNotBlank() && it != "null" },
                    current = item.optBoolean("current", false)
                )
            }
        }
    }

    suspend fun revokeDevice(deviceId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = dataStore.data.first()
            val request = authenticatedRequestBuilder(prefs)
                .url("$baseUrl/v1/sync/devices/$deviceId/revoke")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = parseResponseBody(response.body?.string())
                if (!response.isSuccessful) throw serverError(response.code, body, "revoke_failed")
                if (body.optBoolean("rotationRequired", false)) {
                    dataStore.edit { it[KEY_ROTATION_REQUIRED] = true }
                }
            }
            "设备已吊销。为阻止其解密未来记录，请立即轮换账号加密密钥。"
        }
    }

    suspend fun rotateAccountKey(
        passkey: String,
        recoveryPhrase: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(passkey.length >= 8) { "请输入当前密码" }
            require(recoveryPhrase.isNotBlank()) { "轮换密钥需要恢复短语" }
            val prefs = dataStore.data.first()
            val accountName = prefs[KEY_SERVER_USERNAME]
                ?: throw IllegalStateException("账号信息缺失，请重新登录")
            val passwordSalt = E2eeCrypto.unbase64(
                prefs[KEY_PASSWORD_SALT] ?: throw IllegalStateException("密码盐缺失")
            )
            val recoverySalt = E2eeCrypto.unbase64(
                prefs[KEY_RECOVERY_SALT] ?: throw IllegalStateException("恢复盐缺失")
            )
            val currentEpoch = prefs[KEY_CURRENT_KEY_EPOCH] ?: 0
            val newEpoch = currentEpoch + 1
            val newKey = E2eeCrypto.generateAccountKey()
            val normalizedRecovery = E2eeCrypto.normalizeRecoveryPhrase(recoveryPhrase)

            val passwordWrapped = E2eeCrypto.encrypt(
                newKey.encoded,
                E2eeCrypto.deriveWrappingKey(passkey, passwordSalt, "password"),
                E2eeCrypto.buildKeyAad(accountName, newEpoch, "password")
            )
            val recoveryWrapped = E2eeCrypto.encrypt(
                newKey.encoded,
                E2eeCrypto.deriveWrappingKey(normalizedRecovery, recoverySalt, "recovery"),
                E2eeCrypto.buildKeyAad(accountName, newEpoch, "recovery")
            )
            val payload = JSONObject()
                .put("epoch", newEpoch)
                .put("newEpoch", newEpoch)
                .put("passwordWrappedKey", passwordWrapped.ciphertextBase64)
                .put("passwordNonce", passwordWrapped.nonceBase64)
                .put("recoveryWrappedKey", recoveryWrapped.ciphertextBase64)
                .put("recoveryNonce", recoveryWrapped.nonceBase64)
                .put(
                    "passwordVerifier",
                    E2eeCrypto.deriveAuthVerifier(passkey, passwordSalt, "password")
                )
                .put(
                    "recoveryVerifier",
                    E2eeCrypto.deriveAuthVerifier(normalizedRecovery, recoverySalt, "recovery")
                )

            val request = authenticatedRequestBuilder(prefs)
                .url("$baseUrl/v1/sync/keys/rotate")
                .post(jsonBody(payload))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = parseResponseBody(response.body?.string())
                if (!response.isSuccessful) throw serverError(response.code, body, "key_rotation_failed")
            }

            dataStore.edit { p ->
                val existing = JSONObject(p[KEY_EPOCH_KEYS_JSON] ?: "{}")
                p[KEY_EPOCH_KEYS_JSON] = sealEpochKeys(existing, mapOf(newEpoch to newKey))
                p[KEY_CURRENT_KEY_EPOCH] = newEpoch
                p[KEY_ROTATION_REQUIRED] = false
                p[KEY_KEY_REFRESH_REQUIRED] = false
            }
            "账号加密密钥已轮换到 epoch $newEpoch；被吊销设备无法获取未来密文。"
        }
    }

    suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(currentPassword.length >= 8) { "请输入当前密码" }
            require(newPassword.length >= 8) { "新密码至少需要 8 个字符" }
            val prefs = dataStore.data.first()
            val epochKeys = openEpochKeys(prefs[KEY_EPOCH_KEYS_JSON])
            require(epochKeys.isNotEmpty()) { "本机没有可用账号密钥" }
            val newSalt = E2eeCrypto.generateSalt()
            val wrappingKey = E2eeCrypto.deriveWrappingKey(newPassword, newSalt, "password")
            val accountName = prefs[KEY_SERVER_USERNAME]
                ?: throw IllegalStateException("账号信息缺失")
            val envelopes = JSONArray()
            for ((epoch, key) in epochKeys) {
                val wrapped = E2eeCrypto.encrypt(
                    key.encoded,
                    wrappingKey,
                    E2eeCrypto.buildKeyAad(accountName, epoch, "password")
                )
                envelopes.put(
                    JSONObject()
                        .put("epoch", epoch)
                        .put("passwordWrappedKey", wrapped.ciphertextBase64)
                        .put("passwordNonce", wrapped.nonceBase64)
                )
            }
            val oldSalt = E2eeCrypto.unbase64(
                prefs[KEY_PASSWORD_SALT] ?: throw IllegalStateException("当前密码盐缺失")
            )
            val payload = JSONObject()
                .put(
                    "currentPasswordVerifier",
                    E2eeCrypto.deriveAuthVerifier(currentPassword, oldSalt, "password")
                )
                .put("passwordSalt", E2eeCrypto.base64(newSalt))
                .put("passwordVerifier", E2eeCrypto.deriveAuthVerifier(newPassword, newSalt, "password"))
                .put("keyEnvelopes", envelopes)
            val request = authenticatedRequestBuilder(prefs)
                .url("$baseUrl/v1/sync/auth/change-password")
                .post(jsonBody(payload))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = parseResponseBody(response.body?.string())
                if (!response.isSuccessful) throw serverError(response.code, body, "password_change_failed")
            }
            dataStore.edit { it[KEY_PASSWORD_SALT] = E2eeCrypto.base64(newSalt) }
            "密码已更新，全部历史密钥 epoch 仍可解密。"
        }
    }

    suspend fun recoverAndResetPassword(
        schoolId: String,
        username: String,
        recoveryPhrase: String,
        newPassword: String
    ): Result<SyncAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(recoveryPhrase.isNotBlank()) { "请输入恢复短语" }
            require(newPassword.length >= 8) { "新密码至少需要 8 个字符" }
            val cleanSchool = schoolId.trim().ifBlank { "demo-school" }
            val accountName = serverUsername(cleanSchool, username)
            val known = challenge(accountName) ?: throw IllegalStateException("账号不存在")
            val normalizedRecovery = E2eeCrypto.normalizeRecoveryPhrase(recoveryPhrase)
            val recoveryVerifier = E2eeCrypto.deriveAuthVerifier(
                normalizedRecovery,
                known.recoverySalt,
                "recovery"
            )

            val recoveryRequest = Request.Builder()
                .url("$baseUrl/v1/sync/auth/recovery")
                .post(
                    jsonBody(
                        JSONObject()
                            .put("username", accountName)
                            .put("recoveryVerifier", recoveryVerifier)
                    )
                )
                .build()

            val recoveredKeys = linkedMapOf<Int, SecretKey>()
            httpClient.newCall(recoveryRequest).execute().use { response ->
                val body = parseResponseBody(response.body?.string())
                if (!response.isSuccessful) throw serverError(response.code, body, "recovery_failed")
                val wrappingKey = E2eeCrypto.deriveWrappingKey(
                    normalizedRecovery,
                    known.recoverySalt,
                    "recovery"
                )
                val envelopes = body.optJSONArray("keyEnvelopes") ?: JSONArray()
                for (i in 0 until envelopes.length()) {
                    val envelope = envelopes.getJSONObject(i)
                    val epoch = envelope.getInt("epoch")
                    val raw = E2eeCrypto.decrypt(
                        envelope.getString("wrappedKey"),
                        envelope.getString("nonce"),
                        wrappingKey,
                        E2eeCrypto.buildKeyAad(accountName, epoch, "recovery")
                    )
                    recoveredKeys[epoch] = javax.crypto.spec.SecretKeySpec(raw, "AES")
                }
            }
            require(recoveredKeys.isNotEmpty()) { "恢复密钥包为空" }

            val newSalt = E2eeCrypto.generateSalt()
            val newWrap = E2eeCrypto.deriveWrappingKey(newPassword, newSalt, "password")
            val passwordEnvelopes = JSONArray()
            for ((epoch, key) in recoveredKeys) {
                val wrapped = E2eeCrypto.encrypt(
                    key.encoded,
                    newWrap,
                    E2eeCrypto.buildKeyAad(accountName, epoch, "password")
                )
                passwordEnvelopes.put(
                    JSONObject()
                        .put("epoch", epoch)
                        .put("passwordWrappedKey", wrapped.ciphertextBase64)
                        .put("passwordNonce", wrapped.nonceBase64)
                )
            }

            val resetPayload = JSONObject()
                .put("username", accountName)
                .put("recoveryVerifier", recoveryVerifier)
                .put("passwordSalt", E2eeCrypto.base64(newSalt))
                .put("passwordVerifier", E2eeCrypto.deriveAuthVerifier(newPassword, newSalt, "password"))
                .put("keyEnvelopes", passwordEnvelopes)
            val resetRequest = Request.Builder()
                .url("$baseUrl/v1/sync/auth/reset-password")
                .post(jsonBody(resetPayload))
                .build()
            httpClient.newCall(resetRequest).execute().use { response ->
                val body = parseResponseBody(response.body?.string())
                if (!response.isSuccessful) throw serverError(response.code, body, "password_reset_failed")
            }

            login(
                cleanSchool,
                accountName,
                newPassword,
                recoveryPhrase,
                ensureDeviceFingerprint(),
                Challenge(newSalt, known.recoverySalt, known.currentKeyEpoch)
            )
        }
    }

    suspend fun logout(removeLocalKeyMaterial: Boolean = false) {
        dataStore.edit { prefs ->
            prefs[KEY_SYNC_ENABLED] = false
            prefs.remove(KEY_SYNC_USER_ID)
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_TOKEN_BOX)
            prefs.remove(KEY_LAST_SYNC_AT)
            prefs.remove(KEY_DEVICE_COUNT)
            prefs.remove(KEY_PULL_CURSOR)
            prefs[KEY_ROTATION_REQUIRED] = false
            prefs[KEY_KEY_REFRESH_REQUIRED] = false
            if (removeLocalKeyMaterial) {
                prefs.remove(KEY_SERVER_USERNAME)
                prefs.remove(KEY_PASSWORD_SALT)
                prefs.remove(KEY_RECOVERY_SALT)
                prefs.remove(KEY_EPOCH_KEYS_JSON)
                prefs[KEY_CURRENT_KEY_EPOCH] = 0
            }
        }
    }

    suspend fun setSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SYNC_ENABLED] = enabled }
    }

    suspend fun removeLegacyPassword() {
        dataStore.edit { it.remove(KEY_LEGACY_PASSKEY_CACHED) }
    }

    companion object {
        private const val TOKEN_AAD = "health2609|local-token|v1"

        private val KEY_SYNC_ENABLED = booleanPreferencesKey("e2ee_sync_enabled")
        private val KEY_SYNC_SCHOOL_ID = stringPreferencesKey("e2ee_sync_school_id")
        private val KEY_SYNC_USERNAME = stringPreferencesKey("e2ee_sync_username")
        private val KEY_SERVER_USERNAME = stringPreferencesKey("e2ee_server_username")
        private val KEY_SYNC_USER_ID = stringPreferencesKey("e2ee_sync_user_id")
        private val KEY_DEVICE_ID = stringPreferencesKey("e2ee_device_id")
        private val KEY_LAST_SYNC_AT = stringPreferencesKey("e2ee_last_sync_at")
        private val KEY_DEVICE_COUNT = intPreferencesKey("e2ee_device_count")
        private val KEY_DEVICE_FINGERPRINT = stringPreferencesKey("e2ee_device_fingerprint")
        private val KEY_TOKEN_BOX = stringPreferencesKey("e2ee_token_box")
        private val KEY_PASSWORD_SALT = stringPreferencesKey("e2ee_password_salt")
        private val KEY_RECOVERY_SALT = stringPreferencesKey("e2ee_recovery_salt")
        private val KEY_CURRENT_KEY_EPOCH = intPreferencesKey("e2ee_current_key_epoch")
        private val KEY_EPOCH_KEYS_JSON = stringPreferencesKey("e2ee_epoch_keys_json")
        private val KEY_LOCAL_RECORDS_JSON = stringPreferencesKey("e2ee_local_records_json")
        private val KEY_PULL_CURSOR = longPreferencesKey("e2ee_pull_cursor")
        private val KEY_ROTATION_REQUIRED = booleanPreferencesKey("e2ee_rotation_required")
        private val KEY_KEY_REFRESH_REQUIRED = booleanPreferencesKey("e2ee_key_refresh_required")
        private val KEY_LEGACY_PASSKEY_CACHED = stringPreferencesKey("e2ee_passkey_cached")
    }
}
