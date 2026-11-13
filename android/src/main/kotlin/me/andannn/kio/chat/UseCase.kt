package me.andannn.kio.chat

import me.andannn.kio.chat.util.SnackBarController


context(navigator: Navigator, snackBar: SnackBarController)
suspend fun handleError(error: ApiError) {
    when (error) {
        ApiError.AuthenticationError -> navigator.popToLogin()
        is ApiError.ServerError -> snackBar.showSnackBar(error.message)
    }
}