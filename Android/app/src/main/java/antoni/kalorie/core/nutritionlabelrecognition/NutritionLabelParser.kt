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
    private const val MIN_SEQUENTIAL_ROWS = 4
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
    private const val MAX_SPARSE_HEADER_COLUMN_CELLS = 2
    private const val CONTINUATION_EDGE_TOLERANCE = 0.03
    private const val CONTINUATION_GAP_IN_LINE_HEIGHTS = 0.25
    private const val MAX_ENERGY_COMPANION_DISTANCE_IN_CELL_HEIGHTS = 1.5

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
            "w tym kwasy nasycone", "kwasy tłuszczowe nasycone", "davon gesättigte", "davon gesattigte", "of which saturates", "saturated fat", "saturates",
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
    private val misreadUnitRegex = Regex("(^|\\s)9\\s+(?=[0-9])")
    private val thousandsRegex = Regex("[0-9] [0-9]{3}(?![0-9])")

    // MARK: - Functions

    fun parse(recognizedLines: List<RecognizedTextLine>): NutritionLabelReading {
        val lines = uprightLines(recognizedLines)
        val packageMeasure = packageWeight(lines)?.second
        var reading = NutritionLabelReading()

        val header = perHundredHeader(lines)
        val columnX = valueColumnX(header?.first, lines)
        if (columnX != null) {
            val cells = valueCellsBelowHeader(lines)
            val labels = mergingWrappedLabels(
                lines
                    .mapNotNull { line -> matchedField(line.text)?.let { line to it } }
                    .sortedByDescending { it.first.boundingBox.midY },
            )
            val extendedLabels = extendingOverContinuationLines(labels, lines)
            val shift = rowShift(extendedLabels, cells, columnX)
            val assigned = assignments(extendedLabels, cells, columnX, shift)
            for ((field, valueLine) in assigned) {
                val companion = if (field == LabelField.ENERGY) energyCompanion(valueLine, cells, assigned.map { it.second }) else null
                reading = applying(field, listOfNotNull(valueLine.text, companion?.text).joinToString(" "), reading)
            }
        }

        reading = if (hasNoMacros(reading) && hasNutritionContext(lines)) {
            val sequential = parseSequential(lines)
            applyingConsistencyChecks(if (hasNoMacros(sequential)) parseLinear(lines) else sequential)
        } else {
            applyingConsistencyChecks(reading)
        }

        reading = reading.copy(measure = header?.second ?: linearMeasure(lines) ?: packageMeasure)

        return derivingUnsaturated(reading)
    }

    // MARK: - Measure

    // Vision and ML Kit read sideways and upside-down text as well as upright, but the rows then run along another
    // axis or bottom to top and every pairing below goes wrong. The quarter turn most text is printed at is undone
    // for every box; normalised boxes simply swap and mirror their axes, so no image size is needed.
    private fun uprightLines(lines: List<RecognizedTextLine>): List<RecognizedTextLine> {
        val lengthByQuarterTurn = IntArray(4)
        for (line in lines) lengthByQuarterTurn[quarterTurns(line.angleDegrees)] += line.text.length
        val dominant = lengthByQuarterTurn.indices.maxByOrNull { lengthByQuarterTurn[it] } ?: return lines
        if (dominant == 0 || lengthByQuarterTurn[dominant] <= lengthByQuarterTurn[0]) return lines
        return lines.map { line -> RecognizedTextLine(text = line.text, boundingBox = turnedUpright(line.boundingBox, dominant)) }
    }

    private fun quarterTurns(angleDegrees: Double): Int = ((Math.round(angleDegrees / 90).toInt() % 4) + 4) % 4

    // The text reads counter-clockwise by the given quarter turns, so the page is turned clockwise by as many.
    private fun turnedUpright(box: NormalizedRect, quarterTurns: Int): NormalizedRect = when (quarterTurns) {
        1 -> NormalizedRect(box.minY, 1 - box.maxX, box.height, box.width)
        2 -> NormalizedRect(1 - box.maxX, 1 - box.maxY, box.width, box.height)
        else -> NormalizedRect(1 - box.maxY, box.minX, box.height, box.width)
    }

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
    // A scanner that hands over a whole paragraph gives every line the same width, so no value lies "to the right of"
    // its label, yet the rows still come in print order: labels and values each run top to bottom. From the first
    // label down, the longest stretch that ends on a value and holds exactly as many labels as values is paired in
    // that order; any other count would shift every pair after the odd one out, and a fragment without a keyword
    // ("z toho / w tym") or a stray word between the rows must not end the table.
    private fun parseSequential(lines: List<RecognizedTextLine>): NutritionLabelReading {
        val ordered = lines.sortedByDescending { it.boundingBox.midY }
        val start = ordered.indexOfFirst { matchedField(it.text) != null }
        if (start < 0) return NutritionLabelReading()
        for (end in ordered.indices.reversed()) {
            if (end <= start || !isValueCell(ordered[end])) continue
            val stretch = ordered.subList(start, end + 1)
            val fields = stretch
                .filter { !isValueCell(it) && matchedField(it.text) != null }
                .flatMap { fieldsInOrder(it.text) }
                .fold(mutableListOf<LabelField>()) { result, field ->
                    if (result.lastOrNull() != field) result.add(field)
                    result
                }
            val values = stretch.filter { isValueCell(it) }
            if (fields.size != values.size || fields.size < MIN_SEQUENTIAL_ROWS) continue
            var reading = NutritionLabelReading()
            for ((field, value) in fields.zip(values)) {
                reading = applying(field, value.text, reading)
            }
            return reading
        }
        return NutritionLabelReading()
    }

    // Several labels can share one line ("Sacharidy / z toho cukry / Bílkoviny"); a keyword inside a longer label of
    // another field ("fat" in "saturated fat") is that label's own tail.
    private fun fieldsInOrder(label: String): List<LabelField> {
        val folded = foldedLabelText(label)
        if (unsaturatedMarkers.any { folded.contains(foldedLabelText(it)) }) return listOf(LabelField.UNLISTED)
        class KeywordMatch(val field: LabelField, val start: Int, val end: Int)
        val matches = LabelField.entries.flatMap { field ->
            foldedKeywords[field].orEmpty().flatMap { keyword ->
                indicesOfKeyword(folded, keyword).map { KeywordMatch(field, it, it + keyword.length) }
            }
        }
        return matches
            .filter { match ->
                matches.none { other ->
                    other.field != match.field &&
                        other.end - other.start > match.end - match.start &&
                        other.start <= match.start &&
                        other.end >= match.end
                }
            }
            .sortedBy { it.start }
            .map { it.field }
    }

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

    private fun verticalOverlapRatio(first: NormalizedRect, second: NormalizedRect): Double? {
        val overlap = min(first.maxY, second.maxY) - max(first.minY, second.minY)
        val minHeight = min(first.height, second.height)
        if (minHeight <= 0) return null
        val ratio = overlap / minHeight
        return if (ratio > ROW_OVERLAP_THRESHOLD) ratio else null
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

    // A header printed over the label column rather than the values has next to nothing under it, while the values
    // form a column of their own; two real columns (per 100 g, per portion) both stay well populated.
    private fun valueColumnX(headerX: Double?, lines: List<RecognizedTextLine>): Double? {
        if (headerX == null) return inferredValueColumnX(lines)
        val inferredX = inferredValueColumnX(lines) ?: return headerX
        val cells = valueCells(lines)
        fun cellCount(x: Double): Int = cells.count { abs(it.boundingBox.midX - x) <= COLUMN_TOLERANCE }
        return if (cellCount(headerX) <= MAX_SPARSE_HEADER_COLUMN_CELLS && cellCount(inferredX) >= MIN_INFERRED_COLUMN_CELLS) inferredX else headerX
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

    // Rows run top to bottom and each holds one value, so labels and cells are matched in order, skipping any that
    // find no partner: as many pairs as possible, and among those the highest cells and the earliest labels, which is
    // where a label whose box also overlaps the row below belongs. Picking the best cell for each label on its own lets
    // a label take a value another label needs and shifts every row below it.
    private fun assignments(
        labels: List<Pair<RecognizedTextLine, LabelField>>,
        cells: List<RecognizedTextLine>,
        columnX: Double,
        shift: Double,
    ): List<Pair<LabelField, RecognizedTextLine>> {
        val columnCells = cells
            .filter { abs(it.boundingBox.midX - columnX) <= COLUMN_TOLERANCE }
            .sortedByDescending { it.boundingBox.midY }
        if (labels.isEmpty() || columnCells.isEmpty()) return emptyList()

        val isPair = labels.map { (label, _) ->
            val box = label.boundingBox.copy(y = label.boundingBox.y + shift)
            columnCells.map { cell -> cell.boundingBox.minX > label.boundingBox.minX && verticalOverlapRatio(box, cell.boundingBox) != null }
        }
        val pairWeight = (labels.size + 1) * (labels.size + 1) * (columnCells.size + 1)
        val best = Array(labels.size + 1) { IntArray(columnCells.size + 1) }
        for (labelIndex in 1..labels.size) {
            for (cellIndex in 1..columnCells.size) {
                best[labelIndex][cellIndex] = max(best[labelIndex - 1][cellIndex], best[labelIndex][cellIndex - 1])
                if (isPair[labelIndex - 1][cellIndex - 1]) {
                    val paired = best[labelIndex - 1][cellIndex - 1] + pairWeight - cellIndex * (labels.size + 1) - labelIndex
                    best[labelIndex][cellIndex] = max(best[labelIndex][cellIndex], paired)
                }
            }
        }

        val result = mutableListOf<Pair<LabelField, RecognizedTextLine>>()
        var labelIndex = labels.size
        var cellIndex = columnCells.size
        while (labelIndex > 0 && cellIndex > 0) {
            val paired = best[labelIndex - 1][cellIndex - 1] + pairWeight - cellIndex * (labels.size + 1) - labelIndex
            when {
                isPair[labelIndex - 1][cellIndex - 1] && best[labelIndex][cellIndex] == paired -> {
                    result.add(labels[labelIndex - 1].second to columnCells[cellIndex - 1])
                    labelIndex--
                    cellIndex--
                }
                best[labelIndex][cellIndex] == best[labelIndex - 1][cellIndex] -> labelIndex--
                else -> cellIndex--
            }
        }
        return result.reversed()
    }

    // A multilingual label wraps onto two lines that both name the same field. They overlap vertically and read
    // differently, unlike two rows of the table that repeat the same label, and would otherwise each take a value.
    private fun mergingWrappedLabels(labels: List<Pair<RecognizedTextLine, LabelField>>): List<Pair<RecognizedTextLine, LabelField>> {
        val merged = mutableListOf<Pair<RecognizedTextLine, LabelField>>()
        for (label in labels) {
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.second == label.second &&
                label.second != LabelField.UNLISTED &&
                previous.first.text != label.first.text &&
                previous.first.boundingBox.minY < label.first.boundingBox.maxY
            ) {
                val first = previous.first.boundingBox
                val second = label.first.boundingBox
                val minX = min(first.minX, second.minX)
                val minY = min(first.minY, second.minY)
                val union = NormalizedRect(minX, minY, max(first.maxX, second.maxX) - minX, max(first.maxY, second.maxY) - minY)
                merged[merged.size - 1] = previous.first.copy(boundingBox = union) to label.second
            } else {
                merged.add(label)
            }
        }
        return merged
    }

    // A label that wraps ("z toho nasycené" / "mastné kyseliny") names its field on the first line only, and that line
    // alone may barely touch the value printed between the two. Lines under it that name no field, start at the same
    // edge and touch it belong to the label.
    private fun extendingOverContinuationLines(
        labels: List<Pair<RecognizedTextLine, LabelField>>,
        lines: List<RecognizedTextLine>,
    ): List<Pair<RecognizedTextLine, LabelField>> {
        val candidates = lines.filter { matchedField(it.text) == null && !isValueCell(it) }
        return labels.map { (label, field) ->
            val box = label.boundingBox
            val continuation = candidates.filter { candidate ->
                val other = candidate.boundingBox
                abs(other.minX - box.minX) <= CONTINUATION_EDGE_TOLERANCE &&
                    other.midY < box.midY &&
                    other.maxY >= box.minY - box.height * CONTINUATION_GAP_IN_LINE_HEIGHTS
            }
            if (continuation.isEmpty()) return@map label to field
            val minX = min(box.minX, continuation.minOf { it.boundingBox.minX })
            val minY = min(box.minY, continuation.minOf { it.boundingBox.minY })
            val maxX = max(box.maxX, continuation.maxOf { it.boundingBox.maxX })
            val maxY = max(box.maxY, continuation.maxOf { it.boundingBox.maxY })
            label.copy(boundingBox = NormalizedRect(minX, minY, maxX - minX, maxY - minY)) to field
        }
    }

    // The kJ and kcal of one energy row are sometimes printed as two cells, one under the other.
    private fun energyCompanion(cell: RecognizedTextLine, cells: List<RecognizedTextLine>, used: List<RecognizedTextLine>): RecognizedTextLine? {
        val (kJ, kcal) = energyValues(cell.text)
        if ((kJ == null) == (kcal == null)) return null
        return cells
            .filter { candidate ->
                val (otherKJ, otherKcal) = energyValues(candidate.text)
                (if (kJ == null) otherKJ else otherKcal) != null &&
                    candidate !in used &&
                    abs(candidate.boundingBox.midX - cell.boundingBox.midX) <= cell.boundingBox.width &&
                    abs(candidate.boundingBox.midY - cell.boundingBox.midY) <= cell.boundingBox.height * MAX_ENERGY_COMPANION_DISTANCE_IN_CELL_HEIGHTS
            }
            .minByOrNull { abs(it.boundingBox.midX - cell.boundingBox.midX) }
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
    private fun foldedLabelText(text: String): String = foldDiacritics(text.lowercase()).replace('ü', 'u').replace('ľ', 'l').replace('ł', 'l').replace('q', 'g')

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
        val allowed = "0123456789.,<≤/|() q_•·"
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

    // A lone "9" before a value is the "g" printed in front of it ("g 0,90").
    private fun numbers(text: String): List<Double> {
        val results = mutableListOf<Double>()
        var current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                val trimmed = current.toString().trimEnd(',', '.')
                // A speck read as a leading dot (".3,7") is not a decimal separator when a real one follows.
                val token = (if (trimmed.firstOrNull() in listOf(',', '.') && trimmed.drop(1).any { it == ',' || it == '.' }) trimmed.drop(1) else trimmed).replace(",", ".")
                // "0,10" read as "010": a whole number never starts with a zero.
                val restored = if (token.length > 1 && token.startsWith("0") && token.all { it.isDigit() }) "0.${token.drop(1)}" else token
                restored.toDoubleOrNull()?.let { results.add(it) }
            }
            current = StringBuilder()
        }
        for (char in joiningThousandsSeparators(misreadUnitRegex.replace(text, "\$1g "))) {
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
