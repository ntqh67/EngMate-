package com.engmate.app.feature.reminder.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.reminder.data.ReminderRepository
import com.engmate.app.feature.reminder.domain.ReminderScheduler
import com.engmate.app.feature.reminder.domain.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReminderUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    /** Notification permission was refused; the screen offers to open the app's notification settings. */
    val permissionDenied: Boolean = false,
    val showTimePicker: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private val mutable = MutableStateFlow(ReminderUiState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { s -> mutable.update { it.copy(settings = s, loading = false) } }
        }
    }

    /** Called with true only after the screen has the notification permission. */
    fun setEnabled(enabled: Boolean) = change {
        repository.setEnabled(enabled)
        val s = mutable.value.settings
        if (enabled) scheduler.schedule(s.hour, s.minute) else scheduler.cancel()
        mutable.update { it.copy(permissionDenied = if (enabled) false else it.permissionDenied) }
    }

    /** The learner refused the permission: the switch stays off and nothing is scheduled. */
    fun onPermissionDenied() = change(success = null) {
        repository.setEnabled(false)
        scheduler.cancel()
        mutable.update { it.copy(permissionDenied = true) }
    }

    fun openTimePicker() { if (!mutable.value.busy) mutable.update { it.copy(showTimePicker = true) } }
    fun dismissTimePicker() = mutable.update { it.copy(showTimePicker = false) }

    fun setTime(hour: Int, minute: Int) {
        mutable.update { it.copy(showTimePicker = false) }
        change {
            repository.setTime(hour, minute)
            // Rescheduling replaces the work that still carries the old time.
            if (mutable.value.settings.enabled) scheduler.schedule(hour, minute)
        }
    }

    fun messageShown() = mutable.update { it.copy(message = null) }

    private fun change(success: String? = "Đã lưu", action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try { action(); success }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalArgumentException) { "Giờ nhắc không hợp lệ." }
            catch (_: Exception) { "Không lưu được cài đặt. Hãy thử lại." }
            mutable.update { it.copy(busy = false, message = message) }
        }
    }
}
