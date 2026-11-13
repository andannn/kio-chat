package me.andannn.kio.chat.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedValuesStoreRegistry
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.navigation3.runtime.NavEntryDecorator

@Composable
internal fun <T : Any> rememberRetainedValueStoreNavEntryDecorator(
    retainedValuesStoreRegistry: RetainedValuesStoreRegistry = retainRetainedValuesStoreRegistry(),
): NavEntryDecorator<T> =
    remember(
        retainedValuesStoreRegistry,
    ) { RetainedValueStoreNavEntryDecorator(retainedValuesStoreRegistry) }

private class RetainedValueStoreNavEntryDecorator<T : Any>(
    private val retainedValuesStoreRegistry: RetainedValuesStoreRegistry,
) : NavEntryDecorator<T>(
        onPop = {
            retainedValuesStoreRegistry.clearChild(it)
        },
        decorate = { entry ->
            retainedValuesStoreRegistry.LocalRetainedValuesStoreProvider(
                entry.contentKey,
            ) {
                entry.Content()
            }
        },
    )
