package antoni.kalorie.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign

@Composable
fun NumericRowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    suffix: (@Composable () -> Unit)? = null,
) {
    // MARK: - Body

    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder?.let { { Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End) } },
        suffix = suffix,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End, fontWeight = fontWeight),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedPlaceholderColor = AppColors.hint,
            unfocusedPlaceholderColor = AppColors.hint,
        ),
    )
}
