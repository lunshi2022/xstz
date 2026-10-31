package com.huaying.xstz.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.glass.GlassCapability
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.domain.repository.SettingsRepository
import com.huaying.xstz.core.ui.SolidCard
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeChange: (Int) -> Unit,
    onPrivacyChange: (Boolean) -> Unit,
    onReduceTransparency: (Boolean) -> Unit,
    onExport: (String) -> Unit,
    onImport: (String, String) -> Unit,
    onDismissExport: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenOperationLog: () -> Unit = {},
    onRefreshIntervalChange: (Long) -> Unit = {},
    onBackgroundThrottleChange: (Boolean) -> Unit = {},
    onInitialInvestDayChange: (Long) -> Unit = {},
    onSeedSamples: () -> Unit = {},
    onClearOperationLog: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onClearAllHoldings: () -> Unit = {},
    /**
     * 版本展示串，由 app 模块从 BuildConfig 计算后传入。
     *
     * 为什么不由本模块自己拼：feature 模块拿不到 app 的 BuildConfig，
     * 一旦在本地写死字面量，gradle 里升版本后关于页会继续显示旧号——
     * 这类漂移没人会在发版时想起来查，所以宁可多传一个参数。
     */
    versionText: String,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    val investedDays =
        if (state.initialInvestDay > 0)
            (java.time.LocalDate.now().toEpochDay() - state.initialInvestDay).coerceAtLeast(0)
        else 0L

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeTitleHeader(title = "设置") }

        // ---------- 外观 ----------
        item { SectionHeader(title = "外观") }
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Column {
                    Text("主题", style = XstzType.caption, color = palette.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            SettingsRepository.THEME_AUTO to "跟随系统",
                            SettingsRepository.THEME_LIGHT to "浅色",
                            SettingsRepository.THEME_DARK to "深色",
                        ).forEach { (mode, label) ->
                            val selected = state.themeMode == mode
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) palette.accent.copy(alpha = 0.18f) else palette.divider,
                                        SquircleShape(10.dp, 2.5f),
                                    )
                                    .clickable { onThemeChange(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = XstzType.caption,
                                    color = if (selected) palette.accent else palette.textSecondary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HairlineDivider()
                    Spacer(Modifier.height(12.dp))

                    ToggleRow(
                        title = "隐藏金额",
                        subtitle = "隐私模式下用 **** 代替数字，适合截图分享",
                        checked = state.privacyMode,
                        onCheckedChange = onPrivacyChange,
                    )
                    Spacer(Modifier.height(10.dp))
                    ToggleRow(
                        title = "减弱透明度",
                        subtitle = "关闭实时模糊，改用高不透明度填充（无障碍选项）",
                        checked = state.reduceTransparency,
                        onCheckedChange = onReduceTransparency,
                    )
                }
            }
        }

        // ---------- 数据 ----------
        item { SectionHeader(title = "数据备份", subtitle = "口令派生密钥，换机也能恢复") }
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Column {
                    Text(
                        "备份文件用你自己的口令加密（Argon2id + AES-GCM），" +
                            "不依赖本机密钥，因此换手机后仍可恢复。口令丢失则无法解开。",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionChip("导出备份", palette.accent, Modifier.weight(1f)) { showExport = true }
                        ActionChip("恢复备份", palette.textSecondary, Modifier.weight(1f)) { showImport = true }
                    }
                    if (state.lastBackup > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "上次备份：${Formats.dateTime(state.lastBackup)}",
                            style = XstzType.caption2,
                            color = palette.textTertiary,
                        )
                    }
                }
            }
        }

        // ---------- 行情刷新 ----------
        item { SectionHeader(title = "行情刷新", subtitle = "仅交易时段、应用前台时自动刷新") }
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Column {
                    Text("刷新频率", style = XstzType.caption, color = palette.textTertiary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            SettingsRepository.REFRESH_INTERVAL_FAST to "流畅（3 秒）",
                            SettingsRepository.REFRESH_INTERVAL_NORMAL to "省电（5 秒）",
                        ).forEach { (ms, label) ->
                            val selected = state.refreshIntervalMs == ms
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) palette.accent.copy(alpha = 0.18f) else palette.divider,
                                        SquircleShape(10.dp, 2.5f),
                                    )
                                    .clickable { onRefreshIntervalChange(ms) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = XstzType.caption,
                                    color = if (selected) palette.accent else palette.textSecondary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        title = "后台降频",
                        subtitle = "应用退到后台后放慢刷新以省电；关闭则用前台频率，更实时但更耗电",
                        checked = state.backgroundThrottle,
                        onCheckedChange = onBackgroundThrottleChange,
                    )
                }
            }
        }

        // ---------- 投资基准 ----------
        // 初始投资日期是年化收益的分母。没有它，界面只能给绝对收益，
        // 用户无法判断"赚 6 千"到底是三个月还是三年赚的。
        item { SectionHeader(title = "投资基准", subtitle = "用于计算年化收益与投资时长") }
        item {
            SolidCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("初始投资日期", style = XstzType.body, color = palette.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (state.initialInvestDay > 0)
                            "${Formats.date(state.initialInvestDay)} · 已投资 $investedDays 天"
                        else "未设置",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
            }
        }

        // ---------- 数据管理 ----------
        item { SectionHeader(title = "数据管理", subtitle = "示例数据与本机诊断记录") }
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSeedSamples),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("重建示例数据", style = XstzType.body, color = palette.accent)
                        Spacer(Modifier.weight(1f))
                        Text("重新灌入示例持仓", style = XstzType.caption, color = palette.textTertiary)
                    }
                    HairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClearOperationLog),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("清空操作日志", style = XstzType.body, color = palette.accent)
                        Spacer(Modifier.weight(1f))
                        Text("不影响任何资产数据", style = XstzType.caption, color = palette.textTertiary)
                    }
                    HairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExportCsv),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("导出 CSV", style = XstzType.body, color = palette.accent)
                        Spacer(Modifier.weight(1f))
                        Text("明文，可直接用表格打开", style = XstzType.caption, color = palette.textTertiary)
                    }
                    HairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearConfirm = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 用涨色（红）而非 accent：这是不可撤销的破坏性操作，
                        // 配色上必须和普通操作区分开，避免误当成"又一个导出按钮"
                        Text("清空全部持仓", style = XstzType.body, color = palette.up)
                        Spacer(Modifier.weight(1f))
                        Text("不可撤销，需二次确认", style = XstzType.caption, color = palette.textTertiary)
                    }
                }
            }
        }

        // ---------- 数据 ----------
        item { SectionHeader(title = "操作记录") }
        item {
            SolidCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenOperationLog() },
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "操作日志",
                        style = XstzType.body,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "查看本机数据变更",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
            }
        }

        // ---------- 关于 ----------
        item { SectionHeader(title = "关于") }
        item {
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 14.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    /*
                     * Alpha 标注由调用方（app 模块）拼进 versionText：
                     * 当前是重构后的首个公开形态，功能面完整但仍在真机打磨，
                     * 必须让用户在关于页直接看到，而不是等他遇到问题才发现。
                     */
                    InfoRow("版本", versionText)
                    InfoRow("包名", "com.huaying.xstz")
                    InfoRow("开发者", "lunshi2022")
                    InfoRow("渲染", GlassCapability.describe())
                    InfoRow("崩溃诊断", "零第三方 SDK · 本地日志 · 可导出")
                    HairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangelog = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("更新说明", style = XstzType.body, color = palette.accent)
                        Spacer(Modifier.weight(1f))
                        Text("查看版本变更", style = XstzType.caption, color = palette.textTertiary)
                    }
                }
            }
        }

        if (state.message != null) {
            item {
                Text(
                    state.message,
                    style = XstzType.caption,
                    color = palette.accent,
                    modifier = Modifier.clickable(onClick = onDismissMessage),
                )
            }
        }
    }

    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onConfirm = { pw ->
                showExport = false
                onExport(pw)
            },
        )
    }

    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onConfirm = { payload, pw ->
                showImport = false
                onImport(payload, pw)
            },
        )
    }

    state.exportedPayload?.let { payload ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = onDismissExport,
            containerColor = XstzTheme.palette.backgroundElevated,
            title = {
                Text(
                    if (state.exportedIsCsv) "CSV 已生成" else "备份已生成",
                    style = XstzType.title3,
                    color = XstzTheme.palette.textPrimary,
                )
            },
            text = {
                Column {
                    Text(
                        if (state.exportedIsCsv)
                            "共 ${payload.length} 字符，UTF-8 带 BOM，Excel 可直接打开。这是明文文件，请注意存放位置。"
                        else
                            "共 ${payload.length} 字符。请复制后保存到安全的地方，不要把口令与备份放在一起。",
                        style = XstzType.caption,
                        color = XstzTheme.palette.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        payload.take(180) + "…",
                        style = XstzType.caption2,
                        color = XstzTheme.palette.textTertiary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(payload))
                    onDismissExport()
                }) { Text("复制", color = XstzTheme.palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = onDismissExport) {
                    Text("关闭", color = XstzTheme.palette.textTertiary)
                }
            },
        )
    }

    if (showChangelog) {
        AlertDialog(
            onDismissRequest = { showChangelog = false },
            containerColor = XstzTheme.palette.backgroundElevated,
            title = { Text("更新说明", style = XstzType.title3, color = XstzTheme.palette.textPrimary) },
            text = {
                Text(
                    "当前为 2.0.0 重构版（Alpha）。\n\n" +
                        "旧版 v1.6.5 已停止维护，数据不直接沿用；" +
                        "历史记录请在新版中通过此前导出的备份恢复。\n\n" +
                        "本次重构：模块化架构、资产配置与再平衡、" +
                        "本地加密存储、离线样例显式标注。",
                    style = XstzType.caption,
                    color = XstzTheme.palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showChangelog = false }) {
                    Text("知道了", color = XstzTheme.palette.accent)
                }
            },
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = XstzTheme.palette.backgroundElevated,
            title = {
                Text("清空全部持仓？", style = XstzType.title3, color = XstzTheme.palette.textPrimary)
            },
            text = {
                Text(
                    "将删除所有持仓及其交易流水，此操作不可撤销。\n若没有可用备份，数据无法找回。",
                    style = XstzType.caption,
                    color = XstzTheme.palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearAllHoldings()
                    showClearConfirm = false
                }) { Text("确认清空", color = XstzTheme.palette.up) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消", color = XstzTheme.palette.textTertiary)
                }
            },
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = if (state.initialInvestDay > 0) {
                java.time.LocalDate.ofEpochDay(state.initialInvestDay)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } else null,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker 给的是 UTC 毫秒；转回本地日期的 epochDay 再存，
                    // 与仓储层"存 epochDay 而非时间戳"的约定保持一致
                    dateState.selectedDateMillis?.let { millis ->
                        val day = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .toEpochDay()
                        onInitialInvestDayChange(day)
                    }
                    showDatePicker = false
                }) { Text("确定", color = XstzTheme.palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消", color = XstzTheme.palette.textTertiary)
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = XstzTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = XstzType.callout, color = palette.textPrimary)
            Text(subtitle, style = XstzType.caption2, color = palette.textTertiary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionChip(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .background(color.copy(alpha = 0.14f), SquircleShape(12.dp, 2.6f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = XstzType.caption, color = color)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = XstzTheme.palette
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = XstzType.caption, color = palette.textTertiary, modifier = Modifier.weight(1f))
        Text(value, style = XstzType.caption, color = palette.textSecondary)
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = XstzTheme.palette
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotEmpty() && pw != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = { Text("设置备份口令", style = XstzType.title3, color = palette.textPrimary) },
        text = {
            Column {
                Text(
                    "这个口令用来加密备份。忘记后无法恢复，请务必记牢。",
                    style = XstzType.caption,
                    color = palette.textTertiary,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("口令（至少 8 位）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("再次输入") },
                    singleLine = true,
                    isError = mismatch,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mismatch) {
                    Text("两次输入不一致", style = XstzType.caption2, color = palette.down)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pw) },
                enabled = pw.length >= 8 && pw == confirm,
            ) { Text("生成备份", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textTertiary) }
        },
    )
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    val palette = XstzTheme.palette
    var payload by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = { Text("恢复备份", style = XstzType.title3, color = palette.textPrimary) },
        text = {
            Column {
                Text(
                    "恢复会清空当前全部数据并覆盖为备份内容，此操作不可撤销。",
                    style = XstzType.caption,
                    color = palette.down,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("粘贴备份内容") },
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it },
                    label = { Text("备份口令") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(payload, pw) },
                enabled = payload.isNotBlank() && pw.length >= 8,
            ) { Text("覆盖恢复", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = palette.textTertiary) }
        },
    )
}
