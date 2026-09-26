# TODO

Deferred work, with the reason it was deferred and what would make it actionable. Nothing here is
blocked by a design decision — each item is waiting on a *verification* this machine cannot provide,
or on a phase that has not landed yet.

Ordered roughly by when it should be picked up.

---

## 1. Live remote lookup provider (AGENTS.md §5.2, tier 3)

**What.** A `NutritionLookupProvider` at `LookupTier.REMOTE` talking to a public food database —
OpenFoodFacts for branded/barcode lookups, or USDA FoodData Central's live API for items the bundled
lexicon does not cover.

**Why it was deferred.** Not because the app is offline-first. *Network is not the obstacle, and it
never was*: tier 4 (below) will need the network too, and so will any future sync. The obstacle is
that a live third-party call cannot be **verified** from this machine, and shipping an unverified
adapter would mean shipping a code path nobody has ever seen return a correct answer. AGENTS.md's
verification gate forbids marking that kind of work done.

**What makes it actionable.** The seam is already in place and tested:
- `NutritionLookupProvider` + `LookupTier` + `FoodRef` (`:core:domain`).
- `NutritionLookupRepositoryImpl` orders tiers, de-duplicates by name, routes `profile()` back to the
  owning provider, and is covered by `NutritionLookupRepositoryTest`.
- Registration is one `single<NutritionLookupProvider>(named(...))` block in `AppModule`.

So the work is: (a) write the adapter over an injectable HTTP transport port, (b) test the
JSON→`FoodNutrientProfile` mapping against a canned response, (c) run it once against the real
endpoint on a network that allows it, and (d) add `INTERNET` permission plus a failure path that
degrades to the bundled tier (already automatic — a failing tier is skipped).

Note that USDA data is *already* shipped, just not fetched at runtime: see `docs/development-setup.md`
§8. The gap is branded/prepared/barcode items, which a composition table does not contain at all.

## 2. AI / natural-language estimate provider (AGENTS.md §5.2, tier 4)

**What.** "一碗红烧牛肉面加一个荷包蛋" → a rough calorie and nutrient estimate, at
`LookupTier.ESTIMATE`.

**Why it was deferred.** Same verification problem as item 1, plus it needs a key and a cost policy.
It is also the lowest-priority tier by construction: an estimate must never displace a measured
value, and the enum already puts it last.

**What makes it actionable.** Nothing new is needed in the pipeline — an adapter implementing
`NutritionLookupProvider` is sufficient. Two requirements worth stating now so they are not lost:
it must set `isOfflineCapable = false`, and its output must go through the existing
`ConfirmProfileDialog` unchanged, because §5.3's "show before saving" matters *more* for a guess
than for a database record.

## 3. Broaden the bundled lexicon

205 foods, all resolved unambiguously from SR Legacy. The gaps are structural, not accidental:
SR Legacy is a US composition table, so there are no prepared/Chinese dishes (馒头, 饺子, 油条,
豆浆, 米粥), and a few ingredients have no clean record (莲藕, 空心菜, 荔枝).

Two options, in order of preference:
1. Add FNDDS (`FoodData_Central_survey_food_csv`) as a second source for prepared foods. It is
   per-100 g like SR Legacy and has `fndds_ingredient` mappings, so the same generator can absorb it.
2. Expand the curated catalogue within SR Legacy — cheap, but it is running out of well-covered
   ingredients.

Either way the catalogue (`tools/food_lexicon_catalog.py`) is where the work happens; the generator
and the tests are already general.

## 4. Device verification of the demo seeder and the layouts

The demo path and every layout were verified by *compilation* only — the emulator was not run, by
request. Specifically unverified:
- `DemoDataSeeder` adopting foods through the lookup pipeline at first launch (it compiles and its
  food names are pinned by a test in `:core:data`, but it has never executed).
- Rendering of the Vico charts, the icon-less pills and the compact dialogs on a real screen.
- The `NutrientCatalog` reordering (`displayOrder` changed) on a database seeded by an older build —
  `insertAllIfAbsent` keeps the old order, which is cosmetic but untested.

## 5. Lint noise

`app/build/reports/lint-results-debug.xml` reports three unused string resources
(`action_delete`, `chart_axis_value`, `label_loading`). They predate Phase 4; either wire them up or
delete them.

## 6. Koin DSL deprecation

`AppModule` uses `org.koin.androidx.viewmodel.dsl.viewModel`, which Koin 4 deprecates in favour of
`org.koin.core.module.dsl.*`. Pure import churn, but it prints four warnings on every build.
