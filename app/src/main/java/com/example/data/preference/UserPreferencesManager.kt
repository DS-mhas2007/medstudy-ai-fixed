package com.example.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medstudy_preferences")

const val DEFAULT_AI_MODEL = "gemini-3.5-flash"

data class UserSettings(
    /** Decrypted API key, only ever held in memory. */
    val apiKey: String = "",
    val aiProvider: String = "gemini",
    val aiModel: String = DEFAULT_AI_MODEL,
    val appLanguage: String = "ar", // "ar" or "en"
    val explanationLanguage: String = "ar", // "ar", "en", "mixed"
    val isDarkMode: Boolean = true,
    /**
     * The student's explicit permission to send the text they select to Google Gemini.
     * Defaults to false: nothing leaves the device until this is switched on.
     */
    val hasAcceptedAiConsent: Boolean = false,
    val dailyCardGoal: Int = 20
)

class UserPreferencesManager(private val context: Context) {
    private object PreferencesKeys {
        val API_KEY = stringPreferencesKey("api_key")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val EXPLANATION_LANGUAGE = stringPreferencesKey("explanation_language")
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val HAS_ACCEPTED_CONSENT = booleanPreferencesKey("has_accepted_consent")
        val DAILY_CARD_GOAL = intPreferencesKey("daily_card_goal")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserSettings(
                apiKey = SecureKeyStore.decrypt(preferences[PreferencesKeys.API_KEY] ?: ""),
                aiProvider = preferences[PreferencesKeys.AI_PROVIDER] ?: "gemini",
                aiModel = preferences[PreferencesKeys.AI_MODEL] ?: DEFAULT_AI_MODEL,
                appLanguage = preferences[PreferencesKeys.APP_LANGUAGE] ?: "ar",
                explanationLanguage = preferences[PreferencesKeys.EXPLANATION_LANGUAGE] ?: "ar",
                isDarkMode = preferences[PreferencesKeys.IS_DARK_MODE] ?: true,
                hasAcceptedAiConsent = preferences[PreferencesKeys.HAS_ACCEPTED_CONSENT] ?: false,
                dailyCardGoal = (preferences[PreferencesKeys.DAILY_CARD_GOAL] ?: 20).coerceIn(5, 200)
            )
        }

    suspend fun updateApiKey(apiKey: String) {
        val encrypted = SecureKeyStore.encrypt(apiKey.trim())
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEY] = encrypted
        }
    }

    suspend fun updateAiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AI_MODEL] = model.trim().ifBlank { DEFAULT_AI_MODEL }
        }
    }

    suspend fun updateAppLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = lang
        }
    }

    suspend fun updateExplanationLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXPLANATION_LANGUAGE] = lang
        }
    }

    suspend fun updateDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_DARK_MODE] = isDark
        }
    }

    suspend fun updateConsent(accepted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_ACCEPTED_CONSENT] = accepted
        }
    }

    suspend fun updateDailyCardGoal(goal: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_CARD_GOAL] = goal.coerceIn(5, 200)
        }
    }
}
