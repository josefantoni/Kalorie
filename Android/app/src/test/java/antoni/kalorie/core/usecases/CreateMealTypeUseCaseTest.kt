package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.features.mealtypesheet.CreateMealTypeError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class CreateMealTypeUseCaseTest {

    // MARK: - Tests

    @Test
    fun createMealType_withEmptyName_throwsEmptyNameError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(name = "", startMinutes = 0, endMinutes = 0, existingMealTypes = emptyList())
            fail("Expected emptyName error")
        } catch (_: CreateMealTypeError.EmptyName) {
        }
    }

    @Test
    fun createMealType_withDuplicateName_throwsDuplicateNameError() = runTest {
        val (sut, _) = makeSUT()
        val existing = MealTypeDomain(id = "1", name = "Snídaně", startMinutes = 360, endMinutes = 540)

        try {
            sut(name = "Snídaně", startMinutes = 600, endMinutes = 660, existingMealTypes = listOf(existing))
            fail("Expected duplicateName error")
        } catch (_: CreateMealTypeError.DuplicateName) {
        }
    }

    @Test
    fun createMealType_withTimeConflict_throwsTimeConflictError() = runTest {
        val (sut, _) = makeSUT()
        val existing = MealTypeDomain(id = "1", name = "Snídaně", startMinutes = 360, endMinutes = 540)

        try {
            sut(name = "Druhá snídaně", startMinutes = 420, endMinutes = 480, existingMealTypes = listOf(existing))
            fail("Expected timeConflict error")
        } catch (_: CreateMealTypeError.TimeConflict) {
        }
    }

    @Test
    fun createMealType_wrappingExistingSlot_throwsTimeConflictError() = runTest {
        val (sut, _) = makeSUT()
        val existing = MealTypeDomain(id = "1", name = "Snídaně", startMinutes = 540, endMinutes = 720)

        try {
            sut(name = "Mega snídaně", startMinutes = 420, endMinutes = 840, existingMealTypes = listOf(existing))
            fail("Expected timeConflict error")
        } catch (_: CreateMealTypeError.TimeConflict) {
        }
    }

    @Test
    fun createMealType_wrappingMidnight_isNotRejectedAsTooShort() = runTest {
        val (sut, _) = makeSUT()

        val result = sut(name = "Půlnoční svačina", startMinutes = 1430, endMinutes = 20, existingMealTypes = emptyList())

        assertEquals("Půlnoční svačina", result.name)
    }

    @Test
    fun createMealType_withTooShortWindow_throwsDurationTooShortError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(name = "Svačina", startMinutes = 600, endMinutes = 620, existingMealTypes = emptyList())
            fail("Expected durationTooShort error")
        } catch (_: CreateMealTypeError.DurationTooShort) {
        }
    }

    @Test
    fun createMealType_withValidInput_persistsAndReturnsMealType() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")
        val existing = MealTypeDomain(id = "1", name = "Snídaně", startMinutes = 360, endMinutes = 540)

        val result = sut(name = "Oběd", startMinutes = 660, endMinutes = 780, existingMealTypes = listOf(existing))

        assertEquals("Oběd", result.name)
        assertFalse(result.id.isEmpty())
        assertNotEquals(
            "a new meal type must never reuse an id already in use, since Firestore's setAsync would silently overwrite that document",
            existing.id,
            result.id,
        )
        assertEquals("users/user-123/mealTypes", dataProvider.setSavedCollection)
        assertEquals(result.id, dataProvider.setSavedId)
    }

    @Test
    fun createMealType_assignsUppercaseId() = runTest {
        val (sut, _) = makeSUT()

        val result = sut(name = "Oběd", startMinutes = 660, endMinutes = 780, existingMealTypes = emptyList())

        assertEquals(result.id.uppercase(), result.id)
    }

    @Test
    fun createMealType_calledTwiceFromSameExistingSnapshot_assignsDistinctIds() = runTest {
        val (sut, _) = makeSUT()

        val first = sut(name = "Snídaně", startMinutes = 360, endMinutes = 540, existingMealTypes = emptyList())
        val second = sut(name = "Oběd", startMinutes = 660, endMinutes = 780, existingMealTypes = emptyList())

        assertNotEquals(
            "two devices creating a meal type from the same stale snapshot must not collide on id and silently overwrite each other",
            first.id,
            second.id,
        )
    }

    @Test
    fun createMealType_withWhitespaceOnlyName_throwsEmptyNameError() = runTest {
        val (sut, _) = makeSUT()

        try {
            sut(name = " \n ", startMinutes = 600, endMinutes = 660, existingMealTypes = emptyList())
            fail("Expected emptyName error")
        } catch (_: CreateMealTypeError.EmptyName) {
        }
    }

    @Test
    fun createMealType_withNameDifferingOnlyInCaseAndPadding_throwsDuplicateNameError() = runTest {
        val (sut, _) = makeSUT()
        val existing = MealTypeDomain(id = "1", name = "Snídaně", startMinutes = 360, endMinutes = 540)

        try {
            sut(name = "  SNÍDANĚ ", startMinutes = 600, endMinutes = 660, existingMealTypes = listOf(existing))
            fail("Expected duplicateName error")
        } catch (_: CreateMealTypeError.DuplicateName) {
        }
    }

    @Test
    fun createMealType_withPaddedName_returnsTrimmedName() = runTest {
        val (sut, _) = makeSUT()

        val result = sut(name = "  Oběd ", startMinutes = 660, endMinutes = 780, existingMealTypes = emptyList())

        assertEquals("Oběd", result.name)
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<CreateMealTypeUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = CreateMealTypeUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(userId = userId))
        return sut to dataProvider
    }
}
