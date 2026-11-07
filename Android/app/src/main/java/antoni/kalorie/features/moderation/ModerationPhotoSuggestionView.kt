package antoni.kalorie.features.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.FoodPhotoRemoteContent
import antoni.kalorie.components.LoaderIndicator
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.PrimaryButtonStyle
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (canApprove) {
                    PrimaryButton(
                        text = stringResource(R.string.moderation_button_approve),
                        onClick = { scope.launch { viewModel.onApproveTapped() } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.moderation_button_reject),
                    onClick = { scope.launch { viewModel.onRejectTapped() } },
                    style = PrimaryButtonStyle.DESTRUCTIVE,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                FoodPhotoRemoteContent(
                    url = viewModel.suggestion.photoUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                )
                Text(
                    text = itemName ?: viewModel.suggestion.itemId,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            if (state.isLoading) {
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    LoaderIndicator()
                }
            }
        }
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
