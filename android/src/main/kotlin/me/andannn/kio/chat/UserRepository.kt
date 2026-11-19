package me.andannn.kio.chat

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kio.chat.domain.Room
import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.RoomMember
import kio.chat.domain.VoiceParticipantsEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription

interface UserRepository {
    fun observeRoomChangedById(roomId: Long): Flow<Room?>
    fun observeRooms(onError: suspend (ApiError) -> Unit): Flow<List<Room>>
    fun observeRoomJoinRequest(onError: suspend (ApiError) -> Unit): Flow<List<RoomJoinRequest>>
    fun observeVoiceParticipants(roomId: Long, onError: suspend (ApiError) -> Unit): Flow<List<RoomMember>>
}

class UserRepositoryImpl(
    private val eventSource: UserEventSource,
    private val service: ChatService,
): UserRepository {
    override fun observeRoomChangedById(roomId: Long): Flow<Room> = eventSource.events
        .filterIsInstance<RoomListEvent.Changed>()
        .filter { it.room.id == roomId }
        .map { it.room }

    override fun observeRooms(onError: suspend (ApiError) -> Unit): Flow<List<Room>> = flow {
        val rooms = mutableListOf<Room>()
        eventSource.events
            .onSubscription {
                val (result, error) = service.getRoomList()
                if (error != null) {
                    onError(error)
                    return@onSubscription
                }
                rooms.addAll(result!!)
                emit(rooms.toList())
            }
            .filterIsInstance<RoomListEvent>()
            .collect { event ->
                when(event) {
                    is RoomListEvent.Added -> {
                        if (rooms.none { it.id == event.room.id }) {
                            rooms.add(event.room)
                        }
                    }
                    is RoomListEvent.Deleted -> {
                        rooms.removeIf { it.id == event.roomId }
                    }

                    is RoomListEvent.Changed -> {
                        val index = rooms.indexOfFirst { it.id == event.room.id }
                        if (index != -1) rooms[index] = event.room
                    }
                }
                emit(rooms.toList())
            }
    }

    override fun observeRoomJoinRequest(onError: suspend (ApiError) -> Unit): Flow<List<RoomJoinRequest>> = flow {
        val requests = mutableListOf<RoomJoinRequest>()
        eventSource.events
            .onSubscription {
                val (result, error) = service.getRoomJoinRequests()
                if (error != null) {
                    onError(error)
                    return@onSubscription
                }
                requests.addAll(result.orEmpty().distinctBy { it.id })
                this@flow.emit(requests.toList())
            }
            .filterIsInstance<RoomJoinRequestEvent>()
            .collect { event ->
                when (event) {
                    is RoomJoinRequestEvent.Added -> {
                        val index = requests.indexOfFirst { it.id == event.request.id }
                        if (index < 0) requests.add(event.request) else requests[index] = event.request
                    }
                    is RoomJoinRequestEvent.Removed -> requests.removeAll { it.id == event.requestId }
                }
                emit(requests.toList())
            }
    }

    override fun observeVoiceParticipants(roomId: Long, onError: suspend (ApiError) -> Unit): Flow<List<RoomMember>> = flow {
        val voiceParticipants = mutableListOf<RoomMember>()
        eventSource.events
            .onSubscription {
                val (result, error) = service.getVoiceParticipants(roomId)
                if (error != null) {
                    onError(error)
                    return@onSubscription
                }
                voiceParticipants.addAll(result!!)
                emit(voiceParticipants.toList())
            }
            .filterIsInstance<VoiceParticipantsEvent>()
            .filter { it.roomId == roomId }
            .collect { event ->
                when(event) {
                    is VoiceParticipantsEvent.Added -> {
                        if (voiceParticipants.none { it.userId == event.member.userId }) {
                            voiceParticipants.add(event.member)
                        }
                    }
                    is VoiceParticipantsEvent.Removed -> {
                        voiceParticipants.removeIf { it.userId == event.userId }
                    }
                }
                emit(voiceParticipants.toList())
            }
    }
}
