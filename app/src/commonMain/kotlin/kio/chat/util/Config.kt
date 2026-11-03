package kio.chat.util

object Config {
    const val APP_CSS = "/static/app/app.css"
    const val APP_JS = "/static/app/app.js"
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