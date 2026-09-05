package com.ritesh.iykykcollage.identity

import com.ritesh.iykykcollage.tracking.FaceTracklet

data class AppearanceCountingPolicy(
    val maximumFragmentGapUs: Long = 1_000_000L,
) {
    init {
        require(maximumFragmentGapUs >= 0L) { "Fragment gap cannot be negative" }
    }
}

class FaceAppearanceCounter(
    private val policy: AppearanceCountingPolicy = AppearanceCountingPolicy(),
) {
    fun count(clustering: IdentityClusteringResult): AppearanceCountingResult {
        val people = clustering.identities.map { identity ->
            IdentifiedPerson(
                identity = identity,
                appearances = identity.tracklets.toAppearances(),
            )
        }
        return AppearanceCountingResult(
            people = people,
            unassignedTracklets = clustering.unassignedTracklets,
            similarityThreshold = clustering.similarityThreshold,
        )
    }

    private fun List<FaceTracklet>.toAppearances(): List<PersonAppearance> {
        val groups = mutableListOf<MutableList<FaceTracklet>>()

        sortedBy { it.startTimestampUs }.forEach { tracklet ->
            val currentGroup = groups.lastOrNull()
            val previousTracklet = currentGroup?.lastOrNull()
            val continuesCurrentAppearance = previousTracklet != null &&
                previousTracklet.sceneIndex == tracklet.sceneIndex &&
                tracklet.startTimestampUs - previousTracklet.endTimestampUs <=
                policy.maximumFragmentGapUs

            if (continuesCurrentAppearance) {
                currentGroup += tracklet
            } else {
                groups += mutableListOf(tracklet)
            }
        }

        return groups.mapIndexed { index, tracklets ->
            PersonAppearance(
                number = index + 1,
                tracklets = tracklets,
            )
        }
    }
}
