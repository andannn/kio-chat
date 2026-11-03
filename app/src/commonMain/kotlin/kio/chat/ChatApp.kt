package kio.chat

import io.ktor.http.HttpHeaders
import kio.async.io.ServerSocket
import kio.async.io.getEnv
import kio.chat.util.Config
import kio.http.*
import kio.chat.util.Env
import kio.postgres.conn.PgConnectionPool
import kio.postgres.conn.openPgConnection
import kio.tls.pem
import kio.tls.withServerTls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.title
import kotlin.uuid.Uuid

private val DATA_SOURCE = DataSourceType.MOCK

suspend fun CoroutineScope.chatApp(serverSocket: ServerSocket) {
    val env = loadEnv()

    setupServer(dataSource = DATA_SOURCE, serverSocket, env) {
        inject(
            CallId { Uuid.random().toString() },
            LoggerInterceptor()
        ) {
            route("/") {
                get { call ->
                    call.respondHtml {
                        kNoteHead("Chat")
                        div { +"Hello world" }
                    }
                }
            }

            websocket("/test") { ctx ->
                for (a in ctx.incoming) {
                    println("a $a")
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

fun TagConsumer<*>.kNoteHead(title: String) {
    head {
        script(src = Config.HTMX_MIN_JS) { }
        script(src = Config.APP_JS) { }
        link(rel = "stylesheet", href = Config.APP_CSS)
        title { +title }
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
