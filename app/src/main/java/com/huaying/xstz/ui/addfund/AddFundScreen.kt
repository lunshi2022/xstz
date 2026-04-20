package com.huaying.xstz.ui.addfund

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberCoroutineScope
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.OperationType
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.OperationLogger
import com.huaying.xstz.data.repository.TimeRepository
import com.huaying.xstz.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

// 格式化数字为千分符显示（用于显示）
private fun formatNumber(value: String, decimalPlaces: Int = 2): String {
    if (value.isEmpty()) return ""
    val number = value.toDoubleOrNull() ?: return value
    return String.format(Locale.CHINA, "%,.${decimalPlaces}f", number)
}

// 解析千分符字符串为纯数字
private fun parseNumber(formatted: String): String {
    return formatted.replace(",", "")
}

// 格式化输入值用于显示（处理小数点输入中的状态）
private fun formatForDisplay(value: String, isInteger: Boolean = false): String {
    if (value.isEmpty()) return ""
    
    // 整数直接格式化
    if (isInteger) {
        val number = value.toLongOrNull() ?: return value
        return String.format(Locale.CHINA, "%,d", number)
    }
    
    // 如果正在输入小数点，保留原样
    if (value.endsWith(".")) {
        val intPart = value.substringBefore(".")
        val intFormatted = intPart.toLongOrNull()?.let { 
            String.format(Locale.CHINA, "%,d", it) 
        } ?: intPart
        return "$intFormatted."
    }
    
    // 如果有小数点，格式化整数部分，保留小数部分原样
    if (value.contains(".")) {
        val intPart = value.substringBefore(".")
        val decimalPart = value.substringAfter(".")
        val intFormatted = intPart.toLongOrNull()?.let { 
            String.format(Locale.CHINA, "%,d", it) 
        } ?: intPart
        return "$intFormatted.$decimalPart"
    }
    
    // 纯整数，格式化为千分符（不添加小数位）
    val number = value.toLongOrNull() ?: return value
    return String.format(Locale.CHINA, "%,d", number)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AddFundScreen(
    darkTheme: Boolean = false,
    operationLogRepository: OperationLogRepository? = null,
    onBack: () -> Unit = {},
    onFundAdded: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context)
    val repository = remember { FundRepository(database) }
    val scope = rememberCoroutineScope()

    var fundCode by remember { mutableStateOf(TextFieldValue("")) }
    var fundName by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(AssetType.STOCK) }
    var existingFund by remember { mutableStateOf<Fund?>(null) }
    var isOverwriteMode by remember { mutableStateOf(false) }

    var inputMode by rememberSaveable { mutableStateOf(true) } // true = 按份额, false = 按市值

    // 按份额输入 - 存储原始值（无千分符）
    var holdingQuantity by rememberSaveable { mutableStateOf("") }
    var totalCost by rememberSaveable { mutableStateOf("") }
    // 按市值输入 - 存储原始值（无千分符）
    var marketValue by rememberSaveable { mutableStateOf("") }
    var costPrice by rememberSaveable { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    val fundCodeTextField = remember { FocusRequester() }

    // 记录页面查看
    LaunchedEffect(Unit) {
        OperationLogger.logPageView("添加基金")
    }

    // 检查基金是否已存在
    LaunchedEffect(fundCode.text) {
        if (fundCode.text.length == 6) {
            val fund = repository.getFundByCode(fundCode.text)
            if (fund != null) {
                existingFund = fund
                fundName = fund.name
                selectedType = fund.type
            } else {
                // 如果是6位但没找到，清除之前关联的数据
                existingFund = null
                fundName = ""
                // 重置为默认值，避免上一个基金的数据干扰
                selectedType = AssetType.STOCK
            }
        } else {
            // 如果不是6位，清除所有关联数据
            existingFund = null
            fundName = ""
            // 重置为默认值，避免上一个基金的数据干扰
            selectedType = AssetType.STOCK
        }
    }

    // 判断当前输入方式是否有效
    val hasShareInput = holdingQuantity.isNotBlank() || totalCost.isNotBlank()
    val hasMarketInput = marketValue.isNotBlank() || costPrice.isNotBlank()
    val isShareModeValid = holdingQuantity.isNotBlank() && totalCost.isNotBlank()
    val isMarketModeValid = marketValue.isNotBlank() && costPrice.isNotBlank()
    
    // 确定当前使用的输入方式
    val currentInputMode = when {
        hasShareInput && !hasMarketInput -> true  // 按份额
        hasMarketInput && !hasShareInput -> false // 按市值
        isShareModeValid && !isMarketModeValid -> true
        isMarketModeValid && !isShareModeValid -> false
        else -> inputMode // 默认使用切换选择
    }
    
    // 判断是否正确获取到基金名称
    val isFundNameValid = fundName.isNotBlank() &&
                          !fundName.contains("获取失败") &&
                          !fundName.contains("请检查")

    // A股交易规则验证：股票、债券、商品ETF等场内交易品种，买入数量必须是100股的整数倍
    val quantityLong = parseNumber(holdingQuantity).toLongOrNull() ?: 0L
    val isQuantityMultipleOf100 = quantityLong > 0 && quantityLong % 100 == 0L
    
    // 检查是否需要应用交易规则
    // 股票、债券、商品 都需要遵守一手100股的规则
    val shouldEnforceLotRule = selectedType == AssetType.STOCK || 
                             selectedType == AssetType.BOND || 
                             selectedType == AssetType.COMMODITY
                             
    val showQuantityError = currentInputMode && 
                          shouldEnforceLotRule && 
                          holdingQuantity.isNotEmpty() && 
                          !isQuantityMultipleOf100

    val isValid = fundCode.text.isNotBlank() &&
                  isFundNameValid &&
                  (isShareModeValid || isMarketModeValid) &&
                  !showQuantityError

    val isDarkMode = darkTheme

    Scaffold(
        topBar = {
            // 使用主题背景色半透明，与页面背景协调
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
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    start = 16.dp,
                    top = 120.dp, // 从标题栏下方开始
                    end = 16.dp,
                    bottom = 140.dp // 确保最后一个项目可以滚动到导航栏上方完全可见
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基金代码和基金名称卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            // 只允许输入数字，最多6位
                            val filtered = it.text.filter { char -> char.isDigit() }
                            if (filtered.length <= 6) {
                                fundCode = TextFieldValue(
                                    text = filtered,
                                    selection = TextRange(filtered.length)
                                )
                            }
                        },
                        label = { Text("基金代码") },
                        placeholder = { Text("请输入6位基金代码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fundCodeTextField),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (fundCode.text.length == 6) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .height(40.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoading = true
                                                OperationLogger.logButtonClick("获取基金信息", "添加基金")
                                                try {
                                                    val result = repository.fetchFundInfo(fundCode.text)
                                                    if (result != null) {
                                                        val (name, error) = result
                                                        if (name != null) {
                                                            fundName = name
                                                        } else {
                                                            fundName = "获取失败，请检查基金代码"
                                                        }
                                                    } else {
                                                        fundName = "获取失败，请检查基金代码"
                                                    }
                                                } catch (e: Exception) {
                                                    fundName = "获取失败，请检查基金代码"
                                                } finally {
                                                    isLoading = false
                                                }
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
                    Column() {
                        Text(
                            text = "基金名称",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isFundNameValid)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = fundName.ifEmpty { "请输入基金代码后点击获取" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (fundName.isEmpty()) 
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // 已存在基金提示卡片
            AnimatedContent(
                targetState = existingFund,
                transitionSpec = {
                    expandVertically(expandFrom = Alignment.Top) + fadeIn() togetherWith
                    shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                }
            ) { fund ->
                if (fund != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        // ... card content ...
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
                                
                                // 计算持仓均价
                                val avgCost = if (fund.holdingQuantity > 0) fund.totalCost / fund.holdingQuantity else 0.0
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "持仓均价",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = formatNumber(avgCost.toString(), 3), // 显示3位小数
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
                            
                            // 操作模式切换
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
                                    onCheckedChange = { isOverwriteMode = it },
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
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                        // 过滤掉现金选项，因为系统会自动维护默认现金账户
                        AssetType.values().filter { it != AssetType.CASH }.forEach { type ->
                            val typeName = when (type) {
                                AssetType.STOCK -> "股票"
                                AssetType.BOND -> "债券"
                                AssetType.COMMODITY -> "商品"
                                AssetType.CASH -> "现金"
                            }
                            val isSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isFundNameValid) { selectedType = type }
                                    .background(
                                        color = when {
                                            !isFundNameValid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(8.dp)
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
            
            // 持仓信息输入（切换方式和输入框在一个大卡片中）
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isFundNameValid)
                        MaterialTheme.colorScheme.surfaceVariant
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 配置详情标题和切换方式
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
                            // 按份额
                            Box(
                                modifier = Modifier
                                    .clickable(enabled = isFundNameValid && !hasMarketInput) { inputMode = true }
                                    .background(
                                        color = when {
                                            !isFundNameValid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            hasMarketInput -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            inputMode -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(8.dp)
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
                            // 按市值
                            Box(
                                modifier = Modifier
                                    .clickable(enabled = isFundNameValid && !hasShareInput) { inputMode = false }
                                    .background(
                                        color = when {
                                            !isFundNameValid -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            hasShareInput -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            !inputMode -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(8.dp)
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
                    
                    // 输入字段（带动画切换）
                    AnimatedContent(
                        targetState = currentInputMode,
                        transitionSpec = {
                            if (targetState) {
                                // 切换到按份额输入：从右向左滑入
                                slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                            } else {
                                // 切换到按市值输入：从左向右滑入
                                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                            }
                        }
                    ) { isShareMode ->
                        if (isShareMode) {
                            // 按份额输入：持仓份额 + 总金额 - 水平布局
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // 持仓份额 - 整数，千分符
                                    OutlinedTextField(
                                        value = formatForDisplay(holdingQuantity, true),
                                        onValueChange = {
                                            if (!isFundNameValid) return@OutlinedTextField
                                            val parsed = parseNumber(it)
                                            val filtered = parsed.filter { char -> char.isDigit() }
                                            holdingQuantity = filtered
                                        },
                                        label = { Text(if (existingFund != null && !isOverwriteMode) "新增份额" else "持仓份额") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = isFundNameValid,
                                        isError = showQuantityError,
                                        supportingText = {}
                                    )
                                    // 总金额 - 千分符，小数点后两位
                                    OutlinedTextField(
                                        value = formatForDisplay(totalCost, false),
                                        onValueChange = { newValue ->
                                            if (!isFundNameValid) return@OutlinedTextField
                                            val parsed = parseNumber(newValue)
                                            // 允许空值
                                            if (parsed.isEmpty()) {
                                                totalCost = ""
                                                return@OutlinedTextField
                                            }
                                            // 处理小数点输入
                                            val filtered = parsed.filter { char -> char.isDigit() || char == '.' }
                                            val dotCount = filtered.count { it == '.' }
                                            if (dotCount <= 1) {
                                                totalCost = filtered
                                            }
                                        },
                                        label = { Text(if (existingFund != null && !isOverwriteMode) "新增金额" else "总金额") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = isFundNameValid,
                                        supportingText = {}
                                    )
                                }
                                // 提示文字显示在两个输入框下方
                                if (showQuantityError) {
                                    Text(
                                        text = "根据A股交易规则，买入数量必须是100股的整数倍",
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                        maxLines = Int.MAX_VALUE
                                    )
                                }
                            }
                        } else {
                            // 按市值输入：持仓市值 + 买入价格 - 水平布局
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 持仓市值 - 千分符，小数点后两位
                                OutlinedTextField(
                                    value = formatForDisplay(marketValue, false),
                                    onValueChange = { newValue ->
                                        if (!isFundNameValid) return@OutlinedTextField
                                        val parsed = parseNumber(newValue)
                                        // 允许空值
                                        if (parsed.isEmpty()) {
                                            marketValue = ""
                                            return@OutlinedTextField
                                        }
                                        // 处理小数点输入
                                        val filtered = parsed.filter { char -> char.isDigit() || char == '.' }
                                        val dotCount = filtered.count { it == '.' }
                                        if (dotCount <= 1) {
                                            marketValue = filtered
                                        }
                                    },
                                    label = { Text(if (existingFund != null && !isOverwriteMode) "新增市值" else "持仓市值") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = isFundNameValid,
                                    supportingText = {}
                                )
                                // 买入价格 - 千分符，小数点后两位
                                OutlinedTextField(
                                    value = formatForDisplay(costPrice, false),
                                    onValueChange = { newValue ->
                                        if (!isFundNameValid) return@OutlinedTextField
                                        val parsed = parseNumber(newValue)
                                        // 允许空值
                                        if (parsed.isEmpty()) {
                                            costPrice = ""
                                            return@OutlinedTextField
                                        }
                                        // 处理小数点输入
                                        val filtered = parsed.filter { char -> char.isDigit() || char == '.' }
                                        val dotCount = filtered.count { it == '.' }
                                        if (dotCount <= 1) {
                                            costPrice = filtered
                                        }
                                    },
                                    label = { Text("买入价格") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = isFundNameValid,
                                    supportingText = {}
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部按钮
            Button(
                onClick = {
                    scope.launch {
                        val inputQuantity: Double
                        val inputCost: Double
                        val inputPrice: Double

                        if (currentInputMode) {
                             inputQuantity = parseNumber(holdingQuantity).toDoubleOrNull() ?: 0.0
                             inputCost = parseNumber(totalCost).toDoubleOrNull() ?: 0.0
                             inputPrice = if (inputQuantity > 0) inputCost / inputQuantity else 0.0
                        } else {
                             val value = parseNumber(marketValue).toDoubleOrNull() ?: 0.0
                             inputPrice = parseNumber(costPrice).toDoubleOrNull() ?: 0.0
                             inputQuantity = if (inputPrice > 0) value / inputPrice else 0.0
                             inputCost = value
                        }

                        if (existingFund != null) {
                            val fund = existingFund!!
                            if (isOverwriteMode) {
                                val updatedFund = fund.copy(
                                    name = fundName,
                                    type = selectedType,
                                    holdingQuantity = inputQuantity,
                                    totalCost = inputCost,
                                    updatedAt = TimeRepository.getCurrentTimeMillis()
                                )
                                repository.updateFund(updatedFund)
                                repository.insertTransaction(
                                    Transaction(
                                        fundId = fund.id,
                                        fundCode = fund.code,
                                        fundName = fund.name,
                                        type = TransactionType.ADD_FUNDS,
                                        amount = inputCost,
                                        price = inputPrice,
                                        quantity = inputQuantity,
                                        remark = "覆盖持仓"
                                    )
                                )
                            } else {
                                val newQuantity = fund.holdingQuantity + inputQuantity
                                val newCost = fund.totalCost + inputCost
                                val updatedFund = fund.copy(
                                    holdingQuantity = newQuantity,
                                    totalCost = newCost,
                                    updatedAt = TimeRepository.getCurrentTimeMillis()
                                )
                                repository.updateFund(updatedFund)
                            repository.insertTransaction(
                                Transaction(
                                    fundId = fund.id,
                                    fundCode = fund.code,
                                    fundName = fund.name,
                                    type = TransactionType.BUY,
                                    amount = inputCost,
                                    price = inputPrice,
                                    quantity = inputQuantity,
                                    remark = "添加基金-加仓"
                                )
                            )
                            // 记录操作日志
                            operationLogRepository?.logOperation(
                                type = OperationType.ADD_FUND,
                                title = "加仓基金",
                                description = "代码: ${fund.code}, 份额: ${inputQuantity.toLong()}, 成本: ¥${String.format("%.2f", inputCost)}",
                                targetId = fund.id,
                                targetName = fund.name
                            )
                        }
                    } else {
                        val fund = Fund(
                            code = fundCode.text,
                            name = fundName,
                            type = selectedType,
                            holdingQuantity = inputQuantity,
                            totalCost = inputCost,
                            currentPrice = inputPrice
                        )
                        val id = repository.insertFund(fund)
                        repository.insertTransaction(
                            Transaction(
                                fundId = id,
                                fundCode = fund.code,
                                fundName = fund.name,
                                type = TransactionType.ADD_FUNDS,
                                amount = inputCost,
                                price = inputPrice,
                                quantity = inputQuantity,
                                remark = "初始持仓"
                            )
                        )
                        // 记录操作日志
                        operationLogRepository?.logOperation(
                            type = OperationType.ADD_FUND,
                            title = "添加基金",
                            description = "代码: ${fund.code}, 份额: ${inputQuantity.toLong()}, 成本: ¥${String.format("%.2f", inputCost)}",
                            targetId = id,
                            targetName = fund.name
                        )
                    }
                    onFundAdded()
                    }
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
