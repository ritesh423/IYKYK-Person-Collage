package com.ritesh.iykykcollage.video

data class VideoMetadata(
    val durationMs: Long,
    val encodedWidth: Int,
    val encodedHeight: Int,
    val rotationDegrees: Int,
) {
    val displayWidth: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) {
            encodedHeight
        } else {
            encodedWidth
        }

    val displayHeight: Int
        get() = if (rotationDegrees == 90 || rotationDegrees == 270) {
            encodedWidth
        } else {
            encodedHeight
        }
}
