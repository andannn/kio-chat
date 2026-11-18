package kio.chat.page

import kio.chat.components.roomAsideMenu
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

suspend fun CallContext.chatMainPage(roomId: Long? = null) {
    val sessionId = checkNotNull(requestCookies()["session"])
    respondHtml(configHeaders = {
        appendCookie(sessionCookie(sessionId))
    }) {
        chatHead("Kio Chat")
        body(classes = "app chat-body") {
            attributes["hx-on:htmx:oob-before-swap"] = "filterUserEventSwap(event)"
            aside(classes = "sidebar") {
                attributes["hx-ext"] = "ws"
                attributes["ws-connect"] = "/events?selectedRoomId=${roomId?.toString().orEmpty()}"
                roomAsideMenu()
            }
            main(classes = "chat-layout") {
                id = "room-content"
                if (roomId == null) {
                    div(classes = "chat-empty") { +"请选择左侧聊天室" }
                } else {
                    div {
                        hxGet = "/rooms/$roomId/content"
                        hxTrigger = "load"
                        hxTarget = "#room-content"
                        hxSwap = "innerHTML"
                    }
                }
            }
        }
    }
}
