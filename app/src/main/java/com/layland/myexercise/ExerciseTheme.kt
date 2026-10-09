package com.layland.myexercise

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object UiSpace {
    val screen = 20.dp
    val small = 4.dp
    val gap = 12.dp
    val section = 20.dp
    val cardRadius = 24.dp
    val fieldRadius = 12.dp
    val buttonHeight = 52.dp
    val numberWidth = 64.dp
    val pairBreakpoint = 280.dp
    val wideBreakpoint = 600.dp
    val compactHeight = 560.dp
    val brandIcon = 32.dp
    val icon = 24.dp
    val inputBorder = 1.dp
    val touchTarget = 48.dp
    val rowHeight = 64.dp
    val ringSize = 220.dp
    val ringStroke = 8.dp
    val timerText = 48.sp
    val segmentedPadding = 4.dp
}

@Composable
internal fun ExerciseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = colorResource(R.color.ui_primary),
        onPrimary = colorResource(R.color.ui_on_primary),
        primaryContainer = colorResource(R.color.ui_primary_container),
        onPrimaryContainer = colorResource(R.color.ui_on_primary_container),
        secondary = colorResource(R.color.ui_rest),
        secondaryContainer = colorResource(R.color.ui_rest_container),
        onSecondaryContainer = colorResource(R.color.ui_on_rest_container),
        background = colorResource(R.color.ui_background),
        surface = colorResource(R.color.ui_surface),
        surfaceVariant = colorResource(R.color.ui_outline_variant),
        onSurface = colorResource(R.color.ui_on_surface),
        onSurfaceVariant = colorResource(R.color.ui_muted),
        outline = colorResource(R.color.ui_outline),
        outlineVariant = colorResource(R.color.ui_outline_variant),
    ), content = content)
}
