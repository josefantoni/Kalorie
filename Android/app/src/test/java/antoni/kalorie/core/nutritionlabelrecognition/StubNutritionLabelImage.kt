package antoni.kalorie.core.nutritionlabelrecognition

import android.graphics.Bitmap

object StubNutritionLabelImage : NutritionLabelImage {
    override val rotationDegrees: Int = 0

    override fun toBitmap(): Bitmap = error("the fakes never read the bitmap")
}
