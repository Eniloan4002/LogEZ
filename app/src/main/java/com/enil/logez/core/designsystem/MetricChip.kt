package com.enil.logez.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The loud chip of the v4.0 chip vocabulary (what am I measuring): pill, mono caps, selected = a
 * primary fill. Promoted from AnalyticsScreen.kt on 2026-10-01 so Statistics and Profile's chart
 * switch metric with the same control. Its quiet sibling, the range chip, stays in AnalyticsScreen.
 *
 * Material3's `FilterChip` is not used: its container/label colors, 8dp corner and 32dp height are
 * all baked into `FilterChipDefaults`, so a chip like the mockup's is less code hand-rolled than
 * fought for through overrides.
 */
@Composable
fun MetricChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    val primary = MaterialTheme.colorScheme.primary
    ChipTouchTarget(selected = selected, onClick = onClick) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(if (selected) primary else Color.Transparent)
                .let { if (selected) it else it.border(1.dp, MaterialTheme.colorScheme.outline, shape) }
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                label.uppercase(currentLocale()),
                style = LogEzMono.dataSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.08.em,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * The tappable area around a chip: at least 48dp tall (the Play core-quality touch-target floor;
 * the quiet range chip was about 22dp) while the chip itself keeps its compact look, and announced
 * to TalkBack as a selectable tab with its selected state (2026-09-25 accessibility pass).
 */
@Composable
fun ChipTouchTarget(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

