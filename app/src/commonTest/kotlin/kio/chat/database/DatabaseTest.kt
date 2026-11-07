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
        assertEquals(emptyList(), getMessageList())
        assertNull(getMessageById(-1))
        val user = createUser("sender", "hash")!!
        createMessage(user.id, "你好 'quoted' <text>")
        createMessage(user.id, "second")
        val messages = getMessageList()
        assertEquals(2, messages.size)
        val (first, second) = messages
        assertEquals(user.id, first.userId)
        assertEquals("sender", first.username)
        assertEquals(user.passwordHash, first.passwordHash)
        assertEquals("sender", getMessageById(second.id)?.username)
        assertEquals("你好 'quoted' <text>", first.text)
        assertEquals(first, getMessageById(first.id))
        assertEquals(listOf(first, second), getMessageList())
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
        exec("insert into messages(user_id, text) values (${user.id}, 'direct SQL')")
        val message = getMessageList().single()
        val inserted = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals("INSERT", inserted.operation)
        assertEquals(message.id, inserted.messageId)
        exec("update messages set text = 'edited' where id = ${message.id}")
        assertEquals("UPDATE", Json.decodeFromString<ChatMessageNotification>(waitNotification().message).operation)
        exec("delete from messages where id = ${message.id}")
        val deleted = Json.decodeFromString<ChatMessageNotification>(waitNotification().message)
        assertEquals("DELETE", deleted.operation)
        assertEquals(message.id, deleted.messageId)
        assertNull(getMessageById(message.id))
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