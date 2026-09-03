package com.ritesh.iykykcollage.model

/**
 * The UI and ViewModel store a URI string instead of a file path because Android's
 * Photo Picker grants access through a content URI. Selected media does not have
 * to exist as a directly accessible filesystem file.
 */
data class SelectedVideo(
    val uri: String,
    val displayName: String,
)

