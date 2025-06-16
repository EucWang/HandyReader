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
     * 页面显示宽度
     */
    private var viewWidth = 0

    /***
     * 页面显示高度
     */
    private var viewHeight = 0

    /***
     * 左边距
     */
    var paddingLeft = 0

    /***
     * 上边距
     */
    var paddingTop = 0

    /**
     * 可视宽度, 这里是排除掉了左右padding之后的宽度，而不是屏幕宽度
     */
    var visibleWidth = 0

    /***
     * 可视高度
     */
    var visibleHeight = 0

    /***
     * 可视的右边位置
     */
    var visibleRight = 0

    /***
     * 可视底部位置
     */
    var visibleBottom = 0

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

    fun getTypeface(fontWeight: CssFontWeight, cssFontStyle: CssFontStyle) =
        when (fontWeight) {
            CssFontWeight.FontWeightNormal -> {
                Typeface.create(
                    typeface, if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                        Typeface.ITALIC
                    } else {
                        Typeface.NORMAL
                    }
                )
            }

            CssFontWeight.FontWeightBold -> {
                Typeface.create(
                    typeface, if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                        Typeface.BOLD_ITALIC
                    } else {
                        Typeface.BOLD
                    }
                )
            }

            CssFontWeight.FontWeightBolder -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(typeface, 900, cssFontStyle == CssFontStyle.CssFontStyleItalic)
                } else {
                    Typeface.create(
                        typeface, if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                            Typeface.BOLD_ITALIC
                        } else {
                            Typeface.BOLD
                        }
                    )
                }
            }

            CssFontWeight.FontWeightLighter -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Typeface.create(typeface, 300, cssFontStyle == CssFontStyle.CssFontStyleItalic)
                } else {
                    Typeface.create(
                        typeface, if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                            Typeface.ITALIC
                        } else {
                            Typeface.NORMAL
                        }
                    )
                }
            }
        }


    /****
     * 根据TextTag的name属性，得到对应的TextPaint
     */
    fun getPaintByTagName(tagName: String?, default: TextPaint? = null): TextPaint {
        return when (tagName) {
            "h1" -> h1Paint
            "h2" -> h2Paint
            "h3" -> h3Paint
            "h4" -> h4Paint
            "a" -> aPaint
            else -> default ?: contentPaint
        }
    }

    fun tryCreatePreference(context: Context) {
        if (readerPreferencesUtil == null) {
            readerPreferencesUtil = ReaderPreferencesUtil(context)
        }
        if (readTipPreferencesUtil == null) {
            readTipPreferencesUtil = ReadTipPreferencesUtil(context)
        }
    }

    /**
     * 更新绘制尺寸
     */
    private suspend fun upVisibleSize(context: Context) {
        Logger.i("ChapterProvider:upVisibleSize")
        tryCreatePreference(context)

        if (viewWidth == 0 || viewHeight == 0) {
            val metrics = context.resources.displayMetrics
            viewWidth = metrics.widthPixels
            viewHeight = metrics.heightPixels - context.statusBarHeight
            Logger.d("ChapterProvider::set screen size to view::viewWidth=$viewWidth,viewHeight=$viewHeight")
        }

        val readerPreferences = readerPreferencesUtil?.readerPreferencesFlow?.firstOrNull()
        if (viewWidth > 0 && viewHeight > 0) {
            paddingLeft = (((readerPreferences?.pageHorizontalMargins?.toDouble() ?: 0.0) * 0.1 * viewWidth.toDouble()).toInt()) / 2         //页面左边距
            paddingTop = ((readerPreferences?.pageVerticalMargins ?: 0.0) * 0.1 * viewHeight.toDouble()).toInt() / 2                 //页面顶部间距
            visibleWidth = (viewWidth - paddingLeft * 2).toInt()                                //可视宽度
            visibleHeight = (viewHeight - paddingTop * 2).toInt()                            //可视高度
            visibleRight = paddingLeft + visibleWidth                                       //可视右边
            visibleBottom = paddingTop + visibleHeight                                      //可视底部
        }
        Logger.d("ChapterProvider::upVisibleSize::viewWidth=$viewWidth, viewHeight=$viewHeight, visibleWidth=$visibleWidth,visibleHeight=$visibleHeight,visibleRight=$visibleRight,visibleBottom=$visibleBottom")
    }

    /**
     * 更新样式
     */
    fun upStyle(context: Context) {
        Logger.i("ChapterProvider::upStyle")
        Coroutines.mainScope().launch {
            tryCreatePreference(context)
            val readerPreferences = readerPreferencesUtil?.readerPreferencesFlow?.firstOrNull()
            Logger.d("ChapterProvider::upStyle::readerPreferences =${readerPreferences}")

            //更新字体
            typeface = try {
                val fontPath = readerPreferences?.font.orEmpty()
                Logger.d("ChapterProvider::upStyle::fontPath=$fontPath")
//            val fontPath = ReadBookConfig.textFont  //字体路径
                when {
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
//                else -> when (AppConfig.systemTypefaces) {
//                    1 -> Typeface.SERIF
//                    2 -> Typeface.MONOSPACE
//                    else -> Typeface.SANS_SERIF
//                }
                    else -> Typeface.SANS_SERIF
                }
            } catch (e: Exception) {
//            ReadBookConfig.textFont = ""
//            ReadBookConfig.save()
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        Pair(Typeface.create(typeface, 900, false), bold)
                    else
                        Pair(bold, bold)
                }

                2 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                        Pair(normal, Typeface.create(typeface, 300, false))
                    else
                        Pair(normal, normal)
                }

                else -> Pair(bold, normal)
            }

            //标题的Paint
            titlePaint = TextPaint()
            titlePaint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${titlePaint.color.toString(16)}")
            titlePaint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${titlePaint.letterSpacing}")
            titlePaint.typeface = titleFont                                                     //设置标题字体
//        titlePaint.textSize = with(ReadBookConfig) { textSize + titleSize }.sp.toFloat()    //设置标题字体大小
            titlePaint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE
            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${titlePaint.textSize}")
            titlePaint.isAntiAlias = true                                                       //设置抗锯齿

            //h1的Paint
            h1Paint = TextPaint()
            h1Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h1Paint.color.toString(16)}")
            h1Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h1Paint.letterSpacing}")
            h1Paint.typeface = titleFont                                                     //设置标题字体
            h1Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.95f
            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h1Paint.textSize}")
            h1Paint.isAntiAlias = true                                                       //设置抗锯齿

            //h2的Paint
            h2Paint = TextPaint()
            h2Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h2Paint.color.toString(16)}")
            h2Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h2Paint.letterSpacing}")
            h2Paint.typeface = titleFont                                                     //设置标题字体
            h2Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.9f
            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h2Paint.textSize}")
            h2Paint.isAntiAlias = true                                                       //设置抗锯齿

            //h3的Paint
            h3Paint = TextPaint()
            h3Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h3Paint.color.toString(16)}")
            h3Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h3Paint.letterSpacing}")
            h3Paint.typeface = titleFont                                                     //设置标题字体
            h3Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.8f
            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h3Paint.textSize}")
            h3Paint.isAntiAlias = true                                                       //设置抗锯齿

            //h4的Paint
            h4Paint = TextPaint()
            h4Paint.color = readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
            Logger.d("ChapterProvider::upStyle::titlePaint.color=0x${h4Paint.color.toString(16)}")
            h4Paint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
            Logger.d("ChapterProvider::upStyle::titlePaint.letterSpacing=${h4Paint.letterSpacing}")
            h4Paint.typeface = titleFont                                                     //设置标题字体
            h4Paint.textSize = (readerPreferences?.titleSize?.toFloat() ?: 1.0f) * BASE_TITLE_FONT_SIZE * 0.7f
            Logger.d("ChapterProvider::upStyle::titlePaint.textSize=${h4Paint.textSize}")
            h4Paint.isAntiAlias = true                                                       //设置抗锯齿

            //正文的Paint
            contentPaint = TextPaint()
            contentPaint.color = readerPreferences?.textColor ?: Color.BLACK                    //设置正文文字颜色
            contentPaint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0.0f               //设置正文文字间距
            Logger.d("ChapterProvider::upStyle::contentPaint.letterSpacing=${contentPaint.letterSpacing}")
            contentPaint.typeface = textFont                                                    //设置正文字体
            contentPaint.textSize = (readerPreferences?.fontSize?.toFloat() ?: 1.0f) * BASE_FONT_SIZE                   //设置字体大小
            Logger.d("ChapterProvider::upStyle::contentPaint.textSize=${contentPaint.textSize}")
            contentPaint.isAntiAlias = true                                                     //设置抗锯齿

            //<a>标签的Paint
            aPaint = TextPaint()
            aPaint.color = Color.BLUE
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                aPaint.underlineColor = Color.BLUE
            }
            aPaint.isUnderlineText = true
            aPaint.letterSpacing = readerPreferences?.letterSpacing?.toFloat() ?: 0.0f               //设置正文文字间距
            Logger.d("ChapterProvider::upStyle::contentPaint.letterSpacing=${aPaint.letterSpacing}")
            aPaint.typeface = textFont                                                    //设置正文字体
            aPaint.textSize = (readerPreferences?.fontSize?.toFloat() ?: 1.0f) * BASE_FONT_SIZE                   //设置字体大小
            Logger.d("ChapterProvider::upStyle::contentPaint.textSize=${aPaint.textSize}")
            aPaint.isAntiAlias = true                                                     //设置抗锯齿


            //间距
            lineSpacingExtra = readerPreferences?.lineHeight?.toFloat() ?: 1.2f                //行间距系数，除上10 再和lineHeight相乘
            Logger.d("ChapterProvider::upStyle::lineSpacingExtra=${lineSpacingExtra}")
            paragraphSpacing = readerPreferences?.paragraphSpacing?.toInt() ?: 0                 //段落间距
            Logger.d("ChapterProvider::upStyle::paragraphSpacing=${paragraphSpacing}")
            titleTopSpacing = readerPreferences?.titleTopSpacing?.dp?.toInt() ?: 0               //标题顶部间距
            Logger.d("ChapterProvider::upStyle::titleTopSpacing=${titleTopSpacing}")
            titleBottomSpacing = readerPreferences?.titleBottomSpacing?.dp?.toInt() ?: 0                           //标题底部间距
            Logger.d("ChapterProvider::upStyle::titleBottomSpacing=${titleBottomSpacing}")

            //更新屏幕参数
            upVisibleSize(context)
            Logger.d("ChapterProvider::upStyle done")
        }
    }

    suspend fun getTextChapter(
        chapter: BookChapter,
        contents: List<ReaderText>,
        imageStyles: String = "",
        chapterSize: Int,
    ): TextChapter? {
        val textPages = arrayListOf<TextPage>()   //一个章节的内容，可以拆分成多少页进行显示
        val pageLines = arrayListOf<Int>()          //每一个页面上，显示的行数的集合
        val pageLengths = arrayListOf<Int>()        //每一个页面上，显示的字符数的集合
        val stringBuilder = StringBuilder()
        var offsetY = 0f    //每一行显示时，和顶部的偏移量

        val isOneElePage = (contents.size == 1) //只有一个元素的页面

        textPages.add(TextPage())   //增加一空白页，然后给这个页面增加显示内容
        contents.forEachIndexed { index, paragraph -> //遍历需要显示的内容的每一个自然段， 一个段落一个段落（图片）的遍历
            when (paragraph) {
                is ReaderText.Image -> {
                    val image = paragraph
                    offsetY = setTypeImage(
                        image.path,
                        image.width,
                        image.height,
                        offsetY,
                        textPages,
                        imageStyles
                    )
                }

                is ReaderText.Text -> {
                    val image = paragraph.tryParseToImage()
                    offsetY = if (image != null) {
                        setTypeImage(
                            image.path,
                            image.width,
                            image.height,
                            offsetY,
                            textPages,
                            imageStyles
                        )
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

                else -> {

                }
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

        var width = 0
        var height = 0
        var originWidth = imgWidth  //图片的实际宽高
        var originHeight = imgHeight
        if (imgWidth <= 0 || imgHeight <= 0) {
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = true; // 不加载图片像素，只获取宽高
            options.inSampleSize = 2
            BitmapFactory.decodeFile(imgSrc, options)
            originWidth = options.outWidth
            originHeight = options.outHeight
        }

        if (durY > visibleHeight) { // //当前可显示便宜位置超过了可视高度
            textPages.last().height = durY    //修改上一页的高度
            textPages.add(TextPage())                   //增加新一页
            durY = 0f                                  //修改当前页的距离顶部的偏移量
        }

        width = imgWidth
        height = imgHeight

        //页面宽高和图片宽高的适配
        when (imageStyles.uppercase()) {
            "FULL" -> {                                         //占满宽度
                width = visibleWidth
                height = originHeight * width / originWidth
            }

            else -> {                                           //适配
                if (originWidth > visibleWidth) {
                    height = originHeight * visibleWidth / originWidth
                    width = visibleWidth
                }

                if (height > visibleHeight) {
                    width = width * visibleHeight / height
                    height = visibleHeight
                }

                if (durY + height > visibleHeight) { //当前页显示不下了，则创建新页用于显示图片
                    textPages.last().height = durY
                    textPages.add(TextPage())
                    durY = 0f
                }
            }
        }

        //构建用于显示Image的TextLine
        val textLine = TextLine(isImage = true)
        textLine.lineTop = durY     //图片的顶部
        durY += height
        textLine.lineBottom = durY  //图片的底部
        //图片的左边和右边, 加上页面边距
        val (start, end) = if (visibleWidth > width) {      //图片显示宽度小于可视宽度
            val adjustWidth = (visibleWidth - width) / 2f   //坐偏移量
            Pair(
                paddingLeft.toFloat() + adjustWidth,
                paddingLeft.toFloat() + adjustWidth + width
            )
        } else {
            Pair(paddingLeft.toFloat(), (paddingLeft + width).toFloat())
        }

        textLine.textChars.add(
            TextChar(
                charData = imgSrc, //图片的本地完整路径
                start = start,      //图片的左位置
                end = end,          //图片的右位置
                isImage = true
            )
        )
        textPages.last().textLines.add(textLine)

        return durY + (paragraphSpacing / 10f)
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
            getPaintByTagName(checkedTag?.name)
        } else if (paragraph is ReaderText.Chapter) {
            titlePaint
        } else {
            contentPaint
        }
        textPaint.set(parentPaint)

        var marginLeft = 0f
        var marginRight = 0f
        var marginTop = 0f
        var marginBottom = 0f
        //对齐方式
        var textAlign: CssTextAlign =
            if (isTitle) {
                CssTextAlign.CssTextAlignCenter
            } else {
                CssTextAlign.CssTextAlignLeft
            }
        var lineHeightParam = 1f
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

            if (paragraph.textCssInfo.marginLeft.value > 0 ||
                paragraph.textCssInfo.marginRight.value > 0 ||
                paragraph.textCssInfo.marginTop.value > 0 ||
                paragraph.textCssInfo.marginBottom.value > 0) {

                val oneCh: String = (text.getOrNull(0)?.toString() ?: " ")
                val oneEmWidth =  StaticLayout.getDesiredWidth(oneCh, textPaint)

                //左边距
                marginLeft = (if (paragraph.textCssInfo.marginLeft.isEm()) {
                    oneEmWidth * paragraph.textCssInfo.marginLeft.value
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
                    val oneEmWidth =  StaticLayout.getDesiredWidth(oneCh, textPaint)
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
                    oneEmWidth * paragraph.textCssInfo.marginTop.value
                } else if (paragraph.textCssInfo.marginTop.isPx()) {
                    paragraph.textCssInfo.marginTop.value
                } else if (paragraph.textCssInfo.marginTop.isPercent()) {
                    visibleHeight * paragraph.textCssInfo.marginTop.value
                } else {
                    0f
                }).coerceIn(0f, visibleHeight / 4f)
                //下边距
                marginBottom = (if (paragraph.textCssInfo.marginBottom.isEm()) {
                    oneEmWidth * paragraph.textCssInfo.marginBottom.value
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

        if (marginTop > 0f) {
            durY += marginTop
        }

        val layout = StaticLayout(text, textPaint, visibleWidth - marginLeft.roundToInt() - marginRight.roundToInt(), Layout.Alignment.ALIGN_NORMAL, 0f, 0f, true)
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
                CssTextAlign.CssTextAlignLeft -> addCharsToLineLeft(textLine, words.toStringArray(), textPaint, marginLeft)
                CssTextAlign.CssTextAlignRight -> addCharsToLineRight(textLine, words.toStringArray(), textPaint, desiredWidth, marginRight)
                CssTextAlign.CssTextAlignCenter -> addCharsToLineCenter(textLine, words.toStringArray(), textPaint, desiredWidth)
                CssTextAlign.CssTextAlignJustify -> {
                    if (layout.lineCount == 1) {
                        addCharsToLineLeft(textLine, words.toStringArray(), textPaint, marginLeft)
                    } else {
                        if (isLastLine) {    //两端对齐，除了最后一行
                            addCharsToLineLeft(textLine, words.toStringArray(), textPaint, marginLeft)
                        } else {
                            addCharsToLineMiddle(textLine, words.toStringArray(), textPaint, desiredWidth, marginLeft)
                        }
                    }
                }
            }

            //新增加的行，超过了一页的显示高度, 则创建新页
            if (durY + textPaint.textHeight * lineSpacingExtra * lineHeightParam > visibleHeight) {
                val lastPage = textPages.last()
                lastPage.text = stringBuilder.toString()
                pageLines.add(lastPage.textLines.size)
                pageLengths.add(lastPage.text.length)
                lastPage.height = durY

                textPages.add(TextPage())
                stringBuilder.clear()
                durY = 0f
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

        //一个自然段落遍历完
        if (isTitle) {
            durY += titleBottomSpacing                          //是标题行，则加上标题的底部间距
        }
        if (marginBottom > 0f) {
            durY += marginBottom
        }
//        durY += textPaint.textHeight * paragraphSpacing   //是段落，则加上段落间距 //TODO
        return durY
    }

//    /****
//     * 段落的第一行，非标题，有缩进，处理两端对齐
//     */
//    private suspend fun addCharsToLineFirst(textLine: TextLine, words: Array<String>, textPaint: TextPaint, desiredWidth: Float) {
//        val tipPreference = readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()
//        val textFullJustify = tipPreference?.textFullJustify == true
//        val bodyIndent = paragraphIndent    //段落首行缩进的2个tab
//
//        var x = 0f
//        if (!textFullJustify) { //如果不需要两端对齐，则默认从左向右显示，计算每个字母的显示位置
//            addCharsToLineLeft(textLine, words, textPaint, x)
//            return
//        }
////        val icw = StaticLayout.getDesiredWidth(bodyIndent, textPaint) / bodyIndent.length   //单个Tab的宽度
////        bodyIndent.toStringArray().forEach { tabCh ->
////            val x1 = x + icw
////            textLine.addTextChar(charData = tabCh, start = paddingLeft + x, end = paddingLeft + x1) //将两个Tab加入到TextLine中
////            x = x1
////        }
//        //在disposeContent()方法中，每个自然段都会默认加上2个tab字符，这里将多余的2个tab字符裁剪掉
////        var tmpWords = words;
////        while(true) {
////            if (tmpWords.firstOrNull() == oneParagraphIndent) {
////                tmpWords = words.copyOfRange(1, tmpWords.size)
////            } else {
////                break
////            }
////        }
////        val words1 = words.copyOfRange(bodyIndent.length, words.size)
//        addCharsToLineMiddle(textLine, words, textPaint, desiredWidth, x)
//    }

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
            textLine.addTextChar(charData = char, start = paddingLeft + x, end = paddingLeft + x1)
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
            textLine.addTextChar(charData = char, start = paddingLeft + x, end = paddingLeft + x1)
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
    private fun addCharsToLineRight(textLine: TextLine, words: Array<String>, textPaint: TextPaint, desiredWidth: Float, marginRight:Float) {
        val x = visibleWidth - desiredWidth - marginRight  //标题栏居中显示，左偏移
        addCharsToLineLeft(textLine, words, textPaint, x)
    }


    /****
     * 显示的一行内容，计算的偏移位置检测是否超过了边界， 对偏移进行纠偏
     */
    private fun exceed(textLine: TextLine, words: Array<String>) {
        val endX = textLine.textChars.last().end    //一行的最后一个字符显示的左边位置
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

    fun init(context: Context) {
        upStyle(context)
    }
}