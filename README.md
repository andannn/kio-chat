# Kio Chat

Login, registration and coroutine Session handling are adapted from kio-note.
Only MockRepositoryImpl is wired: users and sessions are in memory and reset on restart.
Register an account at /register, then use /login. The root page requires a valid session.

```sh
./gradlew :app:jvmRun
```

Open http://127.0.0.1:7878. Cookie flags: HttpOnly, SameSite=Lax, Path=/.
SESSION_COOKIE_SECURE can explicitly enable Secure; it defaults to enabled when TLS_CERT is configured, disabled for local HTTP.
The existing /test WebSocket remains a development demo; Kio's websocket route bypasses HTTP interceptors, so it is not protected by AuthSession.
Mock password hashing follows kio-note's SHA-256 format and is for development only.
