package com.ritesh.iykykcollage.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ritesh.iykykcollage.collage.AndroidCollageExporter
import kotlinx.coroutines.launch

@Composable
fun CollageRoute() {
    val context = LocalContext.current
    val viewModelFactory = remember(context.applicationContext) {
        CollageViewModelFactory(context.applicationContext)
    }
    val viewModel: CollageViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collageExporter = remember(context.applicationContext) {
        AndroidCollageExporter(context.applicationContext)
    }
    val coroutineScope = rememberCoroutineScope()

    val videoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(
                uri = uri.toString(),
                displayName = context.displayNameOf(uri),
            )
        }
    }
    val saveCollage = rememberLauncherForActivityResult(CreateDocument("image/png")) { uri ->
        if (uri != null) {
            val bitmap = (viewModel.uiState.value as? CollageUiState.PeopleCounted)
                ?.collage
                ?.bitmap
            if (bitmap == null || bitmap.isRecycled) {
                context.showMessage("The collage is no longer available.")
            } else {
                coroutineScope.launch {
                    runCatching { collageExporter.save(bitmap, uri) }
                        .onSuccess { context.showMessage("Collage saved") }
                        .onFailure { context.showMessage("The collage could not be saved.") }
                }
            }
        }
    }

    CollageScreen(
        uiState = uiState,
        onChooseVideo = {
            videoPicker.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
        },
        onClearSelection = viewModel::onSelectionCleared,
        onAnalyzeVideo = viewModel::onAnalyzeVideo,
        onCancelProcessing = viewModel::onCancelProcessing,
        onSaveCollage = {
            saveCollage.launch("IYKYK-person-collage.png")
        },
        onShareCollage = {
            val bitmap = (uiState as? CollageUiState.PeopleCounted)?.collage?.bitmap
            if (bitmap == null || bitmap.isRecycled) {
                context.showMessage("The collage is no longer available.")
            } else {
                coroutineScope.launch {
                    runCatching {
                        context.openShareSheet(collageExporter.prepareForSharing(bitmap))
                    }.onFailure {
                        context.showMessage("The collage could not be shared.")
                    }
                }
            }
        },
    )
}

private fun Context.openShareSheet(uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri("IYKYK person collage", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, "Share your collage"))
}

private fun Context.showMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private fun Context.displayNameOf(uri: Uri): String {
    val name = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    return name ?: "Selected portrait video"
}
