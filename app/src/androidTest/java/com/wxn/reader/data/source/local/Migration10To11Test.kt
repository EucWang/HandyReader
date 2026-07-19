package com.wxn.reader.data.source.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration_10_11 instrumentation 测试（P-CRASH-3）。
 *
 * per-book 功能（v11）上线前必修：验证 v10→v11 迁移正确，避免迁移失败导致升级变砖。
 *
 * **覆盖**：
 * 1. schema 一致性（[runMigrationsAndValidate] 自动比对 11.json：列名/类型/DEFAULT/FK/索引/PK 全核对）
 * 2. 存量 reader_theme_configs 行的对齐值不被篡改（userTextAlign DEFAULT 4 = Justify，非 1）
 * 3. per_book_meta / per_book_theme_overrides 两表迁移后可写（结构正确）
 *
 * **未覆盖**（接受）：
 * - 真实海量数据迁移（测试用空表+1行模拟）
 * - 多版本连续迁移 v7→...→v11（由 Room 自动链接各 Migration_X_Y 保证，单步测试足以）
 *
 * 运行：`gradlew.bat :app:connectedDebugAndroidTest`（需真机/模拟器）
 */
@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        openFactory = FrameworkSQLiteOpenHelperFactory(),
        specs = emptyList()
    )

    @Test
    fun migrate10To11_schemaConsistent() {
        // 1. 建 v10 库（按 10.json 自动建表，含 books 等所有 v10 表），插入 1 行 reader_theme_configs 测试数据
        //    v10 reader_theme_configs 无 userTextAlign/forceAlignOverride 列（17 列）
        helper.createDatabase(dbName, 10).apply {
            execSQL(
                """INSERT INTO reader_theme_configs
                   (themeId, backgroundColor, textColor, backgroundImage, font, fontVariant,
                    fontSize, lineHeight, letterSpacing, paragraphIndent, paragraphSpacing,
                    pageHorizontalMargins, pageVerticalMargins, titleSize, titleTopSpacing,
                    titleBottomSpacing, updatedAt)
                   VALUES ('test_theme', 0, 0, '', 'sans_serif', 'regular',
                    1.0, 1.5, 0.0, 2.0, 0.6, 1.5, 1.2, 1.0, 18.0, 15.0, 0)"""
            )
            close()
        }

        // 2. 执行迁移 10→11 + 自动比对 11.json
        //    runMigrationsAndValidate 会核对迁移后 schema 与 11.json 完全一致：
        //    列名/类型/DEFAULT/FK/索引/PK 任何不一致都会抛 IllegalStateException
        //    Room 2.6.1 签名：(name, version, validateDroppedTables, vararg migrations)
        val db = helper.runMigrationsAndValidate(
            dbName, 11, true,
            AppDatabase.Migration_10_11
        )

        // 3. 验证 reader_theme_configs 新列 DEFAULT 正确（R2-❶：误用 DEFAULT 1 会篡改存量用户对齐为 Left）
        //    存量行未显式设值 → 走 ALTER TABLE ADD COLUMN 的 DEFAULT
        db.query(
            "SELECT userTextAlign, forceAlignOverride FROM reader_theme_configs WHERE themeId='test_theme'"
        ).use {
            assert(it.moveToFirst()) { "存量行应存在" }
            assert(it.getInt(0) == 4) {
                "userTextAlign DEFAULT 应为 4(Justify)，实际 ${it.getInt(0)}（若为 1 则会篡改用户对齐）"
            }
            assert(it.getInt(1) == 0) {
                "forceAlignOverride DEFAULT 应为 0(false)，实际 ${it.getInt(1)}"
            }
        }

        // 4. 验证 per_book_meta 表迁移后可写（结构由 11.json 自动校验，此处验证 DEFAULT 与 FK）
        //    需先插入 books 父行（FK 约束：per_book_meta.bookId → books.id）
        //    books 表 NOT NULL 列较多，仅填 FK 所需的 id + 少量必填列（其余 NOT NULL 列给默认值）
        db.execSQL(
            """INSERT INTO books (id, uri, fileType, title, authors, wordCount, locator,
                   progression, deleted, rating, isFavorite, readingTime, scrollIndex, scrollOffset,
                   cachedDir, crc, importStatus, source, metaHlcL, metaHlcC, metaHlcDevice,
                   userHlcL, userHlcC, userHlcDevice, syncHlcL, syncHlcC, syncHlcDevice)
               VALUES (1, 'uri', 'TXT', 'test_book', '', 0, '', 0.0, 0, 0.0, 0, 0, 0, 0,
                   '', 0, 0, '', 0, 0, '', 0, 0, '', 0, 0, '')"""
        )
        db.execSQL(
            """INSERT INTO per_book_meta (bookId, overrideEnabled, selectedThemeId, createdAt, updatedAt)
               VALUES (1, 0, NULL, 0, 0)"""
        )

        // 5. 验证 per_book_theme_overrides 表迁移后可写（其余字段走 DEFAULT，验证 DEFAULT 子句可用）
        db.execSQL(
            """INSERT INTO per_book_theme_overrides
               (bookId, themeId, createdAt, updatedAt)
               VALUES (1, 't1', 0, 0)"""
        )

        db.close()
    }
}
