package com.wxn.reader.data.backup

import com.wxn.reader.data.remote.sync.canonical.BookIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * stableId 派生:由 fileType + contentHash 拼成,作为书籍跨设备稳定标识。
 *
 * stableId 也是 ZIP 内 `books/{stableId}.json` 文件名来源。
 *
 * ★ 同步方案 v2.6 §2.2.6。
 */
@Singleton
class StableIdResolver @Inject constructor() {

    /** 由 fileType + contentHash 派生 stableId。contentHash 为空时返回 null(无法派生)。 */
    fun stableId(fileType: String, contentHash: String?): String? {
        if (contentHash.isNullOrBlank()) return null
        return "$fileType:$contentHash"
    }

    /** 从 BookIdentity 派生 stableId(便捷重载)。 */
    fun stableId(identity: BookIdentity): String = identity.stableId

    /** 解析 stableId → (fileType, contentHash)。 */
    fun parse(stableId: String): Pair<String, String>? {
        val idx = stableId.indexOf(':')
        if (idx <= 0 || idx == stableId.length - 1) return null
        return stableId.substring(0, idx) to stableId.substring(idx + 1)
    }
}
