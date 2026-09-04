package com.ritesh.iykykcollage.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneBoundaryDeciderTest {
    private val dark = intArrayOf(0xff101010.toInt(), 0xff202020.toInt())
    private val similar = intArrayOf(0xff111111.toInt(), 0xff212121.toInt())
    private val bright = intArrayOf(0xfff0f0f0.toInt(), 0xffe0e0e0.toInt())

    @Test
    fun firstAndSimilarFrames_doNotCreateBoundary() {
        val decider = SceneBoundaryDecider()

        assertFalse(decider.observe(dark).isSceneBoundary)
        val change = decider.observe(similar)

        assertFalse(change.isSceneBoundary)
        assertTrue(change.differenceScore < 0.14f)
    }

    @Test
    fun largeVisualDifference_createsBoundary() {
        val decider = SceneBoundaryDecider()
        decider.observe(dark)

        val change = decider.observe(bright)

        assertTrue(change.isSceneBoundary)
        assertTrue(change.differenceScore >= 0.14f)
    }

    @Test
    fun cooldownSuppressesDuplicateBoundaryDuringTransition() {
        val decider = SceneBoundaryDecider(
            SceneBoundaryPolicy(minimumFramesBetweenBoundaries = 3),
        )
        decider.observe(dark)
        assertTrue(decider.observe(bright).isSceneBoundary)

        assertFalse(decider.observe(dark).isSceneBoundary)
        assertFalse(decider.observe(bright).isSceneBoundary)
        assertTrue(decider.observe(dark).isSceneBoundary)
    }

    @Test
    fun reset_forgetsPreviousVideo() {
        val decider = SceneBoundaryDecider()
        decider.observe(dark)
        assertTrue(decider.observe(bright).isSceneBoundary)

        decider.reset()

        val firstAfterReset = decider.observe(dark)
        assertFalse(firstAfterReset.isSceneBoundary)
        assertEquals(0f, firstAfterReset.differenceScore)
    }
}
