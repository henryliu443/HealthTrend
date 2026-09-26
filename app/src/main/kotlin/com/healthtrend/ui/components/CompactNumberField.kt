package com.healthtrend.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A one-line numeric input, deliberately shorter than a Material text field.
 *
 * `OutlinedTextField` has a 56 dp minimum height and reserves room for a floating label, which is
 * right for a form's few primary inputs and wrong for a list of twenty-two nutrient figures: at that
 * size the confirmation sheet becomes a full-screen scroll and the food's name scrolls out of sight
 * before the user has read it. The label belongs in the row beside the field anyway (see how
 * `ConfirmProfileDialog` and `NewFoodDialog` lay it out), so the field itself only needs to hold a
 * number.
 */
@Composable
internal fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}
