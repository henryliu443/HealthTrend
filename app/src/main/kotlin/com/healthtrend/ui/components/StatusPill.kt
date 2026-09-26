package com.healthtrend.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A compact rounded label for a short status — a fitted trend, the origin of a food record.
 *
 * Two variants only: `filled` for the assertive case and outlined for the quiet one. They are
 * deliberately not colour-coded. In this app a badge often describes a *datum* rather than a
 * judgement (which way a trend points, where a number came from), and red/green would smuggle in an
 * opinion the app is not entitled to hold (AGENTS.md §10.3).
 *
 * On a `surfaceVariant` card the outlined variant uses `surface`, which reads as a lighter chip in
 * light mode and a darker one in dark mode, so it stays visible either way.
 */
@Composable
internal fun StatusPill(
    filled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (filled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (filled) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (filled) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
