package antoni.kalorie.features.updaterequired

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.PrimaryButton
import antoni.kalorie.core.utils.Constants

// MARK: - Body

@Composable
fun UpdateRequiredView() {
    val uriHandler = LocalUriHandler.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.updateRequired_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.updateRequired_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.weight(1f))
            PrimaryButton(
                text = stringResource(R.string.updateRequired_button),
                onClick = { uriHandler.openUri(Constants.Store.PLAY_STORE_URL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// MARK: - Preview

@Preview(showBackground = true)
@Composable
private fun UpdateRequiredViewLightPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) { UpdateRequiredView() }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun UpdateRequiredViewDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) { UpdateRequiredView() }
}
