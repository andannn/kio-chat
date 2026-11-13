package kio.chat

import io.ktor.http.*
import kio.http.*
import kio.chat.domain.Repository
import kio.chat.page.chatLoginPage
import kio.chat.page.chatRegisterPage
import kio.chat.util.respondByContentType
import kotlinx.html.div

context(_: Repository)
fun Route.chatLoginRoutes() {
    route("register") {
        get { call -> call.chatRegisterPage() }
        post { call -> call.handleRegister() }
    }

    post("logout") { call -> call.handleLogout() }

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
        respondError("请填写所有信息。")
        return
    }

    if (password != confirmPw) {
        respondError("两次输入的密码不一致。")
        return
    }

    val user = repo.createUser(username, password)
    if (user == null) {
        respondError("该用户名已被使用。")
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                sessionCookie(sessionId)
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
        val error = "请输入用户名和密码。"
        respondByContentType(
            HttpStatusCode.BadRequest,
            buildJson = { error },
            buildHtml = { div { +error } }
        )
        return
    }

    val user = repo.findUserByUsername(username)

    if (user == null || !repo.verifyPassword(user, password)) {
        val error = "用户名或密码错误。"
        respondByContentType(
            HttpStatusCode.BadRequest,
            buildJson = { error },
            buildHtml = { div { +error } }
        )
        return
    }

    val sessionId = repo.createSession(user.id)

    respond(
        HttpStatusCode.OK,
        configHeaders = {
            append("HX-Redirect", "/")
            appendCookie(
                sessionCookie(sessionId)
            )
        }
    )
}

context(repo: Repository)
private suspend fun CallContext.handleLogout() {
    requestCookies()["session"]?.let { repo.deleteSession(it) }
    respond(HttpStatusCode.OK, configHeaders = {
        appendCookie(sessionCookie("").copy(maxAge = 0))
        append("HX-Redirect", "/login")
    })
}

private suspend fun CallContext.respondError(message: String) {
    respondHtml {
        div { +message }
    }
}


