package kio.chat

import io.ktor.http.*
import kio.http.*
import kio.chat.domain.Repository
import kio.chat.page.chatLoginPage
import kio.chat.page.chatRegisterPage
import kotlinx.html.div

context(_: Repository)
fun Route.chatLoginRoutes() {
    route("register") {
        get { call -> call.chatRegisterPage() }
        post { call -> call.handleRegister() }
    }

    route("login") {
        get { call -> call.chatLoginPage() }
        post { call -> call.handleLogin() }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleRegister() {
    val params = receiveFormParameters()
    val username = params["username"]
    val password = params["password"]
    val confirmPw = params["confirmPassword"]

    if (username.isNullOrEmpty() || password.isNullOrEmpty() || confirmPw.isNullOrEmpty()) {
        respondError("Please fill in all fields.")
        return
    }

    if (password != confirmPw) {
        respondError("Passwords do not match.")
        return
    }

    val user = repo.createUser(username, password)
    if (user == null) {
        respondError("This username is already taken.")
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                Cookie(
                    "session",
                    sessionId,
                    httpOnly = true,
                    path = "/",
                    secure = kio.chat.util.Config.SECURE_SESSION_COOKIE,
                    extensions = mapOf("SameSite" to "Lax")
                )
            )
        }
    )
}

context(repo: Repository)
private suspend fun CallContext.handleLogin() {
    val params = receiveFormParameters()

    val username = params["username"]
    val password = params["password"]

    if (username.isNullOrBlank() || password.isNullOrBlank()) {
        respondError("Username and password are required.")
        return
    }

    val user = repo.findUserByUsername(username)

    if (user == null || !repo.verifyPassword(user, password)) {
        respondError("Invalid username or password.")
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                Cookie(
                    "session",
                    sessionId,
                    httpOnly = true,
                    path = "/",
                    secure = kio.chat.util.Config.SECURE_SESSION_COOKIE,
                    extensions = mapOf("SameSite" to "Lax")
                )
            )
        }
    )
}

private suspend fun CallContext.respondError(message: String) {
    respondHtml {
        div {
            +message
        }
    }
}


