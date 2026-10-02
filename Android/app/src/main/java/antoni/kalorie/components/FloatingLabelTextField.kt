package antoni.kalorie.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
) {
    // MARK: - Body

    val hintColor = AppColors.hint
    val errorColor = AppColors.error

    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier,
        enabled = enabled,
        textStyle = LocalTextStyle.current.copy(fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal),
        label = { SingleLineText(title) },
        placeholder = placeholder?.let { { SingleLineText(it) } },
        trailingIcon = trailingIcon,
        supportingText = message?.let { { Text(it.text, color = it.color) } },
        isError = message is FloatingLabelTextFieldMessage.Error,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedLabelColor = hintColor,
            unfocusedLabelColor = hintColor,
            focusedPlaceholderColor = hintColor,
            unfocusedPlaceholderColor = hintColor,
            errorLabelColor = errorColor,
            errorIndicatorColor = errorColor,
            errorCursorColor = errorColor,
        ),
    )
}

// MARK: - Functions

@Composable
private fun SingleLineText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
    )
}
