package antoni.kalorie.features.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.FoodPhotoRemoteContent
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionStack
import antoni.kalorie.components.bottomOnly
import antoni.kalorie.components.excludingBottom
import antoni.kalorie.components.sheetBottomBar
import antoni.kalorie.core.utils.isLoading
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationPhotoSuggestionView(
    viewModel: ModerationPhotoSuggestionViewModel,
    onDismiss: () -> Unit,
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val itemName by viewModel.itemName.collectAsState()
    val canApprove by viewModel.canApprove.collectAsState()
    val shouldDismiss by viewModel.shouldDismiss.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val responseDraft by viewModel.responseDraft.collectAsState()
    val isSendingResponse by viewModel.isSendingResponse.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    LaunchedEffect(shouldDismiss) { if (shouldDismiss) onDismiss() }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderation_photoSuggestion_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.sheetBottomBar().fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (canApprove) {
                    PrimaryButton(
                        text = stringResource(R.string.moderation_button_approve),
                        onClick = viewModel::onApproveTapped,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.moderation_button_reject),
                    onClick = viewModel::onRejectTapped,
                    style = PrimaryButtonStyle.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding.excludingBottom())) {
            SectionStack(contentPadding = innerPadding.bottomOnly()) {
                SectionCard {
                    FoodPhotoRemoteContent(
                        url = viewModel.suggestion.photoUrl,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    SectionCardDivider()
                    Text(
                        text = itemName ?: viewModel.suggestion.itemId,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            if (state.isLoading) {
                Box(modifier = Modifier.matchParentSize().padding(innerPadding.bottomOnly()), contentAlignment = Alignment.Center) {
                    LoaderIndicator()
                }
            }
        }
    }

    responseDraft?.let { draft ->
        ModerationResponseSheet(
            draft = draft,
            isSending = isSendingResponse,
            onTextChange = viewModel::onResponseTextChanged,
            onErrorDismissed = viewModel::onResponseErrorDismissed,
            onSend = { scope.launch { viewModel.onResponseSendTapped() } },
            onDismiss = viewModel::onResponseDismissed,
        )
    }

    alertItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(item.titleRes)) },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}
