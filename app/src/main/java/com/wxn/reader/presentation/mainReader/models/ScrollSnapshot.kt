package com.wxn.reader.presentation.mainReader.models

/**
 * 滚动状态快照
 *
 * 用于 snapshotFlow 统一观察 LazyListState 和 mergedPages 的变化。
 * 当任一字段变化时触发重发射。
 *
 * @param pageCount dirty flag — 仅用于触发 snapshotFlow 重发射，
 *   collect 内部通过 mergedPages (Compose state) 读取最新数据
 */
data class ScrollSnapshot(
    val firstVisibleIndex: Int,
    val canScrollForward: Boolean,
    val canScrollBackward: Boolean,
    val pageCount: Int
)