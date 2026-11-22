import java.io.File

internal actual suspend fun recordSchemaTo(
    path: String,
    host: String,
    port: Int,
    user: String,
    password: String,
    database: String,
) {
    val targetFile = File(path)
    targetFile.parentFile?.mkdirs()

    val processBuilder = ProcessBuilder(
        "pg_dump",
        "--host", host,
        "--port", port.toString(),
        "--username", user,
        "--dbname", database,
        "--schema-only",
        "--no-owner",
        "--no-privileges",
        "--exclude-table=schema_migrations",
        "--file", targetFile.absolutePath,
    )

    if (password.isNotEmpty()) {
        processBuilder.environment()["PGPASSWORD"] = password
    }
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        val errorMsg = process.inputStream.bufferedReader().readText()
        System.err.println("pg_dump failed (exit code $exitCode): $errorMsg")
    } else {
        println("Recorded schema to: ${targetFile.absolutePath}")
    }
}
