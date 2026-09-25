package antoni.kalorie.core.nutritionlabelrecognition

import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.macrokit.energyKJFromMacros
import antoni.kalorie.textkit.foldDiacritics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object NutritionLabelParser {

    // MARK: - Properties

    private const val COLUMN_TOLERANCE = 0.15
    private const val ROW_OVERLAP_THRESHOLD = 0.4
    private const val ENERGY_TOLERANCE_RATIO = 0.05
    private const val DERIVED_ENERGY_TOLERANCE_RATIO = 0.15
    private const val MAX_SUMMED_MACROS = 100.0

    private val weightKeywords = listOf("hmotnost", "netto", "obsah", "net weight", "hmotnosc")

    private val nutritionSectionKeywords = listOf(
        "výživové údaje", "vyzivove udaje", "wartość odżywcza", "wartosc odzywcza",
        "nährwertangaben", "naehrwertangaben", "nutrition information", "nutritional information", "nutrition facts",
    )

    private enum class LabelField { ENERGY, FAT, SATURATES, CARBOHYDRATE, SUGARS, FIBER, PROTEIN, SALT }

    private val keywords: Map<LabelField, List<String>> = mapOf(
        LabelField.ENERGY to listOf(
            "energeticka hodnota", "energetická hodnota", "energie", "energia",
            "wartość energetyczna", "wartosc energetyczna", "brennwert", "energy",
        ),
        LabelField.FAT to listOf("tuky", "tłuszcz", "tluszcz", "fett", "fat"),
        LabelField.SATURATES to listOf(
            "z toho nasycene", "z toho nasycené", "nasycené mastné", "nasycene mastne",
            "w tym kwasy nasycone", "davon gesättigte", "davon gesattigte", "of which saturates", "saturated fat", "saturates",
        ),
        LabelField.CARBOHYDRATE to listOf("sacharidy", "węglowodany", "weglowodany", "kohlenhydrate", "carbohydrate"),
        LabelField.SUGARS to listOf("z toho cukry", "cukry", "davon zucker", "of which sugars", "sugars"),
        LabelField.FIBER to listOf("vláknina", "vlaknina", "błonnik", "blonnik", "ballaststoffe", "fibre", "fiber"),
        LabelField.PROTEIN to listOf("bílkoviny", "bilkoviny", "białko", "bialko", "eiweiß", "eiweiss", "protein"),
        LabelField.SALT to listOf("sůl", "sul", "sól", "sól", "salz", "salt"),
    )

    private val weightRegex = Regex("([0-9]+[.,]?[0-9]*)\\s*(kg|g|ml|l)\\b")
    private val energyRegex = Regex("([0-9]+[.,]?[0-9]*)\\s*(kj|kcal)")
    private val thousandsRegex = Regex("[0-9] [0-9]")

    // MARK: - Functions

    fun parse(lines: List<RecognizedTextLine>): NutritionLabelReading {
        val weightResult = packageWeight(lines)
        var reading = NutritionLabelReading(weightOfProduct = weightResult?.first)

        val header = perHundredHeader(lines)
        if (header != null) {
            for (row in groupIntoRows(lines)) {
                reading = apply(row, header.first, reading)
            }
        }

        reading = if (hasNoMacros(reading) && hasNutritionContext(lines)) {
            applyingConsistencyChecks(parseLinear(lines)).copy(weightOfProduct = reading.weightOfProduct)
        } else {
            applyingConsistencyChecks(reading)
        }

        reading = reading.copy(measure = header?.second ?: linearMeasure(lines) ?: weightResult?.second)

        return derivingUnsaturated(reading)
    }

    // MARK: - Measure

    private fun linearMeasure(lines: List<RecognizedTextLine>): FoodMeasure? {
        val compact = lines.joinToString(" ") { it.text }.lowercase().replace(" ", "")
        val hasMl = compact.contains("100ml")
        val hasG = compact.contains("100g")
        if (hasMl == hasG) return null
        return if (hasMl) FoodMeasure.MILLILITRES else FoodMeasure.GRAMS
    }

    private fun hasNoMacros(reading: NutritionLabelReading): Boolean =
        reading.energyKJ == null &&
            reading.fat == null &&
            reading.carbohydrate == null &&
            reading.protein == null &&
            reading.salt == null

    private fun hasNutritionContext(lines: List<RecognizedTextLine>): Boolean {
        val text = lines.joinToString(" ") { it.text }.lowercase()
        if (nutritionSectionKeywords.any { text.contains(it) }) return true
        val compact = text.replace(" ", "")
        return compact.contains("100g") || compact.contains("100ml")
    }

    // MARK: - Linear format

    // Fallback for EU 1169/2011's linear declaration format on small packages, which has no column
    // geometry: each field's keyword marks where its value starts, the next field's keyword where it ends.
    private fun parseLinear(lines: List<RecognizedTextLine>): NutritionLabelReading {
        val fullText = lines.joinToString(" ") { it.text }.lowercase()

        // The ingredients list often repeats a field's keyword before the real value does, so the
        // scan is anchored to the nutrition section itself when it can be found.
        val sectionEnd = nutritionSectionKeywords
            .mapNotNull { keyword -> fullText.indexOf(keyword).takeIf { it >= 0 }?.let { it to it + keyword.length } }
            .minByOrNull { it.first }
            ?.second
        val lower = if (sectionEnd != null) fullText.substring(sectionEnd) else fullText

        data class Match(val field: LabelField, val start: Int, val end: Int)

        val matches = LabelField.entries.mapNotNull { field ->
            val firstMatch = (keywords[field] ?: emptyList())
                .mapNotNull { keyword -> lower.indexOf(keyword).takeIf { it >= 0 }?.let { it to it + keyword.length } }
                .minByOrNull { it.first }
                ?: return@mapNotNull null
            Match(field, firstMatch.first, firstMatch.second)
        }.sortedBy { it.start }

        var reading = NutritionLabelReading()
        matches.forEachIndexed { index, match ->
            val windowEnd = if (index + 1 < matches.size) matches[index + 1].start else lower.length
            if (match.end >= windowEnd) return@forEachIndexed
            val window = lower.substring(match.end, windowEnd)
            reading = when (match.field) {
                LabelField.ENERGY -> {
                    val (kJ, kcal) = energyValues(window)
                    reading.copy(energyKJ = kJ, caloriesPerHundredGrams = kcal)
                }
                LabelField.FAT -> reading.copy(fat = numbers(window).firstOrNull())
                LabelField.SATURATES -> reading.copy(fatSaturated = numbers(window).firstOrNull())
                LabelField.CARBOHYDRATE -> reading.copy(carbohydrate = numbers(window).firstOrNull())
                LabelField.SUGARS -> reading.copy(carbohydratePureSugar = numbers(window).firstOrNull())
                LabelField.FIBER -> reading.copy(fiber = numbers(window).firstOrNull())
                LabelField.PROTEIN -> reading.copy(protein = numbers(window).firstOrNull())
                LabelField.SALT -> reading.copy(salt = numbers(window).firstOrNull())
            }
        }
        return reading
    }

    // MARK: - Row grouping and column detection

    private fun groupIntoRows(lines: List<RecognizedTextLine>): List<List<RecognizedTextLine>> {
        val sorted = lines.sortedByDescending { it.boundingBox.midY }
        val rows = mutableListOf<MutableList<RecognizedTextLine>>()
        for (line in sorted) {
            val last = rows.lastOrNull()
            if (last != null && verticallyOverlaps(line, last)) last.add(line) else rows.add(mutableListOf(line))
        }
        return rows.map { row -> row.sortedBy { it.boundingBox.minX } }
    }

    // Compared against the row's first (seed) line, not the union of every line already in it: a
    // row's accumulated box would otherwise grow with each merge and absorb an unrelated neighbour.
    private fun verticallyOverlaps(line: RecognizedTextLine, row: List<RecognizedTextLine>): Boolean {
        val anchor = row.firstOrNull() ?: return false
        val overlap = min(line.boundingBox.maxY, anchor.boundingBox.maxY) - max(line.boundingBox.minY, anchor.boundingBox.minY)
        val minHeight = min(line.boundingBox.height, anchor.boundingBox.height)
        if (minHeight <= 0) return false
        return overlap / minHeight > ROW_OVERLAP_THRESHOLD
    }

    private fun perHundredHeader(lines: List<RecognizedTextLine>): Pair<Double, FoodMeasure>? {
        val headers = lines.filter { isPerHundredHeader(it.text) }
        val header = headers.singleOrNull() ?: return null
        val folded = foldDiacritics(header.text.lowercase()).replace(" ", "")
        val measure = if (folded.contains("100ml")) FoodMeasure.MILLILITRES else FoodMeasure.GRAMS
        return header.boundingBox.midX to measure
    }

    private fun isPerHundredHeader(text: String): Boolean {
        val folded = foldDiacritics(text.lowercase()).replace(" ", "")
        return folded.contains("100g") || folded.contains("100ml")
    }

    private fun apply(row: List<RecognizedTextLine>, columnX: Double, reading: NutritionLabelReading): NutritionLabelReading {
        val label = row.firstOrNull() ?: return reading
        val field = matchedField(label.text) ?: return reading
        val valueLine = row.drop(1).minByOrNull { abs(it.boundingBox.midX - columnX) } ?: return reading
        if (abs(valueLine.boundingBox.midX - columnX) > COLUMN_TOLERANCE || !isPlausibleValueText(valueLine.text)) return reading

        return when (field) {
            LabelField.ENERGY -> {
                val (kJ, kcal) = energyValues(valueLine.text)
                reading.copy(energyKJ = kJ, caloriesPerHundredGrams = kcal)
            }
            LabelField.FAT -> reading.copy(fat = numbers(valueLine.text).firstOrNull())
            LabelField.SATURATES -> reading.copy(fatSaturated = numbers(valueLine.text).firstOrNull())
            LabelField.CARBOHYDRATE -> reading.copy(carbohydrate = numbers(valueLine.text).firstOrNull())
            LabelField.SUGARS -> reading.copy(carbohydratePureSugar = numbers(valueLine.text).firstOrNull())
            LabelField.FIBER -> reading.copy(fiber = numbers(valueLine.text).firstOrNull())
            LabelField.PROTEIN -> reading.copy(protein = numbers(valueLine.text).firstOrNull())
            LabelField.SALT -> reading.copy(salt = numbers(valueLine.text).firstOrNull())
        }
    }

    // Unsaturated fat is derived from fat minus saturates, so its own rows carry no field; left
    // alone they match "saturates" (unsaturates, nenasycené mastné) or "fat" (ungesättigte Fettsäuren).
    private val unsaturatedMarkers = listOf("unsaturate", "nenasycen", "nienasycon", "ungesättigt", "ungesattigt")

    private fun matchedField(label: String): LabelField? {
        val lower = label.lowercase()
        if (unsaturatedMarkers.any { lower.contains(it) }) return null
        return LabelField.entries
            .mapNotNull { field ->
                val longest = keywords[field].orEmpty().filter { lower.contains(it) }.maxOfOrNull { it.length }
                longest?.let { field to it }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    // A nearest-to-column candidate is picked by geometry alone, which cannot tell a value cell from
    // a neighbouring label fragment ("Omega 3 mastné kyseliny" read as a "3"). Requiring the
    // candidate to be digits, punctuation and known units rejects such prose.
    private fun isPlausibleValueText(text: String): Boolean {
        var remainder = text.lowercase()
        for (unit in listOf("kcal", "kj", "ml", "g", "%")) {
            remainder = remainder.replace(unit, "")
        }
        val allowed = "0123456789.,<≤/| "
        return remainder.all { it in allowed }
    }

    // MARK: - Package weight

    private fun packageWeight(lines: List<RecognizedTextLine>): Pair<Double, FoodMeasure>? {
        val candidates = lines.mapNotNull { line ->
            val folded = foldDiacritics(line.text.lowercase())
            if (weightKeywords.none { folded.contains(it) }) return@mapNotNull null
            weightValue(line.text)?.let { return@mapNotNull scaledPackageWeight(it) }
            // The value is sometimes printed in a larger font directly under its label, which OCR
            // reports as two separate lines.
            val below = nearestLineBelow(line, lines) ?: return@mapNotNull null
            val match = weightValue(below.text) ?: return@mapNotNull null
            scaledPackageWeight(match)
        }
        return candidates.singleOrNull()
    }

    private fun scaledPackageWeight(match: Pair<Double, String>): Pair<Double, FoodMeasure> {
        val value = if (match.second == "kg" || match.second == "l") match.first * 1000 else match.first
        val measure = if (match.second == "ml" || match.second == "l") FoodMeasure.MILLILITRES else FoodMeasure.GRAMS
        return value to measure
    }

    private fun nearestLineBelow(line: RecognizedTextLine, lines: List<RecognizedTextLine>): RecognizedTextLine? =
        lines
            .filter { it.boundingBox.maxY <= line.boundingBox.minY }
            .maxByOrNull { it.boundingBox.maxY }

    private fun weightValue(text: String): Pair<Double, String>? {
        val match = weightRegex.find(text.lowercase()) ?: return null
        val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        return value to match.groupValues[2]
    }

    // MARK: - Number parsing

    // Czech (and Slovak/Polish) typography groups thousands with a space ("3 404 kJ" prints 3404).
    // Collapsing it unconditionally is safe: a genuine word boundary never has a digit on both sides.
    private fun joiningThousandsSeparators(text: String): String {
        var result = text
        while (true) {
            val match = thousandsRegex.find(result) ?: break
            result = result.replaceRange(match.range, match.value.replace(" ", ""))
        }
        return result
    }

    private fun numbers(text: String): List<Double> {
        val results = mutableListOf<Double>()
        var current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) current.toString().replace(",", ".").toDoubleOrNull()?.let { results.add(it) }
            current = StringBuilder()
        }
        for (char in joiningThousandsSeparators(text)) {
            if (char.isDigit() || char == ',' || char == '.') current.append(char) else flush()
        }
        flush()
        return results
    }

    private fun energyValues(text: String): Pair<Double?, Double?> {
        val lower = joiningThousandsSeparators(text.lowercase())
        var kJ: Double? = null
        var kcal: Double? = null
        for (match in energyRegex.findAll(lower)) {
            val value = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
            if (match.groupValues[2] == "kj") kJ = value else kcal = value
        }
        return kJ to kcal
    }

    // MARK: - Consistency checks

    private fun applyingConsistencyChecks(reading: NutritionLabelReading): NutritionLabelReading {
        var result = reading

        val kJ = result.energyKJ
        val kcal = result.caloriesPerHundredGrams
        if (kJ != null && kcal != null) {
            val expectedKJ = kcal * 4.184
            if (expectedKJ <= 0 || abs(kJ - expectedKJ) / expectedKJ > ENERGY_TOLERANCE_RATIO) {
                result = result.copy(energyKJ = null, caloriesPerHundredGrams = null)
            }
        }

        val saturates = result.fatSaturated
        val fatValue = result.fat
        if (saturates != null && fatValue != null && saturates > fatValue) result = result.copy(fatSaturated = null)

        val sugars = result.carbohydratePureSugar
        val carbsValue = result.carbohydrate
        if (sugars != null && carbsValue != null && sugars > carbsValue) result = result.copy(carbohydratePureSugar = null)

        val energy = result.energyKJ
        val fat = result.fat
        val carbs = result.carbohydrate
        val protein = result.protein
        if (energy != null && fat != null && carbs != null && protein != null) {
            val derivedKJ = energyKJFromMacros(fat = fat, carbohydrate = carbs, protein = protein)
            if (derivedKJ <= 0 || abs(energy - derivedKJ) / derivedKJ > DERIVED_ENERGY_TOLERANCE_RATIO) {
                result = result.copy(energyKJ = null, caloriesPerHundredGrams = null)
            }
        }

        val summed = listOfNotNull(result.fat, result.carbohydrate, result.protein, result.salt, result.fiber).sum()
        if (summed > MAX_SUMMED_MACROS) {
            result = result.copy(fat = null, carbohydrate = null, protein = null, salt = null, fiber = null)
        }

        return result
    }

    private fun derivingUnsaturated(reading: NutritionLabelReading): NutritionLabelReading {
        val fat = reading.fat
        val saturated = reading.fatSaturated
        if (reading.fatUnsaturatedFattyAcids == null && fat != null && saturated != null) {
            return reading.copy(fatUnsaturatedFattyAcids = max(0.0, fat - saturated))
        }
        return reading
    }
}
