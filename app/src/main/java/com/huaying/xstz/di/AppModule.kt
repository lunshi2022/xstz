package com.huaying.xstz.di

import android.content.Context
import com.huaying.xstz.data.AppDatabase
import com.huaying.xstz.data.PreferenceManager
import com.huaying.xstz.data.network.FundNetworkService
import com.huaying.xstz.data.network.TencentFundNetworkService
import com.huaying.xstz.data.repository.FundRepository
import com.huaying.xstz.data.repository.OperationLogRepository
import com.huaying.xstz.data.repository.TransactionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideFundNetworkService(): FundNetworkService {
        return TencentFundNetworkService()
    }

    @Provides
    @Singleton
    fun provideFundRepository(database: AppDatabase, networkService: FundNetworkService): FundRepository {
        return FundRepository(database, networkService)
    }

    @Provides
    @Singleton
    fun provideOperationLogRepository(database: AppDatabase): OperationLogRepository {
        return OperationLogRepository(database)
    }

    @Provides
    @Singleton
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideTransactionRepository(database: AppDatabase): TransactionRepository {
        return TransactionRepository(database.transactionDao())
    }
}
