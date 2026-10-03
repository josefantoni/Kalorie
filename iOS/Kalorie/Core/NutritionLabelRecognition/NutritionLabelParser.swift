//
//  NutritionLabelParser.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import Foundation
import MacroKit

enum NutritionLabelParser {

    // MARK: - Properties

    private static let columnTolerance: CGFloat = 0.15
    private static let rowOverlapThreshold: CGFloat = 0.4
    private static let inferredColumnTolerance: CGFloat = 0.08
    private static let minInferredColumnCells = 3
    private static let energyToleranceRatio = 0.05
    private static let derivedEnergyToleranceRatio = 0.15
    // 100 g plus the "<0,5 g" bounds and rounding that a near-pure fat or carbohydrate product can add up.
    private static let maxSummedMacros = 103.0
    private static let maxSingleMacro = 100.0
    private static let maxWordBoundaryKeywordLength = 3
    private static let minFuzzyKeywordLength = 7
    private static let maxValueCellHeightRatio: CGFloat = 2.0
    private static let minRowShiftInCellHeights: CGFloat = 0.75
    private static let maxRowShiftInCellHeights: CGFloat = 1.5

    private static let weightKeywords = ["hmotnost", "netto", "obsah", "net weight", "hmotnosc"]

    private static let nutritionSectionKeywords = [
        "výživové údaje", "vyzivove udaje", "wartość odżywcza", "wartosc odzywcza",
        "nährwertangaben", "naehrwertangaben", "nutrition information", "nutritional information", "nutrition facts"
    ]

    private enum LabelField: CaseIterable {
        case energy, fat, saturates, carbohydrate, sugars, fiber, protein, salt, unlisted
    }

    // Unsaturated fat is derived from fat minus saturates, so its own rows carry no field; left
    // alone they match "saturates" (unsaturates, nenasycené mastné) or "fat" (ungesättigte Fettsäuren).
    // Like polyols or starch, such a row still takes its own value, or every row below it reads its neighbour's.
    private static let unsaturatedMarkers = ["unsaturate", "nenasycen", "nienasycon", "ungesättigt", "ungesattigt"]

    private static let keywords: [LabelField: [String]] = [
        .energy: ["energeticka hodnota", "energetická hodnota", "energie", "energia", "wartość energetyczna", "wartosc energetyczna", "brennwert", "energy"],
        .fat: ["tuky", "tłuszcz", "tluszcz", "fett", "fat"],
        .saturates: [
            "z toho nasycene", "z toho nasycené", "nasycené mastné", "nasycene mastne",
            "w tym kwasy nasycone", "davon gesättigte", "davon gesattigte", "of which saturates", "saturated fat", "saturates"
        ],
        .carbohydrate: ["sacharidy", "węglowodany", "weglowodany", "kohlenhydrate", "carbohydrate"],
        .sugars: ["z toho cukry", "cukry", "davon zucker", "of which sugars", "sugars"],
        .fiber: ["vláknina", "vlaknina", "błonnik", "blonnik", "ballaststoffe", "fibre", "fiber"],
        .protein: ["bílkoviny", "bilkoviny", "bielkoviny", "białko", "bialko", "eiweiß", "eiweiss", "protein"],
        .salt: ["sůl", "sul", "sól", "sól", "soľ", "sol", "salz", "salt"],
        .unlisted: ["polyoly", "polyol", "polyols", "škrob", "skrob", "starch", "stärke", "skrobia"]
    ]

    private static let foldedKeywords: [LabelField: [String]] = keywords.mapValues { list in
        list.map(foldedLabelText).reduce(into: []) { result, keyword in
            if !result.contains(keyword) { result.append(keyword) }
        }
    }

    // MARK: - Functions

    static func parse(lines: [RecognizedTextLine]) -> NutritionLabelReading {
        let packageMeasure = packageWeight(in: lines)?.measure
        var reading = NutritionLabelReading()

        let header = perHundredHeader(in: lines)
        if let columnX = header?.x ?? inferredValueColumnX(in: lines) {
            let cells = valueCellsBelowHeader(in: lines)
            let labels = lines
                .compactMap { line in matchedField(for: line.text).map { (line: line, field: $0) } }
                .sorted { $0.line.boundingBox.midY > $1.line.boundingBox.midY }
            let shift = rowShift(labels: labels, cells: cells, columnX: columnX)
            for assignment in assignments(labels: labels, cells: cells, columnX: columnX, shift: shift) {
                apply(assignment.field, text: assignment.valueLine.text, to: &reading)
            }
        }

        // The table-shaped pass above found literally nothing to reject or accept (not "found a
        // value the checks then threw out" — that rejection must stand) — try the linear fallback,
        // but only where there is an actual per-100g signal, so a label with no nutrition
        // declaration at all still fills nothing (design's own "no header, no data" guarantee).
        if hasNoMacros(reading), hasNutritionContext(lines: lines) {
            reading = applyingConsistencyChecks(parseLinear(lines: lines))
        } else {
            reading = applyingConsistencyChecks(reading)
        }

        // The nutrition basis (what the numbers mean) always outranks the package line, which is
        // only a hint — see design 0011's "core rule".
        reading.measure = header?.measure ?? linearMeasure(lines: lines) ?? packageMeasure

        reading = derivingUnsaturated(reading)
        return reading
    }

    private static func linearMeasure(lines: [RecognizedTextLine]) -> FoodMeasure? {
        let compact = lines.map(\.text).joined(separator: " ").lowercased().replacingOccurrences(of: " ", with: "")
        let hasMl = compact.contains("100ml")
        let hasG = compact.contains("100g")
        guard hasMl != hasG else { return nil }
        return hasMl ? .millilitres : .grams
    }

    private static func hasNoMacros(_ reading: NutritionLabelReading) -> Bool {
        reading.energyKJ == nil
            && reading.fat == nil
            && reading.carbohydrate == nil
            && reading.protein == nil
            && reading.salt == nil
    }

    private static func hasNutritionContext(lines: [RecognizedTextLine]) -> Bool {
        let text = lines.map(\.text).joined(separator: " ").lowercased()
        guard !nutritionSectionKeywords.contains(where: { text.contains($0) }) else { return true }
        let compact = text.replacingOccurrences(of: " ", with: "")
        return compact.contains("100g") || compact.contains("100ml")
    }

    /// Fallback for EU 1169/2011's linear declaration format on small packages ("Tuky 3,3 g z toho
    /// nasycené mastné kyseliny 0,6 g, Sacharidy…"), which has no column geometry to key off — each
    /// field's keyword marks where its value starts, the next field's keyword marks where it ends.
    private static func parseLinear(lines: [RecognizedTextLine]) -> NutritionLabelReading {
        let text = lines.map(\.text).joined(separator: " ")
        let fullText = text.lowercased()

        // A long ingredients list often repeats a field's own keyword before the real value does
        // (e.g. "jedlá sůl" as an ingredient, ahead of "Sůl 1,6 g" in the table) — anchoring the
        // scan to the nutrition section itself, when it can be found, avoids matching those.
        let sectionStart = nutritionSectionKeywords.compactMap { fullText.range(of: $0) }.min { $0.lowerBound < $1.lowerBound }
        let lower = sectionStart.map { String(fullText[$0.upperBound...]) } ?? fullText

        let saturatesStarts = (keywords[.saturates] ?? []).flatMap { ranges(ofKeyword: $0, in: lower) }.map(\.lowerBound)
        // "saturated fat" and "davon gesättigte Fettsäuren" carry the fat keyword inside the saturates
        // label; a fat keyword reached from a saturates keyword with no value in between is that label's tail.
        func isTailOfSaturatesLabel(_ range: Range<String.Index>) -> Bool {
            saturatesStarts.contains { start in
                start <= range.lowerBound && !lower[start..<range.lowerBound].contains(where: \.isNumber)
            }
        }

        var matches: [(field: LabelField, start: String.Index, end: String.Index)] = []
        for field in LabelField.allCases {
            let candidates = (keywords[field] ?? []).flatMap { ranges(ofKeyword: $0, in: lower) }
            let usable = field == .fat ? candidates.filter { !isTailOfSaturatesLabel($0) } : candidates
            guard let firstMatch = usable.min(by: { $0.lowerBound < $1.lowerBound }) else { continue }
            matches.append((field, firstMatch.lowerBound, firstMatch.upperBound))
        }
        matches.sort { $0.start < $1.start }

        var reading = NutritionLabelReading()
        for (index, match) in matches.enumerated() {
            let windowEnd = index + 1 < matches.count ? matches[index + 1].start : lower.endIndex
            guard match.end < windowEnd else { continue }
            let window = String(lower[match.end..<windowEnd])
            switch match.field {
            case .energy:
                let (kJ, kcal) = energyValues(in: window)
                reading.energyKJ = kJ
                reading.caloriesPerHundredGrams = kcal
            case .fat:
                reading.fat = numbers(in: window).first
            case .saturates:
                reading.fatSaturated = numbers(in: window).first
            case .carbohydrate:
                reading.carbohydrate = numbers(in: window).first
            case .sugars:
                reading.carbohydratePureSugar = numbers(in: window).first
            case .fiber:
                reading.fiber = numbers(in: window).first
            case .protein:
                reading.protein = numbers(in: window).first
            case .salt:
                reading.salt = numbers(in: window).first
            case .unlisted:
                break
            }
        }
        return reading
    }

    // A keyword this short ("sul", "fat") also sits inside longer words such as "sulphites" or "fatty".
    private static func ranges(ofKeyword keyword: String, in text: String) -> [Range<String.Index>] {
        var found: [Range<String.Index>] = []
        var searchStart = text.startIndex
        while let range = text.range(of: keyword, range: searchStart..<text.endIndex) {
            let startsWord = range.lowerBound == text.startIndex || !text[text.index(before: range.lowerBound)].isLetter
            let endsWord = range.upperBound == text.endIndex || !text[range.upperBound].isLetter
            if keyword.count > maxWordBoundaryKeywordLength || (startsWord && endsWord) { found.append(range) }
            searchStart = text.index(after: range.lowerBound)
        }
        return found
    }

    static func merging(_ reading: NutritionLabelReading, with candidate: NutritionLabelModelCandidate, ocrText: String) -> NutritionLabelReading {
        var merged = reading
        let text = ocrText.lowercased()

        if
            merged.name == nil,
            let name = candidate.name,
            !name.isEmpty,
            text.contains(name.lowercased())
        {
            merged.name = name
        }
        if
            merged.portions?.isEmpty ?? true,
            let candidatePortions = candidate.portions
        {
            let grounded = candidatePortions
                .filter { !$0.name.isEmpty && $0.grams > 0 && isGrounded($0.grams, in: text) }
                .map { FoodPortionDomain(name: $0.name, grams: $0.grams) }
            if !grounded.isEmpty { merged.portions = grounded }
        }

        var candidateMacros = NutritionLabelReading(
            energyKJ: groundedOrNil(candidate.energyKJPer100g, in: text),
            caloriesPerHundredGrams: groundedOrNil(candidate.caloriesPer100g, in: text),
            fat: groundedOrNil(candidate.fatPer100g, in: text),
            fatSaturated: groundedOrNil(candidate.fatSaturatedPer100g, in: text),
            carbohydrate: groundedOrNil(candidate.carbohydratePer100g, in: text),
            carbohydratePureSugar: groundedOrNil(candidate.carbohydrateSugarPer100g, in: text),
            fiber: groundedOrNil(candidate.fiberPer100g, in: text),
            protein: groundedOrNil(candidate.proteinPer100g, in: text),
            salt: groundedOrNil(candidate.saltPer100g, in: text)
        )
        candidateMacros = applyingConsistencyChecks(candidateMacros)

        if merged.energyKJ == nil { merged.energyKJ = candidateMacros.energyKJ }
        if merged.caloriesPerHundredGrams == nil { merged.caloriesPerHundredGrams = candidateMacros.caloriesPerHundredGrams }
        if merged.fat == nil { merged.fat = candidateMacros.fat }
        if merged.fatSaturated == nil { merged.fatSaturated = candidateMacros.fatSaturated }
        if merged.carbohydrate == nil { merged.carbohydrate = candidateMacros.carbohydrate }
        if merged.carbohydratePureSugar == nil { merged.carbohydratePureSugar = candidateMacros.carbohydratePureSugar }
        if merged.fiber == nil { merged.fiber = candidateMacros.fiber }
        if merged.protein == nil { merged.protein = candidateMacros.protein }
        if merged.salt == nil { merged.salt = candidateMacros.salt }

        return derivingUnsaturated(merged)
    }

    // MARK: - Row grouping and column detection

    // Text printed sideways along the table edge (a batch number, say) is a box several rows tall; it
    // overlaps every row it crosses and would take a real value's place.
    private static func valueCells(in lines: [RecognizedTextLine]) -> [RecognizedTextLine] {
        let cells = lines.filter { isValueCell($0) }
        guard let medianHeight = medianHeight(of: cells) else { return cells }
        return cells.filter { $0.boundingBox.height <= medianHeight * maxValueCellHeightRatio }
    }

    private static func medianHeight(of lines: [RecognizedTextLine]) -> CGFloat? {
        let heights = lines.map(\.boundingBox.height).sorted()
        return heights.isEmpty ? nil : heights[heights.count / 2]
    }

    private static func isValueCell(_ line: RecognizedTextLine) -> Bool {
        line.text.contains(where: \.isNumber) && isPlausibleValueText(line.text)
    }

    // OCR can split the header into "na" "100" "g"; the bare "100" is all digits and would pass for a value, but a
    // genuine "100 g" (a pure fat, say) sits below the top value cell, a header above it.
    private static func valueCellsBelowHeader(in lines: [RecognizedTextLine]) -> [RecognizedTextLine] {
        let cells = valueCells(in: lines)
        guard let topmostValueMidY = cells.filter({ !isPerHundredHeader($0.text) }).map(\.boundingBox.midY).max() else { return cells }
        return cells.filter { !isPerHundredHeader($0.text) || $0.boundingBox.midY <= topmostValueMidY }
    }

    private static func overlapsVertically(_ first: RecognizedTextLine, _ second: RecognizedTextLine) -> Bool {
        let overlap = min(first.boundingBox.maxY, second.boundingBox.maxY) - max(first.boundingBox.minY, second.boundingBox.minY)
        let minHeight = min(first.boundingBox.height, second.boundingBox.height)
        guard minHeight > 0 else { return false }
        return overlap / minHeight > rowOverlapThreshold
    }

    // Without a usable "per 100 g" header, a single column of values still identifies the per-100 g
    // column; two columns (per 100 g and per portion) are told apart by the header only.
    private static func inferredValueColumnX(in lines: [RecognizedTextLine]) -> CGFloat? {
        let cells = valueCells(in: lines)
        guard cells.count >= minInferredColumnCells else { return nil }
        let median = cells.map(\.boundingBox.midX).sorted()[cells.count / 2]
        let inColumn = cells.filter { abs($0.boundingBox.midX - median) <= inferredColumnTolerance }.count
        return inColumn * 2 > cells.count ? median : nil
    }

    private static func perHundredHeader(in lines: [RecognizedTextLine]) -> (x: CGFloat, measure: FoodMeasure?)? {
        let valueCells = valueCells(in: lines)
        let headers = lines.filter { header in
            isPerHundredHeader(header.text) && hasValueCellBelow(header, in: valueCells)
        }
        guard headers.count == 1, let header = headers.first else { return nil }
        let folded = foldedHeaderText(header.text)
        let measure: FoodMeasure?
        if folded.contains("100ml") {
            measure = .millilitres
        } else if folded.contains("100g") {
            measure = .grams
        } else {
            measure = nil
        }
        return (header.boundingBox.midX, measure)
    }

    // A sentence from the ingredients ("…17 g na 100 g…") also contains "100 g", but no value cell sits
    // under it, so only the genuine column header qualifies.
    private static func hasValueCellBelow(_ header: RecognizedTextLine, in valueCells: [RecognizedTextLine]) -> Bool {
        valueCells.contains { cell in
            cell.boundingBox.maxY <= header.boundingBox.minY
                && abs(cell.boundingBox.midX - header.boundingBox.midX) <= columnTolerance
        }
    }

    private static func foldedHeaderText(_ text: String) -> String {
        text.lowercased().foldingDiacritics().replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "q", with: "g")
    }

    private static func isPerHundredHeader(_ text: String) -> Bool {
        let folded = foldedHeaderText(text)
        return folded.contains("100g") || folded.contains("100ml") || folded.hasSuffix("100")
    }

    // Rows run top to bottom, so a label takes the highest value cell it overlaps that no label above has
    // taken; a skewed photo makes the neighbouring row's value overlap just as much.
    private static func assignments(
        labels: [(line: RecognizedTextLine, field: LabelField)],
        cells: [RecognizedTextLine],
        columnX: CGFloat,
        shift: CGFloat
    ) -> [(field: LabelField, valueLine: RecognizedTextLine)] {
        var unusedCells = cells
        return labels.compactMap { label in
            let shifted = RecognizedTextLine(text: label.line.text, boundingBox: label.line.boundingBox.offsetBy(dx: 0, dy: shift))
            guard
                let valueIndex = unusedCells.indices
                    .filter({ unusedCells[$0].boundingBox.minX > label.line.boundingBox.minX && overlapsVertically(shifted, unusedCells[$0]) })
                    .max(by: { unusedCells[$0].boundingBox.midY < unusedCells[$1].boundingBox.midY })
            else { return nil }
            let valueLine = unusedCells[valueIndex]
            guard abs(valueLine.boundingBox.midX - columnX) <= columnTolerance else { return nil }
            unusedCells.remove(at: valueIndex)
            return (label.field, valueLine)
        }
    }

    // A tilted photo or a curved jar lifts the value column against the label column by up to a whole row, and each
    // label then overlaps its neighbour's value. Energy is the one row recognisable from both sides, by its keyword
    // and by the kJ or kcal in its value, so its offset is taken as the whole table's.
    private static func rowShift(
        labels: [(line: RecognizedTextLine, field: LabelField)],
        cells: [RecognizedTextLine],
        columnX: CGFloat
    ) -> CGFloat {
        guard
            let energyLabel = labels.first(where: { $0.field == .energy })?.line,
            let medianHeight = medianHeight(of: cells),
            let shift = cells
                .filter({ abs($0.boundingBox.midX - columnX) <= columnTolerance && hasEnergyUnit($0.text) })
                .map({ $0.boundingBox.midY - energyLabel.boundingBox.midY })
                .min(by: { abs($0) < abs($1) })
        else { return 0 }
        let shiftInCellHeights = abs(shift) / medianHeight
        return shiftInCellHeights >= minRowShiftInCellHeights && shiftInCellHeights <= maxRowShiftInCellHeights ? shift : 0
    }

    private static func hasEnergyUnit(_ text: String) -> Bool {
        text.lowercased().range(of: "[0-9]+[.,]?[0-9]*\\s*(kj|kcal)", options: .regularExpression) != nil
    }

    private static func apply(_ field: LabelField, text: String, to reading: inout NutritionLabelReading) {
        switch field {
        case .energy:
            let (kJ, kcal) = energyValues(in: text)
            reading.energyKJ = kJ
            reading.caloriesPerHundredGrams = kcal
        case .fat:
            reading.fat = numbers(in: text).first
        case .saturates:
            reading.fatSaturated = numbers(in: text).first
        case .carbohydrate:
            reading.carbohydrate = numbers(in: text).first
        case .sugars:
            reading.carbohydratePureSugar = numbers(in: text).first
        case .fiber:
            reading.fiber = numbers(in: text).first
        case .protein:
            reading.protein = numbers(in: text).first
        case .salt:
            reading.salt = numbers(in: text).first
        case .unlisted:
            break
        }
    }

    // OCR misreads one letter of a label as often as a digit of a value ("Vaknina", "Eneraie"), so a long
    // keyword one edit away still matches, but only when no keyword matches exactly.
    private static func matchedField(for label: String) -> LabelField? {
        let folded = foldedLabelText(label)
        guard !unsaturatedMarkers.contains(where: { folded.contains(foldedLabelText($0)) }) else { return .unlisted }
        return longestMatchingField(in: folded) { text, keyword in !ranges(ofKeyword: keyword, in: text).isEmpty }
            ?? longestMatchingField(in: folded) { text, keyword in
                keyword.count >= minFuzzyKeywordLength && containsWithinOneEdit(text, keyword)
            }
    }

    private static func longestMatchingField(in text: String, matches: (String, String) -> Bool) -> LabelField? {
        LabelField.allCases
            .compactMap { field -> (field: LabelField, keywordLength: Int)? in
                guard let longest = foldedKeywords[field]?.filter({ matches(text, $0) }).map(\.count).max() else { return nil }
                return (field, longest)
            }
            .max { $0.keywordLength < $1.keywordLength }?
            .field
    }

    // "ü" and "ľ" are OCR's readings of "ů" and the Slovak "ľ" in "soľ"; "q" is a misread "g".
    private static func foldedLabelText(_ text: String) -> String {
        text
            .lowercased()
            .foldingDiacritics()
            .replacingOccurrences(of: "ü", with: "u")
            .replacingOccurrences(of: "ľ", with: "l")
            .replacingOccurrences(of: "q", with: "g")
    }

    private static func containsWithinOneEdit(_ text: String, _ keyword: String) -> Bool {
        let characters = Array(text)
        let keywordCharacters = Array(keyword)
        return characters.indices
            .filter { $0 == 0 || !characters[$0 - 1].isLetter }
            .contains { start in
                (keywordCharacters.count - 1...keywordCharacters.count + 1).contains { length in
                    start + length <= characters.count && isWithinOneEdit(Array(characters[start..<start + length]), keywordCharacters)
                }
            }
    }

    private static func isWithinOneEdit(_ first: [Character], _ second: [Character]) -> Bool {
        guard abs(first.count - second.count) <= 1 else { return false }
        var firstIndex = 0
        var secondIndex = 0
        var edits = 0
        while firstIndex < first.count, secondIndex < second.count {
            if first[firstIndex] == second[secondIndex] {
                firstIndex += 1
                secondIndex += 1
                continue
            }
            edits += 1
            if edits > 1 { return false }
            if first.count > second.count {
                firstIndex += 1
            } else if first.count < second.count {
                secondIndex += 1
            } else {
                firstIndex += 1
                secondIndex += 1
            }
        }
        return edits + (first.count - firstIndex) + (second.count - secondIndex) <= 1
    }

    // A row's nearest-to-column candidate is picked by geometry alone, which cannot tell a genuine
    // value cell from a neighbouring label fragment that merely sits close to the column (e.g. a
    // misprinted or duplicated row shifting the table by one line, as seen on a real label where
    // "Omega 3 mastné kyseliny" ended up the nearest candidate to the protein row and its "3" was
    // read as the value). Requiring the candidate to be otherwise all digits/punctuation/units, once
    // known units are stripped, rejects such prose candidates instead of inventing a value from them.
    private static func isPlausibleValueText(_ text: String) -> Bool {
        var remainder = text.lowercased()
        for unit in ["kcal", "kj", "ml", "g", "%"] {
            remainder = remainder.replacingOccurrences(of: unit, with: "")
        }
        let allowedCharacters = CharacterSet(charactersIn: "0123456789.,<≤/|() q_")
        return remainder.unicodeScalars.allSatisfy(allowedCharacters.contains)
    }

    // MARK: - Package weight

    private static func packageWeight(in lines: [RecognizedTextLine]) -> (value: Double, measure: FoodMeasure)? {
        let candidates: [(value: Double, measure: FoodMeasure)] = lines.compactMap { line in
            let folded = line.text.lowercased().foldingDiacritics()
            guard weightKeywords.contains(where: { folded.contains($0) }) else { return nil }
            if let match = weightValue(in: line.text) {
                return scaledPackageWeight(match)
            }
            // The value is sometimes printed in a much larger font directly under its label
            // (e.g. "Hmotnost:" / "200 g"), which Vision reports as two separate lines.
            guard
                let below = nearestLineBelow(line, in: lines),
                let match = weightValue(in: below.text)
            else { return nil }
            return scaledPackageWeight(match)
        }
        return candidates.count == 1 ? candidates[0] : nil
    }

    private static func scaledPackageWeight(_ match: (value: Double, unit: String)) -> (value: Double, measure: FoodMeasure) {
        let value = match.unit == "kg" || match.unit == "l" ? match.value * 1000 : match.value
        let measure: FoodMeasure = match.unit == "ml" || match.unit == "l" ? .millilitres : .grams
        return (value, measure)
    }

    private static func nearestLineBelow(_ line: RecognizedTextLine, in lines: [RecognizedTextLine]) -> RecognizedTextLine? {
        lines
            .filter { $0.boundingBox.maxY <= line.boundingBox.minY }
            .max { $0.boundingBox.maxY < $1.boundingBox.maxY }
    }

    private static func weightValue(in text: String) -> (value: Double, unit: String)? {
        guard let regex = try? NSRegularExpression(pattern: "([0-9]+[.,]?[0-9]*)\\s*(kg|g|ml|l)\\b") else { return nil }
        let lower = text.lowercased()
        let range = NSRange(lower.startIndex..., in: lower)
        guard
            let match = regex.firstMatch(in: lower, range: range),
            let numberRange = Range(match.range(at: 1), in: lower),
            let unitRange = Range(match.range(at: 2), in: lower),
            let value = Double(lower[numberRange].replacingOccurrences(of: ",", with: "."))
        else { return nil }
        return (value, String(lower[unitRange]))
    }

    // MARK: - Number parsing

    // Czech (and Slovak/Polish) typography groups thousands with a space — "3 404 kJ" prints
    // 3404 — which would otherwise read as two separate numbers ("3" and "404"). The pattern is
    // safe to collapse unconditionally: a genuine word boundary never has a digit on both sides.
    private static func joiningThousandsSeparators(_ text: String) -> String {
        var result = text
        while let range = result.range(of: "[0-9] [0-9]", options: .regularExpression) {
            result.replaceSubrange(range, with: result[range].replacingOccurrences(of: " ", with: ""))
        }
        return result
    }

    private static func numbers(in text: String) -> [Double] {
        var results: [Double] = []
        var current = ""
        func flush() {
            defer { current = "" }
            while let last = current.last, last == "," || last == "." { current.removeLast() }
            // "0,10" read as "010": a whole number never starts with a zero.
            let restored = current.count > 1 && current.hasPrefix("0") && current.allSatisfy(\.isNumber) ? "0." + String(current.dropFirst()) : current
            guard !current.isEmpty, let value = Double(restored.replacingOccurrences(of: ",", with: ".")) else { return }
            results.append(value)
        }
        for char in joiningThousandsSeparators(text) {
            if char.isNumber || char == "," || char == "." {
                current.append(char)
            } else {
                flush()
            }
        }
        flush()
        return results
    }

    private static func energyValues(in text: String) -> (kJ: Double?, kcal: Double?) {
        let lower = joiningThousandsSeparators(text.lowercased())
        guard let regex = try? NSRegularExpression(pattern: "([0-9]+[.,]?[0-9]*)\\s*(kj|kcal)") else { return (nil, nil) }
        let range = NSRange(lower.startIndex..., in: lower)
        var kJ: Double?
        var kcal: Double?
        regex.enumerateMatches(in: lower, range: range) { match, _, _ in
            guard
                let match,
                let numberRange = Range(match.range(at: 1), in: lower),
                let unitRange = Range(match.range(at: 2), in: lower),
                let value = Double(lower[numberRange].replacingOccurrences(of: ",", with: "."))
            else { return }
            if lower[unitRange] == "kj" { kJ = value } else { kcal = value }
        }
        return (kJ, kcal)
    }

    // MARK: - Consistency checks

    // A value that lost its decimal comma ("15 g" read as "150") is impossible per 100 g; dropping it first
    // keeps it from breaking the cross-field checks and wiping the correct values.
    private static func droppingValuesOverSingleLimit(_ reading: NutritionLabelReading) -> NutritionLabelReading {
        var result = reading
        func withinSingleLimit(_ value: Double?) -> Double? {
            guard
                let value,
                value <= maxSingleMacro
            else { return nil }
            return value
        }
        result.fat = withinSingleLimit(result.fat)
        result.fatSaturated = withinSingleLimit(result.fatSaturated)
        result.carbohydrate = withinSingleLimit(result.carbohydrate)
        result.carbohydratePureSugar = withinSingleLimit(result.carbohydratePureSugar)
        result.protein = withinSingleLimit(result.protein)
        result.salt = withinSingleLimit(result.salt)
        result.fiber = withinSingleLimit(result.fiber)
        return result
    }

    private static func applyingConsistencyChecks(_ reading: NutritionLabelReading) -> NutritionLabelReading {
        var result = droppingValuesOverSingleLimit(reading)

        if
            let kJ = result.energyKJ,
            let kcal = result.caloriesPerHundredGrams
        {
            let expectedKJ = kcal * 4.184
            if expectedKJ <= 0 || abs(kJ - expectedKJ) / expectedKJ > energyToleranceRatio {
                result.energyKJ = nil
                result.caloriesPerHundredGrams = nil
            }
        }

        if
            let saturates = result.fatSaturated,
            let fat = result.fat,
            saturates > fat
        {
            result.fatSaturated = nil
        }
        if
            let sugars = result.carbohydratePureSugar,
            let carbs = result.carbohydrate,
            sugars > carbs
        {
            result.carbohydratePureSugar = nil
        }

        // kJ and kcal that agree are two independent reads already; the general factors would also reject a
        // correct energy whenever polyols or fibre carry less energy than the carbohydrate they are counted in.
        if
            let kJ = result.energyKJ,
            result.caloriesPerHundredGrams == nil,
            let fat = result.fat,
            let carbs = result.carbohydrate,
            let protein = result.protein
        {
            let derivedKJ = MacrosKt.energyKJFromMacros(fat: fat, carbohydrate: carbs, protein: protein)
            if derivedKJ <= 0 || abs(kJ - derivedKJ) / derivedKJ > derivedEnergyToleranceRatio {
                result.energyKJ = nil
                result.caloriesPerHundredGrams = nil
            }
        }

        let summed = [result.fat, result.carbohydrate, result.protein, result.salt, result.fiber].compactMap { $0 }.reduce(0, +)
        if summed > maxSummedMacros {
            result.fat = nil
            result.carbohydrate = nil
            result.protein = nil
            result.salt = nil
            result.fiber = nil
        }

        return result
    }

    private static func derivingUnsaturated(_ reading: NutritionLabelReading) -> NutritionLabelReading {
        var result = reading
        if
            result.fatUnsaturatedFattyAcids == nil,
            let fat = result.fat,
            let saturated = result.fatSaturated
        {
            result.fatUnsaturatedFattyAcids = max(0, fat - saturated)
        }
        return result
    }

    // MARK: - Grounding (Foundation Models path)

    private static func isGrounded(_ value: Double, in text: String) -> Bool {
        let dotForm = String(format: "%g", value)
        let commaForm = dotForm.replacingOccurrences(of: ".", with: ",")
        return containsAsStandaloneNumber(dotForm, in: text) || containsAsStandaloneNumber(commaForm, in: text)
    }

    // A plain substring `contains` would match a short number like "1" inside an unrelated longer
    // one (e.g. "312 kJ"), treating a hallucinated value as grounded when it merely shares digits
    // with something else on the label — the digit-boundary lookaround rules that out.
    private static func containsAsStandaloneNumber(_ number: String, in text: String) -> Bool {
        let pattern = "(?<![0-9.,])\(NSRegularExpression.escapedPattern(for: number))(?![0-9.,])"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return false }
        let range = NSRange(text.startIndex..., in: text)
        return regex.firstMatch(in: text, range: range) != nil
    }

    private static func groundedOrNil(_ value: Double?, in text: String) -> Double? {
        guard let value, isGrounded(value, in: text) else { return nil }
        return value
    }
}
