package com.wxn.bookread.data.model

import android.text.TextPaint
import com.wxn.bookread.textHeight

/***
 * 一行显示的字符串
 */
data class TextLine(

    /**
     * 显示的内容, 或者需要显示的图片的本地路径
     */
    var text: String = "",

    /***
     * 测量之后的，显示的每一个字符水平方向上的偏移位置
     */
    val textChars: ArrayList<TextChar> = arrayListOf(),

    /***
     * 行顶部位置
     */
    var lineTop: Float = 0f,

    /***
     * 行基线位置
     */
    var lineBase: Float = 0f,
    /***
     * 行底部位置
     */
    var lineBottom: Float = 0f,

    /***
     * 是否是标题
     */
    val isTitle: Boolean = false,
    /***
     * 是否是图片
     */
    val isImage: Boolean = false,

    /***
     * 是否正在播放tts语音
     */
//    var isReadAloud: Boolean = false,

    var paragraphIndex: Int = 0,        //当前行所在的段落的序号
    var charStartOffset: Int = 0,       //当前行在所在段落中的起始位置 the start index (inclusive).
    var charEndOffset: Int = 0,        //当前行在所在段落中的结束位置  the end index (exclusive),


    var isLine : Boolean = false,                           // 是否是线段
    var lineStart: Pair<Float, Float> = Pair(0f, 0f),       // 线的起点
    var lineEnd: Pair<Float, Float> = Pair(0f, 0f),         // 线的终点
    var lineBorder: Float = 1f,                             // 线段的粗细
    var lineColor: String? = null,                          // 线段颜色

    //表格中的每一个单元格也是一个TextLine,
    var isTableCell: Boolean = false,
    var rowIndex: Int = 0, //单元格行索引
    var colIndex: Int = 0,  //单元格列索引
    var rowLineOffset : Int = 0,   //单元格所在的行文字在一个tr行中的偏移量

    var lineDot: LineDot? = null,


    /**
     * 行方向：true=RTL，false=LTR。
     * 新建行（!lineShared）恒 = 段落基调 segDirect.baseRtl（UAX#9：行的基方向恒为段落嵌入
     * 方向；段落首行永不与前一段落共享 → 首行必为新建行）。run.isRtl 只决定 run 自身
     * StaticLayout 的文本方向，不决定行方向。
     * 驱动 cursor 起点/推进、相邻摆放、对齐（anchorLine/justify/indent）、列表圆点锚定侧。
     * 默认 false
     */
    var isRtl: Boolean = false,

    /**
     * 粘合段重锚标记：
     * 行发生过 level>=1 多块重锚（行内块序 ≠ list 序；重锚只改 x 坐标，数组序不动）。
     * C5 起 justify 执行器按视觉序分发，重锚行不再据此跳过 justify（C4 摘除守卫）；
     * 本标记仅作重锚形态的诊断/测试选择器（W5-b-3、MixedBaseLtr 断言用）。
     * 运行时布局产物，不持久化。
     */
    var spanReordered: Boolean = false,

    ) {

    /**
     *  段落级字距防御标志：本行所属段落布局时对测量画笔执行了 letterSpacing 置零
     * （段落基调 RTL 或含任何 RTL run——setTypeText 谓词）。渲染侧据此在
     * drawingPaint.set(parentPaint) 之后镜像置零，保证绘制推进量与存储 x 同口径。
     * 布局产物、随行对象只读消费；默认 false（TextPage.format() 等兜底路径以全局画笔
     * 测+绘，两侧自洽，不置零）。
     * 命名按用途而非方向：base-LTR 混排段此值为 true 而 isRtl=false，方向式命名会自相矛盾。
     */
    var letterSpacingZeroed: Boolean = false

}
