package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import antoni.kalorie.core.extensions.LocalKeyboardDoneClearance

val SectionStackSpacing = 8.dp

// MARK: - Body

@Composable
fun SectionStack(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(bottom = SectionStackSpacing + LocalKeyboardDoneClearance.current),
        verticalArrangement = Arrangement.spacedBy(SectionStackSpacing),
        content = content,
    )
}

@Composable
fun LazySectionStack(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + SectionStackSpacing + LocalKeyboardDoneClearance.current,
        ),
        verticalArrangement = Arrangement.spacedBy(SectionStackSpacing),
        content = content,
    )
}

@Composable
fun SectionGroup(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp, end = 32.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (trailing != null) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Box(modifier = Modifier.align(Alignment.CenterVertically)) { trailing() }
                }
            }
        }
        content()
    }
}
