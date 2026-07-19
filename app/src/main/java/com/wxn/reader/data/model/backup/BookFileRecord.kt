package com.wxn.reader.data.model.backup

import com.wxn.reader.data.remote.sync.canonical.BookMetaRecord
import com.wxn.reader.data.remote.sync.canonical.BookReadingRecord
import com.wxn.reader.data.remote.sync.canonical.BookUserRecord
import com.wxn.reader.data.remote.sync.canonical.CanonicalAnnotation
import com.wxn.reader.data.remote.sync.canonical.CanonicalBookmark
import com.wxn.reader.data.remote.sync.canonical.CanonicalNote
import com.wxn.reader.data.remote.sync.canonical.VocabularyRecord
import kotlinx.serialization.Serializable

/**
 * ★ v1.4 严重-F3 + 严重-8:单本书 ZIP 内 JSON 结构。
 *
 * - 无顶层 identity(严重-8):身份只放 [meta.identity],stableId 从文件名解析。
 * - 含 notes/bookmarks/vocabulary 三字段(严重-F3:原 v1.3 缺失致备份丢数据)。
 */
@Serializable
data class BookFileRecord(
    val meta: BookMetaRecord,
    val user: BookUserRecord,
    val reading: BookReadingRecord,
    val annotations: List<CanonicalAnnotation>,
    val notes: List<CanonicalNote>,
    val bookmarks: List<CanonicalBookmark>,
    val vocabulary: List<VocabularyRecord>,
) {
    /** stableId 从 meta.identity 派生(与文件名一致)。 */
    val stableId: String get() = meta.identity.stableId
}
