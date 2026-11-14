package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemReportError
import antoni.kalorie.core.networking.FirestoreDataMapper
import antoni.kalorie.core.networking.FoodItemReportCreateDTO
import antoni.kalorie.core.networking.TicketDataProviderFake
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.features.notifications.UserNotificationsStoreFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubmitFoodItemReportUseCaseTest {

    // MARK: - Tests

    @Test
    fun submitFoodItemReport_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut("12345678", "wrong calories")
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }

        assertTrue(dataProvider.sets.isEmpty())
    }

    @Test
    fun submitFoodItemReport_withEmptyReason_throwsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", "   ")
            fail("Expected reasonRequired error")
        } catch (_: FoodItemReportError.ReasonRequired) {
        }

        assertTrue("a report with no text is a downvote the maintainer cannot act on", dataProvider.sets.isEmpty())
    }

    @Test
    fun submitFoodItemReport_withReasonOverLimit_throwsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", "a".repeat(Constants.Firestore.REPORT_REASON_MAX_LENGTH + 1))
            fail("Expected reasonTooLong error")
        } catch (_: FoodItemReportError.ReasonTooLong) {
        }

        assertTrue(dataProvider.sets.isEmpty())
    }

    @Test
    fun submitFoodItemReport_withReasonOverLimitInUTF16Units_throwsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", "\uD83D\uDE00".repeat(Constants.Firestore.REPORT_REASON_MAX_LENGTH))
            fail("Expected reasonTooLong error")
        } catch (_: FoodItemReportError.ReasonTooLong) {
        }

        assertTrue("the rules count UTF-16 units, so a reason within the limit in code points is still refused server-side", dataProvider.sets.isEmpty())
    }

    @Test
    fun submitFoodItemReport_withDiacriticsAtLimit_writes() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "ě".repeat(Constants.Firestore.REPORT_REASON_MAX_LENGTH))

        assertFalse("the rules accept this, so a Czech reason must not be cut short", dataProvider.sets.isEmpty())
    }

    @Test
    fun submitFoodItemReport_writesUnderAGeneratedIdAsAPendingVisibleTicketWithTheAppLocale() = runTest {
        val (sut, dataProvider) = makeSUT(locale = "sk")
        dataProvider.newIds = mutableListOf("generated-id")

        sut("12345678", "the fat is wrong")

        assertEquals(
            "neither the item nor the user may be in the id, or a kept ticket would block every later report",
            listOf("generated-id"),
            dataProvider.sets.map { it.id },
        )
        assertEquals(Constants.Firestore.FOOD_ITEM_REPORTS, dataProvider.sets.first().collection)
        val encoded = encoded(dataProvider)
        assertEquals("pending", encoded["status"])
        assertEquals(false, encoded["hidden"])
        assertEquals("sk", encoded["locale"])
    }

    @Test
    fun submitFoodItemReport_sendsOnlyTheKeysTheCreateRuleAllows() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "the fat is wrong")

        assertEquals(
            "the rules use keys().hasOnly, so a response or seen_at key on create is denied",
            setOf("barcode", "reported_by", "reported_at", "reason", "status", "locale", "hidden"),
            encoded(dataProvider).keys,
        )
    }

    @Test
    fun submitFoodItemReport_twiceForTheSameItem_createsTwoTickets() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "first")
        sut("12345678", "second")

        assertEquals(2, dataProvider.sets.size)
        assertNotEquals(dataProvider.sets[0].id, dataProvider.sets[1].id)
    }

    @Test
    fun submitFoodItemReport_whenTheWriteFails_doesNotMarkMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val (sut, dataProvider) = makeSUT(store = store)
        dataProvider.failingWriteCollections = setOf(Constants.Firestore.FOOD_ITEM_REPORTS)

        try {
            sut("12345678", "the fat is wrong")
        } catch (_: java.io.IOException) {
        }

        assertFalse("no ticket exists, so the bell has nothing to look for", store.mayHaveUnread)
    }

    @Test
    fun submitFoodItemReport_afterASuccessfulWrite_marksThatTheUserMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val (sut, _) = makeSUT(store = store)

        sut("12345678", "the fat is wrong")

        assertTrue("without it the gated bell would not query and the new notification would wait up to a day", store.mayHaveUnread)
        assertNotNull("a fetch already in flight must not overwrite the flag with a result that predates the submit", store.lastSubmittedAt)
    }

    @Test
    fun submitFoodItemReport_whenValidationFails_doesNotMarkMayHaveUnread() = runTest {
        val store = UserNotificationsStoreFake()
        val (sut, _) = makeSUT(store = store)

        try {
            sut("12345678", "  ")
        } catch (_: FoodItemReportError) {
        }

        assertFalse(store.mayHaveUnread)
    }

    @Test
    fun submitFoodItemReport_trimsWhitespaceFromReason() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut("12345678", "  the fat is wrong  ")

        assertEquals("the fat is wrong", encoded(dataProvider)["reason"])
    }

    // MARK: - Helpers

    private fun encoded(dataProvider: TicketDataProviderFake): Map<String, Any?> {
        val dto = dataProvider.sets.first().item as FoodItemReportCreateDTO
        return FirestoreDataMapper.encode(dto, FoodItemReportCreateDTO.serializer())
    }

    private fun makeSUT(
        userId: String? = "test-user",
        store: UserNotificationsStoreFake = UserNotificationsStoreFake(),
        locale: String = "en",
    ): Pair<SubmitFoodItemReportUseCase, TicketDataProviderFake> {
        val dataProvider = TicketDataProviderFake()
        return SubmitFoodItemReportUseCase(dataProvider, AuthProviderFake(userId = userId), store, locale) to dataProvider
    }
}
