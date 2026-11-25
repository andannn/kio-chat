package kio.chat.components

import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomRole
import kio.chat.domain.RoomMember
import kio.chat.domain.VoiceParticipantsEvent
import kio.chat.domain.Room
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.roomAsideMenu(selectedRoomId: Long? = null) {
    header(classes = "sidebar-header") {
        div(classes = "sidebar-header-top") {
            h1(classes = "sidebar-title") { +"Kio Chat" }
            div(classes = "sidebar-actions") {
                button(type = ButtonType.button, classes = "icon-action-btn") {
                    attributes["aria-label"] = "加入聊天室"
                    attributes["title"] = "加入聊天室"
                    attributes["onclick"] = "document.getElementById('join-room-dialog')?.showModal()"
                    +"+"
                }
                button(type = ButtonType.button, classes = "icon-action-btn") {
                    attributes["aria-label"] = "新建聊天室"
                    attributes["title"] = "新建聊天室"
                    attributes["onclick"] = "document.getElementById('create-room-dialog')?.showModal()"
                    +"✎"
                }
            }
        }
        createRoomModal()
        joinRoomModal()
        p(classes = "section-title") { +"聊天室" }
    }
    nav(classes = "sidebar-content") {
        attributes["aria-label"] = "聊天室"
        div {
            id = "room-list-items"
            hxGet = "/rooms?selectedRoomId=${selectedRoomId?.toString().orEmpty()}"
            hxTrigger = "load"
            hxSwap = "innerHTML"
            attributes["hx-headers"] = """{"Accept":"text/html"}"""
            attributes["hx-sync"] = "this:replace"
        }
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

private fun TagConsumer<*>.createRoomModal() {
    dialog(classes = "app-dialog") {
        id = "create-room-dialog"
        div(classes = "dialog-content") {
            header(classes = "dialog-header") {
                h2 { +"创建聊天室" }
                button(type = ButtonType.button, classes = "members-close") {
                    attributes["aria-label"] = "关闭"
                    attributes["onclick"] = "this.closest('dialog').close()"
                    +"×"
                }
            }
            form {
                hxPost = "/rooms"
                hxTarget = "#create-room-result"
                hxSwap = "innerHTML"
                attributes["hx-disabled-elt"] = "find button"
                hxOnAfterRequest = "if (event.detail.successful) { this.reset(); document.getElementById('create-room-dialog')?.close(); }"
                div(classes = "dialog-form-field") {
                    label {
                        +"聊天室名称"
                        textInput(classes = "dialog-input") {
                            name = "name"
                            required = true
                            placeholder = "给聊天室起个名字"
                        }
                    }
                }
                div(classes = "dialog-actions") {
                    button(type = ButtonType.button, classes = "btn-secondary") {
                        attributes["onclick"] = "this.closest('dialog').close()"
                        +"取消"
                    }
                    button(type = ButtonType.submit, classes = "btn-primary") { +"创建" }
                }
                div {
                    id = "create-room-result"
                    attributes["role"] = "status"
                }
            }
        }
    }
}

private fun TagConsumer<*>.joinRoomModal() {
    dialog(classes = "app-dialog") {
        id = "join-room-dialog"
        div(classes = "dialog-content") {
            header(classes = "dialog-header") {
                h2 { +"加入聊天室" }
                button(type = ButtonType.button, classes = "members-close") {
                    attributes["aria-label"] = "关闭"
                    attributes["onclick"] = "this.closest('dialog').close()"
                    +"×"
                }
            }
            form {
                hxPost = "/rooms/join-requests"
                hxTarget = "#join-room-result"
                hxSwap = "innerHTML"
                attributes["hx-disabled-elt"] = "find button"
                hxOnAfterRequest = "if (event.detail.successful) { this.reset(); }"
                div(classes = "dialog-form-field") {
                    label {
                        +"房间 ID"
                        numberInput(classes = "dialog-input") {
                            name = "roomId"
                            required = true
                            min = "1"
                            placeholder = "输入房间 ID"
                        }
                    }
                }
                div(classes = "dialog-actions") {
                    button(type = ButtonType.button, classes = "btn-secondary") {
                        attributes["onclick"] = "this.closest('dialog').close()"
                        +"取消"
                    }
                    button(type = ButtonType.submit, classes = "btn-primary") { +"发送申请" }
                }
                div {
                    id = "join-room-result"
                    attributes["role"] = "status"
                }
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
                document.querySelectorAll('.room-link[aria-current="page"]').forEach(el => el.removeAttribute('aria-current'));
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

fun TagConsumer<*>.chatRoomContent(room: Room, isOwner: Boolean = false) {
    val messagePath = "/rooms/${room.id}/messages"
    div(classes = "chat-room") {
        attributes["data-room-id"] = room.id.toString()
        div(classes = "chat-conversation") {
            header(classes = "chat-header") {
                div(classes = "room-title-area") {
                    h2 {
                        id = "room-title-${room.id}"
                        attributes["data-room-id"] = room.id.toString()
                        +room.name
                    }
                    span(classes = "room-id-badge") { +"ID: ${room.id}" }
                }

                div(classes = "room-header-actions") {
                    button(type = ButtonType.button, classes = "btn-voice") {
                        id = "voice-toggle"
                        attributes["data-room-id"] = room.id.toString()
                        attributes["onclick"] = "toggleVoice('${room.id}')"
                        attributes["aria-pressed"] = "false"
                        +"加入语音"
                    }

                    button(type = ButtonType.button, classes = "members-toggle") {
                        attributes["aria-controls"] = "room-members-panel"
                        attributes["onclick"] = "const p = document.getElementById('room-members-panel'); if (p) { p.hidden = !p.hidden; this.classList.toggle('active', !p.hidden); }"
                        +"成员"
                    }
                }
            }
            section(classes = "voice-participants") {
                attributes["data-voice-participants"] = ""
                attributes["aria-label"] = "正在语音通话的成员"
                hxGet = "/rooms/${room.id}/voice/members"
                hxTrigger = "load"
                hxSwap = "innerHTML"
                attributes["hx-headers"] = """{"Accept":"text/html"}"""
                attributes["hx-sync"] = "this:drop"
                voiceParticipantList(room.id, emptyList())
            }
            section(classes = "chat-messages") {
                id = "messages-${room.id}"
                hxGet = "$messagePath?beforeId=${Long.MAX_VALUE}"
                hxTrigger = "load"
                hxSwap = "innerHTML scroll:bottom"
            }
            form(classes = "chat-composer") {
                id = "message-form"
                hxPost = messagePath
                hxSwap = "none"
                hxOnAfterRequest = "if (event.detail.successful) { this.reset(); const t = this.querySelector('textarea'); if (t) { t.style.height = 'auto'; } }"
                div(classes = "composer-input-wrapper") {
                    input(type = InputType.file, classes = "image-file-input") {
                        id = "image-upload-input"
                        attributes["accept"] = "image/*"
                        attributes["style"] = "display: none;"
                        attributes["data-upload-url"] = "$messagePath/image"
                        attributes["onchange"] = "window.handleChatImageUpload && window.handleChatImageUpload(this)"
                    }
                    button(type = ButtonType.button, classes = "composer-image-button") {
                        id = "image-button"
                        attributes["aria-label"] = "发送图片"
                        attributes["title"] = "发送图片"
                        attributes["onclick"] = "document.getElementById('image-upload-input')?.click()"
                        unsafe {
                            raw("""<svg class="google-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M5 21q-.825 0-1.412-.587T3 19V5q0-.825.588-1.412T5 3h14q.825 0 1.413.588T21 5v14q0 .825-.587 1.413T19 21zm0-2h14V5H5zm1-2h12l-3.75-5-3 4L9 13.5zm-1 2V5z"/></svg>""")
                        }
                    }
                    textArea {
                        id = "content"
                        name = "content"
                        required = true
                        rows = "1"
                        placeholder = "输入消息…（Enter 发送，Shift+Enter 换行，支持粘贴图片）"
                        attributes["aria-label"] = "消息"
                        attributes["hx-on:keydown"] = "if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); if (!event.repeat && this.value.trim().length > 0) this.form?.requestSubmit(); }"
                        attributes["oninput"] = "this.style.height = 'auto'; this.style.height = Math.min(this.scrollHeight, 180) + 'px';"
                    }
                    button(type = ButtonType.submit, classes = "composer-send-button") {
                        id = "send-button"
                        attributes["aria-label"] = "发送消息"
                        +"发送"
                    }
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

fun TagConsumer<*>.voiceParticipantList(roomId: Long, members: List<RoomMember>) {
    div(classes = "voice-participants-header") {
        strong { +"语音通话中" }
    }
    p(classes = "voice-participants-status") {
        attributes["data-voice-participant-status"] = ""
        attributes["role"] = "status"
        hidden = true
    }
    ul(classes = "voice-participants-list") {
        id = "voice-participants-list-$roomId"
        members.forEach { member ->
            li { voiceParticipantItem(roomId, member) }
        }
    }
}

private fun LI.voiceParticipantItem(roomId: Long, member: RoomMember) {
    id = "voice-participant-$roomId-${member.userId}"
    +member.nickname
    if (member.role == RoomRole.Owner) {
        span(classes = "member-role") { +"群主" }
    }
}

fun TagConsumer<*>.voiceParticipantEvent(event: VoiceParticipantsEvent) {
    when (event) {
        is VoiceParticipantsEvent.Added -> ul {
            id = "voice-participants-list-${event.roomId}"
            hxSwapOob = "beforeend"
            li { voiceParticipantItem(event.roomId, event.member) }
        }
        is VoiceParticipantsEvent.Removed -> div {
            id = "voice-participant-${event.roomId}-${event.userId}"
            hxSwapOob = "delete"
        }
    }
}

fun TagConsumer<*>.roomMemberList(members: List<RoomMember>) {
    ul {
        members.forEach { member ->
            li {
                +member.nickname
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
        attributes["onclick"] = "document.getElementById('join-requests-dialog')?.showModal()"
        +"入群申请"
    }
}

private fun TagConsumer<*>.roomJoinRequestsDialog() {
    dialog(classes = "app-dialog join-requests-dialog") {
        id = "join-requests-dialog"
        attributes["aria-labelledby"] = "join-requests-title"
        div(classes = "dialog-content") {
            header(classes = "dialog-header") {
                h2 { id = "join-requests-title"; +"入群申请" }
                button(type = ButtonType.button, classes = "members-close") {
                    attributes["aria-label"] = "关闭申请列表"
                    attributes["onclick"] = "this.closest('dialog')?.close()"
                    +"×"
                }
            }
            div {
                id = "join-request-result"
                attributes["aria-live"] = "polite"
            }
            div {
                id = "room-join-requests"
                hxGet = "/rooms/join-requests"
                hxTrigger = "load"
                hxSwap = "innerHTML"
                attributes["hx-headers"] = """{"Accept":"text/html"}"""
                attributes["hx-sync"] = "this:replace"
            }
        }
    }
}

fun TagConsumer<*>.roomJoinRequestList(requests: List<RoomJoinRequest>) {
    requests.forEach { request -> roomJoinRequestItem(request) }
}

private fun TagConsumer<*>.roomJoinRequestItem(request: RoomJoinRequest) {
    article(classes = "join-request") {
        id = "join-request-${request.id}"
        attributes["data-room-id"] = request.roomId.toString()
        p { +request.nickname }
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
    div {
        id = "room-content"
        attributes["hx-swap-oob"] = "innerHTML:[data-room-id='$roomId']"
        div(classes = "chat-empty") { +"聊天室已被解散或退出" }
    }
}

fun TagConsumer<*>.roomListChanged(room: Room) {
    roomItem(room, outOfBand = true)
}
