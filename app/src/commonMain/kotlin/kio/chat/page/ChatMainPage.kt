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
        body {
            main {
                attributes["hx-ext"] = "ws"
                attributes["ws-connect"] = "/messages/events"
                h1 { +"Kio Chat" }
                p { +"Signed in as user ${session.userId}" }
                section {
                    id = "messages"
                    hxGet = "/messages"
                    hxTrigger = "load"
                    hxSwap = "innerHTML"
                }
                form {
                    id = "message-form"
                    hxPost = "/messages"
                    hxSwap = "none"

                    textArea {
                        id = "content"
                        name = "content"
                        required = true
                        rows = "3"
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
