package kio.chat.db

import kio.async.io.getEnv
import kio.async.poller.select.Select
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kio.async.runPollEventLoop
import kio.postgres.conn.query
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate
import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MigrationTest {

    @Test
    fun migrateFromEmptyToVer1() = withTestPgDatabase {
        assertIs<MigrationResult.Success>(migrate(migrations, targetVersion = 1))
        exec("insert into users(username, password_hash) values ('userA', 'pass')")
        @Serializable
        data class ChatUserEntity(
            @SerialName("id") val id: PgInt8,
            @SerialName("username") val username: PgText,
            @SerialName("password_hash") val passwordHash: PgText,
            @SerialName("create_at") val createAt: PgTimestampTz,
        )
        val user = query<ChatUserEntity>("select * from users").toList().single()
        assertEquals("userA", user.username)
        assertEquals("pass", user.passwordHash)
        exec("insert into sessions(id, user_id) values ('test-session', ${user.id})")
        @Serializable
        data class ChatSessionEntity(
            @SerialName("id") val id: PgText,
            @SerialName("user_id") val userId: PgInt8,
            @SerialName("create_at") val createAt: PgTimestampTz,
        )
        val session = query<ChatSessionEntity>("select * from sessions").toList().single()
        assertEquals("test-session", session.id)
        assertEquals(user.id, session.userId)
    }

    @Test
    fun migrateAgain() = withTestPgDatabase {
        assertIs<MigrationResult.Success>(migrate(migrations))
        assertEquals(emptyList(), assertIs<MigrationResult.Success>(migrate(migrations)).migrated)
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