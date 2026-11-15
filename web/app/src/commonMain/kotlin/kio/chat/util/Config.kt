package kio.chat.util

import kio.chat.domain.AppVersion
import kio.chat.domain.AppBuildVersion

object Config {
    val SECURE_SESSION_COOKIE: Boolean
        get() = kio.async.io.getEnv("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull()
            ?: !kio.async.io.getEnv("TLS_CERT").isNullOrBlank()

    val APK_DOWNLOAD_DIRECTORY: String
        get() = kio.async.io.getEnv("APK_DOWNLOAD_DIRECTORY") ?: "/opt/kchat/downloads"

    const val ANDROID_APK_URL = "/downloads/kio-chat.apk"
    val ANDROID_APP_VERSION = AppVersion(
        versionCode = AppBuildVersion.VERSION_CODE,
        versionName = AppBuildVersion.VERSION_NAME,
    )
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