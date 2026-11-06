package kio.chat

import io.ktor.http.HttpStatusCode
import kio.chat.components.messageAppend
import kio.chat.components.messageList
import kio.chat.domain.Repository
import kio.chat.domain.MessageEvent
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kio.http.*
import kotlinx.html.stream.createHTML

context(repo: Repository)
fun Route.messagesRoute() {
    route("messages") {
        get { call -> call.handleGetMessageList() }
        post { call -> call.handleNewMessage() }

        websocket("/events") { socket ->
            val userId = requireSession().userId
            repo.observeMessageEvent()
                .filterIsInstance<MessageEvent.Added>()
                .map { it.message }
                .collect { message ->
                    socket.sendText(createHTML().apply { messageAppend(message, userId == message.userId) }.finalize())
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

context(repo: Repository)
private suspend fun CallContext.handleGetMessageList() {
    val userId = requireSession().userId
    val messages = repo.getMessageList()
    respondHtml { messageList(messages, userId) }
}
