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
