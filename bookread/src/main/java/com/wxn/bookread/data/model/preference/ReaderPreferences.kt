package com.wxn.bookread.data.model.preference

import android.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.wxn.bookread.data.model.config.ConfigReadingProgression

/****
 * 阅读设置
 */
data class ReaderPreferences constructor(
    //Font Settings
    val fontSize: Double,                       //字体大小
    val font: String = "",                      //字体路径
    val fontBold: Int = 0,                      //是否粗体

    val titleSize : Double,           //标题文字大小
    val titleTopSpacing: Double,     //标题顶部间距
    val titleBottomSpacing : Double,      //标题底部间距

    val letterSpacing: Double,                  //字母间距
    val lineHeight: Double,                     //行高
    val pageHorizontalMargins: Double,          //页面左右边距
    val pageTopMargins: Double,                     //页面顶部边距
    val lineSpacingExtra: Double,               //行高系数， 最终会除上10， 默认值13

    val paragraphIndent: Double,                //段落缩进
    val paragraphSpacing: Double,               //段落间距
    val wordSpacing: Double,                    //词间距
    val textAlign: TextAlign,                   //文本对齐

    //ui Settings
    val backgroundColor:  Int,                    //背景颜色
    val textColor: Int,                           //文字颜色
    val colorHistory: List<Color> = emptyList(),    //颜色历史
    //Reader Settings
    val keepScreenOn: Boolean,                      //保持屏幕常亮
    val tapNavigation: Boolean,                     //点击导航
    val scroll: Boolean,                            //滚动模式
    val readingProgression: ConfigReadingProgression,       //阅读方向/从左向右/从右向左
    val verticalText: Boolean,                      //垂直文本
    val publisherStyles: Boolean,                   //出版商样式
    val textNormalization: Boolean,                 //文字格式化

)

// Extension function to convert ReaderPreferences to EpubPreferences
fun ReaderPreferences.toEpubPreferences(): EpubPreferences {

    return EpubPreferences(
        fontSize = this.fontSize,
//        fontWeight = this.fontWeight,
        letterSpacing = this.letterSpacing,
        lineHeight = this.lineHeight,
        pageMargins = this.pageHorizontalMargins,
        paragraphIndent = this.paragraphIndent,
        paragraphSpacing = this.paragraphSpacing,
        wordSpacing = this.wordSpacing,
        textAlign = this.textAlign,
        //ui Settings
        backgroundColor = this.backgroundColor,
        textColor = this.textColor,
        //Reader Settings
        scroll = this.scroll,
        readingProgression = this.readingProgression,
        verticalText = this.verticalText,
        publisherStyles = this.publisherStyles,
        textNormalization = this.textNormalization,
    )
}