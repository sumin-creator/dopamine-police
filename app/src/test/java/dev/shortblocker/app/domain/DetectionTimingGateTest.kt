package dev.shortblocker.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetectionTimingGateTest {
    private val packageName = "com.google.android.youtube"

    @Test
    fun waitsUntilConfiguredOverThresholdTime() {
        val gate = DetectionTimingGate(maxCountableGapMillis = 120_000L)
        val requiredMillis = 60_000L

        assertFalse(gate.update(packageName, overThreshold = true, now = 0L, requiredMillis).readyToTrigger)
        assertFalse(gate.update(packageName, overThreshold = true, now = 59_000L, requiredMillis).readyToTrigger)
        assertTrue(gate.update(packageName, overThreshold = true, now = 61_000L, requiredMillis).readyToTrigger)
    }

    @Test
    fun sumsOnlyOverThresholdSegments() {
        val gate = DetectionTimingGate(maxCountableGapMillis = 120_000L)
        val requiredMillis = 60_000L

        gate.update(packageName, overThreshold = true, now = 0L, requiredMillis)
        val firstSegment = gate.update(packageName, overThreshold = true, now = 30_000L, requiredMillis)
        gate.update(packageName, overThreshold = false, now = 40_000L, requiredMillis)
        gate.update(packageName, overThreshold = true, now = 50_000L, requiredMillis)
        val secondSegment = gate.update(packageName, overThreshold = true, now = 80_000L, requiredMillis)

        assertEquals(30_000L, firstSegment.accumulatedMillis)
        assertEquals(60_000L, secondSegment.accumulatedMillis)
        assertTrue(secondSegment.readyToTrigger)
    }

    @Test
    fun packageChangeResetsAccumulatedTime() {
        val gate = DetectionTimingGate(maxCountableGapMillis = 120_000L)
        val requiredMillis = 60_000L

        gate.update(packageName, overThreshold = true, now = 0L, requiredMillis)
        gate.update(packageName, overThreshold = true, now = 45_000L, requiredMillis)
        val changedPackage = gate.update("com.example.maps", overThreshold = true, now = 50_000L, requiredMillis)

        assertEquals(0L, changedPackage.accumulatedMillis)
        assertFalse(changedPackage.readyToTrigger)
    }

    @Test
    fun doesNotTriggerAgainUntilReset() {
        val gate = DetectionTimingGate(maxCountableGapMillis = 120_000L)
        val requiredMillis = 60_000L

        gate.update(packageName, overThreshold = true, now = 0L, requiredMillis)
        assertTrue(gate.update(packageName, overThreshold = true, now = 60_000L, requiredMillis).readyToTrigger)
        assertFalse(gate.update(packageName, overThreshold = true, now = 120_000L, requiredMillis).readyToTrigger)

        gate.reset()
        gate.update(packageName, overThreshold = true, now = 130_000L, requiredMillis)
        assertTrue(gate.update(packageName, overThreshold = true, now = 190_000L, requiredMillis).readyToTrigger)
    }
}
