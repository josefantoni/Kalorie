package antoni.kalorie.core.nutritionlabelrecognition

data class NormalizedRect(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
) {

    // MARK: - Properties

    val minX: Double get() = x
    val maxX: Double get() = x + width
    val midX: Double get() = x + width / 2
    val minY: Double get() = y
    val maxY: Double get() = y + height
    val midY: Double get() = y + height / 2
}

data class RecognizedTextLine(
    val text: String,
    val boundingBox: NormalizedRect,
)
