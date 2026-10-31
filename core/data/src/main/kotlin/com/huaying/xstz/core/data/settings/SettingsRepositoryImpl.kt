package com.huaying.xstz.core.data.settings

import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huaying.xstz.core.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
        val INITIAL_INVEST_DAY = longPreferencesKey("initial_invest_day")
        val LAST_SYNC = longPreferencesKey("last_sync")
        val REFRESH_INTERVAL = longPreferencesKey("refresh_interval_ms")
        val BACKGROUND_THROTTLE = booleanPreferencesKey("background_throttle")
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

    override fun observeReducedMotion(): Flow<Boolean> = callbackFlow {
        val resolver = context.contentResolver

        fun read(): Boolean {
            // 开发者选项「动画程序时长缩放」。API 34 起标记废弃但系统仍写入，保留读取。
            @Suppress("DEPRECATION")
            val globalOff = Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            // 无障碍「移除动画」：写到 Secure 的同名 key（无公开常量，用字符串）
            val secureOff = Settings.Secure.getFloat(
                resolver,
                ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
            return globalOff || secureOff
        }

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(read())
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        trySend(read())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    override fun observeHighContrast(): Flow<Boolean> = callbackFlow {
        val resolver = context.contentResolver

        fun read(): Boolean {
            // 系统「高对比度文字」：Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST
            // （无公开常量，用字符串；值为 "1" 表示开启）。API 29+ 可用，本项目 minSdk 34。
            return Settings.Secure.getInt(resolver, HIGH_TEXT_CONTRAST, 0) == 1
        }

        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(read())
            }
        }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(HIGH_TEXT_CONTRAST),
            false,
            observer,
        )
        trySend(read())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    override fun observeOnboardingDone(): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    override suspend fun isOnboardingDone(): Boolean =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }.first()

    override suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    override fun observeInitialInvestDay(): Flow<Long> =
        context.dataStore.data.map { it[Keys.INITIAL_INVEST_DAY] ?: 0L }

    override suspend fun setInitialInvestDay(epochDay: Long) {
        context.dataStore.edit { it[Keys.INITIAL_INVEST_DAY] = epochDay }
    }

    override fun observeLastSync(): Flow<Long> =
        context.dataStore.data.map { it[Keys.LAST_SYNC] ?: 0L }

    override suspend fun setLastSync(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC] = timestamp }
    }

    override fun observeRefreshIntervalMs(): Flow<Long> =
        context.dataStore.data.map { it[Keys.REFRESH_INTERVAL] ?: SettingsRepository.DEFAULT_REFRESH_INTERVAL }

    override suspend fun setRefreshIntervalMs(ms: Long) {
        // 只允许两档之一落库：用户拨错了也不至于把间隔存成 1ms 或 0 导致死循环
        val clamped = when (ms) {
            SettingsRepository.REFRESH_INTERVAL_FAST -> SettingsRepository.REFRESH_INTERVAL_FAST
            else -> SettingsRepository.REFRESH_INTERVAL_NORMAL
        }
        context.dataStore.edit { it[Keys.REFRESH_INTERVAL] = clamped }
    }

    override fun observeBackgroundThrottle(): Flow<Boolean> =
        // 缺省 true：出厂就省电，用户主动追求实时才关。
        // 反过来会让多数用户在不知情的情况下承担后台耗电。
        context.dataStore.data.map { it[Keys.BACKGROUND_THROTTLE] ?: true }

    override suspend fun setBackgroundThrottle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKGROUND_THROTTLE] = enabled }
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

        /**
         * 无障碍「移除动画」在 Settings.Secure 里的 key（无公开常量，写字符串）。
         * API 34 起与开发者选项的 Global 同名项并存，两者任一为 0 即视为减弱动效。
         */
        private const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"

        /**
         * 系统「高对比度文字」在 Settings.Secure 里的 key（无公开常量，写字符串）。
         * 值为 "1" 表示开启。
         */
        private const val HIGH_TEXT_CONTRAST = "high_text_contrast"
    }
}
