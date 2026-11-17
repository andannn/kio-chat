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

function updateJoinRequestCount(container) {
    const count = container.querySelectorAll("#room-join-requests .join-request").length;
    const badge = container.querySelector("#join-request-count");
    badge.textContent = String(count);
    badge.hidden = count === 0;
    badge.setAttribute("aria-label", `${count} 条待处理申请`);
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

// Server event-dispatch demo: descriptions and candidates are placeholders.
async function postRoomEvent(roomId, message) {
    console.log("[voice] HTTP send", roomId, message);
    const response = await fetch(`/rooms/${encodeURIComponent(roomId)}/events`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(message),
    });
    if (!response.ok || response.redirected) {
        throw new Error(`Room event failed (${response.status}): ${await response.text()}`);
    }
    console.log("[voice] HTTP accepted", roomId, message.type, response.status);
}

async function joinVoice(roomId) {
    try {
        await postRoomEvent(roomId, { type: "user_joined" });
        console.log("[voice] joined", roomId);
    } catch (error) {
        console.error("[voice] join failed", error);
    }
}

async function exitVoice(roomId) {
    try {
        await postRoomEvent(roomId, { type: "user_exit" });
        console.log("[voice] exited", roomId);
    } catch (error) {
        console.error("[voice] exit failed", error);
    }
}

async function sendTestDescription(roomId, userId, type) {
    await postRoomEvent(roomId, {
        type: `rtc_conn_${type}`,
        toUserId: userId,
        description: {
            type,
            sdp: `test-${type}`,
        },
    });
    await postRoomEvent(roomId, {
        type: "rtc_conn_candidate",
        toUserId: userId,
        candidate: { candidate: `test-candidate-after-${type}` },
    });
}

async function handleVoiceEvent(roomId, message) {
    console.log("[voice] WebSocket received", roomId, message);
    switch (message.type) {
        case "on_request_send_offer_to_user":
            await sendTestDescription(roomId, message.userId, "offer");
            break;
        case "on_request_send_answer_to_user":
            await sendTestDescription(roomId, message.userId, "answer");
            break;
        case "on_request_save_answer":
            console.log("[voice] answer delivered from", message.userId, message.answerDescription);
            break;
        case "on_receive_candidate":
            console.log("[voice] candidate delivered from", message.userId, message.candidate);
            break;
    }
}

const voiceEventTypes = new Set([
    "on_request_send_offer_to_user",
    "on_request_send_answer_to_user",
    "on_request_save_answer",
    "on_receive_candidate",
]);

document.addEventListener("htmx:wsBeforeMessage", (event) => {
    const raw = event.detail.message;
    if (typeof raw !== "string" || !raw.trimStart().startsWith("{")) return;
    let message;
    try {
        message = JSON.parse(raw);
    } catch {
        return;
    }
    if (!voiceEventTypes.has(message.type)) return;
    event.preventDefault();
    handleVoiceEvent(message.roomId, message).catch(error => console.error("[voice] dispatch failed", error));
});
