package com.huaying.xstz.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.huaying.xstz.core.database.dao.HoldingDao
import com.huaying.xstz.core.database.dao.NavHistoryDao
import com.huaying.xstz.core.database.dao.OperationLogDao
import com.huaying.xstz.core.database.dao.TradeDao
import com.huaying.xstz.core.database.dao.TransactionDao
import com.huaying.xstz.core.database.entity.HoldingEntity
import com.huaying.xstz.core.database.entity.NavPointEntity
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity

/**
 * 应用数据库。
 *
 * 版本策略：
 *   这是全新重建的库（v1），与旧版 v6 不共享任何 schema——用户没有存量数据，
 *   也不需要迁移（ADR 已确认「无存量数据」）。
 *   但从第一次发版起，任何 schema 变更必须写 Migration 并配迁移测试，
 *   严禁 fallbackToDestructiveMigration。
 */
@Database(
    entities = [
        HoldingEntity::class,
        TradeEntity::class,
        NavPointEntity::class,
        OperationLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class XstzDatabase : RoomDatabase() {

    abstract fun holdingDao(): HoldingDao
    abstract fun tradeDao(): TradeDao
    abstract fun navHistoryDao(): NavHistoryDao
    abstract fun operationLogDao(): OperationLogDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "xstz.db"

        fun create(context: Context): XstzDatabase =
            Room.databaseBuilder(context, XstzDatabase::class.java, NAME)
                // 不提供 fallbackToDestructiveMigration：
                // 宁可崩溃暴露问题，也不要静默清库——用户资产数据丢了是不可接受的
                .build()
    }
}
