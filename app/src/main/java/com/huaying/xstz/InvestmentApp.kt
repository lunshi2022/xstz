package com.huaying.xstz

import android.app.Application
import android.content.Context
import com.huaying.xstz.data.AppDatabase

class InvestmentApp : Application() {
    companion object {
        lateinit var context: Context
            private set
        
        lateinit var database: AppDatabase
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        database = AppDatabase.getDatabase(this)
    }
}
