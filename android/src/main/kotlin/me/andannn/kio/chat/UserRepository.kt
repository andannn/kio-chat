package me.andannn.kio.chat

import android.util.Log
import kio.chat.domain.Message
import kio.chat.domain.Room
import kio.chat.domain.VoiceSignalingEvent
import kio.chat.domain.VoiceParticipantsEvent
import kio.chat.domain.WebSocketSessionReady
import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.UserChatEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter

private const val TAG = "UserRepository"

interface UserRepository {
    suspend fun observingUserEvent(): ApiError?

    fun observeRoomById(roomId: Long): Flow<Room?>
    fun observeRooms(): Flow<List<Room>>
    fun observeRoomJoinRequest(): Flow<List<RoomJoinRequest>>
    fun observeLastMessage(roomId: Long): Flow<Message?>
    fun observeChatMessageEvent(roomId: Long): Flow<UserChatEvent>
}

class UserRepositoryImpl(
    private val service: ChatService
): UserRepository {
    private val rooms = MutableStateFlow<List<Room>>(emptyList())
    private val requests = MutableStateFlow<List<RoomJoinRequest>>(emptyList())
    private val lastMessages = MutableStateFlow<Map<Long, Message?>>(emptyMap())
    private val chatEventFlow = MutableSharedFlow<UserChatEvent>()

    override suspend fun observingUserEvent(): ApiError? {
        return service.observeUserEvents { event ->
            Log.d(TAG, "observeUserEvents: ${event.javaClass} $event")
            when (event) {
                is RoomListEvent -> handleRoomListEvent(event)
                is RoomJoinRequestEvent -> handleRoomJoinRequestEvent(event)
                is VoiceSignalingEvent -> Unit
                is VoiceParticipantsEvent -> Unit
                is WebSocketSessionReady -> Unit
                is UserChatEvent -> {
                    handleChatMessageEvent(event)
                    chatEventFlow.emit(event)
                }
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

    override fun observeLastMessage(roomId: Long): Flow<Message?> =
        lastMessages.map { it[roomId] }.distinctUntilChanged()

    override fun observeChatMessageEvent(roomId: Long): Flow<UserChatEvent> =
        chatEventFlow.filter { it.roomId == roomId }

    private fun handleChatMessageEvent(event: UserChatEvent) {
        val current = lastMessages.value[event.roomId]
        when (event) {
            is UserChatEvent.Initial -> {
                lastMessages.value += event.roomId to event.message.maxByOrNull { it.id }
            }
            is UserChatEvent.Added -> {
                if (current == null || event.message.id >= current.id) {
                    lastMessages.value += event.roomId to event.message
                }
            }
            is UserChatEvent.Changed -> {
                if (current?.id == event.message.id) lastMessages.value += event.roomId to event.message
            }
            is UserChatEvent.Deleted -> {
                if (current?.id == event.messageId) lastMessages.value += event.roomId to null
            }
        }
    }

    private fun handleRoomListEvent(event: RoomListEvent) {
        when (event) {
            is RoomListEvent.Initial -> {
                val roomIds = event.rooms.map { it.id }.toSet()
                lastMessages.value = lastMessages.value.filterKeys { it in roomIds }
                rooms.value = event.rooms
            }
            is RoomListEvent.Added -> rooms.value += event.room
            is RoomListEvent.Deleted -> {
                lastMessages.value -= event.roomId
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