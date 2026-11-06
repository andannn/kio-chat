package kio.chat.database

import kio.postgres.conn.PgConnection
import kio.postgres.conn.param
import kio.postgres.conn.query
import kio.postgres.types.PgInt8
import kio.postgres.types.PostgresInt8Serializer
import kio.postgres.types.PostgresTextSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
suspend fun PgConnection.createMessage(userId: Long, text: String): ChatMessageEntity =
    query<ChatMessageEntity>(
        """
        insert into messages(user_id, text)
        values ($1, $2)
        returning id, user_id, text, create_at
        """.trimIndent()
    ) {
        param(userId, PostgresInt8Serializer)
        param(text, PostgresTextSerializer)
    }.first()

suspend fun PgConnection.getMessageList(): List<ChatMessageEntity> =
    query<ChatMessageEntity>(
        "select id, user_id, text, create_at from messages order by id"
    ).toList()

suspend fun PgConnection.getMessageById(messageId: Long): ChatMessageEntity? =
    query<ChatMessageEntity>(
        "select id, user_id, text, create_at from messages where id = $1"
    ) {
        param(messageId, PostgresInt8Serializer)
    }.firstOrNull()
