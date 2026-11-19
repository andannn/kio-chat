package kio.chat.domain

import kio.chat.database.CreateRoomJoinRequestError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class MockRepositoryImpl : Repository, WsSessionRepository by InMemoryWsSessionRepository() {
    private val users = mutableMapOf<String, User>()
    private val sessions = mutableMapOf<String, Session>()
    private var nextUserId = 1L

    private val rooms = mutableListOf(Room(1, "demo的房间"))
    private val roomMembers = mutableMapOf((1L to 1L) to RoomRole.Owner)
    private val messageEventFlow = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
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
            messages.add(Message(nextMessageId++, RoomMember(user.id, user.nickname), "Mock message ${it + 1}", user.nickname))
        }
    }

    override fun CoroutineScope.startWatchingMessageEvent(): Job = launch {}

    override suspend fun findUserByUsername(userName: String): User? {
        return users[userName]
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean =
        user.passwordHash == password

    override suspend fun createUser(userName: String, password: String, nickname: String): User? {
        if (users.containsKey(userName)) return null
        return User(nextUserId++, userName, password, nickname).also {
            users[userName] = it
            createRoom(it.id, "${it.nickname}的房间")
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

    override suspend fun createRoom(userId: Long, name: String): Room {
        require(users.values.any { it.id == userId }) { "Unknown user" }
        return Room(nextRoomId++, name).also {
            rooms.add(it)
            roomMembers[it.id to userId] = RoomRole.Owner
            messageEventFlow.emit(MessageEvent.RoomsMessage(userId, RoomListEvent.Added(it)))
        }
    }

    override suspend fun createRoomJoinRequest(roomId: Long, userId: Long): Pair<Long?, CreateRoomJoinRequestError?> {
        if (getRoomById(roomId) == null) return null to CreateRoomJoinRequestError.RoomNotExist
        if (isRoomMember(roomId, userId)) return null to CreateRoomJoinRequestError.MemberAlreadyInRoom
        if (joinRequests.any { it.roomId == roomId && it.userId == userId }) {
            return null to CreateRoomJoinRequestError.RequestAlreadyExist
        }
        val user = requireNotNull(users.values.find { it.id == userId })
        val request = RoomJoinRequest(nextJoinRequestId++, roomId, requireNotNull(getRoomById(roomId)).name, userId, user.nickname)
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
        messageEventFlow.emit(MessageEvent.RoomsMessage(request.userId, RoomListEvent.Added(requireNotNull(getRoomById(request.roomId)))))
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
            .sortedBy { it.id }.map { RoomMember(it.id, it.nickname, roomMembers.getValue(roomId to it.id)) }

    override suspend fun getRoomMemberById(roomId: Long, userId: Long) =
         users.values.filter { (roomId to it.id) in roomMembers && userId == it.id }
            .sortedBy { it.id }.map { RoomMember(it.id, it.nickname, roomMembers.getValue(roomId to it.id)) }
            .firstOrNull()

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
            messageEventFlow.emit(MessageEvent.RoomsMessage(memberUserId, RoomListEvent.Deleted(roomId)))
        }
        return true
    }

    override fun getMessageEventSharedFlow(): SharedFlow<MessageEvent> {
        return messageEventFlow
    }

    override suspend fun updateRoomName(roomId: Long, userId: Long, name: String): Boolean {
        if (!isRoomOwner(roomId, userId)) return false
        val index = rooms.indexOfFirst { it.id == roomId }
        if (index < 0) return false
        val room = rooms[index].copy(name = name)
        rooms[index] = room
        val members = roomMembers.keys.filter { it.first == roomId }
        for ((_, memberUserId) in members) {
            messageEventFlow.emit(MessageEvent.RoomsMessage(memberUserId, RoomListEvent.Changed(room)))
        }
        return true
    }

    private suspend fun emitJoinRequestEvent(roomId: Long, event: RoomJoinRequestEvent) {
        val owners = roomMembers.filter { it.key.first == roomId && it.value == RoomRole.Owner }.keys
        for ((_, userId) in owners) joinRequestFlow.emit(userId to event)
    }

    override suspend fun recordMessage(userId: Long, msg: String, roomId: Long) {
        requireNotNull(getRoomById(roomId)) { "Unknown room" }
        val user = requireNotNull(users.values.find { it.id == userId }) { "Unknown user" }
        val message = Message(nextMessageId++, RoomMember(user.id, user.nickname), msg, user.nickname)
        messageRooms[message.id] = roomId
        messages.add(message)
        messageEventFlow.emit(MessageEvent.ChatMessageEvent(UserChatEvent.Added(roomId, message)))
    }
    override suspend fun getOwnMessage(userId: Long, messageId: Long, roomId: Long): Message? =
        messages.find { it.id == messageId && it.member.userId == userId && messageRooms[it.id] == roomId }

    override suspend fun updateMessage(userId: Long, messageId: Long, text: String, roomId: Long): Boolean {
        val index = messages.indexOfFirst { it.id == messageId && it.member.userId == userId && messageRooms[it.id] == roomId }
        if (index < 0) return false
        val message = messages[index].copy(text = text)
        messages[index] = message
        messageEventFlow.emit(MessageEvent.ChatMessageEvent(UserChatEvent.Changed(roomId, message)))
        return true
    }

    override suspend fun deleteMessage(userId: Long, messageId: Long, roomId: Long): Boolean {
        val message = getOwnMessage(userId, messageId, roomId) ?: return false
        messages.remove(message)
        messageRooms.remove(messageId)
        messageEventFlow.emit(MessageEvent.ChatMessageEvent(UserChatEvent.Deleted(roomId, messageId)))
        return true
    }

    override suspend fun getMessageList(beforeId: Long?, limit: Int, roomId: Long): List<Message> {
        require(limit > 0) { "limit must be positive" }
        return messages.filter { messageRooms[it.id] == roomId && (beforeId == null || it.id < beforeId) }.takeLast(limit)
    }
}
