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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

abstract class DatabaseTest {
    abstract val pollerFactory: PollerFactory

    // ==========================================
    // 1. User & Session
    // ==========================================

    @Test
    fun userCreateAndQueryTest() = withTestPgDatabase {
        val user = createUser("userA", "pass", "NicknameA")!!
        assertEquals("userA", user.username)
        assertEquals("pass", user.passwordHash)
        assertEquals("NicknameA", user.nickname)
        assertEquals(user, getUserByUsername("userA"))

        // Duplicate username should return null
        val duplicate = createUser("userA", "otherPass")
        assertNull(duplicate)
    }

    @Test
    fun sessionLifecycleTest() = withTestPgDatabase {
        val user = createUser("sessionUser", "hash")!!
        val sessionId = "session_token_123"

        assertNull(getUserIdBySessionId(sessionId))

        createSession(user.id, sessionId)
        assertEquals(user.id, getUserIdBySessionId(sessionId))

        deleteSession(sessionId)
        assertNull(getUserIdBySessionId(sessionId))
    }

    // ==========================================
    // 2. Room & Membership
    // ==========================================

    @Test
    fun roomLifecycleAndOwnershipTest() = withTestPgDatabase {
        val owner = createUser("room-owner", "hash")!!
        val room = createRoomForUser(owner.id, "测试群组")
        assertEquals(room, getRoomById(room.id))
        assertTrue(isRoomMember(room.id, owner.id))
        assertTrue(room.id in getOwnedRoomIds(owner.id))

        // Update room name (only owner can update)
        val other = createUser("other-user", "hash")!!
        assertFalse(updateRoomName(room.id, other.id, "偷改名字"))
        assertTrue(updateRoomName(room.id, owner.id, "新群名"))
        assertEquals("新群名", getRoomById(room.id)?.name)

        // Delete room (only owner can delete)
        assertFalse(deleteRoom(room.id, other.id))
        assertTrue(deleteRoom(room.id, owner.id))
        assertNull(getRoomById(room.id))
    }

    // ==========================================
    // 3. Message Operations & Notifications
    // ==========================================

    @Test
    fun messageListCursorAndIsolationTest() = withTestPgDatabase {
        val user = createUser("history", "hash")!!
        val room = createRoom("新房间")!!

        // Default room messages
        for (i in 1..5) createMessage(user.id, "message-$i", 1)
        val latest = getMessageList(beforeId = null, limit = 2, roomId = 1)
        assertEquals(listOf("message-5", "message-4"), latest.map { it.text })
        val earlier = getMessageList(beforeId = latest.last().id, limit = 2, roomId = 1)
        assertEquals(listOf("message-3", "message-2"), earlier.map { it.text })
        val oldest = getMessageList(beforeId = earlier.last().id, limit = 2, roomId = 1)
        assertEquals(listOf("message-1"), oldest.map { it.text })
        assertEquals(emptyList(), getMessageList(beforeId = oldest.single().id, limit = 2, roomId = 1))

        // Room isolation
        createMessage(user.id, "room-msg", roomId = room.id)
        assertEquals(listOf("room-msg"), getMessageList(beforeId = null, limit = 10, roomId = room.id).map { it.text })
    }

    @Test
    fun messageUpdateDeleteAndPermissionsTest() = withTestPgDatabase {
        val owner = createUser("owner", "hash")!!
        val other = createUser("other", "hash")!!
        createMessage(owner.id, "original", 1)
        val message = getMessageList(beforeId = null, limit = 50, 1).single()

        // Non-owner cannot modify or delete
        assertFalse(updateMessage(other.id, message.id, "forged"))
        assertFalse(deleteMessage(other.id, message.id))
        assertEquals("original", getMessageById(message.id)?.text)

        // Owner can update
        assertTrue(updateMessage(owner.id, message.id, "edited"))
        assertEquals("edited", getMessageById(message.id)?.text)

        // Owner can delete
        assertTrue(deleteMessage(owner.id, message.id))
        assertNull(getMessageById(message.id))
    }

    @Test
    fun messageNotificationsTest() = withTestPgDatabase {
        val user = createUser("msg-notify-user", "hash")!!
        val room = createRoom("通知房间")!!
        exec("LISTEN chat_messages")

        // 1. Insert notification
        coroutineScope {
            val notificationTask = async(start = CoroutineStart.UNDISPATCHED) { waitNotification() }
            createMessage(user.id, "notify-content", room.id)
            val event = notificationTask.await()
            assertEquals("chat_messages", event.channel)
            val inserted = Json.decodeFromString<ChatMessageNotification>(event.message)
            assertEquals("INSERT", inserted.operation)
            assertEquals(room.id, inserted.roomId)

            val msg = assertIs<ChatMessageWithUserEntity>(getMessageById(inserted.messageId))
            assertEquals("notify-content", msg.text)

            // 2. Update notification
            val updateTask = async(start = CoroutineStart.UNDISPATCHED) { waitNotification() }
            assertTrue(updateMessage(user.id, inserted.messageId, "notify-edited", room.id))
            val updatePayload = Json.decodeFromString<ChatMessageNotification>(updateTask.await().message)
            assertEquals("UPDATE", updatePayload.operation)
            assertEquals(inserted.messageId, updatePayload.messageId)

            // 3. Delete notification
            val deleteTask = async(start = CoroutineStart.UNDISPATCHED) { waitNotification() }
            assertTrue(deleteMessage(user.id, inserted.messageId, room.id))
            val deletePayload = Json.decodeFromString<ChatMessageNotification>(deleteTask.await().message)
            assertEquals("DELETE", deletePayload.operation)
            assertEquals(inserted.messageId, deletePayload.messageId)
        }
    }

    // ==========================================
    // 4. Room Join Requests
    // ==========================================

    @Test
    fun roomJoinRequestLifecycleTest() = withTestPgDatabase {
        val owner = createUser("request-owner", "hash")!!
        val applicant = createUser("applicant", "hash")!!
        val other = createUser("other", "hash")!!
        val room = getUserRoomList(owner.id).single()

        // Error cases
        assertEquals(null to CreateRoomJoinRequestError.MemberAlreadyInRoom, createRoomJoinRequest(room.id, owner.id))
        assertEquals(null to CreateRoomJoinRequestError.RoomNotExist, createRoomJoinRequest(-1, applicant.id))

        // Create request
        val (created, createError) = createRoomJoinRequest(room.id, applicant.id)
        assertNull(createError)
        val request = assertIs<ChatRoomJoinRequestEntity>(created)
        assertEquals(null to CreateRoomJoinRequestError.RequestAlreadyExist, createRoomJoinRequest(room.id, applicant.id))

        // Verify request visibility (only room owners see it)
        assertTrue(getRoomJoinRequests(other.id).isEmpty())
        val ownerPending = getRoomJoinRequests(owner.id)
        assertEquals(1, ownerPending.size)
        assertEquals(applicant.nickname, ownerPending.single().nickname)

        // Non-owner cannot approve/reject
        assertFalse(approveRoomJoinRequest(request.id, other.id))
        assertFalse(rejectRoomJoinRequest(request.id, applicant.id))

        // Owner approves
        assertTrue(approveRoomJoinRequest(request.id, owner.id))
        assertTrue(isRoomMember(room.id, applicant.id))
        assertEquals("member", getRoomMembers(room.id).first { it.userId == applicant.id }.role)
        assertTrue(getRoomJoinRequests(owner.id).isEmpty())

        // Create, then reject another request
        val (rejectedRequest, rejectError) = createRoomJoinRequest(room.id, other.id)
        assertNull(rejectError)
        val rejected = assertIs<ChatRoomJoinRequestEntity>(rejectedRequest)
        assertTrue(rejectRoomJoinRequest(rejected.id, owner.id))
        assertFalse(isRoomMember(room.id, other.id))
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

        val request = createRoomJoinRequest(room.id, applicant.id).first!!
        assertNotification("INSERT", request.id)

        val fetched = getRoomJoinRequestById(request.id)!!
        assertEquals(room.name, fetched.roomName)
        assertEquals(applicant.nickname, fetched.nickname)

        assertTrue(approveRoomJoinRequest(request.id, owner.id))
        assertNotification("DELETE", request.id)
        assertNull(getRoomJoinRequestById(request.id))
    }

    // ==========================================
    // Test Infrastructure
    // ==========================================

    fun withTestPgDatabase(
        block: suspend PgConnection.() -> Unit
    ) = runPollEventLoop(pollerFactory) {
        val host = getEnv("POSTGRES_HOST") ?: "127.0.0.1"
        val port = getEnv("POSTGRES_PORT")?.toInt() ?: 5432
        val user = getEnv("POSTGRES_USER") ?: error("no value found: POSTGRES_USER")
        val password = getEnv("POSTGRES_PASSWORD") ?: error("no value found: POSTGRES_PASSWORD")
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
