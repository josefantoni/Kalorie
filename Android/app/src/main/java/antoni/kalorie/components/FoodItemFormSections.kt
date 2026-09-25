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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.font.FontWeight
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
    highlightedFields: Set<FoodItemFormField> = emptySet(),
    onNutritionLabelScanTapped: (() -> Unit)? = null,
    onFieldEdited: (FoodItemFormField) -> Unit = {},
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
            onValueChange = {
                onFormInputChange(formInput.copy(name = it))
                onFieldEdited(FoodItemFormField.NAME)
            },
            label = { Text(stringResource(R.string.addFood_field_name_title)) },
            placeholder = { Text(stringResource(R.string.addFood_field_name_placeholder)) },
            textStyle = LocalTextStyle.current.copy(fontWeight = highlightWeight(FoodItemFormField.NAME in highlightedFields)),
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
        onNutritionLabelScanTapped?.let { onScanTapped ->
            TextButton(onClick = onScanTapped, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.addFood_button_scanNutritionLabel))
            }
        }
        FoodItemFormFields(
            formInput = formInput,
            onFormInputChange = onFormInputChange,
            highlightedFields = highlightedFields,
            onFieldEdited = onFieldEdited,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemFormFields(
    formInput: FoodItemFormInput,
    onFormInputChange: (FoodItemFormInput) -> Unit,
    modifier: Modifier = Modifier,
    highlightedFields: Set<FoodItemFormField> = emptySet(),
    onFieldEdited: (FoodItemFormField) -> Unit = {},
) {

    // MARK: - Properties

    var isWeightUnitMenuVisible by remember { mutableStateOf(false) }
    val measures = FoodMeasure.entries
    fun edit(field: FoodItemFormField, transform: FoodItemFormInput.() -> FoodItemFormInput) {
        onFormInputChange(formInput.transform())
        onFieldEdited(field)
    }
    fun isHighlighted(field: FoodItemFormField) = field in highlightedFields

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.addFood_field_measure),
                fontWeight = highlightWeight(isHighlighted(FoodItemFormField.MEASURE)),
                modifier = Modifier.weight(1f),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(180.dp)) {
                measures.forEachIndexed { index, measure ->
                    SegmentedButton(
                        selected = formInput.measure == measure,
                        onClick = { edit(FoodItemFormField.MEASURE) { copy(measure = measure) } },
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
            Text(
                text = stringResource(R.string.addFood_field_weight),
                fontWeight = highlightWeight(isHighlighted(FoodItemFormField.WEIGHT)),
                modifier = Modifier.weight(1f),
            )
            DecimalTextField(
                value = formInput.weightOfProduct,
                onValueChange = { edit(FoodItemFormField.WEIGHT) { copy(weightOfProduct = it) } },
                isHighlighted = isHighlighted(FoodItemFormField.WEIGHT),
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
        FormDoubleRow(stringResource(R.string.addFood_field_energyKJ), "kJ", formInput.energyKJ, isHighlighted(FoodItemFormField.ENERGY_KJ)) {
            edit(FoodItemFormField.ENERGY_KJ) { copy(energyKJ = it) }
        }
        FormDoubleRow(
            title = stringResource(
                if (formInput.measure == FoodMeasure.GRAMS) R.string.addFood_field_caloriesPer100g else R.string.addFood_field_caloriesPer100ml,
            ),
            unit = "kcal",
            value = formInput.caloriesPerHundredGrams,
            isHighlighted = isHighlighted(FoodItemFormField.CALORIES),
        ) { edit(FoodItemFormField.CALORIES) { copy(caloriesPerHundredGrams = it) } }
        FormDoubleRow(stringResource(R.string.addFood_field_protein), grams, formInput.protein, isHighlighted(FoodItemFormField.PROTEIN)) {
            edit(FoodItemFormField.PROTEIN) { copy(protein = it) }
        }
        FormDoubleRow(stringResource(R.string.addFood_field_carbs), grams, formInput.carbohydrate, isHighlighted(FoodItemFormField.CARBOHYDRATE)) {
            edit(FoodItemFormField.CARBOHYDRATE) { copy(carbohydrate = it) }
        }
        FormDoubleRow(
            stringResource(R.string.addFood_field_carbsSugar),
            grams,
            formInput.carbohydratePureSugar,
            isHighlighted(FoodItemFormField.CARBOHYDRATE_SUGAR),
        ) { edit(FoodItemFormField.CARBOHYDRATE_SUGAR) { copy(carbohydratePureSugar = it) } }
        FormDoubleRow(stringResource(R.string.addFood_field_fiber), grams, formInput.fiber ?: 0.0, isHighlighted(FoodItemFormField.FIBER)) {
            edit(FoodItemFormField.FIBER) { copy(fiber = it) }
        }
        FormDoubleRow(stringResource(R.string.addFood_field_fat), grams, formInput.fat, isHighlighted(FoodItemFormField.FAT)) {
            edit(FoodItemFormField.FAT) { copy(fat = it) }
        }
        FormDoubleRow(
            stringResource(R.string.addFood_field_fatSaturated),
            grams,
            formInput.fatSaturated ?: 0.0,
            isHighlighted(FoodItemFormField.FAT_SATURATED),
        ) { edit(FoodItemFormField.FAT_SATURATED) { copy(fatSaturated = it) } }
        FormDoubleRow(
            stringResource(R.string.addFood_field_fatUnsaturated),
            grams,
            formInput.fatUnsaturatedFattyAcids,
            isHighlighted(FoodItemFormField.FAT_UNSATURATED),
        ) { edit(FoodItemFormField.FAT_UNSATURATED) { copy(fatUnsaturatedFattyAcids = it) } }
        FormDoubleRow(stringResource(R.string.addFood_field_salt), grams, formInput.salt, isHighlighted(FoodItemFormField.SALT)) {
            edit(FoodItemFormField.SALT) { copy(salt = it) }
        }
    }
}

@Composable
private fun FormDoubleRow(title: String, unit: String, value: Double, isHighlighted: Boolean, onValueChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        DecimalTextField(value = value, onValueChange = onValueChange, isHighlighted = isHighlighted, modifier = Modifier.width(100.dp))
        Text(text = unit, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun DecimalTextField(value: Double, onValueChange: (Double) -> Unit, isHighlighted: Boolean, modifier: Modifier = Modifier) {
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
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End, fontWeight = highlightWeight(isHighlighted)),
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

private fun highlightWeight(isHighlighted: Boolean): FontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
