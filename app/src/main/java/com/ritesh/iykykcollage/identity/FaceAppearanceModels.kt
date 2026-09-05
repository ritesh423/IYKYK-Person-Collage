package com.ritesh.iykykcollage.identity

import com.ritesh.iykykcollage.tracking.FaceTracklet

data class PersonAppearance(
    val number: Int,
    val tracklets: List<FaceTracklet>,
) {
    init {
        require(tracklets.isNotEmpty()) { "An appearance must contain at least one tracklet" }
        require(tracklets.map { it.sceneIndex }.distinct().size == 1) {
            "An appearance cannot cross a scene boundary"
        }
    }

    val sceneIndex: Int get() = tracklets.first().sceneIndex
    val startTimestampUs: Long get() = tracklets.minOf { it.startTimestampUs }
    val endTimestampUs: Long get() = tracklets.maxOf { it.endTimestampUs }
}

data class IdentifiedPerson(
    val identity: FaceIdentity,
    val appearances: List<PersonAppearance>,
) {
    val id: Int get() = identity.id
    val appearanceCount: Int get() = appearances.size
}

data class AppearanceCountingResult(
    val people: List<IdentifiedPerson>,
    val unassignedTracklets: List<FaceTracklet>,
) {
    val uniquePersonCount: Int get() = people.size
    val totalAppearanceCount: Int get() = people.sumOf { it.appearanceCount }
}
