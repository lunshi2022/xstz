package com.huaying.xstz.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huaying.xstz.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置存储。
 *
 * 全部用 DataStore 而不是 SharedPreferences：前者是 Flow 原生的，
 * 改设置后 UI 自动重组；后者要自己搭监听器，是旧版里"改了主题不生效"类 bug 的根源。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val context: Context,
) : SettingsRepository {

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val THRESHOLD = doublePreferencesKey("rebalance_threshold")
        val THRESHOLD_MODE = intPreferencesKey("threshold_mode")
        val REDUCED_TRANSPARENCY = booleanPreferencesKey("reduced_transparency")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_SYNC = longPreferencesKey("last_sync")
    }

    override fun observeThemeMode(): Flow<Int> =
        context.dataStore.data.map { it[Keys.THEME_MODE] ?: THEME_AUTO }

    override suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    override fun observePrivacyMode(): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.PRIVACY_MODE] ?: false }

    override suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY_MODE] = enabled }
    }

    override fun observeThreshold(): Flow<Double> =
        context.dataStore.data.map { it[Keys.THRESHOLD] ?: DEFAULT_THRESHOLD }

    override suspend fun setThreshold(value: Double) {
        context.dataStore.edit { it[Keys.THRESHOLD] = value.coerceIn(0.005, 1.0) }
    }

    override fun observeThresholdMode(): Flow<Int> =
        context.dataStore.data.map { it[Keys.THRESHOLD_MODE] ?: THRESHOLD_ABSOLUTE }

    override suspend fun setThresholdMode(mode: Int) {
        context.dataStore.edit { it[Keys.THRESHOLD_MODE] = mode }
    }

    override fun observeReducedTransparency(): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REDUCED_TRANSPARENCY] ?: false }

    override suspend fun setReducedTransparency(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REDUCED_TRANSPARENCY] = enabled }
    }

    override suspend fun isOnboardingDone(): Boolean =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }.first()

    override suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    override fun observeLastSync(): Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_SYNC] ?: 0L }

    override suspend fun setLastSync(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC] = timestamp }
    }

    companion object {
        // 常量以 domain 接口为准，这里只做转发。
        // 直接在实现类里另写一套值，迟早会出现"UI 用 1 表示浅色、实现用 2"这类错位。
        const val THEME_AUTO = SettingsRepository.THEME_AUTO
        const val THEME_LIGHT = SettingsRepository.THEME_LIGHT
        const val THEME_DARK = SettingsRepository.THEME_DARK

        const val THRESHOLD_ABSOLUTE = SettingsRepository.THRESHOLD_MODE_ABSOLUTE
        const val THRESHOLD_RELATIVE = SettingsRepository.THRESHOLD_MODE_RELATIVE

        const val DEFAULT_THRESHOLD = SettingsRepository.DEFAULT_THRESHOLD
    }
}
