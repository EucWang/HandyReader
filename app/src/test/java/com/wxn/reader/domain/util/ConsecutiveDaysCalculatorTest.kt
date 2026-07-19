package com.wxn.reader.domain.util

import com.wxn.base.util.DateUtil
import com.wxn.reader.domain.model.ReadingActive
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsecutiveDaysCalculatorTest {

    private val minPerDay = 15L * 60 * 1000
    private val today = DateUtil.startOfDay(System.currentTimeMillis())
    private val day = DateUtil.DAY_MS

    private fun act(daysAgo: Int, readingTimeMs: Long) =
        ReadingActive(date = today - daysAgo * day, readingTime = readingTimeMs)

    @Test
    fun `empty list returns 0`() {
        assertEquals(0, ConsecutiveDaysCalculator.calc(emptyList(), minPerDay))
    }

    @Test
    fun `single day meeting threshold returns 1`() {
        assertEquals(1, ConsecutiveDaysCalculator.calc(listOf(act(0, minPerDay)), minPerDay))
    }

    @Test
    fun `5 consecutive days including today returns 5`() {
        val activities = (0..4).map { act(it, minPerDay) }
        assertEquals(5, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }

    @Test
    fun `today insufficient but previous 5 days sufficient returns 5`() {
        val activities = (1..5).map { act(it, minPerDay) } + act(0, minPerDay - 1000)
        assertEquals(5, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }

    @Test
    fun `gap in middle breaks count`() {
        val activities = listOf(
            act(0, minPerDay),
            act(1, minPerDay),
            act(2, minPerDay),
            act(4, minPerDay),
        )
        assertEquals(3, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }

    @Test
    fun `no days meeting threshold returns 0`() {
        val activities = (0..4).map { act(it, 1000) }
        assertEquals(0, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }

    @Test
    fun `multi-device same day aggregates by sum`() {
        val activities = listOf(
            ReadingActive(date = today, readingTime = minPerDay / 2),
            ReadingActive(date = today + 3600_000, readingTime = minPerDay / 2),
            ReadingActive(date = today - day, readingTime = minPerDay),
        )
        assertEquals(2, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }

    @Test
    fun `exactly at threshold passes`() {
        assertEquals(1, ConsecutiveDaysCalculator.calc(listOf(act(0, minPerDay)), minPerDay))
    }

    @Test
    fun `just below threshold fails`() {
        assertEquals(0, ConsecutiveDaysCalculator.calc(listOf(act(0, minPerDay - 1)), minPerDay))
    }

    @Test
    fun `today sufficient but yesterday insufficient returns 1`() {
        val activities = listOf(
            act(0, minPerDay),
            act(1, minPerDay - 1),
        )
        assertEquals(1, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }

    @Test
    fun `extra days beyond 10 do not cause issues`() {
        val activities = (0..14).map { act(it, minPerDay) }
        assertEquals(10, ConsecutiveDaysCalculator.calc(activities, minPerDay))
    }
}
