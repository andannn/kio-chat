package kio.chat

import io.ktor.http.HttpStatusCode
import kio.async.readString
import kio.chat.util.respondByContentType
import kotlinx.serialization.json.Json
import kio.chat.domain.RoomMembersPage
import kio.chat.domain.Repository
import kio.chat.domain.VoiceCallMessage
import kio.chat.components.roomMemberList
import kio.chat.components.roomList
import kio.chat.components.chatRoomContent
import kio.chat.components.roomNameEditor
import kio.http.currentLogger
import kio.http.info
import kio.http.CallContext
import kio.http.respondHtml
import kio.http.CallInterceptor
import kio.http.Route
import kio.http.respond
import kio.http.route
import kio.http.get
import kio.http.post
import kio.http.delete
import kio.http.receiveFormParameters
import kotlinx.html.div
import kio.chat.page.chatMainPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

context(repo: Repository)
fun Route.roomRoute() {
    route("rooms") {
        post { call -> call.handleCreateRoom() }
        get { call -> call.handleGetRoomList() }
        roomJoinRequestsRoute()
        route("{roomId}") {
            inject(RoomId()) {
                get { call -> call.chatMainPage(requireRoomId()) }
                delete { call -> call.handleDeleteRoom() }
                get("members") { call -> call.handleGetRoomMembers() }
                get("content") { call -> call.handleGetRoomContent() }
                post("name-editing") { call -> call.handleEditRoomName() }
                post("name-commit") { call -> call.handleCommitRoomName() }
                post("events") { call -> call.handleRoomEvent() }
                messagesRoute()
            }
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleRoomEvent() {
    val body = requestBody.readString()
    val message = Json.decodeFromString<VoiceCallMessage>(body)

    val roomId = requireRoomId()
    val userId = requireSession().userId
    val eventType = when (message) {
        VoiceCallMessage.JoinCall -> "join"
        VoiceCallMessage.LeaveCall -> "leave"
        is VoiceCallMessage.Offer -> "offer"
        is VoiceCallMessage.Answer -> "answer"
        is VoiceCallMessage.IceCandidate -> "candidate"
    }
    val target = (message as? VoiceCallMessage.PeerSignal)?.recipientUserId
    currentLogger().info("[voice] HTTP received: room=$roomId from=$userId to=$target type=$eventType")
    if (message is VoiceCallMessage.PeerSignal && !repo.isRoomMember(roomId, message.recipientUserId)) {
        currentLogger().info("[voice] HTTP rejected: room=$roomId from=$userId to=$target target is not a room member")
        respond(HttpStatusCode.Forbidden, "目标用户不在该聊天室。")
        return
    }

    val connectionId = requestHeaders["X-Voice-Connection"]
    if (connectionId == null || !repo.publishVoiceMessage(roomId, userId, connectionId, message)) {
        respond(HttpStatusCode.Conflict, "语音连接不可用，或此账号已在其他页面通话。")
        return
    }

    currentLogger().info("[voice] HTTP published: room=$roomId from=$userId to=$target type=$eventType")
    respond(HttpStatusCode.NoContent)
}

suspend fun requireRoomId(): Long {
    return currentCoroutineContext()[CoroutineRoomId]?.roomId ?: error("no room id")
}

context(repo: Repository)
private fun RoomId() = CallInterceptor { call, proceed ->
    val noteRoomId = call.requestParameters["roomId"]?.toLongOrNull()
    if (noteRoomId == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            message = "request roomId parameter but get ${call.requestParameters["roomId"]}"
        )
    } else if (!repo.isRoomMember(noteRoomId, requireSession().userId)) {
        call.respond(HttpStatusCode.Forbidden, "无权访问该聊天室。")
    } else {
        withContext(CoroutineRoomId(noteRoomId)) {
            proceed(call)
        }
    }
}

private data class CoroutineRoomId(
    val roomId: Long
) : AbstractCoroutineContextElement(CoroutineRoomId) {
    companion object Key : CoroutineContext.Key<CoroutineRoomId>

    override fun toString(): String = "CoroutineRoomId(${roomId})"
}

context(repo: Repository)
private suspend fun CallContext.handleCreateRoom() {
    val name = receiveFormParameters()["name"]?.trim().orEmpty()
    if (name.isEmpty()) {
        val message = "给聊天室起个名字吧。"
        respondByContentType(
            status = HttpStatusCode.BadRequest,
            buildJson = { Json.encodeToString(message) },
            buildHtml = { div { +message } },
        )
        return
    }
    val room = repo.createRoom(requireSession().userId, name)
    respondByContentType(
        status = HttpStatusCode.Created,
        configHeaders = { append("HX-Redirect", "/rooms/${room.id}") },
        buildJson = { Json.encodeToString(room) },
        buildHtml = {},
    )
}

context(repo: Repository)
private suspend fun CallContext.handleGetRoomList() {
    val rooms = repo.getRoomList(requireSession().userId)
    val selectedRoomId = requestParameters["selectedRoomId"]?.toLongOrNull()
    respondByContentType(
        status = HttpStatusCode.OK,
        buildJson = { Json.encodeToString(rooms) },
        buildHtml = { roomList(rooms, selectedRoomId) },
    )
}

context(repo: Repository)
private suspend fun CallContext.handleGetRoomContent() {
    val room = repo.getRoomById(requireRoomId())
    if (room == null) {
        respond(HttpStatusCode.NotFound, "聊天室不存在。")
        return
    }
    val isOwner = repo.isRoomOwner(room.id, requireSession().userId)
    respondHtml { chatRoomContent(room, isOwner) }
}

context(repo: Repository)
private suspend fun CallContext.handleGetRoomMembers() {
    val members = repo.getRoomMembers(requireRoomId())
    val page = RoomMembersPage(requireSession().userId, members)
    respondByContentType(
        status = HttpStatusCode.OK,
        buildJson = { Json.encodeToString(page) },
        buildHtml = { roomMemberList(members) },
    )
}

context(repo: Repository)
private suspend fun CallContext.handleEditRoomName() {
    val roomId = requireRoomId()
    if (!repo.isRoomOwner(roomId, requireSession().userId)) {
        respond(HttpStatusCode.Forbidden, "只有群主可以修改聊天室名称。")
        return
    }
    val room = repo.getRoomById(roomId)
    if (room == null) {
        respond(HttpStatusCode.NotFound, "聊天室不存在。")
        return
    }
    respondHtml { roomNameEditor(room) }
}

context(repo: Repository)
private suspend fun CallContext.handleCommitRoomName() {
    val name = receiveFormParameters()["name"]?.trim().orEmpty()
    if (name.isEmpty()) {
        respond(HttpStatusCode.BadRequest, "给聊天室起个名字吧。")
        return
    }
    if (!repo.updateRoomName(requireRoomId(), requireSession().userId, name)) {
        respond(HttpStatusCode.Forbidden, "聊天室不存在，或你不是群主。")
        return
    }
    respond(HttpStatusCode.OK)
}

context(repo: Repository)
private suspend fun CallContext.handleDeleteRoom() {
    if (!repo.deleteRoom(requireRoomId(), requireSession().userId)) {
        respond(HttpStatusCode.Forbidden, "聊天室不存在，或你不是群主。")
        return
    }
    respond(HttpStatusCode.OK, configHeaders = { append("HX-Redirect", "/") })
}
