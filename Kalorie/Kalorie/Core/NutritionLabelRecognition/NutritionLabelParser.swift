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
    private static let energyToleranceRatio = 0.05
    private static let derivedEnergyToleranceRatio = 0.15
    private static let maxSummedMacros = 100.0

    private static let weightKeywords = ["hmotnost", "netto", "obsah", "net weight", "hmotnosc"]

    private static let nutritionSectionKeywords = [
        "výživové údaje", "vyzivove udaje", "wartość odżywcza", "wartosc odzywcza",
        "nährwertangaben", "naehrwertangaben", "nutrition information", "nutritional information", "nutrition facts"
    ]

    private enum LabelField: CaseIterable {
        case energy, fat, saturates, carbohydrate, sugars, fiber, protein, salt
    }

    private static let keywords: [LabelField: [String]] = [
        .energy: ["energeticka hodnota", "energetická hodnota", "energie", "energia", "wartość energetyczna", "wartosc energetyczna", "brennwert", "energy"],
        .fat: ["tuky", "tłuszcz", "tluszcz", "fett", "fat"],
        .saturates: [
            "z toho nasycene", "z toho nasycené", "nasycené mastné", "nasycene mastne",
            "w tym kwasy nasycone", "davon gesättigte", "davon gesattigte", "of which saturates", "saturates"
        ],
        .carbohydrate: ["sacharidy", "węglowodany", "weglowodany", "kohlenhydrate", "carbohydrate"],
        .sugars: ["z toho cukry", "cukry", "davon zucker", "of which sugars", "sugars"],
        .fiber: ["vláknina", "vlaknina", "błonnik", "blonnik", "ballaststoffe", "fibre", "fiber"],
        .protein: ["bílkoviny", "bilkoviny", "białko", "bialko", "eiweiß", "eiweiss", "protein"],
        .salt: ["sůl", "sul", "sól", "sól", "salz", "salt"]
    ]

    // MARK: - Functions

    static func parse(lines: [RecognizedTextLine]) -> NutritionLabelReading {
        let weightResult = packageWeight(in: lines)
        var reading = NutritionLabelReading(weightOfProduct: weightResult?.value)

        let header = perHundredHeader(in: lines)
        if let header {
            for row in groupIntoRows(lines) {
                apply(row: row, columnX: header.x, to: &reading)
            }
        }

        // The table-shaped pass above found literally nothing to reject or accept (not "found a
        // value the checks then threw out" — that rejection must stand) — try the linear fallback,
        // but only where there is an actual per-100g signal, so a label with no nutrition
        // declaration at all still fills nothing (design's own "no header, no data" guarantee).
        if hasNoMacros(reading), hasNutritionContext(lines: lines) {
            var linear = applyingConsistencyChecks(parseLinear(lines: lines))
            linear.weightOfProduct = reading.weightOfProduct
            reading = linear
        } else {
            reading = applyingConsistencyChecks(reading)
        }

        // The nutrition basis (what the numbers mean) always outranks the package line, which is
        // only a hint — see design 0011's "core rule".
        reading.measure = header?.measure ?? linearMeasure(lines: lines) ?? weightResult?.measure

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

        var matches: [(field: LabelField, start: String.Index, end: String.Index)] = []
        for field in LabelField.allCases {
            guard let firstMatch = (keywords[field] ?? []).compactMap({ lower.range(of: $0) }).min(by: { $0.lowerBound < $1.lowerBound }) else { continue }
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
            }
        }
        return reading
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
            merged.weightOfProduct == nil,
            let weight = candidate.packageWeightGrams,
            isGrounded(weight, in: text)
        {
            merged.weightOfProduct = weight
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

    private static func groupIntoRows(_ lines: [RecognizedTextLine]) -> [[RecognizedTextLine]] {
        let sorted = lines.sorted { $0.boundingBox.midY > $1.boundingBox.midY }
        var rows: [[RecognizedTextLine]] = []
        for line in sorted {
            if
                let lastIndex = rows.indices.last,
                verticallyOverlaps(line, rows[lastIndex])
            {
                rows[lastIndex].append(line)
            } else {
                rows.append([line])
            }
        }
        return rows.map { $0.sorted { $0.boundingBox.minX < $1.boundingBox.minX } }
    }

    // Compared against the row's first (seed) line, not the union of every line already in it: a
    // row's accumulated bounding box would otherwise grow with each merge, letting it "reach" and
    // absorb an unrelated neighbouring row's line that only overlaps the enlarged union — confirmed
    // on-device (a real table's "92 g" got absorbed into the row above it once that row's union grew
    // tall enough, leaving "Tuky" to instead pair with the row below's "7 g").
    private static func verticallyOverlaps(_ line: RecognizedTextLine, _ row: [RecognizedTextLine]) -> Bool {
        guard let anchor = row.first else { return false }
        let overlap = min(line.boundingBox.maxY, anchor.boundingBox.maxY) - max(line.boundingBox.minY, anchor.boundingBox.minY)
        let minHeight = min(line.boundingBox.height, anchor.boundingBox.height)
        guard minHeight > 0 else { return false }
        return overlap / minHeight > rowOverlapThreshold
    }

    private static func perHundredHeader(in lines: [RecognizedTextLine]) -> (x: CGFloat, measure: FoodMeasure)? {
        let headers = lines.filter { isPerHundredHeader($0.text) }
        guard headers.count == 1, let header = headers.first else { return nil }
        let folded = header.text.lowercased().foldingDiacritics().replacingOccurrences(of: " ", with: "")
        let measure: FoodMeasure = folded.contains("100ml") ? .millilitres : .grams
        return (header.boundingBox.midX, measure)
    }

    private static func isPerHundredHeader(_ text: String) -> Bool {
        let folded = text.lowercased().foldingDiacritics().replacingOccurrences(of: " ", with: "")
        return folded.contains("100g") || folded.contains("100ml")
    }

    private static func apply(row: [RecognizedTextLine], columnX: CGFloat, to reading: inout NutritionLabelReading) {
        guard let label = row.first, let field = matchedField(for: label.text) else { return }
        let valueCandidates = row.dropFirst()
        guard
            let valueLine = valueCandidates.min(by: { abs($0.boundingBox.midX - columnX) < abs($1.boundingBox.midX - columnX) }),
            abs(valueLine.boundingBox.midX - columnX) <= columnTolerance,
            isPlausibleValueText(valueLine.text)
        else { return }

        switch field {
        case .energy:
            let (kJ, kcal) = energyValues(in: valueLine.text)
            reading.energyKJ = kJ
            reading.caloriesPerHundredGrams = kcal
        case .fat:
            reading.fat = numbers(in: valueLine.text).first
        case .saturates:
            reading.fatSaturated = numbers(in: valueLine.text).first
        case .carbohydrate:
            reading.carbohydrate = numbers(in: valueLine.text).first
        case .sugars:
            reading.carbohydratePureSugar = numbers(in: valueLine.text).first
        case .fiber:
            reading.fiber = numbers(in: valueLine.text).first
        case .protein:
            reading.protein = numbers(in: valueLine.text).first
        case .salt:
            reading.salt = numbers(in: valueLine.text).first
        }
    }

    private static func matchedField(for label: String) -> LabelField? {
        let lower = label.lowercased()
        return LabelField.allCases.first { field in
            keywords[field]?.contains { lower.contains($0) } ?? false
        }
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
        let allowedCharacters = CharacterSet(charactersIn: "0123456789.,<≤/| ")
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
            guard !current.isEmpty, let value = Double(current.replacingOccurrences(of: ",", with: ".")) else { return }
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

    private static func applyingConsistencyChecks(_ reading: NutritionLabelReading) -> NutritionLabelReading {
        var result = reading

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

        if
            let kJ = result.energyKJ,
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
