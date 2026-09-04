package com.ritesh.iykykcollage.tracking

import com.ritesh.iykykcollage.face.FaceBounds
import com.ritesh.iykykcollage.face.FaceObservation
import com.ritesh.iykykcollage.face.FaceQualityAssessment
import com.ritesh.iykykcollage.face.FrameFaceDetection
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceTrackletTrackerTest {
    @Test
    fun consecutiveDetectionsWithSameTrackingId_formOneTracklet() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = 7, left = 100)))
        tracker.add(frame(1, face(frame = 1, trackingId = 7, left = 110)))

        val result = tracker.finish()
        assertEquals(1, result.summary.totalTracklets)
        assertEquals(2, result.tracklets.single().observations.size)
        assertEquals(setOf(7), result.tracklets.single().sourceTrackingIds)
    }

    @Test
    fun twoFacesInOneFrame_createTwoIndependentTracklets() {
        val tracker = FaceTrackletTracker()

        tracker.add(
            frame(
                0,
                face(frame = 0, trackingId = 1, left = 50),
                face(frame = 0, trackingId = 2, left = 300),
            ),
        )
        tracker.add(
            frame(
                1,
                face(frame = 1, trackingId = 1, left = 60),
                face(frame = 1, trackingId = 2, left = 290),
            ),
        )

        val result = tracker.finish()
        assertEquals(2, result.summary.totalTracklets)
        assertEquals(listOf(2, 2), result.tracklets.map { it.observations.size })
    }

    @Test
    fun shortDetectorMiss_isBridged() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = 3, left = 100)))
        tracker.add(frame(1))
        tracker.add(frame(2, face(frame = 2, trackingId = 3, left = 120)))

        assertEquals(1, tracker.finish().summary.totalTracklets)
    }

    @Test
    fun gapBeyondPolicy_startsNewTracklet() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = 3, left = 100)))
        tracker.add(frame(1))
        tracker.add(frame(2))
        tracker.add(frame(3))
        tracker.add(frame(4, face(frame = 4, trackingId = 3, left = 100)))

        assertEquals(2, tracker.finish().summary.totalTracklets)
    }

    @Test
    fun transitionFrame_closesOldTrackletAndDoesNotSeedNewOne() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = 9, left = 100)))
        tracker.add(
            frame(1, face(frame = 1, trackingId = 9, left = 100)),
            isSceneTransitionFrame = true,
        )
        tracker.add(frame(2, face(frame = 2, trackingId = 9, left = 100)))

        val result = tracker.finish()
        assertEquals(2, result.summary.totalTracklets)
        assertEquals(1, result.summary.sceneBoundaries)
        assertEquals(2, result.summary.trackedObservations)
    }

    @Test
    fun changedTrackingIdAfterMiss_canFallBackToGeometry() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = 4, left = 100)))
        tracker.add(frame(1))
        tracker.add(frame(2, face(frame = 2, trackingId = 12, left = 115)))

        val tracklet = tracker.finish().tracklets.single()
        assertEquals(setOf(4, 12), tracklet.sourceTrackingIds)
    }

    @Test
    fun conflictingFreshTrackingIds_areKeptSeparate() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = 4, left = 100)))
        tracker.add(frame(1, face(frame = 1, trackingId = 12, left = 105)))

        assertEquals(2, tracker.finish().summary.totalTracklets)
    }

    @Test
    fun implausibleMovementWithoutTrackingId_startsAnotherTracklet() {
        val tracker = FaceTrackletTracker()

        tracker.add(frame(0, face(frame = 0, trackingId = null, left = 20)))
        tracker.add(frame(1, face(frame = 1, trackingId = null, left = 400)))

        assertEquals(2, tracker.finish().summary.totalTracklets)
    }

    @Test
    fun observationsCanOnlyBeAssignedOnce() {
        val tracker = FaceTrackletTracker()

        tracker.add(
            frame(
                0,
                face(frame = 0, trackingId = null, left = 80),
                face(frame = 0, trackingId = null, left = 140),
            ),
        )
        tracker.add(frame(1, face(frame = 1, trackingId = null, left = 110)))

        val result = tracker.finish()
        assertEquals(2, result.summary.totalTracklets)
        assertEquals(3, result.summary.trackedObservations)
        assertEquals(listOf(1, 2), result.tracklets.map { it.observations.size }.sorted())
    }

    @Test
    fun singleFrameTrack_isRetainedAndReportedForLaterValidation() {
        val tracker = FaceTrackletTracker()
        tracker.add(frame(0, face(frame = 0, trackingId = 4, left = 100)))

        val result = tracker.finish()

        assertEquals(1, result.summary.totalTracklets)
        assertEquals(1, result.summary.singleFrameTracklets)
    }

    private fun frame(index: Int, vararg faces: FaceObservation) = FrameFaceDetection(
        frameIndex = index,
        timestampUs = index * 250_000L,
        faces = faces.toList(),
    )

    private fun face(
        frame: Int,
        trackingId: Int?,
        left: Int,
    ) = FaceObservation(
        frameIndex = frame,
        timestampUs = frame * 250_000L,
        frameWidth = 540,
        frameHeight = 960,
        bounds = FaceBounds(left = left, top = 200, right = left + 120, bottom = 350),
        trackingId = trackingId,
        headEulerAngleX = 0f,
        headEulerAngleY = 0f,
        headEulerAngleZ = 0f,
        leftEyeOpenProbability = 0.9f,
        rightEyeOpenProbability = 0.9f,
        smilingProbability = 0.5f,
        quality = FaceQualityAssessment(
            usableForMatching = true,
            eligibleAsRepresentative = true,
            issues = emptySet(),
        ),
    )
}
