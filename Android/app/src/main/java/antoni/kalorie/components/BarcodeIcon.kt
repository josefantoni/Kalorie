package antoni.kalorie.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val BarcodeIcon: ImageVector = materialIcon(name = "Barcode") {
    materialPath {
        listOf(2f to 2f, 6f to 1f, 9f to 3f, 14f to 1f, 17f to 2f, 20f to 2f).forEach { (x, w) ->
            moveTo(x + 0f, 5f)
            horizontalLineToRelative(w)
            verticalLineToRelative(14f)
            horizontalLineToRelative(-w)
            close()
        }
    }
}
