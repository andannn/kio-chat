import kio.async.io.getEnv
import kio.chat.db.migrations
import kio.postgres.conn.openPgConnection
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate

suspend fun doMigration(): Boolean {
    val conn = openPgConnection(
        getEnv("POSTGRES_HOST") ?: "127.0.0.1",
        getEnv("POSTGRES_PORT")?.toIntOrNull() ?: 5432,
        getEnv("POSTGRES_USER") ?: "postgres",
        getEnv("POSTGRES_PASSWORD") ?: "",
        getEnv("POSTGRES_DB") ?: "kio_chat",
    )
    val result = try { conn.migrate(migrations) } finally { conn.close() }
    if (result is MigrationResult.Error) println("Migration failed: $result")
    return result is MigrationResult.Success
}
