package com.wxn.bookread.provider

import com.wxn.base.bean.TextTag
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [spacing-sym] tr 行索引解析（TableLayoutProvider.tableRowIndex）JVM 直测。
 * TextTag/paramsPairs 为纯字符串解析，TableLayoutProvider object 无 Android 依赖初始化，
 * 与 TableGeometryTest 同款 JVM 约束。该返回值决定「表格首行是否获得段前间距」。
 */
class TableLayoutProviderRowIndexTest {

    private fun trTag(params: String) = TextTag(uuid = "tr", name = "tr", params = params)

    @Test fun `index 正常解析`() =
        assertEquals(2, TableLayoutProvider.tableRowIndex(trTag("index=2")))

    @Test fun `tr 缺失按 0`() =
        assertEquals(0, TableLayoutProvider.tableRowIndex(null))

    @Test fun `index 非法按 0`() =
        assertEquals(0, TableLayoutProvider.tableRowIndex(trTag("index=abc")))

    @Test fun `混合参数仍可解析`() =
        assertEquals(1, TableLayoutProvider.tableRowIndex(trTag("dir=rtl&index=1")))

    @Test fun `index 缺失按 0`() =
        assertEquals(0, TableLayoutProvider.tableRowIndex(trTag("dir=rtl")))
}
