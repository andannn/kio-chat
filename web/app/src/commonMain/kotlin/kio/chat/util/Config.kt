package kio.chat.util

object Config {
    val SECURE_SESSION_COOKIE: Boolean
        get() = kio.async.io.getEnv("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull()
            ?: !kio.async.io.getEnv("TLS_CERT").isNullOrBlank()

    const val APP_CSS = "/static/app/app.css"
    const val APP_JS = "/static/app/app.js"
    const val HTMX_WS_JS = "/static/htmx/ws.min.js"
    const val HTMX_MIN_JS = "/static/htmx/htmx.min.js"
}

class Env(
    val postgresHost: String,
    val postgresPort: Int,
    val postgresUser: String,
    val postgresPassword: String,
    val postgresDatabase: String,
    val tlsCert: String,
    val tlsKey: String,
)