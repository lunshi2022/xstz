package com.huaying.xstz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.huaying.xstz.data.dao.FundDao
import com.huaying.xstz.data.converter.AssetTypeConverter
import com.huaying.xstz.data.converter.EncryptedDoubleConverter
import com.huaying.xstz.data.dao.NetValueRecordDao
import com.huaying.xstz.data.dao.OperationLogDao
import com.huaying.xstz.data.dao.TargetAllocationDao
import com.huaying.xstz.data.dao.TransactionDao
import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.NetValueRecord
import com.huaying.xstz.data.entity.OperationLog
import com.huaying.xstz.data.entity.TargetAllocation
import com.huaying.xstz.data.entity.Transaction

@Database(
    entities = [
        Fund::class,
        Transaction::class,
        NetValueRecord::class,
        TargetAllocation::class,
        OperationLog::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(EncryptedDoubleConverter::class, AssetTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fundDao(): FundDao
    abstract fun transactionDao(): TransactionDao
    abstract fun netValueRecordDao(): NetValueRecordDao
    abstract fun targetAllocationDao(): TargetAllocationDao
    abstract fun operationLogDao(): OperationLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Migration logic for version 1 -> 2
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create operation_logs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS operation_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operationType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        targetId INTEGER,
                        targetName TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """)
            }
        }
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "investment_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
