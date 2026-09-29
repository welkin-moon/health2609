package uk.lunarlab.health2609.core.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.health2609DataStore by preferencesDataStore(
    name = "health2609_preferences"
)

class Health2609Preferences(context: Context) {
    private val dataStore = context.applicationContext.health2609DataStore

    val startDestination: Flow<String> = dataStore.data.map { preferences ->
        when (val saved = preferences[START_DESTINATION]) {
            "today", "meals", "activity" -> saved
            else -> "today"
        }
    }

    suspend fun setStartDestination(destination: String) {
        if (destination !in ALLOWED_DESTINATIONS) return
        dataStore.edit { preferences ->
            preferences[START_DESTINATION] = destination
        }
    }

    private companion object {
        val START_DESTINATION = stringPreferencesKey("start_destination")
        val ALLOWED_DESTINATIONS = setOf("today", "meals", "activity")
    }
}
