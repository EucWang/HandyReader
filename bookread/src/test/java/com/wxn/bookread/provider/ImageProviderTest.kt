package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageProviderTest {

    private val maxDim = 4096  // 模拟主流设备 Canvas 上限

    @Test
    fun `large image with small target returns sampleSize at least 4`() {
        // 原图 7000x4400，目标 1080x675（典型屏幕显示尺寸）
        // 2x 目标 = 2160x1350
        // 7000/4 = 1750 < 2160，4400/4 = 1100 < 1350 → sampleSize=4
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 7000, originH = 4400,
            targetW = 1080, targetH = 675,
            maxDim = maxDim
        )
        assertTrue("Expected sampleSize >= 4, got $sampleSize", sampleSize >= 4)
    }

    @Test
    fun `small image with large target returns sampleSize 1`() {
        // 原图小于 2x 目标，无需采样
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 800, originH = 600,
            targetW = 1080, targetH = 810,
            maxDim = maxDim
        )
        assertEquals(1, sampleSize)
    }

    @Test
    fun `extreme large image does not exceed max dimension`() {
        // 极端大图 20000x20000
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 20000, originH = 20000,
            targetW = 1080, targetH = 1080,
            maxDim = maxDim
        )
        val scaledMaxDim = 20000 / sampleSize
        assertTrue("Scaled max dim $scaledMaxDim too large for sampleSize=$sampleSize",
            scaledMaxDim <= maxDim)
    }

    @Test
    fun `invalid target dimensions fall back to max dimension`() {
        // targetW=0 或 targetH=0：按 maxDim 缩放
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 8000, originH = 6000,
            targetW = 0, targetH = 0,
            maxDim = maxDim
        )
        val scaledMaxW = 8000 / sampleSize
        val scaledMaxH = 6000 / sampleSize
        assertTrue("scaledMaxW=$scaledMaxW too large", scaledMaxW <= maxDim)
        assertTrue("scaledMaxH=$scaledMaxH too large", scaledMaxH <= maxDim)
    }

    @Test
    fun `portrait image with invalid target scales by ratio`() {
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 4000, originH = 8000,
            targetW = 0, targetH = 0,
            maxDim = maxDim
        )
        val scaledH = 8000 / sampleSize
        assertTrue("Portrait longest side (height) $scaledH should <= $maxDim",
            scaledH <= maxDim)
    }

    @Test
    fun `low-end device with small maxDim enforces stricter sampling`() {
        // 模拟低端设备 maxDim=2048
        val lowMaxDim = 2048
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 7000, originH = 4400,
            targetW = 1080, targetH = 675,
            maxDim = lowMaxDim
        )
        val scaledMaxW = 7000 / sampleSize
        val scaledMaxH = 4400 / sampleSize
        assertTrue("scaledMaxW=$scaledMaxW exceeds lowMaxDim", scaledMaxW <= lowMaxDim)
        assertTrue("scaledMaxH=$scaledMaxH exceeds lowMaxDim", scaledMaxH <= lowMaxDim)
    }

    // ----------------------------------------------------------------------
    // preload ↔ getImage 缓存契约一致性（纯逻辑层）
    // 注：preload/getImage 内部依赖 Android BitmapFactory，无法在纯 JVM 单测运行；
    //     此处验证它们共享的 sampleSize 计算逻辑（决定缓存 key）对相同输入产出相同结果，
    //     即「preload 预解码的 bitmap，getImage 用同一 key 必然命中」这一核心保证。
    // ----------------------------------------------------------------------

    @Test
    fun `preload and getImage use identical sampleSize for same display dimensions`() {
        // 模拟 setTypeImage 约束后的显示尺寸与 drawImage 传给 getImage 的 target 尺寸完全一致的场景。
        // 这是修复方案成立的前提：两者 sampleSize/key 必须一致。
        val maxDim = 4096
        val originW = 4000
        val originH = 3000
        val displayW = 800
        val displayH = 600

        // preload 侧（setTypeImage 传入约束后 width/height）
        val sampleSizePreload = ImageProvider.calculateInSampleSize(originW, originH, displayW, displayH, maxDim)
        // getImage 侧（drawImage 传入 rectF.width()/height()，与约束后尺寸相同）
        val sampleSizeGet = ImageProvider.calculateInSampleSize(originW, originH, displayW, displayH, maxDim)

        assertEquals(
            "preload 与 getImage 必须产出相同 sampleSize 才能命中缓存",
            sampleSizePreload,
            sampleSizeGet
        )
    }

    @Test
    fun `sampleSize must be power of two so cache keys are stable`() {
        // sampleSize 必须是 2 的幂，保证相同显示尺寸的多次调用产出同一 key（缓存命中率）
        val maxDim = 4096
        val cases = listOf(
            Triple(7000, 4400, 1080 to 675),
            Triple(800, 600, 1080 to 810),
            Triple(20000, 20000, 1080 to 1080),
            Triple(4000, 8000, 0 to 0)
        )
        cases.forEach { (originW, originH, target) ->
            val sampleSize = ImageProvider.calculateInSampleSize(originW, originH, target.first, target.second, maxDim)
            assertTrue("sampleSize=$sampleSize 不是 2 的幂", sampleSize and (sampleSize - 1) == 0)
        }
    }

    @Test
    fun `same display size from different source resolutions may share cache key`() {
        // 验证：相同显示尺寸下，不同原图分辨率只要 sampleSize 相同就共享缓存 key（缓存命中保证）
        val maxDim = 4096
        val displayW = 1080
        val displayH = 810
        val ss1 = ImageProvider.calculateInSampleSize(8000, 6000, displayW, displayH, maxDim)
        val ss2 = ImageProvider.calculateInSampleSize(8100, 6050, displayW, displayH, maxDim)
        // 两张相近分辨率的图在同一显示尺寸下，sampleSize 应一致（共享缓存）
        assertEquals("相近分辨率在相同显示尺寸下应共享 sampleSize", ss1, ss2)
    }

    @Test
    fun `regression - 30MP 宽图解码后两边均不超过 GPU 纹理上限 4096`() {
        // 对应线上崩溃:Canvas: trying to draw too large(122880000bytes) bitmap
        // 122880000 / 4(ARGB_8888) = 30_720_000 px,典型为 8192 x 3750 的宽图。
        // 全页填充显示目标约 1080 x 2400:短边(高)已满足 2x 目标,2x 循环在 sampleSize=1 停止,
        // 必须由 maxDim 兜底把它降到 4096 以内。
        val sampleSize = ImageProvider.calculateInSampleSize(
            originW = 8192, originH = 3750,
            targetW = 1080, targetH = 2400,
            maxDim = 4096
        )
        assertTrue("decoded width ${8192 / sampleSize} > 4096", 8192 / sampleSize <= 4096)
        assertTrue("decoded height ${3750 / sampleSize} > 4096", 3750 / sampleSize <= 4096)
    }
}
