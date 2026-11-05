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
import kotlinx.coroutines.withTimeout
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

    fun withTestPgDatabase(
        block: suspend PgConnection.() -> Unit
    ) =
        runPollEventLoop(pollerFactory) {
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