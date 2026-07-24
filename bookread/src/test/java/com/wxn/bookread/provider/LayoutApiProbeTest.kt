package com.wxn.bookread.provider

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 临时探针:反射列出 android.text.Layout 在 Robolectric 环境下可用的公开方法,
 * 用于核实 getLineLeading 是否存在,以及获取 leading 的正确 API 名。
 *
 * 运行:`./gradlew :bookread:testDebugUnitTest --tests "*LayoutApiProbeTest"`
 */
@RunWith(RobolectricTestRunner::class)
class LayoutApiProbeTest {

    @Test
    fun `dump all Layout public methods to find leading api`() {
        val paint = TextPaint().apply { textSize = 40f }
        val layout = StaticLayout.Builder.obtain("test", 0, 4, paint, 100).build()

        println("\n========== Layout 公开方法(含继承) ==========")
        val methods = Layout::class.java.methods
        methods.sortedBy { it.name }.forEach { m ->
            val params = m.parameterTypes.joinToString(",") { it.simpleName }
            val ret = m.returnType.simpleName
            if (m.name.contains("ine", ignoreCase = true) ||
                m.name.contains("ead", ignoreCase = true) ||
                m.name.contains("eight", ignoreCase = true) ||
                m.name.contains("etric", ignoreCase = true)
            ) {
                println("  $ret ${m.name}($params)")
            }
        }

        println("\n========== 尝试调用各种候选 leading API ==========")
        val candidates = listOf(
            "getLineLeading",
            "getLineExtra",
            "getLeading",
            "getLineSpacingExtra",
            "getSpacingAdd",
            "getSpacingMult"
        )
        candidates.forEach { name ->
            try {
                val m = Layout::class.java.getMethod(name, Int::class.javaPrimitiveType)
                val v = m.invoke(layout, 0)
                println("  ✓ $name(0) = $v")
            } catch (e: NoSuchMethodException) {
                try {
                    val m = Layout::class.java.getMethod(name)
                    val v = m.invoke(layout)
                    println("  ✓ $name() = $v")
                } catch (e2: NoSuchMethodException) {
                    println("  ✗ $name - 不存在")
                }
            }
        }

        println("\n========== getLineTop/getLineBottom/getLineAscent/getLineDescent 实测 ==========")
        for (i in 0 until layout.lineCount) {
            val top = layout.getLineTop(i)
            val bottom = layout.getLineBottom(i)
            val ascent = layout.getLineAscent(i)
            val descent = layout.getLineDescent(i)
            println("  line $i: top=$top bottom=$bottom ascent=$ascent descent=$descent")
            println("          bottom-top=${bottom - top} | descent-ascent=${descent - ascent} | 差值=${(bottom - top) - (descent - ascent)}")
        }
        println("\n========== Paint.fontMetrics ==========")
        val fm = paint.fontMetrics
        println("  ascent=${fm.ascent} descent=${fm.descent} top=${fm.top} bottom=${fm.bottom} leading=${fm.leading}")
        println("  descent-ascent=${fm.descent - fm.ascent} bottom-top=${fm.bottom - fm.top}")
    }
}
