package com.wxn.bookread.provider

/**
 * 列边界上下文（v4 R9 + v5 S9）。子函数内部一律通过此对象读取「当前列」的水平几何信息，
 * 替代直接读取单例 [visibleWidth]/[paddingHorizontal]/[visibleRight]。
 *
 * - 单列：全页几何 [page]
 * - 双列：列级几何 [leftColumn] / [rightColumn]
 *
 * 关键约束：LayoutBounds 一旦构造即不可变（data class + val），并发场景下不会出现
 * save/restore 被其他线程中途篡改（第三轮 P3 关注点）。[visibleHeight]/[visibleBottom]
 * 两列共享（垂直空间一致），不纳入本对象，子函数继续读单例。
 */
data class LayoutBounds(
    val width: Int,          // 当前列宽（替代 visibleWidth）
    val startX: Int,         // 当前列左边界（替代 paddingHorizontal）
    val endX: Int,           // 当前列右边界（替代 visibleRight）
    val role: ColumnRole     // v5 S9：列角色（FULL/LEFT/RIGHT）
) {
    val isColumn: Boolean get() = role != ColumnRole.FULL
    val isLeftColumn: Boolean get() = role == ColumnRole.LEFT
    val isRightColumn: Boolean get() = role == ColumnRole.RIGHT


}