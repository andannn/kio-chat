package kio.chat.components

import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomRole
import kio.chat.domain.RoomMember
import kio.chat.domain.Room
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.roomAsideMenu(selectedRoomId: Long? = null) {
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
        hxGet = "/rooms?selectedRoomId=${selectedRoomId?.toString().orEmpty()}"
        hxTrigger = "load"
        hxTarget = "#room-list-items"
        hxSwap = "innerHTML"
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
    if (rooms.isEmpty()) p(classes = "section-title") { +"还没有聊天室，试试加入一个吧" }
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
                    h2 { +room.name }
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
        roomJoinRequestCount(0)
    }
}

fun TagConsumer<*>.roomJoinRequestCount(count: Int, outOfBand: Boolean = false) {
    span(classes = "join-request-count") {
        id = "join-request-count"
        hidden = count == 0
        attributes["aria-label"] = "$count 条待处理申请"
        if (outOfBand) hxSwapOob = "outerHTML"
        +count.toString()
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
