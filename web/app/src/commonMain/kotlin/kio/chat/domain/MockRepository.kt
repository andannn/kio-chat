package kio.chat.domain

import kio.chat.database.CreateRoomJoinRequestError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class MockRepositoryImpl : Repository {
    private val users = mutableMapOf<String, User>()
    private val sessions = mutableMapOf<String, Session>()
    private var nextUserId = 1L

    private val rooms = mutableListOf(Room(1, "demo的房间"))
    private val roomMembers = mutableMapOf((1L to 1L) to RoomRole.Owner)
    private val messageEventFlow = MutableSharedFlow<MockMessageEvent>(extraBufferCapacity = 64)
    private val joinRequests = mutableListOf<RoomJoinRequest>()
    private val joinRequestFlow = MutableSharedFlow<Pair<Long, RoomJoinRequestEvent>>(extraBufferCapacity = 64)
    private var nextJoinRequestId = 1L
    private var nextRoomId = 2L
    private val messageRooms = mutableMapOf<Long, Long>()
    private val messages = mutableListOf<Message>()
    private var nextMessageId = 1L

    init {
        val user = User(nextUserId++, "demo", "demo")
        users[user.username] = user
        repeat(125) {
            messageRooms[nextMessageId] = 1
            messages.add(Message(nextMessageId++, user, "Mock message ${it + 1}", user.username))
        }
    }

    override fun CoroutineScope.startWatchingMessageEvent(): Job = launch {}

    override suspend fun findUserByUsername(userName: String): User? {
        return users[userName]
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean =
        user.passwordHash == password

    override suspend fun createUser(userName: String, password: String): User? {
        if (users.containsKey(userName)) return null
        return User(nextUserId++, userName, password).also {
            users[userName] = it
            createRoom(it.id, "${it.username}的房间")
        }
    }

    override suspend fun createSession(userId: Long): String {
        require(users.values.any { it.id == userId }) { "Unknown user" }
        return Uuid.random().toString().also { sessions[it] = Session(userId) }
    }

    override suspend fun deleteSession(sessionId: String) {
        sessions.remove(sessionId)
    }

    override suspend fun getSessionById(sessionId: String): Session? {
        return  sessions[sessionId]
    }

    private fun createRoom(userId: Long, name: String): Room {
        require(users.values.any { it.id == userId }) { "Unknown user" }
        return Room(nextRoomId++, name).also {
            rooms.add(it)
            roomMembers[it.id to userId] = RoomRole.Owner
        }
    }

    override suspend fun createRoomJoinRequest(roomId: Long, userId: Long): Pair<Long?, CreateRoomJoinRequestError?> {
        if (getRoomById(roomId) == null) return null to CreateRoomJoinRequestError.RoomNotExist
        if (isRoomMember(roomId, userId)) return null to CreateRoomJoinRequestError.MemberAlreadyInRoom
        if (joinRequests.any { it.roomId == roomId && it.userId == userId }) {
            return null to CreateRoomJoinRequestError.RequestAlreadyExist
        }
        val user = requireNotNull(users.values.find { it.id == userId })
        val request = RoomJoinRequest(nextJoinRequestId++, roomId, requireNotNull(getRoomById(roomId)).name, userId, user.username)
        joinRequests.add(request)
        emitJoinRequestEvent(request.roomId, RoomJoinRequestEvent.Added(request))
        return request.id to null
    }

    override suspend fun getRoomJoinRequests(ownerId: Long): List<RoomJoinRequest> =
        joinRequests.filter { roomMembers[it.roomId to ownerId] == RoomRole.Owner }

    override suspend fun approveRoomJoinRequest(requestId: Long, ownerId: Long): Boolean {
        val request = joinRequests.find { it.id == requestId } ?: return false
        if (!isRoomOwner(request.roomId, ownerId)) return false
        roomMembers.getOrPut(request.roomId to request.userId) { RoomRole.Member }
        messageEventFlow.emit(MockMessageEvent.RoomsMessage(request.userId, RoomListEvent.Added(requireNotNull(getRoomById(request.roomId)))))
        joinRequests.remove(request)
        emitJoinRequestEvent(request.roomId, RoomJoinRequestEvent.Removed(request.id))
        return true
    }

    override suspend fun rejectRoomJoinRequest(requestId: Long, ownerId: Long): Boolean {
        val request = joinRequests.find { it.id == requestId } ?: return false
        if (!isRoomOwner(request.roomId, ownerId)) return false
        joinRequests.remove(request)
        emitJoinRequestEvent(request.roomId, RoomJoinRequestEvent.Removed(request.id))
        return true
    }

    override suspend fun isRoomOwner(roomId: Long, userId: Long): Boolean =
        roomMembers[roomId to userId] == RoomRole.Owner

    override suspend fun getRoomList(userId: Long): List<Room> =
        rooms.filter { (it.id to userId) in roomMembers }

    override suspend fun getRoomMembers(roomId: Long): List<RoomMember> =
        users.values.filter { (roomId to it.id) in roomMembers }
            .sortedBy { it.id }.map { RoomMember(it.id, it.username, roomMembers.getValue(roomId to it.id)) }

    override suspend fun isRoomMember(roomId: Long, userId: Long): Boolean =
        (roomId to userId) in roomMembers

    override suspend fun getRoomById(roomId: Long): Room? = rooms.find { it.id == roomId }

    override suspend fun deleteRoom(roomId: Long, userId: Long): Boolean {
        if (!isRoomOwner(roomId, userId)) return false
        val members = roomMembers.keys.filter { it.first == roomId }
        val messageIds = messageRooms.filterValues { it == roomId }.keys.toSet()
        rooms.removeAll { it.id == roomId }
        members.forEach { roomMembers.remove(it) }
        messages.removeAll { it.id in messageIds }
        messageIds.forEach { messageRooms.remove(it) }
        joinRequests.removeAll { it.roomId == roomId }
        for ((_, memberUserId) in members) {
            messageEventFlow.emit(MockMessageEvent.RoomsMessage(memberUserId, RoomListEvent.Deleted(roomId)))
        }
        return true
    }

    override suspend fun updateRoomName(roomId: Long, userId: Long, name: String): Boolean {
        if (!isRoomOwner(roomId, userId)) return false
        val index = rooms.indexOfFirst { it.id == roomId }
        if (index < 0) return false
        val room = rooms[index].copy(name = name)
        rooms[index] = room
        val members = roomMembers.keys.filter { it.first == roomId }
        for ((_, memberUserId) in members) {
            messageEventFlow.emit(MockMessageEvent.RoomsMessage(memberUserId, RoomListEvent.Changed(room)))
        }
        return true
    }

    private suspend fun emitJoinRequestEvent(roomId: Long, event: RoomJoinRequestEvent) {
        val owners = roomMembers.filter { it.key.first == roomId && it.value == RoomRole.Owner }.keys
        for ((_, userId) in owners) joinRequestFlow.emit(userId to event)
    }

    override fun observeRoomJoinRequestEvent(userId: Long): Flow<RoomJoinRequestEvent> = flow {
        val ids = mutableSetOf<Long>()
        joinRequestFlow.onSubscription {
            val requests = getRoomJoinRequests(userId)
            ids.addAll(requests.map { it.id })
            this@flow.emit(RoomJoinRequestEvent.Initial(requests))
        }.collect { (eventUserId, event) ->
            if (eventUserId != userId) return@collect
            when (event) {
                is RoomJoinRequestEvent.Added -> if (ids.add(event.request.id)) emit(event)
                is RoomJoinRequestEvent.Removed -> if (ids.remove(event.requestId)) emit(event)
                is RoomJoinRequestEvent.Initial -> Unit
            }
        }
    }

    override fun observeRoomAndChatMessageEventByUser(userId: Long): Flow<UserEvent> = flow {
        val roomIds = mutableSetOf<Long>()
        messageEventFlow.onSubscription {
            val rooms = getRoomList(userId)
            roomIds.addAll(rooms.map { it.id })
            this@flow.emit(RoomListEvent.Initial(rooms))
            for (roomId in roomIds) {
                this@flow.emit(UserChatEvent.Initial(roomId, getMessageList(limit = 1, roomId = roomId)))
            }
        }.collect { message ->
            when (message) {
                is MockMessageEvent.RoomsMessage -> {
                    if (message.userId != userId) return@collect
                    when (val event = message.event) {
                        is RoomListEvent.Added -> if (roomIds.add(event.room.id)) {
                            emit(event)
                            emit(UserChatEvent.Initial(event.room.id, getMessageList(limit = 1, roomId = event.room.id)))
                        }
                        is RoomListEvent.Deleted -> if (roomIds.remove(event.roomId)) emit(event)
                        is RoomListEvent.Changed -> if (event.room.id in roomIds) emit(event)
                        is RoomListEvent.Initial -> Unit
                    }
                }
                is MockMessageEvent.ChatMessageEvent -> {
                    if (message.event.roomId in roomIds) emit(message.event)
                }
            }
        }
    }

    override suspend fun recordMessage(userId: Long, msg: String, roomId: Long) {
        requireNotNull(getRoomById(roomId)) { "Unknown room" }
        val user = requireNotNull(users.values.find { it.id == userId }) { "Unknown user" }
        val message = Message(nextMessageId++, user, msg, user.username)
        messageRooms[message.id] = roomId
        messages.add(message)
        messageEventFlow.emit(MockMessageEvent.ChatMessageEvent(UserChatEvent.Added(roomId, message)))
    }
    override suspend fun getOwnMessage(userId: Long, messageId: Long, roomId: Long): Message? =
        messages.find { it.id == messageId && it.user.id == userId && messageRooms[it.id] == roomId }

    override suspend fun updateMessage(userId: Long, messageId: Long, text: String, roomId: Long): Boolean {
        val index = messages.indexOfFirst { it.id == messageId && it.user.id == userId && messageRooms[it.id] == roomId }
        if (index < 0) return false
        val message = messages[index].copy(text = text)
        messages[index] = message
        messageEventFlow.emit(MockMessageEvent.ChatMessageEvent(UserChatEvent.Changed(roomId, message)))
        return true
    }

    override suspend fun deleteMessage(userId: Long, messageId: Long, roomId: Long): Boolean {
        val message = getOwnMessage(userId, messageId, roomId) ?: return false
        messages.remove(message)
        messageRooms.remove(messageId)
        messageEventFlow.emit(MockMessageEvent.ChatMessageEvent(UserChatEvent.Deleted(roomId, messageId)))
        return true
    }

    override suspend fun getMessageList(beforeId: Long?, limit: Int, roomId: Long): List<Message> {
        require(limit > 0) { "limit must be positive" }
        return messages.filter { messageRooms[it.id] == roomId && (beforeId == null || it.id < beforeId) }.takeLast(limit)
    }
}

private sealed interface MockMessageEvent {
    data class RoomsMessage(val userId: Long, val event: RoomListEvent) : MockMessageEvent
    data class ChatMessageEvent(val event: UserChatEvent) : MockMessageEvent
}
