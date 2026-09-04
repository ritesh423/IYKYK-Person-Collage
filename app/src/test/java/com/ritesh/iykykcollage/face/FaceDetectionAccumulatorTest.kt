package com.ritesh.iykykcollage.face

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceDetectionAccumulatorTest {
    @Test
    fun summary_distinguishesFramesObservationsAndQualityCandidates() {
        val accumulator = FaceDetectionAccumulator()
        accumulator.add(frame(index = 0, faces = emptyList()))
        accumulator.add(
            frame(
                index = 1,
                faces = listOf(
                    observation(index = 1, usable = true, representative = true),
                    observation(index = 1, usable = true, representative = false),
                ),
            ),
        )
        accumulator.add(
            frame(
                index = 2,
                faces = listOf(observation(index = 2, usable = false, representative = false)),
            ),
        )

        val summary = accumulator.snapshot()

        assertEquals(3, summary.analyzedFrames)
        assertEquals(2, summary.framesWithFaces)
        assertEquals(3, summary.totalFaceObservations)
        assertEquals(2, summary.matchingCandidates)
        assertEquals(1, summary.representativeCandidates)
        assertEquals(2, summary.maxFacesInOneFrame)
    }

    private fun frame(index: Int, faces: List<FaceObservation>) = FrameFaceDetection(
        frameIndex = index,
        timestampUs = index * 250_000L,
        faces = faces,
    )

    private fun observation(
        index: Int,
        usable: Boolean,
        representative: Boolean,
    ) = FaceObservation(
        frameIndex = index,
        timestampUs = index * 250_000L,
        frameWidth = 540,
        frameHeight = 960,
        bounds = FaceBounds(150, 200, 390, 500),
        trackingId = null,
        headEulerAngleX = 0f,
        headEulerAngleY = 0f,
        headEulerAngleZ = 0f,
        leftEyeOpenProbability = 0.9f,
        rightEyeOpenProbability = 0.9f,
        smilingProbability = 0.5f,
        quality = FaceQualityAssessment(
            usableForMatching = usable,
            eligibleAsRepresentative = representative,
            issues = emptySet(),
        ),
    )
}
