package com.wxn.bookread.data.model

enum class ListDotShape(
    val inkWidthEm: Float,
    val inkHeightEm: Float,
    val hollow: Boolean
) {

    DISC(0.32f, 0.32f, false),
    CIRCLE_HOLLOW(0.32f, 0.32f, true),
    SQUARE(0.30f, 0.30f, false),
    DASH(0.50f, 0.08f, false);


    companion object {


        /** HTML §15.3.7 深度语义：
         * 1=DISC，2=CIRCLE_HOLLOW，≥3=SQUARE（封顶不循环）。
         *  仅作用于无序标记（order==0）；有序项渲染 "N."（用户已实现，ol=decimal 规范语义 ✓）。
         *  契约：level ≥ 1（draw() 守卫）；防御性 level<=1 一律 DISC。
         *  */
        fun shapeForLevel(level: Int): ListDotShape = when {
            level <= 1 -> DISC
            level == 2 -> CIRCLE_HOLLOW
            else -> SQUARE
        }
    }
}