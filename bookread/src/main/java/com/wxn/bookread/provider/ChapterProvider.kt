package com.wxn.bookread.provider

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssFontStyle
import com.wxn.base.bean.CssFontWeight
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.base.ext.isContentPath
import com.wxn.base.ext.statusBarHeight
import com.wxn.base.ext.toStringArray
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.preference.BASE_FONT_SIZE
import com.wxn.bookread.data.model.preference.BASE_TITLE_FONT_SIZE
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.ext.dp
import com.wxn.bookread.textHeight
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import kotlin.collections.firstOrNull
import kotlin.math.max
import kotlin.math.roundToInt

object ChapterProvider {

    val paragraphIndent: String = "　　" //段落缩进
    val oneParagraphIndent: String = "　" //段落缩进
    val JS_PATTERN: Pattern =
        Pattern.compile("(<js>[\\w\\W]*?</js>|@js:[\\w\\W]*$)", Pattern.CASE_INSENSITIVE)
    val EXP_PATTERN: Pattern = Pattern.compile("\\{\\{([\\w\\W]*?)\\}\\}")
    val imgPattern: Pattern =
        Pattern.compile(
            "<img\\b[^>]*?(?:\\s+src=[\"']([^\"']*)[\"'])?[^>]*?(?:\\s+width=[\"']([^\"']*)[\"'])?[^>]*?(?:\\s+height=[\"']([^\"']*)[\"'])?[^>]*?>",
            Pattern.CASE_INSENSITIVE
        )
//        Pattern.compile("<img .*?src.*?=.*?\"(.*?(?:,\\{.*\\})?)\".*?>", Pattern.CASE_INSENSITIVE)

    val nameRegex = Regex("\\s+作\\s*者.*")
    val authorRegex = Regex(".*?作\\s*?者[:：]")
    val fileNameRegex = Regex("[\\\\/:*?\"<>|.]")
    val splitGroupRegex = Regex("[,;，；]")

    var readerPreferencesUtil: ReaderPreferencesUtil? = null
    var readTipPreferencesUtil: ReadTipPreferencesUtil? = null


    /**
     * 页面显示宽度, 整个控件的可显示宽度, 和屏幕宽度相同
     */
    var viewWidth = 0

    /***
     * 页面显示高度, 整个控件的可显示高度, 是屏幕高度 - 系统状态栏的高度
     */
    var viewHeight = 0

    /***
     * 左边距/右边距
     */
    var paddingHorizontal = 0

    /***
     * 上边距/下边距
     */
    var paddingVertical = 0

    /**
     * 可视宽度,
     * 这里是排除掉了水平方向上的边距之后的页面可显示元素的宽度
     */
    var visibleWidth = 0

    /***
     * 可视高度
     * 这里是已经计算了垂直方向的边距之后的页面可显示元素的高度
     */
    var visibleHeight = 0

    /***
     * 可视的右边的偏移
     */
    var visibleRight = 0

    /***
     * 可视底部的偏移位置
     */
    var visibleBottom = 0

    //    占一屏的图片的最小高度, 450 只是一个默认值
    private var fullImageMinHeight = 450 //
    /***
     * 行间距
     */
    private var lineSpacingExtra = 0f

    /***
     * 段落间距
     */
    private var paragraphSpacing = 0

    /***
     * 标题顶部间距
     */
    private var titleTopSpacing = 0

    /***
     * 标题底部间距
     */
    private var titleBottomSpacing = 0

    /***
     * 字体
     */
    var typeface: Typeface = Typeface.SANS_SERIF

    /***
     * 标题的TextPaint
     */
    lateinit var titlePaint: TextPaint

    /***
     * 文本内容的TextPaint
     */
    lateinit var contentPaint: TextPaint

    lateinit var h1Paint: TextPaint
    lateinit var h2Paint: TextPaint
    lateinit var h3Paint: TextPaint
    lateinit var h4Paint: TextPaint
    lateinit var aPaint: TextPaint

//    private var oneWordWidth = 0f

    private val typerfaceMap = mutableMapOf<String, Typeface>()

    fun getTypeface(fontWeight: CssFontWeight) : Typeface? {
        return getTypeface(fontWeight, CssFontStyle.CssFontStyleNormal)
    }

    fun getTypeface(fontWeight: CssFontWeight, cssFontStyle: CssFontStyle) =
        when (fontWeight) {
            CssFontWeight.FontWeightNormal -> {
                if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                    if (typerfaceMap["weight_normal_italic"] == null) {
                        typerfaceMap["weight_normal_italic"] = Typeface.create(typeface, Typeface.ITALIC)
                    }
                    typerfaceMap["weight_normal_italic"]
                } else {
                    if (typerfaceMap["weight_normal_normal"] == null) {
                        typerfaceMap["weight_normal_normal"] = Typeface.create(typeface, Typeface.NORMAL)
                    }
                    typerfaceMap["weight_normal_normal"]
                }
            }

            CssFontWeight.FontWeightBold -> {
                if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                    if (typerfaceMap["weight_bold_italic"] == null) {
                        typerfaceMap["weight_bold_italic"] = Typeface.create(typeface, Typeface.BOLD_ITALIC)
                    }
                    typerfaceMap["weight_bold_italic"]
                } else {
                    if (typerfaceMap["weight_bold"] == null) {
                        typerfaceMap["weight_bold"] = Typeface.create(typeface, Typeface.BOLD)
                    }
                    typerfaceMap["weight_bold"]
                }
            }

            CssFontWeight.FontWeightBolder -> {
                if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                    if (typerfaceMap["weight_bolder_italic"] == null) {
                        typerfaceMap["weight_bolder_italic"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(typeface, 900, true)
                        } else {
                            Typeface.create(typeface, Typeface.BOLD_ITALIC)
                        }
                    }
                    typerfaceMap["weight_bolder_italic"]
                } else {
                    if (typerfaceMap["weight_bolder"] == null) {
                        typerfaceMap["weight_bolder"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(typeface, 900, false)
                        } else {
                            Typeface.create(typeface, Typeface.BOLD)
                        }
                    }
                    typerfaceMap["weight_bolder"]
                }
            }

            CssFontWeight.FontWeightLighter -> {
                if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                    if (typerfaceMap["weight_lighter_italic"] == null) {
                        typerfaceMap["weight_lighter_italic"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(typeface, 300, true)
                        } else {
                            Typeface.create(typeface, Typeface.ITALIC)
                        }
                    }
                    typerfaceMap["weight_lighter_italic"]
                } else {
                    if (typerfaceMap["weight_lighter"] == null) {
                        typerfaceMap["weight_lighter"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(typeface, 300, false)
                        } else {
                            Typeface.create(typeface, Typeface.NORMAL)
                        }
                    }
                    typerfaceMap["weight_lighter"]
                }
            }
        }


    /****
     * 根据TextTag的name属性，得到对应的TextPaint
     */
    fun getPaintByTagName(tag: TextTag?, default: TextPaint? = null): TextPaint {
        var tagName = tag?.name.orEmpty()
        if (tagName == "a") {
            val pairs = tag?.paramsPairs()
            var hasParams = false
            if (!pairs.isNullOrEmpty()) {
                for (item in pairs) {
                    if (item.first == "href" || item.first == "id") {
                        hasParams = true
                    }
                }
            }
            if (!hasParams) {
                tagName = ""
            }
        }
        return when (tagName) {
            "h1" -> h1Paint
            "h2" -> h2Paint
            "h3" -> h3Paint
            "h4" -> h4Paint
            "a" -> aPaint
            else -> default ?: contentPaint
        }
    }

    /**
     * 更新绘制尺寸
     */
    private suspend fun upVisibleSize(context: Context) {
        Logger.i("ChapterProvider:upVisibleSize")

        if (viewWidth == 0 || viewHeight == 0) {
            val metrics = context.resources.displayMetrics
            viewWidth = metrics.widthPixels
            viewHeight = metrics.heightPixels - context.statusBarHeight
            Logger.d("ChapterProvider::set screen size to view::viewWidth=$viewWidth,viewHeight=$viewHeight")
        }

        val readerPreferences = readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()
        if (viewWidth > 0 && viewHeight > 0) {
            paddingHorizontal = (((readerPreferences?.pageHorizontalMargins?.toDouble() ?: 0.0) * 0.1 * viewWidth.toDouble()).toInt()) / 2         //页面左边距
            paddingVertical = ((readerPreferences?.pageVerticalMargins ?: 0.0) * 0.1 * viewHeight.toDouble()).toInt() / 2                 //页面顶部间距
            visibleWidth = (viewWidth - paddingHorizontal * 2).toInt()                                //可视宽度
            visibleHeight = (viewHeight - paddingVertical * 2).toInt()                            //可视高度
            visibleRight = paddingHorizontal + visibleWidth                                       //可视右边
            visibleBottom = paddingVertical + visibleHeight                                      //可视底部

            fullImageMinHeight = max(max((visibleHeight * .075).toInt(), (viewHeight * 0.6f).toInt()), 450) //占一屏的图片的最小高度
        }
        Logger.d("ChapterProvider::upVisibleSize::viewWidth=$viewWidth, viewHeight=$viewHeight, visibleWidth=$visibleWidth,visibleHeight=$visibleHeight,visibleRight=$visibleRight,visibleBottom=$visibleBottom")
    }

    var imgScale = 1.0f

    /**
     * 更新样式
     */
    fun upStyle(context: Context, onFinish:(()->Unit)? = null) {
        //https://hyperos.mi.com/font 字体下载
        //https://hyperos.mi.com/font/download
        //https://hyperos.mi.com/font-download/MiSans.zip               // 简体中文
        //https://hyperos.mi.com/font-download/MiSans_Latin.zip         // 拉丁语
        //https://hyperos.mi.com/font-download/MiSans_TC.zip            // 繁体中文
        //https://hyperos.mi.com/font-download/MiSans_Tibetan.zip       // 汉藏语系的语言，主要在西藏地区
        //https://hyperos.mi.com/font-download/MiSans_Arabic.zip        // 阿拉伯语
        //https://hyperos.mi.com/font-download/MiSans_Devanagari.zip    // 梵语、印地语、马拉地语、尼泊尔语
        //https://hyperos.mi.com/font-download/MiSans_Gurmukhi.zip      // 旁遮普语
        //https://hyperos.mi.com/font-download/MiSans_Thai.zip          // 泰国
        //https://hyperos.mi.com/font-download/MiSans_Lao.zip           // 老挝
        //https://hyperos.mi.com/font-download/MiSans_Myanmar.zip       // Myanmar" 是一个国家的名称，位于东南亚，其官方全称为 "Republic of the Union of Myanmar"（缅甸联邦共和国）
        //https://hyperos.mi.com/font-download/MiSans_Khmer.zip         // “Khmer”指的是柬埔寨的民族群体，即高棉人

        imgScale = context.resources.displayMetrics.density

//        oneWordWidth = 0f
        Logger.i("ChapterProvider::upStyle")
        Coroutines.mainScope().launch {
            val readerPreferences = readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()
//            Logger.d("ChapterProvider::upStyle::readerPreferences =${readerPreferences}")

            //更新字体
            typeface = try {
                val fontPath = readerPreferences?.font.orEmpty()
                Logger.d("ChapterProvider::upStyle::fontPath=$fontPath")
//            val fontPath = ReadBookConfig.textFont  //字体路径
                when {
                    fontPath == "serif" -> Typeface.SERIF
                    fontPath == "sans_serif" -> Typeface.SANS_SERIF
                    fontPath == "monospace" -> Typeface.MONOSPACE
                    //android26以上版本, 根据file descriptor得到字体类
                    fontPath.isContentPath() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        val fd = context.contentResolver
                            .openFileDescriptor(Uri.parse(fontPath), "r")!!
                            .fileDescriptor
                        Typeface.Builder(fd).build()
                    }
                    //android26以下版本，直接解析得到字体文件路径，从而得到字体类
                    fontPath.isContentPath() -> {
                        Typeface.createFromFile(PathUtil.getPath(context, Uri.parse(fontPath)))
                    }
                    //如果就是字体文件路径，直接得到字体类
                    fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                    //否则采用系统字体
                    else -> Typeface.SANS_SERIF
                }
            } catch (e: Exception) {
                readerPreferences?.copy(
                    font = ""
                )?.let { it ->
                    readerPreferencesUtil?.updatePreferences(it)
                }
                Typeface.SANS_SERIF
            }
            // 字体统一处理
            val bold = Typeface.create(typeface, Typeface.BOLD) //加粗
            val normal = Typeface.create(typeface, Typeface.NORMAL) //正常
            //根据用户配置是否加粗，得到标题字体和内容字体

            val (titleFont, textFont) = when (readerPreferences?.fontBold) {
                1 -> {
                    Pair(getTypeface(CssFontWeight.FontWeightBolder), getTypeface(CssFontWeight.FontWeightBold))
                }
                2 -> {
                    Pair(getTypeface(CssFontWeight.FontWeightBolder), getTypeface(CssFontWeight.FontWeightNormal))
                }
                else -> Pair(bold, normal)
            }

            //标题的Paint
            titlePaint = TextPaint()
            titlePaint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
//            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${titlePaint.color.toString(16)}")
            titlePaint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
//            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${titlePaint.letterSpacing}")
            titlePaint.typeface = titleFont                                                     //设置标题字体
//        titlePaint.textSize = with(ReadBookConfig) { textSize + titleSize }.sp.toFloat()    //设置标题字体大小
            titlePaint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 1.2f
//            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${titlePaint.textSize}")
            titlePaint.isAntiAlias = true                                                       //设置抗锯齿

            //h1的Paint
            h1Paint = TextPaint()
            h1Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
//            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h1Paint.color.toString(16)}")
            h1Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
//            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h1Paint.letterSpacing}")
            h1Paint.typeface = titleFont                                                     //设置标题字体
            h1Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 1.0f
//            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h1Paint.textSize}")
            h1Paint.isAntiAlias = true                                                       //设置抗锯齿

            //h2的Paint
            h2Paint = TextPaint()
            h2Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
//            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h2Paint.color.toString(16)}")
            h2Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
//            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h2Paint.letterSpacing}")
            h2Paint.typeface = titleFont                                                     //设置标题字体
            h2Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.9f
//            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h2Paint.textSize}")
            h2Paint.isAntiAlias = true                                                       //设置抗锯齿

            //h3的Paint
            h3Paint = TextPaint()
            h3Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
//            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h3Paint.color.toString(16)}")
            h3Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
//            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h3Paint.letterSpacing}")
            h3Paint.typeface = titleFont                                                     //设置标题字体
            h3Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.8f
//            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h3Paint.textSize}")
            h3Paint.isAntiAlias = true                                                       //设置抗锯齿

            //h4的Paint
            h4Paint = TextPaint()
            h4Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
//            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h4Paint.color.toString(16)}")
            h4Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
//            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h4Paint.letterSpacing}")
            h4Paint.typeface = titleFont                                                     //设置标题字体
            h4Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.7f
//            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h4Paint.textSize}")
            h4Paint.isAntiAlias = true                                                       //设置抗锯齿

            //正文的Paint
            contentPaint = TextPaint()
            contentPaint.color = readerPreferences?.textColor ?: Color.BLACK                    //设置正文文字颜色
            contentPaint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0.0f               //设置正文文字间距
//            Logger.d("ChapterProvider::upStyle::contentPaint.letterSpacing=${contentPaint.letterSpacing}")
            contentPaint.typeface = textFont                                                    //设置正文字体
            contentPaint.textSize = (readerPreferences?.fontSize?.toFloat() ?: 1.0f) * BASE_FONT_SIZE                   //设置字体大小
//            Logger.d("ChapterProvider::upStyle::contentPaint.textSize=${contentPaint.textSize}")
            contentPaint.isAntiAlias = true                                                     //设置抗锯齿

            //<a>标签的Paint
            aPaint = TextPaint()
            aPaint.color = Color.BLUE
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                aPaint.underlineColor = Color.BLUE
            }
            aPaint.isUnderlineText = true
            aPaint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0.0f               //设置正文文字间距
//            Logger.d("ChapterProvider::upStyle::contentPaint.letterSpacing=${aPaint.letterSpacing}")
            aPaint.typeface = textFont                                                    //设置正文字体
            aPaint.textSize = (readerPreferences?.fontSize?.toFloat() ?: 1.0f) * BASE_FONT_SIZE                   //设置字体大小
//            Logger.d("ChapterProvider::upStyle::contentPaint.textSize=${aPaint.textSize}")
            aPaint.isAntiAlias = true                                                     //设置抗锯齿

            //间距
            lineSpacingExtra = readerPreferences?.lineHeight?.toFloat() ?: 1.2f                //行间距系数，除上10 再和lineHeight相乘
//            Logger.d("ChapterProvider::upStyle::lineSpacingExtra=${lineSpacingExtra}")
            paragraphSpacing = readerPreferences?.paragraphSpacing?.toInt() ?: 0                 //段落间距
//            Logger.d("ChapterProvider::upStyle::paragraphSpacing=${paragraphSpacing}")
            titleTopSpacing = readerPreferences?.titleTopSpacing?.dp?.toInt() ?: 0               //标题顶部间距
//            Logger.d("ChapterProvider::upStyle::titleTopSpacing=${titleTopSpacing}")
            titleBottomSpacing = readerPreferences?.titleBottomSpacing?.dp?.toInt() ?: 0                           //标题底部间距
//            Logger.d("ChapterProvider::upStyle::titleBottomSpacing=${titleBottomSpacing}")
            //更新屏幕参数
            upVisibleSize(context)
            Logger.d("ChapterProvider::upStyle done")
            onFinish?.invoke()
        }
    }

    suspend fun getTextChapter(
        chapter: BookChapter,
        contents: List<ReaderText>,
        imageStyles: String = "",
        chapterSize: Int,
    ): TextChapter? {
        Logger.d("ChapterProvider::getTextChapter::chapterIndex=[${chapter.chapterIndex}]")
        val textPages = arrayListOf<TextPage>()   //一个章节的内容，可以拆分成多少页进行显示
        val pageLines = arrayListOf<Int>()          //每一个页面上，显示的行数的集合
        val pageLengths = arrayListOf<Int>()        //每一个页面上，显示的字符数的集合
        val stringBuilder = StringBuilder()
        var offsetY = 0f    //每一行显示时，和顶部的偏移量

        val isOneElePage = (contents.size == 1) //只有一个元素的页面
        var theImageStyle = imageStyles.uppercase()
        if (contents.size > 1) {
            var imgCount = 0
            for(item in contents) {
                if (item is ReaderText.Image) {
                    imgCount++
                }
            }
            if (imgCount == contents.size) {  //整个章节都是图片,章节内容也不止一条, 那么设置样式都为FULL, 让其全屏居中显示
                if (theImageStyle.isEmpty()) {
                    theImageStyle = "FULL"
                }
            }
        }

        textPages.add(TextPage())   //增加一空白页，然后给这个页面增加显示内容
        offsetY += paddingVertical
        contents.forEachIndexed { index, paragraph -> //遍历需要显示的内容的每一个自然段， 一个段落一个段落（图片）的遍历
            when (paragraph) {
                is ReaderText.Image -> {
                    offsetY = setTypeImageWithStyle(isOneElePage, paragraph, theImageStyle, offsetY, textPages)
                }

                is ReaderText.Text -> {
                    val image = paragraph.tryParseToImage()
                    offsetY = if (image != null) {
                        setTypeImageWithStyle(isOneElePage, image, theImageStyle, offsetY, textPages)
                    } else {
                        val title = paragraph.tryParseToChapter(chapter.chapterIndex)
                        if (title != null) {
                            setTypeText(title, index, offsetY, textPages, pageLines, pageLengths, stringBuilder, true)
                        } else {
                            setTypeText(paragraph, index, offsetY, textPages, pageLines, pageLengths, stringBuilder, false)
                        }
                    }
                }

                is ReaderText.Chapter -> {
                    offsetY = setTypeText(paragraph, index, offsetY, textPages, pageLines, pageLengths, stringBuilder, true)
                }

                else -> {}
            }
        }
        //一个章节的全部自然段落/图片/标题都遍历完，
        val lastPage = textPages.last()
        lastPage.height = offsetY + 20.dp   //一个章节最后一页，高度加上20dp
        lastPage.text = stringBuilder.toString()    //
        if (pageLines.size < textPages.size) {      //最后一页的行数没有统计上，则加上
            pageLines.add(lastPage.textLines.size)
        }
        if (pageLengths.size < textPages.size) {    //最后一页的字符数没有统计上，则加上
            pageLengths.add(lastPage.text.length)
        }

        textPages.forEachIndexed { index, page ->
            page.index = index                          // 设置TextPage在所在章节中的索引位置
            page.pageSize = textPages.size              // 设置TextPage所在章节的页数
            page.chapterIndex = chapter.chapterIndex    // 设置TextPage的章节索引
            page.title = chapter.chapterName            // 设置章节名称
            page.chapterSize = chapterSize
            page.upLinesPosition()                      //对一页的高度进行纠偏
        }

        return TextChapter(
            position = chapter.chapterIndex,
            title = chapter.chapterName,
            chapterId = chapter.id,
            pages = textPages,
            pageLines = pageLines,
            pageLengths = pageLengths,
            chaptersSize = chapterSize,
        )
    }

    private fun setTypeImageWithStyle(
        isOneElePage: Boolean,
        image: ReaderText.Image,
        imageStyles: String,
        offsetY: Float,
        textPages: ArrayList<TextPage>
    ): Float {
        val imgStyle =
            if ((isOneElePage || image.textCssInfo.textAlign == CssTextAlign.CssTextAlignJustify) && image.height >= fullImageMinHeight) {
                "FULL"
            } else {
                imageStyles
            }

        return setTypeImage(
            image.path,
            image.width,
            image.height,
            offsetY,
            textPages,
            imgStyle
        )
    }

    /***
     * 根据图片设置TextLine/TextChar属性，将结果保存到textPages中，并返回offsetY，用于计算下一行内容
     */
    private fun setTypeImage(
        imgSrc: String, //这里就是绝对路径
        imgWidth: Int,
        imgHeight: Int,
        offsetY: Float,
        textPages: ArrayList<TextPage>,
        imageStyles: String
    ): Float {
        var durY: Float = offsetY
        Logger.d("ChapterProvider::setTypeImage::imgSrc=${imgSrc}, offsetY=$offsetY,imgWidth=$imgWidth, imgHeight=$imgHeight")
        if (imgSrc.isEmpty()) {
            Logger.d("ChapterProvider::setTypeImage::imgSrc=${imgSrc}, did not find the imgSrc, pass")
            return offsetY
        }
        val imgVerticalMargin = (contentPaint.textHeight * 1.1f).toInt() //垂直方向上,图片上下两边增加的间隔
        //图片显示可用高度, 页面高度 - 已经显示了的内容占据的偏移 - 底部的边距(paddingTop),
        //当图片占一屏显示时, 这个高度是完全展示的图片最大高度,
        //当图片非独占一屏时, 这个高度还需要 - 图片上下的一个间隔
        var usableHeight = (viewHeight - durY - paddingVertical).toInt()

        //图片约束之后的宽高
        val (originWidth, originHeight) = constraintImageSize(imgWidth, imgHeight, imgSrc, visibleWidth, visibleHeight)
        if (originWidth <= 0 || originHeight <= 0) {
            return durY
        }
        var width = originWidth
        var height = originHeight

        //是否需要新开一页用来显示图片
        // 如果是FULL 类型;
        // 如果 durY大于或者超过 visibleBottom, 即已经达到了一页的底部
        // 如果 (图片高度 + 图片上间隔) 超过了当前页面可展示的高度
        // 当前页不为空
        //---------------------------------------------------------------
        val isNewPage = if (textPages.last().textLines.isEmpty()) { //当前页本身就是一个空白页, 本身就是一个新页面
                true
            } else {
                if (durY >= visibleBottom ||
                    imgVerticalMargin + originHeight > usableHeight ||
                    imageStyles == "FULL") {  //当前可显示位置超过了可视高度
                    textPages.last().height = durY      //修改上一页的高度
                    textPages.add(TextPage())           //增加新一页
                    durY = paddingVertical.toFloat()         //修改当前页的距离顶部的偏移量
                    usableHeight = (viewHeight - durY - paddingVertical).toInt()
                    true
                } else {
                    false
                }
            }

        if (!isNewPage) {  //非新开的一页,
            //需要重新计算,防止超过了图片可用高度
            if (width + imgVerticalMargin > usableHeight) { //图片高度 + 图片的上边距 > 图片可显示高度
                //对图片再次缩放
                val (widthInPage, heightInPage) = constraintImageSize(imgWidth, imgHeight, imgSrc, visibleWidth, usableHeight - imgVerticalMargin)
                width = widthInPage
                height = heightInPage
            }
            durY += imgVerticalMargin  //先加上图片的上边距
        } else {
            if (imageStyles == "FULL") { //全屏展示时, 居中显示
                val (widthInPage, heightInPage) = fillImageSize(imgWidth, imgHeight, imgSrc, visibleWidth, visibleHeight)
                durY += (visibleHeight - heightInPage) / 2f
                width = widthInPage
                height = heightInPage
            }
        }

        //构建用于显示Image的TextLine
        val textLine = TextLine(isImage = true)
        textLine.lineTop = durY     //图片的顶部
        durY += height      //偏移加上图片的高度
        textLine.lineBottom = durY  //图片的底部

        //图片的左边和右边, 加上页面边距
        val (start, end) = if (visibleWidth > width) {      //图片显示宽度小于可视宽度
            val adjustWidth = (visibleWidth - width) / 2f   //坐偏移量
            Pair(
                paddingHorizontal.toFloat() + adjustWidth,
                paddingHorizontal.toFloat() + adjustWidth + width
            )
        } else {
            Pair(paddingHorizontal.toFloat(), (paddingHorizontal + width).toFloat())
        }
        Logger.d("ChapterProvider::setTypeImage::lineTop=${textLine.lineTop},lineBottom=${textLine.lineBottom},start=${start},end=${end}")

        textLine.textChars.add(
            TextChar(
                charData = imgSrc, //图片的本地完整路径
                start = start,      //图片的左位置
                end = end,          //图片的右位置
                isImage = true
            )
        )
        textPages.last().textLines.add(textLine)

        return durY + imgVerticalMargin  //加上图片的下边距
    }

    /****
     * 将图片的宽高缩放到最大宽高匹配的大小,让其填满宽度或者填满高度
     */
    private fun fillImageSize(imgWidth: Int,
                                 imgHeight: Int,
                                 imgSrc: String,
                                 maxWidth: Int,
                                 maxHeight: Int,): Pair<Int, Int>  {
        var originWidth = imgWidth
        var originHeight = imgHeight
        if (originWidth <= 0 || originHeight <= 0) {
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = true // 不加载图片像素，只获取宽高
            options.inSampleSize = 2
            BitmapFactory.decodeFile(imgSrc, options)
            originWidth = options.outWidth
            originHeight = options.outHeight
            if(originWidth <= 0 || originHeight <= 0) {
                Logger.e("ChapterProvider::constraintImageSize::decode image[$imgSrc][$imgWidth,$imgHeight] size failed")
                return Pair(0, 0)
            }
        }
        val radio: Float = (originWidth.toFloat()/ originHeight)  //宽高比

        var targetWidth = maxWidth
        var targetHeight = (targetWidth / radio).toInt()
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight
            targetWidth = (targetHeight * radio).toInt()
        }
        return Pair(targetWidth, targetHeight)
    }

    /****
     * 约束图片的宽高,
     * 和系统的displayMetrics.density 系数得到一个合适的缩放大小.
     * 然后约束到最大宽高范围之内, 即界面可视宽高之内,
     * 如果不超过,则显示默认宽高,
     * @param imgWidth : 图片的宽度
     * @param imgHeight : 图片的高度
     * @param imgSrc : 图片的路径
     * @param maxWidth: 最大图片的宽度
     * @param maxHeight : 最大图片的高度
     * @param useScale : 是否应用系统的density系数, 如果传入的是图片原始的宽高,则需要; 如果传入的是重新计算过的宽高,则不需要
     * @return 约束在最大宽高之内, 重新计算之后的图片的宽高
     */
    private fun constraintImageSize(
        imgWidth: Int,
        imgHeight: Int,
        imgSrc: String,
        maxWidth: Int,
        maxHeight: Int,
        useScale: Boolean = true
    ): Pair<Int, Int> {
        var originWidth = imgWidth
        var originHeight = imgHeight
        if (originWidth <= 0 || originHeight <= 0) {
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = true // 不加载图片像素，只获取宽高
            options.inSampleSize = 2
            BitmapFactory.decodeFile(imgSrc, options)
            originWidth = options.outWidth
            originHeight = options.outHeight
            if(originWidth <= 0 || originHeight <= 0) {
                Logger.e("ChapterProvider::constraintImageSize::decode image[$imgSrc][$imgWidth,$imgHeight] size failed")
                return Pair(0, 0)
            }
        }
        val scale = if (useScale) imgScale else 1.0f
        originWidth = (imgWidth * scale).toInt()  //图片的实际宽高
        originHeight = (imgHeight * scale).toInt()
        if (originWidth <= 0 || originHeight <= 0) {
            return Pair(0, 0)
        }
        val radio: Float = (originWidth.toFloat()/ originHeight)  //宽高比

        //约束到最大范围之内的宽高,不要超过一个屏幕
        if (originWidth > maxWidth) {
            originWidth = maxWidth
            originHeight = (originWidth / radio).toInt()
        }
        if (originHeight > maxHeight) {
            originHeight = maxHeight
            originWidth = (originHeight * radio).toInt()
        }

        return Pair(originWidth, originHeight)
    }

    private suspend fun setTypeText(
        paragraph: ReaderText,
        paragraphIndex: Int,    //段落在章节中的索引位置
        offsetY: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        isTitle: Boolean
    ): Float {
        var text: String = when (paragraph) {
            is ReaderText.Chapter -> paragraph.title
            is ReaderText.Text -> paragraph.line
            else -> ""
        }.toString()

        if (text.isEmpty() || text.isBlank()) { //对于无显示内容的空行，显示一个空白符
            text = "\u3000"
        }

        var durY = if (isTitle) offsetY + titleTopSpacing else offsetY

        val textPaint = TextPaint()
        val parentPaint = if (paragraph is ReaderText.Text) {
            val checkedTag = paragraph.annotations.firstOrNull { item ->
                item.name == "h1" ||
                        item.name == "h2" ||
                        item.name == "h3" ||
                        item.name == "a"
            }
            getPaintByTagName(checkedTag)
        } else if (paragraph is ReaderText.Chapter) {
            titlePaint
        } else {
            contentPaint
        }
        textPaint.set(parentPaint)

        val readerPrefs = readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()

        var marginLeft = 0f
        var marginRight = 0f
        var marginTop = 0f
        var marginBottom = 0f
        var firstLineIndent = 0f
        //对齐方式
        var textAlign: CssTextAlign =
            if (isTitle) {
                CssTextAlign.CssTextAlignCenter
            } else {
                CssTextAlign.CssTextAlignLeft
            }
        var lineHeightParam = 1f    //行高系数
        var oneWordWidth = 0f
        if (paragraph is ReaderText.Text) {
            //文字大小
            if (paragraph.textCssInfo.fontSize.isEm()) {
                textPaint.textSize *= paragraph.textCssInfo.fontSize.value
            } else if (paragraph.textCssInfo.fontSize.isPx()) {
                textPaint.textSize = paragraph.textCssInfo.fontSize.value
            }
            //文字粗体
            textPaint.typeface = getTypeface(paragraph.textCssInfo.fontWeight, paragraph.textCssInfo.fontStyle)
            textAlign = paragraph.textCssInfo.textAlign
            if (paragraph.textCssInfo.fontStyle == CssFontStyle.CssFontStyleItalic) {   //设置斜体
                textPaint.textSkewX = -0.25f
            }

            val userSetIndent = (readerPrefs?.paragraphIndent?.toFloat() ?: 0f)   //用户设置的首航缩进
            val textIndent = if (paragraph.textCssInfo.textIndent.isEm()) paragraph.textCssInfo.textIndent.value.toInt() else 0
            if (paragraph.textCssInfo.marginLeft.value > 0 ||
                paragraph.textCssInfo.marginRight.value > 0 ||
                paragraph.textCssInfo.marginTop.value > 0 ||
                paragraph.textCssInfo.marginBottom.value > 0 ||
                textIndent > 0 || userSetIndent > 0
            ) {

                if (oneWordWidth <= 0f) {
//                    for (index in 0..2) {
//                        val oneCh: String = (text.getOrNull(index)?.toString() ?: "\u3000")
                        val oneCh: String = "\u3000"
                        val width = StaticLayout.getDesiredWidth(oneCh, textPaint)
                        if (width > oneWordWidth) {
                            oneWordWidth = width
                        }
//                    }
                }
                //首行缩进
                val userSetIndent = (readerPrefs?.paragraphIndent?.toFloat() ?: 0f)   //用户设置的首航缩进
                firstLineIndent = (if (userSetIndent <= 0.0f) textIndent.toFloat() else userSetIndent) * oneWordWidth   //书籍自带的样式
//                Logger.d("ChapterProvider::textIndent[$textIndent],firstLineIndent[$firstLineIndent],oneEmWidth=$oneWordWidth")
                //左边距
                marginLeft = (if (paragraph.textCssInfo.marginLeft.isEm()) {
                    oneWordWidth * paragraph.textCssInfo.marginLeft.value
                } else if (paragraph.textCssInfo.marginLeft.isPx()) {
                    paragraph.textCssInfo.marginLeft.value
                } else if (paragraph.textCssInfo.marginLeft.isPercent()) {
                    visibleWidth * paragraph.textCssInfo.marginLeft.value
                } else {
                    0f
                }).coerceIn(0f, visibleWidth / 4f)
                //右边距
                marginRight = (if (paragraph.textCssInfo.marginRight.isEm()) {
                    val oneCh: String = (text.getOrNull(0)?.toString() ?: " ")
                    val oneEmWidth = StaticLayout.getDesiredWidth(oneCh, textPaint)
                    oneEmWidth * paragraph.textCssInfo.marginRight.value
                } else if (paragraph.textCssInfo.marginRight.isPx()) {
                    paragraph.textCssInfo.marginRight.value
                } else if (paragraph.textCssInfo.marginRight.isPercent()) {
                    visibleWidth * paragraph.textCssInfo.marginRight.value
                } else {
                    0f
                }).coerceIn(0f, visibleWidth / 4f)
                //上边距
                marginTop = (if (paragraph.textCssInfo.marginTop.isEm()) {
                    oneWordWidth * paragraph.textCssInfo.marginTop.value
                } else if (paragraph.textCssInfo.marginTop.isPx()) {
                    paragraph.textCssInfo.marginTop.value
                } else if (paragraph.textCssInfo.marginTop.isPercent()) {
                    visibleHeight * paragraph.textCssInfo.marginTop.value
                } else {
                    0f
                }).coerceIn(0f, visibleHeight / 4f)
                //下边距
                marginBottom = (if (paragraph.textCssInfo.marginBottom.isEm()) {
                    oneWordWidth * paragraph.textCssInfo.marginBottom.value
                } else if (paragraph.textCssInfo.marginBottom.isPx()) {
                    paragraph.textCssInfo.marginBottom.value
                } else if (paragraph.textCssInfo.marginBottom.isPercent()) {
                    visibleHeight * paragraph.textCssInfo.marginBottom.value
                } else {
                    0f
                }).coerceIn(0f, visibleHeight / 4f)
            }

            lineHeightParam = (if (paragraph.textCssInfo.lineHeight.isEm()) {
                paragraph.textCssInfo.lineHeight.value
            } else if (paragraph.textCssInfo.lineHeight.isPx()) {
                paragraph.textCssInfo.lineHeight.value / 48f    //48f 定义为标准大小 36/48
            } else {
                1f
            }).coerceIn(0.75f, 2.0f)    //限定范围在0.75, 2.0f 间
        }

        val hasInlineImg = if (paragraph is ReaderText.Text) {
            paragraph.annotations.firstOrNull { tag ->
                tag.name == "img" || tag.name == "image"
            } != null
        } else false

        //是否是列表，嵌套列表
        var isListRow: Boolean = false
        var listLevel: Int = 0
        isListRow = if (paragraph is ReaderText.Text) {
            paragraph.annotations.firstOrNull { tag ->
                tag.name == "li"
            } != null
        } else false
        if(isListRow) {
            if (paragraph is ReaderText.Text) {
                listLevel = paragraph.annotations.filter { tag ->
                    tag.name == "ul" || tag.name == "ol"
                }.size
                if (listLevel > 0) {
                    val lineHeight = textPaint.textHeight * lineSpacingExtra
                    marginLeft += listLevel * lineHeight
                    Logger.d("ChapterProvider::list::level=$listLevel")
                }
            }
        }

        //是否是表格行
        val isTableRow: Boolean = if (paragraph is ReaderText.Text) {
            paragraph.annotations.firstOrNull { tag ->
                tag.name == "tr"
            } != null
        } else false

        if (!isTableRow && !isListRow) {
            val userSetParagraphSpacing = readerPrefs?.paragraphSpacing?.toFloat() ?: 0.0f
//            Logger.d("ChapterProvider::userSetParagraphSpacing=$userSetParagraphSpacing")
            durY += if (userSetParagraphSpacing > 0) (userSetParagraphSpacing * textPaint.textHeight) else marginTop
        }

        if (isTableRow) {               //是表格行
            durY = setTextTable(
                paragraph,
                textPaint,
                marginLeft,
                marginRight,
                paragraphIndex,
                textAlign,
                lineHeightParam,
                textPages,
                pageLines,
                pageLengths,
                stringBuilder,
                durY
            )
        } else if (hasInlineImg) {     //有段落内的图片
            durY = setTextWithInnerImg(
                paragraph,
                textPaint,
                marginLeft,
                marginRight,
                firstLineIndent,
                paragraphIndex,
                textAlign,
                lineHeightParam,
                textPages,
                pageLines,
                pageLengths,
                stringBuilder,
                durY
            )
        } else {                    //没有段落内的图片
            durY = setNormalText(
                text,
                textPaint,
                marginLeft,
                marginRight,
                firstLineIndent,
                isTitle,
                isListRow,
                listLevel,
                paragraphIndex,
                textAlign,
                lineHeightParam,
                textPages,
                pageLines,
                pageLengths,
                stringBuilder,
                durY
            )
        }

        if (!isTableRow && !isListRow) {
            //一个自然段落遍历完
            if (isTitle) {
                durY += titleBottomSpacing                          //是标题行，则加上标题的底部间距
            }
            if (marginBottom > 0f) {
                durY += marginBottom
            }
        }
//        durY += textPaint.textHeight * paragraphSpacing   //是段落，则加上段落间距 //TODO
        return durY
    }

    private suspend fun setTextTable(
        paragraph: ReaderText,
        textPaint: TextPaint,
        marginLeft: Float,
        marginRight: Float,
        paragraphIndex: Int,
        textAlign: CssTextAlign,
        lineHeightParam: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        offsetY: Float
    ): Float {
        var durY = offsetY
        if (paragraph is ReaderText.Text) {
            val tagTable = paragraph.annotations.firstOrNull { tag ->
                tag.name == "table"
            }
            val tagTr = paragraph.annotations.firstOrNull { tag ->
                tag.name == "tr"
            }
            val tagCells = paragraph.annotations.filter { tag ->
                tag.name == "td" || tag.name == "th"
            }
            if (tagCells.isNotEmpty()) {
                var rows = 0    //表格行数
                var cols = 0    //表格列数
                var tablePercents = arrayListOf<Int>()   //每一行所占的百分比
                tagTable?.paramsPairs()?.forEach { param ->
                    if (param.first == "cols") {
                        cols = param.second.toIntOrNull() ?: 0
                    } else if (param.first == "rows") {
                        rows = param.second.toIntOrNull() ?: 0
                    } else if (param.first == "table_percent") {
                        val pers = param.second.split(";")
                        if (pers.isNotEmpty()) {
                            for (per in pers) {
                                if (per.endsWith("%")) {
                                    tablePercents.add(per.substring(0, per.length - 1).toIntOrNull() ?: 0)
                                }
                            }
                        }
                    }
                }
                //当前行索引
                val rowIndex = tagTr?.paramsPairs()?.firstOrNull { param ->
                    param.first == "index"
                }?.second?.toIntOrNull() ?: 0
//                Logger.d("ChapterProvider::rows=$rows,cols=$cols,rowIndex=$rowIndex")
                if (tagCells.size == tablePercents.size) { //
                    val tableCellInnerPadding = 10          //表格单元格内的左右padding
                    var leftOffsetPercent: Int = 0  //距离左边的宽度的百分比
                    val fullWidth = visibleWidth - marginLeft.roundToInt() - marginRight.roundToInt()
                    var maxLineCount = 1 //最大行数，用来计算一行的高度
                    var textLineMaps = hashMapOf<Int, ArrayList<TextLine>>()  //遍历完，用来合并TextLine
                    //每个单元格
                    for (index in 0 until tagCells.size) {
                        val tagCell = tagCells[index]
                        val tagPercent: Int = tablePercents[index] //当前单元格所占的宽度的百分比,
//                        Logger.d("ChapterProvider::setTextTable::line[${paragraph.line}],tagCell[$tagCell],index[$index],tagPercent=$tagPercent")
                        val text = if (tagCell.start in 0 until paragraph.line.length && tagCell.end in 0 .. paragraph.line.length) {
                            paragraph.line.substring(tagCell.start, tagCell.end)
                        } else if (tagCell.start in 0 until paragraph.line.length && tagCell.end > paragraph.line.length){
                            paragraph.line.substring(tagCell.start)
                        } else {
                            ""
                        }

                        val usableWidth = (fullWidth * (tagPercent / 100f) - 2 * tableCellInnerPadding).toInt()   //可用宽度
                        val leftOffset = (fullWidth * (leftOffsetPercent / 100f) + tableCellInnerPadding).toInt()    //距离屏幕左边的偏移位置
                        var rightOffset = visibleRight - (usableWidth + leftOffset)     //距离屏幕右边的偏移量
                        if (rightOffset < 0) {
                            rightOffset = 0
                        }

                        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, usableWidth)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setIncludePad(true)
                            .build()
                        val lineCount = layout.lineCount
                        if (lineCount > maxLineCount) {
                            maxLineCount = lineCount
                        }
                        //每个单元格的字符串，生成多行的情况，每一行都是一个TextLine
                        for (lineIndex in 0 until layout.lineCount) {
                            val offsetStart = layout.getLineStart(lineIndex)
                            val offsetEnd = layout.getLineEnd(lineIndex)
                            val textLine = TextLine(
                                isTitle = false,
                                paragraphIndex = paragraphIndex,
                                charStartOffset = offsetStart,
                                charEndOffset = offsetEnd,
                                rowIndex = rowIndex,
                                colIndex = index,
                                rowLineOffset = tagCell.start,
                                isTableCell = true
                            )
                            val words = text.substring(offsetStart, offsetEnd)
                            textLine.text = words
                            val desiredWidth = layout.getLineWidth(lineIndex)   //排版要求的宽度
//                                var isLastLine = (lineIndex == layout.lineCount - 1)
                            when (textAlign) {
                                CssTextAlign.CssTextAlignLeft, CssTextAlign.CssTextAlignJustify -> addCharsToLineLeft(
                                    textLine,
                                    words.toStringArray(),
                                    textPaint,
                                    leftOffset.toFloat() + marginLeft
                                )

                                CssTextAlign.CssTextAlignRight -> addCharsToLineRight(
                                    textLine,
                                    words.toStringArray(),
                                    textPaint,
                                    desiredWidth,
                                    rightOffset.toFloat()
                                )

                                CssTextAlign.CssTextAlignCenter -> {
                                    addCharsToLineLeft(
                                        textLine, words.toStringArray(), textPaint,
                                        leftOffset + marginLeft + (usableWidth - desiredWidth) / 2f
                                    )
                                }
                            }
                            if (textLineMaps.get(lineIndex) == null) {
                                textLineMaps[lineIndex] = arrayListOf<TextLine>()
                            }
                            textLineMaps.get(lineIndex)?.add(textLine)
                        }
                        leftOffsetPercent += tagPercent
                    }

                    val lines: List<Int> = textLineMaps.keys.toList().sorted()
                    for ((index, line) in lines.withIndex()) { //按行处理不同单元格的内容
                        val lineHeight = textPaint.textHeight * lineSpacingExtra * lineHeightParam
                        val textLines = textLineMaps.get(line).orEmpty()
                        //新增加的表格行，如果超过了一页的显示高度，则创建新页
                        if (durY + lineHeight > visibleHeight) {
                            val lastPage = textPages.last()
                            lastPage.text = stringBuilder.toString()
                            pageLines.add(lastPage.textLines.size)
                            pageLengths.add(lastPage.text.length)
                            lastPage.height = durY

                            textPages.add(TextPage())
                            stringBuilder.clear()
                            durY = paddingVertical.toFloat()
                        }

                        var words = StringBuilder()
                        textLines.forEach {
                            if (!words.isEmpty()) {
                                words.append("\t")
                            }
                            words.append(it.text)
                        }
                        stringBuilder.append(words)
                        val lastLine = (index == lines.size - 1)
                        if (lastLine) {
                            stringBuilder.append("\n")
                        }
                        val lastPage = textPages.last()
                        textLines.forEach {
                            it.upTopBottom(durY, textPaint)
                        }
                        lastPage.textLines.addAll(textLines)

                        //增加表格的边框线
                        if (index == 0) {
                            //横线， 上面的一条横线
                            lastPage.textLines.add(
                                TextLine(
                                    isLine = true,
                                    lineStart = Pair(marginLeft + paddingHorizontal, durY),
                                    lineEnd = Pair(visibleRight - marginRight, durY),
                                    lineBorder = 1f,
                                    lineColor = "#333333"
                                )
                            )
                        }

                        //竖线
                        var leftPercent = 0f
                        var percents = arrayListOf<Int>()
                        percents.add(0)
                        percents.addAll(tablePercents)
                        for ((iline, percent) in percents.withIndex()) {
                            if (iline == percents.size -1) {
                                lastPage.textLines.add(
                                    TextLine(
                                        isLine = true,
                                        lineStart = Pair(visibleRight - marginRight, durY),
                                        lineEnd = Pair(visibleRight - marginRight, durY + lineHeight),
                                        lineBorder = 1f,
                                        lineColor = "#333333"
                                    )
                                )
                            } else {
                                leftPercent += percent
                                val left = fullWidth * (leftPercent / 100f)
                                lastPage.textLines.add(
                                    TextLine(
                                        isLine = true,
                                        lineStart = Pair(left + paddingHorizontal + marginLeft, durY),
                                        lineEnd = Pair(left + paddingHorizontal + marginLeft, durY + lineHeight),
                                        lineBorder = 1f,
                                        lineColor = "#333333"
                                    )
                                )
                            }
                        }

                        if (rowIndex == rows - 1 && index == lines.size - 1) { //最后一条数据的底部的横线
                            lastPage.textLines.add(
                                TextLine(
                                    isLine = true,
                                    lineStart = Pair(marginLeft + paddingHorizontal, durY + lineHeight),
                                    lineEnd = Pair(visibleRight - marginRight, durY + lineHeight),
                                    lineBorder = 1f,
                                    lineColor = "#333333"
                                )
                            )
                        }

                        durY += lineHeight
                        lastPage.height = durY
                    }
                } else {
                    /* 暂时不考虑跨行或者跨列的情况 */
                }
            }
        }
        return durY
    }

    private suspend fun setTextWithInnerImg(
        paragraph: ReaderText,
        textPaint: TextPaint,
        marginLeft: Float,
        marginRight: Float,
        firstLineIndent: Float,
        paragraphIndex: Int,
        textAlign: CssTextAlign,
        lineHeightParam: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        offsetY: Float
    ): Float {
        var durY = offsetY
        if (paragraph is ReaderText.Text) {
            //拿到行内图片
            var imgTags = paragraph.annotations.filter { tag ->
                tag.name == "img" || tag.name == "image"
            }
            //根据图片的位置把段落分割成多个小段
            val texts = arrayListOf<String>()
            val allTags = paragraph.annotations
            if (!imgTags.isEmpty()) {
                var lastEnd = 0
                imgTags = imgTags.sortedBy { it.start }
                for ((index, imgTag) in imgTags.withIndex()) {
                    if (index < imgTags.size - 1) {
                        if (imgTag.start > 0 && imgTag.start < paragraph.line.length) {
                            texts.add(paragraph.line.substring(lastEnd, imgTag.start))
                            lastEnd = imgTag.start
                        }
                    } else {
                        if (imgTag.start > 0 && imgTag.start < paragraph.line.length) { //最后一个位置
                            texts.add(paragraph.line.substring(lastEnd, imgTag.start))
                            texts.add(paragraph.line.substring(imgTag.start))
                        }
                    }

                    //添加了图片作为一个字符,那么annotations中的标记索引都需要增加一个字符的偏移量了
                    for(tag in allTags) {
                        if (tag != imgTag) {
                            if (tag.start > imgTag.start) {
                                tag.start += 1
                            }
                            if (tag.end > imgTag.start) {
                                tag.end += 1
                            }
                        }
                    }
                }
            }
            var morePartIndent = firstLineIndent
            var newPartWithNewLine = true   //新的分段是否需要新的分行，
            var latestPartLine: TextLine? = null
            var pentingImg: TextTag? = null //上一小分段中，图片塞不下了， 放入到下一行第一个位置显示
            for ((partIndex, text) in texts.withIndex()) {  //对每一部分都进行布局测量

                val layout = StaticLayout.Builder.obtain(
                    text, 0, text.length,
                    textPaint,
                    visibleWidth - marginLeft.roundToInt() - marginRight.roundToInt()
                )
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(true)
                    .setIndents(
                        intArrayOf(morePartIndent.toInt(), 0),
                        intArrayOf(0, 0)
                    )
                    .build()

                for (layoutLineIndex in 0 until layout.lineCount) {  //测量得到行数, 对每一行进行遍历  //排版，按行遍历
                    val offsetStart = layout.getLineStart(layoutLineIndex)
                    val offsetEnd = layout.getLineEnd(layoutLineIndex)
                    val textLine = if (layoutLineIndex == 0 && partIndex > 0 && latestPartLine != null) {
                        latestPartLine
                    } else {
                        //这里需要加上前面几段的文字的偏移
                        var offsetLast = 0
                        if(partIndex > 0) {
                            for ((pIndex, pText) in texts.withIndex()) {
                                if (pIndex < partIndex) {
                                    offsetLast += (pText.length + 1) //每一段后面都会追加1个图片占位字.
                                } else {
                                    break
                                }
                            }
                        }
                        TextLine(isTitle = false, paragraphIndex = paragraphIndex, charStartOffset = offsetLast + offsetStart, charEndOffset = offsetLast + offsetEnd)
                    }
                    if (latestPartLine != null) {
                        latestPartLine = null
                    }
                    if (pentingImg != null) {
                        val pairs = pentingImg.paramsPairs()
                        val imgSrc = pairs.firstOrNull { it.first == "src" }?.second.orEmpty()
                        val width = pairs.firstOrNull { it.first == "width" }?.second?.toIntOrNull() ?: 0
                        val height = pairs.firstOrNull { it.first == "height" }?.second?.toIntOrNull() ?: 0
                        if (imgSrc.isNotEmpty() && width > 0 && height > 0) {
                            //根据行高度, 缩放图片的宽度
                            val targetImgHeight = (textPaint.textHeight * lineSpacingExtra).toInt()
                            val (imgW, imgH) = fillImageSize(width, height, imgSrc, 2 * targetImgHeight, targetImgHeight)
                            textLine.textChars.add(TextChar(imgSrc, morePartIndent, morePartIndent + imgW, false, true))
                        }
                        pentingImg = null
                    }
                    val layoutLineWords = text.substring(offsetStart, offsetEnd)

                    textLine.text += layoutLineWords        //  //增加一次换行
                    val desiredWidth = layout.getLineWidth(layoutLineIndex)   //排版要求的宽度
                    var isLastLine = (layoutLineIndex == layout.lineCount - 1)

                    when (textAlign) {
                        CssTextAlign.CssTextAlignLeft, CssTextAlign.CssTextAlignJustify -> addCharsToLineLeft(
                            textLine,
                            layoutLineWords.toStringArray(),
                            textPaint,
                            marginLeft + (if (layoutLineIndex == 0) morePartIndent else 0f)
                        )

                        CssTextAlign.CssTextAlignRight -> addCharsToLineRight(textLine, layoutLineWords.toStringArray(), textPaint, desiredWidth, marginRight)
                        CssTextAlign.CssTextAlignCenter -> addCharsToLineCenter(textLine, layoutLineWords.toStringArray(), textPaint, desiredWidth)
//                        CssTextAlign.CssTextAlignJustify -> {
//                            if (layout.lineCount == 1) {
//                                addCharsToLineLeft(textLine, words.toStringArray(), textPaint, marginLeft)
//                            } else {
//                                if (isLastLine) {    //两端对齐，除了最后一行
//                                    addCharsToLineLeft(textLine, words.toStringArray(), textPaint, marginLeft)
//                                } else {
//                                    addCharsToLineMiddle(textLine, words.toStringArray(), textPaint, desiredWidth, marginLeft)
//                                }
//                            }
//                        }
                    }

                    //新增加的行，超过了一页的显示高度, 则创建新页
                    if (durY + textPaint.textHeight * lineSpacingExtra * lineHeightParam > visibleBottom) {
                        val lastPage = textPages.last()
                        lastPage.text = stringBuilder.toString()
                        pageLines.add(lastPage.textLines.size)
                        pageLengths.add(lastPage.text.length)
                        lastPage.height = durY

                        textPages.add(TextPage())
                        stringBuilder.clear()
                        durY = paddingVertical.toFloat()
                    }

                    stringBuilder.append(layoutLineWords)

                    if (isLastLine) {
                        if (partIndex == texts.size - 1) {
                            stringBuilder.append("\n")  //段落的最后一行，增加换行符
                        } else {
                            val lastLineEnd = textLine.textChars.last().end
                            imgTags.getOrNull(partIndex)?.let { imgTag ->
                                val pairs = imgTag.paramsPairs()

                                val imgWidth = pairs.firstOrNull { it.first == "width" }?.second?.toIntOrNull() ?: 0
                                val imgHeight = pairs.firstOrNull { it.first == "height" }?.second?.toIntOrNull() ?: 0
                                val imgSrc = pairs.firstOrNull { it.first == "src" }?.second.orEmpty()
                                val lineWidth = visibleWidth - marginLeft.roundToInt() - marginRight.roundToInt()
                                if (imgWidth > 0 && imgHeight > 0 && imgSrc.isNotEmpty()) {
                                    val targetImgHeight = (textPaint.textHeight * lineSpacingExtra).toInt()
                                    //根据行高度, 缩放图片的宽度
                                    val (imgW, imgH) = fillImageSize(imgWidth, imgHeight, imgSrc, 2 * targetImgHeight, targetImgHeight)
                                    Logger.d("ChapterProvider::imgTag[$imgTag],img.size=[$imgWidth,$imgHeight],calc size[$imgW, $imgH], lastLineEnd=$lastLineEnd,morePartIndent=$morePartIndent")
                                    val newLineEnd = lastLineEnd + imgW - paddingHorizontal
                                    if (newLineEnd < lineWidth) {
                                        morePartIndent = newLineEnd
                                        newPartWithNewLine = false
                                        textLine.textChars.add(TextChar(imgSrc, lastLineEnd, lastLineEnd + imgW, false, true))
                                    } else {
                                        morePartIndent = imgW.toFloat()
                                        newPartWithNewLine = true
                                        pentingImg = imgTag
                                    }
//                                    Logger.d("ChapterProvider::imgTag[$imgTag],imgWidth=$imgWidth,lastLineEnd=$lastLineEnd,morePartIndent=$morePartIndent,newPartWithNewLine=$newPartWithNewLine")
                                }
                            }
                        }
                    }

                    if (newPartWithNewLine) {
                        val lastPage = textPages.last()
                        lastPage.textLines.add(textLine)    //将新生成的一行加入到最后一页中
                        textLine.upTopBottom(durY, textPaint)       //设置行的上，下，以及基线位置
//            durY += textPaint.textHeight * lineSpacingExtra * lineHeightParam   //将行高度，行间距加入到durY值中
                        durY += textPaint.textHeight * lineSpacingExtra   //将行高度，行间距加入到durY值中
                        lastPage.height = durY
                    } else {
                        latestPartLine = textLine
                        newPartWithNewLine = true
                    }
                }
            }
        }
        return durY
    }


    /***
     * 只有文本内容的Text的测量
     */
    private suspend fun setNormalText(
        text: String,
        textPaint: TextPaint,
        marginLeft: Float,
        marginRight: Float,
        firstLineIndent: Float,
        isTitle: Boolean,
        isListRow : Boolean,
        listLevel : Int,
        paragraphIndex: Int,
        textAlign: CssTextAlign,
        lineHeightParam: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        offsetY: Float
    ): Float {
        var durY = offsetY
        val layout = StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            visibleWidth - marginLeft.roundToInt() - marginRight.roundToInt()
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setIndents(intArrayOf(firstLineIndent.toInt(), 0), intArrayOf(0, 0))
            .build()

        for (lineIndex in 0 until layout.lineCount) {  //排版，按行遍历
            val offsetStart = layout.getLineStart(lineIndex)
            val offsetEnd = layout.getLineEnd(lineIndex)
            val textLine = TextLine(isTitle = isTitle, paragraphIndex = paragraphIndex, charStartOffset = offsetStart, charEndOffset = offsetEnd)
            val words = text.substring(offsetStart, offsetEnd)
//            textLine.text = if (lineIndex == layout.lineCount - 1) "$words\n" else words        //  //增加一次换行
            textLine.text = words        //  //增加一次换行
            val desiredWidth = layout.getLineWidth(lineIndex)   //排版要求的宽度
            var isLastLine = (lineIndex == layout.lineCount - 1)

            when (textAlign) {
                CssTextAlign.CssTextAlignLeft -> addCharsToLineLeft(
                    textLine,
                    words.toStringArray(),
                    textPaint,
                    marginLeft + (if (lineIndex == 0) firstLineIndent.toFloat() else 0f)
                )

                CssTextAlign.CssTextAlignRight -> addCharsToLineRight(textLine, words.toStringArray(), textPaint, desiredWidth, marginRight)
                CssTextAlign.CssTextAlignCenter -> addCharsToLineCenter(textLine, words.toStringArray(), textPaint, desiredWidth)
                CssTextAlign.CssTextAlignJustify -> {
                    if (layout.lineCount == 1) {
                        addCharsToLineLeft(textLine, words.toStringArray(), textPaint, marginLeft + (if (lineIndex == 0) firstLineIndent.toFloat() else 0f))
                    } else {
                        if (isLastLine) {    //两端对齐，除了最后一行
                            addCharsToLineLeft(
                                textLine,
                                words.toStringArray(),
                                textPaint,
                                marginLeft + (if (lineIndex == 0) firstLineIndent.toFloat() else 0f)
                            )
                        } else {
                            addCharsToLineMiddle(
                                textLine,
                                words.toStringArray(),
                                textPaint,
                                desiredWidth,
                                marginLeft + (if (lineIndex == 0) firstLineIndent.toFloat() else 0f)
                            )
                        }
                    }
                }
            }

            //新增加的行，超过了一页的显示高度, 则创建新页
            if (durY + textPaint.textHeight * lineSpacingExtra * lineHeightParam > visibleBottom) {
                val lastPage = textPages.last()
                lastPage.text = stringBuilder.toString()
                pageLines.add(lastPage.textLines.size)
                pageLengths.add(lastPage.text.length)
                lastPage.height = durY

                textPages.add(TextPage())
                stringBuilder.clear()
                durY = paddingVertical.toFloat()
            }

            if (lineIndex == 0 && isListRow && listLevel > 0) { //第一行，并且是列表
                textLine.withLineDot = listLevel
            }

            stringBuilder.append(words)
            if (isLastLine) stringBuilder.append("\n")  //段落的最后一行，增加换行符

            val lastPage = textPages.last()
            lastPage.textLines.add(textLine)    //将新生成的一行加入到最后一页中
            textLine.upTopBottom(durY, textPaint)       //设置行的上，下，以及基线位置
//            durY += textPaint.textHeight * lineSpacingExtra * lineHeightParam   //将行高度，行间距加入到durY值中
            durY += textPaint.textHeight * lineSpacingExtra   //将行高度，行间距加入到durY值中
            lastPage.height = durY
        }
        return durY
    }

    /****
     * 段落的中间行， 两端对齐
     */
    private suspend fun addCharsToLineMiddle(textLine: TextLine, words: Array<String>, textPaint: TextPaint, desiredWidth: Float, offsetX: Float) {
//        val tipPreference = readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()
//        val textFullJustify = tipPreference?.textFullJustify == true
//        if (!textFullJustify) { //非两端对齐, 即左对齐
//            addCharsToLineLeft(textLine, words, textPaint, offsetX)
//            return
//        }

        //两端对齐显示
        val gapCount: Int = words.lastIndex                 //空白间隔的个数
        val gapWidth = (visibleWidth - desiredWidth) / gapCount    //得到每个间隔的平均宽度
        var x = offsetX
        words.forEachIndexed { index, char ->               //遍历每个显示的字符
            val chWidth: Float = StaticLayout.getDesiredWidth(char, textPaint) //单个字符的显示宽度
            val x1 = if (index != words.lastIndex) {
                x + chWidth + gapWidth
            } else {
                x + chWidth
            }
            textLine.addTextChar(charData = char, start = paddingHorizontal + x, end = paddingHorizontal + x1)
            x = x1
        }
        exceed(textLine, words)
    }

    /****
     * 从左向右自然排列一行字符, 即左对齐
     */
    private fun addCharsToLineLeft(textLine: TextLine, words: Array<String>, textPaint: TextPaint, offsetX: Float) {
        var x = offsetX
        words.forEach { char ->
            val cw = StaticLayout.getDesiredWidth(char, textPaint)
            val x1 = x + cw
            textLine.addTextChar(charData = char, start = paddingHorizontal + x, end = paddingHorizontal + x1)
            x = x1
        }
        exceed(textLine, words)
    }

    /**
     * 居中显示文本
     */
    private fun addCharsToLineCenter(textLine: TextLine, words: Array<String>, textPaint: TextPaint, desiredWidth: Float) {
        val x = (visibleWidth - desiredWidth) / 2  //标题栏居中显示，左偏移
        addCharsToLineLeft(textLine, words, textPaint, x)
    }

    /**
     * 右对齐显示文本
     */
    private fun addCharsToLineRight(textLine: TextLine, words: Array<String>, textPaint: TextPaint, desiredWidth: Float, marginRight: Float) {
        val x = visibleWidth - desiredWidth - marginRight  //标题栏居中显示，左偏移
        addCharsToLineLeft(textLine, words, textPaint, x)
    }


    /****
     * 显示的一行内容，计算的偏移位置检测是否超过了边界， 对偏移进行纠偏
     */
    private fun exceed(textLine: TextLine, words: Array<String>) {
        val endX = textLine.textChars.lastOrNull()?.end ?: 0f    //一行的最后一个字符显示的左边位置
        if (endX > visibleRight.toFloat()) {    //超过了可视区域的右侧
            val diff = (endX - visibleRight) / words.size    //将超过的偏移量分配到每个字符上，然后对显示的一行每个字符位置进行修正
            for (index in 0..words.lastIndex) {
                val textChar = textLine.getTextCharReverseAt(index) //反方向上进行
                val offset = diff * (words.size - index)
                textChar.start = textChar.start - offset
                textChar.end = textChar.end - offset
            }
        }
    }

    /***
     * 设置View尺寸
     */
    fun setViewSize(context: Context, width: Int, height: Int) {
        Logger.d("ChapterProvider::setViewSize,width=$width, height=$height")
        val refreshStyle = (width != viewWidth || height != viewHeight)
        if (width > 0 && height > 0 && refreshStyle) {
            viewWidth = width
            viewHeight = height
            Coroutines.mainScope().launch {
                upVisibleSize(context)
                upStyle(context)
            }
        }
        Logger.d("ChapterProvider::setViewSize,viewWidth=$viewWidth, viewHeight=$viewHeight")
    }

    fun init(context: Context, readTipPreferencesUtil: ReadTipPreferencesUtil, readerPreferencesUtil: ReaderPreferencesUtil) {
        Logger.d("ChapterProvider::init,then invoke ChapterProvider::upStyle")
        this.readTipPreferencesUtil = readTipPreferencesUtil
        this.readerPreferencesUtil = readerPreferencesUtil
        upStyle(context)
    }
}