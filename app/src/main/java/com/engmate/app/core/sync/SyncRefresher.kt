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

/** Pulls the signed-in learner's cloud data again (pull-to-refresh). Calls are serialized; extra taps return THROTTLED. */
interface SyncRefresher { suspend fun refresh(): RefreshResult }

@HiltViewModel
class SyncRefreshViewModel @Inject constructor(private val refresher: SyncRefresher) : ViewModel() {
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() { _message.value = null }

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
