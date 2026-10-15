package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderFake
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.UserProfileDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FillEmptyProfileUseCaseTest {

    // MARK: - Tests

    @Test
    fun invoke_whenProfileIsEmpty_fillsNameAndEmail() = runTest {
        val fixture = makeSUT()

        fixture.sut(displayName = "Josef Antoni", email = "josef@example.com")

        val profile = fixture.dataProvider.setSavedItem as UserProfileDTO
        assertEquals(1, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertEquals("Josef Antoni", profile.displayName)
        assertEquals("josef@example.com", profile.email)
    }

    @Test
    fun invoke_whenAccountAlreadyHasADisplayName_doesNotOverwriteIt() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, displayName = "Josef"))
        fixture.dataProvider.stubbedDocument = UserProfileDTO(displayName = "Josef", email = null)

        fixture.sut(displayName = "Apple Name", email = "josef@example.com")

        val profile = fixture.dataProvider.setSavedItem as UserProfileDTO
        assertEquals("linking a second provider must not replace the name the user already has", 0, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertEquals("Josef", profile.displayName)
        assertEquals("an empty field is still filled", "josef@example.com", profile.email)
    }

    @Test
    fun invoke_whenProfileAlreadyHasNameAndEmail_writesNothing() = runTest {
        val fixture = makeSUT(authProvider = AuthProviderFake(isAnonymous = false, displayName = "Josef"))
        fixture.dataProvider.stubbedDocument = UserProfileDTO(displayName = "Josef", email = "josef@example.com")

        fixture.sut(displayName = "Other", email = "other@example.com")

        assertEquals(0, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertNull(fixture.dataProvider.setSavedItem)
    }

    @Test
    fun invoke_withNothingToFill_writesNothing() = runTest {
        val fixture = makeSUT()

        fixture.sut(displayName = null, email = "")

        assertEquals(0, fixture.authCommandProvider.updateDisplayNameCallCount)
        assertNull(fixture.dataProvider.setSavedItem)
    }

    @Test
    fun invoke_whenProfileCannotBeRead_writesNothing() = runTest {
        val fixture = makeSUT()
        fixture.dataProvider.stubbedError = RuntimeException("offline")

        fixture.sut(displayName = "Josef", email = "josef@example.com")

        assertNull("without knowing what is stored, a write could overwrite it", fixture.dataProvider.setSavedItem)
    }

    // MARK: - Helpers

    private class Fixture(
        val sut: FillEmptyProfileUseCase,
        val authCommandProvider: AuthCommandProviderFake,
        val dataProvider: FirestoreDataProviderFake,
    )

    private fun makeSUT(authProvider: AuthProviderFake = AuthProviderFake(userId = "user-1", isAnonymous = false)): Fixture {
        val authCommandProvider = AuthCommandProviderFake()
        val dataProvider = FirestoreDataProviderFake()
        val sut = FillEmptyProfileUseCase(
            authProvider = authProvider,
            authCommandProvider = authCommandProvider,
            dataProvider = dataProvider,
        )
        return Fixture(sut, authCommandProvider, dataProvider)
    }
}
