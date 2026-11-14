package kio.chat.components

import kio.chat.domain.Message
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.messageList(messages: List<Message>, currentUserId: Long, roomId: Long) {
    div {
        id = "messages"
        hxSwapOob = "innerHTML"

        messageHistoryControl(messages.firstOrNull()?.id, messages.size == 50, roomId)
        messages.forEach { message ->
            article { messageItem(message, message.user.id == currentUserId, roomId) }
        }
    }
}

fun TagConsumer<*>.messageAppend(message: Message, isMine: Boolean, roomId: Long) {
    div {
        id = "messages"
        hxSwapOob = "beforeend"
        article { messageItem(message, isMine, roomId) }
    }
}

private fun ARTICLE.messageItem(message: Message, isMine: Boolean, roomId: Long) {
    id = "message-${message.id}"
    attributes["data-mine"] = isMine.toString()
    p {
        strong { +"${message.username}: " }
        +message.text
    }
    if (isMine) {
        details(classes = "menu message-menu") {
            summary(classes = "menu-trigger") {
                attributes["aria-label"] = "消息选项"
                +"⋯"
            }
            div(classes = "menu-popup") {
                button(type = ButtonType.button, classes = "menu-item") {
                    hxGet = "/rooms/$roomId/messages/${message.id}/editor"
                    hxTarget = "closest article"
                    hxSwap = "outerHTML"
                    +"编辑"
                }
                button(type = ButtonType.button, classes = "menu-item") {
                    hxDelete = "/rooms/$roomId/messages/${message.id}"
                    hxSwap = "none"
                    hxConfirm = "确定删除这条消息吗？"
                    +"删除"
                }
            }
        }
    }
}

fun TagConsumer<*>.messageView(message: Message, isMine: Boolean, roomId: Long) {
    article { messageItem(message, isMine, roomId) }
}

fun TagConsumer<*>.messageChanged(message: Message, isMine: Boolean, roomId: Long) {
    article {
        hxSwapOob = "outerHTML"
        messageItem(message, isMine, roomId)
    }
}

fun TagConsumer<*>.messageDeleted(messageId: Long) {
    div {
        id = "message-$messageId"
        hxSwapOob = "delete"
    }
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
        article { messageItem(message, message.user.id == currentUserId, roomId) }
    }
}

private fun TagConsumer<*>.messageHistoryControl(beforeId: Long?, hasMore: Boolean, roomId: Long) {
    div {
        id = "message-history"
        if (hasMore && beforeId != null) {
            button(type = ButtonType.button) {
                hxGet = "/rooms/$roomId/messages?beforeId=$beforeId"
                hxTarget = "#message-history"
                hxSwap = "outerHTML"
                attributes["hx-disabled-elt"] = "this"
                +"加载更早的消息"
            }
        }
    }
}
