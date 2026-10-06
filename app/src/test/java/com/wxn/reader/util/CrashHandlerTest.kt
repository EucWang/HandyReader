package com.wxn.reader.util

import com.wxn.reader.data.source.local.CrashPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Robolectric 4.14.1 最高支持 SDK 34；compileSdk=36 需显式锁定（同 bookread 既有做法）
class CrashHandlerTest {

    private lateinit var crashPrefs: CrashPrefs
    private val forwarded = mutableListOf<Throwable>()
    private val fakePrevious = Thread.UncaughtExceptionHandler { _, e -> forwarded.add(e) }

    @Before
    fun setUp() {
        crashPrefs = CrashPrefs(RuntimeEnvironment.getApplication())
        Thread.setDefaultUncaughtExceptionHandler(fakePrevious)
        forwarded.clear()
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @Test
    fun `crash records timestamp then forwards to previous handler`() {
        CrashHandler.install(crashPrefs)
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertNotNull(handler)

        val boom = IllegalStateException("boom")
        handler!!.uncaughtException(Thread.currentThread(), boom)

        assertTrue("崩溃时间戳未记录", crashPrefs.getLastCrashTimestamp() > 0)
        assertEquals("未原样转发给先前 handler", listOf<Throwable>(boom), forwarded)
    }

    @Test
    fun `reinstall does not nest handlers`() {
        CrashHandler.install(crashPrefs)
        val first = Thread.getDefaultUncaughtExceptionHandler()

        CrashHandler.install(crashPrefs)

        assertSame("重复 install 产生了嵌套链", first, Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun `install is skipped when no default handler is present`() {
        Thread.setDefaultUncaughtExceptionHandler(null)

        CrashHandler.install(crashPrefs)

        assertNull("裸 JVM（default handler 为 null）不应被劫持", Thread.getDefaultUncaughtExceptionHandler())
    }
}
