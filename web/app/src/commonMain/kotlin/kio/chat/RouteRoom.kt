package kio.chat

import io.ktor.http.HttpStatusCode
import kio.chat.util.respondByContentType
import kotlinx.serialization.json.Json
import kio.chat.domain.Repository
import kio.chat.components.roomMemberList
import kio.chat.components.roomList
import kio.chat.components.chatRoomContent
import kio.http.CallContext
import kio.http.respondHtml
import kio.http.CallInterceptor
import kio.http.Route
import kio.http.respond
import kio.http.route
import kio.http.get
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
                get("members") { call -> call.handleGetRoomMembers() }
                get("content") { call -> call.handleGetRoomContent() }
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
    respondHtml { chatRoomContent(room) }
}

context(repo: Repository)
private suspend fun CallContext.handleGetRoomMembers() {
    val members = repo.getRoomMembers(requireRoomId())
    respondByContentType(
        status = HttpStatusCode.OK,
        buildJson = { Json.encodeToString(members) },
        buildHtml = { roomMemberList(members) },
    )
}
