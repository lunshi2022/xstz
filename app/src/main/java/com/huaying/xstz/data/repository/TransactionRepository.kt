package com.huaying.xstz.data.repository

import com.huaying.xstz.data.dao.TransactionDao
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao
) {
    
    fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions()
    }
    
    fun getTransactionsByFund(fundId: Long): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByFund(fundId)
    }
    
    fun getTransactionsByFundCode(fundCode: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByFundCode(fundCode)
    }
    
    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByType(type)
    }
    
    suspend fun insertTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }
    
    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }
    
    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }
    
    suspend fun deleteAllTransactions() {
        transactionDao.deleteAllTransactions()
    }
}