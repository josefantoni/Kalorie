package antoni.kalorie.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val BarcodeIcon: ImageVector = materialIcon(name = "BarcodeViewfinder") {
    materialPath {
        moveTo(3f, 3f)
        horizontalLineToRelative(5f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-2f)
        close()
        moveTo(21f, 3f)
        horizontalLineToRelative(-5f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(2f)
        close()
        moveTo(3f, 21f)
        horizontalLineToRelative(5f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(-3f)
        horizontalLineToRelative(-2f)
        close()
        moveTo(21f, 21f)
        horizontalLineToRelative(-5f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(-3f)
        horizontalLineToRelative(2f)
        close()
        listOf(7f to 1f, 9f to 2f, 12f to 1f, 14f to 1f, 16f to 1f).forEach { (x, w) ->
            moveTo(x, 7f)
            horizontalLineToRelative(w)
            verticalLineToRelative(10f)
            horizontalLineToRelative(-w)
            close()
        }
    }
}
