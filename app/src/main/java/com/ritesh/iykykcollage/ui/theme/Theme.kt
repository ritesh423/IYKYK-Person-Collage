package com.ritesh.iykykcollage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = Peach,
    onPrimary = Paper,
    primaryContainer = SoftPeach,
    onPrimaryContainer = Ink,
    background = WarmWhite,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftPeach,
    onSurfaceVariant = MutedInk,
)

@Composable
fun IYKYKCollageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content,
    )
}

