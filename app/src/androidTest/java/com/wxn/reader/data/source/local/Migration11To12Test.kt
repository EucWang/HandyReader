package com.wxn.reader.data.source.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration_11_12 instrumentation 测试（v12 TXT 统一字节偏移方案，§4.5）。
 *
 * 验证 v11→v12 迁移正确，避免迁移失败导致升级变砖（项目无 `fallbackToDestructiveMigration`）。
 *
 * **覆盖**：
 * 1. schema 一致性（[runMigrationsAndValidate] 自动比对 12.json：列名/类型/DEFAULT/FK/索引/PK 全核对）
 * 2. `books.txtCharset` 列存在且为 nullable TEXT（无 DEFAULT，无 backfill）
 * 3. v11 存量 books 行数据完整保留（迁移不应擦除已有数据）
 * 4. 迁移后可对 txtCharset 做读写 UPDATE/SELECT（验证列可写）
 *
 * 运行：`gradlew.bat :app:connectedDebugAndroidTest`（需真机/模拟器）
 */
@RunWith(AndroidJUnit4::class)
class Migration11To12Test {

    private val dbName = "migration-11-12-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        openFactory = FrameworkSQLiteOpenHelperFactory(),
        specs = emptyList()
    )

    @Test
    fun migrate11To12_schemaConsistent() {
        // 1. 建 v11 库（按 11.json 自动建表），插入 1 行 books 测试数据
        //    v11 books 无 txtCharset 列（迁移前）
        helper.createDatabase(dbName, 11).apply {
            execSQL(
                """INSERT INTO books (id, uri, fileType, title, authors, wordCount, locator,
                       progression, deleted, rating, isFavorite, readingTime, scrollIndex, scrollOffset,
                       cachedDir, crc, importStatus, source, metaHlcL, metaHlcC, metaHlcDevice,
                       userHlcL, userHlcC, userHlcDevice, syncHlcL, syncHlcC, syncHlcDevice)
                   VALUES (1, 'uri', 'TXT', 'test_book', '', 0, '', 0.0, 0, 0.0, 0, 0, 0, 0,
                       '', 0, 0, '', 0, 0, '', 0, 0, '', 0, 0, '')"""
            )
            close()
        }

        // 2. 执行迁移 11→12 + 自动比对 12.json
        //    runMigrationsAndValidate 核对迁移后 schema 与 12.json 完全一致：
        //    列名/类型/DEFAULT/FK/索引/PK 任何不一致都会抛 IllegalStateException
        val db = helper.runMigrationsAndValidate(
            dbName, 12, true,
            AppDatabase.Migration_11_12
        )

        // 3. 验证 books 表 v11 存量行数据完整保留（迁移不应擦除 title 等已有数据）
        db.query("SELECT title FROM books WHERE id = 1").use {
            assertTrue("v11 books 行应保留", it.moveToFirst())
            assertTrue("title 应为 test_book", it.getString(0) == "test_book")
        }

        // 4. 验证 txtCharset 列存在且初始为 NULL（迁移前未回填，老书首次打开时由
        //    TxtTextParser.resolveCharsetName 现场探测后回填）
        db.query("SELECT txtCharset FROM books WHERE id = 1").use {
            assertTrue("查询应命中", it.moveToFirst())
            // 存量行的 txtCharset 应为 NULL（ALTER TABLE ADD COLUMN nullable 无 DEFAULT）
            assertTrue("txtCharset 列应存在且为 NULL", it.isNull(0))
        }

        // 5. 验证 txtCharset 列可写（回填路径 BookDao.updateTxtCharset 依赖此列可 UPDATE）
        db.execSQL("UPDATE books SET txtCharset = 'UTF-16LE' WHERE id = 1")
        db.query("SELECT txtCharset FROM books WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertTrue("txtCharset 应为 UTF-16LE", it.getString(0) == "UTF-16LE")
        }

        // 6. 验证 txtCharset 可清空回 NULL（边界：理论不会用到，但保持 nullable 语义完整）
        db.execSQL("UPDATE books SET txtCharset = NULL WHERE id = 1")
        db.query("SELECT txtCharset FROM books WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertTrue("txtCharset 应可清空回 NULL", it.isNull(0))
            assertFalse("txtCharset 不应为非空", !it.isNull(0))
        }

        db.close()
    }
}
