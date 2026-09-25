package antoni.kalorie.core.nutritionlabelrecognition

import android.graphics.Bitmap

interface NutritionLabelImage {
    val rotationDegrees: Int

    fun toBitmap(): Bitmap
}

class BitmapNutritionLabelImage(
    private val bitmap: Bitmap,
    override val rotationDegrees: Int,
) : NutritionLabelImage {

    // MARK: - Functions

    override fun toBitmap(): Bitmap = bitmap
}
