package antoni.kalorie.features.moderation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionStack
import antoni.kalorie.components.bottomOnly
import antoni.kalorie.components.excludingBottom
import antoni.kalorie.components.sheetBottomBar
import antoni.kalorie.components.sheetContentWindowInsets
import antoni.kalorie.components.sheetScrollGuard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationResponseSheet(
    draft: ModerationResponseDraft,
    isSending: Boolean,
    onTextChange: (String) -> Unit,
    onErrorDismissed: () -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    // MARK: - Body

    ModalBottomSheet(
        onDismissRequest = { if (!isSending) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = { sheetContentWindowInsets() },
    ) {
        Scaffold(
            modifier = Modifier.sheetScrollGuard(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.moderation_response_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isSending) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_button_cancel))
                        }
                    },
                )
            },
            bottomBar = {
                PrimaryButton(
                    text = stringResource(R.string.moderation_response_send),
                    onClick = onSend,
                    enabled = draft.isSendable && !isSending,
                    modifier = Modifier.sheetBottomBar().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding.excludingBottom())) {
                SectionStack(contentPadding = innerPadding.bottomOnly()) {
                    SectionCard {
                        TextField(
                            value = draft.text,
                            onValueChange = onTextChange,
                            label = { Text(stringResource(R.string.moderation_response_title)) },
                            minLines = 4,
                            maxLines = 10,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (isSending) {
                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        LoaderIndicator()
                    }
                }
            }
        }
    }

    draft.errorTitleRes?.let { errorTitleRes ->
        AlertDialog(
            onDismissRequest = onErrorDismissed,
            title = { Text(stringResource(errorTitleRes)) },
            confirmButton = {
                TextButton(onClick = onErrorDismissed) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}
