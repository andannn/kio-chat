package kio.chat.util

import kio.async.AsyncSink
import kio.async.AsyncSource
import kio.async.SuspendIo

expect suspend fun SuspendIo.compressImageSourceToWebp(
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit,
): Result<Unit>

expect suspend fun SuspendIo.transcodeAudioSourceToM4a(
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit,
): Result<Unit>