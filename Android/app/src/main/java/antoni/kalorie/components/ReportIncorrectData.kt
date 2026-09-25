package antoni.kalorie.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import antoni.kalorie.R

@Composable
fun ReportIncorrectDataMenu(hasReportedCurrentItem: Boolean, onReportTapped: () -> Unit) {

    // MARK: - Properties

    var isMenuVisible by remember { mutableStateOf(false) }

    // MARK: - Body

    IconButton(onClick = { isMenuVisible = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = null)
    }
    DropdownMenu(expanded = isMenuVisible, onDismissRequest = { isMenuVisible = false }) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (hasReportedCurrentItem) R.string.foodItemReport_button_alreadyReported else R.string.foodItemReport_button_report,
                    ),
                )
            },
            enabled = !hasReportedCurrentItem,
            onClick = {
                isMenuVisible = false
                onReportTapped()
            },
        )
    }
}

@Composable
fun ReportReasonDialog(text: String, onTextChange: (String) -> Unit, onDismiss: () -> Unit, onSend: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.foodItemReport_alert_title)) },
        text = {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.foodItemReport_alert_placeholder)) },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_button_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onSend) {
                Text(stringResource(R.string.foodItemReport_button_send))
            }
        },
    )
}
