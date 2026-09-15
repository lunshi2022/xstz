package com.huaying.xstz.data.repository
import android.util.Log
import androidx.room.withTransaction
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.entity.AssetType
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.NetValueRecord
import com.huaying.xstz.data.entity.TargetAllocation
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.model.DailyAssetData
import com.huaying.xstz.data.network.FundNetworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class FundRepository(
    private val database: AppDatabase,
    private val networkService: FundNetworkService
) {
    
    // ========== Fund 操作 ==========
    fun getAllFunds(): Flow<List<Fund>> = database.fundDao().getAllFunds()
    
    suspend fun getAllFundsOnce(): List<Fund> = database.fundDao().getAllFunds().first()
    
    suspend fun getFundById(id: Long): Fund? = database.fundDao().getFundById(id)

    fun getFundByIdFlow(id: Long): Flow<Fund?> = database.fundDao().getFundByIdFlow(id)
    
    suspend fun getFundByCode(code: String): Fund? = database.fundDao().getFundByCode(code)
    
    suspend fun getFundByCodeAndName(code: String, name: String): Fund? = database.fundDao().getFundByCodeAndName(code, name)
    
    suspend fun insertFund(fund: Fund): Long {
        val now = TimeRepository.getCurrentTimeMillis()
        val fundWithTimestamp = if (fund.createdAt == 0L && fund.updatedAt == 0L) {
            fund.copy(createdAt = now, updatedAt = now)
        } else if (fund.updatedAt == 0L) {
            fund.copy(updatedAt = now)
        } else {
            fund
        }
        return database.fundDao().insertFund(fundWithTimestamp)
    }
    
    suspend fun updateFund(fund: Fund) = database.fundDao().updateFund(fund)

    suspend fun updateFundTargetRatio(fundId: Long, targetRatio: Double, updatedAt: Long) =
        database.fundDao().updateFundTargetRatio(fundId, targetRatio, updatedAt)
    
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

    // ========== 网络请求代理方法 ==========
    
    suspend fun fetchFundInfoWithOptions(code: String): FundNetworkService.FundQueryResult = 
        networkService.fetchFundInfoWithOptions(code)
    
    suspend fun fetchFundInfo(code: String): Pair<String?, String> = 
        networkService.fetchFundInfo(code)
    
    suspend fun fetchStockRealtimeData(code: String): Map<String, Any>? = 
        networkService.fetchStockRealtimeData(code)
    
    suspend fun updateAllFundsRealtimeData(): Boolean = withContext(Dispatchers.IO) {
        try {
            val funds = getAllFunds().first()
            val updatedFunds = networkService.updateAllFundsRealtimeData(funds)

            // 单事务批量更新，替代逐条 updateFund
            if (updatedFunds.isNotEmpty()) {
                database.fundDao().updateFunds(updatedFunds)
            }

            true
        } catch (e: Exception) {
            Log.e("FundRepository", "updateAllFundsRealtimeData failed", e)
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

    fun getRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<NetValueRecord>> =
        database.netValueRecordDao().getRecordsByDateRange(startTime, endTime)

    fun getRecordsSince(startTime: Long): Flow<List<NetValueRecord>> =
        database.netValueRecordDao().getRecordsSince(startTime)

    suspend fun getRecordBeforeTimestamp(timestamp: Long): NetValueRecord? =
        database.netValueRecordDao().getRecordBeforeTimestamp(timestamp)

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
            val funds = getAllFundsOnce()
            val totalAssets = com.huaying.xstz.util.HoldingsCalculator.calculateTotalAssets(funds)
            val totalCost = com.huaying.xstz.util.HoldingsCalculator.calculateTotalCost(funds)
            val totalReturn = totalAssets - totalCost
            val returnRate = if (totalCost > 0) totalReturn / totalCost * 100 else 0.0
            
            val stockValue = com.huaying.xstz.util.HoldingsCalculator.calculateMarketValueByType(funds, AssetType.STOCK)
            val bondValue = com.huaying.xstz.util.HoldingsCalculator.calculateMarketValueByType(funds, AssetType.BOND)
            val commodityValue = com.huaying.xstz.util.HoldingsCalculator.calculateMarketValueByType(funds, AssetType.COMMODITY)
            val cashValue = com.huaying.xstz.util.HoldingsCalculator.calculateMarketValueByType(funds, AssetType.CASH)
            
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
            Log.e("FundRepository", "calculateCurrentSnapshot failed", e)
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
        // clearAllTables 禁止在事务内调用，必须放在外部
        database.clearAllTables()

        // 单事务批量导入，保证原子性与导入速度
        database.withTransaction {
            if (funds.isNotEmpty()) database.fundDao().insertFunds(funds)
            if (transactions.isNotEmpty()) database.transactionDao().insertTransactions(transactions)
            if (records.isNotEmpty()) database.netValueRecordDao().insertRecords(records)
        }
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
