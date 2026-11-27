package kio.chat.components

import kio.chat.domain.Message
import kio.chat.domain.MessageExtra
import kio.chat.domain.RoomRole
import kio.chat.domain.UserChatEvent
import kio.chat.util.*
import kotlinx.html.*

private fun ARTICLE.messageItem(message: Message, isMine: Boolean, roomId: Long) {
    id = "message-${message.id}"
    attributes["data-mine"] = isMine.toString()
    div(classes = "message-header") {
        span(classes = "message-sender") { +message.nickname }
        when (message.member.role) {
            RoomRole.Owner -> span(classes = "message-role message-role-owner") { +"群主" }
            RoomRole.Member -> {}
        }
    }
    div(classes = "message-bubble") {
        when (val extra = message.extra) {
            is MessageExtra.Image -> {
                a(href = extra.url, target = "_blank", classes = "message-image-link") {
                    img(classes = "message-image", src = extra.url, alt = "图片") {
                        attributes["loading"] = "lazy"
                        if (extra.width > 0 && extra.height > 0) {
                            attributes["style"] = "aspect-ratio: ${extra.width} / ${extra.height};"
                        }
                    }
                }
            }
            is MessageExtra.Voice -> {
                p(classes = "message-text") {
                    +message.text
                }
            }
            null -> {
                p(classes = "message-text") {
                    +message.text
                }
            }
        }
        if (isMine) {
            details(classes = "menu message-menu") {
                summary(classes = "menu-trigger") {
                    attributes["aria-label"] = "消息操作"
                    +"⋯"
                }
                div(classes = "menu-popup") {
                    if (message.extra == null) {
                        button(type = ButtonType.button, classes = "menu-item") {
                            hxGet = "/rooms/$roomId/messages/${message.id}/editor"
                            hxTarget = "closest article"
                            hxSwap = "outerHTML"
                            +"编辑"
                        }
                    }
                    button(type = ButtonType.button, classes = "menu-item menu-item-danger") {
                        hxDelete = "/rooms/$roomId/messages/${message.id}"
                        hxSwap = "none"
                        hxConfirm = "确定删除这条消息吗？此操作无法撤销。"
                        +"删除"
                    }
                }
            }
        }
    }
}

fun TagConsumer<*>.messageView(message: Message, isMine: Boolean, roomId: Long) {
    article { messageItem(message, isMine, roomId) }
}

fun TagConsumer<*>.messageEditor(message: Message, roomId: Long) {
    article(classes = "message-editing") {
        id = "message-${message.id}"
        attributes["data-mine"] = "true"
        form(classes = "message-edit-form") {
            hxPatch = "/rooms/$roomId/messages/${message.id}"
            hxSwap = "none"
            attributes["hx-disabled-elt"] = "find button"
            textArea {
                name = "content"
                required = true
                autoFocus = true
                rows = "3"
                attributes["maxlength"] = "2000"
                attributes["aria-label"] = "编辑消息"
                +message.text
            }
            div(classes = "message-edit-actions") {
                button(type = ButtonType.button) {
                    hxGet = "/rooms/$roomId/messages/${message.id}"
                    hxTarget = "closest article"
                    hxSwap = "outerHTML"
                    +"取消"
                }
                button(type = ButtonType.submit) { +"保存" }
            }
        }
    }
}

fun TagConsumer<*>.messageHistory(messages: List<Message>, currentUserId: Long, hasMore: Boolean, roomId: Long) {
    messageHistoryControl(messages.firstOrNull()?.id, hasMore, roomId)
    messages.forEach { message ->
        article { messageItem(message, message.member.userId == currentUserId, roomId) }
    }
}

private fun TagConsumer<*>.messageHistoryControl(beforeId: Long?, hasMore: Boolean, roomId: Long) {
    div(classes = "message-history-wrapper") {
        id = "message-history"
        if (hasMore && beforeId != null) {
            button(type = ButtonType.button, classes = "load-history-button") {
                hxGet = "/rooms/$roomId/messages?beforeId=$beforeId"
                hxTarget = "#message-history"
                hxSwap = "outerHTML"
                attributes["hx-disabled-elt"] = "this"
                +"加载更早的消息"
            }
        }
    }
}

fun TagConsumer<*>.chatMessageEvent(event: UserChatEvent, userId: Long) {
    when (event) {
        is UserChatEvent.Added -> {
            div {
                id = "messages-${event.roomId}"
                hxSwapOob = "beforeend"
                messageView(event.message, event.message.member.userId == userId, event.roomId)
            }
        }
        is UserChatEvent.Changed -> {
            article {
                hxSwapOob = "outerHTML"
                messageItem(event.message, event.message.member.userId == userId, event.roomId)
            }
        }
        is UserChatEvent.Deleted -> div {
            id = "message-${event.messageId}"
            hxSwapOob = "delete"
        }
    }
}
