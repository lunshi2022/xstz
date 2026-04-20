package com.huaying.xstz.data.model

import com.huaying.xstz.data.entity.Fund
import com.huaying.xstz.data.entity.Transaction
import com.huaying.xstz.data.entity.NetValueRecord

/**
 * 数据导出模型
 * 用于应用数据的导入导出功能
 */
data class ExportData(
    val funds: List<Fund>,
    val transactions: List<Transaction>,
    val records: List<NetValueRecord>
)
