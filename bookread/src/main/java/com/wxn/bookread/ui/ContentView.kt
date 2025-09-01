package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.wxn.base.ext.getCompatColor
import com.wxn.base.ext.statusBarHeight
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchMain
import com.wxn.bookread.R
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.databinding.ViewBookPageBinding
import com.wxn.bookread.provider.ChapterProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ContentView(context: Context) : FrameLayout(context) {

    private val binding = ViewBookPageBinding.inflate(
        LayoutInflater.from(context), this, true
    )

//    private var battery = 100
//    private var tvTitle: BatteryView? = null            //标题 章节名
//    private var tvTime: BatteryView? = null             //时间
//    private var tvBattery: BatteryView? = null          //电量
//    private var tvPage: BatteryView? = null             //
//    private var tvTotalProgress: BatteryView? = null    //总进度
//    private var tvPageAndTotal: BatteryView? = null     //页数和总数
//    private var tvBookName: BatteryView? = null         //书名

    //
//    /***
//     * 头部高度 = 系统状态栏 + 标题高度
//     */
//    val headerHeight: Int
//        get() {
//            val h1 = if (ReadBookConfig.hideStatusBar) 0 else context.statusBarHeight
//            val h2 = if (binding.llHeader.isGone) 0 else binding.llHeader.height
//            return h1 + h2
//        }

    /**
     *  头部高度 = 系统状态栏 + 标题高度
     */
//    suspend fun getHeaderHeight(): Int {
//        val tipPreference = ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()
//                ?: return 0
//        val h1 = if (tipPreference.hideStatusBar) 0 else context.statusBarHeight
//        val h2 = if (binding.llHeader.isGone) 0 else binding.llHeader.height
//        return h1 //+ h2
//    }

    //
    init {
        //设置背景颜色防止切换背景时文字重叠
        setBackgroundColor(context.getCompatColor(R.color.background))
        upTipStyle()
        upStyle()
//        binding.contentTextView.upView = {
//            setProgress(it)
//        }
    }

    var callback: SelectTextCallback? = null

    fun setSelectTextCallback(callback: SelectTextCallback) {
        this.callback = callback
        binding.contentTextView.callback = callback
    }

    /****
     * 更新显示的样式
     */
    fun upStyle() {
        binding.apply {
            Coroutines.mainScope().launch {
                val tipPreference =
                    ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()
                        ?: return@launch
                val readPreference =
                    ChapterProvider.readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()
                        ?: return@launch
                val textColor = readPreference.textColor ?: Color.BLACK
//
                var headerPaddingLeft: Int = tipPreference.headerPaddingLeft
                var headerPaddingRight: Int = tipPreference.headerPaddingRight
                var headerPaddingTop: Int = tipPreference.headerPaddingTop
                var headerPaddingBottom: Int = tipPreference.headerPaddingBottom

                var footerPaddingBottom: Int = tipPreference.footerPaddingBottom
                var footerPaddingLeft: Int = tipPreference.footerPaddingLeft
                var footerPaddingRight: Int = tipPreference.footerPaddingRight
                var footerPaddingTop: Int = tipPreference.footerPaddingTop

                var showHeaderLine: Boolean = !tipPreference.hideHeader
                var showFooterLine: Boolean = !tipPreference.hideFooter

                Logger.d(
                    "ContentView::upStyle::headerPaddingLeft=$headerPaddingLeft,headerPaddingRight=$headerPaddingRight,headerPaddingTop=$headerPaddingTop,headerPaddingBottom=$headerPaddingBottom," +
                            "footerPaddingBottom=$footerPaddingBottom,footerPaddingLeft=$footerPaddingLeft,footerPaddingRight=$footerPaddingRight,footerPaddingTop=$footerPaddingTop," +
                            "showHeaderLine=$showHeaderLine,showFooterLine=$showFooterLine, textColor=0x$${
                                textColor.toString(
                                    16
                                )
                            }"
                )

//                bvHeaderLeft.typeface = ChapterProvider.typeface
//                tvHeaderLeft.typeface = ChapterProvider.typeface
//                tvHeaderMiddle.typeface = ChapterProvider.typeface
//                tvHeaderRight.typeface = ChapterProvider.typeface
//                bvFooterLeft.typeface = ChapterProvider.typeface
//                tvFooterLeft.typeface = ChapterProvider.typeface
//                tvFooterMiddle.typeface = ChapterProvider.typeface
//                tvFooterRight.typeface = ChapterProvider.typeface
//                bvHeaderLeft.setColor(textColor)
//                tvHeaderLeft.setColor(textColor)
//                tvHeaderMiddle.setColor(textColor)
//                tvHeaderRight.setColor(textColor)
//                bvFooterLeft.setColor(textColor)
//                tvFooterLeft.setColor(textColor)
//                tvFooterMiddle.setColor(textColor)
//                tvFooterRight.setColor(textColor)
                upStatusBar()
//                binding.llHeader.setPadding(
//                    headerPaddingLeft.dp,
//                    headerPaddingTop.dp,
//                    headerPaddingRight.dp,
//                    headerPaddingBottom.dp
//                )
//                llFooter.setPadding(
//                    footerPaddingLeft.dp,
//                    footerPaddingTop.dp,
//                    footerPaddingRight.dp,
//                    footerPaddingBottom.dp
//                )
//                vwTopDivider.visibility = if (showHeaderLine) View.VISIBLE else View.GONE
//                vwBottomDivider.visibility = if (showFooterLine) View.VISIBLE else View.GONE
                binding.contentTextView.refreshVisibleRect()
            }
//            upTime()
//            upBattery(battery)
        }
    }

    /**
     * 显示状态栏时隐藏header
     */
    fun upStatusBar() {
        with(binding.vwStatusBar) {
            setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)

            Coroutines.mainScope().launch {
//                ChapterProvider.tryCreatePreference(context)
//                val tipPreference =
//                    ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()
//                        ?: return@launch

//                isGone = tipPreference.hideStatusBar || (activity as? BaseActivity)?.isInMultiWindow == true
            }
        }
    }

    /***
     * 更新提示信息显示的控件， 这些控件的显示内容会根据设置不同而排列不同的内容
     */
    fun upTipStyle() {
        binding.apply {
            Coroutines.mainScope().launch {
//                ChapterProvider.tryCreatePreference(context)
                val tipPreference = ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull() ?: return@launch

                val tipHeaderLeft = tipPreference.tipHeaderLeft
                val tipHeaderRight = tipPreference.tipHeaderRight
                val tipHeaderMiddle = tipPreference.tipHeaderMiddle
                val tipFooterLeft = tipPreference.tipFooterLeft
                val tipFooterRight = tipPreference.tipFooterRight
                val tipFooterMiddle = tipPreference.tipFooterMiddle
                val hideHeader = tipPreference.hideHeader
                val hideFooter = tipPreference.hideFooter
                Logger.d(
                    "ContentView::upTipStyle::tipHeaderLeft=$tipHeaderLeft,tipHeaderRight=$tipHeaderRight,tipHeaderMiddle=$tipHeaderMiddle," +
                            "tipFooterLeft=$tipFooterLeft,tipFooterRight=$tipFooterRight,tipFooterMiddle=$tipFooterMiddle," +
                            "hideHeader=$hideHeader,hideFooter=$hideFooter"
                )
                //tipHeaderLeft=2,tipHeaderRight=3,tipHeaderMiddle=0,tipFooterLeft=1,tipFooterRight=6,tipFooterMiddle=0,hideHeader=true,hideFooter=false
//                tvHeaderLeft.isInvisible = tipHeaderLeft != ReadTip_chapterTitle
//                bvHeaderLeft.isInvisible = tipHeaderLeft == ReadTip_none || !tvHeaderLeft.isInvisible
//                tvHeaderRight.isGone = tipHeaderRight == ReadTip_none
//                tvHeaderMiddle.isGone = tipHeaderMiddle == ReadTip_none
//                tvFooterLeft.isInvisible = tipFooterLeft != ReadTip_chapterTitle
//                bvFooterLeft.isInvisible = tipFooterLeft == ReadTip_none || !tvFooterLeft.isInvisible
//                tvFooterRight.isGone = tipFooterRight == ReadTip_none
//                tvFooterMiddle.isGone = tipFooterMiddle == ReadTip_none
//                binding.llHeader.isGone = hideHeader
//                llFooter.isGone = hideFooter
//                tvTitle = when (ReadTip_chapterTitle) {
//                    tipHeaderLeft -> tvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> tvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvTitle?.apply {
//                    isBattery = false
//                    textSize = 12f
//                }
//                tvTime = when (ReadTip_time) {
//                    tipHeaderLeft -> bvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> bvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvTime?.apply {
//                    isBattery = false
//                    textSize = 12f
//                }
//                tvBattery = when (ReadTip_battery) {
//                    tipHeaderLeft -> bvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> bvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvBattery?.apply {
//                    isBattery = true
//                    textSize = 10f
//                }
//                tvPage = when (ReadTip_page) {
//                    tipHeaderLeft -> bvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> bvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvPage?.apply {
//                    isBattery = false
//                    textSize = 12f
//                }
//                tvTotalProgress = when (ReadTip_totalProgress) {
//                    tipHeaderLeft -> bvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> bvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvTotalProgress?.apply {
//                    isBattery = false
//                    textSize = 12f
//                }
//                tvPageAndTotal = when (ReadTip_pageAndTotal) {
//                    tipHeaderLeft -> bvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> bvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvPageAndTotal?.apply {
//                    isBattery = false
//                    textSize = 12f
//                }
//                tvBookName = when (ReadTip_bookName) {
//                    tipHeaderLeft -> bvHeaderLeft
//                    tipHeaderMiddle -> tvHeaderMiddle
//                    tipHeaderRight -> tvHeaderRight
//                    tipFooterLeft -> bvFooterLeft
//                    tipFooterMiddle -> tvFooterMiddle
//                    tipFooterRight -> tvFooterRight
//                    else -> null
//                }
//                tvBookName?.apply {
//                    isBattery = false
//                    textSize = 12f
//                }
            }
        }
    }

    /****
     * 更新背景显示
     */
    fun setBg(bg: Drawable?) {
        binding.pagePanel.background = bg
    }

    fun setBg(bgColor: Int) {
        binding.pagePanel.setBackgroundColor(bgColor)
    }

    /**
     * 更新时间显示
     */
//    fun upTime() {
//        tvTime?.text = SimpleDateFormat("HH:mm").format(Date(System.currentTimeMillis()))
//    }

    /****
     * 更新电池显示
     */
//    fun upBattery(battery: Int) {
//        this.battery = battery
//        tvBattery?.setBattery(battery)
//    }

    /****
     * 设置需要显示的TextPage内容
     */
    fun setContent(textPage: TextPage, resetPageOffset: Boolean = true) {
        Logger.i("ContentView::setContent::textPage.pageSize=${textPage.pageSize}")
        Coroutines.mainScope().launchMain {
//            setProgress(textPage)
            if (resetPageOffset) {
                resetPageOffset()
            }
            binding.contentTextView.setContent(textPage)
        }
    }

    /***
     * 重置 界面移动时的偏移值
     */
    fun resetPageOffset() {
        binding.contentTextView.resetPageOffset()
    }

    /****
     * 显示进度设置
     */
//    @SuppressLint("SetTextI18n")
//    fun setProgress(textPage: TextPage) = textPage.apply {
//            tvBookName?.text = callback?.book?.title.orEmpty()
//            tvTitle?.text = textPage.title
//            tvPage?.text = "${index.plus(1)}/$pageSize"
//            tvTotalProgress?.text = readProgress
//            tvPageAndTotal?.text = "${index.plus(1)}/$pageSize  $readProgress"
//    }

    /***
     * 移动时，设置显示界面的偏移
     */
    fun onScroll(offset: Float) {
        binding.contentTextView.onScroll(offset)
    }

    /***
     * 是否运行选中文本
     */
    fun upSelectAble(selectAble: Boolean) {
        binding.contentTextView.selectAble = selectAble
    }

    /****
     * 选中文本
     */
    fun selectText(
        x: Float, y: Float,
        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit
    ) {
        Coroutines.mainScope().launch {
            val headerHeight = context.statusBarHeight
            binding.contentTextView.selectText(x, y - headerHeight, select)
        }
    }

    /****
     * 移动 选中结束符
     */
    fun selectStartMove(x: Float, y: Float) {
        Coroutines.mainScope().launch {
            val headerHeight = context.statusBarHeight
            binding.contentTextView.selectStartMove(x, y - headerHeight)
        }
    }

    /****
     * 选中开始符 的位置设置
     */
    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectStartMoveIndex(relativePage, lineIndex, charIndex)
    }

    /****
     * 移动 选中结束符
     */
    fun selectEndMove(x: Float, y: Float) {
        Coroutines.mainScope().launch {
            val headerHeight = context.statusBarHeight
//            val headerHeight = getHeaderHeight()
            binding.contentTextView.selectEndMove(x, y - headerHeight)
        }
    }

    /***
     * 选中结束符 的位置设置
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectEndMoveIndex(relativePage, lineIndex, charIndex)
    }

    /****
     * 取消选中文字
     */
    fun cancelSelect() {
        Logger.i("ContentView::cancelSelect")
        binding.contentTextView.cancelSelect()
    }

    /***
     * 获取选中的文本内容
     */
    val selectedText: String get() = binding.contentTextView.selectText

    override fun onDetachedFromWindow() {
        Logger.i("ContentView::onDetachedFromWindow")
        callback = null
        super.onDetachedFromWindow()
    }
}