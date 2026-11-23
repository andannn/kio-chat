package kio.chat.util

import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kio.chat.components.voiceParticipantEvent
import kio.chat.domain.UserEvent
import kio.http.CallContext
import kio.http.respondHtml
import kio.http.respondText
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json

suspend fun CallContext.respondByContentType(
    status: HttpStatusCode,
    configHeaders: HeadersBuilder.() -> Unit = {},
    configTrailers: HeadersBuilder.() -> Unit = {},
    buildJson: () -> String,
    buildHtml: TagConsumer<*>.() -> Unit,
) {
    val accept = requestHeaders[HttpHeaders.Accept]
    val contentType = accept?.let { ContentType.parse(it) }
    if (contentType == null || ContentType.Text.Html.match(contentType)) {
        respondHtml(
            status = status,
            configHeaders = configHeaders,
            configTrailers = configTrailers,
            block = buildHtml,
        )
    } else {
        respondText(
            text = buildJson(),
            contentType = ContentType.Application.Json,
            status = status,
            configHeaders = configHeaders,
            configTrailers = configTrailers,
        )
    }
}

suspend fun CallContext.respondError(message: String) {
    respondByContentType(
        status = HttpStatusCode.BadRequest,
        buildJson = { message },
        buildHtml = { div { +message } },
    )
}

inline fun <reified T> decodeJson(body: String): T = Json.decodeFromString(body)
inline fun <reified T> json(value: T) = Json.encodeToString(value)
inline fun html(value: TagConsumer<*>.() -> Unit) = createHTML().apply { value() }.finalize()