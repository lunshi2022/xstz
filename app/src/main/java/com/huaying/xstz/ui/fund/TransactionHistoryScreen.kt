package com.huaying.xstz.ui.fund

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import com.huaying.xstz.data.entity.toDisplayName
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import com.huaying.xstz.ui.theme.*

// 筛选类型
enum class FilterType {
    ALL,  // 全部
    BUY,  // 转入
    SELL  // 转出
}

// 筛选按钮组件
@Composable
fun FilterButton(
    filterType: FilterType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isCash: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onSelect() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (filterType) {
                FilterType.ALL -> "全部"
                FilterType.BUY -> if (isCash) "转入" else "买入"
                FilterType.SELL -> if (isCash) "转出" else "卖出"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    transactions: List<Transaction>,
    fundType: com.huaying.xstz.data.entity.AssetType,
    darkTheme: Boolean = false,
    onBack: () -> Unit
) {
    val isDarkMode = darkTheme
    var selectedFilter by remember { mutableStateOf(FilterType.ALL) }

    // 根据选择的筛选类型过滤交易记录
    val filteredTransactions = remember(selectedFilter, transactions, fundType) {
        val isCash = fundType == com.huaying.xstz.data.entity.AssetType.CASH
        when (selectedFilter) {
            FilterType.ALL -> transactions
            FilterType.BUY -> if (isCash) {
                transactions.filter { it.type == TransactionType.BUY || it.type == TransactionType.ADD_FUNDS }
            } else {
                transactions.filter { it.type == TransactionType.BUY }
            }
            FilterType.SELL -> if (isCash) {
                transactions.filter { it.type == TransactionType.SELL || it.type == TransactionType.WITHDRAW }
            } else {
                transactions.filter { it.type == TransactionType.SELL }
            }
        }
    }

    // 计算筛选结果数量
    val resultCount = filteredTransactions.size

    val backgroundColor = if (isDarkMode) DarkBackground else LightBackground
    val textPrimaryColor = if (isDarkMode) DarkTextPrimary else LightTextPrimary

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimaryColor)
                }
                Text(
                    "交易记录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        containerColor = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            // 筛选选项
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterType.values().forEach { filterType ->
                    Box(modifier = Modifier.weight(1f)) {
                        FilterButton(
                            filterType = filterType,
                            isSelected = selectedFilter == filterType,
                            onSelect = { selectedFilter = filterType },
                            isCash = fundType == com.huaying.xstz.data.entity.AssetType.CASH
                        )
                    }
                }
            }

            // 结果数量提示
            if (resultCount > 0) {
                Text(
                    text = "共 $resultCount 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无交易记录",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    val sortedTransactions = filteredTransactions.sortedByDescending { it.createdAt }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(items = sortedTransactions) { index, transaction: Transaction ->
                            TransactionItemRow(transaction, isCash = fundType == com.huaying.xstz.data.entity.AssetType.CASH)
                            if (index < sortedTransactions.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItemRow(transaction: Transaction, isCash: Boolean) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    dateFormat.timeZone = TimeZone.getTimeZone("GMT+8")
    val dateStr = dateFormat.format(Date(transaction.createdAt))
    
    val isIncome = transaction.type == TransactionType.SELL || transaction.type == TransactionType.WITHDRAW
    val amountColor = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    // 根据是否为现金账户决定显示的交易类型文本
    val transactionTypeText = if (isCash) {
        when (transaction.type) {
            TransactionType.BUY -> "转入"
            TransactionType.SELL -> "转出"
            TransactionType.REBALANCE -> "再平衡"
            TransactionType.ADD_FUNDS -> "转入"
            TransactionType.WITHDRAW -> "转出"
        }
    } else {
        transaction.type.toDisplayName()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transactionTypeText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "¥%,.2f".format(Locale.CHINA, if (transaction.type == TransactionType.BUY) abs(transaction.amount) else -abs(transaction.amount)),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Only show price/quantity for trade types
            if (transaction.type == TransactionType.BUY || 
                transaction.type == TransactionType.SELL || 
                transaction.type == TransactionType.REBALANCE) {
                Text(
                    text = "%.3f元 * %.0f份".format(Locale.CHINA, transaction.price, transaction.quantity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (transaction.remark.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "备注: ${transaction.remark}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
