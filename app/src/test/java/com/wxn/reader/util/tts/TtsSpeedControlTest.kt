package com.wxn.reader.util.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.round

class TtsSpeedControlTest {

    private fun stepDown(speed: Float): Float {
        val rounded = (round(speed * 100) / 100f)
        return (round((rounded - 0.01f) * 100) / 100f)
            .coerceIn(TtsNavigator.TTS_MIN_SPEED, TtsNavigator.TTS_MAX_SPEED)
    }

    private fun stepUp(speed: Float): Float {
        val rounded = (round(speed * 100) / 100f)
        return (round((rounded + 0.01f) * 100) / 100f)
            .coerceIn(TtsNavigator.TTS_MIN_SPEED, TtsNavigator.TTS_MAX_SPEED)
    }

    private fun isPresetSelected(currentSpeed: Float, preset: Float): Boolean {
        return abs(currentSpeed - preset) < 0.005f
    }

    @Test
    fun testSpeedLimits() {
        assertEquals(0.25f, TtsNavigator.TTS_MIN_SPEED, 0.0001f)
        assertEquals(3.0f, TtsNavigator.TTS_MAX_SPEED, 0.0001f)
    }

    @Test
    fun testPresetValuesInRange() {
        val presets = listOf(0.8f, 0.9f, 1.0f, 1.2f)
        for (preset in presets) {
            assertTrue(preset >= TtsNavigator.TTS_MIN_SPEED)
            assertTrue(preset <= TtsNavigator.TTS_MAX_SPEED)
        }
    }

    @Test
    fun testPresetSelectionMatching() {
        assertTrue(isPresetSelected(0.8f, 0.8f))
        assertTrue(isPresetSelected(0.801f, 0.8f))
        assertFalse(isPresetSelected(0.81f, 0.8f))
        assertFalse(isPresetSelected(0.79f, 0.8f))

        assertTrue(isPresetSelected(0.9f, 0.9f))
        assertTrue(isPresetSelected(1.0f, 1.0f))
        assertTrue(isPresetSelected(1.2f, 1.2f))
    }

    @Test
    fun testFineTuningStepDown() {
        assertEquals(0.79f, stepDown(0.80f), 0.0001f)
        assertEquals(0.89f, stepDown(0.90f), 0.0001f)
        assertEquals(0.99f, stepDown(1.00f), 0.0001f)
        assertEquals(1.19f, stepDown(1.20f), 0.0001f)
    }

    @Test
    fun testFineTuningStepUp() {
        assertEquals(0.81f, stepUp(0.80f), 0.0001f)
        assertEquals(0.91f, stepUp(0.90f), 0.0001f)
        assertEquals(1.01f, stepUp(1.00f), 0.0001f)
        assertEquals(1.21f, stepUp(1.20f), 0.0001f)
    }

    @Test
    fun testFineTuningLowerBoundClamping() {
        assertEquals(0.25f, stepDown(0.25f), 0.0001f)
        assertEquals(0.25f, stepDown(0.254f), 0.0001f)
    }

    @Test
    fun testFineTuningUpperBoundClamping() {
        assertEquals(3.0f, stepUp(3.0f), 0.0001f)
        assertEquals(3.0f, stepUp(2.999f), 0.0001f)
    }

    @Test
    fun testFloatRoundingPrecision() {
        // Test successive 0.01 increments do not accumulate float drift
        var current = 0.80f
        for (i in 1..20) {
            current = stepUp(current)
        }
        assertEquals(1.00f, current, 0.0001f)

        for (i in 1..20) {
            current = stepDown(current)
        }
        assertEquals(0.80f, current, 0.0001f)
    }
}
