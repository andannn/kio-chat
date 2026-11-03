package kio.chat.page

import kio.http.CallContext
import kio.http.respondHtml
import kio.chat.util.Config
import kotlinx.html.*

fun TagConsumer<*>.kNoteHead(title: String) {
    head {
        script(src = Config.HTMX_MIN_JS) { }
        script(src = Config.APP_JS) { }
        link(rel = "stylesheet", href = Config.APP_CSS)
        title { +title }
    }
}

suspend fun CallContext.chatMainPage(noteId: Long? = null) {
    respondHtml {
        kNoteHead("Chat")

        body(classes = "app") {

        }
    }
}
