package kio.chat

import io.ktor.http.HttpStatusCode
import kio.chat.domain.Repository
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
        route("{roomId}") {
            inject(RoomId()) {
                get { call -> call.chatMainPage(requireRoomId()) }
                messagesRoute()
            }
        }
    }
}

suspend fun requireRoomId(): Long {
    return currentCoroutineContext()[CoroutineRoomId]?.roomId ?: error("no room id")
}

private fun RoomId() = CallInterceptor { call, proceed ->
    val noteRoomId = call.requestParameters["roomId"]?.toLongOrNull()
    if (noteRoomId == null) {
        call.respond(
            HttpStatusCode.BadRequest,
            message = "request roomId parameter but get ${call.requestParameters["roomId"]}"
        )
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
