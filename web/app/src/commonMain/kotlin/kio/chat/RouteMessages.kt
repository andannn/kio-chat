package kio.chat

import io.ktor.http.*
import kio.async.AsyncSource
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
import kio.chat.util.removeFile
import kio.chat.util.respondByContentType
import kio.chat.util.transcodeAudioSourceToM4a
import kio.http.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.files.Path
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

context(repo: Repository)
fun Route.messagesRoute() {
    route("messages") {
        post { call -> call.handleNewTestMessage() }
        post("image") { call -> call.handleNewImageMessage() }
        post("voice") { call -> call.handleNewVoiceMessage() }
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
    val totalStart = TimeSource.Monotonic.markNow()
    val multipartStart = TimeSource.Monotonic.markNow()
    val reader = receiveMultipart()
    var imageBytes: AsyncSource? = null
    var width: Int? = null
    var height: Int? = null

    while (true) {
        val part = reader.nextPart() ?: break
        when (part.contentDisposition?.name) {
            "width" -> {
                width = part.body.buffered().readString().toIntOrNull()
            }

            "height" -> {
                height = part.body.buffered().readString().toIntOrNull()
            }

            "image" -> {
                imageBytes = part.body.buffered()
                break
            }

            else -> Unit
        }
    }

    val multipartMs = multipartStart.elapsedNow().inWholeMilliseconds

    if (imageBytes == null) {
        respond(HttpStatusCode.BadRequest, "缺少图片文件。")
        return
    }

    val fileName = Uuid.random().toString() + ".webp"
    val filePath = Path(Config.UPLOAD_DIR, "image", fileName).toString()

    val transcodeStart = TimeSource.Monotonic.markNow()
    val io = currentCoroutineContext().poller.io
    var savedSize = 0L
    val result = io.compressImageSourceToWebp(
        doSendInput = { sink ->
            sink.transferFrom(imageBytes)
        },
        doTransferData = { source ->
            savedSize = saveStreamToFile(source, filePath)
        }
    )
    val transcodeMs = transcodeStart.elapsedNow().inWholeMilliseconds

    if (result.isFailure) {
        runCatching { removeFile(filePath) }
        currentLogger().error(
            "failed to save file (multipartMs=$multipartMs, transcodeMs=$transcodeMs)",
            result.exceptionOrNull()
        )
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
        size = savedSize,
    )

    val dbStart = TimeSource.Monotonic.markNow()
    repo.recordMessage(
        userId = userId,
        msg = "[图片]",
        roomId = roomId,
        type = MessageType.Image,
        extra = extra,
    )
    val dbMs = dbStart.elapsedNow().inWholeMilliseconds
    val totalMs = totalStart.elapsedNow().inWholeMilliseconds

    currentLogger().info("[image] upload room=$roomId multipartMs=$multipartMs transcodeMs=$transcodeMs dbMs=$dbMs totalMs=$totalMs")

    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleNewVoiceMessage() {
    val totalStart = TimeSource.Monotonic.markNow()
    val multipartStart = TimeSource.Monotonic.markNow()
    val reader = receiveMultipart()
    var voiceBytes: AsyncSource? = null
    var durationMs: Long? = null

    while (true) {
        val part = reader.nextPart() ?: break
        when (part.contentDisposition?.name) {
            "durationMs" -> {
                durationMs = part.body.buffered().readString().toLongOrNull()
            }

            "voice" -> {
                voiceBytes = part.body.buffered()
                break
            }

            else -> Unit
        }
    }

    val multipartMs = multipartStart.elapsedNow().inWholeMilliseconds

    if (voiceBytes == null) {
        respond(HttpStatusCode.BadRequest, "缺少语音文件。")
        return
    }

    val resolvedDurationMs = durationMs ?: 0L
    if (resolvedDurationMs < 0 || resolvedDurationMs > 300_000L) {
        respond(HttpStatusCode.BadRequest, "语音时长须在 0–300 秒之间。")
        return
    }

    val fileName = Uuid.random().toString() + ".m4a"
    val filePath = Path(Config.UPLOAD_DIR, "voice", fileName).toString()

    val transcodeStart = TimeSource.Monotonic.markNow()
    val io = currentCoroutineContext().poller.io
    var savedSize = 0L
    val result = io.transcodeAudioSourceToM4a(
        doSendInput = { sink ->
            sink.transferFrom(voiceBytes)
        },
        doTransferData = { source ->
            savedSize = saveStreamToFile(source, filePath)
        }
    )
    val transcodeMs = transcodeStart.elapsedNow().inWholeMilliseconds

    if (result.isFailure || savedSize <= 0L) {
        runCatching { removeFile(filePath) }
        currentLogger().error(
            "failed to save voice file (multipartMs=$multipartMs, transcodeMs=$transcodeMs, savedSize=$savedSize)",
            result.exceptionOrNull()
        )
        respond(HttpStatusCode.InternalServerError, "语音处理失败。")
        return
    }

    val url = "/attachments/voice/$fileName"
    val roomId = requireRoomId()
    val userId = requireSession().userId
    val extra = MessageExtra.Voice(
        url = url,
        durationMs = resolvedDurationMs,
        size = savedSize,
    )

    val dbStart = TimeSource.Monotonic.markNow()
    repo.recordMessage(
        userId = userId,
        msg = "[语音]",
        roomId = roomId,
        type = MessageType.Voice,
        extra = extra,
    )
    val dbMs = dbStart.elapsedNow().inWholeMilliseconds
    val totalMs = totalStart.elapsedNow().inWholeMilliseconds

    currentLogger().info("[voice] upload room=$roomId durationMs=$resolvedDurationMs size=$savedSize multipartMs=$multipartMs transcodeMs=$transcodeMs dbMs=$dbMs totalMs=$totalMs")

    respond(HttpStatusCode.OK)
}

private suspend fun saveStreamToFile(source: AsyncSource, filePath: String): Long {
    val finalSink = fileSink(filePath).buffered()
    return try {
        val bytes = source.transferTo(finalSink)
        finalSink.flush()
        bytes
    } finally {
        finalSink.close()
    }
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
