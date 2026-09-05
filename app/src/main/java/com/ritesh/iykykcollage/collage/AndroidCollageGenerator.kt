package com.ritesh.iykykcollage.collage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.ritesh.iykykcollage.identity.AppearanceCountingResult
import com.ritesh.iykykcollage.identity.PersonRepresentative
import com.ritesh.iykykcollage.identity.RepresentativeSelectionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.min

class AndroidCollageGenerator(
    context: Context,
    private val cropPlanner: PortraitCropPlanner = PortraitCropPlanner(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CollageGenerator {
    private val appContext = context.applicationContext

    override suspend fun generate(
        videoUri: String,
        representatives: RepresentativeSelectionResult,
        appearances: AppearanceCountingResult,
    ): GeneratedCollage = withContext(ioDispatcher) {
        if (representatives.representatives.isEmpty()) {
            throw CollageGenerationException("No clear representative faces were found for a collage.")
        }

        val retriever = MediaMetadataRetriever()
        val portraits = mutableListOf<PersonPortrait>()
        try {
            retriever.setDataSource(appContext, videoUri.toUri())
            val rotationDegrees = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val appearancesByPerson = appearances.people.associate {
                it.id to it.appearanceCount
            }

            representatives.representatives
                .sortedBy { it.personId }
                .forEach { representative ->
                    coroutineContext.ensureActive()
                    portraits += retriever.loadPortrait(
                        representative = representative,
                        appearanceCount = appearancesByPerson.getValue(representative.personId),
                        rotationDegrees = rotationDegrees,
                    )
                }

            GeneratedCollage(
                bitmap = renderCollage(
                    portraits = portraits,
                    totalAppearanceCount = appearances.totalAppearanceCount,
                ),
                peopleCount = appearances.uniquePersonCount,
                appearanceCount = appearances.totalAppearanceCount,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: CollageGenerationException) {
            throw error
        } catch (error: Exception) {
            throw CollageGenerationException(
                message = "The selected portraits could not be turned into a collage.",
                cause = error,
            )
        } finally {
            portraits.forEach { portrait ->
                if (!portrait.bitmap.isRecycled) portrait.bitmap.recycle()
            }
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.loadPortrait(
        representative: PersonRepresentative,
        appearanceCount: Int,
        rotationDegrees: Int,
    ): PersonPortrait {
        val observation = representative.observation
        val decoded = getFrameAtTime(
            observation.timestampUs,
            MediaMetadataRetriever.OPTION_CLOSEST,
        ) ?: throw CollageGenerationException(
            "A representative frame could not be decoded for Person ${representative.personId}.",
        )
        val upright = decoded.rotatedUpright(rotationDegrees)
        try {
            val crop = cropPlanner.plan(
                faceBounds = observation.bounds,
                coordinateWidth = observation.frameWidth,
                coordinateHeight = observation.frameHeight,
                bitmapWidth = upright.width,
                bitmapHeight = upright.height,
            )
            val cropped = Bitmap.createBitmap(
                upright,
                crop.left,
                crop.top,
                crop.size,
                crop.size,
            )
            val scaled = if (cropped.width == PORTRAIT_SIZE && cropped.height == PORTRAIT_SIZE) {
                cropped.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                cropped.scale(PORTRAIT_SIZE, PORTRAIT_SIZE)
            }
            if (!cropped.isRecycled) cropped.recycle()
            return PersonPortrait(
                personId = representative.personId,
                appearanceCount = appearanceCount,
                bitmap = scaled,
            )
        } finally {
            if (upright !== decoded && !upright.isRecycled) upright.recycle()
            if (!decoded.isRecycled) decoded.recycle()
        }
    }

    private fun renderCollage(
        portraits: List<PersonPortrait>,
        totalAppearanceCount: Int,
    ): Bitmap {
        val columnCount = min(MAX_COLUMNS, portraits.size)
        val rowCount = ceil(portraits.size / columnCount.toDouble()).toInt()
        val cardWidth = (OUTPUT_WIDTH - SIDE_MARGIN * 2 - GRID_GAP * (columnCount - 1)) /
            columnCount
        val cardHeight = cardWidth + LABEL_HEIGHT
        val outputHeight = HEADER_HEIGHT +
            rowCount * cardHeight +
            (rowCount - 1) * GRID_GAP +
            BOTTOM_MARGIN
        val output = Bitmap.createBitmap(OUTPUT_WIDTH, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(BACKGROUND_COLOR)

        paint.color = ACCENT_COLOR
        paint.textSize = 30f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("IYKYK COLLAGE", SIDE_MARGIN.toFloat(), 62f, paint)

        paint.color = TITLE_COLOR
        paint.textSize = 58f
        canvas.drawText(
            "${portraits.size} people in one frame",
            SIDE_MARGIN.toFloat(),
            130f,
            paint,
        )
        paint.color = SECONDARY_TEXT_COLOR
        paint.textSize = 27f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(
            "$totalAppearanceCount appearances • created privately on-device",
            SIDE_MARGIN.toFloat(),
            178f,
            paint,
        )

        portraits.forEachIndexed { index, portrait ->
            val row = index / columnCount
            val column = index % columnCount
            val itemsInRow = min(columnCount, portraits.size - row * columnCount)
            val rowWidth = itemsInRow * cardWidth + (itemsInRow - 1) * GRID_GAP
            val rowStart = (OUTPUT_WIDTH - rowWidth) / 2
            val left = rowStart + column * (cardWidth + GRID_GAP)
            val top = HEADER_HEIGHT + row * (cardHeight + GRID_GAP)
            drawPortraitCard(
                canvas = canvas,
                portrait = portrait,
                left = left.toFloat(),
                top = top.toFloat(),
                width = cardWidth.toFloat(),
                paint = paint,
            )
        }

        return output
    }

    private fun drawPortraitCard(
        canvas: Canvas,
        portrait: PersonPortrait,
        left: Float,
        top: Float,
        width: Float,
        paint: Paint,
    ) {
        val card = RectF(left, top, left + width, top + width + LABEL_HEIGHT)
        paint.color = CARD_COLOR
        canvas.drawRoundRect(card, CORNER_RADIUS, CORNER_RADIUS, paint)

        val imageBounds = RectF(left, top, left + width, top + width)
        val clip = Path().apply {
            addRoundRect(imageBounds, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(portrait.bitmap, null, imageBounds, paint)
        canvas.restore()

        paint.color = TITLE_COLOR
        paint.textSize = 32f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Person ${portrait.personId}", left + 24f, top + width + 43f, paint)

        paint.color = SECONDARY_TEXT_COLOR
        paint.textSize = 25f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val appearanceLabel = if (portrait.appearanceCount == 1) "appearance" else "appearances"
        canvas.drawText(
            "${portrait.appearanceCount} $appearanceLabel",
            left + 24f,
            top + width + 82f,
            paint,
        )
    }

    private data class PersonPortrait(
        val personId: Int,
        val appearanceCount: Int,
        val bitmap: Bitmap,
    )

    private companion object {
        const val OUTPUT_WIDTH = 1080
        const val PORTRAIT_SIZE = 640
        const val MAX_COLUMNS = 2
        const val SIDE_MARGIN = 54
        const val GRID_GAP = 24
        const val HEADER_HEIGHT = 220
        const val LABEL_HEIGHT = 106
        const val BOTTOM_MARGIN = 54
        const val CORNER_RADIUS = 28f
        val BACKGROUND_COLOR: Int = Color.rgb(244, 240, 232)
        val CARD_COLOR: Int = Color.rgb(255, 255, 255)
        val TITLE_COLOR: Int = Color.rgb(31, 31, 29)
        val SECONDARY_TEXT_COLOR: Int = Color.rgb(91, 88, 81)
        val ACCENT_COLOR: Int = Color.rgb(156, 73, 47)
    }
}

private fun Bitmap.rotatedUpright(rotationDegrees: Int): Bitmap {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    if (normalized == 0) return this
    return Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(normalized.toFloat()) },
        true,
    )
}
