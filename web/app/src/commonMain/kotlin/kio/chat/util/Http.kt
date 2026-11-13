package kio.chat.util

import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kio.http.CallContext
import kio.http.respondHtml
import kio.http.respondText
import kotlinx.html.TagConsumer

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
