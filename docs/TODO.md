# TODO

Deferred work, with the reason it was deferred and what would make it actionable. Nothing here is
blocked by a design decision — each item is waiting on a *verification* this machine cannot provide,
on a decision only the product owner can make, or on a phase that has not landed yet.

Ordered roughly by when it should be picked up.

---

## 1. Live remote lookup provider (AGENTS.md §5.2, tier 3)

**What.** A `NutritionLookupProvider` at `LookupTier.REMOTE` talking to a public food database —
OpenFoodFacts for branded/barcode lookups, or USDA FoodData Central's live API for items the bundled
lexicon does not cover.

**Why it was deferred.** Not because the app is offline-first. *Network is not the obstacle, and it
never was*: item 2 will need the network too. The obstacle is that a live third-party call cannot be
**verified** from this machine, and shipping an unverified adapter would mean shipping a code path
nobody has ever seen return a correct answer. AGENTS.md's verification gate forbids calling that kind
of work done.

**What makes it actionable.** The seam is already in place and tested:
- `NutritionLookupProvider` + `LookupTier` + `FoodRef` (`:core:domain`).
- `NutritionLookupRepositoryImpl` orders tiers, de-duplicates by name, routes `profile()` back to the
  owning provider, and re-labels hits the user already owns — all covered by
  `NutritionLookupRepositoryTest`.
- Registration is one `single<NutritionLookupProvider>(named(...))` block in `AppModule`.

So the work is: (a) write the adapter over an injectable HTTP transport port, (b) test the
JSON→`FoodNutrientProfile` mapping against a canned response, (c) run it once against the real
endpoint on a network that allows it, and (d) add the `INTERNET` permission. A failing tier is already
skipped, so degradation to the bundled lexicon is automatic.

## 2. AI / natural-language estimate provider (AGENTS.md §5.2, tier 4)

**What.** "一碗红烧牛肉面加一个荷包蛋" → a rough calorie and nutrient estimate, at
`LookupTier.ESTIMATE`.

**Why it was deferred.** Same verification problem as item 1, plus it needs a key and a cost policy.
It is also the lowest-priority tier by construction: the enum already puts it last, so it can never
displace a measured value.

**What makes it actionable.** An adapter implementing `NutritionLookupProvider` is sufficient — no
pipeline change. Two requirements worth stating now so they are not lost: it must set
`isOfflineCapable = false`, and its output must go through the existing confirmation sheet unchanged,
because §5.3's "show before saving" matters *more* for a guess than for a database record.

## 3. Data importer

**What.** Reading a snapshot (see `DataExporter`) back into the database.

**Why it was deferred.** Deliberately, not for lack of time. Importing into an existing history is
where a local-first app can silently destroy data: id collisions, partially-applied imports, a
snapshot from a different schema version, and the question of whether import *merges* or *replaces*
are all design decisions with no safe default. Export is safe because it only reads; import is not,
and the export format should be exercised by real use before it is frozen into a restore path.

**What makes it actionable.** Decide merge-vs-replace first. The manifest already carries
`formatVersion` and `databaseVersion` so a future importer can refuse a snapshot it does not
understand.

## 4. Broaden the bundled lexicon

205 foods, all resolved unambiguously from SR Legacy. The gaps are structural, not accidental:
SR Legacy is a US composition table, so there are no prepared/Chinese dishes (馒头, 饺子, 油条, 豆浆,
米粥), and a few ingredients have no clean record (莲藕, 空心菜, 荔枝).

Two options, in order of preference:
1. Add FNDDS (`FoodData_Central_survey_food_csv`) as a second source for prepared foods. It is
   per-100 g like SR Legacy and has `fndds_ingredient` mappings, so the same generator can absorb it.
2. Expand the curated catalogue within SR Legacy — cheap, but it is running out of well-covered
   ingredients.

Either way the work happens in `tools/food_lexicon_catalog.py`; the generator and the tests are
already general.

## 5. Traditional Chinese

Only `values-zh-rCN` ships, so a device set to `zh-TW`/`zh-Hant` falls back to English rather than
showing Simplified. Adding `values-zh-rTW` plus a second `<locale>` in `locales_config.xml` is
mechanical, and `docs/TESTING.md` §4 step 9 is where to check it.

## 6. Device verification

Everything visual and the demo seeder have been verified by compilation and preview only — the
emulator was not run. `docs/TESTING.md` §4 is the checklist, with expected results per step, and §6
lists exactly what is still unproven.

## 7. Koin DSL deprecation

`AppModule` uses `org.koin.androidx.viewmodel.dsl.viewModel`, which Koin 4 deprecates in favour of
`org.koin.core.module.dsl.*`. Pure import churn, but it prints four warnings on every build.
