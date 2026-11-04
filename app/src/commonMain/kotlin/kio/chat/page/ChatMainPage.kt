package kio.chat.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.util.Config
import kotlinx.html.*

fun TagConsumer<*>.chatHead(title: String, includeAppScript: Boolean = false) {
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        script(src = Config.HTMX_MIN_JS) { }
        if (includeAppScript) script(src = Config.APP_JS) { }
        link(rel = "stylesheet", href = Config.APP_CSS)
        title { +title }
    }
}

suspend fun CallContext.chatMainPage(noteId: Long? = null) {
    respondHtml {
        chatHead("Chat")

        body(classes = "app") {

        }
    }
}
