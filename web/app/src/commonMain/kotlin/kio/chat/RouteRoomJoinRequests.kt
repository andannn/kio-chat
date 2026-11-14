package kio.chat

import io.ktor.http.HttpStatusCode
import kio.chat.components.roomJoinRequestInitial
import kio.chat.components.roomJoinRequestAdded
import kio.chat.components.roomJoinRequestRemoved
import kio.chat.domain.RoomJoinRequestEvent
import kotlinx.html.stream.createHTML
import kio.chat.components.roomJoinRequestList
import kio.chat.database.CreateRoomJoinRequestError
import kio.chat.domain.Repository
import kio.chat.util.respondByContentType
import kio.http.*
import kotlinx.html.div
import kotlinx.serialization.json.Json

context(repo: Repository)
fun Route.roomJoinRequestsRoute() {
    route("join-requests") {
        post { call -> call.handleCreateJoinRequest() }
        get { call -> call.handleGetJoinRequests() }
        websocket("events") { socket -> socket.handleObserveJoinRequests() }
        route("{requestId}") {
            post("approve") { call -> call.handleReviewJoinRequest(approve = true) }
            delete { call -> call.handleReviewJoinRequest(approve = false) }
        }
    }
}

context(repo: Repository)
private suspend fun CallContext.handleCreateJoinRequest() {
    val roomId = receiveFormParameters()["roomId"]?.toLongOrNull()?.takeIf { it > 0 }
    if (roomId == null) {
        respondJoinResult(HttpStatusCode.BadRequest, "请输入有效的房间 ID。")
        return
    }
    val (_, error) = repo.createRoomJoinRequest(roomId, requireSession().userId)
    when (error) {
        CreateRoomJoinRequestError.RoomNotExist -> respondJoinResult(HttpStatusCode.NotFound, "聊天室不存在。")
        CreateRoomJoinRequestError.MemberAlreadyInRoom -> respondJoinResult(HttpStatusCode.BadRequest, "你已加入该聊天室。")
        CreateRoomJoinRequestError.RequestAlreadyExist -> respondJoinResult(HttpStatusCode.Conflict, "申请已经发送，请等群主处理。")
        null -> respondJoinResult(HttpStatusCode.OK, "申请已发送，等群主同意就能加入啦。")
    }
}

context(repo: Repository)
private suspend fun CallContext.handleGetJoinRequests() {
    val requests = repo.getRoomJoinRequests(requireSession().userId)
    respondByContentType(
        status = HttpStatusCode.OK,
        buildJson = { Json.encodeToString(requests) },
        buildHtml = { roomJoinRequestList(requests) },
    )
}

context(repo: Repository)
private suspend fun CallContext.handleReviewJoinRequest(approve: Boolean) {
    val requestId = requestParameters["requestId"]?.toLongOrNull()?.takeIf { it > 0 }
    if (requestId == null) {
        respondJoinResult(HttpStatusCode.BadRequest, "无效的申请 ID。")
        return
    }
    val userId = requireSession().userId
    val handled = if (approve) repo.approveRoomJoinRequest(requestId, userId)
        else repo.rejectRoomJoinRequest(requestId, userId)
    if (!handled) {
        respondJoinResult(HttpStatusCode.NotFound, "这条申请已处理，或你不是该房间的群主。")
        return
    }
    respondByContentType(
        status = HttpStatusCode.OK,
        buildJson = { "{}" },
        buildHtml = {},
    )
}

private suspend fun CallContext.respondJoinResult(status: HttpStatusCode, message: String) {
    respondByContentType(
        status = status,
        buildJson = { Json.encodeToString(message) },
        buildHtml = { div { +message } },
    )
}

context(repo: Repository)
private suspend fun WebsocketContext.handleObserveJoinRequests() {
    val userId = requireSession().userId
    val isJson = call.requestParameters["format"] == "json"
    repo.observeRoomJoinRequestEvent(userId).collect { event ->
        if (isJson) {
            sendText(Json.encodeToString(event))
            return@collect
        }
        sendText(createHTML().apply {
            when (event) {
                is RoomJoinRequestEvent.Initial -> roomJoinRequestInitial(event.requests)
                is RoomJoinRequestEvent.Added -> roomJoinRequestAdded(event.request)
                is RoomJoinRequestEvent.Removed -> roomJoinRequestRemoved(event.requestId)
            }
        }.finalize())
    }
}
