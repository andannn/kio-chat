package me.andannn.kio.chat

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

interface ChatService {
    suspend fun smock(): String
}

class ChatServiceImpl: ChatService {
    private val httpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                }
            }

            expectSuccess = true

            defaultRequest {
                url("https://133.117.75.202:7879/")
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(Logging) {
                logger =
                    object : Logger {
                        override fun log(message: String) {
                            Log.d("HTTP-SERVICE", "HttpLogInfo: $message")
                        }
                    }
                level = LogLevel.HEADERS
            }
        }

    override suspend fun smock(): String {
        return tryGetResult {
            httpClient.get("/login") {}.body<String>()
        }
    }
}

private inline fun <reified T> tryGetResult(block: () -> T): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: ResponseException) {
        throw ServerResponseException(e.response.status.value, e.message.toString())
    } catch (e: Throwable) {
        throw ServerException(e.message.toString())
    }

open class ServerException(
    override val message: String,
) : IllegalStateException(message)

/**
 * Represents an error response with status code.
 */
open class ServerResponseException(
    val statusCode: Int,
    override val message: String,
) : ServerException(message)
