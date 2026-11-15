package kio.chat

import io.ktor.http.HttpStatusCode
import kio.chat.util.respondByContentType
import kotlinx.serialization.json.Json
import kio.chat.domain.RoomMembersPage
import kio.chat.domain.Repository
import kio.chat.components.roomMemberList
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
import kio.chat.page.chatMainPage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

context(repo: Repository)
fun Route.roomRoute() {
    route("rooms") {
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
                messagesRoute()
            }
        }
    }
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
