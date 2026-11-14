package kio.chat

import kio.chat.components.*
import kio.chat.domain.Repository
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.UserEvent
import kio.http.WebsocketContext
import kio.http.sendText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json

context(repo: Repository)
internal suspend fun WebsocketContext.handleObserveUserEvent() = coroutineScope {
    val userId = requireSession().userId
    val isJson = call.requestParameters["format"] == "json"
    val selectedRoomId = call.requestParameters["selectedRoomId"]?.toLongOrNull()

    launch {
        repo.observeRoomJoinRequestEvent(userId).collect { event ->
            if (isJson) {
                sendText(Json.encodeToString<UserEvent>(event))
            } else {
                sendText(createHTML().apply {
                    when (event) {
                        is RoomJoinRequestEvent.Initial -> roomJoinRequestInitial(event.requests)
                        is RoomJoinRequestEvent.Added -> roomJoinRequestAdded(event.request)
                        is RoomJoinRequestEvent.Removed -> roomJoinRequestRemoved(event.requestId)
                    }
                }.finalize())
            }
        }
    }
    launch {
        repo.observeRoomListEvent(userId).collect { event ->
            if (isJson) {
                sendText(Json.encodeToString<UserEvent>(event))
            } else {
                sendText(createHTML().apply {
                    when (event) {
                        is RoomListEvent.Initial -> roomListInitial(event.rooms, selectedRoomId)
                        is RoomListEvent.Added -> roomListAdded(event.room)
                        is RoomListEvent.Deleted -> roomListDeleted(event.roomId)
                    }
                }.finalize())
            }
        }
    }
}
