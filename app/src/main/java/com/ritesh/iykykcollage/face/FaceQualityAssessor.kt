package com.ritesh.iykykcollage.face

import kotlin.math.abs

data class FaceQualityPolicy(
    val minimumMatchingEdgePx: Int = 80,
    val minimumRepresentativeEdgePx: Int = 120,
    val minimumVisibleFraction: Float = 0.85f,
    val frameEdgeMarginFraction: Float = 0.01f,
    val maximumMatchingPitchDegrees: Float = 35f,
    val maximumMatchingYawDegrees: Float = 45f,
    val maximumMatchingRollDegrees: Float = 40f,
    val maximumRepresentativePitchDegrees: Float = 20f,
    val maximumRepresentativeYawDegrees: Float = 18f,
    val maximumRepresentativeRollDegrees: Float = 20f,
    val minimumOpenEyeProbability: Float = 0.55f,
)

class FaceQualityAssessor(
    private val policy: FaceQualityPolicy = FaceQualityPolicy(),
) {
    fun assess(
        bounds: FaceBounds,
        frameWidth: Int,
        frameHeight: Int,
        headEulerAngleX: Float,
        headEulerAngleY: Float,
        headEulerAngleZ: Float,
        leftEyeOpenProbability: Float?,
        rightEyeOpenProbability: Float?,
    ): FaceQualityAssessment {
        require(frameWidth > 0 && frameHeight > 0) { "Frame dimensions must be positive" }

        val issues = linkedSetOf<FaceQualityIssue>()
        val shortestFaceEdge = minOf(bounds.width, bounds.height)
        val visibleFraction = visibleFraction(bounds, frameWidth, frameHeight)
        val matchingPose =
            abs(headEulerAngleX) <= policy.maximumMatchingPitchDegrees &&
                abs(headEulerAngleY) <= policy.maximumMatchingYawDegrees &&
                abs(headEulerAngleZ) <= policy.maximumMatchingRollDegrees

        if (shortestFaceEdge < policy.minimumMatchingEdgePx) {
            issues += FaceQualityIssue.TOO_SMALL_FOR_MATCHING
        }
        if (shortestFaceEdge < policy.minimumRepresentativeEdgePx) {
            issues += FaceQualityIssue.TOO_SMALL_FOR_REPRESENTATIVE
        }
        if (visibleFraction < policy.minimumVisibleFraction) {
            issues += FaceQualityIssue.PARTIALLY_OUTSIDE_FRAME
        }
        if (!matchingPose) {
            issues += FaceQualityIssue.EXTREME_POSE
        }

        val usableForMatching =
            shortestFaceEdge >= policy.minimumMatchingEdgePx &&
                visibleFraction >= policy.minimumVisibleFraction &&
                matchingPose

        val safelyInsideFrame = isSafelyInsideFrame(bounds, frameWidth, frameHeight)
        if (!safelyInsideFrame) {
            issues += FaceQualityIssue.CLOSE_TO_FRAME_EDGE
        }

        val frontal =
            abs(headEulerAngleX) <= policy.maximumRepresentativePitchDegrees &&
                abs(headEulerAngleY) <= policy.maximumRepresentativeYawDegrees &&
                abs(headEulerAngleZ) <= policy.maximumRepresentativeRollDegrees
        if (!frontal) {
            issues += FaceQualityIssue.NOT_FRONTAL
        }

        val eyesConfidentlyOpen =
            leftEyeOpenProbability != null &&
                rightEyeOpenProbability != null &&
                leftEyeOpenProbability >= policy.minimumOpenEyeProbability &&
                rightEyeOpenProbability >= policy.minimumOpenEyeProbability
        if (!eyesConfidentlyOpen) {
            issues += FaceQualityIssue.EYES_NOT_CONFIDENTLY_OPEN
        }

        val eligibleAsRepresentative =
            usableForMatching &&
                shortestFaceEdge >= policy.minimumRepresentativeEdgePx &&
                safelyInsideFrame &&
                frontal &&
                eyesConfidentlyOpen

        return FaceQualityAssessment(
            usableForMatching = usableForMatching,
            eligibleAsRepresentative = eligibleAsRepresentative,
            issues = issues,
        )
    }

    private fun visibleFraction(bounds: FaceBounds, frameWidth: Int, frameHeight: Int): Float {
        if (bounds.area == 0L) return 0f

        val visibleLeft = bounds.left.coerceIn(0, frameWidth)
        val visibleTop = bounds.top.coerceIn(0, frameHeight)
        val visibleRight = bounds.right.coerceIn(0, frameWidth)
        val visibleBottom = bounds.bottom.coerceIn(0, frameHeight)
        val visibleWidth = (visibleRight - visibleLeft).coerceAtLeast(0)
        val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0)
        val visibleArea = visibleWidth.toLong() * visibleHeight

        return visibleArea.toFloat() / bounds.area
    }

    private fun isSafelyInsideFrame(
        bounds: FaceBounds,
        frameWidth: Int,
        frameHeight: Int,
    ): Boolean {
        val horizontalMargin = frameWidth * policy.frameEdgeMarginFraction
        val verticalMargin = frameHeight * policy.frameEdgeMarginFraction
        return bounds.left >= horizontalMargin &&
            bounds.top >= verticalMargin &&
            bounds.right <= frameWidth - horizontalMargin &&
            bounds.bottom <= frameHeight - verticalMargin
    }
}
