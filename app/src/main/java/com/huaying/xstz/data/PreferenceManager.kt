package com.huaying.xstz.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension to create DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {

    companion object {
        private val THEME_MODE_KEY = intPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val REBALANCE_THRESHOLD_KEY = floatPreferencesKey("rebalance_threshold")
        // Deprecated: old minute-based frequency
        // private val REFRESH_FREQUENCY_KEY = intPreferencesKey("refresh_frequency")
        private val REFRESH_INTERVAL_SECONDS_KEY = intPreferencesKey("refresh_interval_seconds")
        private val REFRESH_MODE_KEY = intPreferencesKey("refresh_mode") // 0: Smart, 1: Fixed
        private val REBALANCE_THRESHOLD_MODE_KEY = intPreferencesKey("rebalance_threshold_mode") // 0: Percentage, 1: Percentage Points
        private val PRIVACY_MODE_KEY = booleanPreferencesKey("privacy_mode")
        private val SHOW_PRINCIPAL_KEY = booleanPreferencesKey("show_principal")
        private val INITIAL_INVESTMENT_DATE_KEY = longPreferencesKey("initial_investment_date")
    }

    // Privacy mode setting
    val privacyModeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PRIVACY_MODE_KEY] ?: false
        }

    suspend fun setPrivacyModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PRIVACY_MODE_KEY] = enabled
        }
    }

    // Theme mode: 0 = Auto, 1 = Light, 2 = Dark
    val themeMode: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_MODE_KEY] ?: 0
        }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode
        }
    }

    // Dynamic color setting
    val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLOR_KEY] ?: true
        }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    // Rebalance threshold setting (percentage)
    val rebalanceThreshold: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[REBALANCE_THRESHOLD_KEY] ?: 20.0f
        }

    suspend fun setRebalanceThreshold(threshold: Float) {
        context.dataStore.edit { preferences ->
            preferences[REBALANCE_THRESHOLD_KEY] = threshold
        }
    }

    // Refresh interval setting (seconds)
    val refreshIntervalSeconds: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[REFRESH_INTERVAL_SECONDS_KEY] ?: 3
        }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_INTERVAL_SECONDS_KEY] = seconds
        }
    }

    // Refresh mode: 0 = Smart (Trading hours only), 1 = Fixed (Always)
    val refreshMode: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[REFRESH_MODE_KEY] ?: 0
        }

    suspend fun setRefreshMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[REFRESH_MODE_KEY] = mode
        }
    }

    // Rebalance threshold mode: 0 = Percentage (Relative), 1 = Percentage Points (Absolute)
    val rebalanceThresholdMode: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[REBALANCE_THRESHOLD_MODE_KEY] ?: 0
        }

    suspend fun setRebalanceThresholdMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[REBALANCE_THRESHOLD_MODE_KEY] = mode
        }
    }

    // Show principal setting
    val showPrincipal: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SHOW_PRINCIPAL_KEY] ?: true
        }

    suspend fun setShowPrincipal(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_PRINCIPAL_KEY] = show
        }
    }

    // Initial investment date setting
    val initialInvestmentDate: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[INITIAL_INVESTMENT_DATE_KEY] ?: 0
        }

    suspend fun setInitialInvestmentDate(date: Long) {
        context.dataStore.edit { preferences ->
            preferences[INITIAL_INVESTMENT_DATE_KEY] = date
        }
    }
}
