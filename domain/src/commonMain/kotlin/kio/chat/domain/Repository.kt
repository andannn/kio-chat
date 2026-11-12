package kio.chat.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

interface Repository {
    fun CoroutineScope.startWatchingMessageEvent(): Job
    suspend fun findUserByUsername(userName: String): User?
    suspend fun verifyPassword(user: User, password: String): Boolean
    suspend fun createUser(userName: String, password: String): User?
    suspend fun createSession(userId: Long): String
    suspend fun deleteSession(sessionId: String)
    suspend fun getSessionById(sessionId: String): Session?
    fun observeMessageEvent(): Flow<MessageEvent>
    suspend fun recordMessage(userId: Long, msg: String)
    suspend fun getOwnMessage(userId: Long, messageId: Long): Message?
    suspend fun updateMessage(userId: Long, messageId: Long, text: String): Boolean
    suspend fun deleteMessage(userId: Long, messageId: Long): Boolean
    suspend fun getMessageList(beforeId: Long? = null, limit: Int = 50): List<Message>
}
