package com.wxn.bookread.provider

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.ext.imgRegex
import com.wxn.base.ext.isContentPath
import com.wxn.base.util.PathUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.ext.dp
import com.wxn.bookread.ext.sp
import com.wxn.base.util.Coroutines
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject

open class PreferencesUser {

    @Inject
    lateinit var readerPreferencesUtil: ReaderPreferencesUtil

    @Inject
    lateinit var readTipPreferencesUtil: ReadTipPreferencesUtil

}

object ChapterProvider : PreferencesUser() {

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
     * 可视宽度
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
    private var lineSpacingExtra = 0

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


    /**
     * 更新绘制尺寸
     */
    private suspend fun upVisibleSize() {
        val readerPreferences = readerPreferencesUtil.readerPreferencesFlow.firstOrNull()
        if (viewWidth > 0 && viewHeight > 0) {
            paddingLeft = readerPreferences?.pageHorizontalMargins?.dp?.toInt() ?: 0         //页面左边距
            paddingTop =
                readerPreferences?.pageTopMargins?.dp?.toInt() ?: 0                 //页面顶部间距
            visibleWidth =
                (viewWidth - paddingLeft * 2).toInt()                                //可视宽度
            visibleHeight = (viewHeight - paddingTop * 2).toInt()                            //可视高度
            visibleRight = paddingLeft + visibleWidth                                       //可视右边
            visibleBottom = paddingTop + visibleHeight                                      //可视底部
        }
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        Coroutines.mainScope().launch {
            val context = readerPreferencesUtil.context
            val readerPreferences = readerPreferencesUtil.readerPreferencesFlow.firstOrNull()

            //更新字体
            typeface = try {
                val fontPath = readerPreferences?.font.orEmpty()
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
                    readerPreferencesUtil.updatePreferences(it)
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

            titlePaint.color =
                readerPreferences?.textColor ?: Color.BLACK                       //设置标题文字颜色
            titlePaint.letterSpacing =
                readerPreferences?.letterSpacing?.toFloat() ?: 0f        //设置标题字母间距
            titlePaint.typeface =
                titleFont                                                     //设置标题字体
//        titlePaint.textSize = with(ReadBookConfig) { textSize + titleSize }.sp.toFloat()    //设置标题字体大小
            titlePaint.textSize = readerPreferences?.titleSize?.sp?.toFloat() ?: 0.0f
            titlePaint.isAntiAlias =
                true                                                       //设置抗锯齿
            //正文的Paint
            contentPaint = TextPaint()
            contentPaint.color =
                readerPreferences?.textColor ?: Color.BLACK                    //设置正文文字颜色
            contentPaint.letterSpacing =
                readerPreferences?.letterSpacing?.toFloat() ?: 0.0f               //设置正文文字间距
            contentPaint.typeface =
                textFont                                                    //设置正文字体
            contentPaint.textSize =
                readerPreferences?.fontSize?.sp?.toFloat() ?: 0.0f                     //设置字体大小
            contentPaint.isAntiAlias =
                true                                                     //设置抗锯齿
            //间距
            lineSpacingExtra =
                readerPreferences?.lineHeight?.toInt() ?: 0  //TODO              //行间距
            paragraphSpacing =
                readerPreferences?.paragraphSpacing?.toInt() ?: 0                 //段落缩进
            titleTopSpacing =
                readerPreferences?.titleTopSpacing?.dp?.toInt() ?: 0               //标题顶部间距
            titleBottomSpacing = readerPreferences?.titleBottomSpacing?.dp?.toInt()
                ?: 0                           //标题底部间距
            //更新屏幕参数
            upVisibleSize()

        }
    }

    fun getTextChapter(
        context: Context,
        book: Book,
        chapter: BookChapter,
        contents: List<String>,
        imageStyles: String = ""
    ): TextChapter? {
        val textPages = arrayListOf<TextPage>()   //一个章节的内容，可以拆分成多少页进行显示
        val pageLines = arrayListOf<Int>()          //每一个页面上，显示的行数的集合
        val pageLengths = arrayListOf<Int>()        //每一个页面上，显示的字符数的集合
        var offsetY = 0    //每一行显示时，和顶部的偏移量

        textPages.add(TextPage())   //增加一空白页，然后给这个页面增加显示内容
        contents.forEachIndexed { index, paragraph -> //遍历需要显示的内容的每一个自然段， 一个段落一个段落（图片）的遍历
            val matcher = imgPattern.matcher(paragraph)
            if (matcher.find()) {
                val imgSrc = matcher.group(1) ?: ""
                val imgWidth = matcher.group(2)?.toIntOrNull() ?: 0
                val imgHeight = matcher.group(3)?.toIntOrNull() ?: 0
                println("src: $imgSrc, width: $imgWidth, height: $imgHeight")

                if (imgSrc.isNotEmpty()) {
                    setTypeImage(
                        context,
                        book,
                        chapter,
                        imgSrc,
                        imgWidth,
                        imgHeight,
                        offsetY,
                        textPages,
                        imageStyles
                    )
                }
            }

        }
        return null
    }

    /***
     * 根据图片设置TextLine/TextChar属性，将结果保存到textPages中，并返回offsetY，用于计算下一行内容
     */
    private fun setTypeImage(
        context: Context,
        book: Book,
        chapter: BookChapter,
        imgSrc: String,
        imgWidth: Int,
        imgHeight: Int,
        offsetY: Int,
        textPages: ArrayList<TextPage>,
        imageStyles: String
    ): Int {
        var durY = offsetY

        var width: Int = 0
        var height: Int = 0
        var originWidth = imgWidth  //图片的实际宽高
        var originHeight = imgHeight
        if (imgWidth <= 0 || imgHeight <= 0) {
            val imgPath = getChapterResourcePath(context, book.id, imgSrc).absolutePath
            if (imgPath.isNullOrEmpty()) {
                return 0
            }
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = true; // 不加载图片像素，只获取宽高
            options.inSampleSize = 2
            BitmapFactory.decodeFile(imgPath, options)
            originWidth = options.outWidth
            originHeight = options.outHeight
        }

        if (durY > visibleHeight) { // //当前可显示便宜位置超过了可视高度
            textPages.last().height = durY.toFloat()    //修改上一页的高度
            textPages.add(TextPage())                   //增加新一页
            durY = 0                                    //修改当前页的距离顶部的偏移量
        }

        width = imgWidth
        height = imgHeight

        when(imageStyles.uppercase()) {
            "FULL" -> {                                         //占满宽度
                width = visibleWidth
                height = originHeight * width / originWidth
            }
            else -> {                                           //适配
                //TODO
            }
        }


        return 0
    }

    /***
     * 设置View尺寸
     */
    fun setViewSize(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            viewWidth = width
            viewHeight = height
            Coroutines.mainScope().launch {
                upVisibleSize()
            }
        }
    }

    init {
        upStyle()
    }


    var paragraphIndent: String = "　　" //段落缩进

    /***
     * 获取章节对应的缓存文件
     */
    fun getChapterFile(context: Context, bookId: Long, chapterPathName: String): File {
        val path =
            context.filesDir.absolutePath + File.separator + "chapters" + File.separator + bookId.toString() + File.separator + chapterPathName
        val destFile = File(path)
        destFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
        return destFile
    }

    /**
     * 获取章节中对应的缓存资源文件
     */
    fun getChapterResourcePath(context: Context, bookId: Long, resourceName: String): File {
        val path =
            context.filesDir.absolutePath + File.separator + "chapters" + File.separator + bookId.toString() + File.separator + "src" + File.separator + resourceName
        val destFile = File(path)
        destFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
        return destFile
    }
}
