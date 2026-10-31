package com.huaying.xstz.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.huaying.xstz.core.database.dao.HoldingDao
import com.huaying.xstz.core.database.dao.NavHistoryDao
import com.huaying.xstz.core.database.dao.NetValueSnapshotDao
import com.huaying.xstz.core.database.dao.OperationLogDao
import com.huaying.xstz.core.database.dao.TradeDao
import com.huaying.xstz.core.database.dao.TransactionDao
import com.huaying.xstz.core.database.entity.HoldingEntity
import com.huaying.xstz.core.database.entity.NavPointEntity
import com.huaying.xstz.core.database.entity.NetValueSnapshotEntity
import com.huaying.xstz.core.database.entity.OperationLogEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
        NetValueSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class XstzDatabase : RoomDatabase() {

    abstract fun holdingDao(): HoldingDao
    abstract fun tradeDao(): TradeDao
    abstract fun navHistoryDao(): NavHistoryDao
    abstract fun operationLogDao(): OperationLogDao
    abstract fun transactionDao(): TransactionDao
    abstract fun netValueSnapshotDao(): NetValueSnapshotDao

    companion object {
        const val NAME = "xstz.db"

        /**
         * v1 → v2：holdings 增加 `kind` 列（标的类型：A股/ETF/债券/基金/现金）。
         *
         * 默认 'FUND' 是**史实**而非兜底猜测：v1 版本只管理基金，
         * 从 v1 升级上来的每一条记录确实都是基金。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE holdings ADD COLUMN kind TEXT NOT NULL DEFAULT 'FUND'"
                )
            }
        }

        /**
         * v2 → v3：新建组合级每日快照表。
         *
         * 这是方案 §5.1「净值记录」的落地，也是收益走势图与日历视图的唯一数据源。
         * 只建表不搬数据：这张表是**派生数据**，可以从 holdings + trades + nav_history
         * 重算出来（见 SnapshotBuilder），历史行由首次计算时补齐。
         *
         * 字段与实体的对应：
         *   total_value_micros    总资产
         *   principal_micros      本金
         *   day_profit_micros     当日盈亏（独立存，不用差值法，见实体注释）
         *   {stock,bond,commodity,cash}_value_micros  四类资产市值
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `net_value_snapshot` (
                        `epoch_day` INTEGER NOT NULL,
                        `total_value_micros` INTEGER NOT NULL,
                        `principal_micros` INTEGER NOT NULL,
                        `day_profit_micros` INTEGER NOT NULL,
                        `stock_value_micros` INTEGER NOT NULL DEFAULT 0,
                        `bond_value_micros` INTEGER NOT NULL DEFAULT 0,
                        `commodity_value_micros` INTEGER NOT NULL DEFAULT 0,
                        `cash_value_micros` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`epoch_day`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * 建库入口。
         *
         * 加密方式：SQLCipher 整库加密，口令由 [DatabaseKeyProvider] 提供
         * （Keystore 包裹的随机 DEK，见该文件的选型说明）。
         *
         * 为什么整库加密而非列级加密：方案 P0-2 已记录——旧版用
         * `EncryptedDoubleConverter` 把金额转成密文 TEXT，导致
         * `SUM(holdingQuantity * currentPrice)` 在 SQLite 侧对密文做算术，
         * 结果必然无效，只能靠应用层重算规避（那三个 DAO 方法是定时炸弹）。
         * 整库加密下 SQL 聚合、索引、ORDER BY 全部照常工作。
         *
         * `SupportOpenHelperFactory` 接管后 DAO 与实体代码零改动。
         */
        fun create(context: Context): XstzDatabase {
            // 必须在任何 SQLCipher 调用之前显式加载 native 库。
            // 新版 sqlcipher-android 与旧版 android-database-sqlcipher 都不做
            // 自动加载（两版都已用 javap 确认类里没有 loadLibrary/LoadLibs）。
            // 漏了这一步的报错是运行期的：
            //   UnsatisfiedLinkError: No implementation found for
            //   net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen
            // 且编译期完全无感，所以放在这里而不是散落在调用方。
            System.loadLibrary("sqlcipher")

            return Room.databaseBuilder(context, XstzDatabase::class.java, NAME)
                // openHelperFactory 必须在 build() 前设置；不指定的话
                // Room 会退回框架自带 SQLite，建出来的库是**明文**且不会报错——
                // 这是最容易踩的坑：功能看起来完全正常，只是没有加密。
                .openHelperFactory(
                    SupportOpenHelperFactory(DatabaseKeyProvider.passphrase(context))
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // 不提供 fallbackToDestructiveMigration：
                // 宁可崩溃暴露问题，也不要静默清库——用户资产数据丢了是不可接受的
                .build()
        }
    }
}
