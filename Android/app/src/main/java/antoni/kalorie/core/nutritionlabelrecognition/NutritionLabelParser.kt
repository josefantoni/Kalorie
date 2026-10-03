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
    private const val INFERRED_COLUMN_TOLERANCE = 0.08
    private const val MIN_INFERRED_COLUMN_CELLS = 3
    private const val ENERGY_TOLERANCE_RATIO = 0.05
    private const val DERIVED_ENERGY_TOLERANCE_RATIO = 0.15

    // 100 g plus the "<0,5 g" bounds and rounding that a near-pure fat or carbohydrate product can add up.
    private const val MAX_SUMMED_MACROS = 103.0
    private const val MAX_SINGLE_MACRO = 100.0
    private const val MAX_WORD_BOUNDARY_KEYWORD_LENGTH = 3
    private const val MIN_FUZZY_KEYWORD_LENGTH = 7
    private const val MAX_VALUE_CELL_HEIGHT_RATIO = 2.0
    private const val MIN_ROW_SHIFT_IN_CELL_HEIGHTS = 0.75
    private const val MAX_ROW_SHIFT_IN_CELL_HEIGHTS = 1.5

    private val weightKeywords = listOf("hmotnost", "netto", "obsah", "net weight", "hmotnosc")

    private val nutritionSectionKeywords = listOf(
        "výživové údaje", "vyzivove udaje", "wartość odżywcza", "wartosc odzywcza",
        "nährwertangaben", "naehrwertangaben", "nutrition information", "nutritional information", "nutrition facts",
    )

    private enum class LabelField { ENERGY, FAT, SATURATES, CARBOHYDRATE, SUGARS, FIBER, PROTEIN, SALT, UNLISTED }

    private val keywords: Map<LabelField, List<String>> = mapOf(
        LabelField.ENERGY to listOf(
            "energeticka hodnota",
            "energetická hodnota",
            "energie",
            "energia",
            "wartość energetyczna",
            "wartosc energetyczna",
            "brennwert",
            "energy",
        ),
        LabelField.FAT to listOf("tuky", "tłuszcz", "tluszcz", "fett", "fat"),
        LabelField.SATURATES to listOf(
            "z toho nasycene", "z toho nasycené", "nasycené mastné", "nasycene mastne",
            "w tym kwasy nasycone", "davon gesättigte", "davon gesattigte", "of which saturates", "saturated fat", "saturates",
        ),
        LabelField.CARBOHYDRATE to listOf("sacharidy", "węglowodany", "weglowodany", "kohlenhydrate", "carbohydrate"),
        LabelField.SUGARS to listOf("z toho cukry", "cukry", "davon zucker", "of which sugars", "sugars"),
        LabelField.FIBER to listOf("vláknina", "vlaknina", "błonnik", "blonnik", "ballaststoffe", "fibre", "fiber"),
        LabelField.PROTEIN to listOf("bílkoviny", "bilkoviny", "bielkoviny", "białko", "bialko", "eiweiß", "eiweiss", "protein"),
        LabelField.SALT to listOf("sůl", "sul", "sól", "sól", "soľ", "sol", "salz", "salt"),
        LabelField.UNLISTED to listOf("polyoly", "polyol", "polyols", "škrob", "skrob", "starch", "stärke", "skrobia"),
    )

    private val foldedKeywords: Map<LabelField, List<String>> = keywords.mapValues { (_, list) -> list.map { foldedLabelText(it) }.distinct() }

    private val weightRegex = Regex("([0-9]+[.,]?[0-9]*)\\s*(kg|g|ml|l)\\b")
    private val energyRegex = Regex("([0-9]+[.,]?[0-9]*)\\s*(kj|kcal)")
    private val thousandsRegex = Regex("[0-9] [0-9]")

    // MARK: - Functions

    fun parse(lines: List<RecognizedTextLine>): NutritionLabelReading {
        val packageMeasure = packageWeight(lines)?.second
        var reading = NutritionLabelReading()

        val header = perHundredHeader(lines)
        val columnX = header?.first ?: inferredValueColumnX(lines)
        if (columnX != null) {
            val cells = valueCellsBelowHeader(lines)
            val labels = lines
                .mapNotNull { line -> matchedField(line.text)?.let { line to it } }
                .sortedByDescending { it.first.boundingBox.midY }
            for ((field, valueLine) in assignments(labels, cells, columnX, rowShift(labels, cells, columnX))) {
                reading = applying(field, valueLine.text, reading)
            }
        }

        reading = if (hasNoMacros(reading) && hasNutritionContext(lines)) {
            applyingConsistencyChecks(parseLinear(lines))
        } else {
            applyingConsistencyChecks(reading)
        }

        reading = reading.copy(measure = header?.second ?: linearMeasure(lines) ?: packageMeasure)

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

    private fun hasNoMacros(reading: NutritionLabelReading): Boolean = reading.energyKJ == null &&
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

        val saturatesStarts = (keywords[LabelField.SATURATES] ?: emptyList()).flatMap { indicesOfKeyword(lower, it) }

        // "saturated fat" and "davon gesättigte Fettsäuren" carry the fat keyword inside the saturates
        // label; a fat keyword reached from a saturates keyword with no value in between is that label's tail.
        fun isTailOfSaturatesLabel(start: Int) = saturatesStarts.any { it <= start && lower.substring(it, start).none { char -> char.isDigit() } }

        val matches = LabelField.entries.mapNotNull { field ->
            val firstMatch = (keywords[field] ?: emptyList())
                .flatMap { keyword -> indicesOfKeyword(lower, keyword).map { it to it + keyword.length } }
                .filter { field != LabelField.FAT || !isTailOfSaturatesLabel(it.first) }
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
                LabelField.UNLISTED -> reading
            }
        }
        return reading
    }

    // A keyword this short ("sul", "fat") also sits inside longer words such as "sulphites" or "fatty".
    private fun indicesOfKeyword(text: String, keyword: String): List<Int> {
        val found = mutableListOf<Int>()
        var index = text.indexOf(keyword)
        while (index >= 0) {
            val end = index + keyword.length
            val isWholeWord = (index == 0 || !text[index - 1].isLetter()) && (end == text.length || !text[end].isLetter())
            if (keyword.length > MAX_WORD_BOUNDARY_KEYWORD_LENGTH || isWholeWord) found.add(index)
            index = text.indexOf(keyword, index + 1)
        }
        return found
    }

    // MARK: - Row grouping and column detection

    // OCR can split the header into "na" "100" "g"; the bare "100" is all digits and would pass for a value, but a
    // genuine "100 g" (a pure fat, say) sits below the top value cell, a header above it.
    private fun valueCellsBelowHeader(lines: List<RecognizedTextLine>): List<RecognizedTextLine> {
        val cells = valueCells(lines)
        val topmostValueMidY = cells.filterNot { isPerHundredHeader(it.text) }.maxOfOrNull { it.boundingBox.midY } ?: return cells
        return cells.filter { !isPerHundredHeader(it.text) || it.boundingBox.midY <= topmostValueMidY }
    }

    // Text printed sideways along the table edge (a batch number, say) is a box several rows tall; it
    // overlaps every row it crosses and would take a real value's place.
    private fun valueCells(lines: List<RecognizedTextLine>): List<RecognizedTextLine> {
        val cells = lines.filter { isValueCell(it) }
        val medianHeight = medianHeight(cells) ?: return cells
        return cells.filter { it.boundingBox.height <= medianHeight * MAX_VALUE_CELL_HEIGHT_RATIO }
    }

    private fun medianHeight(lines: List<RecognizedTextLine>): Double? = lines.map { it.boundingBox.height }.sorted().getOrNull(lines.size / 2)

    private fun isValueCell(line: RecognizedTextLine): Boolean = line.text.any { it.isDigit() } && isPlausibleValueText(line.text)

    private fun overlapsVertically(first: RecognizedTextLine, second: RecognizedTextLine): Boolean {
        val overlap = min(first.boundingBox.maxY, second.boundingBox.maxY) - max(first.boundingBox.minY, second.boundingBox.minY)
        val minHeight = min(first.boundingBox.height, second.boundingBox.height)
        if (minHeight <= 0) return false
        return overlap / minHeight > ROW_OVERLAP_THRESHOLD
    }

    // Without a usable "per 100 g" header, a single column of values still identifies the per-100 g
    // column; two columns (per 100 g and per portion) are told apart by the header only.
    private fun inferredValueColumnX(lines: List<RecognizedTextLine>): Double? {
        val cells = valueCells(lines)
        if (cells.size < MIN_INFERRED_COLUMN_CELLS) return null
        val median = cells.map { it.boundingBox.midX }.sorted()[cells.size / 2]
        val inColumn = cells.count { abs(it.boundingBox.midX - median) <= INFERRED_COLUMN_TOLERANCE }
        return if (inColumn * 2 > cells.size) median else null
    }

    private fun perHundredHeader(lines: List<RecognizedTextLine>): Pair<Double, FoodMeasure?>? {
        val valueCells = valueCells(lines)
        val headers = lines.filter { header -> isPerHundredHeader(header.text) && hasValueCellBelow(header, valueCells) }
        val header = headers.singleOrNull() ?: return null
        val folded = foldedHeaderText(header.text)
        val measure = when {
            folded.contains("100ml") -> FoodMeasure.MILLILITRES
            folded.contains("100g") -> FoodMeasure.GRAMS
            else -> null
        }
        return header.boundingBox.midX to measure
    }

    // A sentence from the ingredients ("…17 g na 100 g…") also contains "100 g", but no value cell sits
    // under it, so only the genuine column header qualifies.
    private fun hasValueCellBelow(header: RecognizedTextLine, valueCells: List<RecognizedTextLine>): Boolean = valueCells.any {
        it.boundingBox.maxY <= header.boundingBox.minY && abs(it.boundingBox.midX - header.boundingBox.midX) <= COLUMN_TOLERANCE
    }

    private fun foldedHeaderText(text: String): String = foldDiacritics(text.lowercase()).replace(" ", "").replace('q', 'g')

    private fun isPerHundredHeader(text: String): Boolean {
        val folded = foldedHeaderText(text)
        return folded.contains("100g") || folded.contains("100ml") || folded.endsWith("100")
    }

    // Rows run top to bottom, so a label takes the highest value cell it overlaps that no label above has
    // taken; a skewed photo makes the neighbouring row's value overlap just as much.
    private fun assignments(
        labels: List<Pair<RecognizedTextLine, LabelField>>,
        cells: List<RecognizedTextLine>,
        columnX: Double,
        shift: Double,
    ): List<Pair<LabelField, RecognizedTextLine>> {
        val unusedCells = cells.toMutableList()
        return labels.mapNotNull { (label, field) ->
            val shifted = label.copy(boundingBox = label.boundingBox.copy(y = label.boundingBox.y + shift))
            val valueLine = unusedCells
                .filter { it.boundingBox.minX > label.boundingBox.minX && overlapsVertically(shifted, it) }
                .maxByOrNull { it.boundingBox.midY }
                ?: return@mapNotNull null
            if (abs(valueLine.boundingBox.midX - columnX) > COLUMN_TOLERANCE) return@mapNotNull null
            unusedCells.remove(valueLine)
            field to valueLine
        }
    }

    // A tilted photo or a curved jar lifts the value column against the label column by up to a whole row, and each
    // label then overlaps its neighbour's value. Energy is the one row recognisable from both sides, by its keyword
    // and by the kJ or kcal in its value, so its offset is taken as the whole table's.
    private fun rowShift(
        labels: List<Pair<RecognizedTextLine, LabelField>>,
        cells: List<RecognizedTextLine>,
        columnX: Double,
    ): Double {
        val energyLabel = labels.firstOrNull { it.second == LabelField.ENERGY }?.first ?: return 0.0
        val medianHeight = medianHeight(cells) ?: return 0.0
        val shift = cells
            .filter { abs(it.boundingBox.midX - columnX) <= COLUMN_TOLERANCE && energyRegex.containsMatchIn(it.text.lowercase()) }
            .map { it.boundingBox.midY - energyLabel.boundingBox.midY }
            .minByOrNull { abs(it) }
            ?: return 0.0
        return if (abs(shift) / medianHeight in MIN_ROW_SHIFT_IN_CELL_HEIGHTS..MAX_ROW_SHIFT_IN_CELL_HEIGHTS) shift else 0.0
    }

    private fun applying(field: LabelField, text: String, reading: NutritionLabelReading): NutritionLabelReading = when (field) {
        LabelField.ENERGY -> {
            val (kJ, kcal) = energyValues(text)
            reading.copy(energyKJ = kJ, caloriesPerHundredGrams = kcal)
        }
        LabelField.FAT -> reading.copy(fat = numbers(text).firstOrNull())
        LabelField.SATURATES -> reading.copy(fatSaturated = numbers(text).firstOrNull())
        LabelField.CARBOHYDRATE -> reading.copy(carbohydrate = numbers(text).firstOrNull())
        LabelField.SUGARS -> reading.copy(carbohydratePureSugar = numbers(text).firstOrNull())
        LabelField.FIBER -> reading.copy(fiber = numbers(text).firstOrNull())
        LabelField.PROTEIN -> reading.copy(protein = numbers(text).firstOrNull())
        LabelField.SALT -> reading.copy(salt = numbers(text).firstOrNull())
        LabelField.UNLISTED -> reading
    }

    // Unsaturated fat is derived from fat minus saturates, so its own rows carry no field; left
    // alone they match "saturates" (unsaturates, nenasycené mastné) or "fat" (ungesättigte Fettsäuren).
    // Like polyols or starch, such a row still takes its own value, or every row below it reads its neighbour's.
    private val unsaturatedMarkers = listOf("unsaturate", "nenasycen", "nienasycon", "ungesättigt", "ungesattigt")

    // OCR misreads one letter of a label as often as a digit of a value ("Vaknina", "Eneraie"), so a long
    // keyword one edit away still matches, but only when no keyword matches exactly.
    private fun matchedField(label: String): LabelField? {
        val folded = foldedLabelText(label)
        if (unsaturatedMarkers.any { folded.contains(foldedLabelText(it)) }) return LabelField.UNLISTED
        return longestMatchingField(folded) { text, keyword -> indicesOfKeyword(text, keyword).isNotEmpty() }
            ?: longestMatchingField(folded) { text, keyword -> keyword.length >= MIN_FUZZY_KEYWORD_LENGTH && containsWithinOneEdit(text, keyword) }
    }

    private fun longestMatchingField(text: String, matches: (String, String) -> Boolean): LabelField? = LabelField.entries
        .mapNotNull { field ->
            val longest = foldedKeywords[field].orEmpty().filter { matches(text, it) }.maxOfOrNull { it.length }
            longest?.let { field to it }
        }
        .maxByOrNull { it.second }
        ?.first

    // "ü" and "ľ" are OCR's readings of "ů" and the Slovak "ľ" in "soľ"; "q" is a misread "g".
    private fun foldedLabelText(text: String): String = foldDiacritics(text.lowercase()).replace('ü', 'u').replace('ľ', 'l').replace('q', 'g')

    private fun containsWithinOneEdit(text: String, keyword: String): Boolean = text.indices
        .filter { it == 0 || !text[it - 1].isLetter() }
        .any { start ->
            (keyword.length - 1..keyword.length + 1).any { length ->
                start + length <= text.length && isWithinOneEdit(text.substring(start, start + length), keyword)
            }
        }

    private fun isWithinOneEdit(first: String, second: String): Boolean {
        if (abs(first.length - second.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < first.length && j < second.length) {
            if (first[i] == second[j]) {
                i++
                j++
                continue
            }
            edits++
            if (edits > 1) return false
            when {
                first.length > second.length -> i++
                first.length < second.length -> j++
                else -> {
                    i++
                    j++
                }
            }
        }
        return edits + (first.length - i) + (second.length - j) <= 1
    }

    // A nearest-to-column candidate is picked by geometry alone, which cannot tell a value cell from
    // a neighbouring label fragment ("Omega 3 mastné kyseliny" read as a "3"). Requiring the
    // candidate to be digits, punctuation and known units rejects such prose.
    private fun isPlausibleValueText(text: String): Boolean {
        var remainder = text.lowercase()
        for (unit in listOf("kcal", "kj", "ml", "g", "%")) {
            remainder = remainder.replace(unit, "")
        }
        val allowed = "0123456789.,<≤/|() q_"
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

    private fun nearestLineBelow(line: RecognizedTextLine, lines: List<RecognizedTextLine>): RecognizedTextLine? = lines
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
            if (current.isNotEmpty()) {
                val token = current.toString().trimEnd(',', '.').replace(",", ".")
                // "0,10" read as "010": a whole number never starts with a zero.
                val restored = if (token.length > 1 && token.startsWith("0") && token.all { it.isDigit() }) "0.${token.drop(1)}" else token
                restored.toDoubleOrNull()?.let { results.add(it) }
            }
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
        // A value that lost its decimal comma ("15 g" read as "150") is impossible per 100 g; dropping it first
        // keeps it from breaking the cross-field checks below and wiping the correct values.
        var result = reading.copy(
            fat = reading.fat?.takeIf { it <= MAX_SINGLE_MACRO },
            fatSaturated = reading.fatSaturated?.takeIf { it <= MAX_SINGLE_MACRO },
            carbohydrate = reading.carbohydrate?.takeIf { it <= MAX_SINGLE_MACRO },
            carbohydratePureSugar = reading.carbohydratePureSugar?.takeIf { it <= MAX_SINGLE_MACRO },
            protein = reading.protein?.takeIf { it <= MAX_SINGLE_MACRO },
            salt = reading.salt?.takeIf { it <= MAX_SINGLE_MACRO },
            fiber = reading.fiber?.takeIf { it <= MAX_SINGLE_MACRO },
        )

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

        // kJ and kcal that agree are two independent reads already; the general factors would also reject a
        // correct energy whenever polyols or fibre carry less energy than the carbohydrate they are counted in.
        val energy = result.energyKJ
        val fat = result.fat
        val carbs = result.carbohydrate
        val protein = result.protein
        if (energy != null && result.caloriesPerHundredGrams == null && fat != null && carbs != null && protein != null) {
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
