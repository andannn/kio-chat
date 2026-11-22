import kio.async.io.getEnv
import kio.chat.db.migrations
import kio.postgres.conn.openPgConnection
import kio.postgres.migration.MigrationResult
import kio.postgres.migration.migrate

suspend fun doMigration(): Boolean {
    val host = getEnv("POSTGRES_HOST") ?: "127.0.0.1"
    val port = getEnv("POSTGRES_PORT")?.toIntOrNull() ?: 5432
    val user = getEnv("POSTGRES_USER") ?: "postgres"
    val password = getEnv("POSTGRES_PASSWORD") ?: ""
    val database = getEnv("POSTGRES_DB") ?: "kio_chat"

    val conn = openPgConnection(
        host,
        port,
        user,
        password,
        database,
    )
    val result = try {
        conn.migrate(migrations) { migrated ->
            recordSchemaTo(
                path = "./schema/schema_v${migrated.version}_${migrated.name.replace(' ', '_')}.sql",
                host = host,
                port = port,
                user = user,
                password = password,
                database = database,
            )
        }
    } finally {
        conn.close()
    }
    if (result is MigrationResult.Error) println("Migration failed: $result")
    return result is MigrationResult.Success
}

internal expect suspend fun recordSchemaTo(
    path: String,
    host: String,
    port: Int,
    user: String,
    password: String,
    database: String,
)