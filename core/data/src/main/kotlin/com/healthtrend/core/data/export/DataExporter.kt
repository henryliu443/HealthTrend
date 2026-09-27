package com.healthtrend.core.data.export

import com.healthtrend.core.data.local.HealthTrendDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One file of a snapshot: an ASCII name and its full text. */
data class ExportFile(val name: String, val content: String)

/** A ready-to-save snapshot. */
data class ExportArchive(val fileName: String, val bytes: ByteArray) {
    // A ByteArray field makes the generated equals/hashCode reference-based, which would be wrong for
    // a value type. Comparing the payload is both cheap to state and what a caller would expect.
    override fun equals(other: Any?): Boolean =
        this === other || (other is ExportArchive && fileName == other.fileName &&
            bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
}

/**
 * Reads the whole database into a portable snapshot (AGENTS.md §10.4).
 *
 * **This never writes to the database.** Export is a read: the app's answer to "how do I get my data
 * out" is a file the user owns, not a second copy the app has to keep consistent. There is no
 * importer yet, deliberately — re-importing is where a local-first app can silently corrupt an
 * existing history, and that is a feature to design on its own rather than to bolt onto this.
 *
 * The output is CSV per table plus two small files that make the archive self-describing:
 * `README.txt` (what each file is, and the conventions) and `manifest.json` (versions and row
 * counts). CSV because the point of an export is to be read by something other than this app, and
 * every spreadsheet and dataframe library reads CSV.
 *
 * Ordering is fixed per table so that two exports of unchanged data are byte-identical, which makes
 * them diffable — the cheap way to answer "did anything change?".
 */
class DataExporter(
    private val database: HealthTrendDatabase,
    private val appVersion: String,
    private val zoneId: ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    /** The snapshot's files, in the order they appear in the archive. */
    suspend fun files(): List<ExportFile> = withContext(Dispatchers.IO) {
        val metricDefinitions = database.metricDefinitionDao().findAll()
        val metricObservations = database.metricObservationDao().findAll()
        val nutrientDefinitions = database.nutrientDefinitionDao().findAll()
        val foodItems = database.foodItemDao().findAll()
        val foodNutrientValues = database.foodNutrientValueDao().findAll()
        val mealLogs = database.mealLogDao().findAll()

        val countsAtExport = linkedMapOf(
            "metric_definitions" to metricDefinitions.size,
            "metric_observations" to metricObservations.size,
            "nutrient_definitions" to nutrientDefinitions.size,
            "food_items" to foodItems.size,
            "food_nutrient_values" to foodNutrientValues.size,
            "meal_logs" to mealLogs.size,
        )

        val metricDefinitionsCsv = Csv("id", "name", "category", "unit", "data_type", "description",
            "expected_frequency", "min_value", "max_value", "reference_range_low",
            "reference_range_high", "concern_direction", "is_built_in", "display_order").apply {
            metricDefinitions.forEach { row ->
                add(row.id, row.name, row.category, row.unit, row.dataType, row.description,
                    row.expectedFrequency, row.minValue, row.maxValue, row.referenceRangeLow,
                    row.referenceRangeHigh, row.concernDirection, row.isBuiltIn, row.displayOrder)
            }
        }

        val metricObservationsCsv = Csv("id", "metric_id", "timestamp_epoch_millis",
            "timestamp_local_iso", "value", "unit", "source", "metadata_json").apply {
            metricObservations.forEach { row ->
                add(row.id, row.metricId, row.timestamp, localIso(row.timestamp), row.value, row.unit,
                    row.source, row.metadataJson)
            }
        }

        val nutrientDefinitionsCsv = Csv("id", "name", "unit", "category", "daily_recommended",
            "display_order").apply {
            nutrientDefinitions.forEach { row ->
                add(row.id, row.name, row.unit, row.category, row.dailyRecommended, row.displayOrder)
            }
        }

        val foodItemsCsv = Csv("id", "name", "brand", "reference_amount", "reference_unit",
            "is_custom", "note").apply {
            foodItems.forEach { row ->
                add(row.id, row.name, row.brand, row.referenceAmount, row.referenceUnit, row.isCustom,
                    if (row.isCustom) "user food" else "adopted from a bundled source")
            }
        }

        val foodNutrientValuesCsv = Csv("food_id", "nutrient_id", "amount_per_reference").apply {
            foodNutrientValues.forEach { row ->
                add(row.foodId, row.nutrientId, row.amountPerReference)
            }
        }

        val mealLogsCsv = Csv("id", "food_id", "timestamp_epoch_millis", "timestamp_local_iso",
            "actual_amount", "actual_unit", "meal_type").apply {
            mealLogs.forEach { row ->
                add(row.id, row.foodId, row.timestamp, localIso(row.timestamp), row.actualAmount,
                    row.actualUnit, row.mealType)
            }
        }

        listOf(
            ExportFile("README.txt", readme(countsAtExport)),
            ExportFile("manifest.json", manifest(countsAtExport)),
            ExportFile("metric_definitions.csv", metricDefinitionsCsv.render()),
            ExportFile("metric_observations.csv", metricObservationsCsv.render()),
            ExportFile("nutrient_definitions.csv", nutrientDefinitionsCsv.render()),
            ExportFile("food_items.csv", foodItemsCsv.render()),
            ExportFile("food_nutrient_values.csv", foodNutrientValuesCsv.render()),
            ExportFile("meal_logs.csv", mealLogsCsv.render()),
        )
    }

    /** The snapshot as a zip. Built entirely in memory; nothing is written here. */
    suspend fun archive(): ExportArchive = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files().forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                zip.write(file.content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        ExportArchive(fileName = suggestedFileName(), bytes = output.toByteArray())
    }

    /** `healthtrend-20260927.zip`, dated in the user's own zone. */
    fun suggestedFileName(): String {
        val day = Instant.ofEpochMilli(nowMillis()).atZone(zoneId).toLocalDate()
        return "healthtrend-$day.zip"
    }

    private fun localIso(epochMilli: Long): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochMilli(epochMilli).atZone(zoneId),
        )

    private fun manifest(counts: Map<String, Int>): String = buildString {
        val exportedAt = Instant.ofEpochMilli(nowMillis())
        appendLine("{")
        appendLine("""  "format": "healthtrend-snapshot",""")
        appendLine("""  "formatVersion": $FORMAT_VERSION,""")
        appendLine("""  "databaseVersion": ${database.openHelper.readableDatabase.version},""")
        appendLine("""  "appVersion": ${jsonString(appVersion)},""")
        appendLine("""  "exportedAtEpochMillis": ${exportedAt.toEpochMilli()},""")
        appendLine("""  "exportedAtLocalIso": "${localIso(exportedAt.toEpochMilli())}",""")
        appendLine("""  "timeZone": ${jsonString(zoneId.id)},""")
        appendLine("""  "rowCounts": {""")
        counts.entries.forEachIndexed { index, (table, count) ->
            val comma = if (index == counts.size - 1) "" else ","
            appendLine("""    ${jsonString(table)}: $count$comma""")
        }
        appendLine("  }")
        appendLine("}")
    }

    private fun readme(counts: Map<String, Int>): String = buildString {
        appendLine("HealthTrend data export")
        appendLine("=".repeat(24))
        appendLine()
        appendLine("Everything this app knows, as of the moment of export. It is a read:")
        appendLine("exporting does not modify, move or delete anything in the app.")
        appendLine()
        appendLine("Files")
        appendLine("-----")
        appendLine("readme.txt                this file")
        appendLine("manifest.json             format/app/database versions, time zone, row counts")
        appendLine("metric_definitions.csv    what each metric is: unit, reference range, display order")
        appendLine("metric_observations.csv   the measurements themselves")
        appendLine("nutrient_definitions.csv  nutrient metadata (unit, category, reference intake)")
        appendLine("food_items.csv            the food library")
        appendLine("food_nutrient_values.csv  per-food nutrient amounts, on each food's own basis")
        appendLine("meal_logs.csv             the food diary (what was eaten, when, how much)")
        appendLine()
        appendLine("Conventions")
        appendLine("-----------")
        appendLine("Encoding      UTF-8 with a byte-order mark, so spreadsheet software detects it.")
        appendLine("Timestamps    Both raw UTC epoch milliseconds and a local ISO-8601 string with offset.")
        appendLine("              The epoch column is the source of truth; the ISO column is for reading.")
        appendLine("Time zone     ${zoneId.id} (the zone the app was using at export time)")
        appendLine("CSV           RFC 4180: fields containing a comma, quote or newline are quoted, and an")
        appendLine("              embedded quote is doubled.")
        appendLine()
        appendLine("How the numbers fit together")
        appendLine("----------------------------")
        appendLine("Each food quotes its nutrients per its own reference amount, which is NOT always 100 g —")
        appendLine("some foods are per piece, per millilitre or per serving. That basis is the pair")
        appendLine("(reference_amount, reference_unit) in food_items.csv. A meal's contribution is therefore")
        appendLine()
        appendLine("    amount_per_reference * actual_amount / reference_amount")
        appendLine()
        appendLine("and never a division by a constant 100.")
        appendLine()
        appendLine("metric_observations holds both what was observed and what was derived: a row with")
        appendLine("source = nutrition_agg is a daily total the app computed from meal_logs, not something")
        appendLine("anyone measured. Deleting those rows loses nothing — they are rebuilt from the diary.")
        appendLine()
        appendLine("Nothing here is medical advice.")
        appendLine()
        appendLine("Row counts")
        appendLine("----------")
        counts.forEach { (table, count) -> appendLine("${table.padEnd(24)} $count") }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private companion object {
        const val FORMAT_VERSION = 1

        /** Excel and most spreadsheet software need this to read UTF-8 rather than the local page. */
        const val BYTE_ORDER_MARK = "\uFEFF"
    }

    /** A minimal RFC 4180 writer. */
    private class Csv(vararg columnNames: String) {
        private val header = columnNames.toList()
        private val rows = mutableListOf<List<Any?>>()

        fun add(vararg values: Any?) {
            rows += values.toList()
        }

        fun render(): String = buildString {
            append(BYTE_ORDER_MARK)
            append(encode(header))
            rows.forEach { row -> append(encode(row)) }
        }

        private fun encode(values: List<Any?>): String =
            values.joinToString(separator = ",", postfix = "\n") { value -> escape(value) }

        private fun escape(value: Any?): String {
            val text = when (value) {
                null -> ""
                is Double -> formatDouble(value)
                else -> value.toString()
            }
            val needsQuoting = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (!needsQuoting) return text
            return '"' + text.replace("\"", "\"\"") + '"'
        }

        /**
         * Doubles print without the exponent or the trailing `.0` that `toString` gives them.
         *
         * `1.0E-5` and `130.0` are both valid CSV but both are awkward to read in a column, and the
         * first is misread by some tools as text.
         */
        private fun formatDouble(value: Double): String = when {
            value.isNaN() -> ""
            value.isInfinite() -> value.toString()
            value == value.toLong().toDouble() -> value.toLong().toString()
            else -> value.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }
}
