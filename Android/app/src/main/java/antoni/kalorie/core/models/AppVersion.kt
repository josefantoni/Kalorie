package antoni.kalorie.core.models

class AppVersion private constructor(private val components: List<Long>) : Comparable<AppVersion> {

    // MARK: - Functions

    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(components.size, other.components.size)) {
            val left = components.getOrElse(index) { 0L }
            val right = other.components.getOrElse(index) { 0L }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is AppVersion && compareTo(other) == 0

    override fun hashCode(): Int = components.dropLastWhile { it == 0L }.hashCode()

    override fun toString(): String = components.joinToString(".")

    companion object {
        fun parse(string: String): AppVersion? {
            val parsed = string.split(".").map { part ->
                if (part.isNotEmpty() && part.all { it in '0'..'9' }) part.toLongOrNull() else null
            }
            if (parsed.any { it == null }) return null
            return AppVersion(parsed.filterNotNull())
        }
    }
}
