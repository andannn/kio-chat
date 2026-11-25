package kio.chat.util

import kio.async.AsyncRawSink
import kio.async.AsyncRawSource

internal expect suspend fun fileSink(path: String): AsyncRawSink
internal expect suspend fun fileSource(path: String): AsyncRawSource
internal expect suspend fun removeFile(path: String)
