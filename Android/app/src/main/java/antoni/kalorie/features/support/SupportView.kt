package antoni.kalorie.features.support

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.SectionRowMinHeight
import antoni.kalorie.components.illustrations.BaguetteRain
import antoni.kalorie.components.illustrations.GrandmaCooking
import antoni.kalorie.components.illustrations.LunchIllustration
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.launch

private const val THANK_YOU_FADE_MILLIS = 800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportView(viewModel: SupportViewModel, onBack: () -> Unit) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val products by viewModel.products.collectAsState()
    val isThankYouVisible by viewModel.isThankYouVisible.collectAsState()
    val thankYouAnimation by viewModel.thankYouAnimation.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_button_support)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    SectionCard {
                        Text(
                            text = stringResource(R.string.support_intro),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    if (products.isNotEmpty()) {
                        SectionCard(modifier = Modifier.padding(top = 12.dp)) {
                            products.forEachIndexed { index, product ->
                                if (index > 0) SectionCardDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { scope.launch { viewModel.onProductTapped(product) } }
                                        .heightIn(min = SectionRowMinHeight)
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(text = stringResource(productTitleRes(product.id)))
                                    Text(text = product.displayPrice, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (state != SupportViewModel.State.IDLE) {
                    Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = isThankYouVisible,
            enter = fadeIn(tween(THANK_YOU_FADE_MILLIS)),
            exit = fadeOut(tween(THANK_YOU_FADE_MILLIS)),
        ) {
            ThankYouOverlay(animation = thankYouAnimation, onDismiss = viewModel::onThankYouDismissed)
        }
    }

    alertItem?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(alert.titleRes)) },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

private fun productTitleRes(productId: String): Int = when (productId) {
    Constants.Tips.SMALL_PRODUCT_ID -> R.string.support_product_tip_small
    Constants.Tips.MEDIUM_PRODUCT_ID -> R.string.support_product_tip_medium
    else -> R.string.support_product_tip_large
}

@Composable
private fun ThankYouOverlay(animation: SupportViewModel.ThankYouAnimation, onDismiss: () -> Unit) {
    when (animation) {
        SupportViewModel.ThankYouAnimation.BAGUETTES -> Box(
            modifier = Modifier.fillMaxSize().clickable(enabled = false) {},
            contentAlignment = Alignment.Center,
        ) {
            BaguetteRain()
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ThankYouText(R.string.support_thankYou_message_small)
                    PrimaryButton(
                        text = stringResource(R.string.support_thankYou_dismiss),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        SupportViewModel.ThankYouAnimation.LUNCH -> IllustratedThankYou(R.string.support_thankYou_message_medium, onDismiss) {
            LunchIllustration()
        }
        SupportViewModel.ThankYouAnimation.GRANDMA -> IllustratedThankYou(R.string.support_thankYou_message_large, onDismiss) {
            GrandmaCooking()
        }
    }
}

@Composable
private fun IllustratedThankYou(messageRes: Int, onDismiss: () -> Unit, illustration: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            illustration()
            ThankYouText(messageRes)
            PrimaryButton(
                text = stringResource(R.string.support_thankYou_dismiss),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThankYouText(messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}
