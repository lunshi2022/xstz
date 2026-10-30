package com.huaying.xstz.core.data.di

import android.content.Context
import com.huaying.xstz.core.data.repository.BackupRepositoryImpl
import com.huaying.xstz.core.data.repository.HoldingRepositoryImpl
import com.huaying.xstz.core.data.repository.NavRepositoryImpl
import com.huaying.xstz.core.data.repository.TradeRepositoryImpl
import com.huaying.xstz.core.data.settings.SettingsRepositoryImpl
import com.huaying.xstz.core.database.XstzDatabase
import com.huaying.xstz.core.database.dao.HoldingDao
import com.huaying.xstz.core.database.dao.NavHistoryDao
import com.huaying.xstz.core.database.dao.OperationLogDao
import com.huaying.xstz.core.database.dao.TradeDao
import com.huaying.xstz.core.database.dao.TransactionDao
import com.huaying.xstz.core.domain.repository.BackupRepository
import com.huaying.xstz.core.domain.repository.HoldingRepository
import com.huaying.xstz.core.domain.repository.NavRepository
import com.huaying.xstz.core.domain.repository.SettingsRepository
import com.huaying.xstz.core.domain.repository.TradeRepository
import com.huaying.xstz.core.network.CachedQuoteSource
import com.huaying.xstz.core.network.QuoteSource
import com.huaying.xstz.core.network.RemoteQuoteSource
import com.huaying.xstz.core.network.SampleQuoteSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖装配。
 *
 * 分两个 Module：
 *   * [DatabaseModule] 提供第三方/框架对象（Room、OkHttp）—— 用 @Provides
 *   * [RepositoryModule] 绑定接口到实现 —— 用 @Binds
 * 混在一起会让人分不清"哪些是可替换的实现"。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XstzDatabase =
        XstzDatabase.create(context)

    @Provides fun provideHoldingDao(db: XstzDatabase): HoldingDao = db.holdingDao()
    @Provides fun provideTradeDao(db: XstzDatabase): TradeDao = db.tradeDao()
    @Provides fun provideNavDao(db: XstzDatabase): NavHistoryDao = db.navHistoryDao()
    @Provides fun provideLogDao(db: XstzDatabase): OperationLogDao = db.operationLogDao()
    @Provides fun provideTransactionDao(db: XstzDatabase): TransactionDao = db.transactionDao()

    /**
     * 行情源降级链：Remote → 内存缓存 → 离线样例。
     * 这是产品口径要求的"任何网络环境都能完整演示"的落点。
     */
    @Provides
    @Singleton
    fun provideQuoteSource(): QuoteSource = CachedQuoteSource(
        remote = RemoteQuoteSource(),
        fallback = SampleQuoteSource(),
    )

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepositoryImpl(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHoldingRepository(impl: HoldingRepositoryImpl): HoldingRepository

    @Binds
    @Singleton
    abstract fun bindTradeRepository(impl: TradeRepositoryImpl): TradeRepository

    @Binds
    @Singleton
    abstract fun bindNavRepository(impl: NavRepositoryImpl): NavRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository
}
