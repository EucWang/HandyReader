package com.wxn.reader.data.backup

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.wxn.base.util.Logger
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.FileTypeHashProjection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * contentHash 计算(SHA-256 全文件,独立于 CRC 路径)。
 *
 * 时机的演进:
 * - **2026-07-07 重构(方案 A)**: 扫描/导入路径的 hash 改由 [com.wxn.bookparser.impl.FileParserImpl]
 *   在解析阶段与 CRC 合并计算填入 `Book.contentHash`,插入后由
 *   [handlePotentialConflict] 做并发兜底去重。本类的 [ensureContentHash] 不再用于新插入路径。
 * - **保留 [ensureContentHash] 的实际调用方(2026-07-07 核查)**:
 *   (1) [EnsureContentHashWorker]: 老用户升级补算 + 顺便去重;
 *   (2) [BackupExporter.ensureAllContentHashes]: 备份前兜底。
 *
 *   ⚠️ 同步方案设计稿原计划"Reader ViewModel 打开老书时调",但实际未实施
 *   (grep 全库 `*ReaderViewModel*` 无 contentHash 调用)。老书补算完全依赖 (1)。
 *
 * ★ A+++ (2026-07-07) 核心修复——统一去重逻辑 [dedupeByHash]:
 *   修复前 [ensureContentHash] 预查/catch 分支、[handlePotentialConflict] 都存在同一 bug:
 *   "keepId == bookId 时不操作",漏 dedupe 已存在的更老活行。
 *   修复后统一扫描同 hash 所有活行,dedupe 非 keepId 的所有(双向 dedupe)。
 *
 * ★ 同步方案 v2.6 §4.6.1 / 一期 §3.4。
 */
@Singleton
class ContentHashCalculator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
) {

    sealed interface EnsureHashResult {
        data class Ok(val hash: String) : EnsureHashResult
        /** ★ A+++ 严重-1:被判为重复并 markDeduped,contentHash 未写入(NULL)。 */
        data object Deduped : EnsureHashResult
        data object Inaccessible : EnsureHashResult
        data object HashFailed : EnsureHashResult
    }

    /**
     * 补算单本书的 contentHash 并写库。已算过(非 null)直接返回 Ok。
     *
     * ★ A+++ 修复:统一去重委托 [dedupeByHash],返回 [EnsureHashResult.Deduped] 区分"算入 hash"和"被去重"。
     *   keepSelf=true 时 UPDATE hash(WHERE importStatus=0 防御极端并发)。
     */
    suspend fun ensureContentHash(bookId: Long): EnsureHashResult = withContext(Dispatchers.IO) {
        val proj: FileTypeHashProjection = bookDao.getContentHashAndFileType(bookId)
            ?: return@withContext EnsureHashResult.Inaccessible
        if (proj.contentHash != null) return@withContext EnsureHashResult.Ok(proj.contentHash)

        val uri = resolveBookUri(bookId) ?: return@withContext EnsureHashResult.Inaccessible
        val hash = try {
            sha256(uri)
        } catch (e: IOException) {
            Logger.w("ContentHash:hash failed bookId=$bookId: ${e.message}")
            return@withContext EnsureHashResult.HashFailed
        }

        // ★ A+++ 统一去重:扫描同 hash 所有活行(含自己),dedupe 非 keepId 的所有
        val keepSelf = dedupeByHash(hash, bookId)
        if (!keepSelf) {
            // 当前 bookId 被判为重复:已 markBookAsDeduped
            return@withContext EnsureHashResult.Deduped
        }

        // 自己是 keepId 或无冲突:UPDATE hash
        // ★ 严重-3:WHERE importStatus=0 防御——若 dedupeByHash 与 UPDATE 之间被并发 dedupe,UPDATE 0 行
        val updated = try {
            bookDao.updateContentHash(bookId, hash)
        } catch (e: CancellationException) {
            throw e  // 红线 #6:取消向上传播,不吞
        } catch (e: Exception) {
            0
        }
        if (updated == 0) {
            Logger.w("ContentHash:UPDATE 0 rows on bookId=$bookId (concurrent dedup)")
            return@withContext EnsureHashResult.Deduped
        }
        EnsureHashResult.Ok(hash)
    }

    /** 直接对 URI 算 SHA-256(不查库,导入路径用)。 */
    suspend fun computeHash(uri: Uri): EnsureHashResult = withContext(Dispatchers.IO) {
        try {
            EnsureHashResult.Ok(sha256(uri))
        } catch (e: IOException) {
            Logger.w("ContentHash:computeHash failed: ${e.message}")
            EnsureHashResult.HashFailed
        } catch (e: SecurityException) {
            Logger.w("ContentHash:computeHash inaccessible: ${e.message}")
            EnsureHashResult.Inaccessible
        }
    }

    /**
     * ★ 扫描/导入去重并发兜底(方案 A 双保险之二):
     * 在 [com.wxn.reader.domain.use_case.books.InsertBookUseCase] 插入后立即调用。
     * 自己已 insert + UPDATE hash(FileParserImpl 算填 + BookDao.insertBook 写入),
     * 内部委托给 [dedupeByHash] 扫描同 hash 所有活行(含自己),dedupe 非 keepId。
     *
     * 与 [ensureContentHash] 的区别:
     * - [ensureContentHash]: bookId 还没写入 hash(从 URI 算),会触发 UPDATE + dedupe
     * - [handlePotentialConflict]: bookId 已写入 hash,只做去重
     *
     * ★ A+++ 修复:复用 [dedupeByHash],修复"keepId == bookId 时漏 dedupe 已存在活行"。
     *
     * 并发安全:多本同书同时插入时,各自调用本方法,都会查到完整活行列表,
     * resolveKeepIdAmong 是 deterministic 的(同输入同输出),决断一致;
     * markBookAsDeduped 是幂等 UPDATE,重复调用无副作用。
     */
    suspend fun handlePotentialConflict(bookId: Long, hash: String) {
        withContext(Dispatchers.IO) { dedupeByHash(hash, bookId) }
    }

    /**
     * ★ A+++ 统一去重(2026-07-07 修复 ensureContentHash / handlePotentialConflict 的边角 bug)。
     *
     * 扫描同 hash 所有活行(含 [involvedBookId]),按 [BookDao.resolveKeepIdAmong] 三级
     * 优先级决断 keepId,然后 dedupe 非 keepId 的所有行(不再只 dedupe 调用方)。
     *
     * 修复前的 bug:3 处去重逻辑都只判断 "keepId != bookId 时 dedupe 自己",
     * 漏处理 "keepId == bookId 时已存在的更老活行",导致老用户升级时同 hash 多本都活着。
     *
     * @param involvedBookId 调用方 bookId。已写入 hash 时自动包含在 liveIds;
     *        未写入时(ensureContentHash 在 UPDATE 之前调用)手动补入。
     * @return true=involvedBookId 是 keepId 或无冲突;false=involvedBookId 被 dedupe。
     */
    @VisibleForTesting
    internal suspend fun dedupeByHash(hash: String, involvedBookId: Long): Boolean {
        val liveIds = bookDao.getActiveBookIdsByContentHash(hash)
        // 自己已写入 hash 时 liveIds 包含自己;未写入时不包含,需补上
        val allIds = if (involvedBookId in liveIds) liveIds else liveIds + involvedBookId
        if (allIds.size <= 1) return true  // 仅自己或无活行,无冲突

        val keepId = bookDao.resolveKeepIdAmong(allIds) ?: return true  // 理论不应发生,保守
        val toDedupe = allIds.filter { it != keepId }
        if (toDedupe.isEmpty()) return keepId == involvedBookId

        // ★ 严重-11:移除降级 forEach(批量失败时降级也必然失败,无意义)。
        //   失败由下次 Worker 调度兜底(KEEP policy + getActiveBookIds 仍查到 contentHash=NULL 的行)。
        try {
            bookDao.markBooksAsDeduped(toDedupe)
            toDedupe.forEach { id ->
                Logger.d("ContentHash:dedup bookId=$id keepId=$keepId hash=${hash.take(8)}")
            }
        } catch (e: CancellationException) {
            throw e  // 红线 #6:取消向上传播,不吞
        } catch (e: Exception) {
            Logger.w("ContentHash:markBooksAsDeduped failed (${toDedupe.size} ids): ${e.message}")
        }
        return keepId == involvedBookId
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) throw IOException("Cannot open input stream for $uri")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 通过 bookId 解析书文件 URI(从 BookEntity.uri 取;orphan 无文件返回 null)。 */
    private suspend fun resolveBookUri(bookId: Long): Uri? {
        val book = bookDao.getBookByIdIncludeDeleted(bookId) ?: return null
        val path = book.uri
        if (path.isBlank()) return null
        return runCatching { Uri.parse(path) }.getOrNull()
    }
}

/**
 * 备份前兜底补算结果汇总。
 */
sealed interface HashCheck {
    data object Ok : HashCheck
    data class Partial(val inaccessible: List<String>, val failed: List<String>) : HashCheck
    data object Failed : HashCheck
}
