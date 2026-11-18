package me.andannn.kio.chat

import me.andannn.kio.chat.util.SnackBarController


context(navigator: RootNavigator, snackBar: SnackBarController)
suspend fun handleError(error: ApiError) {
    when (error) {
        ApiError.AuthenticationError -> navigator.clearTopAndGoToLogin()
        is ApiError.ServerError -> snackBar.showSnackBar(error.message)
    }
}