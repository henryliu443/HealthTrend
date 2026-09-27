#!/usr/bin/env python3
"""Generate the bundled offline food lexicon (Phase 4, AGENTS.md 5.2).

Pulls USDA FoodData Central **SR Legacy** (April 2018 — public domain, U.S. government work),
resolves the curated entries in `food_lexicon_catalog.py`, and emits generated Kotlin that is
compiled into the app.

Why a generator instead of hand-written numbers: a health app must not ship nutrient values that
somebody typed from memory. Every value here traces back to a specific FDC record whose id and
description are recorded next to it, and the script fails loudly if a curated pattern stops
resolving to exactly one record.

Usage:
    python3 tools/generate_food_lexicon.py            # resolve, report, write nothing
    python3 tools/generate_food_lexicon.py --emit     # resolve and write the Kotlin
    python3 tools/generate_food_lexicon.py --refresh  # re-download the dataset first

The dataset is cached in `tools/.cache/`. Regenerating needs the network; the generated Kotlin is
committed, so ordinary builds and tests never touch it.
"""

from __future__ import annotations

import argparse
import csv
import io
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

from food_lexicon_catalog import (
    BEVERAGES,
    CATALOG,
    CONDIMENTS,
    DAIRY,
    DISPLAY_NAMES,
    FRUITS,
    GRAINS,
    LEGUMES,
    MEAT,
    NUTS,
    SEAFOOD,
    VEGETABLES,
    Entry,
)

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
CACHE = ROOT / ".cache"
DATASET_ZIP = CACHE / "sr_legacy.zip"
DATASET_URL = (
    "https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip"
)
DATASET_DIR = "FoodData_Central_sr_legacy_food_csv_2018-04"
DATASET_LABEL = "USDA FoodData Central — SR Legacy (April 2018)"
RETRIEVED = "2026-09-27"

OUTPUT = (
    REPO
    / "core/data/src/main/kotlin/com/healthtrend/core/data/nutrition/lexicon"
    / "BundledFoodLexiconData.kt"
)

CATEGORY_GROUPS: dict[str, list[Entry]] = {
    "GRAINS": GRAINS,
    "LEGUMES": LEGUMES,
    "VEGETABLES": VEGETABLES,
    "FRUITS": FRUITS,
    "MEAT": MEAT,
    "SEAFOOD": SEAFOOD,
    "DAIRY": DAIRY,
    "NUTS": NUTS,
    "CONDIMENTS": CONDIMENTS,
    "BEVERAGES": BEVERAGES,
}

# One `object` per category, not one top-level `val` per category.
#
# All of a file's top-level property initialisers are compiled into that file's single `<clinit>`,
# so 205 foods with ~22 nutrients each overflowed the JVM's 64 KB per-method limit and the Kotlin
# compiler died with `MethodTooLargeException`. An `object` gets its own `<clinit>`, which splits
# the work into chunks that are comfortably sized (the largest category is ~33 entries).
CATEGORY_OBJECTS = {name: f"Lexicon{name.title().replace('_', '')}" for name in CATEGORY_GROUPS}

# --------------------------------------------------------------------------- nutrient mapping
#
# Keyed by (nutrient name, unit) rather than by nutrient number: the numbers are easy to
# mis-remember (potassium is 1092, not 1094), and the name alone is not unique — SR Legacy carries
# *two* rows called "Energy", one in kcal (1008) and one in kJ (1062). Selecting on the name alone
# silently picked up the kilojoule row and inflated every calorie figure by 4.184x.
NUTRIENT_SPECS: list[tuple[str, str, str]] = [
    ("Protein", "G", "protein"),
    ("Carbohydrate, by difference", "G", "carbohydrates"),
    ("Total lipid (fat)", "G", "fat"),
    ("Fiber, total dietary", "G", "fiber"),
    ("Sugars, Total", "G", "sugar"),
    ("Fatty acids, total saturated", "G", "saturated_fat"),
    ("Fatty acids, total monounsaturated", "G", "monounsaturated_fat"),
    ("Fatty acids, total polyunsaturated", "G", "polyunsaturated_fat"),
    ("Cholesterol", "MG", "cholesterol"),
    ("Sodium, Na", "MG", "sodium"),
    ("Potassium, K", "MG", "potassium"),
    ("Calcium, Ca", "MG", "calcium"),
    ("Iron, Fe", "MG", "iron"),
    ("Magnesium, Mg", "MG", "magnesium"),
    ("Phosphorus, P", "MG", "phosphorus"),
    ("Zinc, Zn", "MG", "zinc"),
    ("Vitamin A, RAE", "UG", "vitamin_a"),
    ("Vitamin C, total ascorbic acid", "MG", "vitamin_c"),
    ("Vitamin D (D2 + D3)", "UG", "vitamin_d"),
    ("Vitamin B-12", "UG", "vitamin_b12"),
    ("Folate, total", "UG", "folate"),
]

# Plain "Energy" in kcal is what the tables quote; the Atwater variants are recomputations, used
# only as a fallback for the handful of records that omit it. In preference order.
ENERGY_SPECS: list[tuple[str, str]] = [
    ("Energy", "KCAL"),
    ("Energy (Atwater General Factors)", "KCAL"),
    ("Energy (Atwater Specific Factors)", "KCAL"),
]

# Emission order: macros, then lipids, minerals, vitamins — the order the UI shows.
NUTRIENT_ORDER = [
    "calories", "protein", "carbohydrates", "fat", "fiber", "sugar",
    "saturated_fat", "monounsaturated_fat", "polyunsaturated_fat", "cholesterol",
    "sodium", "potassium", "calcium", "iron", "magnesium", "phosphorus", "zinc",
    "vitamin_a", "vitamin_c", "vitamin_d", "vitamin_b12", "folate",
]

PAIRS_PER_LINE = 4

# Size words that may lead a household measure ("large", "medium (3\" dia)", ...). Used to name a
# per-piece food readably — "Egg (1 large, about 50 g)" rather than "Egg (1 piece, about 50 g)".
PIECE_WORDS = ("extra small", "extra large", "small", "medium", "large", "fruit")

# Household measures are only trusted between these bounds (grams). Anything outside is a sign the
# measure matched the wrong row.
MIN_PIECE_GRAMS = 5.0
MAX_PIECE_GRAMS = 600.0

# A food with fewer tracked nutrients than this is treated as a resolution mistake.
MIN_NUTRIENTS = 4


class ResolutionError(Exception):
    pass


# --------------------------------------------------------------------------- dataset


def ensure_dataset(refresh: bool) -> zipfile.ZipFile:
    if refresh or not DATASET_ZIP.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        print(f"downloading {DATASET_URL}", file=sys.stderr)
        with urllib.request.urlopen(DATASET_URL, timeout=300) as response:
            DATASET_ZIP.write_bytes(response.read())
    return zipfile.ZipFile(DATASET_ZIP)


def read_table(archive: zipfile.ZipFile, name: str) -> list[dict[str, str]]:
    with archive.open(f"{DATASET_DIR}/{name}") as raw:
        return list(csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8", newline="")))


# --------------------------------------------------------------------------- resolution


def resolve(entry: Entry, foods: list[dict[str, str]]) -> dict[str, str]:
    """Finds the single SR Legacy record [entry] refers to.

    Ambiguity is an error, not a coin flip. Two tie-breakers are applied first, and both of them
    only ever discard a *derived* record in favour of the plain one:

    1. "without salt" beats "with salt" — the base food is what the user then seasons themselves,
       and it keeps ~380 mg of sodium from being baked into every serving.
    2. The shortest description wins, because FDC spells a food out in full the plainer it is.
    """
    pattern = re.compile(entry.pattern, re.IGNORECASE)
    matches = [row for row in foods if pattern.search(row["description"])]
    if not matches:
        raise ResolutionError(f"{entry.slug}: matched no record — {entry.pattern}")

    if len(matches) > 1:
        without_salt = [row for row in matches if "without salt" in row["description"].lower()]
        if len(without_salt) == len(matches) - 1:
            matches = without_salt

    if len(matches) > 1:
        shortest = min(len(row["description"]) for row in matches)
        matches = [row for row in matches if len(row["description"]) == shortest]

    if len(matches) != 1:
        options = "\n".join(f"           {row['fdc_id']}  {row['description']}" for row in matches)
        raise ResolutionError(f"{entry.slug}: ambiguous —\n{options}")

    return matches[0]


def piece_weight(
    entry: Entry,
    fdc_id: str,
    portions: list[dict[str, str]],
    measures: dict[str, str],
) -> tuple[float, str]:
    """The gram weight of one household measure (e.g. `1 large` egg) from `food_portion.csv`."""
    pattern = re.compile(entry.piece or "", re.IGNORECASE)

    def labels(row: dict[str, str]) -> list[str]:
        unit = measures.get(row["measure_unit_id"], "")
        amount = row["amount"].removesuffix(".0") or row["amount"]
        composed = " ".join(part for part in (amount, row["modifier"], unit) if part)
        return [label for label in (row["portion_description"], composed, row["modifier"]) if label]

    hits = [
        row
        for row in portions
        if row["fdc_id"] == fdc_id and any(pattern.search(label) for label in labels(row))
    ]
    if len(hits) != 1:
        found = ", ".join(row["portion_description"] or row["modifier"] for row in hits) or "none"
        raise ResolutionError(
            f"{entry.slug}: piece measure {entry.piece!r} matched {len(hits)} rows ({found})"
        )
    row = hits[0]
    return float(row["gram_weight"]), (row["modifier"] or row["portion_description"])


# --------------------------------------------------------------------------- formatting


def kotlin_double(value: float) -> str:
    # Two decimals is well inside the source data's own precision, and keeps the file readable.
    text = repr(float(round(value * 100 + 1e-9) / 100))
    return text if ("." in text or "e" in text) else text + ".0"


def kotlin_string(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    return f'"{escaped}"'


def piece_word(measure_label: str) -> str:
    """The short, readable name of a household measure, for a per-piece food's display name."""
    lowered = measure_label.lower()
    for word in PIECE_WORDS:
        if lowered.startswith(word):
            return word
    return "piece"


# --------------------------------------------------------------------------- pipeline


def collect(archive: zipfile.ZipFile) -> tuple[dict[str, list[dict[str, object]]], list[str]]:
    foods = read_table(archive, "food.csv")
    nutrients = read_table(archive, "nutrient.csv")
    portions = read_table(archive, "food_portion.csv")
    measures = {row["id"]: row["name"] for row in read_table(archive, "measure_unit.csv")}

    key_by_nutrient_id: dict[str, str] = {}
    id_by_spec = {(row["name"], row["unit_name"]): row["id"] for row in nutrients}
    for name, unit, key in NUTRIENT_SPECS:
        if (name, unit) not in id_by_spec:
            raise ResolutionError(f"dataset has no nutrient {name!r} in {unit!r}")
        key_by_nutrient_id[id_by_spec[(name, unit)]] = key
    energy_ids = [id_by_spec[spec] for spec in ENERGY_SPECS if spec in id_by_spec]
    if not energy_ids:
        raise ResolutionError("dataset has no energy nutrient")

    problems: list[str] = []
    resolved: list[tuple[str, Entry, dict[str, str]]] = []
    for category, entries in CATEGORY_GROUPS.items():
        for entry in entries:
            try:
                resolved.append((category, entry, resolve(entry, foods)))
            except ResolutionError as error:
                problems.append(str(error))

    wanted = {row["fdc_id"] for _, _, row in resolved}
    raw_values: dict[str, dict[str, float]] = {fdc_id: {} for fdc_id in wanted}
    energy_values: dict[str, dict[str, float]] = {fdc_id: {} for fdc_id in wanted}
    energy_rank = set(energy_ids)
    with archive.open(f"{DATASET_DIR}/food_nutrient.csv") as raw:
        for row in csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8", newline="")):
            if row["fdc_id"] not in wanted or not row["amount"]:
                continue
            key = key_by_nutrient_id.get(row["nutrient_id"])
            if key is not None:
                raw_values[row["fdc_id"]][key] = float(row["amount"])
            elif row["nutrient_id"] in energy_rank:
                energy_values[row["fdc_id"]][row["nutrient_id"]] = float(row["amount"])

    # Energy is resolved by preference rather than by whichever row the CSV happened to list first.
    for fdc_id, found in energy_values.items():
        for energy_id in energy_ids:
            if energy_id in found:
                raw_values[fdc_id]["calories"] = found[energy_id]
                break

    records: dict[str, list[dict[str, object]]] = {}
    for category, entry, row in resolved:
        fdc_id = row["fdc_id"]
        profile = dict(raw_values[fdc_id])

        if "calories" not in profile:
            problems.append(f"{entry.slug}: no energy value — {row['description']}")
            continue

        display = DISPLAY_NAMES.get(entry.slug)
        if display is None:
            problems.append(f"{entry.slug}: no English display name in DISPLAY_NAMES")
            continue

        # The curated Chinese name becomes a search term, so the food is findable in either
        # language while its stored name stays a single, stable label.
        search_terms = [entry.name, *entry.aliases]
        name = display
        reference_amount, reference_unit = 100.0, "g"
        if entry.piece:
            try:
                grams, label = piece_weight(entry, fdc_id, portions, measures)
            except ResolutionError as error:
                problems.append(str(error))
                continue
            if not MIN_PIECE_GRAMS <= grams <= MAX_PIECE_GRAMS:
                problems.append(f"{entry.slug}: implausible piece weight {grams} g ({label})")
                continue
            scale = grams / 100.0
            profile = {key: value * scale for key, value in profile.items()}
            reference_amount, reference_unit = grams, "piece"
            name = f"{display} (1 {piece_word(label)}, about {grams:g} g)"

        if len(profile) < MIN_NUTRIENTS:
            problems.append(
                f"{entry.slug}: only {len(profile)} tracked nutrients — {row['description']}"
            )
            continue

        records.setdefault(category, []).append(
            {
                "entry": entry,
                "row": row,
                "name": name,
                "search_terms": search_terms,
                "reference_amount": reference_amount,
                "reference_unit": reference_unit,
                "nutrients": profile,
            }
        )
    return records, problems


def render(records: dict[str, list[dict[str, object]]]) -> str:
    header = f'''// GENERATED FILE — DO NOT EDIT.
//
// Source: {DATASET_LABEL}.
// URL: {DATASET_URL}
// Licence: public domain (works of the U.S. federal government).
//
// Regenerate with `python3 tools/generate_food_lexicon.py --emit`. Every value below names the FDC
// record it came from, so any number in the shipped app can be traced back to its source. Do not
// hand-edit: the next regeneration would overwrite it, and a silently wrong nutrient value is
// worse than a missing one.

package com.healthtrend.core.data.nutrition.lexicon
'''

    lines: list[str] = [header]

    for category, object_name in CATEGORY_OBJECTS.items():
        items = records.get(category, [])
        if not items:
            continue
        lines.append(f"\nprivate object {object_name} {{")
        lines.append("    val entries: List<LexiconEntry> = listOf(")
        for item in items:
            entry: Entry = item["entry"]  # type: ignore[assignment]
            row: dict[str, str] = item["row"]  # type: ignore[assignment]
            profile: dict[str, float] = item["nutrients"]  # type: ignore[assignment]
            lines.append("        LexiconEntry(")
            lines.append(f"            id = {kotlin_string(entry.slug)},")
            lines.append(f"            name = {kotlin_string(str(item['name']))},")
            lines.append(f"            fdcId = {row['fdc_id']}L,")
            lines.append(f"            fdcDescription = {kotlin_string(row['description'])},")
            terms: list[str] = item["search_terms"]  # type: ignore[assignment]
            if terms:
                joined = ", ".join(kotlin_string(term) for term in terms)
                lines.append(f"            searchTerms = listOf({joined}),")
            lines.append(
                f"            referenceAmount = {kotlin_double(float(item['reference_amount']))},"
                f" referenceUnit = {kotlin_string(str(item['reference_unit']))},"
            )
            pairs = [
                f"{kotlin_string(key)} to {kotlin_double(profile[key])}"
                for key in NUTRIENT_ORDER
                if key in profile
            ]
            for index in range(0, len(pairs), PAIRS_PER_LINE):
                chunk = ", ".join(pairs[index : index + PAIRS_PER_LINE])
                if index == 0:
                    lines.append(f"            nutrients = mapOf({chunk},")
                elif index + PAIRS_PER_LINE >= len(pairs):
                    lines.append(f"                {chunk},")
                    lines.append("            ),")
                else:
                    lines.append(f"                {chunk},")
            lines.append("        ),")
        lines.append("    )")
        lines.append("}")

    parts = [
        f"    {object_name}.entries"
        for category, object_name in CATEGORY_OBJECTS.items()
        if records.get(category)
    ]
    joined = " +\n".join(parts) if parts else "    emptyList()"
    lines.append(
        "\n/** Every bundled food, in catalogue order. Consumed by `BundledLexiconProvider`. */"
    )
    lines.append(f"internal val BUNDLED_LEXICON: List<LexiconEntry> =\n{joined}\n")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--emit", action="store_true", help="write the generated Kotlin")
    parser.add_argument("--refresh", action="store_true", help="re-download the dataset")
    args = parser.parse_args()

    records, problems = collect(ensure_dataset(args.refresh))

    total = sum(len(items) for items in records.values())
    print(f"catalogue: {len(CATALOG)} curated entries", file=sys.stderr)
    print(f"resolved:  {total} foods from {DATASET_LABEL}", file=sys.stderr)
    for category, items in records.items():
        print(f"  {category:<11} {len(items):>3}", file=sys.stderr)

    sparse = sorted(
        ((item["nutrients"], item["entry"]) for items in records.values() for item in items),  # type: ignore[arg-type]
        key=lambda pair: len(pair[0]),
    )[:5]
    print("\nsparsest profiles:", file=sys.stderr)
    for profile, entry in sparse:
        print(f"  {entry.slug:<28} {len(profile)}/{len(NUTRIENT_ORDER)}", file=sys.stderr)

    if problems:
        print(f"\n{len(problems)} PROBLEMS:", file=sys.stderr)
        for problem in problems:
            print(f"  ! {problem}", file=sys.stderr)
        return 1

    if not args.emit:
        print("\n(dry run — pass --emit to write)", file=sys.stderr)
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(records), encoding="utf-8")
    print(f"\nwrote {OUTPUT.relative_to(REPO)}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
