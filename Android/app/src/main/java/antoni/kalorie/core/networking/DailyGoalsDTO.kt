package antoni.kalorie.core.networking

import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.models.GoalActivityLevel
import antoni.kalorie.core.models.GoalMacroMode
import antoni.kalorie.core.models.GoalSex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

@Serializable
data class DailyGoalsDTO(
    @Serializable(with = LossyGoalEntriesSerializer::class) val goals: List<DailyGoalEntryDTO> = emptyList(),
) {

    // MARK: - Init

    constructor(history: DailyGoalHistory) : this(goals = history.entries.map(::DailyGoalEntryDTO))

    // MARK: - Functions

    fun asDomain(): DailyGoalHistory = DailyGoalHistory(entries = goals.mapNotNull { it.asDomain() }.sortedBy { it.validFrom })
}

object LossyGoalEntriesSerializer : KSerializer<List<DailyGoalEntryDTO>> {

    // MARK: - Properties

    private val listSerializer = ListSerializer(DailyGoalEntryDTOSerializer)

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    // MARK: - Functions

    override fun deserialize(decoder: Decoder): List<DailyGoalEntryDTO> {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DailyGoalsDTO decodes from JSON only")
        val array = jsonDecoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.map { DailyGoalEntryDTO.decode(it) }
    }

    override fun serialize(encoder: Encoder, value: List<DailyGoalEntryDTO>) = listSerializer.serialize(encoder, value)
}

@Serializable(with = DailyGoalEntryDTOSerializer::class)
data class DailyGoalEntryDTO(
    val validFrom: String?,
    val kcalGoal: Double?,
    val macroMode: String?,
    val carbsPercent: Double?,
    val proteinPercent: Double?,
    val fatPercent: Double?,
    val carbsGrams: Double?,
    val proteinGrams: Double?,
    val fatGrams: Double?,
    val sex: String?,
    val birthYear: Int?,
    val heightCm: Double?,
    val weightKg: Double?,
    val activityLevel: String?,
    val hasUnreadableDecisiveField: Boolean = false,
) {

    // MARK: - Init

    constructor(goal: DailyGoalDomain) : this(
        validFrom = goal.validFrom,
        kcalGoal = goal.kcalGoal,
        macroMode = if (goal.kcalGoal == null) null else goal.macroMode.rawValue,
        carbsPercent = goal.carbsPercent.takeIf { goal.macroMode == GoalMacroMode.PERCENT },
        proteinPercent = goal.proteinPercent.takeIf { goal.macroMode == GoalMacroMode.PERCENT },
        fatPercent = goal.fatPercent.takeIf { goal.macroMode == GoalMacroMode.PERCENT },
        carbsGrams = goal.carbsGrams.takeIf { goal.macroMode == GoalMacroMode.GRAMS },
        proteinGrams = goal.proteinGrams.takeIf { goal.macroMode == GoalMacroMode.GRAMS },
        fatGrams = goal.fatGrams.takeIf { goal.macroMode == GoalMacroMode.GRAMS },
        sex = goal.sex?.rawValue,
        birthYear = goal.birthYear,
        heightCm = goal.heightCm,
        weightKg = goal.weightKg,
        activityLevel = goal.activityLevel?.rawValue,
    )

    // MARK: - Functions

    fun asDomain(): DailyGoalDomain? {
        val validFrom = validFrom ?: return null
        if (hasUnreadableDecisiveField) return null
        val mode = if (macroMode == null) GoalMacroMode.PERCENT else GoalMacroMode.fromRawValue(macroMode) ?: return null
        return DailyGoalDomain(
            validFrom = validFrom,
            kcalGoal = kcalGoal,
            macroMode = mode,
            carbsPercent = carbsPercent,
            proteinPercent = proteinPercent,
            fatPercent = fatPercent,
            carbsGrams = carbsGrams,
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            sex = sex?.let(GoalSex::fromRawValue),
            birthYear = birthYear,
            heightCm = heightCm,
            weightKg = weightKg,
            activityLevel = activityLevel?.let(GoalActivityLevel::fromRawValue),
        )
    }

    companion object {

        // MARK: - Functions

        fun decode(element: JsonElement): DailyGoalEntryDTO {
            val obj = element as? JsonObject
                ?: return DailyGoalEntryDTO(null, null, null, null, null, null, null, null, null, null, null, null, null, null, true)
            val kcalGoal = obj.number("kcal_goal")
            val macroMode = obj.string("macro_mode")
            return DailyGoalEntryDTO(
                validFrom = obj.string("valid_from"),
                kcalGoal = kcalGoal,
                macroMode = macroMode,
                carbsPercent = obj.number("carbs_pct"),
                proteinPercent = obj.number("protein_pct"),
                fatPercent = obj.number("fat_pct"),
                carbsGrams = obj.number("carbs_g"),
                proteinGrams = obj.number("protein_g"),
                fatGrams = obj.number("fat_g"),
                sex = obj.string("sex"),
                birthYear = obj.number("birth_year")?.takeIf { it % 1.0 == 0.0 }?.toInt(),
                heightCm = obj.number("height_cm"),
                weightKg = obj.number("weight_kg"),
                activityLevel = obj.string("activity_level"),
                hasUnreadableDecisiveField = (kcalGoal == null && obj.isPresentAndNotNull("kcal_goal")) ||
                    (macroMode == null && obj.isPresentAndNotNull("macro_mode")),
            )
        }

        private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        private fun JsonObject.isPresentAndNotNull(key: String): Boolean = containsKey(key) && this[key] !is JsonNull
    }
}

object DailyGoalEntryDTOSerializer : KSerializer<DailyGoalEntryDTO> {

    // MARK: - Properties

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    // MARK: - Functions

    override fun deserialize(decoder: Decoder): DailyGoalEntryDTO {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DailyGoalEntryDTO decodes from JSON only")
        return DailyGoalEntryDTO.decode(jsonDecoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: DailyGoalEntryDTO) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("DailyGoalEntryDTO encodes to JSON only")
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                value.validFrom?.let { put("valid_from", it) }
                value.kcalGoal?.let { put("kcal_goal", it) }
                value.macroMode?.let { put("macro_mode", it) }
                value.carbsPercent?.let { put("carbs_pct", it) }
                value.proteinPercent?.let { put("protein_pct", it) }
                value.fatPercent?.let { put("fat_pct", it) }
                value.carbsGrams?.let { put("carbs_g", it) }
                value.proteinGrams?.let { put("protein_g", it) }
                value.fatGrams?.let { put("fat_g", it) }
                value.sex?.let { put("sex", it) }
                value.birthYear?.let { put("birth_year", it) }
                value.heightCm?.let { put("height_cm", it) }
                value.weightKg?.let { put("weight_kg", it) }
                value.activityLevel?.let { put("activity_level", it) }
            },
        )
    }
}
