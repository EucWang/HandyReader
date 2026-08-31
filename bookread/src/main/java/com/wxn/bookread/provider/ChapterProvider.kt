package com.wxn.bookread.provider

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssFontStyle
import com.wxn.base.bean.CssFontWeight
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.InlineStyle
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.SegmentResult
import com.wxn.base.bean.TextDirection
import com.wxn.base.bean.TextTag
import com.wxn.base.ext.isContentPath
import com.wxn.base.ext.statusBarHeight
import com.wxn.base.ext.toStringArray
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.base.util.launchIO
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.preference.BASE_FONT_SIZE
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.ext.dp
import com.wxn.bookread.provider.ChapterProvider.dualColumnEnabled
import com.wxn.bookread.provider.ChapterProvider.getTextChapter
import com.wxn.bookread.provider.ChapterProvider.paddingHorizontal
import com.wxn.bookread.provider.ChapterProvider.paddingVertical
import com.wxn.bookread.provider.ChapterProvider.recomputeDerivedSizes
import com.wxn.bookread.provider.ChapterProvider.setViewSize
import com.wxn.bookread.provider.ChapterProvider.upStyle
import com.wxn.bookread.provider.ChapterProvider.upVisibleSize
import com.wxn.bookread.provider.ChapterProvider.viewHeight
import com.wxn.bookread.provider.ChapterProvider.viewWidth
import com.wxn.bookread.textHeight
import com.wxn.bookread.ui.ListDotRenderer
import com.wxn.bookread.ui.RenderResources
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.regex.Pattern
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
     *
     * v5 S3：加 @Volatile 保证跨线程可见性。[setViewSize]（主线程）写入，
     * [upVisibleSize]/[getTextChapter]（IO 单线程）读取——主线程的写入对 IO 线程立即可见，
     * 避免 getTextChapter 用到过期尺寸。函数体（setViewSize/upStyle）不改。
     */
    @Volatile
    var viewWidth = 0

    /***
     * 页面显示高度, 整个控件的可显示高度, 是屏幕高度 - 系统状态栏的高度
     *
     * v5 S3：加 @Volatile 保证跨线程可见性（同 [viewWidth]）。
     */
    @Volatile
    var viewHeight = 0

    /***
     * 左边距/右边距
     */
    @Volatile
    var paddingHorizontal = 0

    /***
     * 上边距/下边距
     */
    @Volatile
    var paddingVertical = 0

    /**
     * 可视宽度,
     * 这里是排除掉了水平方向上的边距之后的页面可显示元素的宽度
     */
    @Volatile
    var visibleWidth = 0

    /***
     * 可视高度
     * 这里是已经计算了垂直方向的边距之后的页面可显示元素的高度
     */
    @Volatile
    var visibleHeight = 0

    /***
     * 可视的右边的偏移
     */
    @Volatile
    var visibleRight = 0

    /***
     * 可视底部的偏移位置
     */
    @Volatile
    var visibleBottom = 0

    /***
     * 双列间隔实际像素（= visibleWidth * DUAL_COLUMN_GAP_RATIO）。单列下 = 0。
     */
    @Volatile
    var columnGapActual = 0

    //    占一屏的图片的最小高度, 450 只是一个默认值
    @Volatile
    private var fullImageMinHeight = 450 //

    /***
     * 行间距 系数
     */
    var lineSpacingExtra = 0f

    /***
     * 段落间距
     */
    private var paragraphSpacing = 0

    /***
     * 标题顶部间距
     */
    internal var titleTopSpacing = 0

    /***
     * 标题底部间距
     */
    internal var titleBottomSpacing = 0

    @Volatile
    private var isVScrollMode = false

    //region v5 双列显示（dual-column）
    /**
     * 双列开关（由 [upStyle] 从 prefs 读入，整章排版期间只读）。
     * 与 scroll/leftHandedMode 同组（全局阅读设置，不进 per-book override，S2 决策）。
     *
     * internal 供单测（DualColumnLayoutTest）设置后验证 LayoutBounds 列几何。
     */
    @Volatile
    internal var dualColumnEnabled = false

    /** 当前列宽（双列下 = visibleWidth 的一半再减去列间隔；单列下 = visibleWidth）。由 [upVisibleSize] 计算。 */
    @Volatile
    internal var columnWidth = 0

    /**
     * v5 S10：列间隔比例写死常量，不进 DataStore、不提供 UI。
     * ≈ CSS column-gap: normal (1em)，7 寸手机 ≈ 21.6dp，12 寸平板 ≈ 48dp。
     * v2 可根据用户反馈再加可调节 Slider。
     */
    private const val DUAL_COLUMN_GAP_RATIO = 0.06

    //endregion

    /***
     * 字体
     */
    var typeface: Typeface = Typeface.SANS_SERIF

    /***
     * 回退字体: 当主字体缺少字符字形时使用系统字体作为回退
     */
    var fallbackTypeface: Typeface = Typeface.SANS_SERIF

    // Q-08 Typeface 缓存：字体路径未变时复用已解码的 Typeface，避免主线程重复解码大字体文件。
    // 预设主题大多使用系统字体(serif/sans_serif/monospace, 常量级开销)，但用户自定义字体/切换主题时
    // 若不缓存，每次 upStyle 都会重新解码（数 MB 字体文件 → 主线程数十~数百 ms 卡顿）。
    @Volatile
    private var cachedFontPath: String? = null
    @Volatile
    private var cachedTypeface: Typeface? = null
    @Volatile
    private var cachedTitleTypeface: Typeface? = null
    @Volatile
    private var cachedTitleFontPath: String? = null

    /***
     * 标题的TextPaint
     */
    val titlePaint: TextPaint = TextPaint()

    /***
     * 文本内容的TextPaint
     */
    val contentPaint: TextPaint = TextPaint()

    val h1Paint: TextPaint = TextPaint()
    val h2Paint: TextPaint = TextPaint()
    val h3Paint: TextPaint = TextPaint()
    val h4Paint: TextPaint = TextPaint()
    val aPaint: TextPaint = TextPaint()

    /**
     * Incremented every time applyStyleInternal takes effect. Stamped onto each TextChapter
     * at pagination time so pre-paginated caches (nextTextChapter/prevTextChapter, etc.) can
     * be checked for staleness before being reused (e.g. font size changed after they were
     * paginated but before they were displayed).
     */
    val styleVersion = java.util.concurrent.atomic.AtomicInteger(0)
    
    /*
     * F4 新增:几何轨工作 paint,用于 addCharsToLineXxx 按字符 scale 算宽度时临时切 textSize。
     * 与现有 contentPaint/titlePaint 等共享相同线程模型(getTextChapter 在 launchIO 单协程内串行调用,
     * PageViewController.loadChapterDispatcher = limitedParallelism(1)),无新增线程安全风险。
     */
    private val workPaint = TextPaint()

    private val typerfaceMap = mutableMapOf<String, Typeface>()

    fun getTypeface(fontWeight: CssFontWeight, cssFontStyle: CssFontStyle) =
        when (fontWeight) {
            CssFontWeight.FontWeightNormal -> {
                if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                    if (typerfaceMap["weight_normal_italic"] == null) {
                        typerfaceMap["weight_normal_italic"] =
                            Typeface.create(typeface, Typeface.ITALIC)
                    }
                    typerfaceMap["weight_normal_italic"]
                } else {
                    if (typerfaceMap["weight_normal_normal"] == null) {
                        typerfaceMap["weight_normal_normal"] =
                            Typeface.create(typeface, Typeface.NORMAL)
                    }
                    typerfaceMap["weight_normal_normal"]
                }
            }

            CssFontWeight.FontWeightBold -> {
                if (cssFontStyle == CssFontStyle.CssFontStyleItalic) {
                    if (typerfaceMap["weight_bold_italic"] == null) {
                        typerfaceMap["weight_bold_italic"] =
                            Typeface.create(typeface, Typeface.BOLD_ITALIC)
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
                        typerfaceMap["weight_bolder_italic"] =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                Typeface.create(typeface, 900, true)
                            } else {
                                Typeface.create(typeface, Typeface.BOLD_ITALIC)
                            }
                    }
                    typerfaceMap["weight_bolder_italic"]
                } else {
                    if (typerfaceMap["weight_bolder"] == null) {
                        typerfaceMap["weight_bolder"] =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
                        typerfaceMap["weight_lighter_italic"] =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                Typeface.create(typeface, 300, true)
                            } else {
                                Typeface.create(typeface, Typeface.ITALIC)
                            }
                    }
                    typerfaceMap["weight_lighter_italic"]
                } else {
                    if (typerfaceMap["weight_lighter"] == null) {
                        typerfaceMap["weight_lighter"] =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
     * @param readerPreferences 调用方已读取的偏好（Q-08 冗余读消除：避免重复 firstOrNull() 读 DataStore）
     */
    private fun upVisibleSize(
        context: Context,
        readerPreferences: ReaderPreferences? = null
    ) {
//        Logger.i("ChapterProvider:upVisibleSize，paddingHorizontal=$paddingHorizontal")

        if (viewWidth == 0 || viewHeight == 0) {
            val metrics = context.resources.displayMetrics
            viewWidth = metrics.widthPixels
            viewHeight = metrics.heightPixels - context.statusBarHeight
            Logger.d("ChapterProvider::set screen size to view::viewWidth=$viewWidth,viewHeight=$viewHeight")
        }

        // Q-08 冗余读消除：避免在主线程调用 runBlocking 读 DataStore。
        // 如果 readerPreferences 已提供，则直接使用；否则仅在非主线程尝试同步读取，
        // 或者回退到默认值。注意：此函数应尽量由 upStyle (已在协程中) 或 setViewSize 调用。
        val prefs = readerPreferences ?: if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            null // 主线程不执行 runBlocking
        } else {
            runBlocking { readerPreferencesUtil?.readerPrefsFlow?.firstOrNull() }
        }

        if (viewWidth > 0 && viewHeight > 0) {
            if (prefs != null) {
                paddingHorizontal = ((prefs.pageHorizontalMargins * 0.1 * viewWidth.toDouble()).toInt()) / 2         //页面左边距
                paddingVertical = if (!isVScrollMode) { //非连续垂直滚动阅读模式，才会设置这个值
                    (prefs.pageVerticalMargins * 0.1 * viewHeight.toDouble()).toInt() / 2                 //页面顶部间距
                } else {
                    0
                }
            }
            recomputeDerivedSizes()
        }
//        Logger.d("ChapterProvider::upVisibleSize::viewWidth=$viewWidth, viewHeight=$viewHeight, " +
//                "visibleWidth=$visibleWidth,visibleHeight=$visibleHeight," +
//                "visibleRight=$visibleRight,visibleBottom=$visibleBottom," +
//                "paddingHorizontal=$paddingHorizontal,paddingVertical=$paddingVertical," +
//                "prefs is null? ${if (prefs != null) "no" else "yes" }")
    }

    //region v5 同步布局尺寸计算（消除旋转时 loadContent 与 async upVisibleSize 的竞争条件）

    /**
     * 同步重算所有布局派生尺寸。
     * 依赖 [viewWidth]、[viewHeight]、[paddingHorizontal]、[paddingVertical]、[dualColumnEnabled]。
     * 不依赖 DataStore（所有输入已在调用前就绪），可在主线程同步执行。
     */
    private fun recomputeDerivedSizes() {
        visibleWidth = (viewWidth - paddingHorizontal * 2).coerceAtLeast(0)
        visibleHeight = (viewHeight - paddingVertical * 2).coerceAtLeast(0)
        visibleRight = paddingHorizontal + visibleWidth
        visibleBottom = paddingVertical + visibleHeight
        if (dualColumnEnabled) {
            columnGapActual = (visibleWidth * DUAL_COLUMN_GAP_RATIO).toInt()
            columnWidth = (visibleWidth - columnGapActual) / 2
        } else {
            columnGapActual = 0
            columnWidth = 0
        }
        fullImageMinHeight = max(
            max((visibleHeight * .075).toInt(), (viewHeight * 0.6f).toInt()),
            450
        )
    }

    /**
     * 同步更新排版尺寸，在 [loadContent] 前调用。
     *
     * 边距与 viewWidth/viewHeight 成正比（paddingHorizontal ∝ viewWidth），
     * 利用 [PageView.onSizeChanged] 传来的 oldw/oldh 等比缩放。
     * oldw<=0 时（首次布局）不缩放，沿用 [upVisibleSize] 已计算的值。
     *
     * 线程安全：[PageView.onSizeChanged]（主线程）写入 → IO 协程的 `launchIO` 调度
     * 隐式建立 happens-before，IO 线程一定读到最新值。
     */
    fun synchronouslyUpdateLayout(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        if (w == viewWidth && h == viewHeight) return
        Logger.d("ChapterProvider::synchronouslyUpdateLayout:w=$w,h=$h,oldw=$oldw,oldh=$oldh")
        viewWidth = w
        viewHeight = h
        if (oldw > 0 && oldh > 0) {
            paddingHorizontal = (paddingHorizontal.toFloat() * w / oldw).toInt()
            paddingVertical = if (!isVScrollMode) {
                (paddingVertical.toFloat() * h / oldh).toInt()
            } else {
                0
            }
        }
        recomputeDerivedSizes()
        Logger.d("ChapterProvider::synchronouslyUpdateLayout::viewWidth=$viewWidth, viewHeight=$viewHeight, visibleWidth=$visibleWidth, visibleHeight=$visibleHeight")
    }
    //endregion


    /**
     * 更新样式
     * @param readerPreferences 可选的偏好值。如果提供，则直接使用；否则从 DataStore 读取。
     *                          (Q-08 冗余读消除：在高频更新如滑动滑块时，由 ViewModel 直接传入新值)
     *
     * suspend: the caller (PageViewController.updatePageViews) needs this style change to be
     * fully applied before it triggers a redraw — otherwise the redraw would pair the new
     * paint size with the old pagination, causing a visible mis-sized flash (most noticeable
     * while dragging the font-size slider on a device slower than an emulator).
     */
    suspend fun upStyle(context: Context, readerPreferences: ReaderPreferences? = null) {
        ImageLayoutProvider.imgScale = context.resources.displayMetrics.density
        Logger.i("ChapterProvider::upStyle")

        val prefs = readerPreferences ?: readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()
        if (prefs != null) {
            applyStyleInternal(context, prefs)
        }
        Logger.d("ChapterProvider::upStyle done")
    }

    /**
     * 内部应用样式到画笔和全局几何变量。
     * 必须在主线程调用（更新 lateinit var paints 且 onDraw 在主线程读取）。
     */
    private fun applyStyleInternal(context: Context, prefs: ReaderPreferences) {
        //更新字体
        val fontPath = prefs.font.orEmpty()
        val fontVariant = prefs.fontVariant ?: "regular"
        isVScrollMode = (prefs.scroll == 6) //是否是连续垂直滚动阅读模式
        //region v5 双列：读取开关（S2：放 ReaderPreferences）+ 互斥防御（P8）
        dualColumnEnabled = (prefs.columns == 2)
        if (dualColumnEnabled && isVScrollMode) {
            Logger.d("ChapterProvider::upStyle::dualColumn conflicts with scroll=6, fallback to single column")
            dualColumnEnabled = false
        }
        //endregion

        typeface = try {
            Logger.d("ChapterProvider::upStyle::fontPath=$fontPath, fontVariant=$fontVariant")
            // Q-08 Typeface 缓存
            val fontCacheKey = "$fontPath|$fontVariant"
            if (fontCacheKey == cachedFontPath && cachedTypeface != null) {
                cachedTypeface!!
            } else {
                val isSystemFont = fontPath in listOf("serif", "sans_serif", "monospace")
                val resolved = when {
                    fontPath == "serif" -> Typeface.SERIF
                    fontPath == "sans_serif" -> Typeface.SANS_SERIF
                    fontPath == "monospace" -> Typeface.MONOSPACE
                    fontPath.isContentPath() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        context.contentResolver
                            .openFileDescriptor(Uri.parse(fontPath), "r")!!.use { pfd ->
                                Typeface.Builder(pfd.fileDescriptor).build()
                            }
                    }
                    fontPath.isContentPath() -> {
                        Typeface.createFromFile(PathUtil.getPath(context, Uri.parse(fontPath)))
                    }
                    fontPath.isNotEmpty() && !isSystemFont -> {
                        val fontDir = File(fontPath)
                        val variantFile = fontDir.listFiles()?.firstOrNull {
                            it.nameWithoutExtension.equals(fontVariant, ignoreCase = true)
                                    && (it.extension.equals("ttf", ignoreCase = true)
                                    || it.extension.equals("otf", ignoreCase = true))
                        }
                        if (variantFile != null && variantFile.exists()) {
                            Typeface.createFromFile(variantFile)
                        } else {
                            val anyFont = fontDir.listFiles()?.firstOrNull {
                                it.extension.equals("ttf", ignoreCase = true)
                                        || it.extension.equals("otf", ignoreCase = true)
                            }
                            if (anyFont != null) Typeface.createFromFile(anyFont)
                            else Typeface.SANS_SERIF
                        }
                    }
                    fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                    else -> Typeface.SANS_SERIF
                }
                cachedFontPath = fontCacheKey
                cachedTypeface = resolved
                resolved
            }
        } catch (e: Exception) {
            Coroutines.scope().launch {
                readerPreferencesUtil?.updateFontPrefs("", "")
            }
            Typeface.SANS_SERIF
        }
        typerfaceMap.clear()

        //回退字体
        fallbackTypeface = Typeface.SANS_SERIF

        //标题字体
        val titleTypeface = try {
            val titleFontPath = prefs.font.orEmpty()
            if (titleFontPath == cachedTitleFontPath && cachedTitleTypeface != null) {
                cachedTitleTypeface!!
            } else {
                val isSystemFont =
                    titleFontPath in listOf("serif", "sans_serif", "monospace", "")
                val resolved = if (!isSystemFont && titleFontPath.isNotEmpty()) {
                    val fontDir = File(titleFontPath)
                    val boldFile = fontDir.listFiles()?.firstOrNull {
                        it.nameWithoutExtension.equals("bold", ignoreCase = true)
                                && (it.extension.equals("ttf", ignoreCase = true)
                                || it.extension.equals("otf", ignoreCase = true))
                    }
                    if (boldFile != null && boldFile.exists()) {
                        Typeface.createFromFile(boldFile)
                    } else {
                        Typeface.create(typeface, Typeface.BOLD)
                    }
                } else {
                    Typeface.create(typeface, Typeface.BOLD)
                }
                cachedTitleFontPath = titleFontPath
                cachedTitleTypeface = resolved
                resolved
            }
        } catch (e: Exception) {
            Typeface.create(typeface, Typeface.BOLD)
        }

        val titleFont = titleTypeface
        val textFont = typeface

        val baseTextSize = (prefs.fontSize.toFloat() ?: 1.0f) * BASE_FONT_SIZE

        //标题的Paint
        titlePaint.color = prefs.textColor ?: Color.BLACK
        titlePaint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0f
        titlePaint.typeface = titleFont
        titlePaint.textSize = baseTextSize * 1.8f
        titlePaint.isAntiAlias = true

        //h1的Paint
        h1Paint.color = prefs.textColor ?: Color.BLACK
        h1Paint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0f
        h1Paint.typeface = titleFont
        h1Paint.textSize = baseTextSize * 1.5f
        h1Paint.isAntiAlias = true

        //h2的Paint
        h2Paint.color = prefs.textColor ?: Color.BLACK
        h2Paint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0f
        h2Paint.typeface = titleFont
        h2Paint.textSize = baseTextSize * 1.35f
        h2Paint.isAntiAlias = true

        //h3的Paint
        h3Paint.color = prefs.textColor ?: Color.BLACK
        h3Paint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0f
        h3Paint.typeface = titleFont
        h3Paint.textSize = baseTextSize * 1.2f
        h3Paint.isAntiAlias = true

        //h4的Paint
        h4Paint.color = prefs.textColor ?: Color.BLACK
        h4Paint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0f
        h4Paint.typeface = titleFont
        h4Paint.textSize = baseTextSize * 1.05f
        h4Paint.isAntiAlias = true

        //正文的Paint
        contentPaint.color = prefs.textColor ?: Color.BLACK
        contentPaint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0.0f
        contentPaint.typeface = textFont
        contentPaint.textSize = (prefs.fontSize.toFloat() ?: 1.0f) * BASE_FONT_SIZE
        contentPaint.isAntiAlias = true

        //<a>标签的Paint
        aPaint.color = Color.BLUE
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            aPaint.underlineColor = Color.BLUE
        }
        aPaint.isUnderlineText = true
        aPaint.letterSpacing = prefs.letterSpacing.toFloat() ?: 0.0f
        aPaint.typeface = textFont
        aPaint.textSize = (prefs.fontSize.toFloat() ?: 1.0f) * BASE_FONT_SIZE
        aPaint.isAntiAlias = true

        //间距
        lineSpacingExtra = prefs.lineHeight.toFloat() ?: 1.2f
        paragraphSpacing = prefs.paragraphSpacing.toInt() ?: 0
        titleTopSpacing = prefs.titleTopSpacing.dp.toInt() ?: 0
        titleBottomSpacing = prefs.titleBottomSpacing.dp.toInt() ?: 0

        RenderResources.listDotPaint.color = prefs.textColor ?: Color.BLACK
        RenderResources.listDotStrokePaint.color = prefs.textColor ?: Color.BLACK

        RenderResources.listMarkerPaint.color = prefs.textColor ?: Color.BLACK
        RenderResources.listMarkerPaint.textSize = contentPaint.textSize   // D-2 决策：1.0×（::marker 继承正文字号）
        RenderResources.listMarkerPaint.typeface = contentPaint.typeface

        //更新屏幕参数
        upVisibleSize(context, prefs)

        styleVersion.incrementAndGet()
    }


    suspend fun getTextChapter(
        chapter: BookChapter,
        contents: List<ReaderText>,
        imageStyles: String = "",
        chapterSize: Int,
    ): TextChapter? {

        ListOrderCalculator.clear()
        ListOrderCalculator.prescan(contents)

//        Logger.d("ChapterProvider::getTextChapter::chapterIndex=[${chapter.chapterIndex}]," +
//                "paddingHorizontal=$paddingHorizontal,paddingVertical=$paddingVertical," +
//                "visibleWidth=$visibleWidth,visibleHeight=$visibleHeight," +
//                "viewWidth=$viewWidth,viewHeight=$viewHeight")
        val textPages = arrayListOf<TextPage>()   //一个章节的内容，可以拆分成多少页进行显示
        val pageLines = arrayListOf<Int>()          //每一个页面上，显示的行数的集合
        val pageLengths = arrayListOf<Int>()        //每一个页面上，显示的字符数的集合
        val stringBuilder = StringBuilder()

        //每一行显示时，和顶部的偏移量
        //滚动模式下，如果连续多个单页章节内容都很少，导致高度不够，滚动距离不够，会导致出现bug
        var offsetY = if (isVScrollMode) {   // 连续垂直滚动翻页模式
            if (chapter.chapterIndex == 0) { // 第一章
                96.dp.toFloat()
            } else if (contents.size == 1) { // 滚动模式下，一个章节只有一个ReaderText
                val content = contents[0]
                if (content is ReaderText.Chapter || (content is ReaderText.Text && null == content.tryParseToImage())) {
                    (viewHeight / 2f) - (contentPaint.textHeight * 2f) // 给予更多的高度上的偏移
                } else { //单章节-单页面： 章节内容为图片 下面已经有处理了，这里不用再处理了
                    0f
                }
            } else {
                0f
            }
        } else {
            0f
        }

        val isOneElePage = (contents.size == 1) //只有一个元素的页面
        var theImageStyle = imageStyles.uppercase()
        if (contents.size > 1) {
            var imgCount = 0
            for (item in contents) {
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

        // ★ 章节方向 = 段落 segDirect 聚合（RTL 段占比 > 0.33 即判 RTL）
        //   - 数据源：disposeContent(:957) 已为每个 ReaderText.Text/.Chapter 写入 segDirect
        //   - 时序：disposeContent → getTextChapter 同协程顺序调用，同对象实例，segDirect 必已就绪
        //   - 阈值 0.33：对 RTL 敏感（1/3 阿语段即判 RTL 章节），因章节方向仅影响双列起始列，判错无排版后果
        //   - 纯图片章/无 segDirect 段的章节 → 默认 LTR（图片无方向，可接受）
        val chapterIsRtl: Boolean = run {
            val segParagraphs = contents.filterIsInstance<ReaderText>()
                .mapNotNull { it.segDirect }
            if (segParagraphs.isEmpty()) false
            else segParagraphs.count { it.baseRtl }.toFloat() / segParagraphs.size > 0.33f
        }

        textPages.add(TextPage())   //增加一空白页，然后给这个页面增加显示内容
        offsetY += paddingVertical
        // v4：用 LayoutCursor 游标对象在段落间传递「当前 Y 偏移 + 当前列几何」。
        // 主循环极度简化——顺序遍历段落，传递游标，无 retry/while/零进展熔断/列状态变量。
        var cursor = LayoutCursor(
            offsetY = offsetY,
            bounds = when {
                dualColumnEnabled && chapterIsRtl -> layoutBoundsRightColumn()
                dualColumnEnabled -> layoutBoundsLeftColumn()
                else -> layoutBoundsPage()
            }
        )
        contents.forEachIndexed { index, paragraph -> //遍历需要显示的内容的每一个自然段， 一个段落一个段落（图片）的遍历
            when (paragraph) {
                is ReaderText.Image -> {
                    cursor = setTypeImageWithStyle(
                        isOneElePage,
                        paragraph,
                        theImageStyle,
                        cursor.offsetY,
                        textPages,
                        stringBuilder,
                        pageLines,
                        pageLengths,
                        cursor.bounds   // v4：透传当前列
                    )
                }

                is ReaderText.Text -> {
                    val image = paragraph.tryParseToImage()
                    cursor = if (image != null) {
                        setTypeImageWithStyle(
                            isOneElePage,
                            image,
                            theImageStyle,
                            cursor.offsetY,
                            textPages,
                            stringBuilder,
                            pageLines,
                            pageLengths,
                            cursor.bounds
                        )
                    } else {
                        val title = paragraph.tryParseToChapter(chapter.chapterIndex)
                        if (title != null) {
                            setTypeText(
                                title,
                                index,
                                cursor,
                                textPages,
                                pageLines,
                                pageLengths,
                                stringBuilder,
                                true,
                                chapterIsRtl
                            )
                        } else {
                            setTypeText(
                                paragraph,
                                index,
                                cursor,
                                textPages,
                                pageLines,
                                pageLengths,
                                stringBuilder,
                                false,
                                chapterIsRtl
                            )
                        }
                    }
                }

                is ReaderText.Chapter -> {
                    cursor = setTypeText(
                        paragraph,
                        index,
                        cursor,
                        textPages,
                        pageLines,
                        pageLengths,
                        stringBuilder,
                        true,
                        chapterIsRtl
                    )
                }

                else -> {}
            }
        }
        //一个章节的全部自然段落/图片/标题都遍历完，
        val lastPage = textPages.last()
        lastPage.height = cursor.offsetY + 20.dp   //一个章节最后一页，高度加上20dp（v4：cursor.offsetY）
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
            styleVersion = styleVersion.get(),
        )
    }

    private fun setTypeImageWithStyle(
        isOneElePage: Boolean,
        image: ReaderText.Image,
        imageStyles: String,
        offsetY: Float,
        textPages: ArrayList<TextPage>,
        stringBuilder: StringBuilder,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        bounds: LayoutBounds = layoutBoundsPage()   // v4 新增：列边界（透传给 setTypeImage）
    ): LayoutCursor {
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
            imgStyle,
            stringBuilder,
            pageLines,
            pageLengths,
            bounds   // v4：透传
        )
    }

    /***
     * 根据图片设置TextLine/TextChar属性，将结果保存到textPages中，并返回 [LayoutCursor]。
     *
     * v4（方案 B）：返回类型从 Float 改为 [LayoutCursor]，新增 [bounds] 参数。
     * 图片作为原子单元：装不下当前列 → 若左列则切右列（同页），右列/单列则建新页回左列。
     * FULL 类型例外：强制独占整页（不切列，用全页几何）。
     * 图片尺寸约束用 [bounds].width 替代单例 visibleWidth；X 坐标按 [bounds].startX 居中。
     */
    private fun setTypeImage(
        imgSrc: String, //这里就是绝对路径
        imgWidth: Int,
        imgHeight: Int,
        offsetY: Float,
        textPages: ArrayList<TextPage>,
        imageStyles: String,
        stringBuilder: StringBuilder,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        bounds: LayoutBounds = layoutBoundsPage()   // v4 新增
    ): LayoutCursor {
        var durY: Float = offsetY
        var currentBounds = bounds   // v4：局部变量，随列切换更新
        Logger.d("ChapterProvider::setTypeImage::imgSrc=${imgSrc}, offsetY=$offsetY,imgWidth=$imgWidth, imgHeight=$imgHeight")
        if (imgSrc.isEmpty()) {
            Logger.d("ChapterProvider::setTypeImage::imgSrc=${imgSrc}, did not find the imgSrc, pass")
            return LayoutCursor(offsetY, currentBounds)
        }
        val imgVerticalMargin = (contentPaint.textHeight * 1.1f).toInt() //垂直方向上,图片上下两边增加的间隔
        //图片显示可用高度, 页面高度 - 已经显示了的内容占据的偏移 - 底部的边距(paddingTop),
        //当图片占一屏显示时, 这个高度是完全展示的图片最大高度,
        //当图片非独占一屏时, 这个高度还需要 - 图片上下的一个间隔
        var usableHeight = (viewHeight - durY - paddingVertical).toInt()

        //图片约束之后的宽高（v4：currentBounds.width 替代 visibleWidth）
        val (originWidth, originHeight) = ImageLayoutProvider.constraintImageSize(
            imgWidth,
            imgHeight,
            imgSrc,
            currentBounds.width,
            visibleHeight
        )
        if (originWidth <= 0 || originHeight <= 0) {
            return LayoutCursor(durY, currentBounds)
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
                imageStyles == "FULL"
            ) {  //当前可显示位置超过了可视高度
                // v4 方案 B：图片原子切列（FULL 类型例外，强制独占整页）
                if (dualColumnEnabled && currentBounds.isLeftColumn && imageStyles != "FULL") {
                    // 左列放不下 → 切右列（同页），不建新页
                    currentBounds = layoutBoundsRightColumn()
                    durY = paddingVertical.toFloat()
                    usableHeight = (viewHeight - durY - paddingVertical).toInt()
                    false
                } else {
                    // 右列也满 / 单列装不下 / FULL 类型 → 建新页，回左列（或单列 page）
                    val lastPage = textPages.last()
                    lastPage.text = stringBuilder.toString()
                    pageLines.add(lastPage.textLines.size)
                    pageLengths.add(lastPage.text.length)
                    lastPage.height = durY
                    textPages.add(TextPage())           //增加新一页
                    stringBuilder.clear()
                    durY = paddingVertical.toFloat()         //修改当前页的距离顶部的偏移量
                    usableHeight = (viewHeight - durY - paddingVertical).toInt()
                    // FULL 类型新页用全页几何；否则用左列/单列
                    currentBounds = if (imageStyles == "FULL") {
                        layoutBoundsPage()
                    } else if (dualColumnEnabled) {
                        layoutBoundsLeftColumn()
                    } else {
                        layoutBoundsPage()
                    }
                    true
                }
            } else {
                false
            }
        }

        if (!isNewPage) {  //非新开的一页,
            //需要重新计算,防止超过了图片可用高度
            if (width + imgVerticalMargin > usableHeight) { //图片高度 + 图片的上边距 > 图片可显示高度
                //对图片再次缩放
                val (widthInPage, heightInPage) = ImageLayoutProvider.constraintImageSize(
                    imgWidth,
                    imgHeight,
                    imgSrc,
                    currentBounds.width,   // v4：currentBounds.width
                    usableHeight - imgVerticalMargin
                )
                width = widthInPage
                height = heightInPage
            }
            durY += imgVerticalMargin  //先加上图片的上边距
        } else {
            if (imageStyles == "FULL") { //全屏展示时, 居中显示（FULL 用全页几何）
                val (widthInPage, heightInPage) = ImageLayoutProvider.fillImageSize(
                    imgWidth,
                    imgHeight,
                    imgSrc,
                    visibleWidth,
                    visibleHeight
                )
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

        //图片的左边和右边（v4：按 currentBounds.startX 居中，替代 paddingHorizontal）
        val (start, end) = if (currentBounds.width > width) {      //图片显示宽度小于当前列宽
            val adjustWidth = (currentBounds.width - width) / 2f   //左偏移量
            Pair(
                currentBounds.startX.toFloat() + adjustWidth,
                currentBounds.startX.toFloat() + adjustWidth + width
            )
        } else {
            Pair(currentBounds.startX.toFloat(), (currentBounds.startX + width).toFloat())
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

        // 后台预解码图片并写入 ImageProvider 缓存，使后续主线程 onDraw 的 getImage 调用成为零 I/O 缓存命中，
        // 消除阅读页主线程图片文件 I/O（Sentry file.read 79ms）。
        // 此处 width/height 是约束后的最终显示尺寸，与 drawImage 传给 getImage 的 target 尺寸一致 → sampleSize/缓存 key 必然命中。
        // 异步派发，不阻塞分页线程返回；赶不上的首帧由 getImage 同步兜底。
        Coroutines.scope().launchIO {
            ImageProvider.preload(imgSrc, width, height)
        }

        return LayoutCursor(durY + imgVerticalMargin, currentBounds)  //加上图片的下边距
    }

    /**
     * F2 新增:把纯文本 + inline 字号区间转成 SpannableStringBuilder,
     * 挂 RelativeSizeSpan 让 StaticLayout 自动 per-char 度量。
     * - inlineStyles null/empty → 直接返回原 String 引用(零开销,90%+ 段落)
     */
    internal fun buildSpannedText(
        text: String,
        inlineStyles: List<InlineStyle>?
    ): CharSequence {
        if (inlineStyles.isNullOrEmpty() ||
            inlineStyles.none { it.props.fontScale != null }) return text
        val ssb = SpannableStringBuilder(text)
        inlineStyles.forEach { style ->
            val start = style.start.coerceIn(0, text.length)
            val end = style.end.coerceIn(start, text.length)
            val fontScale = style.props.fontScale
            if (end > start && fontScale != null) {
                ssb.setSpan(
                    RelativeSizeSpan(fontScale),
                    start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return ssb
    }

    /**
     * v4（方案 B）：参数从 `offsetY: Float` 改为 `cursor: LayoutCursor`，
     * 返回类型从 Float 改为 [LayoutCursor]。透传 cursor.bounds 给所有子函数。
     *
     * 与 v3 的关键区别：无 startLine 参数（方案 B 不需要），无 overflowLines 守卫
     * （子函数内部已处理列切换）。底部间距在整段排完后追加。
     */
    private suspend fun setTypeText(
        paragraph: ReaderText,
        paragraphIndex: Int,    //段落在章节中的索引位置
        cursor: LayoutCursor,   // v4 新增：游标（offsetY + bounds）
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        isTitle: Boolean,
        chapterIsRtl: Boolean
    ): LayoutCursor {
//        Logger.d("ChapterProvider::setTypeText::paragraph=${paragraph}")
        val offsetY = cursor.offsetY
        val bounds = cursor.bounds

        val readerPrefs = readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()
        val userSetParagraphSpacing = (readerPrefs?.paragraphSpacing?.toFloat() ?: 0.0f).coerceAtLeast(0.0f)
        var durY = if (isTitle) offsetY + titleTopSpacing else offsetY

        var text: String = when (paragraph) {
            is ReaderText.Chapter -> paragraph.title
            is ReaderText.Text -> paragraph.line
            else -> ""
        }.toString()
        if (text.isEmpty() || text.isBlank()) { //对于无显示内容的空行，显示一个空白符
            val lineHeight = if (isTitle) {
                titlePaint.textHeight
            } else {
                contentPaint.textHeight
            }
//            Logger.d("ChapterProvider::setTypeText::empty text::lineHeight=$lineHeight,durY=$durY")
            val lineSpace = userSetParagraphSpacing * lineHeight
            durY += lineSpace
//            Logger.d("ChapterProvider::setTypeText::empty text::lineSpace=$lineSpace,durY=$durY")
            var curBounds = bounds
            if (durY + lineHeight > visibleBottom) {
                if (curBounds.isLeftColumn) {
                    // 左列满 → 切右列（同页），不建新页
                    curBounds = layoutBoundsRightColumn()
                    durY = paddingVertical.toFloat()
                } else {
                    // 右列满 / 单列装不下 → 建新页，回左列（或单列 page）
                    val lastPage = textPages.last()
                    lastPage.text = stringBuilder.toString()
                    pageLines.add(lastPage.textLines.size)
                    pageLengths.add(lastPage.text.length)
                    lastPage.height = durY

                    textPages.add(TextPage())
                    stringBuilder.clear()
                    durY = paddingVertical.toFloat()
                    curBounds = if (dualColumnEnabled) layoutBoundsLeftColumn() else layoutBoundsPage()
                }
            } else {
                durY += lineHeight
            }
            Logger.d("ChapterProvider::setTypeText::empty text:: after add lineHeight, then durY=$durY")
            return LayoutCursor(durY, curBounds)
        }


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
        // 方案 §2.2 D 防御项：letter-spacing 在 shaping 后拉开 glyph，会拆断阿拉伯等连写文字。
        // 章级方向（chapterIsRtl）只有此处可知；本拷贝供新引擎与 setTextTable 共用，置 0 不泄漏
        // 到 upStyle 的跨章节共享单例画笔。
        if (chapterIsRtl) {
            textPaint.letterSpacing = 0f
        }

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
            //文字粗体
            textPaint.typeface =
                getTypeface(paragraph.textCssInfo.fontWeight, paragraph.textCssInfo.fontStyle)
            textAlign = paragraph.textCssInfo.textAlign
            if (paragraph.textCssInfo.fontStyle == CssFontStyle.CssFontStyleItalic) {   //设置斜体
                textPaint.textSkewX = -0.25f
            }
            if (paragraph.textCssInfo.display == "block") {
                val fs = paragraph.textCssInfo.fontSize
                when {
                    fs.isEm() -> textPaint.textSize *= fs.value
                    fs.isPx() -> textPaint.textSize = fs.value
                }
            }

            val userSetIndent = (readerPrefs?.paragraphIndent?.toFloat() ?: 0f)   //用户设置的首航缩进
            val textIndent =
                if (paragraph.textCssInfo.textIndent.isEm()) paragraph.textCssInfo.textIndent.value.toInt() else 0
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
                firstLineIndent =
                    (if (userSetIndent <= 0.0f) textIndent.toFloat() else userSetIndent) * oneWordWidth   //书籍自带的样式
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

        val seg = paragraph.segDirect ?: SegmentResult(TextDirection.LTR, false, emptyList())

        //是否是列表，嵌套列表

        var isListRow: Boolean = false
        var listLevel: Int = 0
        var listOrder: Int = 0
        var liTag : TextTag? = null
        isListRow = if (paragraph is ReaderText.Text) {
            liTag = ListOrderCalculator.findOwnLi(paragraph.annotations)
            listOrder = ListOrderCalculator.getLiOrder(liTag, paragraph.annotations)
            liTag != null
        } else false

        if (isListRow) {
            if (paragraph is ReaderText.Text) {
                listLevel = paragraph.annotations.filter { tag ->
                    tag.name == "ul" || tag.name == "ol"
                }.size

                if (listLevel > 0) {
                    val containerWidth = if (dualColumnEnabled && columnWidth > 0) columnWidth else visibleWidth

                    val orderLabelWidth = if (listOrder > 0) {
                        val maxOrder = ListOrderCalculator.maxOrderOf(liTag, paragraph.annotations)
                        RenderResources.listMarkerPaint.measureText(
                            ListDotRenderer.orderedLabel(maxOrder, seg?.anchorBaseRtl == true))
                    } else 0f

                    val listIndent = ListDotRenderer.calcListIndent(listLevel, textPaint.textSize,
                        containerWidth.toFloat(), orderLabelWidth)

                    // 列表缩进作用于阅读起始侧：RTL 段（走 RTL 引擎）加在右侧（为圆点预留空间），
                    // 其余（纯 LTR legacy / 混合 LTR 基调）维持左侧现状
                    if (seg?.anchorBaseRtl == true) {
                        marginRight += listIndent
                    } else {
                        marginLeft += listIndent
                    }
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

        // Alignment decision chain: (1) title → Center, (2) table row + Undefined → Left,
        // (3) forceAlignOverride → user choice, (4) CSS Undefined → user fallback, (5) CSS value → keep as-is
        if (isTitle) {
            textAlign = CssTextAlign.CssTextAlignCenter
        } else if (isTableRow && textAlign == CssTextAlign.CssTextAlignUndefined) {
            textAlign = CssTextAlign.CssTextAlignLeft
        } else if (readerPrefs?.forceAlignOverride == true) {
            textAlign = userTextAlignToCss(readerPrefs?.userTextAlign ?: 4)
        } else if (textAlign == CssTextAlign.CssTextAlignUndefined) {
            textAlign = userTextAlignToCss(readerPrefs?.userTextAlign ?: 4)
        }

        // Center/Right alignment → suppress firstLineIndent (paragraph indent breaks centered/right text)
        if (!isTableRow && seg?.baseRtl == true &&
            textAlign == CssTextAlign.CssTextAlignLeft) {
            textAlign = CssTextAlign.CssTextAlignRight
        }
        if (textAlign == CssTextAlign.CssTextAlignCenter || textAlign == CssTextAlign.CssTextAlignRight) {
            firstLineIndent = 0f
        }

        if (!isTableRow && !isListRow) {
//            Logger.d("ChapterProvider::userSetParagraphSpacing=$userSetParagraphSpacing")
            durY += if (userSetParagraphSpacing > 0) (userSetParagraphSpacing * textPaint.textHeight) else marginTop
        }

        // v4：子函数返回 LayoutCursor，透传 bounds
        val result = if (isTableRow) {               //是表格行
            setTextTable(
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
                durY,
                bounds   // v4：透传
            )
        } else {                    //非表格行：列表/行内图/标题/普通段（含 RTL/LTR/混排），全部走 RTL 引擎 layoutNormalTextRtl
            // F2: 构造含 Span 的 CharSequence(仅当段落有 inline 字号时)
            // ★ 命名注意:局部变量用 paragraphInlineFontSizes
            val paragraphInlineFontSizes: List<InlineStyle>? = if (paragraph is ReaderText.Text) {
                paragraph.inlineStyles
            } else {
                null  // 标题(ReaderText.Chapter)/图片走旧路径
            }
            val charSequence: CharSequence = buildSpannedText(text, paragraphInlineFontSizes)

            // buildSpannedText 内部判断 null/empty → 直接返回原 String 引用(零开销)

            TextLayoutProvider.layoutNormalTextRtl(
                charSequence,
                paragraphInlineFontSizes,
                seg,
                textPaint,

                marginLeft,
                marginRight,
                firstLineIndent,
                isTitle,
                isListRow,

                listLevel,
                listOrder,
                paragraphIndex,
                textAlign,
                lineHeightParam,
                paragraph,

                textPages,
                pageLines,
                pageLengths,
                stringBuilder,

                durY,
                bounds,
                chapterIsRtl,
                hasInlineImg  //是否有段落内的图片
            )
        }

        // v4：底部间距在整段排完后追加（result.offsetY 已是整段排完的 Y）
        var adjustedY = result.offsetY
        if (!isTableRow && !isListRow) {
            //一个自然段落遍历完
            if (isTitle) {
                adjustedY += titleBottomSpacing                          //是标题行，则加上标题的底部间距
            }
            if (marginBottom > 0f) {
                adjustedY += marginBottom
            }
        }
//        durY += textPaint.textHeight * paragraphSpacing   //是段落，则加上段落间距 //TODO
        return LayoutCursor(adjustedY, result.bounds)
    }

    /**
     * v4（方案 B）：返回类型从 Float 改为 [LayoutCursor]，新增 [bounds] 参数。
     * 表格行作为原子单元切列（不拆分）。
     *
     * [bugfix]：原 `durY + lineHeight > visibleHeight` 改为 `> visibleBottom`——
     * visibleHeight 是高度值，visibleBottom 是 Y 偏移，分页判断应该用后者。
     * 此 bug 在单列下因两者差值（= paddingVertical）较小而偶发未暴露，双列/大边距下会少建一页。
     */
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
        offsetY: Float,
        bounds: LayoutBounds = layoutBoundsPage()   // v4 新增
    ): LayoutCursor {
        var durY = offsetY
        var currentBounds = bounds   // v4：局部变量，随列切换更新
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
                                    tablePercents.add(
                                        per.substring(0, per.length - 1).toIntOrNull() ?: 0
                                    )
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
                    // v4 方案 B：表格行作为原子单元，整段在同一个列内排版（不中途切列）。
                    // 先用「单行高度」做预检：若连一行都放不下当前列，则切列/建新页。
                    // 切列后用 layoutBounds 快照固定本段表格的列几何——单元格坐标和边框线都基于它，
                    // 保证一致；下方行渲染循环只做页面级拆分（不再切列）。
                    val singleLineHeight = textPaint.textHeight * lineSpacingExtra * lineHeightParam
                    if (durY + singleLineHeight > visibleBottom) {
                        if (currentBounds.isLeftColumn) {
                            currentBounds = layoutBoundsRightColumn()
                            durY = paddingVertical.toFloat()
                        } else {
                            val lastPage = textPages.last()
                            lastPage.text = stringBuilder.toString()
                            pageLines.add(lastPage.textLines.size)
                            pageLengths.add(lastPage.text.length)
                            lastPage.height = durY

                            textPages.add(TextPage())
                            stringBuilder.clear()
                            durY = paddingVertical.toFloat()
                            currentBounds = if (dualColumnEnabled) layoutBoundsLeftColumn() else layoutBoundsPage()
                        }
                    }
                    val layoutBounds = currentBounds   // 固定快照，单元格坐标 + 边框线均基于此
                    val fullWidth =
                        layoutBounds.width - marginLeft.roundToInt() - marginRight.roundToInt()   // v4：layoutBounds.width
                    var maxLineCount = 1 //最大行数，用来计算一行的高度
                    var textLineMaps = hashMapOf<Int, ArrayList<TextLine>>()  //遍历完，用来合并TextLine
                    //每个单元格
                    for (index in 0 until tagCells.size) {
                        val tagCell = tagCells[index]
                        val tagPercent: Int = tablePercents[index] //当前单元格所占的宽度的百分比,
//                        Logger.d("ChapterProvider::setTextTable::line[${paragraph.line}],tagCell[$tagCell],index[$index],tagPercent=$tagPercent")
                        val text =
                            if (tagCell.start in 0 until paragraph.line.length && tagCell.end in 0..paragraph.line.length) {
                                paragraph.line.substring(tagCell.start, tagCell.end)
                            } else if (tagCell.start in 0 until paragraph.line.length && tagCell.end > paragraph.line.length) {
                                paragraph.line.substring(tagCell.start)
                            } else {
                                ""
                            }

                        val usableWidth =
                            (fullWidth * (tagPercent / 100f) - 2 * tableCellInnerPadding).toInt()   //可用宽度
                        val leftOffset =
                            (fullWidth * (leftOffsetPercent / 100f) + tableCellInnerPadding).toInt()    //距离屏幕左边的偏移位置
                        var rightOffset = layoutBounds.endX - (usableWidth + leftOffset)     //距离屏幕右边的偏移量（v4：layoutBounds.endX）
                        if (rightOffset < 0) {
                            rightOffset = 0
                        }

                        val layout = StaticLayout.Builder.obtain(
                            text,
                            0,
                            text.length,
                            textPaint,
                            usableWidth
                        )
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
                                CssTextAlign.CssTextAlignLeft, CssTextAlign.CssTextAlignJustify, CssTextAlign.CssTextAlignUndefined -> addCharsToLineLeft(
                                    textLine,
                                    words.toStringArray(),
                                    textPaint,
                                    layoutBounds.startX.toFloat() + leftOffset + marginLeft,   // v4：layoutBounds.startX
                                    layoutBounds
                                )

                                CssTextAlign.CssTextAlignRight -> addCharsToLineRight(
                                    textLine,
                                    words.toStringArray(),
                                    textPaint,
                                    desiredWidth,
                                    (layoutBounds.endX - usableWidth - leftOffset - layoutBounds.startX).toFloat().coerceAtLeast(0f),   // v4：相对列内右边距
                                    layoutBounds
                                )

                                CssTextAlign.CssTextAlignCenter -> {
                                    addCharsToLineLeft(
                                        textLine, words.toStringArray(), textPaint,
                                        layoutBounds.startX.toFloat() + leftOffset + marginLeft + (usableWidth - desiredWidth) / 2f,   // v4：layoutBounds.startX
                                        layoutBounds
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
                        // v4 方案 B + [bugfix]：表格只做页面级拆分（不切列——单元格已基于 layoutBounds 排好）。
                        // 原 visibleHeight → visibleBottom 的 bugfix 保留。
                        if (durY + lineHeight > visibleBottom) {
                            val lastPage = textPages.last()
                            lastPage.text = stringBuilder.toString()
                            pageLines.add(lastPage.textLines.size)
                            pageLengths.add(lastPage.text.length)
                            lastPage.height = durY

                            textPages.add(TextPage())
                            stringBuilder.clear()
                            durY = paddingVertical.toFloat()
                            // 新页从左列开始（表格跨页时整段重排到左列/单列）
                            currentBounds = if (dualColumnEnabled) layoutBoundsLeftColumn() else layoutBoundsPage()
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

                        //增加表格的边框线（v4：用 layoutBounds.startX/endX 替代 paddingHorizontal/visibleRight）
                        if (index == 0) {
                            //横线， 上面的一条横线
                            lastPage.textLines.add(
                                TextLine(
                                    isLine = true,
                                    lineStart = Pair(marginLeft + layoutBounds.startX, durY),
                                    lineEnd = Pair(layoutBounds.endX - marginRight, durY),
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
                            if (iline == percents.size - 1) {
                                lastPage.textLines.add(
                                    TextLine(
                                        isLine = true,
                                        lineStart = Pair(layoutBounds.endX - marginRight, durY),
                                        lineEnd = Pair(
                                            layoutBounds.endX - marginRight,
                                            durY + lineHeight
                                        ),
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
                                        lineStart = Pair(
                                            left + layoutBounds.startX + marginLeft,
                                            durY
                                        ),
                                        lineEnd = Pair(
                                            left + layoutBounds.startX + marginLeft,
                                            durY + lineHeight
                                        ),
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
                                    lineStart = Pair(
                                        marginLeft + layoutBounds.startX,
                                        durY + lineHeight
                                    ),
                                    lineEnd = Pair(layoutBounds.endX - marginRight, durY + lineHeight),
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
        return LayoutCursor(durY, currentBounds)
    }


    /****
     * 从左向右自然排列一行字符, 即左对齐。
     *
     * v4（P4 修复）：新增 [bounds] 参数
     *
     * F4: 新增 [charScaleLookup]/[paragraphOffset],按字符实际 scale 算宽度(混合字号)。
     */
    private fun addCharsToLineLeft(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        offsetX: Float,
        bounds: LayoutBounds = layoutBoundsPage(),
        charScaleLookup: (Int) -> Float = { 1f },   // F4 新增(默认向后兼容)
        paragraphOffset: Int = 0                     // F4 新增:words[0] 在段落中的全局 offset
    ) {
        var x = offsetX
        workPaint.set(textPaint)
        val baseTextSize = textPaint.textSize
        words.forEachIndexed { i, char ->
            val scale = charScaleLookup(paragraphOffset + i)
            workPaint.textSize = baseTextSize * scale   // scale=1 跳过赋值,避免 native fontMetrics 重算
            val cw = StaticLayout.getDesiredWidth(char, workPaint)
            val x1 = x + cw
            textLine.addTextChar(
                charData = char,
                start = bounds.startX + x,    // v4：bounds.startX 替代 paddingHorizontal
                end = bounds.startX + x1
            )
            x = x1
        }
        exceed(textLine, words, bounds)
    }

    /**
     * 右对齐显示文本。
     *
     * v4（P4 修复）：新增 [bounds] 参数，右对齐按 bounds.width 计算（v4）。
     *
     * F4: 透传 [charScaleLookup]/[paragraphOffset]。desiredWidth 已是 layout.getLineWidth
     *      返回的混合字号宽度(Span 路径下正确),无需重算。
     */
    private fun addCharsToLineRight(
        textLine: TextLine,
        words: Array<String>,
        textPaint: TextPaint,
        desiredWidth: Float,
        marginRight: Float,
        bounds: LayoutBounds = layoutBoundsPage(),
        charScaleLookup: (Int) -> Float = { 1f },   // F4 新增(默认向后兼容)
        paragraphOffset: Int = 0                     // F4 新增
    ) {
        val x = bounds.width - desiredWidth - marginRight  //标题栏居中显示，左偏移（v4：bounds.width）
        addCharsToLineLeft(textLine, words, textPaint, x, bounds, charScaleLookup, paragraphOffset)
    }

    /****
     * 显示的一行内容，计算的偏移位置检测是否超过了边界， 对偏移进行纠偏。
     *
     * v4（P4 修复）：新增 [bounds] 参数，用 bounds.endX 替代单例 visibleRight 做越界检测。
     * 切列后传 rightColumn()，越界检测以右列右边界为准。
     */
    private fun exceed(textLine: TextLine, words: Array<String>, bounds: LayoutBounds = layoutBoundsPage()) {
        val endX = textLine.textChars.lastOrNull()?.end ?: 0f    //一行的最后一个字符显示的左边位置
        if (endX > bounds.endX.toFloat()) {    //超过了可视区域的右侧（v4：bounds.endX）
            val diff = (endX - bounds.endX) / words.size    //将超过的偏移量分配到每个字符上，然后对显示的一行每个字符位置进行修正
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
     *
     * v5：同步调用 [recomputeDerivedSizes]，确保 [loadContent] 的 IO 协程读到正确派生值。
     * 异步协程中 [upStyle] 仍会完整走一遍 [upVisibleSize]（含 DataStore 偏好读取），
     * 但此时 viewWidth/viewHeight/派生值已正确，upVisibleSize 只调整边距精度（~1px）。
     *
     * v6：返回尺寸是否真的变化（状态是否实际更新），作为「是否需要重排」的唯一信号源
     * （非连续翻页首帧裁剪修复）。调用方（[ContentTextView.onSizeChanged]）据此决定是否上报重排请求。
     * 仅当尺寸参数有效（>0）且与当前值不同、状态确实被更新时才返回 true；
     * 无效参数（<=0）不更新状态，返回 false，避免无意义的重排。
     */
    fun setViewSize(context: Context, width: Int, height: Int): Boolean {
        Logger.d("ChapterProvider::setViewSize,width=$width, height=$height," +
                "paddingHorizontal=$paddingHorizontal, paddingVertical=$paddingVertical")
        if (width <= 0 || height <= 0) {
            Logger.d("ChapterProvider::setViewSize invalid dimensions, changed=false")
            return false
        }
        val refreshStyle = (width != viewWidth || height != viewHeight)
        if (refreshStyle) {
            viewWidth = width
            viewHeight = height
            upVisibleSize(context)
            recomputeDerivedSizes()
            Coroutines.mainScope().launch {
                upStyle(context)
            }
        }
        Logger.d("ChapterProvider::setViewSize,viewWidth=$viewWidth, viewHeight=$viewHeight, changed=$refreshStyle")
        return refreshStyle
    }

    fun init(
        context: Context,
        readTipPreferencesUtil: ReadTipPreferencesUtil,
        readerPreferencesUtil: ReaderPreferencesUtil
    ) {
        Logger.d("ChapterProvider::init,then invoke ChapterProvider::upStyle")
        this.readTipPreferencesUtil = readTipPreferencesUtil
        this.readerPreferencesUtil = readerPreferencesUtil
        Coroutines.mainScope().launch {
            upStyle(context)
        }
    }

    internal fun userTextAlignToCss(userTextAlign: Int): CssTextAlign {
        return when (userTextAlign) {
            1 -> CssTextAlign.CssTextAlignLeft
            2 -> CssTextAlign.CssTextAlignRight
            3 -> CssTextAlign.CssTextAlignCenter
            4 -> CssTextAlign.CssTextAlignJustify
            else -> CssTextAlign.CssTextAlignJustify
        }
    }
}