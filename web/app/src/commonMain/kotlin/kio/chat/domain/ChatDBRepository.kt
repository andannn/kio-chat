package kio.chat.domain

import kio.chat.database.*
import kio.chat.util.hashPassword
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.useConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

interface ChatDBRepository {
    fun CoroutineScope.startWatchingMessageEvent(): Job
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createUser(userName: String, password: String, nickname: String = userName): User?
    suspend fun createSession(userId: Long): String
    suspend fun deleteSession(sessionId: String)
    suspend fun getSessionById(sessionId: String): Session?
    suspend fun createRoomJoinRequest(roomId: Long, userId: Long): Pair<Long?, CreateRoomJoinRequestError?>
    suspend fun getRoomJoinRequests(ownerId: Long): List<RoomJoinRequest>
    suspend fun approveRoomJoinRequest(requestId: Long, ownerId: Long): Boolean
    suspend fun rejectRoomJoinRequest(requestId: Long, ownerId: Long): Boolean
    suspend fun isRoomOwner(roomId: Long, userId: Long): Boolean
    suspend fun createRoom(userId: Long, name: String): Room
    suspend fun getOwnedRoomIds(userId: Long): List<Long>
    suspend fun getRoomList(userId: Long): List<Room>
    suspend fun getRoomMembers(roomId: Long): List<RoomMember>
    suspend fun getRoomMemberById(roomId: Long, userId: Long): RoomMember?
    suspend fun isRoomMember(roomId: Long, userId: Long): Boolean
    suspend fun getRoomById(roomId: Long): Room?
    suspend fun updateRoomName(roomId: Long, userId: Long, name: String): Boolean
    suspend fun deleteRoom(roomId: Long, userId: Long): Boolean
    fun getMessageEventSharedFlow(): SharedFlow<MessageEvent>
    suspend fun recordMessage(userId: Long, msg: String, roomId: Long)
    suspend fun getOwnMessage(userId: Long, messageId: Long, roomId: Long): Message?
    suspend fun updateMessage(userId: Long, messageId: Long, text: String, roomId: Long): Boolean
    suspend fun deleteMessage(userId: Long, messageId: Long, roomId: Long): Boolean
    suspend fun getMessageList(beforeId: Long? = null, limit: Int = 50, roomId: Long): List<Message>
}

private val notificationJson = Json { ignoreUnknownKeys = true }

class ChatDBRepositoryImpl(
    private val pgPool: PgConnectionPool,
) : ChatDBRepository {

    private val dbMessageEventFlow = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)

    override fun CoroutineScope.startWatchingMessageEvent() = launch {
        pgPool.useConnection { c ->
            c.exec("LISTEN chat_messages")
            c.exec("LISTEN room_join_requests")
            c.exec("LISTEN room_members")
            c.exec("LISTEN rooms")
            while (currentCoroutineContext().isActive) {
                val notification = c.waitNotification()
                when (notification.channel) {
                    "chat_messages" -> {
                        val event = notificationJson.decodeFromString<ChatMessageNotification>(notification.message)
                        val chatEvent = when (event.operation) {
                            "DELETE" -> {
                                UserChatEvent.Deleted(event.roomId, event.messageId)
                            }

                            "INSERT" -> {
                                val message = pgPool.useConnection { it.getMessageById(event.messageId) } ?: continue
                                UserChatEvent.Added(event.roomId, message.toMessage())
                            }

                            "UPDATE" -> {
                                val message = pgPool.useConnection { it.getMessageById(event.messageId) } ?: continue
                                UserChatEvent.Changed(event.roomId, message.toMessage())
                            }

                            else -> continue
                        }
                        dbMessageEventFlow.emit(MessageEvent.ChatMessageEvent(chatEvent))
                    }

                    "rooms" -> {
                        val event = notificationJson.decodeFromString<ChatRoomNotification>(notification.message)
                        if (event.operation != "UPDATE") continue
                        val room = pgPool.useConnection { it.getRoomById(event.roomId) } ?: continue
                        dbMessageEventFlow.emit(MessageEvent.RoomChangeEvent(RoomListEvent.Changed(room.toRoom())))
                    }

                    "room_members" -> {
                        val event = notificationJson.decodeFromString<ChatRoomMemberNotification>(notification.message)
                        val roomEvent = when (event.operation) {
                            "DELETE" -> {
                                MessageEvent.UserRoomDeleteEvent(event.userId, RoomListEvent.Deleted(event.roomId))
                            }

                            "INSERT" -> {
                                val message = pgPool.useConnection { it.getRoomById(event.roomId) } ?: continue
                                MessageEvent.UserRoomAddEvent(event.userId, RoomListEvent.Added(message.toRoom()))
                            }

                            else -> continue
                        }
                        dbMessageEventFlow.emit(roomEvent)
                    }

                    "room_join_requests" -> {
                        val event = notificationJson.decodeFromString<ChatRoomJoinRequestNotification>(notification.message)
                        val joinReqEvent = when (event.operation) {
                            "DELETE" -> {
                                RoomJoinRequestEvent.Removed(event.requestId)
                            }

                            "INSERT" -> {
                                val request = pgPool.useConnection { it.getRoomJoinRequestById(event.requestId) }
                                    ?: continue
                                RoomJoinRequestEvent.Added(request.toRoomJoinRequest())
                            }

                            else -> continue
                        }

                        dbMessageEventFlow.emit(MessageEvent.RoomJoinRequestMessageEvent(event.requestRoomId, joinReqEvent))
                    }
                }
            }
        }
    }

    override suspend fun findUserByUsername(userName: String): User? =
        pgPool.useConnection { it.getUserByUsername(userName) }?.toUser()

    override suspend fun verifyPassword(user: User, password: String): Boolean =
        hashPassword(password) == user.passwordHash

    override suspend fun createUser(userName: String, password: String, nickname: String): User? =
        pgPool.useConnection { it.createUser(userName, hashPassword(password), nickname) }?.toUser()

    override suspend fun createSession(userId: Long): String {
        val sessionId = Uuid.random().toString()
        pgPool.useConnection { it.createSession(userId, sessionId) }
        return sessionId
    }

    override suspend fun deleteSession(sessionId: String) {
        pgPool.useConnection { it.deleteSession(sessionId) }
    }

    override suspend fun getSessionById(sessionId: String): Session? =
        pgPool.useConnection { it.getUserIdBySessionId(sessionId) }?.let { Session(it) }

    override suspend fun createRoomJoinRequest(roomId: Long, userId: Long): Pair<Long?, CreateRoomJoinRequestError?> {
        val (request, error) = pgPool.useConnection { it.createRoomJoinRequest(roomId, userId) }
        return request?.id to error
    }

    override suspend fun getRoomJoinRequests(ownerId: Long): List<RoomJoinRequest> =
        pgPool.useConnection { it.getRoomJoinRequests(ownerId) }.map { it.toRoomJoinRequest() }

    override suspend fun approveRoomJoinRequest(requestId: Long, ownerId: Long): Boolean =
        pgPool.useConnection { it.approveRoomJoinRequest(requestId, ownerId) }

    override suspend fun rejectRoomJoinRequest(requestId: Long, ownerId: Long): Boolean =
        pgPool.useConnection { it.rejectRoomJoinRequest(requestId, ownerId) }

    override suspend fun isRoomOwner(roomId: Long, userId: Long): Boolean =
        getRoomMemberById(roomId, userId)?.role == RoomRole.Owner

    override suspend fun getOwnedRoomIds(userId: Long): List<Long> =
        pgPool.useConnection { it.getOwnedRoomIds(userId) }

    override suspend fun createRoom(userId: Long, name: String): Room =
        pgPool.useConnection { it.createRoomForUser(userId, name) }.toRoom()

    override suspend fun getRoomList(userId: Long): List<Room> =
        pgPool.useConnection { it.getUserRoomList(userId) }.map { it.toRoom() }

    override suspend fun getRoomMembers(roomId: Long): List<RoomMember> =
        pgPool.useConnection { it.getRoomMembers(roomId) }.map { it.toRoomMember() }

    override suspend fun getRoomMemberById(roomId: Long, userId: Long) =
        pgPool.useConnection { it.getRoomMemberById(roomId, userId) }?.toRoomMember()

    override suspend fun isRoomMember(roomId: Long, userId: Long): Boolean =
        pgPool.useConnection { it.isRoomMember(roomId, userId) }

    override suspend fun getRoomById(roomId: Long): Room? =
        pgPool.useConnection { it.getRoomById(roomId) }?.toRoom()

    override suspend fun deleteRoom(roomId: Long, userId: Long): Boolean =
        pgPool.useConnection { it.deleteRoom(roomId, userId) }

    override fun getMessageEventSharedFlow(): SharedFlow<MessageEvent> {
        return dbMessageEventFlow
    }

    override suspend fun updateRoomName(roomId: Long, userId: Long, name: String): Boolean =
        pgPool.useConnection { it.updateRoomName(roomId, userId, name) }

    override suspend fun recordMessage(userId: Long, msg: String, roomId: Long) {
        pgPool.useConnection { it.createMessage(userId, msg, roomId) }
    }

    override suspend fun getOwnMessage(userId: Long, messageId: Long, roomId: Long): Message? =
        pgPool.useConnection { it.getMessageById(messageId) }?.takeIf { it.userId == userId && it.roomId == roomId }?.toMessage()

    override suspend fun updateMessage(userId: Long, messageId: Long, text: String, roomId: Long): Boolean =
        pgPool.useConnection { it.updateMessage(userId, messageId, text, roomId) }

    override suspend fun deleteMessage(userId: Long, messageId: Long, roomId: Long): Boolean =
        pgPool.useConnection { it.deleteMessage(userId, messageId, roomId) }

    override suspend fun getMessageList(
        beforeId: Long?,
        limit: Int,
        roomId: Long,
    ): List<Message> {
        return pgPool.useConnection { it.getMessageList(beforeId, limit, roomId) }
            .map { it.toMessage() }
            .asReversed()
    }
}

sealed interface MessageEvent {
    sealed interface UserRoomEvent: MessageEvent {
        val userId: Long
    }

    data class UserRoomAddEvent(
        override val userId: Long,
        val event: RoomListEvent.Added
    ): UserRoomEvent

    data class UserRoomDeleteEvent(
        override val userId: Long,
        val event: RoomListEvent.Deleted
    ): UserRoomEvent

    data class RoomChangeEvent(
        val event: RoomListEvent.Changed
    ) : MessageEvent

    data class ChatMessageEvent(
        val event: UserChatEvent
    ) : MessageEvent

    data class RoomJoinRequestMessageEvent(
        val roomId: Long,
        val event: RoomJoinRequestEvent
    ) : MessageEvent
}

private fun ChatUserEntity.toUser() = User(id, username, passwordHash, nickname)
private fun ChatMessageWithUserEntity.toMessage() = Message(
    id = id,
    member = RoomMember(
        userId = userId,
        nickname = nickname,
        role = when (role) {
            "owner" -> RoomRole.Owner
            "member" -> RoomRole.Member
            null -> RoomRole.Member
            else -> error("Unknown room role: $role")
        },
    ),
    text = text,
    nickname = nickname,
)

private fun ChatRoomEntity.toRoom() = Room(id, name)

private fun ChatRoomMemberEntity.toRoomMember() = RoomMember(
    userId = userId,
    nickname = nickname,
    role = when (role) {
        "owner" -> RoomRole.Owner
        "member" -> RoomRole.Member
        else -> error("Unknown room role: $role")
    },
)

private fun ChatRoomJoinRequestWithUserEntity.toRoomJoinRequest() = RoomJoinRequest(id, roomId, roomName, userId, nickname)
