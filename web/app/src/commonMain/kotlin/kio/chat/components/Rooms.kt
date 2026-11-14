package kio.chat.components

import kio.chat.domain.Room
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.roomAsideMenu(selectedRoomId: Long? = null) {
    header(classes = "sidebar-header") {
        h1(classes = "sidebar-title") { +"Kio Chat" }
        p(classes = "section-title") { +"聊天室" }
    }
    nav(classes = "sidebar-content") {
        attributes["aria-label"] = "聊天室"
        hxGet = "/rooms?selectedRoomId=${selectedRoomId?.toString().orEmpty()}"
        hxTrigger = "load"
        hxTarget = "#room-list-items"
        hxSwap = "innerHTML"
        div { id = "room-list-items" }
    }
    footer(classes = "sidebar-footer") {
        button(type = ButtonType.button, classes = "logout-button") {
            hxPost = "/logout"
            hxSwap = "none"
            +"退出登录"
        }
    }
}

fun TagConsumer<*>.roomList(rooms: List<Room>, selectedRoomId: Long?) {
    rooms.forEach { room ->
        button(type = ButtonType.button, classes = "room-link") {
            hxGet = "/rooms/${room.id}/content"
            hxTarget = "#room-content"
            hxSwap = "innerHTML"
            hxPushUrl = "/rooms/${room.id}"
            if (room.id == selectedRoomId) attributes["aria-current"] = "page"
            attributes["hx-on:click"] = """
                document.querySelectorAll('.room-link[aria-current="page"]')
                    .forEach(it => it.removeAttribute('aria-current'));
                this.setAttribute('aria-current', 'page');
            """.trimIndent()
            +room.name
        }
    }
    if (rooms.isEmpty()) p(classes = "section-title") { +"暂无聊天室" }
}

fun TagConsumer<*>.chatRoomContent(room: Room) {
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
