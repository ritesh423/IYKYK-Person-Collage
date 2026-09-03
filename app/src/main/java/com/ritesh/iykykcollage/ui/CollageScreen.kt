package com.ritesh.iykykcollage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritesh.iykykcollage.model.SelectedVideo

@Composable
fun CollageScreen(
    uiState: CollageUiState,
    onChooseVideo: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                BrandMark()
                Spacer(Modifier.height(48.dp))
                Text(
                    text = "Every face,\none beautiful frame.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 46.sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Choose a portrait video. The app will privately find each person and their separate appearances on your device.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(36.dp))

                when (uiState) {
                    CollageUiState.AwaitingVideo -> EmptySelectionCard(onChooseVideo)
                    is CollageUiState.VideoReady -> VideoReadyCard(
                        video = uiState.video,
                        onChooseVideo = onChooseVideo,
                        onClearSelection = onClearSelection,
                    )
                    is CollageUiState.Processing -> ProcessingCard(uiState)
                    is CollageUiState.Failure -> FailureCard(uiState, onChooseVideo)
                }
            }

            Text(
                text = "No upload • No account • On-device processing",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "i",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "IYKYK COLLAGE",
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun EmptySelectionCard(onChooseVideo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = "Start with one video",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Portrait clips around 30 seconds work best.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onChooseVideo,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("Choose video", modifier = Modifier.padding(vertical = 7.dp))
            }
        }
    }
}

@Composable
private fun VideoReadyCard(
    video: SelectedVideo,
    onChooseVideo: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = "Video selected",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = video.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Ready for on-device analysis",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onChooseVideo,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Choose another")
                }
                OutlinedButton(
                    onClick = onClearSelection,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun ProcessingCard(state: CollageUiState.Processing) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text(state.stage, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("${(state.progress * 100).toInt()}% • ${state.video.displayName}")
        }
    }
}

@Composable
private fun FailureCard(
    state: CollageUiState.Failure,
    onChooseVideo: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(24.dp)) {
            Text("We couldn't process that video", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onChooseVideo) { Text("Choose another video") }
        }
    }
}

