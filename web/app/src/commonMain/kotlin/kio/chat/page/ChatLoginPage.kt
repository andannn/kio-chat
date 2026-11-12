package kio.chat.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.util.hxPost
import kio.chat.util.hxSwap
import kio.chat.util.hxTarget
import kotlinx.html.*

suspend fun CallContext.chatLoginPage() {
    respondHtml {
        chatHead("Login - Kio Chat")

        body(classes = "app login-body") {
            chatLogin()
        }
    }
}

fun TagConsumer<*>.chatLogin() {
    main(classes = "login-page") {
        section(classes = "login-panel") {
            div(classes = "login-header") {
                h1(classes = "login-title") {
                    +"Kio Chat"
                }

                p(classes = "login-subtitle") {
                    +"Sign in to continue"
                }
            }

            form(classes = "login-form") {
                hxPost = "/login"
                hxTarget = "#login-result"
                hxSwap = "innerHTML"

                div(classes = "login-field") {
                    label {
                        htmlFor = "username"
                        +"Username"
                    }

                    input(
                        type = InputType.text,
                        name = "username",
                        classes = "login-input",
                    ) {
                        id = "username"
                        autoComplete = "username"
                        autoFocus = true
                        required = true
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "password"
                        +"Password"
                    }

                    input(
                        type = InputType.password,
                        name = "password",
                        classes = "login-input",
                    ) {
                        id = "password"
                        autoComplete = "current-password"
                        required = true
                    }
                }

                div {
                    id = "login-result"
                }

                button(
                    type = ButtonType.submit,
                    classes = "login-submit",
                ) {
                    +"Sign in"
                }
            }
        }

        p(classes = "auth-footer") {
            +"Don't have an account? "
            a(href = "/register") {
                +"Create account"
            }
        }
    }
}
