package kio.chat

import io.ktor.http.HttpHeaders
import kio.async.io.ServerSocket
import kio.async.io.getEnv
import kio.http.*
import kio.chat.util.Env
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.openPgConnection
import kio.tls.pem
import kio.tls.withServerTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

private val DATA_SOURCE = DataSourceType.PRODUCTION

suspend fun CoroutineScope.chatApp(serverSocket: ServerSocket) {
    val env = loadEnv()

    setupServer(dataSource = DATA_SOURCE, serverSocket, env) {
        inject(
            CallId { Uuid.random().toString() },
            LoggerInterceptor()
        ) {
            staticResource(
                remotePath = "/",
                basePackage = "resource",
                configHeaders = {
                    append(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
                }
            )
        }
    }
}

private enum class DataSourceType {
    MOCK,
    LOCAL_DB,
    PRODUCTION
}

private suspend fun CoroutineScope.setupServer(
    dataSource: DataSourceType,
    serverSocket: ServerSocket, env: Env,
    block: suspend Route.() -> Unit
) {
    when (dataSource) {
        DataSourceType.MOCK -> {
            httpServer(
                serverSocket = serverSocket,
            ) {
                block()
            }
        }

        DataSourceType.LOCAL_DB -> {
            httpServer(serverSocket = serverSocket) {
                block()
            }
        }

        DataSourceType.PRODUCTION -> {
            httpServer(
                serverSocket = serverSocket,
                loggingBackEnd = AsyncLoggingBackEnd(this, enableTrace = true),
                connectionWrapper = {
                    withServerTls(
                        env.tlsCert.pem,
                        env.tlsKey.pem,
                        supportAlpnProtocols = listOf("h2", "http/1.1")
                    )
                },
            ) {
                block()
            }
        }
    }
}

private fun loadEnv() = Env(
    postgresHost = getEnv("POSTGRES_HOST") ?: "127.0.0.1",
    postgresPort = getEnv("POSTGRES_PORT")?.toIntOrNull() ?: 5432,
    postgresUser = getEnv("POSTGRES_USER") ?: "postgres",
    postgresPassword = getEnv("POSTGRES_PASSWORD") ?: "",
    postgresDatabase = getEnv("POSTGRES_DB") ?: "knote",
    tlsCert = getEnv("TLS_CERT") ?: "",
    tlsKey = getEnv("TLS_KEY") ?: "",
)

private fun createPgPool(env: Env): PgConnectionPool {
    return PgConnectionPool(3) {
        openPgConnection(
            env.postgresHost,
            env.postgresPort,
            env.postgresUser,
            env.postgresPassword,
            env.postgresDatabase,
        )
    }
}

private fun LoggerInterceptor(): CallInterceptor = CallInterceptor { context, proceed ->
    val newLogger = currentLoggingBackend().newLogger("Call", mapOf("CallId" to currentCallId()))
    withContext(CoroutineLogger(newLogger)) {
        proceed(context)
    }
}
