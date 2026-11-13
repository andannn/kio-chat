
package me.andannn.kio.chat.util

import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

val LocalSnackBarController: ProvidableCompositionLocal<SnackBarController> =
    compositionLocalOf { error("no SnackBarController") }

fun SnackBarController(): SnackBarController = SnackBarControllerImpl()

interface SnackBarController {
    var snackBarController: SnackbarHostState?

    suspend fun showSnackBar(message: String): SnackbarResult
}

@Composable
fun rememberAndSetupSnackBarHostState(holder: SnackBarController = LocalSnackBarController.current): SnackbarHostState {
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(snackbarHostState) {
        holder.snackBarController = snackbarHostState

        onDispose {
            holder.snackBarController = null
        }
    }

    return snackbarHostState
}

private const val TAG = "SnackBarController"

internal class SnackBarControllerImpl : SnackBarController {
    override var snackBarController: SnackbarHostState? = null

    /**
     * Show snackbar and wait for user interaction.
     */
    override suspend fun showSnackBar(message: String): SnackbarResult {
        Log.d(TAG, "show snackbar. message = $message")
        return snackBarController
            ?.showSnackbar(message, null)
            ?: error("Snackbar HostState is not setup. ")
    }
}
