package kio.chat.components

import kio.chat.domain.Message
import kio.chat.util.hxSwapOob
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
    attributes["data-mine"] = isMine.toString()
    p {
        strong { +"${message.username}: " }
        +message.text
    }
}
