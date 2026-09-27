package com.healthtrend.ui.theme

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The app's typeface.
 *
 * Samsung devices ship their own Latin family as part of One UI, and it reads noticeably better than
 * the platform default at small sizes. There is no public API for "does this family exist", and
 * `Typeface.create` quietly substitutes the default for an unknown name, so the honest arrangement is
 * to ask for it by name and let the platform fall back: on a device without it every glyph resolves
 * to Roboto, which is exactly the fallback that is wanted, with nothing to detect and nothing to get
 * wrong at runtime.
 *
 * CJK is unaffected either way — those glyphs are not in the Latin family on any device and come from
 * the system fallback chain, so specifying this family cannot produce missing glyphs.
 *
 * The availability probe is a heuristic on purpose. It only decides whether to name a family or to
 * ask for [FontFamily.Default]; both answers produce a working app, so a wrong answer costs a font,
 * never a crash.
 */
@OptIn(ExperimentalTextApi::class)
private val AppFontFamily: FontFamily by lazy {
    val available = DEVICE_FAMILY_CANDIDATES.firstOrNull(::isDeviceFamilyAvailable)
    if (available == null) {
        FontFamily.Default
    } else {
        FontFamily(
            Font(DeviceFontFamilyName(available), FontWeight.Normal),
            Font(DeviceFontFamilyName(available), FontWeight.Medium),
            Font(DeviceFontFamilyName(available), FontWeight.Bold),
        )
    }
}

/** Most specific first: One UI's Latin family, then the older Samsung names. */
private val DEVICE_FAMILY_CANDIDATES = listOf("SamsungOne", "SamsungOneUI", "SamsungSans")

/**
 * Whether the device actually has [familyName].
 *
 * `Typeface.create` substitutes the default family for an unknown name instead of failing, so the
 * answer has to be read back off the result. A family that was found reports itself by name; a miss
 * reports the default family (or nothing at all).
 */
private fun isDeviceFamilyAvailable(familyName: String): Boolean =
    try {
        val typeface = Typeface.create(familyName, Typeface.NORMAL)
        typeface.systemFontFamilyName?.contains(familyName, ignoreCase = true) == true
    } catch (exception: RuntimeException) {
        // Fonts are cosmetic; a hostile or malformed font config must not take the app down.
        false
    }

/**
 * Material 3's scale, in [AppFontFamily].
 *
 * The scale itself is left alone — Material's sizes and tracking are the result of more testing than
 * this app could justify replacing — so the only thing this does is put one font family under every
 * style. Nothing in the UI sets a font individually, so this file remains the single place a
 * typographic change happens.
 */
internal val HealthTrendTypography: Typography = Typography().withFontFamily(AppFontFamily)

private fun Typography.withFontFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
