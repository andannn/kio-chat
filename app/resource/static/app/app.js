
window.onload = function () {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    console.log("protocol ", protocol);
    const host = window.location.hostname;
    console.log("host ", host);
    const port = window.location.port;
    console.log("port ", port);

    const socket = new WebSocket(`${protocol}//${host}:${port}/test`);
    console.log("socket ", socket);

    socket.onopen = (event) => {
        event.target.send("Hello Server!");
    };

    socket.onmessage = (event) => {
        console.log("event received.", event.data)
    };
}

