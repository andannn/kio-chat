package me.andannn.kio.chat

import kio.chat.domain.UserEvent
import kio.chat.domain.WebSocketSessionReady
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex

class UserEventSource(private val service: ChatService) {
    private val observationMutex = Mutex()

    private val _events = MutableSharedFlow<UserEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private var _wsSessionId: String? = null
    val wsSessionId get() = _wsSessionId ?: error("wsSessionId is not ready")

    suspend fun observe(): ApiError? {
        if (!observationMutex.tryLock()) return ApiError.ServerError("事件连接已经启动。")
        try {
            return service.observeUserEvents { event ->
                if (event is WebSocketSessionReady) _wsSessionId = event.wsSessionId
                _events.emit(event)
            }
        } finally {
            _wsSessionId = null
            observationMutex.unlock()
        }
    }
}
