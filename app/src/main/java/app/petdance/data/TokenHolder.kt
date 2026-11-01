package app.petdance.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class TokenHolder {
    @Volatile var access: String? = null
    @Volatile var refresh: String? = null

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired = _expired.asSharedFlow()

    fun update(access: String, refresh: String) {
        this.access = access
        this.refresh = refresh
    }

    fun clear(notify: Boolean) {
        access = null
        refresh = null
        if (notify) _expired.tryEmit(Unit)
    }
}
