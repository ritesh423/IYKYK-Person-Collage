package com.ritesh.iykykcollage.video

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSamplingPlanTest {
    @Test
    fun thirtySecondVideo_atFourFps_requestsOneHundredTwentyFrames() {
        val timestamps = FrameSamplingPlan.timestampsUs(
            durationMs = 30_000,
            framesPerSecond = 4,
        )

        assertEquals(120, timestamps.size)
        assertEquals(0L, timestamps.first())
        assertEquals(29_750_000L, timestamps.last())
    }

    @Test
    fun portraitFrame_isDownscaledWithoutChangingAspectRatio() {
        val size = FrameSamplingPlan.scaledSize(
            width = 1080,
            height = 1920,
            targetLongEdgePx = 960,
        )

        assertEquals(540, size.width)
        assertEquals(960, size.height)
    }

    @Test
    fun smallFrame_isNotUpscaled() {
        val size = FrameSamplingPlan.scaledSize(
            width = 360,
            height = 640,
            targetLongEdgePx = 960,
        )

        assertEquals(360, size.width)
        assertEquals(640, size.height)
    }
}
