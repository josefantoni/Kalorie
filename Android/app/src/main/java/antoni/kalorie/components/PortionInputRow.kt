package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodMeasure

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(R.string.foodPortion_field_namePlaceholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            TextField(
                value = gramsText,
                onValueChange = { onGramsTextChange(sanitizedGramsText(it)) },
                placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
                singleLine = true,
                modifier = Modifier.width(88.dp),
            )
            Text(
                text = stringResource(measure.unitSymbolRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in quickAddOptions) {
                AssistChip(
                    onClick = {
                        onNameChange(option)
                        focusManager.clearFocus()
                    },
                    label = {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun sanitizedGramsText(text: String): String {
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
