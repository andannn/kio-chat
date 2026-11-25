package kio.chat

import io.ktor.http.*
import kio.async.AsyncRawSource
import kio.async.buffered
import kio.async.poller
import kio.async.readString
import kio.chat.components.*
import kio.chat.domain.MessageExtra
import kio.chat.domain.MessagePage
import kio.chat.domain.MessageType
import kio.chat.domain.Repository
import kio.chat.util.Config
import kio.chat.util.compressImageSourceToWebp
import kio.chat.util.fileSink
import kio.chat.util.json
import kio.chat.util.respondByContentType
import kio.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.uuid.Uuid

context(repo: Repository)
fun Route.messagesRoute() {
    route("messages") {
        post { call -> call.handleNewTestMessage() }
        post("image") { call -> call.handleNewImageMessage() }
        get { call -> call.handleGetMessageHistory() }

        route("/{id}") {
            get { call -> call.handleGetMessage() }
            patch { call -> call.handleUpdateMessage() }
            delete { call -> call.handleDeleteMessage() }
            get("editor") { call -> call.handleGetEditingMessage() }
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
        buildJson = { json(page) },
        buildHtml = { messageHistory(page.messages, userId, page.hasMore, roomId) },
    )
}

context(repo: Repository)
private suspend fun CallContext.handleNewTestMessage() {
    val content = receiveFormParameters()["content"]?.trim().orEmpty()
    if (content.isEmpty() || content.length > 2000) {
        respond(HttpStatusCode.BadRequest, "消息内容须为 1–2000 个字符。")
        return
    }
    val roomId = requireRoomId()
    repo.recordMessage(requireSession().userId, content, roomId)

    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleNewImageMessage() {
    val reader = receiveMultipart()
    var imageFileSource: AsyncRawSource? = null
    var width: Int? = null
    var height: Int? = null

    while (true) {
        val part = reader.nextPart() ?: break
        when (part.contentDisposition?.name) {
            "image" -> {
                imageFileSource = part.body
                break
            }
            "width" -> {
                width = part.body.buffered().readString().toIntOrNull()
            }
            "height" -> {
                height = part.body.buffered().readString().toIntOrNull()
            }
        }
    }

    if (imageFileSource == null) {
        respond(HttpStatusCode.BadRequest, "缺少图片文件。")
        return
    }

    val fileName = Uuid.random().toString() + ".webp"
    val filePath = Path(Config.UPLOAD_DIR, "image", fileName).toString()

    val io = currentCoroutineContext().poller.io
    val result = io.compressImageSourceToWebp(
        doSendInput = { sink ->
            sink.transferFrom(imageFileSource)
        },
        doTransferData = { source ->
            val finalSink = fileSink(filePath).buffered()
            try {
                source.transferTo(finalSink)
                finalSink.flush()
            } finally {
                finalSink.close()
            }
        }
    )

    if (result.isFailure) {
        currentLogger().error("failed to save file", result.exceptionOrNull())
        respond(HttpStatusCode.InternalServerError, "图片处理失败。")
        return
    }

    val url = "/attachments/image/$fileName"
    val roomId = requireRoomId()
    val userId = requireSession().userId
    val extra = MessageExtra.Image(
        url = url,
        width = width ?: 0,
        height = height ?: 0,
    )

    repo.recordMessage(
        userId = userId,
        msg = "[图片]",
        roomId = roomId,
        type = MessageType.Image,
        extra = extra,
    )

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
    respondHtml { messageView(message, isMine = true, roomId = roomId) }
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
