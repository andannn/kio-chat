package kio.chat.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.requireSession
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
    val session = requireSession()
    respondHtml {
        chatHead("Chat")
        body(classes = "app chat-body") {
            main(classes = "chat-layout") {
                attributes["hx-ext"] = "ws"
                attributes["ws-connect"] = "/messages/events"
                header(classes = "chat-header") {
                    h1 { +"Kio Chat" }
                    p(classes = "chat-user") { +"User ${session.userId}" }
                }
                section(classes = "chat-messages") {
                    id = "messages"
                }
                form(classes = "chat-composer") {
                    id = "message-form"
                    hxPost = "/messages"
                    hxSwap = "none"

                    textArea {
                        id = "content"
                        name = "content"
                        required = true
                        rows = "3"
                        placeholder = "Write a message…"
                        attributes["aria-label"] = "Message"
                    }
                    button(type = ButtonType.submit) {
                        id = "send-button"

                        +"Send"
                    }
                }
            }
        }
    }
}
