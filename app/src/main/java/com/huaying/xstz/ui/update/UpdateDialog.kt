package com.huaying.xstz.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huaying.xstz.data.model.UpdateInfo

/**
 * 格式化文件大小
 */
private fun formatFileSize(size: Long): String {
    if (size <= 0) return ""
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "%.0f KB".format(kb)
}

/**
 * 格式化发布日期（ISO 8601 -> 可读格式）
 */
private fun formatDate(dateStr: String): String {
    if (dateStr.isBlank()) return ""
    return try {
        val datePart = dateStr.substringBefore("T")
        val parts = datePart.split("-")
        if (parts.size == 3) "${parts[0]}年${parts[1].toInt()}月${parts[2].toInt()}日" else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

/**
 * 发现新版本对话框
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    currentVersionName: String = "",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    text = "发现新版本：v${updateInfo.versionName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                // 副标题：文件大小 + 日期 + 版本对比
                val metaParts = mutableListOf<String>()
                if (updateInfo.fileSize > 0) metaParts.add(formatFileSize(updateInfo.fileSize))
                if (updateInfo.publishDate.isNotBlank()) metaParts.add(formatDate(updateInfo.publishDate))
                if (currentVersionName.isNotBlank()) metaParts.add("v$currentVersionName → v${updateInfo.versionName}")
                if (metaParts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metaParts.joinToString("  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 可滚动区域 + 右侧滚动条
                Row(
                    modifier = Modifier.heightIn(max = 220.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "暂无更新说明" },
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                    }

                    // 右侧滚动条
                    Spacer(modifier = Modifier.width(8.dp))
                    VerticalScrollbar(scrollState = scrollState)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("立即更新", modifier = Modifier.padding(horizontal = 8.dp))
            }
        },
        dismissButton = if (!updateInfo.forceUpdate) {
            {
                TextButton(onClick = onDismiss) {
                    Text("稍后提醒")
                }
            }
        } else null
    )
}

/**
 * 竖向滚动条组件
 */
@Composable
private fun RowScope.VerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    if (scrollState.maxValue == 0) return

    val scrollFraction by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            if (max > 0) scrollState.value.toFloat() / max else 0f
        }
    }

    val density = LocalDensity.current
    var trackHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .padding(horizontal = 0.dp, vertical = 2.dp)
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        if (trackHeightPx > 0) {
            val thumbFraction = 0.3f
            val thumbHeightPx = (trackHeightPx * thumbFraction).toInt().coerceAtLeast(density.density.toInt())
            val offsetY = scrollFraction * (trackHeightPx - thumbHeightPx)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { thumbHeightPx.toDp() })
                    .offset(y = with(density) { offsetY.toDp() })
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        }
    }
}

/**
 * 检查更新中对话框
 */
@Composable
fun CheckingUpdateDialog() {
    AlertDialog(
        onDismissRequest = { },
        shape = RoundedCornerShape(20.dp),
        title = { Text("正在检查更新...") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        },
        confirmButton = { }
    )
}

/**
 * 已是最新版本对话框
 */
@Composable
fun NoUpdateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("已是最新版本") },
        text = { Text("当前已是最新版本，无需更新。") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

/**
 * 检查更新失败对话框
 */
@Composable
fun UpdateCheckErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "检查更新失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
