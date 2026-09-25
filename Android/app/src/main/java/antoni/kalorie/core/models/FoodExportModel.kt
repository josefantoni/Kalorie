package antoni.kalorie.core.models

enum class FoodExportFormat(val fileExtension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}
