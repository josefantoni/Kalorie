package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun NumericRowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    fontWeight: FontWeight = FontWeight.Normal,
    suffix: (@Composable () -> Unit)? = null,
    fieldAlignment: Alignment = Alignment.Center,
) {
    // MARK: - Properties

    var isFocused by remember { mutableStateOf(false) }
    val underlineColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    // MARK: - Body

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = SectionRowMinHeight).onFocusChanged { isFocused = it.isFocused },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            textAlign = TextAlign.End,
            fontWeight = fontWeight,
            color = LocalContentColor.current,
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.heightIn(min = SectionRowMinHeight), contentAlignment = fieldAlignment) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FIELD_MIN_HEIGHT)
                        .drawBehind {
                            val stroke = (if (isFocused) FOCUSED_UNDERLINE else UNFOCUSED_UNDERLINE).toPx()
                            drawLine(underlineColor, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
                        }
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                color = AppColors.hint,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.End,
                            )
                        }
                        innerTextField()
                    }
                    suffix?.invoke()
                }
            }
        },
    )
}

private val FIELD_MIN_HEIGHT = 36.dp
private val FOCUSED_UNDERLINE = 2.dp
private val UNFOCUSED_UNDERLINE = 1.dp
