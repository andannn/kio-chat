// Display client error responses in the request's configured target.
htmx.config.responseHandling.unshift({ code: "4..", swap: true, error: false });

function handleMessageKeydown(event, input) {
    if (event.key !== "Enter" || !event.shiftKey || event.isComposing) return;
    event.preventDefault();
    if (!event.repeat) input.form?.requestSubmit();
}

function scrollMessageList(container) {
    const list = container.querySelector("#messages");
    if (!list) return;
    requestAnimationFrame(() => {
        list.scrollTop = list.scrollHeight;
    });
}

function toggleRoomMembers(button) {
    const room = button.closest('.chat-room');
    const panel = room.querySelector('#room-members-panel');
    const toggle = button;
    panel.hidden = !panel.hidden;
    toggle.setAttribute('aria-expanded', String(!panel.hidden));
    if (panel.hidden) {
        toggle.focus();
    }
}

function filterUserEventSwap(event) {
    const target = event.detail.target;
    if (target.classList.contains("room-item") && !event.detail.fragment.childNodes.length) {
        const roomId = target.id.slice("room-link-".length);
        document.querySelectorAll(`.join-request[data-room-id="${roomId}"]`).forEach(request => request.remove());
        if (document.querySelector(".chat-room")?.dataset.roomId === roomId) {
            window.location.replace("/");
        }
    }
    const participant = event.detail.fragment.querySelector("[data-voice-room-id]");
    if (participant && (participant.dataset.voiceRoomId !== document.querySelector(".chat-room")?.dataset.roomId ||
        document.getElementById(participant.id))) {
        event.detail.shouldSwap = false;
        return;
    }
    const message = event.detail.fragment.querySelector("[data-chat-room-id]");
    if (message && message.dataset.chatRoomId !== document.querySelector(".chat-room")?.dataset.roomId) {
        event.detail.shouldSwap = false;
        return;
    }
    const current = event.detail.target;
    if (!current.classList.contains("room-message-preview")) return;
    const incoming = event.detail.fragment.querySelector(".room-message-preview");
    if (!incoming) return;
    const currentId = current.dataset.messageId;
    const incomingId = incoming.dataset.messageId;
    if (incoming.dataset.messageChanged === "true") {
        event.detail.shouldSwap = Boolean(incomingId) && incomingId === currentId;
    } else if (currentId) {
        event.detail.shouldSwap = Boolean(incomingId) && BigInt(incomingId) >= BigInt(currentId);
    }
}

let wsSessionId = null;

async function postRoomEvent(session, message) {
    const roomId = session.roomId;
    console.log("[voice] HTTP send", roomId, message);
    const response = await fetch(`/rooms/${encodeURIComponent(roomId)}/events`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ wsSessionId: session.wsSessionId, message }),
    });
    if (!response.ok || response.redirected) {
        throw new Error(`Room event failed (${response.status}): ${await response.text()}`);
    }
    console.log("[voice] HTTP accepted", roomId, message.type, response.status);
}

let voiceSession = null;
let voiceEventQueue = Promise.resolve();

async function joinVoice(roomId) {
    roomId = String(roomId);
    if (voiceSession?.roomId === roomId) return;
    if (voiceSession) await exitVoice();

    if (!wsSessionId) {
        window.alert("语音连接尚未就绪，请稍后重试。");
        return;
    }
    const session = {
        wsSessionId: wsSessionId,
        roomId,
        stream: null,
        peers: new Map(),
        audios: new Map(),
        pendingCandidates: new Map(),
    };
    voiceSession = session;
    try {
        session.stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
        if (voiceSession !== session) {
            session.stream.getTracks().forEach(track => track.stop());
            return;
        }
        await postRoomEvent(session, { type: "user_joined" });
        console.log("[voice] joined", roomId);
    } catch (error) {
        if (voiceSession === session) window.alert(error.message);
        closeVoiceSession(session);
        console.error("[voice] join failed", error);
    }
}

function closeVoiceSession(session) {
    for (const pc of session.peers.values()) pc.close();
    for (const audio of session.audios.values()) audio.remove();
    session.peers.clear();
    session.audios.clear();
    session.pendingCandidates.clear();
    session.stream?.getTracks().forEach(track => track.stop());
    if (voiceSession === session) voiceSession = null;
}

async function exitVoice() {
    const session = voiceSession;
    if (!session) return;
    closeVoiceSession(session);
    try {
        await postRoomEvent(session, { type: "user_exit" });
        console.log("[voice] exited", session.roomId);
    } catch (error) {
        console.error("[voice] exit failed", error);
    }
}

function createPeer(session, userId) {
    const key = String(userId);
    const existing = session.peers.get(key);
    if (existing) return existing;

    const pc = new RTCPeerConnection({
        iceServers: [{ urls: "stun:stun.l.google.com:19302" }],
    });
    const audio = document.createElement("audio");
    audio.autoplay = true;
    audio.controls = true;
    audio.setAttribute("aria-label", `用户 ${userId} 的语音`);
    document.body.appendChild(audio);

    session.peers.set(key, pc);
    session.audios.set(key, audio);
    session.pendingCandidates.set(key, []);
    session.stream.getTracks().forEach(track => pc.addTrack(track, session.stream));

    pc.onicecandidate = event => {
        if (!event.candidate || voiceSession !== session) return;
        postRoomEvent(session, {
            type: "rtc_conn_candidate",
            toUserId: userId,
            candidate: event.candidate.toJSON(),
        }).catch(error => console.error("[voice] candidate send failed", error));
    };
    pc.ontrack = event => {
        audio.srcObject = event.streams[0] ?? new MediaStream([event.track]);
        audio.play().catch(error => console.warn("[voice] 请点击音频控件播放", error));
    };
    pc.onconnectionstatechange = () => {
        console.log("[voice] connection", userId, pc.connectionState);
        if (pc.connectionState === "failed") {
            pc.close();
            audio.remove();
            if (session.peers.get(key) === pc) {
                session.peers.delete(key);
                session.audios.delete(key);
                session.pendingCandidates.delete(key);
            }
        }
    };
    return pc;
}

async function handleVoiceEvent(roomId, message) {
    const session = voiceSession;
    if (!session?.stream || session.roomId !== String(roomId)) return;
    console.log("[voice] WebSocket received", roomId, message);
    const userId = message.userId;
    const key = String(userId);
    const pc = createPeer(session, userId);
    const candidates = session.pendingCandidates.get(key);
    switch (message.type) {
        case "on_request_send_offer_to_user":
            await pc.setLocalDescription(await pc.createOffer());
            await postRoomEvent(session, {
                type: "rtc_conn_offer",
                toUserId: userId,
                description: pc.localDescription.toJSON(),
            });
            break;
        case "on_request_send_answer_to_user":
            await pc.setRemoteDescription(message.offerDescription);
            for (const candidate of candidates.splice(0)) await pc.addIceCandidate(candidate);
            await pc.setLocalDescription(await pc.createAnswer());
            await postRoomEvent(session, {
                type: "rtc_conn_answer",
                toUserId: userId,
                description: pc.localDescription.toJSON(),
            });
            break;
        case "on_request_save_answer":
            await pc.setRemoteDescription(message.answerDescription);
            for (const candidate of candidates.splice(0)) await pc.addIceCandidate(candidate);
            break;
        case "on_receive_candidate":
            if (pc.remoteDescription) await pc.addIceCandidate(message.candidate);
            else candidates.push(message.candidate);
            break;
    }
}

const voiceEventTypes = new Set([
    "on_request_send_offer_to_user",
    "on_request_send_answer_to_user",
    "on_request_save_answer",
    "on_receive_candidate",
]);

let voiceSocket = null;
document.addEventListener("htmx:wsOpen", event => {
    voiceSocket = event.detail.event.target;
    wsSessionId = null;
    if (voiceSession) closeVoiceSession(voiceSession);
});
document.addEventListener("htmx:wsClose", event => {
    if (event.detail.event.target !== voiceSocket) return;
    voiceSocket = null;
    wsSessionId = null;
    if (voiceSession) closeVoiceSession(voiceSession);
    const panel = document.querySelector("[data-voice-participants]");
    if (panel) {
        htmx.trigger(panel, "htmx:abort");
        const status = panel.querySelector("[data-voice-participant-status]");
        status.textContent = "连接已断开，名单可能已过期。";
        status.hidden = false;
    }
});

document.addEventListener("htmx:wsBeforeMessage", (event) => {
    const raw = event.detail.message;
    if (typeof raw !== "string" || !raw.trimStart().startsWith("{")) return;
    let message;
    try {
        message = JSON.parse(raw);
    } catch {
        return;
    }
    if (message.type === "ws_session_ready") {
        event.preventDefault();
        wsSessionId = message.wsSessionId;
        const panel = document.querySelector("[data-voice-participants]");
        if (panel) htmx.trigger(panel, "refresh");
        return;
    }
    if (!voiceEventTypes.has(message.type)) return;
    event.preventDefault();
    const session = voiceSession;
    voiceEventQueue = voiceEventQueue.then(() => {
        if (voiceSession === session) return handleVoiceEvent(message.roomId, message);
    }).catch(error => console.error("[voice] dispatch failed", error));
});
