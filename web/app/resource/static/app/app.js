// Display client error responses in the request's configured target.
htmx.config.responseHandling.unshift({ code: "4..", swap: true, error: false });

// OOB 插入消息后自动平滑滚动到底部
document.addEventListener("htmx:oobAfterSwap", (event) => {
    const target = event.detail.target;
    if (target?.classList.contains("chat-messages")) {
        target.scrollTop = target.scrollHeight;
    }
});

// 图片上传与发送逻辑
window.sendChatImageFile = function(file, uploadUrl) {
    if (!file || !file.type.startsWith("image/")) return;

    const img = new Image();
    const objectUrl = URL.createObjectURL(file);
    img.onload = () => {
        const width = img.naturalWidth;
        const height = img.naturalHeight;
        URL.revokeObjectURL(objectUrl);

        const formData = new FormData();
        formData.append("image", file);
        formData.append("width", width.toString());
        formData.append("height", height.toString());

        fetch(uploadUrl, {
            method: "POST",
            body: formData,
        }).catch((err) => {
            console.error("Failed to upload image", err);
            alert("发送图片失败，请重试。");
        });
    };
    img.onerror = () => {
        URL.revokeObjectURL(objectUrl);
        const formData = new FormData();
        formData.append("image", file);
        fetch(uploadUrl, {
            method: "POST",
            body: formData,
        }).catch((err) => {
            console.error("Failed to upload image", err);
            alert("发送图片失败，请重试。");
        });
    };
    img.src = objectUrl;
};

window.handleChatImageUpload = function(input) {
    const file = input.files?.[0];
    const uploadUrl = input.getAttribute("data-upload-url");
    if (file && uploadUrl) {
        window.sendChatImageFile(file, uploadUrl);
    }
    input.value = "";
};

// 支持直接在输入框粘贴图片发送
document.addEventListener("paste", (event) => {
    const items = event.clipboardData?.items;
    if (!items) return;
    const input = document.getElementById("image-upload-input");
    const uploadUrl = input?.getAttribute("data-upload-url");
    if (!uploadUrl) return;

    for (let i = 0; i < items.length; i++) {
        if (items[i].type.indexOf("image") !== -1) {
            const file = items[i].getAsFile();
            if (file) {
                event.preventDefault();
                window.sendChatImageFile(file, uploadUrl);
                break;
            }
        }
    }
});
