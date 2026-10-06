package antoni.kalorie.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface FloatingLabelTextFieldMessage {
    val text: String
    val color: Color
        @Composable get

    data class Hint(override val text: String) : FloatingLabelTextFieldMessage {
        override val color: Color
            @Composable get() = AppColors.hint
    }

    data class Warning(override val text: String) : FloatingLabelTextFieldMessage {
        override val color: Color
            @Composable get() = AppColors.warning
    }

    data class Error(override val text: String) : FloatingLabelTextFieldMessage {
        override val color: Color
            @Composable get() = AppColors.error
    }
}

@Composable
fun FloatingLabelTextField(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    message: FloatingLabelTextFieldMessage? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isHighlighted: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: (@Composable () -> Unit)? = null,
    isUnderlineFullWidth: Boolean = false,
) {
    // MARK: - Properties

    val hintColor = AppColors.hint
    val errorColor = AppColors.error
    val isError = message is FloatingLabelTextFieldMessage.Error
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val underlineColor = when {
        isError -> errorColor
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedLabelColor = hintColor,
        unfocusedLabelColor = hintColor,
        focusedPlaceholderColor = hintColor,
        unfocusedPlaceholderColor = hintColor,
        errorLabelColor = errorColor,
        errorCursorColor = errorColor,
    )
    val textStyle = LocalTextStyle.current.copy(
        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
        color = when {
            !enabled -> colors.disabledTextColor
            isError -> colors.errorTextColor
            isFocused -> colors.focusedTextColor
            else -> colors.unfocusedTextColor
        },
    )

    // MARK: - Body

    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = modifier
                .defaultMinSize(minWidth = TextFieldDefaults.MinWidth, minHeight = TextFieldDefaults.MinHeight)
                .then(if (message is FloatingLabelTextFieldMessage.Error) Modifier.semantics { error(message.text) } else Modifier),
            enabled = enabled,
            textStyle = textStyle,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = text,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = { SingleLineText(title) },
                    placeholder = placeholder?.let { { SingleLineText(it) } },
                    trailingIcon = trailingIcon,
                    supportingText = message?.let { { Text(it.text, color = it.color) } },
                    colors = colors,
                    container = {
                        Box(
                            modifier = Modifier.fillMaxSize().drawBehind {
                                val stroke = (if (isFocused || isError) FOCUSED_UNDERLINE else UNFOCUSED_UNDERLINE).toPx()
                                val y = size.height - stroke / 2
                                drawLine(underlineColor, Offset(if (isUnderlineFullWidth) 0f else UNDERLINE_INSET.toPx(), y), Offset(size.width, y), stroke)
                            },
                        )
                    },
                )
            },
        )
    }
}

private val UNDERLINE_INSET = 16.dp
private val FOCUSED_UNDERLINE = 2.dp
private val UNFOCUSED_UNDERLINE = 1.dp

// MARK: - Functions

@Composable
private fun SingleLineText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
    )
}
