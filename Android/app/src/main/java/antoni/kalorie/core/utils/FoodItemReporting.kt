package antoni.kalorie.core.utils

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemReportError
import antoni.kalorie.core.usecases.FetchMyFoodItemReportUseCaseProtocol
import antoni.kalorie.core.usecases.SubmitFoodItemReportUseCaseProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

interface FoodItemReporting {
    val hasReportedCurrentItem: MutableStateFlow<Boolean>
    val isSubmittingReport: MutableStateFlow<Boolean>
    val isReportReasonAlertVisible: MutableStateFlow<Boolean>
    val reportReasonText: MutableStateFlow<String>
    val alertItem: MutableStateFlow<AlertItem?>

    // MARK: - Functions

    suspend fun loadReportState(barcode: String, fetchMyFoodItemReport: FetchMyFoodItemReportUseCaseProtocol) {
        try {
            hasReportedCurrentItem.value = fetchMyFoodItemReport(barcode) != null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.FOOD_ITEM_REPORT)
            hasReportedCurrentItem.value = false
        }
    }

    fun onReportIncorrectDataTapped() {
        if (hasReportedCurrentItem.value) return
        reportReasonText.value = ""
        isReportReasonAlertVisible.value = true
    }

    suspend fun onReportSubmitted(barcode: String, submitFoodItemReport: SubmitFoodItemReportUseCaseProtocol) {
        if (isSubmittingReport.value) return
        isSubmittingReport.value = true
        try {
            submitFoodItemReport(barcode, reportReasonText.value)
            hasReportedCurrentItem.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: FoodItemReportError.ReasonRequired) {
            alertItem.value = AlertItem(titleRes = R.string.foodItemReport_error_reasonRequired)
        } catch (_: FoodItemReportError.ReasonTooLong) {
            alertItem.value = AlertItem(titleRes = R.string.foodItemReport_error_reasonTooLong)
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.FOOD_ITEM_REPORT)
            alertItem.value = AlertItem(titleRes = R.string.common_error_unknown)
        } finally {
            isSubmittingReport.value = false
        }
    }
}
