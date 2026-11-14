package me.andannn.kio.chat

import android.util.Log
import kio.chat.domain.Room
import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

private const val TAG = "UserRepository"

interface UserRepository {
    suspend fun observingUserEvent(): ApiError?

    fun observeRoomById(roomId: Long): Flow<Room>
    fun observeRooms(): Flow<List<Room>>
    fun observeRoomJoinRequest(): Flow<List<RoomJoinRequest>>
}

class UserRepositoryImpl(
    private val service: ChatService
): UserRepository {
    private val rooms = MutableStateFlow<List<Room>>(emptyList())
    private val requests = MutableStateFlow<List<RoomJoinRequest>>(emptyList())

    override suspend fun observingUserEvent(): ApiError? {
        return service.observeUserEvents { event ->
            Log.d(TAG, "observeUserEvents: ${event.javaClass} $event")
            when (event) {
                is RoomListEvent -> handleRoomListEvent(event)
                is RoomJoinRequestEvent -> handleRoomJoinRequestEvent(event)
            }
        }
    }

    override fun observeRoomById(roomId: Long): Flow<Room> {
        return rooms.mapNotNull { list ->
            list.firstOrNull { it.id == roomId }
        }
    }

    override fun observeRooms(): Flow<List<Room>> = rooms

    override fun observeRoomJoinRequest(): Flow<List<RoomJoinRequest>> = requests

    private fun handleRoomListEvent(event: RoomListEvent) {
        when (event) {
            is RoomListEvent.Initial -> {
                rooms.value = event.rooms
            }
            is RoomListEvent.Added -> rooms.value += event.room
            is RoomListEvent.Deleted -> rooms.value = rooms.value.filterNot { it.id == event.roomId }
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
            is RoomJoinRequestEvent.Initial -> {
                requests.value = event.requests
            }
            is RoomJoinRequestEvent.Added -> {
                requests.value += event.request
            }
            is RoomJoinRequestEvent.Removed -> {
                requests.value = requests.value.filterNot { it.id == event.requestId }
            }
        }
    }
}