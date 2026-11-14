package kio.chat.components

import kio.chat.domain.RoomRole
import kio.chat.domain.RoomMember
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
        div(classes = "chat-conversation") {
            header(classes = "chat-header") {
                h2 { +room.name }
                button(type = ButtonType.button, classes = "members-toggle") {
                    attributes["aria-controls"] = "room-members-panel"
                    attributes["aria-expanded"] = "false"
                    attributes["hx-on:click"] = "toggleRoomMembers(this)"
                    +"成员"
                }
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
        aside(classes = "room-members-panel") {
            id = "room-members-panel"
            hidden = true
            attributes["aria-label"] = "房间成员"
            hxGet = "/rooms/${room.id}/members"
            hxTrigger = "members-open"
            hxTarget = "#room-members-list"
            hxSwap = "innerHTML"
            header(classes = "members-header") {
                h2 { +"房间成员" }
                button(type = ButtonType.button, classes = "members-close") {
                    attributes["aria-label"] = "关闭成员列表"
                    attributes["hx-on:click"] = "toggleRoomMembers(this)"
                    +"×"
                }
            }
            div(classes = "members-content") {
                id = "room-members-list"
            }
        }
    }
}

fun TagConsumer<*>.roomMemberList(members: List<RoomMember>) {
    ul {
        members.forEach { member ->
            li {
                +member.username
                span(classes = "member-role") {
                    +when (member.role) {
                        RoomRole.Owner -> "所有者"
                        RoomRole.Member -> "成员"
                    }
                }
            }
        }
    }
}
