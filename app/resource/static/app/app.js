
window.onload = function () {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    console.log("protocol ", protocol);
    const host = window.location.hostname;
    console.log("host ", host);
    const port = window.location.port;
    console.log("port ", port);

    const socket = new WebSocket(`${protocol}//${host}:${port}/test`);
    console.log("socket ", socket);

    socket.addEventListener("open", event => {
        socket.send("Hello Server!");
    });
}

