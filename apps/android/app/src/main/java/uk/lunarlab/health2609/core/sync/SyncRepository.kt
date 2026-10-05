package uk.lunarlab.health2609.core.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.syncDataStore by preferencesDataStore(name = "health2609_sync_store")

data class SyncState(
    val isEnabled: Boolean = false,
    val schoolId: String? = null,
    val username: String? = null,
    val userId: String? = null,
    val lastSyncTimestamp: String? = null,
    val deviceCount: Int = 0,
    val deviceFingerprint: String = "unknown"
)

/** Preserve account metadata for migration; a heartbeat never counts as record backup. */
class SyncRepository(context: Context) {
    private val dataStore = context.applicationContext.syncDataStore
    val syncState: Flow<SyncState> = dataStore.data.map { prefs ->
        SyncState(username = prefs[stringPreferencesKey("e2ee_sync_username")])
    }

    suspend fun removeLegacyPassword() {
        dataStore.edit { it.remove(stringPreferencesKey("e2ee_passkey_cached")) }
    }

    suspend fun triggerSync(): Result<String> = Result.failure(
        IllegalStateException("跨设备同步尚未开放，个人记录还没有加密备份。")
    )
    suspend fun syncNow(): Result<String> = triggerSync()

    suspend fun logout() { dataStore.edit { it.clear() } }
}
