package me.andannn.kio.chat

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kio.chat.domain.Room
import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.WebSocketSessionReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val LocalUserRepository: ProvidableCompositionLocal<UserRepository> =
    compositionLocalOf { error("no Local UserRepository") }

interface UserRepository {
    fun CoroutineScope.startObservingEvents(onError: (ApiError) -> Unit): Job
    fun observeRoomById(roomId: Long): Flow<Room?>
    fun observeRooms(): Flow<List<Room>>
    fun observeRoomJoinRequest(): Flow<List<RoomJoinRequest>>
}

class UserRepositoryImpl(
    private val eventSource: UserEventSource,
    private val service: ChatService,
): UserRepository {
    private val rooms = MutableStateFlow<List<Room>>(emptyList())
    private val requests = MutableStateFlow<List<RoomJoinRequest>>(emptyList())

    override fun CoroutineScope.startObservingEvents(onError: (ApiError) -> Unit): Job = launch(start = CoroutineStart.UNDISPATCHED) {
        eventSource.events.collect { event ->
            when (event) {
                is WebSocketSessionReady -> {
                    // The existing subscription buffers incremental events while HTTP loads the snapshots.
                    val (initialRooms, roomError) = service.getRoomList()
                    if (initialRooms != null) rooms.value = initialRooms.distinctBy { it.id }
                    if (roomError != null) onError(roomError)

                    val (initialRequests, requestError) = service.getRoomJoinRequests()
                    if (initialRequests != null) requests.value = initialRequests.distinctBy { it.id }
                    if (requestError != null) onError(requestError)
                }
                is RoomListEvent -> handleRoomListEvent(event)
                is RoomJoinRequestEvent -> handleRoomJoinRequestEvent(event)
                else -> Unit
            }
        }
    }

    override fun observeRoomById(roomId: Long): Flow<Room?> {
        return rooms.map { list ->
            list.firstOrNull { it.id == roomId }
        }
    }

    override fun observeRooms(): Flow<List<Room>> = rooms

    override fun observeRoomJoinRequest(): Flow<List<RoomJoinRequest>> = requests

    private fun handleRoomListEvent(event: RoomListEvent) {
        when (event) {
            is RoomListEvent.Added -> rooms.value += event.room
            is RoomListEvent.Deleted -> {
                rooms.value = rooms.value.filterNot { it.id == event.roomId }
                requests.value = requests.value.filterNot { it.roomId == event.roomId }
            }
            is RoomListEvent.Changed -> {
                rooms.value = rooms.value.map {
                    if (it.id == event.room.id) {
                        event.room
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun handleRoomJoinRequestEvent(event: RoomJoinRequestEvent) {
        when (event) {
            is RoomJoinRequestEvent.Added -> requests.value += event.request
            is RoomJoinRequestEvent.Removed -> {
                requests.value = requests.value.filterNot { it.id == event.requestId }
            }
        }
    }
}