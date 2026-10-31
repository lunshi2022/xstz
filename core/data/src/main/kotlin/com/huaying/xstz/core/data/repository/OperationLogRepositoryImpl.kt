package com.huaying.xstz.core.data.repository

import com.huaying.xstz.core.database.dao.OperationLogDao
import com.huaying.xstz.core.domain.repository.OperationLogEntry
import com.huaying.xstz.core.domain.repository.OperationLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * 操作日志查询仓库。
 *
 * 只读 + 清理——写入分散在各业务仓库（addHolding/recordTrade/export...），
 * 由它们自行 insert。这里不提供写接口是刻意的：一旦开放，
 * "在哪一层记日志"的问题就会重新出现。
 */
class OperationLogRepositoryImpl @Inject constructor(
    private val dao: OperationLogDao,
) : OperationLogRepository {

    override fun observeRecent(limit: Int): Flow<List<OperationLogEntry>> =
        dao.observeRecent(limit).map { list ->
            list.map { it.toEntry() }
        }

    override suspend fun purgeBefore(before: Long): Int {
        val victims = dao.recent(Int.MAX_VALUE).count { it.createdAt < before }
        dao.deleteOlderThan(before)
        return victims
    }

    override suspend fun clear() = dao.clear()
}

private fun com.huaying.xstz.core.database.entity.OperationLogEntity.toEntry() =
    OperationLogEntry(
        id = id,
        action = action,
        detail = detail,
        createdAt = createdAt,
    )
