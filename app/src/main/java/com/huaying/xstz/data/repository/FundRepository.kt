package com.huaying.xstz.data.repository

import android.util.Log
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.NetValueRecord
import com.huaying.xstz.data.entity.TargetAllocation
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.model.DailyAssetData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.LocalDate

class FundRepository(
    private val database: AppDatabase
) {
    private val okHttpClient = OkHttpClient()
    
    // ========== Fund 操作 ==========
    fun getAllFunds(): Flow<List<Fund>> = database.fundDao().getAllFunds()
    
    suspend fun getAllFundsOnce(): List<Fund> = database.fundDao().getAllFunds().first()
    
    suspend fun getFundById(id: Long): Fund? = database.fundDao().getFundById(id)
    
    suspend fun getFundByCode(code: String): Fund? = database.fundDao().getFundByCode(code)
    
    suspend fun insertFund(fund: Fund): Long = database.fundDao().insertFund(fund)
    
    suspend fun updateFund(fund: Fund) = database.fundDao().updateFund(fund)
    
    suspend fun deleteFund(fund: Fund) = database.fundDao().deleteFund(fund)

    suspend fun deleteAllFunds() = database.fundDao().deleteAllFunds()

    suspend fun clearAllDataPreservingCash() {
        // 1. 删除除现金账户外的所有基金
        database.fundDao().deleteFundsExcludeType(AssetType.CASH)
        // 2. 重置现金账户的持仓和成本为0
        database.fundDao().resetFundByType(AssetType.CASH)
        // 3. 清空所有交易记录
        database.transactionDao().deleteAllTransactions()
        // 4. 清空所有净值记录（图表快照）
        database.netValueRecordDao().deleteAllRecords()
    }

    suspend fun resetCashFund() {
        database.fundDao().resetFundByType(AssetType.CASH)
    }
    
    suspend fun deleteFundById(id: Long) = database.fundDao().deleteFundById(id)
    
    suspend fun getTotalMarketValue(): Double = database.fundDao().getTotalMarketValue() ?: 0.0
    
    suspend fun getTotalCost(): Double = database.fundDao().getTotalCost() ?: 0.0
    
    suspend fun getMarketValueByType(type: AssetType): Double = 
        database.fundDao().getMarketValueByType(type) ?: 0.0

    // ========== Transaction 操作 ==========
    fun getAllTransactions(): Flow<List<Transaction>> = database.transactionDao().getAllTransactions()

    fun getTransactionsByFund(fundId: Long): Flow<List<Transaction>> = database.transactionDao().getTransactionsByFund(fundId)

    suspend fun insertTransaction(transaction: Transaction): Long = database.transactionDao().insertTransaction(transaction)

    suspend fun deleteTransaction(transaction: Transaction) = database.transactionDao().deleteTransaction(transaction)

    suspend fun deleteAllTransactions() = database.transactionDao().deleteAllTransactions()

    
    // 从网络获取基金名称
    suspend fun fetchFundInfo(code: String): Pair<String?, String>? = withContext(Dispatchers.IO) {
        try {
            // 移除空格
            val trimmedCode = code.trim()
            
            // 检查代码长度
            if (trimmedCode.length != 6) {
                return@withContext Pair(null, "代码长度错误")
            }
            
            // 构建URL
            val url = if (isStockCode(trimmedCode)) {
                // 判断股票交易所
                val exchange = when {
                    trimmedCode.startsWith("6") || trimmedCode.startsWith("9") -> "sh"
                    trimmedCode.startsWith("0") || trimmedCode.startsWith("3") || trimmedCode.startsWith("2") -> "sz"
                    else -> return@withContext Pair(null, "无效的股票代码")
                }
                "https://qt.gtimg.cn/q=${exchange}${trimmedCode}"
            } else if (is场内基金Code(trimmedCode)) {
                // 场内基金使用股票接口
                val exchange = when {
                    trimmedCode.startsWith("5") -> "sh"
                    trimmedCode.startsWith("1") -> "sz"
                    else -> return@withContext Pair(null, "无效的场内基金代码")
                }
                "https://qt.gtimg.cn/q=${exchange}${trimmedCode}"
            } else if (is场外基金Code(trimmedCode)) {
                "https://qt.gtimg.cn/q=jj${trimmedCode}"
            } else {
                return@withContext Pair(null, "无效的代码")
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Referer", "https://gu.qq.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful && response.body != null) {
                val content = response.body!!.string()

                // 检查是否为空响应
                if (content.isEmpty() || content.trim().isEmpty()) {
                    return@withContext Pair(null, "响应为空")
                }

                // 解析返回数据: v_sz159509="纳指科技ETF~1.234~..." 或 v_jj110022="易方达消费行业股票~1.234~..."
                // 找到第一个 " 和最后一个 " 之间的内容
                val startIndex = content.indexOf('"')
                val endIndex = content.lastIndexOf('"')

                if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                    return@withContext Pair(null, "数据格式错误。响应: $content")
                }

                val data = content.substring(startIndex + 1, endIndex)

                if (data.isEmpty()) {
                    return@withContext Pair(null, "数据为空。响应: $content")
                }

                val fields = data.split("~")

                // 返回名称 (index 1)
                if (fields.size > 2 && fields[1].isNotEmpty()) {
                    Pair(fields[1], "")
                } else {
                    Pair(null, "字段数量: ${fields.size}。数据: $data")
                }
            } else {
                Pair(null, "请求失败: ${response.code}")
            }
        } catch (e: Exception) {
            Pair(null, "异常: ${e.message}")
        }
    }
    
    // 判断是否为股票代码（根据中国市场规则）
    private fun isStockCode(code: String): Boolean {
        // 股票代码规则：
        // 上海主板：600、601、603、605
        // 上海科创板：688
        // 上海B股：900
        // 深圳主板（含中小板）：000、001、002、003、004
        // 深圳创业板：300、301
        // 深圳B股：200
        // 北京证券交易所：43、83、87、88
        return code.matches(Regex("^(60[0135]|688|900|00[0-4]|30[01]|200|43|8[378])\\d{3,4}$"))
    }
    
    // 判断是否为场内基金代码（根据中国市场规则）
    private fun is场内基金Code(code: String): Boolean {
        // 场内基金代码规则：
        // 上海证券交易所：5、50、51、52
        // 深圳证券交易所：15、16、18
        return code.matches(Regex("^(5|50|51|52|15|16|18)\\d{4,5}$"))
    }
    
    // 判断是否为场外基金代码（根据中国市场规则）
    private fun is场外基金Code(code: String): Boolean {
        // 场外基金代码规则：
        // 除上述股票和场内基金开头之外的数字，尤其是0、00开头
        return code.matches(Regex("^\\d{6}$")) && !isStockCode(code) && !is场内基金Code(code)
    }
    
    // 从腾讯接口获取完整实时数据
    suspend fun fetchStockRealtimeData(code: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            // 移除空格
            val trimmedCode = code.trim()
            
            // 检查代码长度
            if (trimmedCode.length != 6) {
                return@withContext null
            }
            
            // 构建URL
            val url = if (isStockCode(trimmedCode)) {
                // 判断股票交易所
                val exchange = when {
                    trimmedCode.startsWith("6") || trimmedCode.startsWith("9") -> "sh"
                    trimmedCode.startsWith("0") || trimmedCode.startsWith("3") || trimmedCode.startsWith("2") -> "sz"
                    else -> return@withContext null
                }
                "https://qt.gtimg.cn/q=${exchange}${trimmedCode}"
            } else if (is场内基金Code(trimmedCode)) {
                // 场内基金使用股票接口
                val exchange = when {
                    trimmedCode.startsWith("5") -> "sh"
                    trimmedCode.startsWith("1") -> "sz"
                    else -> return@withContext null
                }
                "https://qt.gtimg.cn/q=${exchange}${trimmedCode}"
            } else if (is场外基金Code(trimmedCode)) {
                "https://qt.gtimg.cn/q=jj${trimmedCode}"
            } else {
                return@withContext null
            }
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .header("Referer", "https://gu.qq.com/")
                .build()
            val response = okHttpClient.newCall(request).execute()
            
            if (response.isSuccessful && response.body != null) {
                val content = response.body!!.string()
                // 解析返回数据: v_sz159509="纳指科技ETF~1.234~..." 或 v_jj110022="易方达消费行业股票~1.234~..."
                val startIndex = content.indexOf('"')
                val endIndex = content.lastIndexOf('"')

                if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                    return@withContext null
                }

                val data = content.substring(startIndex + 1, endIndex)
                val fields = data.split("~")

                if (is场外基金Code(trimmedCode)) {
                    // 场外基金数据解析
                    if (fields.size > 8) {
                        val result: Map<String, Any> = mapOf(
                            "name" to fields[1],       // 基金名称
                            "currentPrice" to (fields[6].toDoubleOrNull() ?: 0.0),   // 单位净值
                            "preClosePrice" to 0.0, // 基金无昨收盘价
                            "change" to 0.0,       // 基金无涨跌
                            "changePercent" to (fields[8].toDoubleOrNull() ?: 0.0) // 日涨跌幅(%)
                        )
                        result
                    } else {
                        null
                    }
                } else if (is场内基金Code(trimmedCode) || isStockCode(trimmedCode)) {
                    // 场内基金和股票数据解析
                    if (fields.size > 32) {
                        val result: Map<String, Any> = mapOf(
                            "name" to fields[1],       // 名称
                            "currentPrice" to (fields[3].toDoubleOrNull() ?: 0.0),   // 现价
                            "preClosePrice" to (fields[4].toDoubleOrNull() ?: 0.0), // 昨收盘价
                            "change" to (fields[31].toDoubleOrNull() ?: 0.0),       // 涨跌
                            "changePercent" to (fields[32].toDoubleOrNull() ?: 0.0) // 涨跌幅(%)
                        )
                        result
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 批量更新基金数据 - 使用并发请求加速
    suspend fun updateAllFundsRealtimeData(): Boolean = withContext(Dispatchers.IO) {
        try {
            val funds = getAllFunds().first()
            
            // 使用 coroutineScope 和 async 实现并发请求
            coroutineScope {
                funds.map { fund ->
                    async {
                        try {
                            // 使用腾讯接口获取数据
                            val stockData = fetchStockRealtimeData(fund.code)
                            if (stockData != null) {
                                val updatedFund = fund.copy(
                                    currentPrice = (stockData["currentPrice"] as? Double) ?: fund.currentPrice,
                                    changePercent = (stockData["changePercent"] as? Double) ?: fund.changePercent,
                                    updatedAt = TimeRepository.getCurrentTimeMillis()
                                )
                                updateFund(updatedFund)
                            }
                        } catch (e: Exception) {
                            // 单个基金更新失败不影响其他基金
                            e.printStackTrace()
                        }
                    }
                }.awaitAll() // 等待所有并发请求完成
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // ========== NetValueRecord 操作 ==========
    fun getAllNetValueRecords(): Flow<List<NetValueRecord>> = 
        database.netValueRecordDao().getAllRecords()
    
    fun getRecentNetValueRecords(limit: Int = 30): Flow<List<NetValueRecord>> = 
        database.netValueRecordDao().getRecentRecords(limit)
    
    suspend fun insertNetValueRecord(record: NetValueRecord): Long = 
        database.netValueRecordDao().insertRecord(record)
    
    suspend fun deleteAllNetValueRecords() = database.netValueRecordDao().deleteAllRecords()

    /**
     * 获取处理后的每日资产数据流，用于图表显示。
     * 逻辑：从数据库获取所有快照，按日期分组，每天仅保留最后一个快照。
     */
    fun getDailyAssetDataFlow(): Flow<List<DailyAssetData>> {
        return getAllNetValueRecords().map { records ->
            records.map { record ->
                val date = Instant.ofEpochMilli(record.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                
                DailyAssetData(
                    date = date,
                    stockValue = record.stockValue,
                    bondValue = record.bondValue,
                    goldValue = record.goldValue,
                    cashValue = record.cashValue,
                    principal = record.principal
                )
            }
            .groupBy { it.date }
            .map { (_, dailyRecords) -> dailyRecords.last() }
            .sortedBy { it.date }
        }
    }
    
    // 记录当前净值快照
    suspend fun recordCurrentSnapshot(): NetValueRecord? = withContext(Dispatchers.IO) {
        try {
            val totalAssets = getTotalMarketValue()
            val totalCost = getTotalCost()
            val totalReturn = totalAssets - totalCost
            val returnRate = if (totalCost > 0) totalReturn / totalCost * 100 else 0.0
            
            val stockValue = getMarketValueByType(AssetType.STOCK)
            val bondValue = getMarketValueByType(AssetType.BOND)
            val commodityValue = getMarketValueByType(AssetType.COMMODITY)
            val cashValue = getMarketValueByType(AssetType.CASH)
            
            NetValueRecord(
                totalAssets = totalAssets,
                principal = totalCost,
                totalReturn = totalReturn,
                returnRate = returnRate,
                stockValue = stockValue,
                bondValue = bondValue,
                goldValue = commodityValue,
                cashValue = cashValue,
                createdAt = TimeRepository.getCurrentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // ========== TargetAllocation 操作 ==========
    fun getTargetAllocation(): Flow<TargetAllocation?> = 
        database.targetAllocationDao().getTargetAllocation()
    
    suspend fun getTargetAllocationSync(): TargetAllocation? = 
        database.targetAllocationDao().getTargetAllocation().first()
    
    suspend fun updateTargetAllocation(allocation: TargetAllocation) = 
        database.targetAllocationDao().updateTargetAllocation(allocation)
    
    // 初始化默认目标配置和现金账户
    suspend fun initDefaultTargetAllocation() = withContext(Dispatchers.IO) {
        val existing = getTargetAllocationSync()
        if (existing == null) {
            val default = TargetAllocation(
                stockRatio = 40.0,
                bondRatio = 30.0,
                goldRatio = 10.0,
                cashRatio = 20.0
            )
            database.targetAllocationDao().insertTargetAllocation(default)
        }
        ensureDefaultCashFund()
    }

    // ========== 数据导出/导入辅助 ==========
    suspend fun getAllDataForExport() = withContext(Dispatchers.IO) {
        val funds = database.fundDao().getAllFunds().first()
        val transactions = database.transactionDao().getAllTransactions().first()
        val records = database.netValueRecordDao().getAllRecords().first()
        Triple(funds, transactions, records)
    }

    suspend fun importAllData(
        funds: List<Fund>,
        transactions: List<Transaction>,
        records: List<NetValueRecord>
    ) = withContext(Dispatchers.IO) {
        // runInTransaction 不支持挂起函数，所以我们需要在外部逐个执行或使用其他方式
        // 这里为了保证原子性，可以考虑在 DAO 中提供批量插入的方法，或者在这里手动处理
        database.clearAllTables()
        
        funds.forEach { database.fundDao().insertFund(it) }
        transactions.forEach { database.transactionDao().insertTransaction(it) }
        records.forEach { database.netValueRecordDao().insertRecord(it) }
    }

    private suspend fun ensureDefaultCashFund() {
        val cashFund = database.fundDao().getFundByCode("CASH")
        if (cashFund == null) {
            val newFund = Fund(
                code = "CASH",
                name = "现金账户",
                type = AssetType.CASH,
                holdingQuantity = 0.0,
                totalCost = 0.0,
                currentPrice = 1.0,
                changePercent = 0.0,
                targetRatio = 0.0 // 默认不强制分配，只作为缓冲
            )
            database.fundDao().insertFund(newFund)
        }
    }
}
