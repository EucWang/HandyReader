package com.wxn.base

import com.wxn.base.util.BreakSentenceUtil
import org.junit.Test

import org.junit.Assert.*
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testBreakSentenceUtil() {
        val string = "This ebook is for the use of anyone anywhere in the United States and most other parts of the world at no cost and with almost no restrictions whatsoever. You may copy it, git it away or re-use it under the terms of the Project Gutenberg License included with this ebook or online at www.gutenberg.org. If you are not located in the United States, you will have to check the laws of the country where you are located before using the eBook.";
        val ret = BreakSentenceUtil.breakSentence(string, Locale.ENGLISH)

        for (item in ret) {
            println("start=${item.second}, end=${item.third}, text=${item.first}")
        }
    }
}
