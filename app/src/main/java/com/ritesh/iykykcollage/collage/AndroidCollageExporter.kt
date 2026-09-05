package com.ritesh.iykykcollage.collage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

class AndroidCollageExporter(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    suspend fun save(bitmap: Bitmap, destination: Uri) = withContext(ioDispatcher) {
        writePng(bitmap) {
            appContext.contentResolver.openOutputStream(destination, "w")
                ?: throw IOException("The selected save location could not be opened.")
        }
    }

    suspend fun prepareForSharing(bitmap: Bitmap): Uri = withContext(ioDispatcher) {
        val shareDirectory = File(appContext.cacheDir, SHARE_DIRECTORY)
        if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
            throw IOException("The temporary share folder could not be created.")
        }

        val shareFile = File(shareDirectory, SHARE_FILE_NAME)
        writePng(bitmap, shareFile::outputStream)
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            shareFile,
        )
    }

    private fun writePng(
        bitmap: Bitmap,
        openOutput: () -> OutputStream,
    ) {
        if (bitmap.isRecycled) throw IOException("The collage is no longer available.")
        openOutput().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                throw IOException("The collage could not be encoded as PNG.")
            }
        }
    }

    private companion object {
        const val SHARE_DIRECTORY = "shared_collages"
        const val SHARE_FILE_NAME = "iykyk-person-collage.png"
        const val PNG_QUALITY = 100
    }
}
