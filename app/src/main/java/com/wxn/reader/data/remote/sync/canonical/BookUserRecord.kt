package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 用户行为档:评分/收藏/书评/阅读状态。
 *
 * ★ 严重-C:[readingStatus] 是可空 `Int?`(对应本地 `ReadingStatus?` 枚举,null = 未设置)。
 * 合并语义:rating/favorite/review LWW;readingStatus 单调上升状态机(`maxOf(local ?: 0, remote ?: 0)`)。
 *
 * ★ 同步方案 v2.6 §2.5.3 / 一期 §3.3.0 mergeUser。
 */
@Serializable
data class BookUserRecord(
    val rating: Float = 0f,
    val isFavorite: Boolean = false,
    /** NOT_STARTED=0 / IN_PROGRESS=1 / FINISHED=2;null 表示未设置。 */
    val readingStatus: Int? = null,
    val review: String? = null,
    /** 收藏时间戳，用于首页「最近收藏」排序。 */
    val favoriteDate: Long? = null,
    val hlc: HlcTs,
) {
    val schemaVersion: Int get() = 2
}
