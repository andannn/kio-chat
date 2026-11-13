package kio.chat.page

import kio.chat.sessionCookie
import kio.http.appendCookie
import kio.http.requestCookies
import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.chatHead(title: String) {
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        script(src = Config.HTMX_MIN_JS) { }
        script(src = Config.HTMX_WS_JS) { }
        script(src = Config.APP_JS) { attributes["defer"] = "defer" }
        link(rel = "stylesheet", href = Config.APP_CSS)
        title { +title }
    }
}

suspend fun CallContext.chatMainPage() {
    val sessionId = checkNotNull(requestCookies()["session"])
    respondHtml(configHeaders = {
        appendCookie(sessionCookie(sessionId))
    }) {
        chatHead("Kio Chat")
        body(classes = "app chat-body") {
            main(classes = "chat-layout") {
                attributes["hx-ext"] = "ws"
                attributes["ws-connect"] = "/messages/events"
                attributes["hx-on:htmx:ws-after-message"] = "scrollMessageList(this)"
                header(classes = "chat-header") {
                    h1 { +"Kio Chat" }
                    button(type = ButtonType.button, classes = "logout-button") {
                        hxPost = "/logout"
                        hxSwap = "none"
                        span(classes = "logout-icon") {
                            attributes["aria-hidden"] = "true"
                            unsafe {
                                raw("""<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5H5v14h4M14 8l4 4-4 4M9 12h9"/></svg>""")
                            }
                        }
                        +"退出登录"
                    }
                }
                section(classes = "chat-messages") {
                    id = "messages"
                }
                form(classes = "chat-composer") {
                    id = "message-form"
                    hxPost = "/messages"
                    hxSwap = "none"
                    hxOnAfterRequest = "if (event.detail.successful) this.reset()"

                    textArea {
                        id = "content"
                        name = "content"
                        required = true
                        rows = "3"
                        placeholder = "输入消息…"
                        attributes["aria-label"] = "消息"
                        attributes["hx-on:keydown"] = "handleMessageKeydown(event, this)"
                    }
                    button(type = ButtonType.submit) {
                        id = "send-button"

                        +"发送"
                    }
                }
            }
        }
    }
}
