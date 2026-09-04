package com.ritesh.iykykcollage.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CollageRoute() {
    val context = LocalContext.current
    val viewModelFactory = remember(context.applicationContext) {
        CollageViewModelFactory(context.applicationContext)
    }
    val viewModel: CollageViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val videoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(
                uri = uri.toString(),
                displayName = context.displayNameOf(uri),
            )
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
    )
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
