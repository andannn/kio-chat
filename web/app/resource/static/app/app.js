// Display client error responses in the request's configured target.
htmx.config.responseHandling.unshift({ code: "4..", swap: true, error: false });

// OOB 插入消息后自动平滑滚动到底部
document.addEventListener("htmx:oobAfterSwap", (event) => {
    const target = event.detail.target;
    if (target?.classList.contains("chat-messages")) {
        target.scrollTop = target.scrollHeight;
    }
});
