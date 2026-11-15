package kio.chat.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.util.Config
import kio.chat.util.hxPost
import kio.chat.util.hxSwap
import kio.chat.util.hxTarget
import kotlinx.html.*

suspend fun CallContext.chatLoginPage() {
    respondHtml {
        chatHead("登录 - Kio Chat")

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
                    +"登录后继续"
                }
            }

            form(classes = "login-form") {
                hxPost = "/login"
                hxTarget = "#login-result"
                hxSwap = "innerHTML"

                div(classes = "login-field") {
                    label {
                        htmlFor = "username"
                        +"登录账号"
                    }

                    input(
                        type = InputType.text,
                        name = "username",
                        classes = "login-input",
                    ) {
                        id = "username"
                        autoComplete = "username"
                        attributes["pattern"] = "[!-~]+"
                        attributes["title"] = "请使用 ASCII 字符，不含空格"
                        autoFocus = true
                        required = true
                    }
                }

                div(classes = "login-field") {
                    label {
                        htmlFor = "password"
                        +"密码"
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
                    +"登录"
                }
            }
        }

        p(classes = "auth-footer") {
            a(href = Config.ANDROID_APK_URL) {
                attributes["download"] = "kio-chat.apk"
                +"下载 Android 版"
            }
        }

        p(classes = "auth-footer") {
            +"还没有账号？ "
            a(href = "/register") {
                +"注册账号"
            }
        }
    }
}
