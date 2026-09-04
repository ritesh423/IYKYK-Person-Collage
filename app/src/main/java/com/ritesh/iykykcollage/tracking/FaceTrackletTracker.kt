package com.ritesh.iykykcollage.tracking

import com.ritesh.iykykcollage.face.FaceBounds
import com.ritesh.iykykcollage.face.FaceObservation
import com.ritesh.iykykcollage.face.FrameFaceDetection
import kotlin.math.hypot

data class FaceTrackletPolicy(
    val maximumGapUs: Long = 750_000L,
    val minimumIntersectionOverUnion: Float = 0.12f,
    val maximumCenterDistanceInFaceWidths: Float = 1.25f,
    val minimumSizeRatio: Float = 0.40f,
) {
    init {
        require(maximumGapUs >= 0) { "Maximum gap cannot be negative" }
        require(minimumIntersectionOverUnion in 0f..1f) { "IoU threshold must be between 0 and 1" }
        require(maximumCenterDistanceInFaceWidths > 0) { "Center-distance threshold must be positive" }
        require(minimumSizeRatio in 0f..1f) { "Size ratio must be between 0 and 1" }
    }
}

class FaceTrackletTracker(
    private val policy: FaceTrackletPolicy = FaceTrackletPolicy(),
) {
    private data class MutableTracklet(
        val id: Int,
        val observations: MutableList<FaceObservation>,
    ) {
        val latest: FaceObservation get() = observations.last()

        fun snapshot() = FaceTracklet(
            id = id,
            observations = observations.toList(),
        )
    }

    private data class Association(
        val tracklet: MutableTracklet,
        val observationIndex: Int,
        val score: Float,
    )

    private val activeTracklets = mutableListOf<MutableTracklet>()
    private val completedTracklets = mutableListOf<FaceTracklet>()
    private var nextTrackletId = 1
    private var lastFrameIndex = -1
    private var lastTimestampUs = -1L
    private var sceneBoundaryCount = 0
    private var finished = false

    fun add(
        frame: FrameFaceDetection,
        isSceneTransitionFrame: Boolean = false,
    ) {
        check(!finished) { "Cannot add observations after tracking has finished" }
        require(frame.frameIndex > lastFrameIndex) { "Frames must arrive in increasing index order" }
        require(frame.timestampUs >= lastTimestampUs) { "Frame timestamps must not move backwards" }

        if (isSceneTransitionFrame) {
            completeAllActiveTracklets()
            sceneBoundaryCount += 1
            lastFrameIndex = frame.frameIndex
            lastTimestampUs = frame.timestampUs
            return
        } else {
            completeExpiredTracklets(frame.timestampUs)
        }

        val associations = buildAssociations(frame)
        val assignedTrackletIds = mutableSetOf<Int>()
        val assignedObservationIndexes = mutableSetOf<Int>()

        associations.forEach { association ->
            if (
                association.tracklet.id !in assignedTrackletIds &&
                association.observationIndex !in assignedObservationIndexes
            ) {
                association.tracklet.observations += frame.faces[association.observationIndex]
                assignedTrackletIds += association.tracklet.id
                assignedObservationIndexes += association.observationIndex
            }
        }

        frame.faces.forEachIndexed { index, observation ->
            if (index !in assignedObservationIndexes) {
                activeTracklets += MutableTracklet(
                    id = nextTrackletId++,
                    observations = mutableListOf(observation),
                )
            }
        }

        lastFrameIndex = frame.frameIndex
        lastTimestampUs = frame.timestampUs
    }

    fun finish(): FaceTrackletResult {
        if (!finished) {
            completeAllActiveTracklets()
            finished = true
        }
        return FaceTrackletResult(
            tracklets = completedTracklets.sortedBy { it.id },
            sceneBoundaryCount = sceneBoundaryCount,
        )
    }

    private fun completeExpiredTracklets(timestampUs: Long) {
        val expired = activeTracklets.filter {
            timestampUs - it.latest.timestampUs > policy.maximumGapUs
        }
        expired.forEach { completedTracklets += it.snapshot() }
        activeTracklets.removeAll(expired.toSet())
    }

    private fun completeAllActiveTracklets() {
        completedTracklets += activeTracklets.map { it.snapshot() }
        activeTracklets.clear()
    }

    private fun buildAssociations(frame: FrameFaceDetection): List<Association> {
        return activeTracklets.flatMap { tracklet ->
            frame.faces.mapIndexedNotNull { observationIndex, observation ->
                associationScore(
                    previous = tracklet.latest,
                    current = observation,
                )?.let { score ->
                    Association(
                        tracklet = tracklet,
                        observationIndex = observationIndex,
                        score = score,
                    )
                }
            }
        }.sortedWith(
            compareByDescending<Association> { it.score }
                .thenBy { it.tracklet.id }
                .thenBy { it.observationIndex },
        )
    }

    private fun associationScore(
        previous: FaceObservation,
        current: FaceObservation,
    ): Float? {
        val sameTrackingId = previous.trackingId != null && previous.trackingId == current.trackingId
        val conflictingFreshTrackingIds =
            previous.trackingId != null &&
                current.trackingId != null &&
                previous.trackingId != current.trackingId &&
                current.frameIndex - previous.frameIndex <= 1
        if (conflictingFreshTrackingIds) return null

        val intersectionOverUnion = previous.bounds.intersectionOverUnion(current.bounds)
        val centerDistance = previous.bounds.centerDistanceInFaceWidths(current.bounds)
        val sizeRatio = previous.bounds.sizeRatio(current.bounds)
        val plausibleTrackingIdMatch =
            sameTrackingId && sizeRatio >= 0.25f && centerDistance <= 3f
        val plausibleGeometryMatch =
            sizeRatio >= policy.minimumSizeRatio &&
                (
                    intersectionOverUnion >= policy.minimumIntersectionOverUnion ||
                        centerDistance <= policy.maximumCenterDistanceInFaceWidths
                    )

        if (!plausibleTrackingIdMatch && !plausibleGeometryMatch) return null

        return (if (sameTrackingId) 10f else 0f) +
            intersectionOverUnion * 3f +
            (1f / (1f + centerDistance)) * 2f +
            sizeRatio
    }
}

private fun FaceBounds.intersectionOverUnion(other: FaceBounds): Float {
    val intersectionLeft = maxOf(left, other.left)
    val intersectionTop = maxOf(top, other.top)
    val intersectionRight = minOf(right, other.right)
    val intersectionBottom = minOf(bottom, other.bottom)
    val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0)
    val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0)
    val intersectionArea = intersectionWidth.toLong() * intersectionHeight
    val unionArea = area + other.area - intersectionArea
    return if (unionArea == 0L) 0f else intersectionArea.toFloat() / unionArea
}

private fun FaceBounds.centerDistanceInFaceWidths(other: FaceBounds): Float {
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val otherCenterX = (other.left + other.right) / 2f
    val otherCenterY = (other.top + other.bottom) / 2f
    val averageFaceWidth = (width + other.width) / 2f
    return if (averageFaceWidth == 0f) {
        Float.POSITIVE_INFINITY
    } else {
        hypot(centerX - otherCenterX, centerY - otherCenterY) / averageFaceWidth
    }
}

private fun FaceBounds.sizeRatio(other: FaceBounds): Float {
    val largerArea = maxOf(area, other.area)
    val smallerArea = minOf(area, other.area)
    return if (largerArea == 0L) 0f else smallerArea.toFloat() / largerArea
}
