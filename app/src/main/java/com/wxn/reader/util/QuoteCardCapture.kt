package com.wxn.reader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import com.wxn.base.util.Logger
import com.wxn.reader.presentation.shareQuoteCard.components.QuoteCard
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardConfig
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 书摘卡片截图捕获层（无状态 object，Android-specific，KMP 时改 expect/actual）。
 *
 * 使用 [ComposeView] + [View.draw] 离屏渲染。临时 ComposeView 挂载到 Activity 的 contentView，
 * 继承其 ViewTreeLifecycleOwner。等待 composition + layout 完成后绘制到 [Bitmap]。
 *
 * 整个过程在主线程执行（View 操作要求），调用方应确保有 Loading 文字提示。
 */
object QuoteCardCapture {

    /** 渲染时长上限（ms），超出返回 null（调用方报 RENDER_TIMEOUT） */
    private const val RENDER_TIMEOUT_MS = 2500L

    /** composition 等待帧数（每帧 delay 16ms） */
    private const val COMPOSITION_SETTLE_FRAMES = 6

    /**
     * 捕获卡片为 [ImageBitmap]。
     *
     * @param width 输出 Bitmap 宽度（像素）
     * @param height 输出 Bitmap 高度（像素）
     * @return 捕获的 ImageBitmap，超时或失败返回 null
     */
    suspend fun capture(
        context: Context,
        data: QuoteCardData,
        editableText: String,
        config: QuoteCardConfig,
        coverBitmap: ImageBitmap?,
        fontFamily: FontFamily?,
        width: Int,
        height: Int
    ): ImageBitmap? = withContext(Dispatchers.Main) {
        val activity = context.findActivity() ?: run {
            Logger.w("QuoteCardCapture::capture: cannot find Activity from context")
            return@withContext null
        }
        // 防配置变更期间 Activity 已 destroyed/finishing，addView 会抛 IllegalStateException 或泄漏 container
        if (activity.isDestroyed || activity.isFinishing) {
            Logger.w("QuoteCardCapture::capture: activity destroyed/finishing, abort")
            return@withContext null
        }

        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            ?: return@withContext null

        // 创建临时 ComposeView（继承 rootView 的 ViewTreeLifecycleOwner）
        val composeView = ComposeView(activity).apply {
            setContent {
                MaterialTheme {
                    QuoteCard(
                        data = data,
                        editableText = editableText,
                        config = config,
                        coverBitmap = coverBitmap,
                        fontFamily = fontFamily,
                        modifier = Modifier.fillMaxSize()  // 填满 1080×1440 容器（A4）
                    )
                }
            }
        }
        val container = FrameLayout(activity).apply {
            addView(composeView, FrameLayout.LayoutParams(width, height))
            visibility = View.INVISIBLE  // 参与布局但不显示
        }

        return@withContext try {
            withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                rootView.addView(container)
                // 等待 composition 完成（Compose 异步组合）
                repeat(COMPOSITION_SETTLE_FRAMES) { delay(16) }
                // 强制 measure + layout
                container.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                container.layout(0, 0, width, height)
                // 再等一帧确保 layout 完成
                delay(32)
                // 绘制到 Bitmap
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                container.draw(canvas)
                bitmap.asImageBitmap()
            }.also {
                if (it == null) Logger.w("QuoteCardCapture::capture: timeout or failed")
            }
        } catch (e: OutOfMemoryError) {
            Logger.w("QuoteCardCapture::capture OOM: ${e.message}")
            null
        } catch (e: Exception) {
            Logger.w("QuoteCardCapture::capture failed: ${e.message}")
            null
        } finally {
            rootView.removeView(container)
        }
    }

    /** 从 Context 找到 Activity */
    private tailrec fun Context.findActivity(): android.app.Activity? {
        return when (this) {
            is android.app.Activity -> this
            is android.content.ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }
}
