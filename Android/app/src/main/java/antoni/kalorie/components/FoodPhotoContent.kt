package antoni.kalorie.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import antoni.kalorie.core.models.FoodItemFormPhoto
import coil3.compose.SubcomposeAsyncImage

// MARK: - Body

@Composable
fun FoodPhotoContent(
    photo: FoodItemFormPhoto,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    when (photo) {
        FoodItemFormPhoto.None -> Box(modifier = modifier)
        is FoodItemFormPhoto.Local -> {
            val bitmap = remember(photo) { BitmapFactory.decodeByteArray(photo.data, 0, photo.data.size)?.asImageBitmap() }
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = contentScale, modifier = modifier)
            } else {
                Box(modifier = modifier)
            }
        }
        is FoodItemFormPhoto.Remote -> FoodPhotoRemoteContent(photo.url, modifier, contentScale)
    }
}

@Composable
fun FoodPhotoRemoteContent(url: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        },
        error = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
