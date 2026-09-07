package com.enil.logez.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M9b (Neon Lab, docs/adr/0003-neon-lab-rebrand.md) — a real elevation/border treatment
 * (`Elevation.card` shadow + an `outlineVariant` hairline) replacing the flat, borderless cards
 * every screen used before this milestone. Default-styled `Card(...)` call sites should use this
 * instead; a call site that already customizes `colors`/`elevation`/`border`/`shape` for a
 * deliberate reason (a selected/highlighted state, a specific accent) stays plain `Card(...)` --
 * this wrapper is for the common case, not a blanket replacement.
 */
@Composable
fun LogEzCard(
    modifier: Modifier = Modifier,
    /** M20a: `Elevation.dragging` while a reorderable card is being dragged; the default otherwise. */
    elevation: Dp = Elevation.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun LogEzCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}
