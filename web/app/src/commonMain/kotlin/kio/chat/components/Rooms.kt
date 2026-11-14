package kio.chat.components

import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomRole
import kio.chat.domain.RoomMember
import kio.chat.domain.Room
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.roomAsideMenu() {
    header(classes = "sidebar-header") {
        h1(classes = "sidebar-title") { +"Kio Chat" }
        p(classes = "section-title") { +"聊天室" }
        details(classes = "join-room") {
            summary { +"加入聊天室" }
            form {
                hxPost = "/rooms/join-requests"
                hxTarget = "#join-room-result"
                hxSwap = "innerHTML"
                attributes["hx-disabled-elt"] = "find button"
                label {
                    +"房间 ID"
                    numberInput {
                        name = "roomId"
                        required = true
                        min = "1"
                    }
                }
                button(type = ButtonType.submit) { +"发送申请" }
                div { id = "join-room-result" }
            }
        }
    }
    nav(classes = "sidebar-content") {
        attributes["aria-label"] = "聊天室"
        div { id = "room-list-items" }
    }
    footer(classes = "sidebar-footer") {
        roomJoinRequestsButton()
        roomJoinRequestsDialog()
        button(type = ButtonType.button, classes = "logout-button") {
            hxPost = "/logout"
            hxSwap = "none"
            +"退出登录"
        }
    }
}

fun TagConsumer<*>.roomList(rooms: List<Room>, selectedRoomId: Long?) {
    rooms.forEach { room -> roomItem(room, room.id == selectedRoomId) }
}

fun TagConsumer<*>.roomItem(room: Room, selected: Boolean = false, outOfBand: Boolean = false) {
    div(classes = "room-item") {
        id = "room-link-${room.id}"
        if (outOfBand) hxSwapOob = "outerHTML"
        button(type = ButtonType.button, classes = "room-link") {
            hxGet = "/rooms/${room.id}/content"
            hxTarget = "#room-content"
            hxSwap = "innerHTML"
            hxPushUrl = "/rooms/${room.id}"
            if (selected) attributes["aria-current"] = "page"
            attributes["hx-on:click"] = """
                document.querySelectorAll('.room-link[aria-current="page"]')
                    .forEach(it => it.removeAttribute('aria-current'));
                this.setAttribute('aria-current', 'page');
            """.trimIndent()
            span(classes = "room-name") { +room.name }
        }
        roomItemOptionsMenu(room.id)
    }
}

private fun TagConsumer<*>.roomItemOptionsMenu(roomId: Long) {
    details(classes = "menu room-item-menu") {
        summary(classes = "menu-trigger") {
            attributes["aria-label"] = "聊天室选项"
            +"⋯"
        }
        div(classes = "menu-popup") {
            button(type = ButtonType.button, classes = "menu-item") {
                hxPost = "/rooms/$roomId/name-editing"
                hxTarget = "closest .room-item"
                hxSwap = "outerHTML"
                +"修改名称"
            }
        }
    }
}

fun TagConsumer<*>.roomNameEditor(room: Room) {
    div(classes = "room-item room-item-editing") {
        id = "room-link-${room.id}"
        textInput(classes = "room-name-input") {
            name = "name"
            value = room.name
            autoFocus = true
            required = true
            attributes["aria-label"] = "聊天室名称"
            hxPost = "/rooms/${room.id}/name-commit"
            hxTrigger = "keydown[key=='Enter' && !isComposing], blur changed"
            hxTarget = "closest .room-item"
            hxSwap = "none"
            attributes["hx-sync"] = "this:drop"
        }
    }
}

fun TagConsumer<*>.chatRoomContent(room: Room) {
    val messagePath = "/rooms/${room.id}/messages"
    div(classes = "chat-room") {
        attributes["hx-ext"] = "ws"
        attributes["ws-connect"] = "$messagePath/events"
        attributes["hx-on:htmx:ws-after-message"] = "scrollMessageList(this)"
        div(classes = "chat-conversation") {
            header(classes = "chat-header") {
                div {
                    h2 {
                        id = "room-title-${room.id}"
                        attributes["data-room-id"] = room.id.toString()
                        +room.name
                    }
                    small { +"房间 ID：${room.id}" }
                }
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
            hxTrigger = "load"
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
                        RoomRole.Owner -> "群主"
                        RoomRole.Member -> "成员"
                    }
                }
            }
        }
    }
}

private fun TagConsumer<*>.roomJoinRequestsButton() {
    button(type = ButtonType.button, classes = "logout-button") {
        attributes["commandfor"] = "join-requests-dialog"
        attributes["command"] = "show-modal"
        +"入群申请"
        roomJoinRequestCount()
    }
}

private fun TagConsumer<*>.roomJoinRequestCount() {
    span(classes = "join-request-count") {
        id = "join-request-count"
        hidden = true
        attributes["aria-label"] = "0 条待处理申请"
        +"0"
    }
}

private fun TagConsumer<*>.roomJoinRequestsDialog() {
    dialog(classes = "join-requests-dialog") {
        id = "join-requests-dialog"
        attributes["aria-labelledby"] = "join-requests-title"
        header(classes = "members-header") {
            h2 { id = "join-requests-title"; +"入群申请" }
            button(type = ButtonType.button, classes = "members-close") {
                attributes["commandfor"] = "join-requests-dialog"
                attributes["command"] = "close"
                attributes["aria-label"] = "关闭申请列表"
                +"×"
            }
        }
        div {
            id = "join-request-result"
            attributes["aria-live"] = "polite"
        }
        div { id = "room-join-requests" }
    }
}

fun TagConsumer<*>.roomJoinRequestList(requests: List<RoomJoinRequest>) {
    requests.forEach { request -> roomJoinRequestItem(request) }
}

private fun TagConsumer<*>.roomJoinRequestItem(request: RoomJoinRequest) {
    article(classes = "join-request") {
        id = "join-request-${request.id}"
        p { +request.username }
        p(classes = "join-request-room") { +"申请加入「${request.roomName}」" }
        div(classes = "join-request-actions") {
            button(type = ButtonType.button) {
                hxPost = "/rooms/join-requests/${request.id}/approve"
                hxTarget = "#join-request-result"
                hxSwap = "innerHTML"
                attributes["hx-disabled-elt"] = "this"
                +"同意"
            }
            button(type = ButtonType.button) {
                hxDelete = "/rooms/join-requests/${request.id}"
                hxTarget = "#join-request-result"
                hxSwap = "innerHTML"
                +"拒绝"
            }
        }
    }
}

fun TagConsumer<*>.roomJoinRequestInitial(requests: List<RoomJoinRequest>) {
    div {
        id = "room-join-requests"
        hxSwapOob = "innerHTML"
        roomJoinRequestList(requests)
    }
}

fun TagConsumer<*>.roomJoinRequestAdded(request: RoomJoinRequest) {
    div {
        id = "room-join-requests"
        hxSwapOob = "beforeend"
        roomJoinRequestItem(request)
    }
}

fun TagConsumer<*>.roomJoinRequestRemoved(requestId: Long) {
    div {
        id = "join-request-$requestId"
        hxSwapOob = "delete"
    }
}

fun TagConsumer<*>.roomListInitial(rooms: List<Room>, selectedRoomId: Long?) {
    div {
        id = "room-list-items"
        hxSwapOob = "innerHTML"
        roomList(rooms, selectedRoomId)
    }
}

fun TagConsumer<*>.roomListAdded(room: Room) {
    div {
        id = "room-list-items"
        hxSwapOob = "beforeend"
        roomList(listOf(room), null)
    }
}

fun TagConsumer<*>.roomListDeleted(roomId: Long) {
    div {
        id = "room-link-$roomId"
        hxSwapOob = "delete"
    }
}

fun TagConsumer<*>.roomListChanged(room: Room) {
    roomItem(room, outOfBand = true)
}
