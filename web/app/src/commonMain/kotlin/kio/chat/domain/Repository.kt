package kio.chat.domain

import kio.chat.database.CreateRoomJoinRequestError
import kio.chat.database.createRoomJoinRequest
import kio.chat.database.getRoomJoinRequestById
import kio.chat.database.getRoomJoinRequests
import kio.chat.database.approveRoomJoinRequest
import kio.chat.database.rejectRoomJoinRequest
import kio.chat.database.ChatRoomJoinRequestWithUserEntity
import kio.chat.database.getUserRoomList
import kio.chat.database.getRoomOwners
import kio.chat.database.getRoomMembers
import kio.chat.database.isRoomMember
import kio.chat.database.ChatRoomMemberEntity
import kio.chat.database.ChatRoomEntity
import kio.chat.database.getRoomById
import kio.chat.database.ChatUserEntity
import kio.chat.database.ChatMessageWithUserEntity
import kio.chat.database.ChatMessageNotification
import kio.chat.database.ChatRoomJoinRequestNotification
import kio.chat.database.updateMessage
import kio.chat.database.deleteMessage
import kio.chat.database.createMessage
import kio.chat.database.getMessageList
import kio.chat.database.getMessageById
import kio.chat.database.deleteSession
import kio.chat.database.createSession
import kio.chat.database.createUser
import kio.chat.database.getUserByUsername
import kio.chat.database.getUserIdBySessionId
import kio.chat.util.hashPassword
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.useConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.serialization.json.Json
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

interface Repository {
    fun CoroutineScope.startWatchingMessageEvent(): Job
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createUser(userName: String, password: String): User?
    suspend fun createSession(userId: Long): String
    suspend fun deleteSession(sessionId: String)
    suspend fun getSessionById(sessionId: String): Session?
    suspend fun createRoomJoinRequest(roomId: Long, userId: Long): Pair<Long?, CreateRoomJoinRequestError?>
    suspend fun getRoomJoinRequests(ownerId: Long): List<RoomJoinRequest>
    suspend fun approveRoomJoinRequest(requestId: Long, ownerId: Long): Boolean
    suspend fun rejectRoomJoinRequest(requestId: Long, ownerId: Long): Boolean
    suspend fun isRoomOwner(roomId: Long, userId: Long): Boolean
    suspend fun getRoomList(userId: Long): List<Room>
    suspend fun getRoomMembers(roomId: Long): List<RoomMember>
    suspend fun isRoomMember(roomId: Long, userId: Long): Boolean
    suspend fun getRoomById(roomId: Long): Room?
    fun observeRoomJoinRequestEvent(userId: Long): Flow<RoomJoinRequestEvent>
    fun observeChatMessageEvent(roomId: Long): Flow<ChatEvent>
    suspend fun recordMessage(userId: Long, msg: String, roomId: Long)
    suspend fun getOwnMessage(userId: Long, messageId: Long, roomId: Long): Message?
    suspend fun updateMessage(userId: Long, messageId: Long, text: String, roomId: Long): Boolean
    suspend fun deleteMessage(userId: Long, messageId: Long, roomId: Long): Boolean
    suspend fun getMessageList(beforeId: Long? = null, limit: Int = 50, roomId: Long): List<Message>
}

fun Repository(pgPool: PgConnectionPool): Repository = ServerRepositoryImpl(pgPool)

private class ServerRepositoryImpl(
    private val pgPool: PgConnectionPool,
) : Repository {

    private val messageEventFlow = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
    private val notificationJson = Json { ignoreUnknownKeys = true }

    override fun CoroutineScope.startWatchingMessageEvent() = launch {
        pgPool.useConnection { c ->
            c.exec("LISTEN chat_messages")
            c.exec("LISTEN room_join_requests")
            while (currentCoroutineContext().isActive) {
                val notification = c.waitNotification()
                when (notification.channel) {
                    "chat_messages" -> {
                        val event = notificationJson.decodeFromString<ChatMessageNotification>(notification.message)
                        val chatEvent = when (event.operation) {
                            "DELETE" -> {
                                ChatEvent.Deleted(event.messageId)
                            }

                            "INSERT" -> {
                                val message = pgPool.useConnection { it.getMessageById(event.messageId) } ?: continue
                                ChatEvent.Added(message.toMessage())
                            }

                            "UPDATE" -> {
                                val message = pgPool.useConnection { it.getMessageById(event.messageId) } ?: continue
                                ChatEvent.Changed(message.toMessage())
                            }

                            else -> continue
                        }
                        messageEventFlow.emit(MessageEvent.ChatMessageEvent(event.roomId, chatEvent))
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

                        val owners = pgPool.useConnection { it.getRoomOwners(event.requestRoomId) }
                        for ((userId) in owners) {
                            messageEventFlow.emit(MessageEvent.RoomJoinRequestMessageEvent(userId, joinReqEvent))
                        }
                    }
                }
            }
        }
    }

    override suspend fun findUserByUsername(userName: String): User? =
        pgPool.useConnection { it.getUserByUsername(userName) }?.toUser()

    override suspend fun verifyPassword(user: User, password: String): Boolean =
        hashPassword(password) == user.passwordHash

    override suspend fun createUser(userName: String, password: String): User? =
        pgPool.useConnection { it.createUser(userName, hashPassword(password)) }?.toUser()

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
        getRoomMembers(roomId).any { it.userId == userId && it.role == RoomRole.Owner }

    override suspend fun getRoomList(userId: Long): List<Room> =
        pgPool.useConnection { it.getUserRoomList(userId) }.map { it.toRoom() }

    override suspend fun getRoomMembers(roomId: Long): List<RoomMember> =
        pgPool.useConnection { it.getRoomMembers(roomId) }.map { it.toRoomMember() }

    override suspend fun isRoomMember(roomId: Long, userId: Long): Boolean =
        pgPool.useConnection { it.isRoomMember(roomId, userId) }

    override suspend fun getRoomById(roomId: Long): Room? =
        pgPool.useConnection { it.getRoomById(roomId) }?.toRoom()

    override fun observeRoomJoinRequestEvent(userId: Long): Flow<RoomJoinRequestEvent> = flow {
        val ids = mutableSetOf<Long>()
        messageEventFlow
            .onSubscription {
                val requests = getRoomJoinRequests(userId)
                ids.addAll(requests.map { it.id })
                this@flow.emit(RoomJoinRequestEvent.Initial(requests))
            }
            .filterIsInstance<MessageEvent.RoomJoinRequestMessageEvent>()
            .collect { (eventUserId, event) ->
                if (eventUserId != userId) return@collect
                when (event) {
                    is RoomJoinRequestEvent.Added -> if (ids.add(event.request.id)) emit(event)
                    is RoomJoinRequestEvent.Removed -> if (ids.remove(event.requestId)) emit(event)
                    is RoomJoinRequestEvent.Initial -> Unit
                }
            }
    }

    override fun observeChatMessageEvent(roomId: Long): Flow<ChatEvent> = flow {
        val ids = mutableSetOf<Long>()

        messageEventFlow
            .onSubscription {
                val messages = getMessageList(roomId = roomId)
                ids.addAll(messages.map { it.id })
                this@flow.emit(ChatEvent.Initial(messages))
            }
            .filterIsInstance<MessageEvent.ChatMessageEvent>()
            .collect { (eventRoomId, event) ->
                if (eventRoomId != roomId) return@collect
                when (event) {
                    is ChatEvent.Added ->
                        if (ids.add(event.message.id)) emit(event)

                    is ChatEvent.Changed -> emit(event)

                    is ChatEvent.Deleted -> {
                        ids.remove(event.messageId)
                        emit(event)
                    }

                    is ChatEvent.Initial -> Unit
                }
            }
    }

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

private sealed interface MessageEvent {
    data class ChatMessageEvent(
        val roomId: Long,
        val event: ChatEvent
    ) : MessageEvent

    data class RoomJoinRequestMessageEvent(
        val userId: Long,
        val event: RoomJoinRequestEvent
    ) : MessageEvent
}

private fun ChatUserEntity.toUser() = User(id, username, passwordHash)
private fun ChatMessageWithUserEntity.toMessage() = Message(id, User(userId, username, passwordHash), text, username)

private fun ChatRoomEntity.toRoom() = Room(id, name)

private fun ChatRoomMemberEntity.toRoomMember() = RoomMember(
    userId = userId,
    username = username,
    role = when (role) {
        "owner" -> RoomRole.Owner
        "member" -> RoomRole.Member
        else -> error("Unknown room role: $role")
    },
)

private fun ChatRoomJoinRequestWithUserEntity.toRoomJoinRequest() = RoomJoinRequest(id, roomId, roomName, userId, username)
