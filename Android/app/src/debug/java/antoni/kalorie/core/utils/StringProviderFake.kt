package antoni.kalorie.core.utils

class StringProviderFake : StringProvider {

    // MARK: - Functions

    override fun getString(id: Int, vararg formatArgs: Any): String =
        if (formatArgs.isEmpty()) "string-$id" else "string-$id(${formatArgs.joinToString()})"
}
