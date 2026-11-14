package kio.chat.domain

import kio.chat.database.ChatRoomEntity
import kio.chat.database.createRoom
import kio.chat.database.getRoomList
import kio.chat.database.getRoomById
import kio.chat.database.ChatUserEntity
import kio.chat.database.ChatMessageWithUserEntity
import kio.chat.database.ChatMessageNotification
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
    suspend fun createRoom(name: String): Room?
    suspend fun getRoomList(): List<Room>
    suspend fun getRoomById(roomId: Long): Room?
    fun observeMessageEvent(roomId: Long): Flow<MessageEvent>
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

    private val messageEventFlow = MutableSharedFlow<Pair<Long, MessageEvent>>(extraBufferCapacity = 64)
    private val notificationJson = Json { ignoreUnknownKeys = true }

    override fun CoroutineScope.startWatchingMessageEvent() = launch {
        pgPool.useConnection { c ->
            c.exec("LISTEN chat_messages")
            while (currentCoroutineContext().isActive) {
                val notification = c.waitNotification()
                when (notification.channel) {
                    "chat_messages" -> {
                        val event = notificationJson.decodeFromString<ChatMessageNotification>(notification.message)
                        val messageEvent = when (event.operation) {
                            "DELETE" -> {
                                MessageEvent.Deleted(event.messageId)
                            }

                            "INSERT" -> {
                                val message = pgPool.useConnection { it.getMessageById(event.messageId) } ?: continue
                                MessageEvent.Added(message.toMessage())
                            }

                            "UPDATE" -> {
                                val message = pgPool.useConnection { it.getMessageById(event.messageId) } ?: continue
                                MessageEvent.Changed(message.toMessage())
                            }

                            else -> continue
                        }
                        messageEventFlow.emit(event.roomId to messageEvent)
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

    override suspend fun createRoom(name: String): Room? =
        pgPool.useConnection { it.createRoom(name) }?.toRoom()

    override suspend fun getRoomList(): List<Room> =
        pgPool.useConnection { it.getRoomList() }.map { it.toRoom() }

    override suspend fun getRoomById(roomId: Long): Room? =
        pgPool.useConnection { it.getRoomById(roomId) }?.toRoom()

    override fun observeMessageEvent(roomId: Long): Flow<MessageEvent> = flow {
        val ids = mutableSetOf<Long>()

        messageEventFlow
            .onSubscription {
                val messages = getMessageList(roomId = roomId)
                ids.addAll(messages.map { it.id })
                this@flow.emit(MessageEvent.Initial(messages))
            }
            .collect { (eventRoomId, event) ->
                if (eventRoomId != roomId) return@collect
                when (event) {
                    is MessageEvent.Added ->
                        if (ids.add(event.message.id)) emit(event)

                    is MessageEvent.Changed -> emit(event)

                    is MessageEvent.Deleted -> {
                        ids.remove(event.messageId)
                        emit(event)
                    }

                    is MessageEvent.Initial -> Unit
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

private fun ChatUserEntity.toUser() = User(id, username, passwordHash)
private fun ChatMessageWithUserEntity.toMessage() = Message(id, User(userId, username, passwordHash), text, username)

private fun ChatRoomEntity.toRoom() = Room(id, name)
