package kio.chat.database

import kio.postgres.conn.PgConnection
import kio.postgres.conn.param
import kio.postgres.conn.transaction
import kio.postgres.conn.query
import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kio.postgres.types.PostgresInt8Serializer
import kio.postgres.types.PostgresTextSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

suspend fun PgConnection.createUser(userName: String, passwordHash: String, nickname: String = userName): ChatUserEntity? {
    val ret: Flow<ChatUserEntity> = query(
        """
        INSERT INTO users (
            username,
            password_hash,
            nickname
        )
        VALUES ($1, $2, $3)
        ON CONFLICT (username) DO NOTHING
        RETURNING id, username, password_hash, create_at, nickname
        """.trimIndent()
    ) {
        param(userName, PostgresTextSerializer)
        param(passwordHash, PostgresTextSerializer)
        param(nickname, PostgresTextSerializer)
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
        SELECT id, username, password_hash, create_at, nickname
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
suspend fun PgConnection.createMessage(userId: Long, text: String, roomId: Long) {
    exec(
        """
        INSERT INTO messages(user_id, text, room_id)
        VALUES ($1, $2, $3)
        """.trimIndent()
    ) {
        param(userId, PostgresInt8Serializer)
        param(text, PostgresTextSerializer)
        param(roomId, PostgresInt8Serializer)
    }
}

@Serializable
data class ChatMessageWithUserEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("room_id")
    val roomId: PgInt8,
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
    @SerialName("nickname") val nickname: PgText,
    @SerialName("role") val role: PgText? = null,
)

suspend fun PgConnection.getMessageList(beforeId: Long?, limit: Int, roomId: Long): List<ChatMessageWithUserEntity> {
    require(limit > 0) { "limit must be positive" }
    val whereClause = if (beforeId != null) "AND m.id < $beforeId" else ""

    return query<ChatMessageWithUserEntity>(
        """
        SELECT m.id, m.room_id, m.user_id, m.text, m.create_at, u.username, u.password_hash, u.nickname, rm.role
        FROM messages m
        JOIN users u ON u.id = m.user_id
        LEFT JOIN room_members rm ON rm.room_id = m.room_id AND rm.user_id = m.user_id
        WHERE m.room_id = $1 $whereClause
        ORDER BY m.id DESC
        LIMIT $limit
        """.trimIndent()
    ) {
        param(roomId, PostgresInt8Serializer)
    }.toList()
}

suspend fun PgConnection.getMessageById(messageId: Long): ChatMessageWithUserEntity? =
    query<ChatMessageWithUserEntity>(
        """
        SELECT m.id, m.room_id, m.user_id, m.text, m.create_at, u.username, u.password_hash, u.nickname, rm.role
        FROM messages m
        JOIN users u ON u.id = m.user_id
        LEFT JOIN room_members rm ON rm.room_id = m.room_id AND rm.user_id = m.user_id
        WHERE m.id = $1
        """.trimIndent()
    ) {
        param(messageId, PostgresInt8Serializer)
    }.firstOrNull()

suspend fun PgConnection.updateMessage(userId: Long, messageId: Long, text: String, roomId: Long = 1): Boolean =
    exec("UPDATE messages SET text = $1 WHERE id = $2 AND user_id = $3 AND room_id = $4") {
        param(text, PostgresTextSerializer)
        param(messageId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
        param(roomId, PostgresInt8Serializer)
    } == "UPDATE 1"

suspend fun PgConnection.deleteMessage(userId: Long, messageId: Long, roomId: Long = 1): Boolean =
    exec("DELETE FROM messages WHERE id = $1 AND user_id = $2 AND room_id = $3") {
        param(messageId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
        param(roomId, PostgresInt8Serializer)
    } == "DELETE 1"

suspend fun PgConnection.createRoom(name: String): ChatRoomEntity? =
    query<ChatRoomEntity>(
        "INSERT INTO rooms(name) VALUES ($1) RETURNING id, name, create_at"
    ) {
        param(name, PostgresTextSerializer)
    }.firstOrNull()

suspend fun PgConnection.createRoomForUser(userId: Long, name: String): ChatRoomEntity = transaction { tx ->
    val room = tx.query<ChatRoomEntity>(
        "INSERT INTO rooms(name) VALUES ($1) RETURNING id, name, create_at"
    ) {
        param(name, PostgresTextSerializer)
    }.first()
    tx.exec("INSERT INTO room_members(room_id, user_id, role) VALUES ($1, $2, 'owner')") {
        param(room.id, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    }
    room
}

suspend fun PgConnection.getRoomList(): List<ChatRoomEntity> =
    query<ChatRoomEntity>("SELECT id, name, create_at FROM rooms ORDER BY id").toList()

suspend fun PgConnection.getRoomById(roomId: Long): ChatRoomEntity? =
    query<ChatRoomEntity>("SELECT id, name, create_at FROM rooms WHERE id = $1") {
        param(roomId, PostgresInt8Serializer)
    }.firstOrNull()

suspend fun PgConnection.updateRoomName(roomId: Long, userId: Long, name: String): Boolean =
    exec(
        """
        UPDATE rooms SET name = $1
        WHERE id = $2 AND EXISTS (
            SELECT 1 FROM room_members
            WHERE room_id = rooms.id AND user_id = $3 AND role = 'owner'
        )
        """.trimIndent()
    ) {
        param(name, PostgresTextSerializer)
        param(roomId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    } == "UPDATE 1"

suspend fun PgConnection.deleteRoom(roomId: Long, userId: Long): Boolean = transaction { tx ->
    tx.query<ChatRoomEntity>(
        """
        SELECT r.id, r.name, r.create_at
        FROM rooms r
        JOIN room_members rm ON rm.room_id = r.id
        WHERE r.id = $1 AND rm.user_id = $2 AND rm.role = 'owner'
        FOR UPDATE OF r, rm
        """.trimIndent()
    ) {
        param(roomId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    }.firstOrNull() ?: return@transaction false

    tx.exec("DELETE FROM rooms WHERE id = $1") {
        param(roomId, PostgresInt8Serializer)
    } == "DELETE 1"
}

suspend fun PgConnection.addRoomMember(roomId: Long, userId: Long) {
    exec("INSERT INTO room_members(room_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING") {
        param(roomId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    }
}

suspend fun PgConnection.getUserRoomList(userId: Long): List<ChatRoomEntity> =
    query<ChatRoomEntity>(
        """
        SELECT r.id, r.name, r.create_at
        FROM rooms r
        JOIN room_members rm ON rm.room_id = r.id
        WHERE rm.user_id = $1
        ORDER BY r.id
        """.trimIndent()
    ) {
        param(userId, PostgresInt8Serializer)
    }.toList()

suspend fun PgConnection.isRoomMember(roomId: Long, userId: Long): Boolean {
    @Serializable
    data class Member(@SerialName("user_id") val userId: PgInt8)

    return query<Member>("SELECT user_id FROM room_members WHERE room_id = $1 AND user_id = $2") {
        param(roomId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    }.firstOrNull() != null
}

suspend fun PgConnection.getRoomMembers(roomId: Long): List<ChatRoomMemberEntity> =
    query<ChatRoomMemberEntity>(
        """
        SELECT u.id, u.nickname, rm.role
        FROM room_members rm
        JOIN users u ON u.id = rm.user_id
        WHERE rm.room_id = $1
        ORDER BY u.id
        """.trimIndent()
    ) {
        param(roomId, PostgresInt8Serializer)
    }.toList()

suspend fun PgConnection.getRoomMemberById(roomId: Long, userId: Long): ChatRoomMemberEntity? =
    query<ChatRoomMemberEntity>(
        """
        SELECT u.id, u.nickname, rm.role
        FROM room_members rm
        JOIN users u ON u.id = rm.user_id
        WHERE rm.room_id = $1 AND u.id = $2
        ORDER BY u.id
        LIMIT 1
        """.trimIndent()
    ) {
        param(roomId, PostgresInt8Serializer)
        param(userId, PostgresInt8Serializer)
    }.firstOrNull()

suspend fun PgConnection.getRoomOwners(roomId: Long): List<ChatRoomMemberEntity> =
    query<ChatRoomMemberEntity>(
        """
        SELECT u.id, u.nickname, rm.role
        FROM room_members rm
        JOIN users u ON u.id = rm.user_id
        WHERE rm.room_id = $1 AND rm.role = 'owner'
        ORDER BY u.id
        """.trimIndent()
    ) {
        param(roomId, PostgresInt8Serializer)
    }.toList()

suspend fun PgConnection.getOwnedRoomIds(userId: Long): List<Long> {
    @Serializable
    data class Result(val roomId: PgInt8)
    return query<Result>(
        """
        SELECT room_id
        FROM room_members
        WHERE user_id = $1 AND role = 'owner'
        ORDER BY room_id
        """.trimIndent()
    ) {
        param(userId, PostgresInt8Serializer)
    }.map { it.roomId }.toList()
}

enum class CreateRoomJoinRequestError {
    RoomNotExist,
    MemberAlreadyInRoom,
    RequestAlreadyExist,
}

suspend fun PgConnection.createRoomJoinRequest(roomId: Long, userId: Long): Pair<ChatRoomJoinRequestEntity?, CreateRoomJoinRequestError?> =
    transaction { tx ->
        val room = tx.query<ChatRoomEntity>(
            "SELECT id, name, create_at FROM rooms WHERE id = $1 FOR KEY SHARE"
        ) {
            param(roomId, PostgresInt8Serializer)
        }.firstOrNull() ?: return@transaction (null to CreateRoomJoinRequestError.RoomNotExist)

        @Serializable
        data class ExistingMember(@SerialName("user_id") val userId: PgInt8)

        val member = tx.query<ExistingMember>(
            "SELECT user_id FROM room_members WHERE room_id = $1 AND user_id = $2"
        ) {
            param(room.id, PostgresInt8Serializer)
            param(userId, PostgresInt8Serializer)
        }.firstOrNull()
        if (member != null) return@transaction (null to CreateRoomJoinRequestError.MemberAlreadyInRoom)

        val request = tx.query<ChatRoomJoinRequestEntity>(
            """
            INSERT INTO room_join_requests(room_id, user_id)
            VALUES ($1, $2)
            ON CONFLICT (room_id, user_id) DO NOTHING
            RETURNING id, room_id, user_id, create_at
            """.trimIndent()
        ) {
            param(room.id, PostgresInt8Serializer)
            param(userId, PostgresInt8Serializer)
        }.firstOrNull()
        if (request == null) return@transaction null to CreateRoomJoinRequestError.RequestAlreadyExist

        return@transaction request to null
    }

suspend fun PgConnection.getRoomJoinRequests(ownerId: Long): List<ChatRoomJoinRequestWithUserEntity> =
    query<ChatRoomJoinRequestWithUserEntity>(
        """
        SELECT jr.id, jr.room_id, r.name AS room_name, jr.user_id, u.nickname, jr.create_at
        FROM room_join_requests jr
        JOIN rooms r ON r.id = jr.room_id
        JOIN users u ON u.id = jr.user_id
        JOIN room_members owner_member ON owner_member.room_id = jr.room_id
            AND owner_member.user_id = $1 AND owner_member.role = 'owner'
        ORDER BY jr.id
        """.trimIndent()
    ) {
        param(ownerId, PostgresInt8Serializer)
    }.toList()

suspend fun PgConnection.approveRoomJoinRequest(requestId: Long, ownerId: Long): Boolean =
    transaction { tx ->
        val request = tx.query<ChatRoomJoinRequestEntity>(
            """
            SELECT jr.id, jr.room_id, jr.user_id, jr.create_at
            FROM room_join_requests jr
            JOIN room_members owner_member ON owner_member.room_id = jr.room_id
                AND owner_member.user_id = $2 AND owner_member.role = 'owner'
            WHERE jr.id = $1
            FOR UPDATE OF jr
            """.trimIndent()
        ) {
            param(requestId, PostgresInt8Serializer)
            param(ownerId, PostgresInt8Serializer)
        }.firstOrNull() ?: return@transaction false

        tx.exec("INSERT INTO room_members(room_id, user_id, role) VALUES ($1, $2, 'member') ON CONFLICT DO NOTHING") {
            param(request.roomId, PostgresInt8Serializer)
            param(request.userId, PostgresInt8Serializer)
        }
        tx.exec("DELETE FROM room_join_requests WHERE id = $1") {
            param(request.id, PostgresInt8Serializer)
        }
        true
    }

suspend fun PgConnection.rejectRoomJoinRequest(requestId: Long, ownerId: Long): Boolean =
    exec(
        """
        DELETE FROM room_join_requests jr
        USING room_members owner_member
        WHERE jr.id = $1 AND owner_member.room_id = jr.room_id
            AND owner_member.user_id = $2 AND owner_member.role = 'owner'
        """.trimIndent()
    ) {
        param(requestId, PostgresInt8Serializer)
        param(ownerId, PostgresInt8Serializer)
    } == "DELETE 1"

suspend fun PgConnection.getRoomJoinRequestById(requestId: Long): ChatRoomJoinRequestWithUserEntity? =
    query<ChatRoomJoinRequestWithUserEntity>(
        """
        SELECT jr.id, jr.room_id, r.name AS room_name, jr.user_id, u.nickname, jr.create_at
        FROM room_join_requests jr
        JOIN rooms r ON r.id = jr.room_id
        JOIN users u ON u.id = jr.user_id
        WHERE jr.id = $1
        """.trimIndent()
    ) {
        param(requestId, PostgresInt8Serializer)
    }.firstOrNull()
