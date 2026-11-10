package antoni.kalorie.features.dailygoals

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import antoni.kalorie.R
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.models.GoalActivityLevel
import antoni.kalorie.core.models.GoalEstimate
import antoni.kalorie.core.models.GoalLimits
import antoni.kalorie.core.models.GoalMacroGrams
import antoni.kalorie.core.models.GoalMacroMode
import antoni.kalorie.core.models.GoalSex
import antoni.kalorie.core.models.GoalVariant
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCaseProtocol
import antoni.kalorie.core.usecases.SaveBodyWeightUseCaseProtocol
import antoni.kalorie.core.usecases.SaveDailyGoalUseCase
import antoni.kalorie.core.usecases.SaveDailyGoalUseCaseProtocol
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import antoni.kalorie.macrokit.energyShares
import antoni.kalorie.macrokit.gramsFromKcal
import antoni.kalorie.macrokit.gramsKcalDiffersFromGoal
import antoni.kalorie.macrokit.referenceKcal
import antoni.kalorie.macrokit.roundedToHundred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

enum class GoalMacro {
    PROTEIN,
    CARBS,
    FAT,
}

class DailyGoalsViewModel(
    history: DailyGoalHistory,
    val unit: BodyWeightUnit,
    private val fetchBodyWeights: FetchBodyWeightsUseCaseProtocol,
    private val saveDailyGoal: SaveDailyGoalUseCaseProtocol,
    private val saveBodyWeight: SaveBodyWeightUseCaseProtocol,
    private val now: () -> Instant = { Instant.now() },
    private val onChanged: (DailyGoalHistory) -> Unit,
) : ViewModel() {

    // MARK: - Properties

    private val _kcalText = MutableStateFlow("")
    val kcalText: StateFlow<String> = _kcalText
    private val _macroMode = MutableStateFlow(GoalMacroMode.PERCENT)
    val macroMode: StateFlow<GoalMacroMode> = _macroMode
    private val _proteinText = MutableStateFlow("")
    val proteinText: StateFlow<String> = _proteinText
    private val _carbsText = MutableStateFlow("")
    val carbsText: StateFlow<String> = _carbsText
    private val _fatText = MutableStateFlow("")
    val fatText: StateFlow<String> = _fatText
    private val _sex = MutableStateFlow<GoalSex?>(null)
    val sex: StateFlow<GoalSex?> = _sex
    private val _birthYear = MutableStateFlow<Int?>(null)
    val birthYear: StateFlow<Int?> = _birthYear
    private val _heightCm = MutableStateFlow<Int?>(null)
    val heightCm: StateFlow<Int?> = _heightCm
    private val _weightKilograms = MutableStateFlow<Double?>(null)
    val weightKilograms: StateFlow<Double?> = _weightKilograms
    private val _activityLevel = MutableStateFlow<GoalActivityLevel?>(null)
    val activityLevel: StateFlow<GoalActivityLevel?> = _activityLevel
    private val _selectedVariantIndex = MutableStateFlow(GoalEstimate.percentChanges.indexOf(0).coerceAtLeast(0).toDouble())
    val selectedVariantIndex: StateFlow<Double> = _selectedVariantIndex
    private val _estimate = MutableStateFlow<GoalEstimate?>(null)
    val estimate: StateFlow<GoalEstimate?> = _estimate
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving
    private val _didFinish = MutableStateFlow(false)
    val didFinish: StateFlow<Boolean> = _didFinish
    private val _rejectedMacroMode = MutableStateFlow<GoalMacroMode?>(null)
    val rejectedMacroMode: StateFlow<GoalMacroMode?> = _rejectedMacroMode
    private val _kcalFocusRequestCount = MutableStateFlow(0)
    val kcalFocusRequestCount: StateFlow<Int> = _kcalFocusRequestCount
    private val _isBodyDataCollapsed = MutableStateFlow(false)
    val isBodyDataCollapsed: StateFlow<Boolean> = _isBodyDataCollapsed
    private val _isActivityCollapsed = MutableStateFlow(false)
    val isActivityCollapsed: StateFlow<Boolean> = _isActivityCollapsed
    val isWeightPickerVisible = MutableStateFlow(false)
    val isBirthYearPickerVisible = MutableStateFlow(false)
    val isHeightPickerVisible = MutableStateFlow(false)
    val isEndConfirmationVisible = MutableStateFlow(false)
    val alertItem = MutableStateFlow<AlertItem?>(null)

    val hasActiveGoal: Boolean
    val isBodyDataRemembered: Boolean
    val isActivityRemembered: Boolean

    private var rejectedMacroModeBlock: MacroModeSwitchBlock? = null
    private var gramsSplitAnchor: List<Double>? = null
    private var initialInputs: Inputs? = null
    private var isConvertingMacros = false
    private var isWeightDefaulted = false

    // MARK: - Init

    init {
        val dayKey = BodyWeightDayKey.make(now())
        val latest = history.latestEntry(dayKey)
        val profile = history.latestProfileEntry(dayKey) ?: latest
        val active = latest?.takeIf { it.isActive }
        hasActiveGoal = active != null
        _kcalText.value = active?.kcalGoal?.let(::format).orEmpty()
        _macroMode.value = active?.macroMode ?: GoalMacroMode.PERCENT
        if (active != null && active.macroMode == GoalMacroMode.GRAMS) {
            _proteinText.value = active.proteinGrams?.let(::format).orEmpty()
            _carbsText.value = active.carbsGrams?.let(::format).orEmpty()
            _fatText.value = active.fatGrams?.let(::format).orEmpty()
        } else if (active != null) {
            _proteinText.value = active.proteinPercent?.let(::format).orEmpty()
            _carbsText.value = active.carbsPercent?.let(::format).orEmpty()
            _fatText.value = active.fatPercent?.let(::format).orEmpty()
        } else {
            _proteinText.value = DEFAULT_PROTEIN_PERCENT
            _carbsText.value = DEFAULT_CARBS_PERCENT
            _fatText.value = DEFAULT_FAT_PERCENT
        }
        _sex.value = profile?.sex ?: GoalSex.MALE
        _birthYear.value = profile?.birthYear
        _heightCm.value = profile?.heightCm?.roundToInt()
        _weightKilograms.value = profile?.weightKg
        _activityLevel.value = profile?.activityLevel ?: GoalActivityLevel.LIGHT
        isBodyDataRemembered = profile?.sex != null && profile.birthYear != null && profile.heightCm != null && profile.weightKg != null
        isActivityRemembered = profile?.activityLevel != null
        _isBodyDataCollapsed.value = isBodyDataRemembered
        _isActivityCollapsed.value = isActivityRemembered
        if (active == null) {
            _kcalText.value = referenceKcalText(isMale = (profile?.sex ?: GoalSex.MALE) == GoalSex.MALE)
        }
        updateEstimate()
        matchingVariantIndex?.let { _selectedVariantIndex.value = it.toDouble() }
        initialInputs = inputs
    }

    // MARK: - Types

    private data class Inputs(
        val kcalText: String,
        val macroMode: GoalMacroMode,
        val proteinText: String,
        val carbsText: String,
        val fatText: String,
        val sex: GoalSex?,
        val birthYear: Int?,
        val heightCm: Int?,
        val weightKilograms: Double?,
        val activityLevel: GoalActivityLevel?,
    )

    private enum class MacroModeSwitchBlock {
        INCOMPLETE_MACROS,
        MISSING_KCAL,
        PERCENT_SUM_NOT_100,
    }

    // MARK: - Computed

    private val inputs: Inputs
        get() = Inputs(
            kcalText = _kcalText.value,
            macroMode = _macroMode.value,
            proteinText = _proteinText.value,
            carbsText = _carbsText.value,
            fatText = _fatText.value,
            sex = _sex.value,
            birthYear = _birthYear.value,
            heightCm = _heightCm.value,
            weightKilograms = _weightKilograms.value,
            activityLevel = _activityLevel.value,
        )

    val kcal: Double? get() = parse(_kcalText.value)

    val protein: Double? get() = parse(_proteinText.value)

    val carbs: Double? get() = parse(_carbsText.value)

    val fat: Double? get() = parse(_fatText.value)

    val currentYear: Int get() = BodyWeightDayKey.year(now())

    val birthYears: List<Int>
        get() = ((currentYear - GoalLimits.MAX_AGE)..(currentYear - GoalLimits.MIN_AGE)).toList().reversed()

    val percentSum: Double?
        get() {
            val protein = protein ?: return null
            val carbs = carbs ?: return null
            val fat = fat ?: return null
            return protein + carbs + fat
        }

    val isPercentSumInvalid: Boolean
        get() {
            if (_macroMode.value != GoalMacroMode.PERCENT) return false
            val sum = percentSum ?: return true
            return abs(sum - PERCENT_TOTAL) > PERCENT_TOLERANCE
        }

    val gramsInPercentMode: GoalMacroGrams?
        get() {
            if (_macroMode.value != GoalMacroMode.PERCENT) return null
            val goal = DailyGoalDomain(
                validFrom = "",
                kcalGoal = kcal ?: return null,
                macroMode = GoalMacroMode.PERCENT,
                proteinPercent = protein ?: return null,
                carbsPercent = carbs ?: return null,
                fatPercent = fat ?: return null,
            )
            return goal.macroGrams
        }

    val kcalOfEnteredGrams: Double?
        get() {
            if (_macroMode.value != GoalMacroMode.GRAMS) return null
            return GoalMacroGrams(protein = protein ?: return null, carbs = carbs ?: return null, fat = fat ?: return null).kcal
        }

    val kcalOfEnteredGramsDifferingFromGoal: Double?
        get() {
            val grams = kcalOfEnteredGrams ?: return null
            val goal = kcal ?: return null
            return grams.takeIf { gramsKcalDiffersFromGoal(it, goal) }
        }

    val matchingVariantIndex: Int?
        get() = _estimate.value?.variants?.indexOfFirst { it.kcal.toDouble() == kcal }?.takeIf { it >= 0 }

    val selectedVariant: GoalVariant?
        get() = matchingVariantIndex?.let { _estimate.value?.variants?.get(it) }

    val kcalPlaceholder: String
        get() = when (_sex.value) {
            GoalSex.MALE -> referenceKcalText(isMale = true)
            GoalSex.FEMALE -> referenceKcalText(isMale = false)
            null -> "${referenceKcalText(isMale = false)}–${referenceKcalText(isMale = true)}"
        }

    val canSave: Boolean
        get() {
            val macroRange = if (_macroMode.value == GoalMacroMode.PERCENT) {
                SaveDailyGoalUseCase.MACRO_PERCENT_RANGE
            } else {
                SaveDailyGoalUseCase.MACRO_GRAMS_RANGE
            }
            if (_isSaving.value) return false
            val kcal = kcal ?: return false
            val protein = protein ?: return false
            val carbs = carbs ?: return false
            val fat = fat ?: return false
            if (kcal !in SaveDailyGoalUseCase.KCAL_RANGE) return false
            if (protein !in macroRange || carbs !in macroRange || fat !in macroRange) return false
            return !isPercentSumInvalid && inputs != initialInputs
        }

    @get:StringRes
    val macroModeHint: Int?
        get() {
            val rejected = _rejectedMacroMode.value ?: return null
            return when (macroModeSwitchBlock(rejected) ?: return null) {
                MacroModeSwitchBlock.INCOMPLETE_MACROS -> R.string.goals_hint_macroModeIncomplete
                MacroModeSwitchBlock.MISSING_KCAL -> R.string.goals_hint_macroModeNeedsKcal
                MacroModeSwitchBlock.PERCENT_SUM_NOT_100 -> R.string.goals_hint_macroModeSumNot100
            }
        }

    // MARK: - Functions

    fun heightLabel(cm: Int): String = when (unit) {
        BodyWeightUnit.KG -> "$cm cm"
        BodyWeightUnit.LB -> {
            val totalInches = (cm / CENTIMETRES_PER_INCH).roundToInt()
            "${totalInches / INCHES_PER_FOOT}′ ${totalInches % INCHES_PER_FOOT}″"
        }
    }

    suspend fun onAppear() {
        if (!(_weightKilograms.value == null || isWeightDefaulted)) return
        try {
            val latest = fetchBodyWeights.upTo(now()).firstOrNull() ?: return
            if (_weightKilograms.value == null || isWeightDefaulted) {
                val wasUntouched = inputs == initialInputs
                setWeightKilograms(latest.kilograms)
                initialInputs = if (wasUntouched) inputs else initialInputs?.copy(weightKilograms = latest.kilograms)
                isWeightDefaulted = false
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.DAILY_GOALS)
        }
    }

    fun onKcalTextChanged(text: String) = setKcalText(text)

    fun onProteinTextChanged(text: String) = setProteinText(text)

    fun onCarbsTextChanged(text: String) = setCarbsText(text)

    fun onFatTextChanged(text: String) = setFatText(text)

    fun onSexChanged(newValue: GoalSex?) {
        val oldValue = _sex.value
        if (newValue != oldValue) matchingVariantIndex?.let { _selectedVariantIndex.value = it.toDouble() }
        _sex.value = newValue
        if (newValue == oldValue) return
        updateEstimate()
        applyEstimateOrReferenceKcal()
    }

    fun onBirthYearChanged(year: Int?) {
        _birthYear.value = year
        updateEstimate()
    }

    fun onHeightChanged(cm: Int?) {
        _heightCm.value = cm
        updateEstimate()
    }

    fun onActivityLevelChanged(newValue: GoalActivityLevel?) {
        val oldValue = _activityLevel.value
        if (newValue != oldValue) matchingVariantIndex?.let { _selectedVariantIndex.value = it.toDouble() }
        _activityLevel.value = newValue
        if (newValue == oldValue) return
        updateEstimate()
        onVariantSelected(_selectedVariantIndex.value.toInt())
    }

    fun onMacroModeChanged(mode: GoalMacroMode) {
        if (mode == _macroMode.value) return
        val block = macroModeSwitchBlock(mode)
        if (block != null) {
            _rejectedMacroMode.value = mode
            rejectedMacroModeBlock = block
            if (block == MacroModeSwitchBlock.MISSING_KCAL) _kcalFocusRequestCount.value += 1
            return
        }
        _rejectedMacroMode.value = null
        rejectedMacroModeBlock = null
        gramsSplitAnchor = null
        isConvertingMacros = true
        try {
            convertMacros(mode)
        } finally {
            _macroMode.value = mode
            isConvertingMacros = false
        }
    }

    fun onStep(macro: GoalMacro, direction: Int) {
        val isPercent = _macroMode.value == GoalMacroMode.PERCENT
        val step = if (isPercent) PERCENT_STEP else GRAMS_STEP
        val upper = if (isPercent) PERCENT_TOTAL else MAX_GRAMS
        fun stepped(text: String): String {
            val value = parse(text) ?: 0.0
            return format((value + step * direction).coerceIn(0.0, upper))
        }
        when (macro) {
            GoalMacro.PROTEIN -> setProteinText(stepped(_proteinText.value))
            GoalMacro.CARBS -> setCarbsText(stepped(_carbsText.value))
            GoalMacro.FAT -> setFatText(stepped(_fatText.value))
        }
    }

    fun onBirthYearTapped() {
        if (_birthYear.value == null) onBirthYearChanged(currentYear - DEFAULT_AGE)
        isBirthYearPickerVisible.value = true
    }

    fun onHeightTapped() {
        if (_heightCm.value == null) onHeightChanged(if (_sex.value == GoalSex.FEMALE) DEFAULT_HEIGHT_FEMALE_CM else DEFAULT_HEIGHT_MALE_CM)
        isHeightPickerVisible.value = true
    }

    fun onWeightTapped() {
        if (_weightKilograms.value == null) {
            setWeightKilograms(Constants.BodyWeight.DEFAULT_KILOGRAMS)
            isWeightDefaulted = true
        }
        isWeightPickerVisible.value = true
    }

    fun onWeightChanged(kilograms: Double) {
        setWeightKilograms(kilograms)
        isWeightDefaulted = false
    }

    fun onBodyDataToggled() {
        _isBodyDataCollapsed.value = !_isBodyDataCollapsed.value
    }

    fun onActivityToggled() {
        _isActivityCollapsed.value = !_isActivityCollapsed.value
    }

    fun onVariantSliderReleased() {
        if (matchingVariantIndex != null) return
        onVariantSelected(_selectedVariantIndex.value.toInt())
    }

    fun onVariantSelected(index: Int) {
        val variants = _estimate.value?.variants ?: return
        if (index !in variants.indices) return
        _selectedVariantIndex.value = index.toDouble()
        val variantKcal = variants[index].kcal.toDouble()
        if (_macroMode.value == GoalMacroMode.GRAMS) rescaleGrams(variantKcal)
        setKcalText(format(variantKcal))
    }

    suspend fun onSaveTapped() {
        if (!canSave) return
        val kcal = kcal ?: return
        val protein = protein ?: return
        val carbs = carbs ?: return
        val fat = fat ?: return
        val mode = _macroMode.value
        val goal = DailyGoalDomain(
            validFrom = "",
            kcalGoal = kcal,
            macroMode = mode,
            proteinPercent = protein.takeIf { mode == GoalMacroMode.PERCENT },
            carbsPercent = carbs.takeIf { mode == GoalMacroMode.PERCENT },
            fatPercent = fat.takeIf { mode == GoalMacroMode.PERCENT },
            proteinGrams = protein.takeIf { mode == GoalMacroMode.GRAMS },
            carbsGrams = carbs.takeIf { mode == GoalMacroMode.GRAMS },
            fatGrams = fat.takeIf { mode == GoalMacroMode.GRAMS },
            sex = _sex.value,
            birthYear = _birthYear.value,
            heightCm = _heightCm.value?.toDouble(),
            weightKg = _weightKilograms.value,
            activityLevel = _activityLevel.value,
        )
        val isWeightEdited = !isWeightDefaulted &&
            goal.weightKg?.let(unit::tenths) != initialInputs?.weightKilograms?.let(unit::tenths)
        save(goal, loggingWeight = if (isWeightEdited) goal.weightKg else null)
    }

    suspend fun onEndConfirmed() {
        if (_isSaving.value) return
        save(DailyGoalDomain.ended(validFrom = ""), loggingWeight = null)
    }

    // MARK: - Private

    private fun setKcalText(text: String) {
        _kcalText.value = text
        matchingVariantIndex?.let { _selectedVariantIndex.value = it.toDouble() }
        clearResolvedMacroModeRejection()
    }

    private fun setProteinText(text: String) {
        _proteinText.value = text
        macroTextDidChange()
    }

    private fun setCarbsText(text: String) {
        _carbsText.value = text
        macroTextDidChange()
    }

    private fun setFatText(text: String) {
        _fatText.value = text
        macroTextDidChange()
    }

    private fun setWeightKilograms(kilograms: Double?) {
        _weightKilograms.value = kilograms
        updateEstimate()
    }

    private fun updateEstimate() {
        val hadEstimate = _estimate.value != null
        _estimate.value = GoalEstimate.make(
            sex = _sex.value,
            birthYear = _birthYear.value,
            heightCm = _heightCm.value?.toDouble(),
            weightKg = _weightKilograms.value,
            activityLevel = _activityLevel.value,
            currentYear = currentYear,
        )
        if (!hadEstimate && _estimate.value != null && !hasActiveGoal && isKcalUntouchedPrefill) {
            onVariantSelected(_selectedVariantIndex.value.toInt())
        }
    }

    private val isKcalUntouchedPrefill: Boolean
        get() {
            if (_kcalText.value.isEmpty()) return true
            val sex = _sex.value ?: return false
            return _kcalText.value == referenceKcalText(isMale = sex == GoalSex.MALE)
        }

    private fun macroTextDidChange() {
        if (!isConvertingMacros) gramsSplitAnchor = null
        syncKcalWithGrams()
        clearResolvedMacroModeRejection()
    }

    private fun clearResolvedMacroModeRejection() {
        val rejected = _rejectedMacroMode.value ?: return
        if (macroModeSwitchBlock(rejected) == rejectedMacroModeBlock) return
        _rejectedMacroMode.value = null
        rejectedMacroModeBlock = null
    }

    private fun syncKcalWithGrams() {
        if (_macroMode.value != GoalMacroMode.GRAMS || isConvertingMacros) return
        val grams = kcalOfEnteredGrams ?: return
        setKcalText(format(rounded(grams)))
    }

    private fun currentGramsShares(): List<Double>? {
        val shares = energyShares(protein ?: return null, carbs ?: return null, fat ?: return null).map { it * PERCENT_TOTAL }
        return shares.takeIf { it.sum() > 0 }
    }

    private fun rescaleGrams(kcal: Double) {
        val shares = gramsSplitAnchor ?: currentGramsShares() ?: return
        gramsSplitAnchor = shares
        isConvertingMacros = true
        try {
            setProteinText(format(rounded(gramsFromKcal(kcal, shares[0], isFat = false))))
            setCarbsText(format(rounded(gramsFromKcal(kcal, shares[1], isFat = false))))
            setFatText(format(rounded(gramsFromKcal(kcal, shares[2], isFat = true))))
        } finally {
            isConvertingMacros = false
        }
    }

    private fun applyEstimateOrReferenceKcal() {
        if (_estimate.value != null) onVariantSelected(_selectedVariantIndex.value.toInt()) else applyReferenceKcal()
    }

    private fun applyReferenceKcal() {
        val sex = _sex.value ?: return
        val reference = referenceKcalText(isMale = sex == GoalSex.MALE)
        val referenceValue = parse(reference)
        if (_macroMode.value == GoalMacroMode.GRAMS && referenceValue != null) rescaleGrams(referenceValue)
        setKcalText(reference)
    }

    private fun convertMacros(mode: GoalMacroMode) {
        val protein = protein ?: return
        val carbs = carbs ?: return
        val fat = fat ?: return
        when (mode) {
            GoalMacroMode.GRAMS -> {
                val kcal = kcal ?: return
                setProteinText(format(rounded(gramsFromKcal(kcal, protein, isFat = false))))
                setCarbsText(format(rounded(gramsFromKcal(kcal, carbs, isFat = false))))
                setFatText(format(rounded(gramsFromKcal(kcal, fat, isFat = true))))
            }
            GoalMacroMode.PERCENT -> {
                val shares = energyShares(protein, carbs, fat).map { it * PERCENT_TOTAL }
                if (shares.sum() <= 0) return
                val roundedShares = roundedToHundred(shares)
                setProteinText(format(roundedShares[0]))
                setCarbsText(format(roundedShares[1]))
                setFatText(format(roundedShares[2]))
            }
        }
    }

    private suspend fun save(goal: DailyGoalDomain, loggingWeight: Double?) {
        _isSaving.value = true
        try {
            withContext(NonCancellable) {
                val history = saveDailyGoal(goal)
                if (loggingWeight != null) logWeightIfMissing(loggingWeight)
                onChanged(history)
            }
            _didFinish.value = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.DAILY_GOALS)
            alertItem.value = AlertItem(titleRes = R.string.goals_error_saveFailed)
        } finally {
            _isSaving.value = false
        }
    }

    private suspend fun logWeightIfMissing(kilograms: Double) {
        try {
            val today = now()
            val latest = fetchBodyWeights.upTo(today).firstOrNull()
            if (latest?.dayKey == BodyWeightDayKey.make(today)) return
            saveBodyWeight(kilograms, today)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.DAILY_GOALS)
        }
    }

    private fun macroModeSwitchBlock(mode: GoalMacroMode): MacroModeSwitchBlock? {
        val filledMacros = listOfNotNull(protein, carbs, fat).count { it.isFinite() && it >= 0 }
        if (filledMacros != 0 && filledMacros != MACRO_COUNT) return MacroModeSwitchBlock.INCOMPLETE_MACROS
        if (mode == GoalMacroMode.GRAMS && kcal == null && filledMacros == MACRO_COUNT) return MacroModeSwitchBlock.MISSING_KCAL
        if (mode == GoalMacroMode.GRAMS && _macroMode.value == GoalMacroMode.PERCENT && filledMacros == MACRO_COUNT && isPercentSumInvalid) {
            return MacroModeSwitchBlock.PERCENT_SUM_NOT_100
        }
        return null
    }

    companion object {
        private const val DEFAULT_PROTEIN_PERCENT = "20"
        private const val DEFAULT_CARBS_PERCENT = "50"
        private const val DEFAULT_FAT_PERCENT = "30"
        private const val PERCENT_TOTAL = 100.0
        private const val PERCENT_TOLERANCE = 0.001
        private const val PERCENT_STEP = 1.0
        private const val GRAMS_STEP = 5.0
        private const val MAX_GRAMS = 1000.0
        private const val MACRO_COUNT = 3
        private const val DEFAULT_AGE = 30
        private const val DEFAULT_HEIGHT_FEMALE_CM = 165
        private const val DEFAULT_HEIGHT_MALE_CM = 178
        private const val CENTIMETRES_PER_INCH = 2.54
        private const val INCHES_PER_FOOT = 12

        private fun referenceKcalText(isMale: Boolean): String = referenceKcal(isMale).toString()

        private fun rounded(value: Double): Double = Math.round(value).toDouble()

        private fun parse(text: String): Double? {
            val normalised = text.trim().replace(',', '.')
            if (normalised.lastOrNull()?.lowercaseChar() in setOf('d', 'f')) return null
            return normalised.toDoubleOrNull()
        }

        private fun format(value: Double): String = NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 6
            isGroupingUsed = false
        }.format(value)
    }
}
