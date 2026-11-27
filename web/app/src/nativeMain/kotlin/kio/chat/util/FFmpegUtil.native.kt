package kio.chat.util

import kio.async.*
import kio.async.io.asyncRawSink
import kio.async.io.asyncRawSource
import kio.async.io.exitStatus
import kio.async.io.posixSpawn
import kotlinx.cinterop.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual suspend fun SuspendIo.compressImageSourceToWebp(
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit,
): Result<Unit> {
    val args = listOf(
        "-v", "error",            // 只输出致命错误日志，避免污染管道
        "-nostdin",               // 避免交互式控制台挂起
        "-i", "pipe:0",           // 从标准输入读取待清洗图片流 (stdin)
        "-map_metadata", "-1",    // 清洗脱敏：彻底剥离 GPS、相机型号等全部 EXIF 隐私
        "-frames:v", "1",         // 只取第一帧，防止多帧/Gainmap/缩略图 JPEG 触发 image2 报错
        "-vcodec", "libwebp",     // 转码为现代 WebP 格式
        "-compression_level", "4",// 压缩率与 CPU 开销的黄金平衡点 (0-6)
        "-q:v", "75",             // 视觉无损质量级别 (8MB -> ~200KB)
        "-vf", "scale='min(2048,iw)':'min(2048,ih)':force_original_aspect_ratio=decrease", // 限制最大边不超 2048px，防像素爆炸
        "-f", "image2",           // 输出到 pipe 时必须显式声明封装格式为单张图片流
        "pipe:1"                  // 从标准输出写出 WebP 字节流 (stdout)
    )
    return runFfmpegPipeCommand(args, doSendInput, doTransferData)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun SuspendIo.transcodeAudioSourceToM4a(
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit,
): Result<Unit> {
    val args = listOf(
        "-v", "error",            // 只输出致命错误日志，避免污染管道
        "-nostdin",               // 避免交互式控制台挂起
        "-i", "pipe:0",           // 从标准输入读取待清洗音频流 (stdin)
        "-map_metadata", "-1",    // 清洗脱敏：剥离设备与位置等元数据
        "-vn",                    // 忽略视频流或内嵌封面
        "-c:a", "aac",            // 转码为 AAC 以兼容 Android / Web / Safari
        "-b:a", "48k",            // 语音清晰且体积小
        "-ac", "1",               // 单声道
        "-ar", "24000",           // 24kHz 采样率
        "-movflags", "+frag_keyframe+empty_moov", // 支持向不可 seek 的 stdout pipe 流式写出 MP4/M4A
        "-f", "mp4",              // 输出 MP4/M4A 容器
        "pipe:1"                  // 从标准输出写出 M4A 字节流 (stdout)
    )
    return runFfmpegPipeCommand(args, doSendInput, doTransferData)
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun SuspendIo.runFfmpegPipeCommand(
    args: List<String>,
    doSendInput: suspend (AsyncSink) -> Unit,
    doTransferData: suspend (AsyncSource) -> Unit,
): Result<Unit> = coroutineScope {
    prepareFfmpegCommand(args).mapCatching { (pid, sink, source) ->
        val writeJob = launch {
            try {
                doSendInput(sink)
                sink.flush()
            } catch (_: IOException) {
                // ffmpeg may close stdin early (e.g., with -frames:v 1 on multi-frame inputs or on error);
                // rely on waitid exitStatus below to report ffmpeg failures.
            } finally {
                runCatching { sink.close() }
            }
        }

        try {
            doTransferData(source)
        } catch (e: Throwable) {
            writeJob.cancel()
            throw e
        } finally {
            source.close()
        }

        writeJob.join()

        memScoped {
            val info = alloc<siginfo_t>()

            val rc = waitid(idtype_t.P_PID, pid.toUInt(), info.ptr, WEXITED)
            if (rc != 0) throw IOException("waitid failed: ${strerror(rc)?.toKString()}")
            if (info.exitStatus != 0) throw IOException("ffmpeg exited with non-zero status: ${info.exitStatus}")
        }
    }
}

private data class FfmpegCommand(
    val pid: Int,
    val sink: AsyncSink,
    val source: AsyncSource,
)

private suspend fun SuspendIo.prepareFfmpegCommand(args: List<String>): Result<FfmpegCommand> {
    val (stdinRead, stdinWrite) = openPipe()
    val (stdoutRead, stdoutWrite) = openPipe()

    val pid = posixSpawn("ffmpeg", args) {
        dup2(stdinRead, STDIN_FILENO)
        dup2(stdoutWrite, STDOUT_FILENO)
        close(stdinWrite)
        close(stdoutRead)
    }

    close(stdinRead)
    close(stdoutWrite)

    if (pid < 0) {
        close(stdinWrite)
        close(stdoutRead)

        return Result.failure(IOException("spawn FFmpeg command failed."))
    }

    val sink = asyncRawSink(stdinWrite).buffered()
    val source = asyncRawSource(stdoutRead).buffered()

    return Result.success(FfmpegCommand(pid, sink, source))
}
@OptIn(ExperimentalForeignApi::class)
private suspend fun SuspendIo.openPipe(): Pair<Int, Int> = memScoped {
    val fds = allocArray<IntVar>(2)
    check(pipe(fds) == 0)
    return@memScoped fds[0] to fds[1]
}