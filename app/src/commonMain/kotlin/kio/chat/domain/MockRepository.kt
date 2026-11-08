package kio.chat.domain

import kio.chat.util.hashPassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class MockRepositoryImpl : Repository {
    private val users = mutableMapOf<String, User>()
    private val sessions = mutableMapOf<String, Session>()
    private var nextUserId = 1L

    private val messages = mutableListOf<Message>()
    private val messageFlow = MutableSharedFlow<MessageEvent>()
    private var nextMessageId = 1L

    init {
        val user = User(nextUserId++, "demo", hashPassword("demo"))
        users[user.username] = user
        repeat(125) {
            messages.add(Message(nextMessageId++, user, "Mock message ${it + 1}", user.username))
        }
    }

    override fun CoroutineScope.startWatchingMessageEvent(): Job = launch {}

    override suspend fun findUserByUsername(userName: String): User? {
        return users[userName]
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean =
        user.passwordHash == hashPassword(password)

    override suspend fun createUser(userName: String, password: String): User? {
        if (users.containsKey(userName)) return null
        return User(nextUserId++, userName, hashPassword(password)).also { users[userName] = it }
    }

    override suspend fun createSession(userId: Long): String {
        require(users.values.any { it.id == userId }) { "Unknown user" }
        return Uuid.random().toString().also { sessions[it] = Session(userId) }
    }

    override suspend fun getSessionById(sessionId: String): Session? {
        return  sessions[sessionId]
    }

    override fun observeMessageEvent(): Flow<MessageEvent> {
        return flow {
            val ids = mutableSetOf<Long>()
            messageFlow.onSubscription {
                val initial = getMessageList()
                ids.addAll(initial.map { it.id })
                this@flow.emit(MessageEvent.Initial(initial))
            }.collect { event ->
                if (event !is MessageEvent.Added || ids.add(event.message.id)) emit(event)
            }
        }
    }

    override suspend fun recordMessage(userId: Long, msg: String) {
        val user = requireNotNull(users.values.find { it.id == userId }) { "Unknown user" }
        val message = Message(nextMessageId++, user, msg, user.username)
        messages.add(message)
        messageFlow.emit(MessageEvent.Added(message))
    }
    override suspend fun getOwnMessage(userId: Long, messageId: Long): Message? =
        messages.find { it.id == messageId && it.user.id == userId }

    override suspend fun updateMessage(userId: Long, messageId: Long, text: String): Boolean {
        val index = messages.indexOfFirst { it.id == messageId && it.user.id == userId }
        if (index < 0) return false
        val message = messages[index].copy(text = text)
        messages[index] = message
        messageFlow.emit(MessageEvent.Changed(message))
        return true
    }

    override suspend fun deleteMessage(userId: Long, messageId: Long): Boolean {
        val message = getOwnMessage(userId, messageId) ?: return false
        messages.remove(message)
        messageFlow.emit(MessageEvent.Deleted(messageId))
        return true
    }

    override suspend fun getMessageList(beforeId: Long?, limit: Int): List<Message> {
        require(limit > 0) { "limit must be positive" }
        return messages.filter { beforeId == null || it.id < beforeId }.takeLast(limit)
    }
}
