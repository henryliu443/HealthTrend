package com.healthtrend.core.data.lexicon

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.healthtrend.core.data.nutrition.lexicon.BUNDLED_LEXICON
import com.healthtrend.core.data.nutrition.lexicon.BundledLexiconNames
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The lexicon ships two things that must agree: the canonical stored name in
 * `BundledFoodLexiconData.kt`, and the localised names in the generated `food_names.xml` resources.
 *
 * They come from one generator run, so the realistic failure is not subtle drift but a whole file
 * written twice — English into the Chinese resources, or a stale resource list after a catalogue
 * change. Both are invisible at compile time and would show up as a mixed-language UI, which is
 * exactly the bug this arrangement exists to fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BundledLexiconNamesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun localized(locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
    }

    @Test
    fun `every bundled food has a localised name in both languages`() {
        val chinese = localized(Locale.SIMPLIFIED_CHINESE)

        BundledLexiconNames.size shouldBe BUNDLED_LEXICON.size

        var translated = 0
        for (entry in BUNDLED_LEXICON) {
            val resource = BundledLexiconNames.nameRes(entry.id)
                ?: throw AssertionError("no name resource generated for ${entry.id}")

            val english = context.getString(resource)
            val chineseName = chinese.getString(resource)
            if (english.isBlank() || chineseName.isBlank()) {
                throw AssertionError("${entry.id} has a blank name: en=$english zh=$chineseName")
            }
            // The English resource is what the food is stored under, so it must not drift from it.
            if (english != entry.name) {
                throw AssertionError("${entry.id}: resource '$english' != stored name '${entry.name}'")
            }
            if (english != chineseName) translated++
        }

        translated shouldBeGreaterThanOrEqual BUNDLED_LEXICON.size
    }

    @Test
    fun `an unknown food has no name resource`() {
        BundledLexiconNames.nameRes("food_0191f2a3-user-owned") shouldBe null
    }
}
