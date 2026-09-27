package com.healthtrend.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the system's per-app language page for this app.
 *
 * The app deliberately has no language picker of its own. Android 13+ already owns that decision,
 * keeps it per-app, and remembers it across reinstalls of the setting — a second, app-drawn picker
 * would be a worse copy of a system screen, and it would have to reimplement "follow the system" for
 * users who never wanted to choose. So the app only offers the jump (AGENTS.md's local-first stance:
 * own the data, not the platform's jobs).
 *
 * The locales the app *ships* are declared in `res/xml/locales_config.xml`; without that file the
 * system page would have nothing to list.
 *
 * @return `false` when no activity could handle either intent, in which case the caller tells the
 *   user rather than appearing to do nothing.
 */
internal fun openAppLanguageSettings(context: Context): Boolean {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        // The language page itself (API 33+; this app's minSdk is above that).
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, packageUri),
        // Fallback: the app's details page, which carries a Languages entry on the same versions.
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent)
            return true
        } catch (exception: ActivityNotFoundException) {
            // Try the next candidate; a device with no settings activity at all is not impossible.
        }
    }
    return false
}
