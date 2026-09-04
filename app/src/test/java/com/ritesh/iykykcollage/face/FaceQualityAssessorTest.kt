package com.ritesh.iykykcollage.face

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceQualityAssessorTest {
    private val assessor = FaceQualityAssessor()

    @Test
    fun largeFrontalFaceWithOpenEyes_isUsableAndRepresentativeEligible() {
        val quality = assess(
            bounds = FaceBounds(left = 150, top = 200, right = 390, bottom = 500),
        )

        assertTrue(quality.usableForMatching)
        assertTrue(quality.eligibleAsRepresentative)
        assertTrue(quality.issues.isEmpty())
    }

    @Test
    fun smallFace_isRejectedForMatching() {
        val quality = assess(
            bounds = FaceBounds(left = 200, top = 300, right = 260, bottom = 370),
        )

        assertFalse(quality.usableForMatching)
        assertFalse(quality.eligibleAsRepresentative)
        assertTrue(FaceQualityIssue.TOO_SMALL_FOR_MATCHING in quality.issues)
    }

    @Test
    fun faceAtFrameEdge_canMatchButCannotRepresentPerson() {
        val quality = assess(
            bounds = FaceBounds(left = 0, top = 200, right = 240, bottom = 500),
        )

        assertTrue(quality.usableForMatching)
        assertFalse(quality.eligibleAsRepresentative)
        assertTrue(FaceQualityIssue.CLOSE_TO_FRAME_EDGE in quality.issues)
    }

    @Test
    fun closedEyeFace_isNotRepresentativeEligible() {
        val quality = assess(
            bounds = FaceBounds(left = 150, top = 200, right = 390, bottom = 500),
            leftEyeOpenProbability = 0.2f,
        )

        assertTrue(quality.usableForMatching)
        assertFalse(quality.eligibleAsRepresentative)
        assertTrue(FaceQualityIssue.EYES_NOT_CONFIDENTLY_OPEN in quality.issues)
    }

    @Test
    fun extremeYaw_isRejectedForMatching() {
        val quality = assess(
            bounds = FaceBounds(left = 150, top = 200, right = 390, bottom = 500),
            headEulerAngleY = 52f,
        )

        assertFalse(quality.usableForMatching)
        assertTrue(FaceQualityIssue.EXTREME_POSE in quality.issues)
    }

    private fun assess(
        bounds: FaceBounds,
        headEulerAngleX: Float = 0f,
        headEulerAngleY: Float = 0f,
        headEulerAngleZ: Float = 0f,
        leftEyeOpenProbability: Float? = 0.9f,
        rightEyeOpenProbability: Float? = 0.9f,
    ): FaceQualityAssessment = assessor.assess(
        bounds = bounds,
        frameWidth = 540,
        frameHeight = 960,
        headEulerAngleX = headEulerAngleX,
        headEulerAngleY = headEulerAngleY,
        headEulerAngleZ = headEulerAngleZ,
        leftEyeOpenProbability = leftEyeOpenProbability,
        rightEyeOpenProbability = rightEyeOpenProbability,
    )
}
