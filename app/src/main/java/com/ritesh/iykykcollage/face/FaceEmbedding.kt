package com.ritesh.iykykcollage.face

import kotlin.math.sqrt

/**
 * Immutable, unit-length identity descriptor produced by the face-embedding model.
 */
class FaceEmbedding private constructor(
    private val normalizedComponents: FloatArray,
) {
    val dimension: Int get() = normalizedComponents.size

    operator fun get(index: Int): Float = normalizedComponents[index]

    fun toFloatArray(): FloatArray = normalizedComponents.copyOf()

    fun cosineSimilarity(other: FaceEmbedding): Float {
        require(dimension == other.dimension) {
            "Embedding dimensions must match: $dimension and ${other.dimension}"
        }

        var dotProduct = 0.0
        normalizedComponents.indices.forEach { index ->
            dotProduct += normalizedComponents[index] * other.normalizedComponents[index]
        }
        return dotProduct.toFloat().coerceIn(-1f, 1f)
    }

    override fun equals(other: Any?): Boolean =
        other is FaceEmbedding && normalizedComponents.contentEquals(other.normalizedComponents)

    override fun hashCode(): Int = normalizedComponents.contentHashCode()

    override fun toString(): String = "FaceEmbedding(dimension=$dimension)"

    companion object {
        fun from(rawComponents: FloatArray): FaceEmbedding {
            require(rawComponents.isNotEmpty()) { "An embedding cannot be empty" }
            require(rawComponents.all(Float::isFinite)) { "Embedding values must be finite" }

            var squaredNorm = 0.0
            rawComponents.forEach { component ->
                val componentAsDouble = component.toDouble()
                squaredNorm += componentAsDouble * componentAsDouble
            }
            require(squaredNorm.isFinite() && squaredNorm > 0.0) {
                "An embedding must have a finite, non-zero norm"
            }

            val norm = sqrt(squaredNorm).toFloat()
            return FaceEmbedding(
                FloatArray(rawComponents.size) { index -> rawComponents[index] / norm },
            )
        }
    }
}
