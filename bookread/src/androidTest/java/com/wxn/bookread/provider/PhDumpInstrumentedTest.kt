package com.wxn.bookread.provider

import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** 临时诊断（取证后删除）：镜像场景 LTR 启发式 + 阿语内容 */
@RunWith(AndroidJUnit4::class)
class PhDumpInstrumentedTest {

    @Test
    fun dumpPrimaryHorizontal() {
        val paint = TextPaint().apply { textSize = 40f; isAntiAlias = true }
        // 镜像场景：LTR 表格（tableIsRtl=false）里的阿语单元格
        val samples = listOf(
            "الكتاب",
            "القاهرة",
            "abc عربي xyz"
        )
        for (s in samples) {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, 2000)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.LTR)
                .setIncludePad(true)
                .build()
            val sb = StringBuilder()
            sb.append("LTRbase TEXT=[$s] lines=${layout.lineCount} | ")
            for (i in 0..s.length) {
                sb.append("ph($i)=${layout.getPrimaryHorizontal(i)} ")
            }
            sb.append("| ")
            for (i in 0 until s.length) {
                sb.append("sh($i)=${layout.getSecondaryHorizontal(i)} ")
            }
            Log.i("PhDump", sb.toString())
        }
    }
}
