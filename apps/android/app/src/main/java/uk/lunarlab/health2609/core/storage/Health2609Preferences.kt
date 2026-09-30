package uk.lunarlab.health2609.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.health2609DataStore by preferencesDataStore(
    name = "health2609_preferences"
)

enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class AppearancePreferences(
    val mode: AppearanceMode = AppearanceMode.SYSTEM,
    val dynamicColor: Boolean = true
)

class Health2609Preferences(context: Context) {
    private val dataStore = context.applicationContext.health2609DataStore

    val startDestination: Flow<String> = dataStore.data.map { preferences ->
        when (val saved = preferences[START_DESTINATION]) {
            "today", "meals", "activity" -> saved
            else -> "today"
        }
    }

    val selectedSchoolId: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_SCHOOL_ID] ?: "demo-school"
    }

    val appearance: Flow<AppearancePreferences> = dataStore.data.map { preferences ->
        val mode = when (preferences[APPEARANCE_MODE]) {
            "light" -> AppearanceMode.LIGHT
            "dark" -> AppearanceMode.DARK
            else -> AppearanceMode.SYSTEM
        }
        AppearancePreferences(
            mode = mode,
            dynamicColor = preferences[DYNAMIC_COLOR] ?: true
        )
    }

    suspend fun setStartDestination(destination: String) {
        if (destination !in ALLOWED_DESTINATIONS) return
        dataStore.edit { preferences ->
            preferences[START_DESTINATION] = destination
        }
    }

    suspend fun setSelectedSchoolId(schoolId: String) {
        val value = schoolId.trim()
        if (value.isEmpty()) return
        dataStore.edit { preferences ->
            preferences[SELECTED_SCHOOL_ID] = value
        }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        dataStore.edit { preferences ->
            preferences[APPEARANCE_MODE] = when (mode) {
                AppearanceMode.SYSTEM -> "system"
                AppearanceMode.LIGHT -> "light"
                AppearanceMode.DARK -> "dark"
            }
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR] = enabled
        }
    }

    private companion object {
        val START_DESTINATION = stringPreferencesKey("start_destination")
        val SELECTED_SCHOOL_ID = stringPreferencesKey("selected_school_id")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ALLOWED_DESTINATIONS = setOf("today", "meals", "activity")
    }
}
