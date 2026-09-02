package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 表格列几何镜像数值验算（方案 §5.2 / 任务 A1.1）。
 * 公式来源：develop_rtl 批次 E（含竖线哨兵修复 BUG-2），LTR 分支与 legacy
 * setTextTable 的 fullWidth×percent + tableCellInnerPadding 公式逐字等价。
 */
class TableGeometryTest {
    private val d = 0.01f

    @Test fun `LTR 首列偏移`() = assertEquals(10f, TableGeometry.cellLeftOffset(400f, 0, 50, false), d)
    @Test fun `LTR 次列偏移`() = assertEquals(210f, TableGeometry.cellLeftOffset(400f, 50, 50, false), d)
    @Test fun `RTL 首列贴右`() = assertEquals(210f, TableGeometry.cellLeftOffset(400f, 0, 50, true), d)
    @Test fun `RTL 次列贴左`() = assertEquals(10f, TableGeometry.cellLeftOffset(400f, 50, 50, true), d)
    @Test fun `LTR 竖线`() {
        assertEquals(0f, TableGeometry.verticalBorderX(400f, 0f, false), d)
        assertEquals(200f, TableGeometry.verticalBorderX(400f, 50f, false), d)
    }
    @Test fun `RTL 竖线镜像`() {
        assertEquals(200f, TableGeometry.verticalBorderX(400f, 50f, true), d)      // 两列对称
        assertEquals(266.68f, TableGeometry.verticalBorderX(400f, 33.33f, true), d) // 三列内部线
    }
    @Test fun `RTL 哨兵不镜像`() = assertEquals(0f, TableGeometry.verticalBorderX(400f, 0f, true), d)

    @Test fun `畸形书 percents 超 100 的 RTL 末列负偏移-现状存档`() {
        // leftOffsetPercent+tagPercent=120 > 100 → 负偏移（内容区越出左缘）。
        // 现状不钳制（畸形书容错为记录在案的后续任务：usableWidth 已有 coerceAtLeast(1)，
        // 左偏移无对应下限）；本用例固化现状数值，容错改动时应同步更新。
        assertEquals(-70f, TableGeometry.cellLeftOffset(400f, 80, 40, true), d)
    }
}
