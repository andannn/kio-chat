package kio.chat.database

import kio.async.PollerFactory
import kio.async.io.getEnv
import kio.async.runPollEventLoop
import kio.chat.db.dropAllTables
import kio.chat.db.migrations
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

abstract class DatabaseTest {
    abstract val pollerFactory: PollerFactory

    @Test
    fun getUserByUsernameTest() = withTestPgDatabase {
        val user = createUser("userA", "pass")!!
        assertEquals("userA", user.username)
        assertEquals("pass", user.passwordHash)
        assertEquals(user, getUserByUsername(user.username))
    }

    @Test
    fun createUserDuplicatedTest() = withTestPgDatabase {
        val user1 = createUser("userA", "pass")!!
        val user2 = createUser("userA", "pass1")
        assertNull(user2)
    }

    @Test
    fun sessionTest() = withTestPgDatabase {
        val user = createUser(userName = "1", passwordHash = "pas")!!
        createSession(user.id, "12345")
        assertEquals(user.id, getUserIdBySessionId("12345"))
    }

    @Test
    fun createAndQueryMessagesTest() = withTestPgDatabase {
        assertEquals(emptyList(), getMessageList(beforeId = null, limit = 50).asReversed())
        assertNull(getMessageById(-1))
        val user = createUser("sender", "hash")!!
        createMessage(user.id, "你好 'quoted' <text>")
        createMessage(user.id, "second")
        val messages = getMessageList(beforeId = null, limit = 50).asReversed()
        assertEquals(2, messages.size)
        val (first, second) = messages
        assertEquals(user.id, first.userId)
        assertEquals("sender", first.username)
        assertEquals(user.passwordHash, first.passwordHash)
        assertEquals("sender", getMessageById(second.id)?.username)
        assertEquals("你好 'quoted' <text>", first.text)
        assertEquals(first, getMessageById(first.id))
        assertEquals(listOf(first, second), getMessageList(beforeId = null, limit = 50).asReversed())
    }

    @Test
    fun createMessageNotifiesIdTest() = withTestPgDatabase {
        val user = createUser("sender", "hash")!!
        exec("LISTEN chat_messages")
        coroutineScope {
            val notification = async(start = CoroutineStart.UNDISPATCHED) { waitNotification() }
            createMessage(user.id, "hello")
            val event = notification.await()
            assertEquals("chat_messages", event.channel)
            val payload = Json.decodeFromString<ChatMessageNotification>(event.message)
            assertEquals("INSERT", payload.operation)
            val message = assertIs<ChatMessageWithUserEntity>(getMessageById(payload.messageId))
            assertEquals(user.id, message.userId)
            assertEquals("hello", message.text)
        }
    }

    @Test
    fun directSqlChangesNotifyTest() = withTestPgDatabase {
        val user = createUser("sender", "hash")!!
        exec("LISTEN chat_messages")
        exec("INSERT INTO messages(user_id, text) VALUES (${user.id}, 'direct SQL')")
        val message = getMessageList(beforeId = null, limit = 50).asReversed().single()
        val inserted = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals("INSERT", inserted.operation)
        assertEquals(message.id, inserted.messageId)
        exec("UPDATE messages SET text = 'edited' WHERE id = ${message.id}")
        assertEquals("UPDATE", Json.decodeFromString<ChatMessageNotification>(waitNotification().message).operation)
        exec("DELETE FROM messages WHERE id = ${message.id}")
        val deleted = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals("DELETE", deleted.operation)
        assertEquals(message.id, deleted.messageId)
        assertNull(getMessageById(message.id))
    }

    @Test
    fun onlyOwnerCanUpdateOrDeleteMessageTest() = withTestPgDatabase {
        val owner = createUser("owner", "hash")!!
        val other = createUser("other", "hash")!!
        createMessage(owner.id, "original")
        createMessage(other.id, "keep")
        val message = getMessageList(beforeId = null, limit = 50).asReversed().first()
        assertFalse(updateMessage(other.id, message.id, "forged"))
        assertFalse(deleteMessage(other.id, message.id))
        assertEquals("original", getMessageById(message.id)?.text)
        assertTrue(updateMessage(owner.id, message.id, "edited"))
        assertEquals("edited", getMessageById(message.id)?.text)
        assertTrue(deleteMessage(owner.id, message.id))
        assertNull(getMessageById(message.id))
        assertFalse(updateMessage(owner.id, message.id, "missing"))
        assertFalse(deleteMessage(owner.id, message.id))
        assertEquals(listOf("keep"), getMessageList(beforeId = null, limit = 50).asReversed().map { it.text })
    }

    @Test
    fun messageListCursorTest() = withTestPgDatabase {
        val user = createUser("history", "hash")!!
        for (i in 1..5) createMessage(user.id, "message-$i")
        val latest = getMessageList(beforeId = null, limit = 2)
        assertEquals(listOf("message-5", "message-4"), latest.map { it.text })
        val earlier = getMessageList(beforeId = latest.last().id, limit = 2)
        assertEquals(listOf("message-3", "message-2"), earlier.map { it.text })
        val oldest = getMessageList(beforeId = earlier.last().id, limit = 2)
        assertEquals(listOf("message-1"), oldest.map { it.text })
        assertEquals(emptyList(), getMessageList(beforeId = oldest.single().id, limit = 2))
    }

    @Test
    fun roomsIsolateMessagesTest() = withTestPgDatabase {
        val defaultRoom = getRoomList().single()
        assertEquals(1L, defaultRoom.id)
        assertEquals("公共聊天室", defaultRoom.name)
        val room = createRoom("第二个房间 'quoted'")!!
        assertEquals(room, getRoomById(room.id))
        assertNull(getRoomById(-1))
        assertEquals(listOf(defaultRoom, room), getRoomList())

        val user = createUser("room-user", "hash")!!
        createMessage(user.id, "public")
        createMessage(user.id, "room-first", roomId = room.id)
        createMessage(user.id, "room-second", roomId = room.id)
        assertEquals(listOf("public"), getMessageList(null, 50).map { it.text })
        val latest = getMessageList(null, 1, room.id).single()
        assertEquals(room.id, latest.roomId)
        assertEquals("room-second", latest.text)
        assertEquals(listOf("room-first"), getMessageList(latest.id, 50, room.id).map { it.text })
        assertFalse(updateMessage(user.id, latest.id, "wrong-room"))
        assertFalse(deleteMessage(user.id, latest.id))
        assertTrue(updateMessage(user.id, latest.id, "edited", room.id))
        assertEquals("edited", getMessageById(latest.id)?.text)
        assertTrue(deleteMessage(user.id, latest.id, room.id))
    }

    @Test
    fun roomNotificationsTest() = withTestPgDatabase {
        val user = createUser("room-notify", "hash")!!
        val room = createRoom("通知测试")!!
        exec("LISTEN chat_messages")
        createMessage(user.id, "hello", room.id)
        val inserted = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals(room.id, inserted.roomId)
        assertEquals("INSERT", inserted.operation)
        assertTrue(updateMessage(user.id, inserted.messageId, "edited", room.id))
        val updated = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals(room.id, updated.roomId)
        assertEquals("UPDATE", updated.operation)
        assertTrue(deleteMessage(user.id, inserted.messageId, room.id))
        val deleted = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals(room.id, deleted.roomId)
        assertEquals(inserted.messageId, deleted.messageId)
        assertEquals("DELETE", deleted.operation)
    }

    @Test
    fun roomJoinRequestTest() = withTestPgDatabase {
        val owner = createUser("request-owner", "hash")!!
        val applicant = createUser("applicant", "hash")!!
        val other = createUser("other", "hash")!!
        val room = getUserRoomList(owner.id).single()
        assertEquals(null to CreateRoomJoinRequestError.MemberAlreadyInRoom, createRoomJoinRequest(room.id, owner.id))
        assertEquals(null to CreateRoomJoinRequestError.RoomNotExist, createRoomJoinRequest(-1, applicant.id))
        val (created, createError) = createRoomJoinRequest(room.id, applicant.id)
        assertNull(createError)
        val request = assertIs<ChatRoomJoinRequestEntity>(created)
        assertEquals(null to CreateRoomJoinRequestError.RequestAlreadyExist, createRoomJoinRequest(room.id, applicant.id))
        assertTrue(getRoomJoinRequests(other.id).isEmpty())
        assertEquals(applicant.username, getRoomJoinRequests(owner.id).single().username)
        assertFalse(approveRoomJoinRequest(request.id, other.id))
        assertFalse(rejectRoomJoinRequest(request.id, applicant.id))
        assertTrue(approveRoomJoinRequest(request.id, owner.id))
        assertTrue(isRoomMember(room.id, applicant.id))
        assertEquals("member", getRoomMembers(room.id).first { it.userId == applicant.id }.role)
        assertFalse(approveRoomJoinRequest(request.id, owner.id))
        assertTrue(getRoomJoinRequests(owner.id).isEmpty())

        val (rejectedRequest, rejectError) = createRoomJoinRequest(room.id, other.id)
        assertNull(rejectError)
        val rejected = assertIs<ChatRoomJoinRequestEntity>(rejectedRequest)
        assertTrue(rejectRoomJoinRequest(rejected.id, owner.id))
        assertFalse(isRoomMember(room.id, other.id))
        assertFalse(rejectRoomJoinRequest(rejected.id, owner.id))
        val (resubmitted, resubmitError) = createRoomJoinRequest(room.id, other.id)
        assertNull(resubmitError)
        assertIs<ChatRoomJoinRequestEntity>(resubmitted)
    }

    @Test
    fun pendingRoomJoinRequestsTest() = withTestPgDatabase {
        val owner = createUser("pending-owner", "hash")!!
        val otherOwner = createUser("other-owner", "hash")!!
        val applicant = createUser("pending-applicant", "hash")!!
        val firstRoom = getUserRoomList(owner.id).single()
        val secondRoom = getUserRoomList(otherOwner.id).single()
        exec("INSERT INTO room_members(room_id, user_id, role) VALUES (${secondRoom.id}, ${owner.id}, 'owner')")
        assertNull(createRoomJoinRequest(firstRoom.id, applicant.id).second)
        assertNull(createRoomJoinRequest(secondRoom.id, applicant.id).second)

        val pending = getRoomJoinRequests(owner.id)
        assertEquals(listOf(firstRoom.id, secondRoom.id), pending.map { it.roomId })
        assertEquals(listOf(firstRoom.name, secondRoom.name), pending.map { it.roomName })
        assertTrue(pending.all { it.userId == applicant.id && it.username == applicant.username })
        assertEquals(listOf(secondRoom.id), getRoomJoinRequests(otherOwner.id).map { it.roomId })
        assertTrue(getRoomJoinRequests(applicant.id).isEmpty())
    }

    @Test
    fun roomJoinRequestNotificationsTest() = withTestPgDatabase {
        val owner = createUser("notify-owner", "hash")!!
        val applicant = createUser("notify-applicant", "hash")!!
        val room = getUserRoomList(owner.id).single()
        exec("LISTEN room_join_requests")

        suspend fun assertNotification(operation: String, requestId: Long) {
            val notification = waitNotification()
            assertEquals("room_join_requests", notification.channel)
            val event = Json.decodeFromString<ChatRoomJoinRequestNotification>(notification.message)
            assertEquals(operation, event.operation)
            assertEquals(requestId, event.requestId)
            assertEquals(room.id, event.requestRoomId)
            assertEquals(applicant.id, event.requestUserId)
        }

        val rejected = createRoomJoinRequest(room.id, applicant.id).first!!
        assertNotification("INSERT", rejected.id)
        val request = getRoomJoinRequestById(rejected.id)!!
        assertEquals(room.name, request.roomName)
        assertEquals(applicant.username, request.username)
        assertTrue(rejectRoomJoinRequest(rejected.id, owner.id))
        assertNotification("DELETE", rejected.id)
        assertNull(getRoomJoinRequestById(rejected.id))

        val approved = createRoomJoinRequest(room.id, applicant.id).first!!
        assertNotification("INSERT", approved.id)
        assertTrue(approveRoomJoinRequest(approved.id, owner.id))
        assertNotification("DELETE", approved.id)
    }

    fun withTestPgDatabase(
        block: suspend PgConnection.() -> Unit
    ) =
        runPollEventLoop(pollerFactory) {
            val host = getEnv("POSTGRES_HOST") ?: "127.0.0.1"
            val port = getEnv("POSTGRES_PORT")?.toInt() ?: 5432
            val user = getEnv("POSTGRES_USER")  ?: error("no value found: POSTGRES_USER")
            val password = getEnv("POSTGRES_PASSWORD")  ?: error("no value found: POSTGRES_PASSWORD")
            val database = getEnv("POSTGRES_DB") ?: error("no value found: POSTGRES_DB")

            withTimeout(10.seconds) {
                val conn = openPgConnection(
                    host = host,
                    port = port,
                    user = user,
                    password = password,
                    database = database,
                )
                conn.dropAllTables()
                val result = conn.migrate(migrations)
                if (result is MigrationResult.Error) {
                    println(result)
                }
                assertIs<MigrationResult.Success>(result)

                try {
                    conn.block()
                } finally {
                    conn.dropAllTables()
                }

                conn.close()
            }
        }
}