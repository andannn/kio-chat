// 语音消息播放（全局单实例互斥）
let activeVoicePlayback = null;

function formatVoiceSeconds(ms) {
    return `${Math.max(1, Math.round((ms || 0) / 1000))}"`;
}

function resetActiveVoicePlayback() {
    if (!activeVoicePlayback) return;
    const { audio, container, progressEl, durationEl, defaultDurationText } = activeVoicePlayback;
    audio.pause();
    audio.src = "";
    container.classList.remove("is-playing");
    const btn = container.querySelector(".voice-play-button");
    if (btn) btn.setAttribute("aria-label", "播放语音");
    if (progressEl) progressEl.style.width = "0%";
    if (durationEl && defaultDurationText) durationEl.textContent = defaultDurationText;
    activeVoicePlayback = null;
}

window.toggleChatVoice = function(button) {
    const container = button.closest(".message-voice");
    const url = container?.getAttribute("data-voice-url");
    if (!container || !url) return;

    if (activeVoicePlayback && activeVoicePlayback.container === container) {
        const { audio } = activeVoicePlayback;
        if (audio.paused) {
            audio.play().then(() => {
                container.classList.add("is-playing");
                button.setAttribute("aria-label", "暂停语音");
            }).catch((err) => {
                console.error("Failed to play voice", err);
                resetActiveVoicePlayback();
            });
        } else {
            audio.pause();
            container.classList.remove("is-playing");
            button.setAttribute("aria-label", "播放语音");
        }
        return;
    }

    resetActiveVoicePlayback();

    const progressEl = container.querySelector(".voice-progress");
    const durationEl = container.querySelector(".voice-duration");
    const defaultDurationMs = Number(container.getAttribute("data-duration-ms")) || 0;
    const defaultDurationText = durationEl?.textContent || formatVoiceSeconds(defaultDurationMs);

    const audio = new Audio(url);
    activeVoicePlayback = {
        audio,
        container,
        progressEl,
        durationEl,
        defaultDurationText,
    };

    audio.addEventListener("timeupdate", () => {
        if (!activeVoicePlayback || activeVoicePlayback.audio !== audio) return;
        const total = Number.isFinite(audio.duration) && audio.duration > 0
            ? audio.duration
            : defaultDurationMs / 1000;
        if (total > 0 && progressEl) {
            const pct = Math.min(100, Math.max(0, (audio.currentTime / total) * 100));
            progressEl.style.width = `${pct}%`;
        }
        if (durationEl && total > 0) {
            const remaining = Math.max(0, Math.ceil(total - audio.currentTime));
            durationEl.textContent = `${remaining}"`;
        }
    });

    audio.addEventListener("ended", () => {
        if (activeVoicePlayback?.audio === audio) {
            resetActiveVoicePlayback();
        }
    });

    audio.addEventListener("error", () => {
        if (activeVoicePlayback?.audio === audio) {
            resetActiveVoicePlayback();
            alert("语音播放失败，请稍后重试。");
        }
    });

    container.classList.add("is-playing");
    button.setAttribute("aria-label", "暂停语音");
    audio.play().catch((err) => {
        console.error("Failed to play voice", err);
        resetActiveVoicePlayback();
        alert("无法播放该语音。");
    });
};

// 浏览器录音与上传（Toggle 模式）
const MAX_VOICE_DURATION_MS = 60_000;
const MIN_VOICE_DURATION_MS = 500;
let activeVoiceRecorder = null;

function formatTimer(ms) {
    const totalSec = Math.floor(ms / 1000);
    const min = Math.floor(totalSec / 60);
    const sec = String(totalSec % 60).padStart(2, "0");
    return `${min}:${sec}`;
}

function pickSupportedAudioMimeType() {
    if (typeof MediaRecorder === "undefined" || typeof MediaRecorder.isTypeSupported !== "function") {
        return "";
    }
    const candidates = [
        "audio/mp4",
        "audio/webm;codecs=opus",
        "audio/webm",
        "audio/ogg;codecs=opus",
    ];
    return candidates.find((type) => MediaRecorder.isTypeSupported(type)) || "";
}

function mimeTypeToExt(mimeType) {
    if (!mimeType) return "webm";
    if (mimeType.includes("mp4")) return "m4a";
    if (mimeType.includes("ogg")) return "ogg";
    return "webm";
}

function setVoiceRecordingUi(recording, uploading = false) {
    const btn = document.getElementById("voice-record-button");
    const bar = document.getElementById("voice-record-bar");
    const textarea = document.getElementById("content");
    const sendBtn = document.getElementById("send-button");
    const timer = document.getElementById("voice-record-timer");

    if (btn) {
        btn.classList.toggle("is-recording", recording);
        btn.disabled = uploading;
        btn.setAttribute("aria-label", recording ? "停止并发送语音" : "录制语音");
        btn.setAttribute("title", recording ? "点击停止并发送" : "点击开始/停止录音");
    }
    if (bar) {
        bar.hidden = !recording && !uploading;
    }
    if (textarea) {
        textarea.hidden = recording || uploading;
    }
    if (sendBtn) {
        sendBtn.disabled = recording || uploading;
    }
    if (timer && uploading) {
        timer.textContent = "正在发送语音…";
    }
}

function cleanupVoiceRecorderState() {
    if (!activeVoiceRecorder) return;
    clearInterval(activeVoiceRecorder.timerId);
    activeVoiceRecorder.stream.getTracks().forEach((track) => track.stop());
    activeVoiceRecorder = null;
}

window.cancelChatVoiceRecord = function() {
    if (!activeVoiceRecorder) return;
    activeVoiceRecorder.cancelled = true;
    const { recorder } = activeVoiceRecorder;
    if (recorder.state !== "inactive") {
        recorder.stop();
    } else {
        cleanupVoiceRecorderState();
        setVoiceRecordingUi(false, false);
    }
};

window.toggleChatVoiceRecord = async function(button) {
    if (activeVoiceRecorder) {
        if (activeVoiceRecorder.recorder.state !== "inactive") {
            activeVoiceRecorder.recorder.stop();
        }
        return;
    }

    const uploadUrl = button.getAttribute("data-upload-url");
    if (!uploadUrl) return;

    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
        alert("当前浏览器不支持录音功能。");
        return;
    }

    let stream;
    try {
        stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (err) {
        console.error("Microphone permission denied or unavailable", err);
        alert("无法访问麦克风，请检查浏览器权限设置。");
        return;
    }

    const mimeType = pickSupportedAudioMimeType();
    let recorder;
    try {
        recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
    } catch (err) {
        stream.getTracks().forEach((track) => track.stop());
        console.error("Failed to create MediaRecorder", err);
        alert("初始化录音器失败。");
        return;
    }

    const chunks = [];
    const startTime = Date.now();
    const state = {
        recorder,
        stream,
        cancelled: false,
        timerId: null,
    };
    activeVoiceRecorder = state;

    const timerEl = document.getElementById("voice-record-timer");
    if (timerEl) timerEl.textContent = "正在录音 0:00";
    setVoiceRecordingUi(true, false);

    state.timerId = setInterval(() => {
        const elapsed = Date.now() - startTime;
        if (timerEl) timerEl.textContent = `正在录音 ${formatTimer(elapsed)}`;
        if (elapsed >= MAX_VOICE_DURATION_MS && recorder.state !== "inactive") {
            recorder.stop();
        }
    }, 250);

    recorder.addEventListener("dataavailable", (event) => {
        if (event.data && event.data.size > 0) {
            chunks.push(event.data);
        }
    });

    recorder.addEventListener("stop", async () => {
        const wasCancelled = state.cancelled;
        const durationMs = Date.now() - startTime;
        const finalMimeType = recorder.mimeType || mimeType;
        cleanupVoiceRecorderState();

        if (wasCancelled) {
            setVoiceRecordingUi(false, false);
            return;
        }

        if (durationMs < MIN_VOICE_DURATION_MS || chunks.length === 0) {
            setVoiceRecordingUi(false, false);
            alert("录音时间太短。");
            return;
        }

        const blob = new Blob(chunks, { type: finalMimeType || "audio/webm" });
        const ext = mimeTypeToExt(finalMimeType);
        const formData = new FormData();
        formData.append("durationMs", String(durationMs));
        formData.append("voice", blob, `voice.${ext}`);

        setVoiceRecordingUi(false, true);
        try {
            const response = await fetch(uploadUrl, {
                method: "POST",
                body: formData,
            });
            if (!response.ok) {
                const msg = await response.text();
                alert(msg || "发送语音失败，请重试。");
            }
        } catch (err) {
            console.error("Failed to upload voice", err);
            alert("发送语音失败，请重试。");
        } finally {
            setVoiceRecordingUi(false, false);
        }
    });

    recorder.start();
};

// 切换房间前自动清理正在播放或录制的音频
document.addEventListener("htmx:beforeSwap", (event) => {
    if (event.detail.target?.id === "room-content") {
        resetActiveVoicePlayback();
        window.cancelChatVoiceRecord();
    }
});
