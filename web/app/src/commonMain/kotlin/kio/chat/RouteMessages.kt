package kio.chat

import io.ktor.http.*
import kio.chat.components.*
import kio.chat.domain.MessagePage
import kio.chat.util.respondByContentType
import kio.chat.domain.Repository
import kio.http.*
import kotlinx.serialization.json.Json

context(repo: Repository)
fun Route.messagesRoute() {
    route("messages") {
        post { call -> call.handleNewMessage() }
        get { call -> call.handleGetMessageHistory() }

        route("/{id}") {
            get { call -> call.handleGetMessage() }
            patch { call -> call.handleUpdateMessage() }
            delete { call -> call.handleDeleteMessage() }
            get("/editor") { call -> call.handleGetEditingMessage() }
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetMessageHistory() {
    val userId = requireSession().userId
    val beforeId = requestParameters["beforeId"]?.toLongOrNull()?.takeIf { it > 0 }
    if (beforeId == null) {
        respond(HttpStatusCode.BadRequest, "无效的消息编号。")
        return
    }
    val roomId = requireRoomId()
    val messages = repo.getMessageList(beforeId, limit = 51, roomId)
    val page = MessagePage(messages.takeLast(50), hasMore = messages.size > 50, userId = userId)
    respondByContentType(
        status = HttpStatusCode.OK,
        buildJson = { Json.encodeToString(page) },
        buildHtml = { messageHistory(page.messages, userId, page.hasMore, roomId) },
    )
}

context(repo: Repository)
private suspend fun CallContext.handleNewMessage() {
    val content = receiveFormParameters()["content"]?.trim().orEmpty()
    if (content.isEmpty() || content.length > 2000) {
        respond(HttpStatusCode.BadRequest, "消息内容须为 1–2000 个字符。")
        return
    }
    val roomId = requireRoomId()
    repo.recordMessage(requireSession().userId, content, roomId)

    respond(HttpStatusCode.OK)
}

private suspend fun CallContext.messageIdOrRespond(): Long? {
    val id = requestParameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
    if (id == null) respond(HttpStatusCode.BadRequest, "无效的消息编号。")
    return id
}

context(repo: Repository)
private suspend fun CallContext.handleGetMessage() {
    val id = messageIdOrRespond() ?: return
    val roomId = requireRoomId()
    val message = repo.getOwnMessage(requireSession().userId, id, roomId)
    if (message == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respondHtml {
        messageView(message, isMine = true, roomId = roomId)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetEditingMessage() {
    val id = messageIdOrRespond() ?: return
    val roomId = requireRoomId()
    val message = repo.getOwnMessage(requireSession().userId, id, roomId)
    if (message == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respondHtml {
        messageEditor(message, roomId)
    }
}

context(repo: Repository)
private suspend fun CallContext.handleUpdateMessage() {
    val id = messageIdOrRespond() ?: return
    val content = receiveFormParameters()["content"]?.trim().orEmpty()
    if (content.isEmpty() || content.length > 2000) {
        respond(HttpStatusCode.BadRequest, "消息内容须为 1–2000 个字符。")
        return
    }
    val roomId = requireRoomId()
    if (!repo.updateMessage(requireSession().userId, id, content, roomId)) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleDeleteMessage() {
    val id = messageIdOrRespond() ?: return
    val roomId = requireRoomId()
    if (!repo.deleteMessage(requireSession().userId, id, roomId)) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.OK)
}
