package kio.chat.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

data class User(val id: Long, val username: String, val passwordHash: String)
data class Session(val userId: Long)

data class Message(val userId: Long, val text: String)

interface Repository {
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

fun Repository(pgPool: kio.postgres.conn.PgConnectionPool): Repository = RepositoryImpl(pgPool)
