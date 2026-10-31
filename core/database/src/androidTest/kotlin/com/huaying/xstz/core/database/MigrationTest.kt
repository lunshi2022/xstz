package com.huaying.xstz.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * schema 迁移测试（方案阶段 1 验收项）。
 *
 * ══════════════════════════════════════════════════════════════════
 * 为什么这也不能用 Robolectric
 *
 * [MigrationTestHelper] 需要用同一把钥匙建库，即 SQLCipher 的
 * `SupportOpenHelperFactory`。而 SQLCipher 依赖 native 库，
 * Robolectric 加载不了。所以迁移测试同样只能跑在设备上。
 *
 * 版本历史（schema JSON 已随 Room 导出）：
 *   v1 → v2  holdings 增加 `kind` 列（标的类型）
 *   v2 → v3  新建 net_value_snapshot 表（组合级每日快照）
 *
 * 这两次都是**加列/加表**，不改既有列，因此测试重点是：
 *   1. 迁移后 schema 与目标版本完全一致（Room 自动比对）
 *   2. 迁移**不丢数据**——旧行在新版本里仍可读出
 * ══════════════════════════════════════════════════════════════════
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration_test.db"
    private val key = ByteArray(32) { (it + 7).toByte() }

    companion object {
        init {
            System.loadLibrary("sqlcipher")
        }
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        XstzDatabase::class.java,
        emptyList(),
        // 关键：校验迁移时也用 SQLCipher 打开，否则会拿明文库去对密文 schema
        SupportOpenHelperFactory(key),
    )

    /**
     * v1 → v3 全路径，验证旧数据完整保留。
     *
     * v1 的 holdings 没有 `kind` 列。MIGRATION_1_2 用
     * `DEFAULT 'FUND'` 补默认值——这是**史实**：v1 只管理基金，
     * 每条从 v1 升上来的记录确实是基金。这条断言守住该语义。
     */
    @Test
    fun v1升到v3_保留既有数据并补出默认kind() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO holdings (code, name, type, shares_micros, cost_micros,
                    nav_micros, prev_nav_micros, target_ratio, nav_epoch_day,
                    sort_order, created_at, updated_at)
                VALUES ('000001', '老基金', 'FUND', 10000000, 15000000,
                    16000, 16000, 0.5, 20000, 0, 1, 1)
                """.trimIndent()
            )
            close()
        }

        // runMigrationsAndValidate 会执行全部迁移并在验证 schema 指纹后返回
        val db = helper.runMigrationsAndValidate(
            dbName, 3, true, XstzDatabase.MIGRATION_1_2, XstzDatabase.MIGRATION_2_3,
        )

        db.query("SELECT code, name, shares_micros, kind FROM holdings").use { c ->
            assertTrue("迁移后应仍有一条持仓", c.moveToFirst())
            assertEquals("000001", c.getString(0))
            assertEquals("老基金", c.getString(1))
            assertEquals(10000000L, c.getLong(2))
            // v1 记录升上来必须被标成 FUND，而不是空串或 UNKNOWN
            assertEquals("FUND", c.getString(3))
        }
        db.close()
    }

    /**
     * v2 → v3：新表必须存在且可写。
     *
     * net_value_snapshot 是派生数据（可从 holdings + trades + nav_history
     * 重算），所以迁移只建表不搬数据是正确的。但要确认表结构真的可用——
     * 主键是 epoch_day，重复插入应走 REPLACE 而非报错。
     */
    @Test
    fun v2升到v3_快照表可正常写入() {
        helper.createDatabase(dbName, 2).close()

        val db = helper.runMigrationsAndValidate(
            dbName, 3, true, XstzDatabase.MIGRATION_2_3,
        )

        db.execSQL(
            """
            INSERT OR REPLACE INTO net_value_snapshot
                (epoch_day, total_value_micros, principal_micros, day_profit_micros,
                 stock_value_micros, bond_value_micros, commodity_value_micros,
                 cash_value_micros, created_at)
            VALUES (20321, 100000000, 90000000, 1000000, 60000000, 20000000, 0, 20000000, 1)
            """.trimIndent()
        )

        db.query("SELECT total_value_micros, day_profit_micros FROM net_value_snapshot").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(100000000L, c.getLong(0))
            assertEquals(1000000L, c.getLong(1))
        }
        db.close()
    }

    /**
     * v1 → v2 单独验证：`kind` 列的默认值必须是 FUND。
     *
     * 与上面第一条的区别：这条只跑到 v2，确保 1→2 这一步自身正确，
     * 出问题时能立刻定位是哪一步坏的，不用在两个迁移里猜。
     */
    @Test
    fun v1升到v2_kind列默认值为FUND() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO holdings (code, name, type, shares_micros, cost_micros,
                    nav_micros, prev_nav_micros, target_ratio, nav_epoch_day,
                    sort_order, created_at, updated_at)
                VALUES ('110011', '另一支', 'FUND', 5000000, 7000000,
                    14000, 14000, 0.3, 20000, 1, 2, 2)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName, 2, true, XstzDatabase.MIGRATION_1_2,
        )
        db.query("SELECT kind FROM holdings WHERE code = '110011'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("FUND", c.getString(0))
        }
        db.close()
    }
}
