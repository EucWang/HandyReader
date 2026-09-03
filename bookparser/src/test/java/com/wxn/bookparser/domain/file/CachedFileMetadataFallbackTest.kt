package com.wxn.bookparser.domain.file

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * CachedFile 元数据查询三级降级阶梯单测（方案 2026-09-03-plan-import-metadata-query-fallback.md §4.1）。
 *
 * 用 Robolectric 桩 provider 模拟故障设备 DocumentsProvider：按投影内容编程化抛异常（模拟
 * "Invalid column last_modifed" 类投影校验炸裂）或返回数据；openFile 返回真实 fd 以断言
 * statSize 兜底通道（审查 F-R1-5）。@Config 钉 SDK 规避 targetSdk=36 > maxSdk=35 初始化阻塞。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedFileMetadataFallbackTest {

    companion object {
        private const val AUTHORITY = "stub.docs"
        private const val NAME_COL = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        private const val SIZE_COL = DocumentsContract.Document.COLUMN_SIZE
        private const val LAST_MODIFIED_COL = DocumentsContract.Document.COLUMN_LAST_MODIFIED
        private const val MIME_COL = DocumentsContract.Document.COLUMN_MIME_TYPE
        private const val DOC_ID_COL = DocumentsContract.Document.COLUMN_DOCUMENT_ID
        private const val DOC_URI = "content://$AUTHORITY/document/primary:Books/book.epub"
        private const val DIR_URI = "content://$AUTHORITY/tree/primary:Books/document/primary:Books"
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var provider: StubDocumentsProvider
    private lateinit var backingFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        backingFile = tmp.newFile("backing.epub").apply {
            writeBytes(ByteArray(2048) { (it % 251).toByte() })
        }
        provider = StubDocumentsProvider(openFileTarget = backingFile)
        // Robolectric 4.14 无旧版静态 registerProvider：手动 attachInfo + registerProviderInternal
        // （registerProviderInternal 仅写入 provider 映射，attachInfo 负责绑定 context/authority 并回调 onCreate）
        val providerInfo = android.content.pm.ProviderInfo().apply { authority = AUTHORITY }
        provider.attachInfo(context, providerInfo)
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
    }

    private fun contentUri(): Uri = Uri.parse(DOC_URI)

    private fun importStyleBuilder() = CachedFileBuilder(
        name = "book.epub",
        path = "/storage/emulated/0/Books/book.epub",
        isDirectory = false
    )

    // ── T1：L1 健康路径（回归基线：健康设备行为与改前一致） ──

    @Test
    fun `T1 healthy provider returns real values via single query`() {
        val file = CachedFile(context, contentUri(), importStyleBuilder())

        assertEquals(123456L, file.size)
        assertEquals(1700000000000L, file.lastModified)
        assertEquals("book.epub", file.name)
        assertEquals(false, file.isDirectory)
        assertEquals(1, provider.queryCount.get())
        assertEquals(listOf(listOf(SIZE_COL, LAST_MODIFIED_COL)), provider.capturedProjections)
    }

    @Test
    fun `T1b healthy provider no builder resolves all four fields`() {
        val file = CachedFile(context, contentUri())

        assertEquals("book.epub", file.name)
        assertEquals(123456L, file.size)
        assertEquals(1700000000000L, file.lastModified)
        assertEquals(false, file.isDirectory)
        assertEquals(1, provider.queryCount.get())
    }

    // ── T2：L1 毒列炸裂 → L2 最小投影重试 + statSize 兜底（BookHelper 型，无 builder） ──

    @Test
    fun `T2 poisoned size and last_modified degrade to minimal projection plus statSize`() {
        provider.failProjectionIfContains = setOf(SIZE_COL, LAST_MODIFIED_COL)

        val file = CachedFile(context, contentUri())

        assertEquals("book.epub", file.name)
        assertEquals(backingFile.length(), file.size)
        assertEquals(0L, file.lastModified)
        assertEquals(2, provider.queryCount.get())
        assertEquals(listOf(NAME_COL), provider.capturedProjections[1])
    }

    // ── T2b：L2 空交集跳过（导入型 builder 已含 name+isDirectory，F-R1-3） ──

    @Test
    fun `T2b import style builder skips L2 when safe intersection empty`() {
        provider.failProjectionIfContains = setOf(SIZE_COL, LAST_MODIFIED_COL)

        val file = CachedFile(context, contentUri(), importStyleBuilder())

        assertEquals("book.epub", file.name)
        assertEquals(backingFile.length(), file.size)
        assertEquals(0L, file.lastModified)
        assertEquals(1, provider.queryCount.get())
    }

    // ── T3：全通道失败 → 默认值兜底，不抛 ──

    @Test
    fun `T3 all queries and openFile failed falls back to defaults without throwing`() {
        provider.failAll = true
        provider.failExceptionFactory = { IllegalArgumentException("Invalid column last_modifed") }
        provider.openFileFail = true

        val file = CachedFile(context, contentUri(), importStyleBuilder())

        assertEquals("book.epub", file.name)
        assertEquals(0L, file.size)
        assertEquals(0L, file.lastModified)
        assertEquals(false, file.isDirectory)
    }

    // ── T4：file:// URI 走 File.length() 特判（审查 F-R1-2，存量修复验证） ──

    @Test
    fun `T4 file uri resolves size from real file length`() {
        val localFile = tmp.newFile("local-book.txt").apply {
            writeBytes(ByteArray(4096) { 'a'.code.toByte() })
        }

        val file = CachedFile(context, Uri.fromFile(localFile))

        assertEquals(4096L, file.size)
        assertEquals(localFile.length(), file.size)
    }

    // ── T5：catch 广度（门禁批复①）：SecurityException/IllegalState/NPE/RuntimeException 均进降级流 ──

    @Test
    fun `T5 non IAE provider exceptions also degrade instead of propagating`() {
        val exceptionTypes = listOf(
            { SecurityException("permission denied") },
            { IllegalStateException("cursor window could not be initialized") },
            { NullPointerException("sloppy third party provider") },
            { RuntimeException("Unknown exception code: 999") },
        )
        for (factory in exceptionTypes) {
            provider.reset()
            provider.failAll = true
            provider.failExceptionFactory = factory
            provider.openFileFail = true

            val file = CachedFile(context, contentUri(), importStyleBuilder())

            assertEquals("degradation must not throw for ${factory().javaClass.simpleName}", "book.epub", file.name)
            assertEquals(0L, file.size)
        }
    }

    // ── T6：listFiles 降级（防御性加固，F-R1-4：子项 builder 不固化占位值） ──

    @Test
    fun `T6 listFiles degrades to minimal projection and children do not fixate placeholder values`() {
        val dir = CachedFile(
            context,
            Uri.parse(DIR_URI),
            CachedFileBuilder(name = "Books", path = "/storage/emulated/0/Books", isDirectory = true)
        )
        provider.failProjectionIfContains = setOf(SIZE_COL, LAST_MODIFIED_COL, MIME_COL)
        provider.children = listOf(
            StubDocumentsProvider.Row(name = "a.epub", documentId = "primary:Books/a.epub"),
            StubDocumentsProvider.Row(name = "b.mobi", documentId = "primary:Books/b.mobi"),
        )

        val children = dir.listFiles()

        assertEquals(2, children.size)
        // 投影记录序：[0]=canAccess(null 投影)、[1]=L1 五列（抛）、[2]=L2 最小投影
        assertEquals(listOf(NAME_COL, DOC_ID_COL), provider.capturedProjections[2])
        // 子项 size/lastModified/mime 未获得 → builder 必须为 null → 访问 .size 走子项自身
        // statSize 兜底拿到真实大小，而不是被 builder 固化为 0（F-R1-4）
        assertEquals(backingFile.length(), children[0].size)
        assertEquals(backingFile.length(), children[1].size)
        assertEquals(0L, children[0].lastModified)
    }

    @Test
    fun `T6b listFiles total failure returns collected result without throwing`() {
        val dir = CachedFile(
            context,
            Uri.parse(DIR_URI),
            CachedFileBuilder(name = "Books", path = "/storage/emulated/0/Books", isDirectory = true)
        )
        provider.failAll = true

        val children = dir.listFiles()

        assertTrue(children.isEmpty())
    }

    // ── T7：builder 四字段全给 → 零查询短路（现状语义保持） ──

    @Test
    fun `T7 fully provided builder short circuits without any query`() {
        val file = CachedFile(
            context,
            contentUri(),
            CachedFileBuilder(name = "x.epub", path = "/x", size = 42L, lastModified = 7L, isDirectory = false)
        )

        assertEquals(42L, file.size)
        assertEquals(7L, file.lastModified)
        assertEquals("x.epub", file.name)
        assertEquals(false, file.isDirectory)
        assertEquals(0, provider.queryCount.get())
    }

    // ── T8：builder 部分（导入路径现状）→ L1 投影恰为 [size, last_modified] ──

    @Test
    fun `T8 partial builder L1 projection matches legacy behaviour`() {
        val file = CachedFile(context, contentUri(), importStyleBuilder())

        file.size
        assertEquals(listOf(listOf(SIZE_COL, LAST_MODIFIED_COL)), provider.capturedProjections)
        assertEquals(1, provider.queryCount.get())
    }

    /** 桩 DocumentsProvider：按投影内容模拟故障设备行为，openFile 返回真实 fd。 */
    private class StubDocumentsProvider(
        private val openFileTarget: File?
    ) : android.content.ContentProvider() {

        data class Row(val name: String, val documentId: String)

        var displayName: String = "book.epub"
        var size: Long = 123456L
        var lastModified: Long = 1700000000000L
        var mimeType: String = "application/epub+zip"

        /** 命中即抛异常的投影列集合（模拟 provider 投影映射缺失这些列） */
        var failProjectionIfContains: Set<String> = emptySet()

        /** 无视投影全部抛异常（模拟 provider 整体不可用） */
        var failAll: Boolean = false
        var failExceptionFactory: () -> Exception = { IllegalArgumentException("Invalid column last_modifed") }
        var openFileFail: Boolean = false

        /** listFiles 子项数据（T6） */
        var children: List<Row> = emptyList()

        val queryCount = AtomicInteger()
        val capturedProjections = mutableListOf<List<String>>()

        fun reset() {
            failAll = false
            failProjectionIfContains = emptySet()
            openFileFail = false
            queryCount.set(0)
            capturedProjections.clear()
            children = emptyList()
        }

        override fun onCreate(): Boolean = true
        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? {
            queryCount.incrementAndGet()
            capturedProjections.add(projection?.toList() ?: emptyList())
            if (failAll) throw failExceptionFactory()
            projection?.forEach { col ->
                if (col in failProjectionIfContains) throw failExceptionFactory()
            }

            val columns = projection?.toList() ?: listOf(NAME_COL, SIZE_COL, LAST_MODIFIED_COL, MIME_COL)
            val cursor = MatrixCursor(columns.toTypedArray())
            if (children.isEmpty()) {
                cursor.addRow(columns.map { valueOf(it) })
            } else {
                children.forEach { row ->
                    cursor.addRow(columns.map { col -> childValueOf(col, row) })
                }
            }
            return cursor
        }

        private fun valueOf(col: String): Any? = when (col) {
            NAME_COL -> displayName
            SIZE_COL -> size
            LAST_MODIFIED_COL -> lastModified
            MIME_COL -> mimeType
            DOC_ID_COL -> "primary:Books/book.epub"
            else -> null
        }

        private fun childValueOf(col: String, row: Row): Any? = when (col) {
            NAME_COL -> row.name
            DOC_ID_COL -> row.documentId
            else -> null
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (openFileFail) throw IllegalStateException("openFile not supported")
            val target = openFileTarget ?: throw java.io.FileNotFoundException("no backing file")
            return ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: android.content.ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: android.content.ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }
}
