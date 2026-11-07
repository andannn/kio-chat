package kio.chat

import io.ktor.http.*
import kio.chat.components.*
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
                        is MessageEvent.Changed -> messageChanged(event.message, userId == event.message.user.id)
                        is MessageEvent.Deleted -> messageDeleted(event.messageId)
                    }
                }.finalize())
            }
        }

        route("/{id}") {
            get { call -> call.handleOwnMessage(editing = false) }
            get("/editor") { call -> call.handleOwnMessage(editing = true) }
            patch { call -> call.handleUpdateMessage() }
            delete { call -> call.handleDeleteMessage() }
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

private suspend fun CallContext.messageIdOrRespond(): Long? {
    val id = requestParameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
    if (id == null) respond(HttpStatusCode.BadRequest, "Invalid message ID.")
    return id
}

context(repo: Repository)
private suspend fun CallContext.handleOwnMessage(editing: Boolean) {
    val id = messageIdOrRespond() ?: return
    val message = repo.getOwnMessage(requireSession().userId, id)
    if (message == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respondHtml {
        if (editing) messageEditor(message) else messageView(message, isMine = true)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleUpdateMessage() {
    val id = messageIdOrRespond() ?: return
    val content = receiveFormParameters()["content"]?.trim().orEmpty()
    if (content.isEmpty() || content.length > 2000) {
        respond(HttpStatusCode.BadRequest, "Message must contain 1–2000 characters.")
        return
    }
    if (!repo.updateMessage(requireSession().userId, id, content)) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}

context(repo: Repository)
private suspend fun CallContext.handleDeleteMessage() {
    val id = messageIdOrRespond() ?: return
    if (!repo.deleteMessage(requireSession().userId, id)) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}
