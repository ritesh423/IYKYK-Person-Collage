package com.ritesh.iykykcollage.identity

import com.ritesh.iykykcollage.face.FaceEmbedding
import com.ritesh.iykykcollage.tracking.FaceTracklet

data class FaceIdentity(
    val id: Int,
    val tracklets: List<FaceTracklet>,
    val centroid: FaceEmbedding,
) {
    init {
        require(tracklets.isNotEmpty()) { "An identity must contain at least one tracklet" }
    }

    val firstTimestampUs: Long get() = tracklets.minOf { it.startTimestampUs }
}

data class IdentityClusteringResult(
    val identities: List<FaceIdentity>,
    val trackletsWithoutEmbeddings: List<FaceTracklet>,
    val similarityThreshold: Float,
) {
    val identifiedTrackletCount: Int get() = identities.sumOf { it.tracklets.size }
}
