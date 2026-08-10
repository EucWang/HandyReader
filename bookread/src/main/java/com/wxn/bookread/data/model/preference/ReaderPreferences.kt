package com.wxn.bookread.data.model.preference

import android.graphics.Color
import com.wxn.bookread.data.model.config.ConfigReadingProgression
import com.wxn.bookread.ext.sp

val BASE_FONT_SIZE : Float = 16.sp.toFloat()
val BASE_TITLE_FONT_SIZE : Float = 24.sp.toFloat()

/****
 * 阅读设置
 */
data class ReaderPreferences(
    //Font Settings
    val fontSize: Double,                       //字体大小   //取值 0.5 ～ 2.0 之间， 基础字体大小的系数， 基础大小16.sp
    val font: String = "",                      //字体路径

    @Deprecated("never used")
    val fontBold: Int = 0,                      //是否粗体
    val fontVariant: String = "regular",        //字体变体名称: regular, bold, italic, bolditalic 等

    val titleSize : Double,           //标题文字大小  //取值 0.5 ～ 2.0 之间， 基础字体大小的系数， 基础大小 20.sp
    val titleTopSpacing: Double,     //标题顶部间距
    val titleBottomSpacing : Double,      //标题底部间距

    val letterSpacing: Double,                  //字母间距
    val lineHeight: Double,                     //行高        取值 1.0 ～ 3.0 之间， 基础行高的系数，
    val pageHorizontalMargins: Double,          //页面左右边距 取值 0.5 ～ 5.0 之间，  取值5.0 即表示左右边距占屏幕的一半
    val pageVerticalMargins: Double,                     //页面顶部边距  取值 0.5 ～ 5.0 之间，  取值5.0 即表示上下边距占屏幕的一半
//    val lineSpacingExtra: Double,               //行高系数， 最终会除上10， 默认值13

    val paragraphIndent: Double,                //段落缩进, 段落首行缩进， 多少个字符宽度
    val paragraphSpacing: Double,               //段落间距

    @Deprecated("never used")
    val wordSpacing: Double,                    //词间距   取值 0.0 ～ 3.0 之间，TextPaint.wordSpacing设置无效果
    val forceAlignOverride: Boolean = false,    //强制覆盖书籍 CSS 对齐样式
    val userTextAlign: Int = 1,                 //用户对齐偏好: 1=Left, 2=Right, 3=Center, 4=Justify

    //ui Settings
    val backgroundColor:  Int,                    //背景颜色
    val backgroundImage: String,                  //背景图片
    val textColor: Int,                           //文字颜色
    val colorHistory: List<Color> = emptyList(),    //颜色历史
    /**
     * 当前阅读主题 id（持久化）。
     * - null：老用户/重置后（按 Q-02-B 方案，主题选择器不选中任何项，色值保持旧值不变）。
     * - 非 null：9 个预设 themeId 之一，表示当前应用的主题。
     */
    val readerThemeId: String? = null,            //阅读主题id
    /**
     * 阅读主题模式（LIGHT/DARK/AUTO）。
     * - AUTO（默认）：跟随系统暗色信号，系统切深色时自动切到配对暗主题（[ReaderThemePresets.getPairedThemeId]）。
     * - LIGHT/DARK：固定显示对应明暗的主题列表。
     * UI 层据此过滤主题选择器（LIGHT_THEMES / DARK_THEMES）。
     */
    val readerThemeMode: ReaderThemeMode = ReaderThemeMode.AUTO,  //阅读主题模式
    //Reader Settings
    val keepScreenOn: Boolean,                      //保持屏幕常亮
    val tapNavigation: Boolean,                     //点击导航 , 不知道是用来做什么的

    /****
     * 页面切换动画方式
     *  0   -> NoAnimPageDelegate 无动画
     *  1   -> CoverPageDelegate 水平覆盖
     *  2   -> SlidePageDelegate 水平滑动
     *  3   -> SimulationPageDelegate 仿真翻页
     *  4   -> CoverVerticalPageDelegate 垂直覆盖
     *  5   -> SlideVerticalPageDelegate 垂直滑动
     *  6   -> ContinuousScrollReaderView 连续垂直滚动 (Compose)
     */
    val scroll: Int,                            //滚动模式

    val animationSpeed: Int,            //页面切换动画速度, 取值 50 ~ 1000, default 320

    val readingProgression: ConfigReadingProgression,       //阅读方向/从左向右/从右向左
    val verticalText: Boolean,                      //垂直文本
    val publisherStyles: Boolean,                   //出版商样式
    val textNormalization: Boolean,                 //文字格式化
    val volumeKeyPageTurning: Boolean = false,       //音量键翻页，默认关闭
    val clickAreaMode: Int = 0,                     //点击区域模式: 0=中间区域(centerRectF), 1=顶部区域(topRectF)
    val leftHandedMode: Boolean = false,            //左手操作模式: true=左半部下一页,右半部上一页; false=左半部上一页,右半部下一页
    val invertPageTurn: Boolean = false,            //翻页方向反转，影响水平方向的翻页动画
    val brightness: Float = 0.0f,                   //用户设置的亮度值 0.0~1.0
    val brightnessSet: Boolean = false,             //用户是否手动设置过亮度
    val columns: Int = 1,                //双列显示开关（全局阅读设置，与 scroll/leftHandedMode 同组，不进 per-book override）
)