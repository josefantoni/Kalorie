package antoni.kalorie.features.export

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodExportFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportView(viewModel: ExportViewModel, onBack: () -> Unit) {

    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val fromDate by viewModel.fromDate.collectAsState()
    val toDate by viewModel.toDate.collectAsState()
    val format by viewModel.format.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editedDate by remember { mutableStateOf<EditedDate?>(null) }
    val isExportDisabled = state == ExportViewModel.State.GENERATING ||
        fromDate.atZone(ZoneId.systemDefault()).toLocalDate().isAfter(toDate.atZone(ZoneId.systemDefault()).toLocalDate())

    LaunchedEffect(exportedFile) {
        val exported = exportedFile ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported.file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = exported.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
        viewModel.onShareFinished()
    }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_navigationTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                DateRow(label = stringResource(R.string.export_datePicker_from), date = fromDate) { editedDate = EditedDate.FROM }
                DateRow(label = stringResource(R.string.export_datePicker_to), date = toDate) { editedDate = EditedDate.TO }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = stringResource(R.string.export_picker_format), modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        FoodExportFormat.entries.forEachIndexed { index, entry ->
                            SegmentedButton(
                                selected = format == entry,
                                onClick = { viewModel.format.value = entry },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = FoodExportFormat.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        when (entry) {
                                            FoodExportFormat.PDF -> R.string.export_format_pdf
                                            FoodExportFormat.XLSX -> R.string.export_format_excel
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { scope.launch { viewModel.onExportTapped() } },
                    enabled = !isExportDisabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.export_button_export))
                }
            }
            if (state == ExportViewModel.State.GENERATING) {
                Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    editedDate?.let { edited ->
        val initial = if (edited == EditedDate.FROM) fromDate else toDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { editedDate = null },
            dismissButton = {
                TextButton(onClick = { editedDate = null }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                            if (edited == EditedDate.FROM) viewModel.fromDate.value = day else viewModel.toDate.value = day
                        }
                        editedDate = null
                    },
                ) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    alertItem?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(alert.titleRes)) },
            text = alert.messageRes?.let { messageRes -> { Text(stringResource(messageRes)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

private enum class EditedDate { FROM, TO }

@Composable
private fun DateRow(label: String, date: Instant, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(
            text = date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
