package com.healthtrend.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.healthtrend.core.analytics.trend.TrendDirection
import com.healthtrend.ui.common.glyphRes
import com.healthtrend.ui.common.isEstablished
import com.healthtrend.ui.common.labelRes

/**
 * The fitted trend's verdict, as a compact pill.
 *
 * The verdict is the conclusion of the whole regression block, so it sits on its own rather than as
 * one more `label / value` line — it is what a reader is looking for, and a bare sentence stranded
 * between the numbers and the footnote reads as a stray paragraph.
 *
 * The two established directions are filled and the two "no conclusion" directions are outlined.
 * Deliberately **not** colour-coded: a rising weight and a rising uric acid are not the same news,
 * and this app is forbidden from implying which way is good (AGENTS.md §10.3).
 */
@Composable
internal fun TrendBadge(direction: TrendDirection, modifier: Modifier = Modifier) {
    val established = direction.isEstablished
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (established) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (established) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (established) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decorative: the word beside it already carries the meaning for screen readers.
            Text(
                text = stringResource(direction.glyphRes),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(
                text = stringResource(direction.labelRes),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
