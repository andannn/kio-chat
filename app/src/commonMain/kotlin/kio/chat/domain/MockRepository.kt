package kio.chat.domain

import kio.chat.util.hashPassword
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

// In-memory development repository. Users and sessions disappear on restart.
class MockRepositoryImpl : Repository {
    private val mutex = Mutex()
    private val users = mutableMapOf<String, User>()
    private val sessions = mutableMapOf<String, Session>()
    private var nextUserId = 1L

    override suspend fun findUserByUsername(userName: String): User? = mutex.withLock {
        users[userName]
    }

    override suspend fun verifyPassword(user: User, password: String): Boolean =
        user.passwordHash == hashPassword(password)

    override suspend fun createUser(userName: String, password: String): User? = mutex.withLock {
        if (users.containsKey(userName)) return@withLock null
        User(nextUserId++, userName, hashPassword(password)).also { users[userName] = it }
    }

    override suspend fun createSession(userId: Long): String = mutex.withLock {
        require(users.values.any { it.id == userId }) { "Unknown user" }
        Uuid.random().toString().also { sessions[it] = Session(userId) }
    }

    override suspend fun getSessionById(sessionId: String): Session? = mutex.withLock {
        sessions[sessionId]
    }

    private val messageFlow = MutableSharedFlow<Message>()

    override fun observeNewMessageFlow(): SharedFlow<Message> {
        return messageFlow
    }

    override suspend fun recordMessage(userId: Long, msg: String) {
        messageFlow.emit(Message(userId, msg))
    }
}
