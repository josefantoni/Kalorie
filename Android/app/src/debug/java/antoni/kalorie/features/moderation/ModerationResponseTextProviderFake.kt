package antoni.kalorie.features.moderation

class ModerationResponseTextProviderFake : ModerationResponseTextProviderProtocol {

    // MARK: - Functions

    override fun reportAccepted(locale: String): String = "report-accepted-$locale"

    override fun photoAccepted(locale: String): String = "photo-accepted-$locale"
}
