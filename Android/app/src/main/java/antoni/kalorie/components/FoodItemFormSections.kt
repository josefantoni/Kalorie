package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.features.addfoodsheet.FoodItemFormInput
import java.math.BigDecimal

sealed interface FoodItemFormBarcodeRow {
    data object Hidden : FoodItemFormBarcodeRow
    data object Locked : FoodItemFormBarcodeRow
    data class Editable(val onScanTapped: () -> Unit) : FoodItemFormBarcodeRow
}

@Composable
fun FoodItemFormSections(
    formInput: FoodItemFormInput,
    onFormInputChange: (FoodItemFormInput) -> Unit,
    modifier: Modifier = Modifier,
    barcodeRow: FoodItemFormBarcodeRow = FoodItemFormBarcodeRow.Hidden,
) {

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth()) {
        FoodPortionsSection(
            portions = formInput.portions,
            onPortionsChange = { onFormInputChange(formInput.copy(portions = it)) },
            measure = formInput.measure,
        )
        TextField(
            value = formInput.name,
            onValueChange = { onFormInputChange(formInput.copy(name = it)) },
            label = { Text(stringResource(R.string.addFood_field_name_title)) },
            placeholder = { Text(stringResource(R.string.addFood_field_name_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        when (barcodeRow) {
            FoodItemFormBarcodeRow.Hidden -> Unit
            FoodItemFormBarcodeRow.Locked -> TextField(
                value = formInput.scannedCode,
                onValueChange = {},
                enabled = false,
                label = { Text(stringResource(R.string.addFood_field_barcode_title)) },
                placeholder = {
                    Text(
                        stringResource(
                            if (formInput.scannedCode.isEmpty()) R.string.addFood_field_barcode_missingLabel else R.string.addFood_field_barcode_placeholder,
                        ),
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            is FoodItemFormBarcodeRow.Editable -> Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                TextField(
                    value = formInput.scannedCode,
                    onValueChange = { onFormInputChange(formInput.copy(scannedCode = it.filter { char -> char in '0'..'9' })) },
                    label = { Text(stringResource(R.string.addFood_field_barcode_title)) },
                    placeholder = { Text(stringResource(R.string.addFood_field_barcode_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = barcodeRow.onScanTapped) {
                            Icon(BarcodeIcon, contentDescription = stringResource(R.string.addFood_nutritionLabel_barcodeScanAccessibility))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (formInput.scannedCode.isEmpty()) {
                    Text(
                        text = stringResource(R.string.addFood_warning_missingBarcode),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB58900),
                    )
                }
            }
        }
        FoodItemFormFields(formInput = formInput, onFormInputChange = onFormInputChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemFormFields(formInput: FoodItemFormInput, onFormInputChange: (FoodItemFormInput) -> Unit, modifier: Modifier = Modifier) {

    // MARK: - Properties

    var isWeightUnitMenuVisible by remember { mutableStateOf(false) }
    val measures = FoodMeasure.entries

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.addFood_field_measure), modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(180.dp)) {
                measures.forEachIndexed { index, measure ->
                    SegmentedButton(
                        selected = formInput.measure == measure,
                        onClick = { onFormInputChange(formInput.copy(measure = measure)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = measures.size),
                    ) {
                        Text(stringResource(measure.unitSymbolRes))
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(R.string.addFood_field_weight), modifier = Modifier.weight(1f))
            DecimalTextField(
                value = formInput.weightOfProduct,
                onValueChange = { onFormInputChange(formInput.copy(weightOfProduct = it)) },
                modifier = Modifier.width(100.dp),
            )
            TextButton(onClick = { isWeightUnitMenuVisible = true }) {
                Text(stringResource(if (formInput.isWeightInThousands) formInput.measure.thousandUnitSymbolRes else formInput.measure.unitSymbolRes))
            }
            DropdownMenu(expanded = isWeightUnitMenuVisible, onDismissRequest = { isWeightUnitMenuVisible = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(formInput.measure.unitSymbolRes)) },
                    onClick = {
                        isWeightUnitMenuVisible = false
                        onFormInputChange(formInput.withWeightInThousands(false))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(formInput.measure.thousandUnitSymbolRes)) },
                    onClick = {
                        isWeightUnitMenuVisible = false
                        onFormInputChange(formInput.withWeightInThousands(true))
                    },
                )
            }
        }
        val grams = stringResource(R.string.common_unit_grams)
        FormDoubleRow(stringResource(R.string.addFood_field_energyKJ), "kJ", formInput.energyKJ) { onFormInputChange(formInput.copy(energyKJ = it)) }
        FormDoubleRow(
            title = stringResource(
                if (formInput.measure == FoodMeasure.GRAMS) R.string.addFood_field_caloriesPer100g else R.string.addFood_field_caloriesPer100ml,
            ),
            unit = "kcal",
            value = formInput.caloriesPerHundredGrams,
        ) { onFormInputChange(formInput.copy(caloriesPerHundredGrams = it)) }
        FormDoubleRow(stringResource(R.string.addFood_field_protein), grams, formInput.protein) { onFormInputChange(formInput.copy(protein = it)) }
        FormDoubleRow(stringResource(R.string.addFood_field_carbs), grams, formInput.carbohydrate) { onFormInputChange(formInput.copy(carbohydrate = it)) }
        FormDoubleRow(stringResource(R.string.addFood_field_carbsSugar), grams, formInput.carbohydratePureSugar) {
            onFormInputChange(formInput.copy(carbohydratePureSugar = it))
        }
        FormDoubleRow(stringResource(R.string.addFood_field_fiber), grams, formInput.fiber ?: 0.0) { onFormInputChange(formInput.copy(fiber = it)) }
        FormDoubleRow(stringResource(R.string.addFood_field_fat), grams, formInput.fat) { onFormInputChange(formInput.copy(fat = it)) }
        FormDoubleRow(stringResource(R.string.addFood_field_fatSaturated), grams, formInput.fatSaturated ?: 0.0) {
            onFormInputChange(formInput.copy(fatSaturated = it))
        }
        FormDoubleRow(stringResource(R.string.addFood_field_fatUnsaturated), grams, formInput.fatUnsaturatedFattyAcids) {
            onFormInputChange(formInput.copy(fatUnsaturatedFattyAcids = it))
        }
        FormDoubleRow(stringResource(R.string.addFood_field_salt), grams, formInput.salt) { onFormInputChange(formInput.copy(salt = it)) }
    }
}

@Composable
private fun FormDoubleRow(title: String, unit: String, value: Double, onValueChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        DecimalTextField(value = value, onValueChange = onValueChange, modifier = Modifier.width(100.dp))
        Text(text = unit, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun DecimalTextField(value: Double, onValueChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(formattedDecimal(value)) }

    LaunchedEffect(value) {
        if ((text.replace(',', '.').toDoubleOrNull() ?: 0.0) != value) text = formattedDecimal(value)
    }

    TextField(
        value = text,
        onValueChange = { newText ->
            val sanitized = sanitizedGramsText(newText)
            text = sanitized
            onValueChange(sanitized.replace(',', '.').toDoubleOrNull() ?: 0.0)
        },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
        singleLine = true,
        modifier = modifier,
    )
}

private fun formattedDecimal(value: Double): String =
    if (value == 0.0) "" else BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private fun FoodItemFormInput.withWeightInThousands(newValue: Boolean): FoodItemFormInput {
    if (newValue == isWeightInThousands) return this
    return copy(
        weightOfProduct = if (newValue) weightOfProduct / 1000 else weightOfProduct * 1000,
        isWeightInThousands = newValue,
    )
}
