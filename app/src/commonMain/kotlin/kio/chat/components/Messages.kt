package kio.chat.components

import kio.chat.domain.Message
import kio.chat.util.*
import kotlinx.html.*

fun TagConsumer<*>.messageList(messages: List<Message>, currentUserId: Long) {
    div {
        id = "messages"
        hxSwapOob = "innerHTML"
        messages.forEach { message ->
            article { messageItem(message, message.user.id == currentUserId) }
        }
    }
}

fun TagConsumer<*>.messageAppend(message: Message, isMine: Boolean) {
    div {
        id = "messages"
        hxSwapOob = "beforeend"
        article { messageItem(message, isMine) }
    }
}

private fun ARTICLE.messageItem(message: Message, isMine: Boolean) {
    id = "message-${message.id}"
    attributes["data-mine"] = isMine.toString()
    p {
        strong { +"${message.username}: " }
        +message.text
    }
    if (isMine) {
        details(classes = "menu message-menu") {
            summary(classes = "menu-trigger") {
                attributes["aria-label"] = "Message options"
                +"⋯"
            }
            div(classes = "menu-popup") {
                button(type = ButtonType.button, classes = "menu-item") {
                    hxGet = "/messages/${message.id}/editor"
                    hxTarget = "closest article"
                    hxSwap = "outerHTML"
                    +"Edit"
                }
                button(type = ButtonType.button, classes = "menu-item") {
                    hxDelete = "/messages/${message.id}"
                    hxSwap = "none"
                    hxConfirm = "Delete this message?"
                    +"Delete"
                }
            }
        }
    }
}

fun TagConsumer<*>.messageView(message: Message, isMine: Boolean) {
    article { messageItem(message, isMine) }
}

fun TagConsumer<*>.messageChanged(message: Message, isMine: Boolean) {
    article {
        hxSwapOob = "outerHTML"
        messageItem(message, isMine)
    }
}

fun TagConsumer<*>.messageDeleted(messageId: Long) {
    div {
        id = "message-$messageId"
        hxSwapOob = "delete"
    }
}

fun TagConsumer<*>.messageEditor(message: Message) {
    article(classes = "message-editing") {
        id = "message-${message.id}"
        attributes["data-mine"] = "true"
        form(classes = "message-edit-form") {
            hxPatch = "/messages/${message.id}"
            hxSwap = "none"
            attributes["hx-disabled-elt"] = "find button"
            textArea {
                name = "content"
                required = true
                autoFocus = true
                rows = "3"
                attributes["maxlength"] = "2000"
                attributes["aria-label"] = "Edit message"
                +message.text
            }
            div(classes = "message-edit-actions") {
                button(type = ButtonType.button) {
                    hxGet = "/messages/${message.id}"
                    hxTarget = "closest article"
                    hxSwap = "outerHTML"
                    +"Cancel"
                }
                button(type = ButtonType.submit) { +"Save" }
            }
        }
    }
}
