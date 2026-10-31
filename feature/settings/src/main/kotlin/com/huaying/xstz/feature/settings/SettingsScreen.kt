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
import androidx.compose.material3.OutlinedTextField
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
import com.huaying.xstz.core.designsystem.glass.GlassLevel
import com.huaying.xstz.core.designsystem.glass.GlassPanel
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.domain.repository.SettingsRepository
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.LargeTitleHeader
import com.huaying.xstz.core.ui.SectionHeader
import dev.chrisbanes.haze.HazeState

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    hazeState: HazeState?,
    onThemeChange: (Int) -> Unit,
    onPrivacyChange: (Boolean) -> Unit,
    onReduceTransparency: (Boolean) -> Unit,
    onExport: (String) -> Unit,
    onImport: (String, String) -> Unit,
    onDismissExport: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LargeTitleHeader(title = "设置") }

        // ---------- 外观 ----------
        item { SectionHeader(title = "外观") }
        item {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                state = hazeState,
                cornerRadius = 20.dp,
                level = GlassLevel.Regular,
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
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                state = hazeState,
                cornerRadius = 20.dp,
                level = GlassLevel.Regular,
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

        // ---------- 关于 ----------
        item { SectionHeader(title = "关于") }
        item {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                state = hazeState,
                cornerRadius = 20.dp,
                level = GlassLevel.Thin,
                contentPadding = 14.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("版本", "2.0.0 (200)")
                    InfoRow("包名", "com.huaying.xstz")
                    InfoRow("渲染", GlassCapability.describe())
                    InfoRow("崩溃诊断", "零第三方 SDK · 本地日志 · 可导出")
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
            title = { Text("备份已生成", style = XstzType.title3, color = XstzTheme.palette.textPrimary) },
            text = {
                Column {
                    Text(
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
