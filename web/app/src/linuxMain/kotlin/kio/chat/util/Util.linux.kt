package kio.chat.util

import kio.async.AsyncRawSink
import kio.async.AsyncRawSource
import kio.async.SuspendIo
import kio.async.io.asyncRawSink
import kio.async.io.asyncRawSource
import kio.async.poller
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.io.IOException
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun fileSink(path: String): AsyncRawSink {
    val io = currentCoroutineContext().poller.io
    val parent = path.substringBeforeLast('/', "")
    if (parent.isNotEmpty()) {
        io.mkdirP(parent)
    }

    val fd = io.open(path, O_WRONLY or O_CREAT or O_TRUNC or O_CLOEXEC, 0x1A4.toUInt())

    if (fd < 0) {
        throw IOException("open failed for $path: " + (strerror(fd)?.toKString() ?: "errno=$fd"))
    }

    return io.asyncRawSink(fd)
}

private suspend fun SuspendIo.mkdirP(path: String) {
    var index = 0
    while (index < path.length) {
        val nextSlash = path.indexOf('/', index)
        val subPath = if (nextSlash == -1) path else path.substring(0, nextSlash)
        if (subPath.isNotEmpty()) {
            mkdir(subPath, 0x1ED.toUInt()) // 0755
        }
        if (nextSlash == -1) break
        index = nextSlash + 1
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun removeFile(path: String) {
    val io = currentCoroutineContext().poller.io
    val result = io.unlink(path, 0)

    if (result < 0) {
        val errorCode = -result
        val message = strerror(errorCode)?.toKString()
            ?: "errno=$errorCode"

        throw IOException("unlink failed for $path: $message")
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun fileSource(path: String): AsyncRawSource {
    val io = currentCoroutineContext().poller.io
    val fd = io.open(path, O_RDONLY or O_CLOEXEC, 0x1A4.toUInt())
    if (fd < 0) {
        throw IOException("open failed for $path: " + (strerror(-fd)?.toKString() ?: "errno=${-fd}"))
    }

    return io.asyncRawSource(fd)
}