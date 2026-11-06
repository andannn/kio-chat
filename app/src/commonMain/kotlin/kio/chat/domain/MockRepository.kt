package kio.chat.domain

import kio.chat.util.hashPassword
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class MockRepositoryImpl : Repository {
    private val users = mutableMapOf<String, User>()
    private val sessions = mutableMapOf<String, Session>()
    private var nextUserId = 1L

    private val messages = mutableListOf<Message>()
    private val messageFlow = MutableSharedFlow<Message>()

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

    override suspend fun getMessageList(): List<Message>  {
        return messages.toList()
    }


    override fun observeNewMessageFlow(): SharedFlow<Message> {
        return messageFlow
    }

    override suspend fun recordMessage(userId: Long, msg: String) {
        val message = Message(userId, msg)
        messages.add(message)
        messageFlow.emit(message)
    }
}
