package com.enil.logez.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.HeartRateZone
import com.enil.logez.core.wellness.HeartRateAccess
import com.enil.logez.feature.workout.finish.CardHeadingRow

/**
 * Which of the tracking screen's heart-rate forms applies (decision 6, `tracking-states-sheet.png`).
 * A reading beats everything else; without one, the state is why there is none. States that nothing
 * the user taps can change ([WAITING], [UNAVAILABLE]) are one quiet line, not a card of placeholders.
 */
internal enum class TrackingHeartRateState {
    LIVE,
    LIVE_NO_MAX,
    STALE,
    WAITING,
    NOT_ALLOWED,
    STILL_OFF,
    NEEDS_HEALTH_CONNECT,
    UNAVAILABLE,

    /** Access is not known yet: show nothing rather than a card that then pops away. */
    UNKNOWN,
}

internal fun trackingHeartRateState(
    access: HeartRateAccess?,
    hasReading: Boolean,
    hasMaxHeartRate: Boolean,
    isStale: Boolean,
    permissionRequestRefused: Boolean,
): TrackingHeartRateState = when {
    hasReading && isStale -> TrackingHeartRateState.STALE
    hasReading && !hasMaxHeartRate -> TrackingHeartRateState.LIVE_NO_MAX
    hasReading -> TrackingHeartRateState.LIVE
    access == null -> TrackingHeartRateState.UNKNOWN
    // Exhaustive over [HeartRateAccess], no else: a new value must be placed here deliberately, never
    // read as "no data yet".
    else -> when (access) {
        HeartRateAccess.UNAVAILABLE -> TrackingHeartRateState.UNAVAILABLE
        HeartRateAccess.NEEDS_INSTALL_OR_UPDATE -> TrackingHeartRateState.NEEDS_HEALTH_CONNECT
        HeartRateAccess.NOT_GRANTED ->
            if (permissionRequestRefused) TrackingHeartRateState.STILL_OFF else TrackingHeartRateState.NOT_ALLOWED
        HeartRateAccess.GRANTED -> TrackingHeartRateState.WAITING
    }
}

/** The heart-rate section of the stats view; see [TrackingHeartRateState] for what shows when. */
@Composable
internal fun TrackingHeartRate(
    state: TrackingHeartRateState,
    bpm: Long?,
    zone: HeartRateZone?,
    asOf: String?,
    noun: String,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onGetHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        TrackingHeartRateState.UNKNOWN -> Unit
        TrackingHeartRateState.WAITING -> HeartRateLine(stringResource(R.string.activity_tracking_heart_rate_waiting), modifier)
        TrackingHeartRateState.UNAVAILABLE -> HeartRateLine(stringResource(R.string.activity_tracking_heart_rate_unavailable_line), modifier)
        TrackingHeartRateState.LIVE, TrackingHeartRateState.LIVE_NO_MAX, TrackingHeartRateState.STALE ->
            if (bpm != null) HeartRateReadingCard(state, bpm, zone, asOf, modifier)
        TrackingHeartRateState.NOT_ALLOWED -> HeartRateMessageCard(
            title = stringResource(R.string.activity_tracking_heart_rate_not_allowed_title),
            body = stringResource(R.string.activity_tracking_heart_rate_not_allowed_body, noun),
            action = stringResource(R.string.activity_tracking_heart_rate_allow),
            onAction = onAllow,
            modifier = modifier,
        )
        TrackingHeartRateState.STILL_OFF -> HeartRateMessageCard(
            title = stringResource(R.string.activity_tracking_heart_rate_still_off_title),
            body = stringResource(R.string.activity_tracking_heart_rate_still_off_body),
            action = stringResource(R.string.activity_tracking_heart_rate_open_settings),
            onAction = onOpenSettings,
            modifier = modifier,
        )
        TrackingHeartRateState.NEEDS_HEALTH_CONNECT -> HeartRateMessageCard(
            title = stringResource(R.string.activity_tracking_heart_rate_needs_title),
            body = stringResource(R.string.activity_tracking_heart_rate_needs_body),
            action = stringResource(R.string.activity_tracking_heart_rate_get_health_connect),
            onAction = onGetHealthConnect,
            modifier = modifier,
        )
    }
}

@Composable
private fun HeartRateLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
private fun HeartRateMessageCard(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            CardHeadingRow(
                title = stringResource(R.string.summary_gps_heart_rate_header),
                meta = stringResource(R.string.summary_gps_heart_rate_source),
            )
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.sm))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            // Nudged left so the label's text lines up with the body text above it, not the button's inset.
            TextButton(onClick = onAction, modifier = Modifier.padding(top = Spacing.xs).offset(x = (-12).dp)) { Text(action) }
        }
    }
}

/** bpm in primary, "Zone 4 / Hard" at the end, a five-segment zone bar and the reading's age; grey once stale. */
@Composable
private fun HeartRateReadingCard(state: TrackingHeartRateState, bpm: Long, zone: HeartRateZone?, asOf: String?, modifier: Modifier = Modifier) {
    val muted = state == TrackingHeartRateState.STALE
    // A stale reading greys its number (the mockup's text2) but its zone segment is the outline colour.
    val accent = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            CardHeadingRow(
                title = stringResource(R.string.summary_gps_heart_rate_header),
                meta = stringResource(R.string.summary_gps_heart_rate_source),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.Bottom) {
                // The number and its unit are measured first and keep their width; the zone takes what is
                // left and wraps there, so a large system font squeezes the zone text, not the reading.
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        bpm.toString(),
                        style = LogEzMono.dataLarge.copy(fontSize = 40.sp, lineHeight = 1.1.em),
                        color = accent,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.activity_tracking_bpm_unit),
                        style = LogEzMono.dataMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = 6.dp),
                    )
                }
                if (zone != null) {
                    Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm), horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.activity_tracking_zone_value, zone.number),
                            style = LogEzMono.dataLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                        Text(
                            heartRateZoneLabel(zone),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            if (zone != null) ZoneBar(zone, if (muted) MaterialTheme.colorScheme.outline else accent, Modifier.padding(top = Spacing.sm))
            val note = when {
                state == TrackingHeartRateState.LIVE_NO_MAX -> stringResource(R.string.activity_tracking_heart_rate_set_max)
                muted && asOf != null -> stringResource(R.string.activity_tracking_heart_rate_stale_as_of, asOf)
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            // A fresh reading says its age on its own line; a stale one carries it in the note above.
            if (!muted && asOf != null) {
                Text(
                    stringResource(R.string.activity_tracking_bpm_as_of, asOf),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/**
 * Five segments, Z1 to Z5, the current zone filled and named. Hidden from TalkBack as a whole: the
 * "Zone 4 / Hard" text above it already says the same thing in words.
 */
@Composable
private fun ZoneBar(zone: HeartRateZone, accent: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HeartRateZone.entries.forEach { z ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (z == zone) accent else MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Text(
                        stringResource(R.string.activity_tracking_zone_short, z.number),
                        style = LogEzMono.dataSmall,
                        color = if (z == zone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
            }
        }
    }
}
