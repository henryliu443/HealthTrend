package com.healthtrend.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * Established directions (_p_ < 0.05) are filled and the two "no conclusion" directions are
 * outlined, because "the regression reached a verdict" and "it did not" are different claims and
 * should not look alike. See [StatusPill] for why there is no red/green here.
 */
@Composable
internal fun TrendBadge(direction: TrendDirection, modifier: Modifier = Modifier) {
    StatusPill(filled = direction.isEstablished, modifier = modifier) {
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
