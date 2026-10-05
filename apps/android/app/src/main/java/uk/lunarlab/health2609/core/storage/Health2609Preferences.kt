package uk.lunarlab.health2609.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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

data class UserProfile(
    val age: Int = 14,
    val gender: String = "neutral", // "male", "female", "neutral"
    val heightCm: Double = 165.0,
    val weightKg: Double = 55.0
) {
    val bmi: Double
        get() {
            val hM = heightCm / 100.0
            return if (hM > 0.5) {
                kotlin.math.round((weightKg / (hM * hM)) * 10.0) / 10.0
            } else 20.0
        }

    val bmr: Double
        get() {
            val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
            val result = when (gender.lowercase()) {
                "male" -> base + 5.0
                "female" -> base - 161.0
                else -> base - 78.0 // (5.0 + -161.0) / 2.0 = -78.0 (average of male and female)
            }
            return kotlin.math.round(result * 10.0) / 10.0
        }

    val recommendedEnergyKcal: Int
        get() {
            val tdee = bmr * 1.35
            return kotlin.math.round(tdee).toInt().coerceIn(1200, 3800)
        }

    val bmrStatusText: String
        get() {
            val genderText = when (gender.lowercase()) {
                "male" -> "男生"
                "female" -> "女生"
                else -> "通用"
            }
            return if (age < 18) "成长阶段不自动估算能量需求" else "基础代谢约 ${bmr.toInt()} 千卡/天（$genderText）"
        }

    val bmiStatusText: String
        get() = "BMI $bmi · 仅展示计算值，不作体重评价"
}

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

    val userProfile: Flow<UserProfile> = dataStore.data.map { preferences ->
        UserProfile(
            age = preferences[USER_AGE] ?: 14,
            gender = preferences[USER_GENDER] ?: "neutral",
            heightCm = preferences[USER_HEIGHT_CM] ?: 165.0,
            weightKg = preferences[USER_WEIGHT_KG] ?: 55.0
        )
    }

    suspend fun setUserProfile(profile: UserProfile) {
        dataStore.edit { preferences ->
            preferences[USER_AGE] = profile.age.coerceIn(6, 25)
            preferences[USER_GENDER] = profile.gender
            preferences[USER_HEIGHT_CM] = profile.heightCm.coerceIn(80.0, 230.0)
            preferences[USER_WEIGHT_KG] = profile.weightKg.coerceIn(20.0, 200.0)
        }
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

    val customApiBaseUrl: Flow<String?> = dataStore.data.map { preferences ->
        preferences[CUSTOM_API_BASE_URL]
    }

    suspend fun setCustomApiBaseUrl(url: String?) {
        dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(CUSTOM_API_BASE_URL)
            } else {
                preferences[CUSTOM_API_BASE_URL] = url.trim()
            }
        }
    }

    private companion object {
        val START_DESTINATION = stringPreferencesKey("start_destination")
        val SELECTED_SCHOOL_ID = stringPreferencesKey("selected_school_id")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CUSTOM_API_BASE_URL = stringPreferencesKey("custom_api_base_url")
        val USER_AGE = intPreferencesKey("user_age")
        val USER_GENDER = stringPreferencesKey("user_gender")
        val USER_HEIGHT_CM = doublePreferencesKey("user_height_cm")
        val USER_WEIGHT_KG = doublePreferencesKey("user_weight_kg")
        val ALLOWED_DESTINATIONS = setOf("today", "meals", "activity")
    }
}

