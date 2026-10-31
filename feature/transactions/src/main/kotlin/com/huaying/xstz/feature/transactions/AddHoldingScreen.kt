package com.huaying.xstz.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.huaying.xstz.core.common.Formats
import com.huaying.xstz.core.designsystem.shape.SquircleShape
import com.huaying.xstz.core.designsystem.theme.XstzTheme
import com.huaying.xstz.core.designsystem.theme.XstzType
import com.huaying.xstz.core.model.InstrumentKind
import com.huaying.xstz.core.model.QuoteCandidate
import com.huaying.xstz.core.ui.SolidCard
import com.huaying.xstz.core.ui.GlassTextField
import com.huaying.xstz.core.ui.HairlineDivider
import com.huaying.xstz.core.ui.MeshBackdrop
import com.huaying.xstz.core.ui.SectionHeader
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 添加标的页。
 *
 * 这个应用管的是 **A股、ETF、债券、场外基金** 四类，不是纯基金。
 * 因此这一页要同时回答两个问题：
 *
 *   1. "这个代码是什么" —— 输 `000001`，要同时给出平安银行（A股）
 *      与华夏成长（基金），由用户点选。只查一个接口会替他做决定。
 *   2. "叫什么的东西有哪些" —— 输"茅台"或"沪深300"，要列出所有相关标的。
 *      用户记不住代码，要求他记是设计缺陷。
 *
 * 两种输入共用一个框，靠形态自动分流：纯数字满 6 位按代码查，其余按名称搜。
 * 不让用户先选"按代码 / 按名称"——那是在把实现的复杂度转嫁给用户。
 */
@Composable
fun AddHoldingScreen(
    state: AddHoldingUiState,
    onTextChange: (String) -> Unit,
    onSelect: (QuoteCandidate) -> Unit,
    onEnableManual: () -> Unit,
    onManualNameChange: (String) -> Unit,
    onManualKindChange: (InstrumentKind) -> Unit,
    onSubmit: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = XstzTheme.palette
    // 页面主体是内容层 SolidCard（红线 R3），保留 hazeSource 只为控件层的
    // 半透明输入框提供采样源
    val haze = rememberHazeState()

    // 添加成功后自动退回列表：停在一个已经提交完的表单上没有意义
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Box(modifier.fillMaxSize().imePadding()) {
        MeshBackdrop(Modifier.hazeSource(haze))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- 顶栏 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(palette.glassFill, SquircleShape(12.dp, 2.8f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("添加标的", style = XstzType.title3, color = palette.textPrimary)
                    Text(
                        "支持 A股 · ETF · 债券 · 场外基金",
                        style = XstzType.caption,
                        color = palette.textTertiary,
                    )
                }
            }

            // ---------- 搜索框 + 候选 ----------
            SolidCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                contentPadding = 18.dp,
            ) {
                Column {
                    GlassTextField(
                        value = state.text,
                        onValueChange = onTextChange,
                        label = if (state.codeMode) "代码" else "名称或代码",
                        placeholder = "茅台 / 沪深300 / 600519",
                        // 含汉字时必须给文本键盘。数字键盘打不出"茅台"——
                        // 之前固定 Number 时，用户以为这个框只能输代码
                        keyboardType = if (state.codeMode) KeyboardType.Number else KeyboardType.Text,
                        trailing = {
                            when {
                                state.querying -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.accent,
                                )

                                state.text.isNotEmpty() -> Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = palette.textTertiary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onTextChange("") },
                                )

                                else -> Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = palette.textTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        helper = if (state.phase == QueryPhase.IDLE && state.text.isEmpty()) {
                            "输入名称可联想搜索；输入 6 位代码可精确区分同号标的"
                        } else null,
                    )

                    Spacer(Modifier.height(14.dp))
                    HairlineDivider()
                    Spacer(Modifier.height(14.dp))

                    when (state.phase) {
                        QueryPhase.QUERYING -> Text(
                            if (state.codeMode) "正在同时查询场内与场外接口…" else "正在搜索…",
                            style = XstzType.caption,
                            color = palette.textTertiary,
                        )

                        QueryPhase.HIT -> Column {
                            Text(
                                if (state.candidates.size > 1)
                                    "找到 ${state.candidates.size} 个相关标的，请选择要添加的"
                                else "已找到，确认后添加",
                                style = XstzType.caption2,
                                color = palette.textTertiary,
                            )
                            Spacer(Modifier.height(8.dp))
                            state.candidates.forEach { c ->
                                // 搜索结果的 price 为 0（未查行情），此时不显示价格列，
                                // 否则每行都是 0.0000，看起来像"数据坏了"
                                CandidateRow(
                                    candidate = c,
                                    isSelected = c.key == state.selectedKey,
                                    showPrice = c.price > 0.0,
                                    onClick = { onSelect(c) },
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        QueryPhase.MISS -> Column {
                            Text(
                                if (state.codeMode) "没有查到该代码" else "没有搜到「${state.text.trim()}」",
                                style = XstzType.caption,
                                color = palette.down,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (state.codeMode) {
                                    "可能是代码有误，或该标的未被接口收录。可以手动填写名称后添加。"
                                } else {
                                    "试试更短的关键词（如「茅台」而不是「贵州茅台股份」），" +
                                        "或直接输入 6 位代码。交易所债券只能按代码添加。"
                                },
                                style = XstzType.caption2,
                                color = palette.textTertiary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .background(
                                        palette.accent.copy(alpha = 0.14f),
                                        SquircleShape(11.dp, 2.6f),
                                    )
                                    .clickable(onClick = onEnableManual)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("手动填写", style = XstzType.caption, color = palette.accent)
                            }
                        }

                        QueryPhase.ERROR -> Column {
                            Text("查询失败", style = XstzType.caption, color = palette.down)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "网络或接口异常，与关键词无关。可以重试，或手动填写。",
                                style = XstzType.caption2,
                                color = palette.textTertiary,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .background(
                                        palette.accent.copy(alpha = 0.14f),
                                        SquircleShape(11.dp, 2.6f),
                                    )
                                    .clickable(onClick = onEnableManual)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            ) {
                                Text("手动填写", style = XstzType.caption, color = palette.accent)
                            }
                        }

                        QueryPhase.IDLE -> Text(
                            "输入名称或 6 位代码开始查询",
                            style = XstzType.caption2,
                            color = palette.textTertiary,
                        )
                    }
                }
            }

            // ---------- 手动填写 ----------
            if (state.manualMode) {
                SectionHeader(
                    title = "手动填写",
                    subtitle = "接口未收录时使用，类型决定它算在哪个资产大类里",
                )
                SolidCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    contentPadding = 16.dp,
                ) {
                    Column {
                        GlassTextField(
                            value = state.manualName,
                            onValueChange = onManualNameChange,
                            label = "名称",
                            placeholder = "例如 22国债01",
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("标的类型", style = XstzType.caption, color = palette.textTertiary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            InstrumentKind.entries.forEach { k ->
                                SegChip(
                                    label = k.displayName,
                                    selected = k == state.manualKind,
                                    small = true,
                                    onClick = { onManualKindChange(k) },
                                )
                            }
                        }
                        if (state.effectiveCode.length != 6) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "手动添加需要一个 6 位代码。名称搜索不到时，先在搜索框输入代码。",
                                style = XstzType.caption2,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
            }

            if (state.error != null) {
                Text(state.error, style = XstzType.caption, color = palette.down)
            }
        }

        // ---------- 底部提交 ----------
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            val enabled = state.canSubmit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (enabled) palette.accent else palette.glassFillStrong,
                        SquircleShape(16.dp, 2.8f),
                    )
                    .clickable(enabled = enabled, onClick = onSubmit)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        state.saving -> "添加中…"
                        state.selected != null -> "添加 ${state.selected!!.name}"
                        else -> "添加到持仓"
                    },
                    style = XstzType.callout,
                    color = if (enabled) Color.White else palette.textTertiary,
                )
            }
        }
    }
}

/**
 * 一条候选。
 *
 * 类型徽章必须醒目：`000001` 那两条候选的名称（平安银行 / 华夏成长）
 * 本身看不出类别，唯一的区分依据就是徽章上的"A股""基金"。
 */
@Composable
private fun CandidateRow(
    candidate: QuoteCandidate,
    isSelected: Boolean,
    showPrice: Boolean,
    onClick: () -> Unit,
) {
    val palette = XstzTheme.palette
    val accent = palette.asset(candidate.assetType)
    val shape = SquircleShape(14.dp, 2.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) palette.accent.copy(alpha = 0.10f) else palette.glassFill,
                shape,
            )
            .border(
                width = if (isSelected) 1.2.dp else 0.8.dp,
                color = if (isSelected) palette.accent.copy(alpha = 0.6f) else palette.divider,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型徽章
        Box(
            Modifier
                .background(accent.copy(alpha = 0.16f), SquircleShape(7.dp, 2.4f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(candidate.kind.displayName, style = XstzType.caption2, color = accent)
        }
        Spacer(Modifier.size(10.dp))

        Column(Modifier.weight(1f)) {
            Text(candidate.name, style = XstzType.callout, color = palette.textPrimary)
            Text(
                buildString {
                    append(candidate.code)
                    if (candidate.dateText.isNotBlank()) append(" · ${candidate.dateText}")
                },
                style = XstzType.caption2,
                color = palette.textTertiary,
            )
        }

        if (showPrice) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Formats.nav(candidate.price),
                    style = XstzType.numericXS,
                    color = palette.textPrimary,
                )
                if (candidate.prevPrice > 0.0) {
                    Text(
                        Formats.percent(candidate.changePercent),
                        style = XstzType.caption2,
                        color = palette.change(candidate.changePercent),
                    )
                }
            }
        }

        if (isSelected) {
            Spacer(Modifier.size(8.dp))
            Icon(
                Icons.Rounded.Check,
                contentDescription = "已选中",
                tint = palette.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
