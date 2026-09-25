package antoni.kalorie.core.utils

import android.content.Context
import androidx.annotation.StringRes

interface StringProvider {
    fun getString(@StringRes id: Int, vararg formatArgs: Any): String
}

class ContextStringProvider(private val context: Context) : StringProvider {

    // MARK: - Functions

    override fun getString(id: Int, vararg formatArgs: Any): String = context.getString(id, *formatArgs)
}
