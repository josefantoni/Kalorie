package antoni.kalorie.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

// ModalBottomSheet hands the leftover of a downward drag to the sheet once the list reaches the top, so a swipe that
// started mid-list ends up closing the sheet. Only a drag that started with the list already at the top may move it.
fun Modifier.sheetScrollGuard(): Modifier = composed {
    val connection = remember { SheetScrollGuardConnection() }
    nestedScroll(connection)
}

private class SheetScrollGuardConnection : NestedScrollConnection {
    private var didContentScrollInGesture = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) didContentScrollInGesture = true
        return if (didContentScrollInGesture && available.y > 0f) Offset(0f, available.y) else Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val result = if (didContentScrollInGesture) available else Velocity.Zero
        didContentScrollInGesture = false
        return result
    }
}
