package kio.chat.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.util.hxPost
import kio.chat.util.hxSwap
import kio.chat.util.hxTarget
import kotlinx.html.*

suspend fun CallContext.chatRegisterPage() {
    respondHtml {
        chatHead("注册 - Kio Chat")

        body(classes = "app login-body register-body") {
            chatRegister()
        }
    }
}

fun TagConsumer<*>.chatRegister() {
    main(classes = "login-page") {
        section(classes = "login-panel") {
            div(classes = "login-header") {
                h1(classes = "login-title") {
                    +"注册账号"
                }
                p(classes = "login-subtitle") {
                    +"开始使用 Kio Chat 聊天"
                }
            }

            form(classes = "login-form") {
                hxPost = "/register"
                hxTarget = "#register-result"
                hxSwap = "innerHTML"

                div(classes = "login-field") {
                    label {
                        htmlFor = "register-username"
                        +"用户名"
                    }
                    input(
                        type = InputType.text,
                        name = "username",
                        classes = "login-input",
                    ) {
                        id = "register-username"
                        autoComplete = "username"
                        autoFocus = true
                        required = true
                        attributes["autocapitalize"] = "none"
                        attributes["spellcheck"] = "false"
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "register-password"
                        +"密码"
                    }
                    input(
                        type = InputType.password,
                        name = "password",
                        classes = "login-input",
                    ) {
                        id = "register-password"
                        autoComplete = "new-password"
                        required = true
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "register-confirm-password"
                        +"确认密码"
                    }
                    input(
                        type = InputType.password,
                        name = "confirmPassword",
                        classes = "login-input",
                    ) {
                        id = "register-confirm-password"
                        autoComplete = "new-password"
                        required = true
                    }
                }

                div(classes = "register-result") {
                    id = "register-result"
                    attributes["role"] = "status"
                    attributes["aria-live"] = "polite"
                }

                button(
                    type = ButtonType.submit,
                    classes = "login-submit",
                ) {
                    +"注册账号"
                }
            }

            p(classes = "auth-footer") {
                +"已有账号？ "
                a(href = "/login") {
                    +"登录"
                }
            }
        }
    }
}