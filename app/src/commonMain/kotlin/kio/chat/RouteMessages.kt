package kio.chat

import io.ktor.http.*
import kio.chat.components.messageAppend
import kio.chat.components.messageList
import kio.chat.domain.MessageEvent
import kio.chat.domain.Repository
import kio.http.*
import kotlinx.html.stream.createHTML

context(repo: Repository)
fun Route.messagesRoute() {
    route("messages") {
        post { call -> call.handleNewMessage() }

        websocket("/events") { socket ->
            val userId = requireSession().userId
            repo.observeMessageEvent().collect { event ->
                socket.sendText(createHTML().apply {
                    when (event) {
                        is MessageEvent.Initial -> {
                            messageList(event.message, userId)
                        }
                        is MessageEvent.Added -> {
                            messageAppend(event.message, userId == event.message.user.id)
                        }
                        is MessageEvent.Changed -> TODO()
                        is MessageEvent.Deleted -> TODO()
                    }
                }.finalize())
            }
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleNewMessage() {
    val content = receiveFormParameters()["content"]?.trim().orEmpty()
    if (content.isEmpty() || content.length > 2000) {
        respond(HttpStatusCode.BadRequest, "Message must contain 1–2000 characters.")
        return
    }
    repo.recordMessage(requireSession().userId, content)

    respond(HttpStatusCode.NoContent)
}
