package kio.chat.database

import kio.postgres.conn.PgConnection
import kio.postgres.conn.param
import kio.postgres.conn.query
import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kio.postgres.types.PostgresInt8Serializer
import kio.postgres.types.PostgresTextSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

suspend fun PgConnection.createUser(userName: String, passwordHash: String): ChatUserEntity? {
    val ret: Flow<ChatUserEntity> = query(
        """
        INSERT INTO users (
            username,
            password_hash
        )
        VALUES ($1, $2)
        ON CONFLICT (username) DO NOTHING
        RETURNING id, username, password_hash, create_at
        """.trimIndent()
    ) {
        param(userName, PostgresTextSerializer)
        param(passwordHash, PostgresTextSerializer)
    }
    return ret.firstOrNull()
}

suspend fun PgConnection.createSession(userId: Long, sessionId: String) {
    val ret = exec(
        """
            INSERT INTO sessions(
                id,
                user_id
            )
            VALUES ($1, $2)
            """.trimIndent()
    ) {
        param(sessionId, PostgresTextSerializer)
        param(userId, PostgresInt8Serializer)
    }

    if (ret != "INSERT 0 1") {
        error("create session failed $ret")
    }
}

suspend fun PgConnection.deleteSession(sessionId: String) {
    exec("DELETE FROM sessions WHERE id = $1") {
        param(sessionId, PostgresTextSerializer)
    }
}

suspend fun PgConnection.getUserIdBySessionId(sessionId: String): Long? {
    @Serializable
    class UserId(@SerialName("user_id") val userId: PgInt8)

    return query<UserId>(
        """
            SELECT user_id
            FROM sessions
            WHERE id = $1
            LIMIT 1
            """.trimIndent()
    ) {
        param(sessionId, PostgresTextSerializer)
    }.firstOrNull()?.userId
}

suspend fun PgConnection.getUserByUsername(userName: String): ChatUserEntity? {
    val ret: Flow<ChatUserEntity> = query(
        """
        SELECT *
        FROM users
        WHERE username = $1
        LIMIT 1
        """.trimIndent()
    ) {
        param(userName, PostgresTextSerializer)
    }
    return ret.firstOrNull()
}


// The database trigger publishes the notification when the transaction commits.
suspend fun PgConnection.createMessage(userId: Long, text: String) {
    exec(
        """
        INSERT INTO messages(user_id, text)
        VALUES ($1, $2)
        """.trimIndent()
    ) {
        param(userId, PostgresInt8Serializer)
        param(text, PostgresTextSerializer)
    }
}

@Serializable
data class ChatMessageWithUserEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("user_id")
    val userId: PgInt8,
    @SerialName("text")
    val text: PgText,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
    @SerialName("username")
    val username: PgText,
    @SerialName("password_hash")
    val passwordHash: PgText,
)

suspend fun PgConnection.getMessageList(beforeId: Long?, limit: Int): List<ChatMessageWithUserEntity> {
    require(limit > 0) { "limit must be positive" }
    val whereClause = if (beforeId != null) "WHERE m.id < $beforeId" else ""

    return query<ChatMessageWithUserEntity>(
        """
        SELECT m.id, m.user_id, m.text, m.create_at, u.username, u.password_hash
        FROM messages m
        JOIN users u ON u.id = m.user_id
        $whereClause
        ORDER BY m.id DESC
        LIMIT $limit
        """.trimIndent()
    ).toList()
}

suspend fun PgConnection.getMessageById(messageId: Long): ChatMessageWithUserEntity? =
    query<ChatMessageWithUserEntity>(
        """
        SELECT m.id, m.user_id, m.text, m.create_at, u.username, u.password_hash
        FROM messages m
        JOIN users u ON u.id = m.user_id
        WHERE m.id = $1
        """.trimIndent()
    ) {
        param(messageId, PostgresInt8Serializer)
    }.firstOrNull()

suspend fun PgConnection.updateMessage(userId: Long, messageId: Long, text: String): Boolean =
    exec("UPDATE messages SET text = $1 WHERE id = $2 AND user_id = $3") {
        param(text, PostgresTextSerializer)
        param(messageId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    } == "UPDATE 1"

suspend fun PgConnection.deleteMessage(userId: Long, messageId: Long): Boolean =
    exec("DELETE FROM messages WHERE id = $1 AND user_id = $2") {
        param(messageId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    } == "DELETE 1"
