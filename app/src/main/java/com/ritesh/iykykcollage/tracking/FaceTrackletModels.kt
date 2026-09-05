package com.ritesh.iykykcollage.tracking

import com.ritesh.iykykcollage.face.FaceObservation

data class FaceTracklet(
    val id: Int,
    val observations: List<FaceObservation>,
    val sceneIndex: Int = 0,
) {
    init {
        require(observations.isNotEmpty()) { "A tracklet must contain at least one observation" }
        require(sceneIndex >= 0) { "Scene index cannot be negative" }
    }

    val firstFrameIndex: Int get() = observations.first().frameIndex
    val lastFrameIndex: Int get() = observations.last().frameIndex
    val startTimestampUs: Long get() = observations.first().timestampUs
    val endTimestampUs: Long get() = observations.last().timestampUs
    val matchingCandidateCount: Int get() = observations.count { it.quality.usableForMatching }
    val representativeCandidateCount: Int
        get() = observations.count { it.quality.eligibleAsRepresentative }
    val embeddingCount: Int get() = observations.count { it.embedding != null }
    val sourceTrackingIds: Set<Int>
        get() = observations.mapNotNullTo(linkedSetOf()) { it.trackingId }
}

data class FaceTrackletSummary(
    val totalTracklets: Int,
    val trackletsWithMatchingCandidates: Int,
    val singleFrameTracklets: Int,
    val trackedObservations: Int,
    val sceneBoundaries: Int,
    val longestTrackletObservations: Int,
    val generatedEmbeddings: Int,
    val trackletsWithEmbeddings: Int,
)

data class FaceTrackletResult(
    val tracklets: List<FaceTracklet>,
    val sceneBoundaryCount: Int,
) {
    val summary: FaceTrackletSummary
        get() = FaceTrackletSummary(
            totalTracklets = tracklets.size,
            trackletsWithMatchingCandidates = tracklets.count { it.matchingCandidateCount > 0 },
            singleFrameTracklets = tracklets.count { it.observations.size == 1 },
            trackedObservations = tracklets.sumOf { it.observations.size },
            sceneBoundaries = sceneBoundaryCount,
            longestTrackletObservations = tracklets.maxOfOrNull { it.observations.size } ?: 0,
            generatedEmbeddings = tracklets.sumOf { it.embeddingCount },
            trackletsWithEmbeddings = tracklets.count { it.embeddingCount > 0 },
        )
}
