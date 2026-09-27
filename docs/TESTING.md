# Testing HealthTrend

Three layers, cheapest first: automated tests, then Android Studio previews, then a device. Most
mistakes are caught by the first two, so work down in order rather than starting on the phone.

Setup (JDK, SDK, mirrors) is in `development-setup.md`; this file assumes it is done.

---

## 1. Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew build            # lint + release APK + every unit test
./gradlew installDebug     # onto a connected device
```

A **debug** build seeds about three months of demo data on first launch, so the app has something to
show. A **release** build does not — it starts empty, which is what a real user sees. Test the empty
states in release.

---

## 2. Automated tests

| Suite | Covers | Command |
|---|---|---|
| `:core:analytics` (64) | statistics, EWMA, rolling windows, OLS trend and its p-value, anomaly detection, normalisation, downsampling — checked against NumPy/SciPy golden vectors | `./gradlew :core:analytics:test` |
| `:core:data` (29) | the projection pipeline, the lookup tiers, the schema migration, the export | `./gradlew :core:data:testDebugUnitTest` |

Failure output lands in `core/*/build/test-results/`, readable in HTML at
`core/*/build/reports/tests/`. The golden vectors come from `tools/generate_golden_vectors.py` and are
committed; the analytics tests load that JSON.

Two suites are worth knowing about specifically, because they encode rules rather than behaviour:

- `NutritionLookupRepositoryTest` asserts the tier order, that a food the user already owns is never
  offered as new, and that saving registers nutrient metadata *before* the value rows (the `RESTRICT`
  foreign key rejects the other order).
- `MetricDefinitionMigrationTest` builds a v1 database **from the committed `1.json`**, migrates it,
  and opens it with Room, which validates the whole resulting schema. If you change an entity, this is
  the test that tells you the migration did not keep up.

---

## 3. Previews — no device needed

Nine previews, all rendering real data through the real analytics engine and the real nutrient
catalogue. In Android Studio open the file and click the gutter icon, or use the Preview tool window.

| File | Preview | What to check |
|---|---|---|
| `dashboard/DashboardScreen.kt` | `DashboardPreview` | category sections in order, latest value per metric |
| | `DashboardEmptyPreview` | empty state reads well, demo button present |
| `detail/MetricDetailScreen.kt` | `MetricDetailPreview` | chart layers: raw line, EWMA fit, dotted OLS line, flagged points, reference band; **the `Watch for` row**; the trend badge in the card header |
| | `MetricDetailEmptyPreview` | no chart, no statistics, no crash |
| `compare/CompareScreen.kt` | `ComparePreview` | three normalised lines + legend; the "cannot be normalised" error line at the bottom |
| | `CompareMinMaxPreview` | dark theme, "select at least 2" state |
| `nutrition/NutritionScreen.kt` | `NutritionPreview` | search rows with provenance badges, selected food, day totals with `value / reference` |
| | `NutritionConfirmPreview` | **the lookup confirmation sheet** — sized to fit, source cited, every value editable |
| | `NutritionEmptyPreview` | dark theme, nothing logged |

Previews render in the default locale (English). To see Chinese without a device, add
`locale = "zh-rCN"` to a `@Preview` annotation temporarily.

---

## 4. Device checklist

The emulator was deliberately not used while building this, so everything below has been verified by
compilation and preview only. This is the list that needs eyes.

1. **First launch.** Install debug, open it. Expect ~11 metrics across five categories (Body, Health,
   Lifestyle, Activity, Nutrition), each with a latest value. The Nutrition section should have ~19
   rows — the nutrients the demo diary contains.
2. **Metric detail.** Tap *Body weight*. Expect a 90-day chart with a reference band, a raw line, a
   smoothed line, a dotted trend line and two flagged points; then statistics, a trend card and a
   data-quality card.
3. **A directional marker.** Open *Total cholesterol*. The trend badge should read **Falling** while
   the `Watch for` row says **Higher values**. Nothing should be green or red — the app must not
   imply that falling is good news.
4. **Comparison.** Pick three or four metrics and cycle the three normalisation modes. A metric that
   cannot be normalised (try *Uric acid* under Z-score) should appear as an error line rather than
   silently vanishing.
5. **Bilingual search.** In the food diary, search `米饭`, then `rice`. Both should find *Cooked white
   rice*, and its badge should say **My foods** (the demo already adopted it) — not "Built-in
   lexicon", which would mean it is being offered as new.
6. **Adopt a new food.** Search `蓝莓` or `blueberry` (not in the demo diary). Tap it: the
   confirmation sheet should open, fit on screen without swamping it, name the USDA record it came
   from, and let you edit any figure. Confirm it, then log 100 g.
7. **Projection.** After logging, the day's totals should update immediately, and the dashboard's
   Nutrition section should pick up the new nutrients. This is AGENTS.md §4.3 — the totals are read
   back out of `metric_observations`, not summed from the diary.
8. **Export.** Dashboard → *Export* → save to Downloads. Unzip it: eight files. Open
   `food_items.csv` in a spreadsheet and confirm the names read correctly (the file starts with a
   UTF-8 BOM for exactly that reason). Open `README.txt` — it should explain the reference-amount
   arithmetic.
9. **Language.** Dashboard → *Language*: the **system** per-app language page should open, listing
   English and 中文. Switch to 中文 and back. UI chrome should change language; **stored names (food
   names, metric names) should not** — that is deliberate, see `docs/TODO.md`.
10. **Font.** On a Samsung device the Latin text should be SamsungOne; on anything else, Roboto. Both
    are correct — the app asks for the device family by name and falls back.
11. **Dark mode and rotation.** Toggle dark mode: the chart palette must stay legible. Rotate the
    diary and the detail page.
12. **Empty state.** Install the release build (or clear app data) and open it: no metrics, and the
    empty state should offer both "load demo data" (debug only) and the manual entry path.

---

## 5. Regenerating the two generated artifacts

Both are committed; regenerate only when you intend to change them, and expect a reviewable diff.

```bash
# NumPy/SciPy golden vectors (needs tools/.venv, see development-setup.md §5)
tools/.venv/bin/python tools/generate_golden_vectors.py

# The 205-food lexicon, from USDA FoodData Central (needs the network once, ~6 MB)
python3 tools/generate_food_lexicon.py            # dry run: resolve and report only
python3 tools/generate_food_lexicon.py --emit     # write the Kotlin
```

The lexicon generator refuses to emit while any curated food resolves ambiguously, so a failure there
means a data problem to fix, not a step to skip.

---

## 6. Known gaps

- No instrumented (on-device) tests at all.
- The demo seeder has never actually run: it compiles, and the food names it searches for are pinned
  by a test in `:core:data`, but the seeding path itself is unverified.
- Everything visual — Vico rendering, the pills, the compact dialogs — is unverified on a screen.
- `docs/TODO.md` lists the deferred work with reasons.
