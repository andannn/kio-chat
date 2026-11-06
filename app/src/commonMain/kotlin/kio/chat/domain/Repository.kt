package kio.chat.domain

import kio.chat.database.ChatUserEntity
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

data class User(val id: Long, val username: String, val passwordHash: String)
data class Session(val userId: Long)

data class Message(val userId: Long, val text: String)

interface Repository {
    fun CoroutineScope.startWatchingMessageEvent(): Job
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createUser(userName: String, password: String): User?
    suspend fun createSession(userId: Long): String
    suspend fun getSessionById(sessionId: String): Session?

    suspend fun getMessageList(): List<Message>

    fun observeNewMessageFlow() : SharedFlow<Message>

    //
    suspend fun recordMessage(userId: Long, msg: String)
}

fun Repository(pgPool: PgConnectionPool): Repository = RepositoryImpl(pgPool)

internal class RepositoryImpl(
    private val pgPool: PgConnectionPool,
) : Repository {

    override fun CoroutineScope.startWatchingMessageEvent() = launch {
        pgPool.useConnection { c ->
            c.exec("LISTEN chat_messages")
            while (currentCoroutineContext().isActive) {
                val notification = c.waitNotification()
                println("notification $notification")
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

    override suspend fun getMessageList(): List<Message> {
        TODO("Not yet implemented")
    }

    override fun observeNewMessageFlow(): SharedFlow<Message> {
        TODO("Not yet implemented")
    }

    override suspend fun recordMessage(userId: Long, msg: String) {
        TODO("Not yet implemented")
    }
}

private fun ChatUserEntity.toUser() = User(id, username, passwordHash)

