package me.andannn.kio.chat

import android.util.Log
import android.webkit.CookieManager
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.renderSetCookieHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import kio.chat.domain.Room
import kio.chat.domain.MessagePage
import kio.chat.domain.MessagePack
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

private const val TAG = "ChatService"

sealed interface ApiError {
    data class ServerError(val message: String, val t: Throwable? = null) : ApiError
    data object AuthenticationError : ApiError
}

interface ChatService {
    suspend fun register(userName: String, password: String, confirmPassword: String): ApiResult<Unit>
    suspend fun login(userName: String, password: String): ApiResult<Unit>
    suspend fun sendMessage(roomId: Long, content: String): ApiResult<Unit>
    suspend fun updateMessage(roomId: Long, messageId: Long, content: String): ApiResult<Unit>
    suspend fun deleteMessage(roomId: Long, messageId: Long): ApiResult<Unit>
    suspend fun getMessageHistory(roomId: Long, beforeId: Long): ApiResult<MessagePage>
    suspend fun logout(): ApiResult<Unit>
    suspend fun getRoomList(): ApiResult<List<Room>>
    suspend fun checkLogin(): ApiResult<Unit>
    suspend fun observeMessageEvents(roomId: Long, onEvent: suspend (MessagePack) -> Unit): ApiError?
}

data class ApiResult<T>(val value: T?, val error: ApiError?)

class ChatServiceImpl : ChatService {
    private val httpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                }
            }

            expectSuccess = true
            followRedirects = false

            defaultRequest {
                url("https://133.117.75.202:7879/")
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }

            install(WebSockets)

            install(HttpCookies) {
                storage = CustomCookiesStorage()
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            HttpResponseValidator {
                handleResponseExceptionWithRequest { cause, _ ->
                    val response = (cause as? ResponseException)?.response
                        ?: return@handleResponseExceptionWithRequest

                    if (
                        response.status == HttpStatusCode.Found &&
                        response.headers[HttpHeaders.Location] == "/login"
                    ) {
                        throw LoginRequiredException()
                    }
                }
            }

            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            Log.d("HTTP-SERVICE", "HttpLogInfo: $message")
                        }
                    }
                level = LogLevel.ALL
            }
        }

    override suspend fun register(userName: String, password: String, confirmPassword: String): ApiResult<Unit> = tryGetResult {
        httpClient.submitForm(
            url = "/register",
            formParameters = Parameters.build {
                append("username", userName)
                append("password", password)
                append("confirmPassword", confirmPassword)
            },
        ) {
            contentType(ContentType.Application.FormUrlEncoded)
        }
    }

    override suspend fun login(userName: String, password: String): ApiResult<Unit> {
        return tryGetResult {
            httpClient.submitForm(
                url = "/login",
                formParameters = Parameters.build {
                    append("username", userName)
                    append("password", password)
                },
            ) {
                contentType(ContentType.Application.FormUrlEncoded)
            }
        }
    }

    override suspend fun sendMessage(roomId: Long, content: String): ApiResult<Unit> = tryGetResult {
        httpClient.submitForm(
            url = "/rooms/$roomId/messages",
            formParameters = Parameters.build { append("content", content) },
        ) {
            contentType(ContentType.Application.FormUrlEncoded)
        }
    }

    override suspend fun updateMessage(roomId: Long, messageId: Long, content: String): ApiResult<Unit> = tryGetResult {
        httpClient.patch("/rooms/$roomId/messages/$messageId") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(FormDataContent(Parameters.build { append("content", content) }))
        }
    }

    override suspend fun deleteMessage(roomId: Long, messageId: Long): ApiResult<Unit> = tryGetResult {
        httpClient.delete("/rooms/$roomId/messages/$messageId")
    }

    override suspend fun getMessageHistory(roomId: Long, beforeId: Long): ApiResult<MessagePage> = tryGetResult {
        httpClient.get("/rooms/$roomId/messages") {
            parameter("beforeId", beforeId)
        }.body<MessagePage>()
    }

    override suspend fun logout(): ApiResult<Unit> = tryGetResult {
        httpClient.post("/logout")
    }

    override suspend fun getRoomList(): ApiResult<List<Room>> = tryGetResult {
        httpClient.get("/rooms").body<List<Room>>()
    }

    override suspend fun checkLogin(): ApiResult<Unit> = tryGetResult {
        httpClient.get("/")
    }

    override suspend fun observeMessageEvents(
        roomId: Long,
        onEvent: suspend (MessagePack) -> Unit,
    ): ApiError? = tryGetResult {
        httpClient.webSocket(urlString = "wss://133.117.75.202:7879/rooms/$roomId/messages/events?format=json") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    onEvent(Json.decodeFromString<MessagePack>(frame.readText()))
                }
            }
        }
    }.error
}

private suspend inline fun <reified T> tryGetResult(block: () -> T): ApiResult<T> =
    try {
        ApiResult(block(), null)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: LoginRequiredException) {
        ApiResult(null, ApiError.AuthenticationError)
    } catch (e: ResponseException) {
        ApiResult(null, ApiError.ServerError(e.response.bodyAsText(), e))
    } catch (e: Throwable) {
        Log.e(TAG, "Unexpected error: $e")
        ApiResult(null, ApiError.ServerError(e.message.toString(), e))
    }

private class LoginRequiredException : Exception("Login required")
private class CustomCookiesStorage : CookiesStorage {

    private val cookieManager = CookieManager.getInstance()

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val cookieUrl = requestUrl.toString()
            .replaceFirst("wss://", "https://")
        val cookieString = cookieManager.getCookie(cookieUrl) ?: return emptyList()
        return cookieString.split(";")
            .mapNotNull { item ->
                val parts = item.trim().split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null

                Cookie(
                    name = parts[0].trim(),
                    value = parts[1].trim()
                )
            }
    }

    override suspend fun addCookie(
        requestUrl: Url,
        cookie: Cookie
    ) {
        suspendCancellableCoroutine { continuation ->
            cookieManager.setCookie(
                requestUrl.toString(),
                renderSetCookieHeader(cookie)
            ) {
                if (continuation.isActive) {
                    cookieManager.flush()
                    continuation.resume(Unit)
                }
            }
        }
    }

    override fun close() {
        cookieManager.flush()
    }
}