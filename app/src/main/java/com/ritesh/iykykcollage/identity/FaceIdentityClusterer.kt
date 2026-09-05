package com.ritesh.iykykcollage.identity

import com.ritesh.iykykcollage.face.FaceEmbedding
import com.ritesh.iykykcollage.tracking.FaceTracklet

data class IdentityClusteringPolicy(
    val minimumCosineSimilarity: Float = 0.80f,
    val minimumRecoverySimilarity: Float = 0.30f,
    val minimumConfirmedObservations: Int = 2,
) {
    init {
        require(minimumCosineSimilarity in -1f..1f) {
            "Cosine-similarity threshold must be between -1 and 1"
        }
        require(minimumRecoverySimilarity in -1f..minimumCosineSimilarity) {
            "Recovery threshold must not exceed the main similarity threshold"
        }
        require(minimumConfirmedObservations > 0) {
            "An identity must require at least one observation"
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

        recoverSimultaneousOutliers(identities, candidates)
        recoverPersistentOutliers(identities)

        val transientOutliers = identities.filter { identity ->
            identity.candidates.size == 1 &&
                identity.candidates.single().tracklet.observations.size <
                policy.minimumConfirmedObservations
        }
        identities.removeAll(transientOutliers.toSet())

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
        val unassignedTracklets = (
            tracklets.filter { it.embeddingCount == 0 } +
                transientOutliers.flatMap { identity ->
                    identity.candidates.map { it.tracklet }
                }
            )
            .distinctBy { it.id }
            .sortedBy { it.startTimestampUs }
        return IdentityClusteringResult(
            identities = orderedIdentities,
            unassignedTracklets = unassignedTracklets,
            similarityThreshold = policy.minimumCosineSimilarity,
        )
    }

    private fun recoverSimultaneousOutliers(
        identities: MutableList<MutableIdentity>,
        allCandidates: List<Candidate>,
    ) {
        val sceneSizes = allCandidates.groupingBy { it.tracklet.sceneIndex }.eachCount()
        val stableIdentities = identities.filter { it.candidates.size >= 2 }.toMutableList()
        if (stableIdentities.isEmpty()) return

        val outliersByScene = identities
            .filter { identity ->
                identity.candidates.size == 1 &&
                    sceneSizes.getValue(identity.candidates.single().tracklet.sceneIndex) > 1
            }
            .groupBy { it.candidates.single().tracklet.sceneIndex }
            .toSortedMap()

        outliersByScene.values.forEach { outlierIdentities ->
            val remaining = outlierIdentities.map { it.candidates.single() }.toMutableList()
            val available = stableIdentities.toMutableList()
            while (remaining.isNotEmpty() && available.isNotEmpty()) {
                val smallestIdentitySize = available.minOf { it.candidates.size }
                val preferred = available.filter { it.candidates.size == smallestIdentitySize }
                val bestMatch = remaining
                    .flatMap { candidate ->
                        preferred
                            .filter { it.canAccept(candidate.tracklet) }
                            .map { identity ->
                                Triple(
                                    candidate,
                                    identity,
                                    identity.centroid.cosineSimilarity(candidate.centroid),
                                )
                            }
                    }
                    .maxByOrNull { (_, _, similarity) -> similarity }
                    ?: break
                if (bestMatch.third < policy.minimumRecoverySimilarity) break

                val candidate = bestMatch.first
                val identity = bestMatch.second
                identity.candidates += candidate
                identities.removeAll {
                    it.candidates.size == 1 && it.candidates.single() === candidate
                }
                remaining.remove(candidate)
                available.remove(identity)
            }
        }
    }

    private fun recoverPersistentOutliers(identities: MutableList<MutableIdentity>) {
        val stableIdentities = identities.filter { it.candidates.size >= 2 }
        if (stableIdentities.isEmpty()) return

        identities
            .filter { identity ->
                identity.candidates.size == 1 &&
                    identity.candidates.single().tracklet.observations.size >=
                    policy.minimumConfirmedObservations &&
                    identity.candidates.single().tracklet.observations.any {
                        it.fromSideBySideLayout
                    }
            }
            .sortedBy { it.candidates.single().tracklet.startTimestampUs }
            .forEach { outlier ->
                val candidate = outlier.candidates.single()
                val bestMatch = stableIdentities
                    .asSequence()
                    .filter { it.canAccept(candidate.tracklet) }
                    .map { identity ->
                        identity to identity.centroid.cosineSimilarity(candidate.centroid)
                    }
                    .maxByOrNull { (_, similarity) -> similarity }

                if (bestMatch != null && bestMatch.second >= policy.minimumRecoverySimilarity) {
                    bestMatch.first.candidates += candidate
                    identities.remove(outlier)
                }
            }
    }
}

private fun FaceTracklet.embeddingCentroid(): FaceEmbedding? {
    val embeddings = observations.mapNotNull { it.embedding }
    return if (embeddings.isEmpty()) null else FaceEmbedding.meanOf(embeddings)
}

private fun FaceTracklet.overlaps(other: FaceTracklet): Boolean =
    startTimestampUs <= other.endTimestampUs && other.startTimestampUs <= endTimestampUs
