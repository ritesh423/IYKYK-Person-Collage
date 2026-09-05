package com.ritesh.iykykcollage.identity

import com.ritesh.iykykcollage.face.FaceEmbedding
import com.ritesh.iykykcollage.tracking.FaceTracklet

data class IdentityClusteringPolicy(
    val minimumCosineSimilarity: Float = 0.60f,
) {
    init {
        require(minimumCosineSimilarity in -1f..1f) {
            "Cosine-similarity threshold must be between -1 and 1"
        }
    }
}

class FaceIdentityClusterer(
    private val policy: IdentityClusteringPolicy = IdentityClusteringPolicy(),
) {
    private data class Candidate(
        val tracklet: FaceTracklet,
        val centroid: FaceEmbedding,
    )

    private data class MutableIdentity(
        val candidates: MutableList<Candidate>,
    ) {
        val centroid: FaceEmbedding
            get() = FaceEmbedding.meanOf(candidates.map { it.centroid })

        fun canAccept(tracklet: FaceTracklet): Boolean = candidates.none { candidate ->
            candidate.tracklet.overlaps(tracklet)
        }
    }

    fun cluster(tracklets: List<FaceTracklet>): IdentityClusteringResult {
        val candidates = tracklets.mapNotNull { tracklet ->
            tracklet.embeddingCentroid()?.let { Candidate(tracklet, it) }
        }
        val identities = mutableListOf<MutableIdentity>()

        candidates
            .sortedWith(
                compareByDescending<Candidate> { it.tracklet.embeddingCount }
                    .thenByDescending { it.tracklet.observations.size }
                    .thenBy { it.tracklet.startTimestampUs },
            )
            .forEach { candidate ->
                val bestIdentity = identities
                    .asSequence()
                    .filter { it.canAccept(candidate.tracklet) }
                    .map { identity -> identity to identity.centroid.cosineSimilarity(candidate.centroid) }
                    .filter { (_, similarity) -> similarity >= policy.minimumCosineSimilarity }
                    .maxByOrNull { (_, similarity) -> similarity }
                    ?.first

                if (bestIdentity == null) {
                    identities += MutableIdentity(mutableListOf(candidate))
                } else {
                    bestIdentity.candidates += candidate
                }
            }

        val orderedIdentities = identities
            .sortedBy { identity -> identity.candidates.minOf { it.tracklet.startTimestampUs } }
            .mapIndexed { index, identity ->
                FaceIdentity(
                    id = index + 1,
                    tracklets = identity.candidates
                        .map { it.tracklet }
                        .sortedBy { it.startTimestampUs },
                    centroid = identity.centroid,
                )
            }

        return IdentityClusteringResult(
            identities = orderedIdentities,
            trackletsWithoutEmbeddings = tracklets
                .filter { it.embeddingCount == 0 }
                .sortedBy { it.startTimestampUs },
            similarityThreshold = policy.minimumCosineSimilarity,
        )
    }
}

private fun FaceTracklet.embeddingCentroid(): FaceEmbedding? {
    val embeddings = observations.mapNotNull { it.embedding }
    return if (embeddings.isEmpty()) null else FaceEmbedding.meanOf(embeddings)
}

private fun FaceTracklet.overlaps(other: FaceTracklet): Boolean =
    startTimestampUs <= other.endTimestampUs && other.startTimestampUs <= endTimestampUs
