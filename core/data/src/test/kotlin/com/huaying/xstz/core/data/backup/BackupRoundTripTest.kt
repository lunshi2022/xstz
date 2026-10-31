package com.huaying.xstz.core.data.backup

import androidx.room.Room
import com.huaying.xstz.core.data.repository.BackupRepositoryImpl
import com.huaying.xstz.core.database.XstzDatabase
import com.huaying.xstz.core.database.entity.HoldingEntity
import com.huaying.xstz.core.database.entity.NavPointEntity
import com.huaying.xstz.core.database.entity.TradeEntity
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 备份「导出 → 清空 → 导入」完整往返测试。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么这组测试值得单独写
 *
 * 验收清单原有一条「跨设备备份恢复」，需要第二台设备才能做，一直挂着。
 * 但**跨设备并不是关键变量**——真正会出错的地方在于：恢复时自增 id 必然重新分配，
 * 如果流水/净值还按旧 id 关联，就会挂到错误的基金上。
 * 这个问题在本机就能完整复现，而且历史上确实踩过一次（见 ADR / TEST_PLAN）。
 *
 * 所以这里不模拟"另一台手机"，而是直接构造那个致命场景：
 *   导出时 id = 1,2  →  清空  →  再插一条占位持仓把自增序列推高  →  导入后 id 变成 4,5
 * 只要实现里有任何一处用 id 关联，下面「流水必须挂在正确的 code 上」就会失败。
 *
 * 用 Robolectric 而非 instrumentation：库层面建的是**内存明文库**，
 * 不经过 SQLCipher 的 native 层（那是迁移测试必须上设备的原因），
 * 因此可以在 JVM 上跑，纳入日常 CI，不必等设备。
 * ══════════════════════════════════════════════════════════════════
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private lateinit var db: XstzDatabase
    private lateinit var repo: BackupRepositoryImpl

    private val pass = "correct horse battery staple".toCharArray()

    /** 今日往前推若干天，保证落在导出时的「近三年」窗口内。 */
    private fun daysAgo(n: Long): Long = LocalDate.now().minusDays(n).toEpochDay()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            XstzDatabase::class.java,
        ).allowMainThreadQueries().build()

        repo = BackupRepositoryImpl(
            holdingDao = db.holdingDao(),
            tradeDao = db.tradeDao(),
            navDao = db.navHistoryDao(),
            logDao = db.operationLogDao(),
            transactionDao = db.transactionDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------- 测试数据 ----------------

    private suspend fun seedTwoHoldings(): Pair<Long, Long> {
        val fundId = db.holdingDao().insert(
            HoldingEntity(
                code = "519066",
                name = "汇添富蓝筹稳健",
                type = "STOCK",
                kind = "FUND",
                sharesMicros = 1_234_5678,
                costMicros = 2_0000_0000,
                navMicros = 1_6000,
                prevNavMicros = 1_5900,
                targetRatio = 0.4,
                navEpochDay = daysAgo(1),
                sortOrder = 0,
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_100_000,
            )
        )
        val stockId = db.holdingDao().insert(
            HoldingEntity(
                code = "600519",
                name = "贵州茅台",
                type = "STOCK",
                kind = "STOCK",
                sharesMicros = 500_0000,
                costMicros = 8000_0000,
                navMicros = 12_3400,
                prevNavMicros = 12_3000,
                targetRatio = 0.6,
                navEpochDay = daysAgo(2),
                sortOrder = 1,
                createdAt = 1_700_001_000_000,
                updatedAt = 1_700_001_100_000,
            )
        )

        // 两只持仓各 2 条流水，金额刻意不同：一旦 id 关联错位，金额会张冠李戴
        db.tradeDao().insertAll(
            listOf(
                TradeEntity(
                    holdingId = fundId, type = "BUY",
                    sharesMicros = 1_000_0000, navMicros = 1_5000,
                    feeMicros = 150_0000, amountMicros = 1_5000_0000,
                    epochDay = daysAgo(30), note = "申购", createdAt = 1_700_002_000_000,
                ),
                TradeEntity(
                    holdingId = fundId, type = "BUY",
                    sharesMicros = 234_5678, navMicros = 1_5500,
                    feeMicros = 35_0000, amountMicros = 363_5800,
                    epochDay = daysAgo(10), note = "", createdAt = 1_700_003_000_000,
                ),
                TradeEntity(
                    holdingId = stockId, type = "BUY",
                    sharesMicros = 300_0000, navMicros = 11_0000,
                    feeMicros = 0, amountMicros = 3300_0000,
                    epochDay = daysAgo(20), note = "建仓", createdAt = 1_700_004_000_000,
                ),
                TradeEntity(
                    holdingId = stockId, type = "SELL",
                    sharesMicros = 100_0000, navMicros = 12_0000,
                    feeMicros = 0, amountMicros = 1200_0000,
                    epochDay = daysAgo(5), note = "减仓", createdAt = 1_700_005_000_000,
                ),
            )
        )

        db.navHistoryDao().upsertAll(
            listOf(
                NavPointEntity(holdingId = fundId, epochDay = daysAgo(2), navMicros = 1_5800, accNavMicros = 2_1000),
                NavPointEntity(holdingId = fundId, epochDay = daysAgo(1), navMicros = 1_6000, accNavMicros = 2_1200),
                NavPointEntity(holdingId = stockId, epochDay = daysAgo(1), navMicros = 12_3400, accNavMicros = 12_3400),
            )
        )
        return fundId to stockId
    }

    private fun decode(payload: String): String {
        val raw = Base64.getDecoder().decode(payload)
        return String(BackupCrypto.decrypt(raw, pass), Charsets.UTF_8)
    }

    /** 去掉随机化的时间戳，便于比较两次导出的内容是否等价。 */
    private fun stable(json: String): String =
        json.replace(Regex("\"exportedAt\":\\d+"), "\"exportedAt\":0")

    // ---------------- 核心：往返一致性 ----------------

    @Test
    fun `导出后清空再导入，数据完整回到原样`() = runTest {
        val (oldFundId, oldStockId) = seedTwoHoldings()
        val payload = repo.export(pass)

        // 清空，并把自增序列推高，确保导入拿到的新 id 与备份时必然不同
        db.tradeDao().deleteAll()
        db.navHistoryDao().deleteAll()
        db.holdingDao().deleteAll()
        val filler = db.holdingDao().insert(
            HoldingEntity(code = "999999", name = "占位", type = "CASH", kind = "CASH")
        )
        db.holdingDao().deleteById(filler)

        val result = repo.import(payload, pass)
        assertEquals(2, result.holdings)
        assertEquals(4, result.trades)
        assertEquals(3, result.navPoints)

        val holdings = db.holdingDao().getAll()
        assertEquals("持仓数量应恢复为 2", 2, holdings.size)

        val newFund = holdings.single { it.code == "519066" }
        val newStock = holdings.single { it.code == "600519" }

        // 这是本测试的核心断言：id 必须被重新分配，否则下面的映射断言等于没测
        assertNotEquals("恢复后 id 应重新分配，才符合真实场景", oldFundId, newFund.id)
        assertNotEquals(oldStockId, newStock.id)

        // 标量字段逐个核对（漏一个就说明某个 DTO 字段没被搬过去）
        assertEquals("汇添富蓝筹稳健", newFund.name)
        assertEquals("STOCK", newFund.type)
        assertEquals("FUND", newFund.kind)
        assertEquals(1_234_5678, newFund.sharesMicros)
        assertEquals(2_0000_0000, newFund.costMicros)
        assertEquals(1_6000, newFund.navMicros)
        assertEquals(1_5900, newFund.prevNavMicros)
        assertEquals(0.4, newFund.targetRatio, 1e-9)
        assertEquals(daysAgo(1), newFund.navEpochDay)
        assertEquals(0, newFund.sortOrder)
        assertEquals(1_700_000_000_000, newFund.createdAt)

        // 流水必须挂在**正确的持仓**上：按 code 归组后金额要能对上
        val fundTrades = db.tradeDao().getByHolding(newFund.id)
        val stockTrades = db.tradeDao().getByHolding(newStock.id)
        assertEquals("519066 应有 2 条流水", 2, fundTrades.size)
        assertEquals("600519 应有 2 条流水", 2, stockTrades.size)

        assertEquals(
            listOf(1_5000_0000L, 363_5800L),
            fundTrades.sortedBy { it.epochDay }.map { it.amountMicros },
        )
        assertEquals(
            listOf(3300_0000L, 1200_0000L),
            stockTrades.sortedBy { it.epochDay }.map { it.amountMicros },
        )

        // 净值点同理
        val fundNavs = db.navHistoryDao().all(newFund.id)
        val stockNavs = db.navHistoryDao().all(newStock.id)
        assertEquals(2, fundNavs.size)
        assertEquals(1, stockNavs.size)
        assertEquals(1_6000, fundNavs.single { it.epochDay == daysAgo(1) }.navMicros)
        assertEquals(12_3400, stockNavs.single().navMicros)
    }

    @Test
    fun `再次导出与首次导出内容等价`() = runTest {
        seedTwoHoldings()
        val first = repo.export(pass)

        db.tradeDao().deleteAll()
        db.navHistoryDao().deleteAll()
        db.holdingDao().deleteAll()
        repo.import(first, pass)

        val second = repo.export(pass)
        assertEquals(
            "往返后再次导出应与首次导出逐字段一致（exportedAt 除外）",
            stable(decode(first)),
            stable(decode(second)),
        )
    }

    @Test
    fun `导入是整体替换而不是合并`() = runTest {
        seedTwoHoldings()
        val payload = repo.export(pass)

        db.tradeDao().deleteAll()
        db.navHistoryDao().deleteAll()
        db.holdingDao().deleteAll()
        // 导入前先塞一条备份里没有的持仓
        db.holdingDao().insert(
            HoldingEntity(code = "000001", name = "华夏成长", type = "STOCK", kind = "STOCK")
        )

        repo.import(payload, pass)

        val codes = db.holdingDao().getAll().map { it.code }.toSet()
        assertEquals(setOf("519066", "600519"), codes)
        assertTrue("导入前的持仓应被清除，不能残留", "000001" !in codes)
    }

    @Test
    fun `空库导出再导入不报错`() = runTest {
        val payload = repo.export(pass)
        val result = repo.import(payload, pass)
        assertEquals(0, result.holdings)
        assertEquals(0, result.trades)
        assertEquals(0, result.navPoints)
        assertTrue(db.holdingDao().getAll().isEmpty())
    }

    // ---------------- 失败路径 ----------------

    @Test
    fun `口令错误时导入失败且不清空现有数据`() = runTest {
        seedTwoHoldings()
        val payload = repo.export(pass)

        val before = db.holdingDao().getAll().map { it.code }.toSet()

        try {
            repo.import(payload, "wrong passphrase".toCharArray())
            fail("错误口令不应解密成功")
        } catch (e: Exception) {
            // 界面上这条消息是直接拼给用户看的（SettingsViewModel: "恢复失败：${it.message}"），
            // 所以断言的是**消息内容可读**，而不是某个特定异常类型。
            // 口令错误是用户最容易犯的错，绝不能让 "AEADBadTagException" 这种字样漏到界面上。
            val msg = e.message.orEmpty()
            assertTrue("口令错误必须给出可读中文提示，实际=[$msg]", msg.contains("口令"))
            assertTrue("提示里不应出现底层异常术语，实际=[$msg]", !msg.contains("Exception"))
        }

        assertEquals("解密失败必须发生在清库之前", before, db.holdingDao().getAll().map { it.code }.toSet())
        assertEquals(4, db.tradeDao().getAll().size)
    }

    @Test
    fun `拒绝来自更高版本的备份`() = runTest {
        val future = """
            {"schemaVersion":99,"exportedAt":1,"appVersion":"9.9.9",
             "holdings":[],"trades":[],"navPoints":[]}
        """.trimIndent()
        val payload = Base64.getEncoder().encodeToString(BackupCrypto.encrypt(future.toByteArray(), pass))

        try {
            repo.import(payload, pass)
            fail("更高版本的备份应被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue("提示应告诉用户升级，而不是笼统报错：${e.message}", e.message!!.contains("升级"))
        }
    }

    @Test
    fun `非 Base64 内容给出可读错误`() = runTest {
        try {
            repo.import("这不是备份文件", pass)
            fail("非法内容应被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue("错误信息应可读：${e.message}", e.message!!.contains("Base64"))
        }
    }

    // ---------------- 审计日志 ----------------

    @Test
    fun `导出与导入都会留下审计日志`() = runTest {
        seedTwoHoldings()
        val payload = repo.export(pass)
        assertEquals("BACKUP_EXPORT", db.operationLogDao().recent(10).first().action)

        db.tradeDao().deleteAll()
        db.navHistoryDao().deleteAll()
        db.holdingDao().deleteAll()
        repo.import(payload, pass)

        val actions = db.operationLogDao().all().map { it.action }
        assertTrue("应记录导出", "BACKUP_EXPORT" in actions)
        assertTrue("应记录导入", "BACKUP_IMPORT" in actions)
    }

    @Test
    fun `lastBackupTime 取最近一次导出时间`() = runTest {
        assertEquals(0L, repo.lastBackupTime())
        repo.export(pass)
        val t = repo.lastBackupTime()
        assertTrue("导出后应能读到时间戳", t > 0)
    }
}
