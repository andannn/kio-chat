package kio.chat.db

import kio.async.io.getEnv
import kio.async.poller.select.Select
import kio.async.runPollEventLoop
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kio.postgres.conn.query
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate
import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class MigrationTest {

    @Test
    fun migrateFromVer3To4() = withTestPgDatabase {
        assertIs<MigrationResult.Success>(migrate(migrations, targetVersion = 3))
        exec("INSERT INTO users(username, password_hash) VALUES ('existing-user', 'hash')")
        exec("INSERT INTO messages(user_id, text) SELECT id, 'existing-message' FROM users")

        assertIs<MigrationResult.Success>(migrate(migrations, targetVersion = 4))

        @Serializable
        data class RoomEntity(
            val id: PgInt8,
            val name: PgText,
        )
        val room = query<RoomEntity>("SELECT id, name FROM rooms").toList().single()
        assertEquals(1L, room.id)
        assertEquals("公共聊天室", room.name)

        @Serializable
        data class MessageEntity(
            @SerialName("room_id") val roomId: PgInt8,
            val text: PgText,
        )
        val message = query<MessageEntity>("SELECT room_id, text FROM messages").toList().single()
        assertEquals(room.id, message.roomId)
        assertEquals("existing-message", message.text)

        // Existing callers can still insert messages without specifying a room.
        exec("INSERT INTO messages(user_id, text) SELECT id, 'new-message' FROM users")
        val messages = query<MessageEntity>("SELECT room_id, text FROM messages ORDER BY id").toList()
        assertEquals(listOf("existing-message", "new-message"), messages.map { it.text })
        assertEquals(listOf(room.id, room.id), messages.map { it.roomId })
    }

    fun withTestPgDatabase(
        block: suspend PgConnection.() -> Unit
    ) = runPollEventLoop(Select) {
            val host = getEnv("POSTGRES_HOST") ?: "127.0.0.1"
            val port = getEnv("POSTGRES_PORT")?.toInt() ?: 5432
            val user = getEnv("POSTGRES_USER")  ?: error("no value found: POSTGRES_USER")
            val password = getEnv("POSTGRES_PASSWORD")  ?: error("no value found: POSTGRES_PASSWORD")
            val database = getEnv("POSTGRES_DB") ?: error("no value found: POSTGRES_DB")

            withTimeout(1.seconds) {
                val conn = openPgConnection(
                    host = host,
                    port = port,
                    user = user,
                    password = password,
                    database = database,
                )

                try {
                    conn.block()
                } finally {
                    conn.dropAllTables()
                }

                conn.close()
            }
        }
}