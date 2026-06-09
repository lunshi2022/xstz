package com.huaying.xstz.ui.addfund

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.OperationType
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.ui.animation.AnimationConstants
import com.huaying.xstz.ui.component.ThousandSeparatorTransformation
import com.huaying.xstz.ui.theme.*
import com.huaying.xstz.util.AppConstants
import kotlinx.coroutines.launch
import java.util.Locale

// 格式化数字为千分符显示（用于显示）
private fun formatNumber(value: String, decimalPlaces: Int = 2): String {
    if (value.isEmpty()) return ""
    val number = value.toDoubleOrNull() ?: return value
    return String.format(Locale.CHINA, "%,.${decimalPlaces}f", number)
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AddFundScreen(
    darkTheme: Boolean = false,
    operationLogRepository: OperationLogRepository? = null,
    onBack: () -> Unit = {},
    onFundAdded: () -> Unit = {},
    viewModel: AddFundViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState as? AddFundUiState.Success

    val scope = rememberCoroutineScope()

    val fundCode = state?.fundCode ?: TextFieldValue("")
    val fundName = state?.fundName ?: ""
    val selectedType = state?.selectedType ?: AssetType.STOCK
    val existingFund = state?.existingFund
    val isOverwriteMode = state?.isOverwriteMode ?: false
    val inputMode = state?.inputMode ?: true
    val holdingQuantity = state?.holdingQuantity ?: TextFieldValue("")
    val totalCost = state?.totalCost ?: TextFieldValue("")
    val marketValue = state?.marketValue ?: TextFieldValue("")
    val costPrice = state?.costPrice ?: TextFieldValue("")
    val isLoading = state?.isLoading ?: false
    val queryResult = state?.queryResult

    val fundCodeTextField = remember { FocusRequester() }

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("添加基金")
    }

    // 判断当前输入方式是否有效
    val hasShareInput = holdingQuantity.text.isNotBlank() || totalCost.text.isNotBlank()
    val hasMarketInput = marketValue.text.isNotBlank() || costPrice.text.isNotBlank()
    val isShareModeValid = holdingQuantity.text.isNotBlank() && totalCost.text.isNotBlank()
    val isMarketModeValid = marketValue.text.isNotBlank() && costPrice.text.isNotBlank()

    val currentInputMode = when {
        hasShareInput && !hasMarketInput -> true
        hasMarketInput && !hasShareInput -> false
        isShareModeValid && !isMarketModeValid -> true
        isMarketModeValid && !isShareModeValid -> false
        else -> inputMode
    }

    val isFundNameValid = fundName.isNotBlank() &&
                          !fundName.contains("获取失败") &&
                          !fundName.contains("请检查")

    val quantityLong = holdingQuantity.text.toLongOrNull() ?: AppConstants.ZERO_LONG
    val isQuantityMultipleOf100 = quantityLong > 0 && quantityLong % AppConstants.MIN_TRADE_UNIT == AppConstants.ZERO_LONG

    val shouldEnforceLotRule = selectedType == AssetType.STOCK ||
                             selectedType == AssetType.BOND ||
                             selectedType == AssetType.COMMODITY

    val showQuantityError = currentInputMode &&
                          shouldEnforceLotRule &&
                          holdingQuantity.text.isNotEmpty() &&
                          !isQuantityMultipleOf100

    val isValid = fundCode.text.isNotBlank() &&
                  isFundNameValid &&
                  (isShareModeValid || isMarketModeValid) &&
                  !showQuantityError

    darkTheme

    Scaffold(
        topBar = {
            val backgroundColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* 拦截点击事件 */ }
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    OperationLogger.logBack("添加基金")
                    onBack()
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "添加基金",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(paddingValues)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基金代码和基金名称卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 基金代码
                    OutlinedTextField(
                        value = fundCode,
                        onValueChange = {
                            viewModel.setFundCode(it.text)
                        },
                        label = { Text("基金代码") },
                        placeholder = { Text("请输入${AppConstants.FUND_CODE_LENGTH}位基金代码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fundCodeTextField),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (fundCode.text.length == AppConstants.FUND_CODE_LENGTH) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .height(40.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                OperationLogger.logButtonClick("获取基金信息", "添加基金")
                                                viewModel.fetchFundInfo()
                                            }
                                        },
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxHeight(),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("获取")
                                        }
                                    }
                                }
                            }
                        }
                    )

                    // 基金名称
                    Column {
                        Text(
                            text = "基金名称",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFundNameValid)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        val displayText = when {
                            fundName.isNotBlank() -> fundName
                            queryResult?.hasAnyResult == true -> "请选择下方列表中的资产"
                            else -> "请输入基金代码后点击获取"
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                fundName.isNotBlank() -> MaterialTheme.colorScheme.onSurface
                                queryResult?.hasAnyResult == true -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = queryResult?.hasBothResults == true && fundName.isEmpty(),
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                            animationSpec = tween(AnimationConstants.Duration.NORMAL, easing = AnimationConstants.Easing.Decelerate)
                        ) + fadeIn(tween(AnimationConstants.Duration.NORMAL)),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                            animationSpec = tween(AnimationConstants.Duration.FAST, easing = AnimationConstants.Easing.Accelerate)
                        ) + fadeOut(tween(AnimationConstants.Duration.FAST))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "该代码对应多个资产，请选择：",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            queryResult?.fundName?.let { name ->
                                val fundInteractionSource = remember { MutableInteractionSource() }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = fundInteractionSource,
                                            indication = LocalIndication.current
                                        ) {
                                            viewModel.setFundName(name)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "场外基金",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            queryResult?.stockName?.let { name ->
                                val stockInteractionSource = remember { MutableInteractionSource() }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = stockInteractionSource,
                                            indication = LocalIndication.current
                                        ) {
                                            viewModel.setFundName(name)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "股票/场内基金",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 已存在基金提示卡片
            AnimatedContent(
                targetState = existingFund,
                transitionSpec = {
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(AnimationConstants.Duration.NORMAL, easing = AnimationConstants.Easing.Decelerate)
                    ) + fadeIn(tween(AnimationConstants.Duration.NORMAL)) togetherWith
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(AnimationConstants.Duration.FAST, easing = AnimationConstants.Easing.Accelerate)
                    ) + fadeOut(tween(AnimationConstants.Duration.FAST))
                }
            ) { fund ->
                if (fund != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "已在持仓列表中",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "持仓份额",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = formatNumber(fund.holdingQuantity.toString()),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val avgCost = if (fund.holdingQuantity > 0) fund.totalCost / fund.holdingQuantity else 0.0
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "持仓均价",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = formatNumber(avgCost.toString(), 3),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "持仓总额",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = formatNumber(fund.totalCost.toString()),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        if (isOverwriteMode) {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("覆盖模式")
                                            }
                                            append("(将使用新数据直接覆盖现有持仓)")
                                        } else {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("加仓模式")
                                            }
                                            append("(将在现有持仓基础上增加新份额)")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = isOverwriteMode,
                                    onCheckedChange = { viewModel.setOverwriteMode(it) },
                                    thumbContent = {
                                        if (isOverwriteMode) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        checkedTrackColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        uncheckedBorderColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.fillMaxWidth().height(0.dp))
                }
            }

            // 资产类别
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFundNameValid)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "资产类别",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFundNameValid)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssetType.values().filter { it != AssetType.CASH }.forEach { type ->
                            val typeName = when (type) {
                                AssetType.STOCK -> "股票"
                                AssetType.BOND -> "债券"
                                AssetType.COMMODITY -> "商品"
                                AssetType.CASH -> "现金"
                            }
                            val isSelected = selectedType == type
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        enabled = isFundNameValid,
                                        interactionSource = interactionSource,
                                        indication = LocalIndication.current
                                    ) { viewModel.setSelectedType(type) }
                                    .background(
                                        color = when {
                                            !isFundNameValid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = typeName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        !isFundNameValid -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 持仓信息输入
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFundNameValid)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "配置详情",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFundNameValid)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val shareInteractionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        enabled = isFundNameValid && !hasMarketInput,
                                        interactionSource = shareInteractionSource,
                                        indication = LocalIndication.current
                                    ) { viewModel.setInputMode(true) }
                                    .background(
                                        color = when {
                                            !isFundNameValid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            hasMarketInput -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            inputMode -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "按份额",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        !isFundNameValid -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        hasMarketInput -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        inputMode -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (inputMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            val marketInteractionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        enabled = isFundNameValid && !hasShareInput,
                                        interactionSource = marketInteractionSource,
                                        indication = LocalIndication.current
                                    ) { viewModel.setInputMode(false) }
                                    .background(
                                        color = when {
                                            !isFundNameValid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            hasShareInput -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            !inputMode -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "按市值",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        !isFundNameValid -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        hasShareInput -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        !inputMode -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (!inputMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    AnimatedContent(
                        targetState = currentInputMode,
                        transitionSpec = {
                            val spec = tween<IntOffset>(AnimationConstants.Duration.NORMAL, easing = AnimationConstants.Easing.Standard)
                            if (targetState) {
                                slideInHorizontally(animationSpec = spec, initialOffsetX = { width -> width }) + fadeIn(tween(AnimationConstants.Duration.NORMAL)) togetherWith
                                slideOutHorizontally(animationSpec = spec, targetOffsetX = { width -> -width }) + fadeOut(tween(AnimationConstants.Duration.NORMAL))
                            } else {
                                slideInHorizontally(animationSpec = spec, initialOffsetX = { width -> -width }) + fadeIn(tween(AnimationConstants.Duration.NORMAL)) togetherWith
                                slideOutHorizontally(animationSpec = spec, targetOffsetX = { width -> width }) + fadeOut(tween(AnimationConstants.Duration.NORMAL))
                            }
                        }
                    ) { isShareMode ->
                        if (isShareMode) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    OutlinedTextField(
                                        value = holdingQuantity,
                                        onValueChange = {
                                            if (!isFundNameValid) return@OutlinedTextField
                                            viewModel.setHoldingQuantity(it)
                                        },
                                        label = { Text(if (existingFund != null && !isOverwriteMode) "新增份额" else "持仓份额") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = isFundNameValid,
                                        isError = showQuantityError,
                                        supportingText = {},
                                        visualTransformation = ThousandSeparatorTransformation()
                                    )
                                    OutlinedTextField(
                                        value = totalCost,
                                        onValueChange = { newValue ->
                                            if (!isFundNameValid) return@OutlinedTextField
                                            viewModel.setTotalCost(newValue)
                                        },
                                        label = { Text(if (existingFund != null && !isOverwriteMode) "新增金额" else "总金额") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = isFundNameValid,
                                        supportingText = {},
                                        visualTransformation = ThousandSeparatorTransformation()
                                    )
                                }
                                if (showQuantityError) {
                                    Text(
                                        text = "根据A股交易规则，买入数量必须是${AppConstants.MIN_TRADE_UNIT}股的整数倍",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                        maxLines = Int.MAX_VALUE
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedTextField(
                                    value = marketValue,
                                    onValueChange = { newValue ->
                                        if (!isFundNameValid) return@OutlinedTextField
                                        viewModel.setMarketValue(newValue)
                                    },
                                    label = { Text(if (existingFund != null && !isOverwriteMode) "新增市值" else "持仓市值") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = isFundNameValid,
                                    supportingText = {},
                                    visualTransformation = ThousandSeparatorTransformation()
                                )
                                OutlinedTextField(
                                    value = costPrice,
                                    onValueChange = { newValue ->
                                        if (!isFundNameValid) return@OutlinedTextField
                                        viewModel.setCostPrice(newValue)
                                    },
                                    label = { Text("买入价格") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = isFundNameValid,
                                    supportingText = {},
                                    visualTransformation = ThousandSeparatorTransformation()
                                )
                            }
                        }
                    }
                }
            }

            // 底部按钮
            Button(
                onClick = {
                    viewModel.saveFund(operationLogRepository, onFundAdded)
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            ) {
                Text(
                    text = if (existingFund != null) (if (isOverwriteMode) "确认覆盖" else "确认加仓") else "确认添加",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
