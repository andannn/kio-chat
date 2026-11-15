package kio.chat.components

import kio.chat.domain.Message
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
        roomCreateForm()
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

private fun TagConsumer<*>.roomCreateForm() {
    details(classes = "join-room") {
        summary { +"创建聊天室" }
        form {
            hxPost = "/rooms"
            hxTarget = "#create-room-result"
            hxSwap = "innerHTML"
            attributes["hx-disabled-elt"] = "find button"
            label {
                +"聊天室名称"
                textInput {
                    name = "name"
                    required = true
                    placeholder = "给聊天室起个名字"
                }
            }
            button(type = ButtonType.submit) { +"创建" }
            div {
                id = "create-room-result"
                attributes["role"] = "status"
            }
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
            roomMessagePreview(room.id, null)
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
        roomMessagePreview(room.id, null)
    }
}

fun TagConsumer<*>.chatRoomContent(room: Room, isOwner: Boolean = false) {
    val messagePath = "/rooms/${room.id}/messages"
    div(classes = "chat-room") {
        attributes["data-room-id"] = room.id.toString()
        attributes["hx-on:htmx:oob-after-swap"] =
            "if (event.detail.shouldSwap && event.detail.target.id === 'messages') scrollMessageList(this)"
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
                hxGet = "$messagePath?beforeId=${Long.MAX_VALUE}"
                hxTrigger = "load"
                hxSwap = "innerHTML"
                attributes["hx-on:htmx:after-swap"] =
                    "if (event.detail.target === this) scrollMessageList(this.closest('.chat-room'))"
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
            if (isOwner) {
                div(classes = "room-danger-zone") {
                    button(type = ButtonType.button, classes = "room-disband-button") {
                        hxDelete = "/rooms/${room.id}"
                        hxTarget = "#room-delete-result"
                        hxSwap = "innerHTML"
                        hxConfirm = "确定解散「${room.name}」吗？所有成员将被移出，聊天记录会被删除，无法恢复。"
                        attributes["hx-disabled-elt"] = "this"
                        +"解散聊天室"
                    }
                    div {
                        id = "room-delete-result"
                        attributes["role"] = "status"
                    }
                }
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
        attributes["data-room-id"] = request.roomId.toString()
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

fun TagConsumer<*>.roomMessagePreview(
    roomId: Long,
    message: Message?,
    outOfBand: Boolean = false,
    changed: Boolean = false,
) {
    span(classes = "room-message-preview") {
        id = "room-message-preview-$roomId"
        attributes["data-message-id"] = message?.id?.toString().orEmpty()
        if (outOfBand) {
            hxSwapOob = "outerHTML"
            attributes["data-message-changed"] = changed.toString()
        } else {
            attributes["hx-preserve"] = "true"
        }
        if (message == null) +"还没有消息"
        else +"${message.username}：${message.text}"
    }
}
