package com.wxn.reader.data.backup

import com.wxn.reader.data.dto.BookEntity

/**
 * 备份选书过滤器：决定一本书是否应进入备份 ZIP。
 *
 * 当前规则：
 *   1. 失败占位行（importStatus != 0）不参与备份。
 *      背景：源文件损坏（截断的 EPUB/MOBI）会导致 native 解析返回 null，
 *      HomeViewModel 随后插入占位行（crc=0、元数据全空、importStatus=-1）。
 *      这类行若进入备份，还原到他端会生成 uri="" 的幽灵书（用户可见却打不开）。
 *   2. ★ 2026-07-07 新增：去重行（source='deduped'）不参与备份。
 *      见 docs/plans/2026-07-07-扫描导入同书去重.md。被去重的行虽然 importStatus=-1
 *      已被规则 1 排除,但显式加 source 校验防止未来规则调整时不慎纳入。
 *
 * 与日常展示查询 `WHERE importStatus = 0` 语义保持一致。
 *
 * 抽取为纯函数便于单测（零 Android 依赖）。
 */
object BackupBookFilter {

    /** 失败占位行 / 去重行返回 false（不备份），正常书返回 true。 */
    fun shouldExport(book: BookEntity): Boolean =
        book.importStatus == 0 && book.source != "deduped"
}
