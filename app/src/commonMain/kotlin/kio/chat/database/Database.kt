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
        insert into users (
            username,
            password_hash
        )
        values ($1, $2)
        ON CONFLICT (username) DO NOTHING
        returning id, username, password_hash, create_at
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
            insert into sessions(
                id,
                user_id
            )
            values ($1, $2)
            """.trimIndent()
    ) {
        param(sessionId, PostgresTextSerializer)
        param(userId, PostgresInt8Serializer)
    }

    if (ret != "INSERT 0 1") {
        error("create session failed $ret")
    }
}

suspend fun PgConnection.getUserIdBySessionId(sessionId: String): Long? {
    @Serializable
    class UserId(@SerialName("user_id") val userId: PgInt8)

    return query<UserId>(
        """
            select user_id
            from sessions
            where id = $1
            limit 1
            """.trimIndent()
    ) {
        param(sessionId, PostgresTextSerializer)
    }.firstOrNull()?.userId
}

suspend fun PgConnection.getUserByUsername(userName: String): ChatUserEntity? {
    val ret: Flow<ChatUserEntity> = query(
        """
        select *
        from users
        where username = $1
        limit 1
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
        insert into messages(user_id, text)
        values ($1, $2)
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

suspend fun PgConnection.getMessageList(): List<ChatMessageWithUserEntity> =
    query<ChatMessageWithUserEntity>(
        """
        select m.id, m.user_id, m.text, m.create_at, u.username, u.password_hash
        from messages m
        join users u on u.id = m.user_id
        order by m.id
        """.trimIndent()
    ).toList()

suspend fun PgConnection.getMessageById(messageId: Long): ChatMessageWithUserEntity? =
    query<ChatMessageWithUserEntity>(
        """
        select m.id, m.user_id, m.text, m.create_at, u.username, u.password_hash
        from messages m
        join users u on u.id = m.user_id
        where m.id = $1
        """.trimIndent()
    ) {
        param(messageId, PostgresInt8Serializer)
    }.firstOrNull()

suspend fun PgConnection.updateMessage(userId: Long, messageId: Long, text: String): Boolean =
    exec("update messages set text = $1 where id = $2 and user_id = $3") {
        param(text, PostgresTextSerializer)
        param(messageId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    } == "UPDATE 1"

suspend fun PgConnection.deleteMessage(userId: Long, messageId: Long): Boolean =
    exec("delete from messages where id = $1 and user_id = $2") {
        param(messageId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    } == "DELETE 1"
