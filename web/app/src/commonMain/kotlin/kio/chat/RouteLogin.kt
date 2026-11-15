package kio.chat

import io.ktor.http.*
import kio.http.*
import kio.chat.domain.Repository
import kio.chat.page.chatLoginPage
import kio.chat.page.chatRegisterPage
import kio.chat.util.respondByContentType
import kio.chat.util.respondError
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
    val nickname = params["nickname"]?.trim()

    if (username.isNullOrEmpty() || password.isNullOrEmpty() || confirmPw.isNullOrEmpty() || nickname.isNullOrEmpty()) {
        respondError("请填写所有信息。")
        return
    }

    if (!username.all { it in '!'..'~' }) {
        respondError("登录账号只能使用 ASCII 字符，不能包含空格。")
        return
    }

    if (password != confirmPw) {
        respondError("两次输入的密码不一致。")
        return
    }

    val user = repo.createUser(username, password, nickname)
    if (user == null) {
        respondError("该登录账号已被使用。")
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
        respondError("请输入登录账号和密码。")
        return
    }

    if (!username.all { it in '!'..'~' }) {
        respondError("登录账号只能使用 ASCII 字符，不能包含空格。")
        return
    }

    val user = repo.findUserByUsername(username)

    if (user == null || !repo.verifyPassword(user, password)) {
        respondError("登录账号或密码错误。")
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
