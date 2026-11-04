package kio.chat

import io.ktor.http.*
import kio.async.io.ServerSocket
import kio.async.io.getEnv
import kio.chat.domain.MockRepositoryImpl
import kio.chat.domain.Repository
import kio.chat.page.chatHead
import kio.chat.util.Env
import kio.http.*
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.openPgConnection
import kio.tls.pem
import kio.tls.withServerTls
import kio.websocket.WebSocketEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.html.div
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private val DATA_SOURCE = DataSourceType.MOCK

suspend fun CoroutineScope.chatApp(serverSocket: ServerSocket) {
    val env = loadEnv()

    val repo: Repository = MockRepositoryImpl()
    setupServer(dataSource = DATA_SOURCE, serverSocket, env) {
        context(repo) {
            inject(
                CallId { Uuid.random().toString() },
                LoggerInterceptor()
            ) {
                chatLoginRoutes()

                inject(AuthSession()) {
                    route("/") {
                        get { call ->
                            call.respondHtml {
                                chatHead("Chat", includeAppScript = true)
                                div { +"Hello world" }
                            }
                        }
                    }

                    websocket("/test") { ctx ->
                        launch {
                            for (msg in ctx.incoming) {
                                when (msg) {
                                    is WebSocketEvent.Binary -> Unit
                                    is WebSocketEvent.Text -> {
                                        repo.recordMessage(requireSession().userId, msg.text)
                                    }
                                }
                            }
                        }

                        launch {
                            while (true) {
                                delay(1.seconds)
                                ctx.outgoing.send(WebSocketEvent.Text("Hello i an ${requireSession().userId}"))
                            }
                        }
                    }
                }

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
