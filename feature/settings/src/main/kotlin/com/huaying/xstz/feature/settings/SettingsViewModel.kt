package com.huaying.xstz.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huaying.xstz.core.domain.repository.BackupRepository
import com.huaying.xstz.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: Int = SettingsRepository.THEME_AUTO,
    val privacyMode: Boolean = false,
    val reduceTransparency: Boolean = false,
    val thresholdPct: Double = SettingsRepository.DEFAULT_THRESHOLD,
    val lastBackup: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null,
    /** 导出结果，非空时弹出可复制的对话框 */
    val exportedPayload: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val exported = MutableStateFlow<String?>(null)
    private val lastBackup = MutableStateFlow(0L)

    init {
        viewModelScope.launch { lastBackup.value = backup.lastBackupTime() }
    }

    val state: StateFlow<SettingsUiState> = combine(
        settings.observeThemeMode(),
        settings.observePrivacyMode(),
        settings.observeReducedTransparency(),
        settings.observeThreshold(),
    ) { theme, privacy, transparency, threshold ->
        SettingsUiState(
            themeMode = theme,
            privacyMode = privacy,
            reduceTransparency = transparency,
            thresholdPct = threshold,
        )
    }.combine(busy) { s, b -> s.copy(busy = b) }
        .combine(message) { s, m -> s.copy(message = m) }
        .combine(exported) { s, e -> s.copy(exportedPayload = e) }
        .combine(lastBackup) { s, t -> s.copy(lastBackup = t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setPrivacy(enabled: Boolean) {
        viewModelScope.launch { settings.setPrivacyMode(enabled) }
    }

    fun setReduceTransparency(enabled: Boolean) {
        viewModelScope.launch { settings.setReducedTransparency(enabled) }
    }

    /**
     * 导出备份。
     *
     * 口令强度先在本层校验，避免用户设一个 3 位口令就把整个资产库加密出去。
     * 这里不保存口令 —— 丢了就真的解不开，界面上必须明确说明这一点。
     */
    fun export(passphrase: String) {
        passphraseHint(passphrase)?.let {
            message.value = it
            return
        }
        viewModelScope.launch {
            busy.value = true
            runCatching { backup.export(passphrase.toCharArray()) }
                .onSuccess {
                    exported.value = it
                    lastBackup.value = System.currentTimeMillis()
                    message.value = "备份已生成，请立即复制保存"
                }
                .onFailure { message.value = "导出失败：${it.message}" }
            busy.value = false
        }
    }

    fun import(payload: String, passphrase: String) {
        if (payload.isBlank()) {
            message.value = "请先粘贴备份内容"
            return
        }
        passphraseHint(passphrase)?.let {
            message.value = it
            return
        }
        viewModelScope.launch {
            busy.value = true
            runCatching { backup.import(payload.trim(), passphrase.toCharArray()) }
                .onSuccess { r ->
                    message.value = "恢复成功：${r.holdings} 只持仓、${r.trades} 条流水"
                }
                .onFailure { message.value = "恢复失败：${it.message}" }
            busy.value = false
        }
    }

    fun dismissExport() {
        exported.value = null
    }

    fun dismissMessage() {
        message.value = null
    }

    /**
     * 口令强度提示。只拦"太短"和"纯数字"两种，
     * 不做"必须含特殊字符"这类过时规则（它们的边际安全性远低于长度）。
     */
    private fun passphraseHint(passphrase: String): String? = when {
        passphrase.length < 8 -> "口令至少 8 位"
        passphrase.all { it.isDigit() } -> "纯数字口令太弱，建议加入字母"
        else -> null
    }
}
