package kio.chat.domain

import kio.chat.database.ChatUserEntity
import kio.chat.database.createSession
import kio.chat.database.createUser
import kio.chat.database.getUserByUsername
import kio.chat.database.getUserIdBySessionId
import kio.chat.util.hashPassword
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.useConnection
import kotlin.uuid.Uuid

internal class RepositoryImpl(
    private val pgPool: PgConnectionPool,
) : Repository by MockRepositoryImpl() {
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
}

private fun ChatUserEntity.toUser() = User(id, username, passwordHash)
