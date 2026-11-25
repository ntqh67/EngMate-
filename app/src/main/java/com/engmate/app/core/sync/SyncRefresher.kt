package com.engmate.app.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RefreshResult { DONE, THROTTLED, SIGNED_OUT, OFFLINE, FAILED }

/**
 * Pulls the signed-in learner's cloud data again. Calls are serialized; a call too soon after the last pull returns THROTTLED.
 * A [background] call (the app came back, a screen opened) also waits until the sign-in pull has run, so it never duplicates it.
 */
interface SyncRefresher { suspend fun refresh(background: Boolean = false): RefreshResult }

/** Asked before a handbook is changed: did another device already delete it? */
interface HandbookGuard {
    /** True when the cloud says the handbook was deleted; its local copy is then removed. False when unsure (offline, slow, never uploaded). */
    suspend fun deletedElsewhere(handbookId: Long): Boolean
}

@HiltViewModel
class SyncRefreshViewModel @Inject constructor(private val refresher: SyncRefresher) : ViewModel() {
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() { _message.value = null }

    /** No spinner and no message: keeps data current without the learner asking. */
    fun refreshInBackground() { viewModelScope.launch { refresher.refresh(background = true) } }

    fun refresh() {
        if (!_refreshing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                _message.value = when (refresher.refresh()) {
                    RefreshResult.OFFLINE -> "Không có kết nối Internet. Chưa đồng bộ được."
                    RefreshResult.FAILED -> "Một số dữ liệu chưa đồng bộ được. Hãy thử lại."
                    RefreshResult.SIGNED_OUT -> "Đăng nhập để đồng bộ dữ liệu."
                    RefreshResult.DONE, RefreshResult.THROTTLED -> null
                }
            } finally { _refreshing.value = false }
        }
    }
}
