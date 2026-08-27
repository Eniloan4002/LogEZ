package com.enil.logez.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        TopEdgeAccent(content)
    }
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
    ) {
        TopEdgeAccent(content)
    }
}

/**
 * v4.0's card top-edge accent: a bright primary edge fading out to the right over a soft wash.
 *
 * It has to live in the content slot, not on the card's own `modifier` — a `drawBehind` there
 * paints under the card's opaque surface (invisible), and it is Surface's clip that makes the
 * accent follow the rounded corners. A bare wrapper Column adds no padding and no offset, so
 * content keeps its position and the card keeps its shape, border and elevation.
 *
 * `fillMaxWidth` is load-bearing rather than cosmetic: without it the wrapper measures to its
 * widest child, and the wash — which spans its whole width — would end in a hard vertical seam
 * partway across cards whose content does not fill them.
 */
@Composable
private fun TopEdgeAccent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glowFalloff(MaterialTheme.colorScheme.primary),
        content = content,
    )
}
