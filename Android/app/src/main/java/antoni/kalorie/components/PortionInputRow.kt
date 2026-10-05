package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodMeasure

@Composable
fun PortionInputRow(
    name: String,
    gramsText: String,
    measure: FoodMeasure,
    onNameChange: (String) -> Unit,
    onGramsTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
) {
    // MARK: - Properties

    val focusManager = LocalFocusManager.current
    val quickAddOptions = listOf(
        stringResource(R.string.foodPortion_quickAdd_piece),
        stringResource(R.string.foodPortion_quickAdd_package),
        stringResource(R.string.foodPortion_quickAdd_spoon),
    )

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingLabelTextField(
                title = stringResource(R.string.foodPortion_field_namePlaceholder),
                text = name,
                onTextChange = onNameChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            NumericRowTextField(
                value = gramsText,
                onValueChange = { onGramsTextChange(sanitizedGramsText(it)) },
                placeholder = "0",
                modifier = Modifier.width(88.dp),
            )
            Text(
                text = stringResource(measure.unitSymbolRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in quickAddOptions) {
                Surface(
                    onClick = {
                        onNameChange(option)
                        focusManager.clearFocus()
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f).height(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun sanitizedGramsText(text: String): String {
    var seenSeparator = false
    return text.filter { char ->
        if (char == '.' || char == ',') {
            if (seenSeparator) return@filter false
            seenSeparator = true
            true
        } else {
            char in '0'..'9'
        }
    }
}
