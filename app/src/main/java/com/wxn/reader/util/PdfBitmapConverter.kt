package com.wxn.reader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.wxn.base.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.min

class PdfBitmapConverter @Inject constructor(
    private val context: Context
) {
    suspend fun getPageCount(contentUri: Uri): Int {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(contentUri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        renderer.pageCount
                    }
                } ?: throw IOException("Unable to open PDF file")
            } catch (e: Exception) {
                throw IOException("Failed to get page count: ${e.message}", e)
            }
        }
    }

    /**
     * 将 PDF 指定页面渲染为 Bitmap。
     *
     * 渲染尺寸策略（与 ImageProvider 同源）：
     * - 朝目标显示尺寸 × 2（Retina 清晰度）渲染，使矢量 PDF 文字清晰；
     * - 以设备 Canvas 单张位图上限（maxDim）作硬约束，确保 OOM 不可能发生；
     * - PDF 是矢量，Compose 的 ContentScale.Fit 无法补回未渲染的细节，故允许 >1f 上采样。
     *
     * @param displayWidth 目标显示宽度（px），0 = 按设备上限渲染
     * @param displayHeight 目标显示高度（px），0 = 按设备上限渲染
     */
    suspend fun pdfToBitmap(
        contentUri: Uri,
        pageIndex: Int,
        displayWidth: Int = 0,
        displayHeight: Int = 0
    ): Bitmap {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(contentUri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                        throw IndexOutOfBoundsException("Invalid page index: $pageIndex")
                    }
                    renderer.openPage(pageIndex).use { page ->
                        // ---- 计算清晰且安全的渲染尺寸 ----
                        val maxDim = getDeviceMaxBitmapDimension()
                        val pageW = page.width.toFloat()
                        val pageH = page.height.toFloat()

                        // 目标清晰渲染尺寸：屏幕宽高 × 2（Retina），0 时回退到设备上限
                        val targetW = if (displayWidth > 0) displayWidth * 2 else maxDim
                        val targetH = if (displayHeight > 0) displayHeight * 2 else maxDim

                        // 1) 朝清晰目标的上采样（允许 >1f，让矢量文字清晰）
                        val scaleByDisplay = minOf(targetW / pageW, targetH / pageH)
                        // 2) 设备位图上限的硬约束（使 OOM 不可能发生）
                        val scaleByMaxDim = maxDim.toFloat() / maxOf(pageW, pageH)
                        // 取两者较小值：既清晰又不超限；下限 1f 保证小页面至少按原尺寸渲染
                        val scale = minOf(scaleByDisplay, scaleByMaxDim).coerceAtLeast(1f)

                        val safeWidth = ceil(pageW * scale).toInt().coerceIn(1, maxDim)
                        val safeHeight = ceil(pageH * scale).toInt().coerceIn(1, maxDim)

                        Logger.d(
                            "PdfBitmapConverter::render page=$pageIndex " +
                                    "${page.width}x${page.height} " +
                                    "scale=${"%.2f".format(scale)} -> ${safeWidth}x${safeHeight}"
                        )

                        // ---- 创建 bitmap 并渲染（统一 ARGB_8888）----
                        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            } ?: throw IOException("Unable to open PDF file")
        }
    }

    companion object {
        private const val FALLBACK_MAX_BITMAP_DIMENSION = 4096

        @Volatile
        private var cachedMaxBitmapDimension: Int = 0

        /**
         * 获取设备 Canvas 单张 bitmap 安全上限（最长边像素）。
         * 与 ImageProvider.getDeviceMaxBitmapDimension() 策略一致。
         * TODO(CR-14): 后续与 ImageProvider 一起抽取到 base 模块复用。
         */
        private fun getDeviceMaxBitmapDimension(): Int {
            cachedMaxBitmapDimension.takeIf { it > 0 }?.let { return it }
            val ret = try {
                val canvas = Canvas()
                val reported = maxOf(canvas.maximumBitmapWidth, canvas.maximumBitmapHeight)
                if (reported > 0) min(reported, FALLBACK_MAX_BITMAP_DIMENSION)
                else FALLBACK_MAX_BITMAP_DIMENSION
            } catch (e: Throwable) {
                FALLBACK_MAX_BITMAP_DIMENSION
            }
            cachedMaxBitmapDimension = ret
            return ret
        }
    }
}
