package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.wxn.base.ext.getCompatColor
import com.wxn.bookread.R
import com.wxn.base.util.Coroutines
import com.wxn.bookread.databinding.ViewBookPageBinding
import com.wxn.bookread.ext.dp
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.widget.BatteryView
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ContentView(context: Context) : FrameLayout(context) {

    private val binding = ViewBookPageBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    private var battery = 100
    private var tvTitle: BatteryView? = null            //标题 章节名
    private var tvTime: BatteryView? = null             //时间
    private var tvBattery: BatteryView? = null          //电量
    private var tvPage: BatteryView? = null             //
    private var tvTotalProgress: BatteryView? = null    //总进度
    private var tvPageAndTotal: BatteryView? = null     //页数和总数
    private var tvBookName: BatteryView? = null         //书名

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
//
//    init {
//        //设置背景颜色防止切换背景时文字重叠
//        setBackgroundColor(context.getCompatColor(R.color.background))
//        upTipStyle()
//        upStyle()
//        binding.contentTextView.upView = {
//            setProgress(it)
//        }
//    }
//
//
//    fun upStyle() = with(binding){
//        Coroutines.mainScope().launch{
//            val preference = ChapterProvider.readerPreferencesUtil.readerPreferencesFlow.firstOrNull() ?: return
//            val textColor = preference.textColor ?: Color.BLACK
//
//            var headerPaddingLeft: Int = 16
//            var headerPaddingRight: Int = 16
//            var headerPaddingTop: Int = 0
//            var headerPaddingBottom: Int = 0,
//            var footerPaddingBottom: Int = 60
//            var footerPaddingLeft: Int = 16
//            var footerPaddingRight: Int = 16
//            var footerPaddingTop: Int = 6
//            var showHeaderLine: Boolean = false
//            var showFooterLine: Boolean = false
//
////        ReadBookConfig.apply {
//            bvHeaderLeft.typeface = ChapterProvider.typeface
//            tvHeaderLeft.typeface = ChapterProvider.typeface
//            tvHeaderMiddle.typeface = ChapterProvider.typeface
//            tvHeaderRight.typeface = ChapterProvider.typeface
//            bvFooterLeft.typeface = ChapterProvider.typeface
//            tvFooterLeft.typeface = ChapterProvider.typeface
//            tvFooterMiddle.typeface = ChapterProvider.typeface
//            tvFooterRight.typeface = ChapterProvider.typeface
//            bvHeaderLeft.setColor(textColor)
//            tvHeaderLeft.setColor(textColor)
//            tvHeaderMiddle.setColor(textColor)
//            tvHeaderRight.setColor(textColor)
//            bvFooterLeft.setColor(textColor)
//            tvFooterLeft.setColor(textColor)
//            tvFooterMiddle.setColor(textColor)
//            tvFooterRight.setColor(textColor)
//            upStatusBar()
//            binding.llHeader.setPadding(
//                headerPaddingLeft.dp,
//                headerPaddingTop.dp,
//                headerPaddingRight.dp,
//                headerPaddingBottom.dp
//            )
//            llFooter.setPadding(
//                footerPaddingLeft.dp,
//                footerPaddingTop.dp,
//                footerPaddingRight.dp,
//                footerPaddingBottom.dp
//            )
//            vwTopDivider.visible(showHeaderLine)
//            vwBottomDivider.visible(showFooterLine)
//            binding.contentTextView.upVisibleRect()
//        }
//        upTime()
//        upBattery(battery)
//    }
//
//    /**
//     * 显示状态栏时隐藏header
//     */
//    fun upStatusBar() = with(binding.vwStatusBar){
//        setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)
//        isGone =
//            ReadBookConfig.hideStatusBar || (activity as? BaseActivity<*>)?.isInMultiWindow == true
//    }
//
//    fun upTipStyle()  = with(binding) {
//        ReadTipConfig.apply {
//            tvHeaderLeft.isInvisible = tipHeaderLeft != chapterTitle
//            bvHeaderLeft.isInvisible = tipHeaderLeft == none || !tvHeaderLeft.isInvisible
//            tvHeaderRight.isGone = tipHeaderRight == none
//            tvHeaderMiddle.isGone = tipHeaderMiddle == none
//            tvFooterLeft.isInvisible = tipFooterLeft != chapterTitle
//            bvFooterLeft.isInvisible = tipFooterLeft == none || !tvFooterLeft.isInvisible
//            tvFooterRight.isGone = tipFooterRight == none
//            tvFooterMiddle.isGone = tipFooterMiddle == none
//            binding.llHeader.isGone = hideHeader
//            llFooter.isGone = hideFooter
//        }
//        tvTitle = when (ReadTipConfig.chapterTitle) {
//            ReadTipConfig.tipHeaderLeft -> tvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> tvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvTitle?.apply {
//            isBattery = false
//            textSize = 12f
//        }
//        tvTime = when (ReadTipConfig.time) {
//            ReadTipConfig.tipHeaderLeft -> bvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> bvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvTime?.apply {
//            isBattery = false
//            textSize = 12f
//        }
//        tvBattery = when (ReadTipConfig.battery) {
//            ReadTipConfig.tipHeaderLeft -> bvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> bvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvBattery?.apply {
//            isBattery = true
//            textSize = 10f
//        }
//        tvPage = when (ReadTipConfig.page) {
//            ReadTipConfig.tipHeaderLeft -> bvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> bvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvPage?.apply {
//            isBattery = false
//            textSize = 12f
//        }
//        tvTotalProgress = when (ReadTipConfig.totalProgress) {
//            ReadTipConfig.tipHeaderLeft -> bvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> bvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvTotalProgress?.apply {
//            isBattery = false
//            textSize = 12f
//        }
//        tvPageAndTotal = when (ReadTipConfig.pageAndTotal) {
//            ReadTipConfig.tipHeaderLeft -> bvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> bvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvPageAndTotal?.apply {
//            isBattery = false
//            textSize = 12f
//        }
//        tvBookName = when (ReadTipConfig.bookName) {
//            ReadTipConfig.tipHeaderLeft -> bvHeaderLeft
//            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
//            ReadTipConfig.tipHeaderRight -> tvHeaderRight
//            ReadTipConfig.tipFooterLeft -> bvFooterLeft
//            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
//            ReadTipConfig.tipFooterRight -> tvFooterRight
//            else -> null
//        }
//        tvBookName?.apply {
//            isBattery = false
//            textSize = 12f
//        }
//    }
//
//    fun setBg(bg: Drawable?) {
//        binding.pagePanel.background = bg
//    }
//
//    fun upTime() {
//        tvTime?.text = timeFormat.format(Date(System.currentTimeMillis()))
//    }
//
//    fun upBattery(battery: Int) {
//        this.battery = battery
//        tvBattery?.setBattery(battery)
//    }
//
//    fun setContent(textPage: TextPage, resetPageOffset: Boolean = true) {
//        setProgress(textPage)
//        if (resetPageOffset)
//            resetPageOffset()
//        binding.contentTextView.setContent(textPage)
//    }
//
//    fun resetPageOffset() {
//        binding.contentTextView.resetPageOffset()
//    }
//
//    @SuppressLint("SetTextI18n")
//    fun setProgress(textPage: TextPage) = textPage.apply {
//        tvBookName?.text = ReadBook.book?.bookName
//        tvTitle?.text = textPage.title
//        tvPage?.text = "${index.plus(1)}/$pageSize"
//        tvTotalProgress?.text = readProgress
//        tvPageAndTotal?.text = "${index.plus(1)}/$pageSize  $readProgress"
//    }
//
//    fun onScroll(offset: Float) {
//        binding.contentTextView.onScroll(offset)
//    }
//
//    fun upSelectAble(selectAble: Boolean) {
//        binding.contentTextView.selectAble = selectAble
//    }
//
//    fun selectText(
//        x: Float, y: Float,
//        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit
//    ) {
//        return binding.contentTextView.selectText(x, y - headerHeight, select)
//    }
//
//    fun selectStartMove(x: Float, y: Float) {
//        binding.contentTextView.selectStartMove(x, y - headerHeight)
//    }
//
//    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
//        binding.contentTextView.selectStartMoveIndex(relativePage, lineIndex, charIndex)
//    }
//
//    fun selectEndMove(x: Float, y: Float) {
//        binding.contentTextView.selectEndMove(x, y - headerHeight)
//    }
//
//    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
//        binding.contentTextView.selectEndMoveIndex(relativePage, lineIndex, charIndex)
//    }
//
//    fun cancelSelect() {
//        binding.contentTextView.cancelSelect()
//    }
//
//    val selectedText: String get() = binding.contentTextView.selectedText
}