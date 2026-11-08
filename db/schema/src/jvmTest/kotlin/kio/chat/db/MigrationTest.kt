package kio.chat.db

import kio.async.io.getEnv
import kio.async.poller.select.Select
import kio.async.runPollEventLoop
import kio.postgres.conn.PgConnection
import kio.postgres.conn.openPgConnection
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class MigrationTest {

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