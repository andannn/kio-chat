package kio.chat.page

import kio.chat.domain.Repository
import kio.chat.domain.Room
import io.ktor.http.HttpStatusCode
import kio.http.respond
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

context(repo: Repository)
suspend fun CallContext.chatMainPage(roomId: Long? = null) {
    val rooms = repo.getRoomList()
    val room = roomId?.let { repo.getRoomById(it) }
    if (roomId != null && room == null) {
        respond(HttpStatusCode.NotFound, "聊天室不存在。")
        return
    }
    val sessionId = checkNotNull(requestCookies()["session"])
    respondHtml(configHeaders = {
        appendCookie(sessionCookie(sessionId))
    }) {
        chatHead("Kio Chat")
        body(classes = "app chat-body") {
            aside(classes = "sidebar") {
                header(classes = "sidebar-header") {
                    h1(classes = "sidebar-title") { +"Kio Chat" }
                    p(classes = "section-title") { +"聊天室" }
                }
                nav(classes = "sidebar-content") {
                    attributes["aria-label"] = "聊天室"
                    rooms.forEach { entry ->
                        a(href = "/rooms/${entry.id}", classes = "room-link") {
                            if (entry.id == room?.id) attributes["aria-current"] = "page"
                            +entry.name
                        }
                    }
                    if (rooms.isEmpty()) p(classes = "section-title") { +"暂无聊天室" }
                }
                footer(classes = "sidebar-footer") {
                    button(type = ButtonType.button, classes = "logout-button") {
                        hxPost = "/logout"
                        hxSwap = "none"
                        +"退出登录"
                    }
                }
            }
            main(classes = "chat-layout") {
                if (room == null) {
                    div(classes = "chat-empty") { +"请选择左侧聊天室" }
                } else {
                    chatRoomContent(room)
                }
            }
        }
    }
}

internal fun TagConsumer<*>.chatRoomContent(room: Room) {
    val messagePath = "/rooms/${room.id}/messages"
    div(classes = "chat-room") {
        attributes["hx-ext"] = "ws"
        attributes["ws-connect"] = "$messagePath/events"
        attributes["hx-on:htmx:ws-after-message"] = "scrollMessageList(this)"
        header(classes = "chat-header") {
            h2 { +room.name }
        }
        section(classes = "chat-messages") {
            id = "messages"
        }
        form(classes = "chat-composer") {
            id = "message-form"
            hxPost = messagePath
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
