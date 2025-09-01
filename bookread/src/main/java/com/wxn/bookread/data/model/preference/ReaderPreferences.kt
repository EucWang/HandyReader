package com.wxn.bookread.data.model.preference

import android.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
    val fontBold: Int = 0,                      //是否粗体

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
    val wordSpacing: Double,                    //词间距   取值 0.0 ～ 3.0 之间，TextPaint.wordSpacing设置无效果
    val textAlign: TextAlign,                   //文本对齐

    //ui Settings
    val backgroundColor:  Int,                    //背景颜色
    val backgroundImage: String,                  //背景图片
    val textColor: Int,                           //文字颜色
    val colorHistory: List<Color> = emptyList(),    //颜色历史
    //Reader Settings
    val keepScreenOn: Boolean,                      //保持屏幕常亮
    val tapNavigation: Boolean,                     //点击导航 , 不知道是用来做什么的


    /****
     * 页面切换动画方式
     *  0   -> pageDelegate = CoverPageDelegate(this) 覆盖
     *  1   -> pageDelegate = SlidePageDelegate(this)   滑动
     *  2   -> pageDelegate = SimulationPageDelegate(this) 仿真
     *  3   -> pageDelegate = ScrollPageDelegate(this)  滚动
     *  else -> pageDelegate = NoAnimPageDelegate(this) 无动画
     */
    val scroll: Int,                            //滚动模式

    val readingProgression: ConfigReadingProgression,       //阅读方向/从左向右/从右向左
    val verticalText: Boolean,                      //垂直文本
    val publisherStyles: Boolean,                   //出版商样式
    val textNormalization: Boolean,                 //文字格式化
)