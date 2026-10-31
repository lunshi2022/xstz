package com.huaying.xstz.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.huaying.xstz.core.database.entity.HoldingEntity
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * SQLCipher 整库加密的设备侧验收（方案阶段 1）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 必须跑在真机或模拟器上，原因：
 *   SQLCipher 的实现是 `libsqlcipher.so`，Robolectric 无法加载 native 库
 *   （实测抛 UnsatisfiedLinkError: nativeOpen）。所以"加密是否真的生效"
 *   与"SUM() 在密文库上是否正确"这两件事，JVM 测不了。
 *
 * 本文件覆盖方案 §阶段 1 的三条验收：
 *   1. SQLCipher 下 SUM() 查询结果正确 —— 这正是旧版 P0-2 的返修验证
 *   2. 落盘文件确实是密文（不是明文 SQLite）
 *   3. 口令错误时打不开（说明密钥真的参与了加密，而不是摆设）
 * ══════════════════════════════════════════════════════════════════
 *
 * 运行：`./gradlew :core:database:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherAggregationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dbName = "agg_test.db"
    private lateinit var db: XstzDatabase

    private val key = ByteArray(32) { (it + 1).toByte() }

    companion object {
        // 测试自己建库，没走 XstzDatabase.create()，所以这里要自己加载。
        // 放在 companion 的 init 里只执行一次；重复 loadLibrary 虽幂等，
        // 但每次 @Before 调一遍会污染日志。
        init {
            System.loadLibrary("sqlcipher")
        }
    }

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        db = openWith(key)
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        context.deleteDatabase(dbName)
    }

    private fun openWith(passphrase: ByteArray): XstzDatabase =
        Room.databaseBuilder(context, XstzDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()

    /**
     * 定点换算助手，避免手写下划线分组读错数量级。
     *
     * 直接用字面量写过 `1_5000_0000` 想表达 1500，实际是 15000——
     * Kotlin 的下划线只是可读性分隔符，不改变数值，而四位一分组
     * （中文习惯「万」进位）与三位一分组（英文千进位）极易混淆。
     * 这里统一用「金额 × 1e4 再取整」，量的含义在一处定义。
     */
    private fun micro(value: Double): Long = kotlin.math.round(value * 10_000.0).toLong()

    private fun holding(
        code: String,
        shares: Double,
        cost: Double,
        nav: Double,
        target: Double,
    ) = HoldingEntity(
        code = code,
        name = "标的$code",
        type = "FUND",
        kind = "FUND",
        sharesMicros = micro(shares),
        costMicros = micro(cost),
        navMicros = micro(nav),
        prevNavMicros = micro(nav),
        targetRatio = target,
        navEpochDay = 20_000,
    )

    /**
     * 验收 1：SQL 侧的聚合必须正确。
     *
     * 这是旧版 P0-2 的返修核心。旧实现把金额列做列级加密存成 TEXT，
     * `SUM(holdingQuantity * currentPrice)` 变成对密文字符串做算术，
     * SQLite 会静默返回 0 或垃圾值而不报错——这正是"定时炸弹"的含义：
     * 任何 DAO 调用都会拿到错误金额。
     *
     * 整库加密下这些列在 SQLite 眼里仍是普通 INTEGER/REAL，
     * 聚合在引擎内部完成，结果必须精确。
     */
    @Test
    fun 整库加密下_SUM_聚合结果精确() = runBlocking {
        val dao = db.holdingDao()
        dao.insert(holding("000001", shares = 1000.0, cost = 1500.0, nav = 1.6000, target = 0.4))
        dao.insert(holding("000002", shares = 2500.0, cost = 3000.0, nav = 3.2000, target = 0.35))
        dao.insert(holding("000003", shares = 500.0, cost = 800.0, nav = 0.9000, target = 0.25))

        // 份额 1000 + 2500 + 500 = 4000
        assertEquals(micro(4000.0), dao.sumSharesMicros())

        // 成本 1500 + 3000 + 800 = 5300
        assertEquals(micro(5300.0), dao.sumCostMicros())

        // 市值 = 份额 × 净值，逐项：
        //   1000 × 1.6000 = 1600
        //   2500 × 3.2000 = 8000
        //    500 × 0.9000 =  450
        // 合计 10050
        assertEquals(micro(10050.0), dao.sumMarketValueMicros())

        // target_ratio 是 REAL，浮点求和必须用 delta
        assertEquals(1.0, dao.sumTargetRatio(), 1e-9)
    }

    /**
     * 验收 2：SQLCipher 下 COALESCE(SUM(...), 0) 的空表行为不变。
     *
     * SUM 在空集合上返回 NULL，Room 会把它映射到非空 Long 而崩溃，
     * 所以 DAO 必须用 COALESCE 包住。这条测试守住"空表不炸"。
     */
    @Test
    fun 空表时聚合返回零而非崩溃() = runBlocking {
        val dao = db.holdingDao()
        assertEquals(0L, dao.sumSharesMicros())
        assertEquals(0L, dao.sumMarketValueMicros())
        assertEquals(0.0, dao.sumTargetRatio(), 1e-9)
    }

    /**
     * 验收 2b：落盘的必须是密文。
     *
     * 判据取文件头 16 字节。明文 SQLite 的头部固定是 `SQLite format 3\0`，
     * 而 SQLCipher 的头部是随机 salt——两者不可能相同。
     *
     * 这个断言的价值在于：如果哪天有人把 openHelperFactory 删掉，
     * 所有功能测试仍然全绿（Room 用框架 SQLite 也能跑），只有这条会红。
     */
    @Test
    fun 落盘文件头部不是明文SQLite标志() = runBlocking {
        db.holdingDao().insert(holding("000001", 100.0, 100.0, 1.0, 1.0))
        // 触发一次 WAL 落盘
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

        val file = context.getDatabasePath(dbName)
        assertTrue("库文件应存在：${file.absolutePath}", file.exists())
        assertTrue("库文件不应为空", file.length() > 0)

        val header = file.inputStream().use { it.readNBytes(16) }
        val plainHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        assertNotEquals(
            "文件头与明文 SQLite 相同，说明加密没有生效（openHelperFactory 可能被移除）",
            plainHeader.toList(),
            header.toList(),
        )
    }

    /**
     * 验收 3：口令错误必须打不开。
     *
     * 证明密钥真的参与了加密。若用错口令也能读出数据，
     * 说明 SQLCipher 处在某种降级/明文模式下。
     */
    @Test
    fun 错误口令无法打开数据库() = runBlocking {
        db.holdingDao().insert(holding("000001", 100.0, 100.0, 1.0, 1.0))
        db.close()

        val wrongKey = ByteArray(32) { (it + 99).toByte() }
        val opened = runCatching {
            val wrongDb = openWith(wrongKey)
            // Room 是懒打开，必须真正访问一次才会触发解密
            wrongDb.holdingDao().getAll()
            wrongDb.close()
        }
        assertTrue(
            "用错误口令竟然能读出数据，说明库并未真正加密",
            opened.isFailure,
        )
    }
}
