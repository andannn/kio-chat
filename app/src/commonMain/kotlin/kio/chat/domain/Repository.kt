package kio.chat.domain

import kio.chat.database.ChatUserEntity
import kio.chat.database.ChatMessageEntity
import kio.chat.database.ChatMessageNotification
import kio.chat.database.createMessage
import kio.chat.database.getMessageList
import kio.chat.database.getMessageById
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

data class User(val id: Long, val username: String, val passwordHash: String)
data class Session(val userId: Long)

data class Message(val id: Long, val userId: Long, val text: String)

sealed interface MessageEvent {
    data class Initial(val message: List<Message>): MessageEvent
    data class Added(val message: Message) : MessageEvent
    data class Changed(val message: Message) : MessageEvent
    data class Deleted(val messageId: Long) : MessageEvent
}

interface Repository {
    fun CoroutineScope.startWatchingMessageEvent(): Job
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createUser(userName: String, password: String): User?
    suspend fun createSession(userId: Long): String
    suspend fun getSessionById(sessionId: String): Session?
    fun observeMessageEvent(): Flow<MessageEvent>
    suspend fun recordMessage(userId: Long, msg: String)
}

fun Repository(pgPool: PgConnectionPool): Repository = RepositoryImpl(pgPool)

internal class RepositoryImpl(
    private val pgPool: PgConnectionPool,
) : Repository {

    private val messageEventFlow = MutableSharedFlow<MessageEvent>(extraBufferCapacity = 64)
    private val notificationJson = Json { ignoreUnknownKeys = true }

    override fun CoroutineScope.startWatchingMessageEvent() = launch {
        pgPool.useConnection { c ->
            c.exec("LISTEN chat_messages")
            while (currentCoroutineContext().isActive) {
                val notification = c.waitNotification()
                if (notification.channel != "chat_messages") continue
                val event = notificationJson.decodeFromString<ChatMessageNotification>(notification.message)
                println("event ${event}")
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
                messageEventFlow.emit(messageEvent)
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

    override suspend fun getSessionById(sessionId: String): Session? =
        pgPool.useConnection { it.getUserIdBySessionId(sessionId) }?.let { Session(it) }

    override fun observeMessageEvent(): Flow<MessageEvent> = flow {
        val ids = mutableSetOf<Long>()

        messageEventFlow
            .onSubscription {
                val messages = pgPool.useConnection { it.getMessageList() }.map { it.toMessage() }
                ids.addAll(messages.map { it.id })
                this@flow.emit(MessageEvent.Initial(messages))
            }
            .collect { event ->
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

    override suspend fun recordMessage(userId: Long, msg: String) {
        pgPool.useConnection { it.createMessage(userId, msg) }
    }
}

private fun ChatUserEntity.toUser() = User(id, username, passwordHash)
private fun ChatMessageEntity.toMessage() = Message(id, userId, text)
