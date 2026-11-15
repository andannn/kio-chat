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
    const toggle = room.querySelector('.members-toggle');
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
