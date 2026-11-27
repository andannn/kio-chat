package me.andannn.kio.chat.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.andannn.kio.chat.BuildConfig
import timber.log.Timber
import java.io.File

data class RecordedVoice(
    val bytes: ByteArray,
    val durationMs: Long,
)

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun start(): Boolean {
        if (recorder != null) return false
        val file = File(context.cacheDir, "voice_rec_${System.currentTimeMillis()}.m4a")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(24000)
                setAudioEncodingBitRate(48000)
                setAudioChannels(1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = instance
            outputFile = file
            startTimeMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start VoiceRecorder")
            runCatching { instance.release() }
            file.delete()
            false
        }
    }

    fun stop(): RecordedVoice? {
        val current = recorder ?: return null
        val file = outputFile
        val durationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
        recorder = null
        outputFile = null

        return try {
            current.stop()
            current.release()
            val bytes = file?.takeIf { it.exists() }?.readBytes()
            if (bytes == null || bytes.isEmpty()) null else RecordedVoice(bytes, durationMs)
        } catch (e: Exception) {
            Timber.w(e, "Failed to stop VoiceRecorder cleanly")
            runCatching { current.release() }
            null
        } finally {
            file?.delete()
        }
    }

    fun cancel() {
        val current = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        file?.delete()
    }
}

data class VoicePlaybackState(
    val activeUrl: String? = null,
    val loadingUrl: String? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
)

class VoiceMessagePlayer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    fun toggle(url: String, fallbackDurationMs: Long) {
        val current = _state.value
        if (current.loadingUrl == url) return

        if (current.activeUrl == url && player != null) {
            val mp = player ?: return
            if (mp.isPlaying) {
                mp.pause()
                progressJob?.cancel()
                _state.value = _state.value.copy(isPlaying = false)
            } else {
                mp.start()
                _state.value = _state.value.copy(isPlaying = true)
                startProgressLoop()
            }
            return
        }

        stop()
        _state.value = VoicePlaybackState(loadingUrl = url, durationMs = fallbackDurationMs)

        val fullUrl = "${BuildConfig.BASE_URL}/${url.trimStart('/')}"
        val cookie = CookieManager.getInstance().getCookie(fullUrl)
        val headers = if (!cookie.isNullOrBlank()) mapOf("Cookie" to cookie) else emptyMap()

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(context, Uri.parse(fullUrl), headers)
            mp.setOnPreparedListener { preparedPlayer ->
                if (player !== preparedPlayer) return@setOnPreparedListener
                val totalDurationMs = preparedPlayer.duration.takeIf { it > 0 }?.toLong()
                    ?: fallbackDurationMs
                preparedPlayer.start()
                _state.value = VoicePlaybackState(
                    activeUrl = url,
                    isPlaying = true,
                    progress = 0f,
                    currentPositionMs = 0L,
                    durationMs = totalDurationMs,
                )
                startProgressLoop()
            }
            mp.setOnCompletionListener {
                if (player === mp) stop()
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) {
                    stop()
                    onError("语音播放失败。")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Timber.e(e, "Failed to stream voice url=%s", fullUrl)
            stop()
            onError("无法播放该语音。")
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val mp = player ?: break
                val duration = mp.duration.takeIf { it > 0 }?.toLong()
                    ?: _state.value.durationMs
                val position = runCatching { mp.currentPosition.toLong() }.getOrDefault(0L)
                val progress = if (duration > 0L) {
                    (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                _state.value = _state.value.copy(
                    currentPositionMs = position,
                    durationMs = duration,
                    progress = progress,
                )
                delay(100L)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        player?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        player = null
        _state.value = VoicePlaybackState()
    }
}
