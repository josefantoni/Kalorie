package antoni.kalorie.core.foodphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.min

sealed class FoodPhotoProcessingError : Exception() {
    data object Undecodable : FoodPhotoProcessingError()
    data object EncodingFailed : FoodPhotoProcessingError()
}

object FoodPhotoProcessing {

    // MARK: - Properties

    const val MAX_SIDE = 1080
    const val JPEG_QUALITY = 70

    // MARK: - Functions

    fun process(data: ByteArray): ByteArray {
        val decoded = decode(data)
        val side = min(decoded.width, decoded.height)
        if (side <= 0) throw FoodPhotoProcessingError.Undecodable
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
            orientationMatrix(data),
            true,
        )
        val target = min(side, MAX_SIDE)
        val scaled = if (target == side) square else Bitmap.createScaledBitmap(square, target, target, true)
        val output = ByteArrayOutputStream()
        if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) throw FoodPhotoProcessingError.EncodingFailed
        return output.toByteArray()
    }

    // The shorter side only has to stay at or above MAX_SIDE, so a full-resolution camera frame is never held in memory.
    private fun decode(data: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val shorterSide = min(bounds.outWidth, bounds.outHeight)
        if (shorterSide <= 0) throw FoodPhotoProcessingError.Undecodable
        var sampleSize = 1
        while (shorterSide / (sampleSize * 2) >= MAX_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options) ?: throw FoodPhotoProcessingError.Undecodable
    }

    private fun orientationMatrix(data: ByteArray): Matrix {
        val orientation = ExifInterface(ByteArrayInputStream(data))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        return matrix
    }
}
