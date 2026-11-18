package kio.chat

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kio.async.readString
import kio.chat.util.respondByContentType
import kotlinx.serialization.json.Json
import kio.chat.domain.RoomMembersPage
import kio.chat.domain.Repository
import kio.chat.domain.VoiceCallMessage
import kio.chat.domain.VoiceCallRequest
import kio.chat.components.roomMemberList
import kio.chat.components.voiceParticipantList
import kio.chat.components.roomList
import kio.chat.components.chatRoomContent
import kio.chat.components.roomNameEditor
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
                get("voice/members") { call -> call.handleGetVoiceParticipants() }
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
    val request = Json.decodeFromString<VoiceCallRequest>(body)
    val message = request.message

    val roomId = requireRoomId()
    val userId = requireSession().userId
    if (message is VoiceCallMessage.PeerSignal && !repo.isRoomMember(roomId, message.recipientUserId)) {
        respond(HttpStatusCode.Forbidden, "目标用户不在该聊天室。")
        return
    }

    if (!repo.publishVoiceMessage(roomId, userId, request.wsSessionId, message)) {
        respond(HttpStatusCode.Conflict, "语音连接不可用，或此账号已在其他页面通话。")
        return
    }

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
private suspend fun CallContext.handleGetVoiceParticipants() {
    val roomId = requireRoomId()
    val participantIds = repo.getVoiceParticipantIds(roomId)
    val members = repo.getRoomMembers(roomId).filter { it.userId in participantIds }
    respondByContentType(
        status = HttpStatusCode.OK,
        configHeaders = { append(HttpHeaders.CacheControl, "no-store") },
        buildJson = { Json.encodeToString(members) },
        buildHtml = { voiceParticipantList(roomId, members) },
    )
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
