package com.engmate.app.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engmate.app.feature.auth.domain.SessionUser
import com.engmate.app.feature.auth.domain.UserSession
import com.engmate.app.feature.profile.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: SessionUser? = null,
    val profile: UserProfile? = null,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val editing: Boolean = false,
    val busy: Boolean = false,
    val confirmDelete: Boolean = false,
    val input: ProfileInput = ProfileInput(""),
    val errors: Map<ProfileField, String> = emptyMap(),
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val session: UserSession,
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileUiState())
    val state = mutable.asStateFlow()
    val isDemo = session.isDemo
    private var observation: Job? = null
    private var mutation: Job? = null

    init {
        viewModelScope.launch {
            session.user.collect { user ->
                mutation?.cancel()
                observation?.cancel()
                mutable.value = ProfileUiState(user = user, loading = user != null,
                    input = ProfileInput(user?.suggestedName.orEmpty()))
                if (user != null) observe(user.uid)
            }
        }
    }

    private fun observe(uid: String) {
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                repository.observe(uid).collect { profile ->
                    if (mutable.value.user?.uid == uid) mutable.update {
                        it.copy(profile = profile, loading = false, loadFailed = false)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (mutable.value.user?.uid == uid) mutable.update {
                    it.copy(loading = false, loadFailed = true, message = "Không đọc được hồ sơ. Vui lòng thử lại.")
                }
            }
        }
    }
    fun retry() {
        val uid = mutable.value.user?.uid ?: return
        mutable.update { it.copy(loading = true, loadFailed = false, message = null) }
        observe(uid)
    }
    fun edit() {
        val value = mutable.value
        if (value.busy || value.loading || value.loadFailed) return
        val p = value.profile
        mutable.update { it.copy(editing = true, errors = emptyMap(), message = null,
            input = p?.let { ProfileInput(it.displayName, it.photoUrl.orEmpty(), it.englishLevel, it.dailyGoalMinutes.toString()) }
                ?: ProfileInput(value.user?.suggestedName.orEmpty())) }
    }
    fun change(input: ProfileInput) {
        if (!mutable.value.busy) mutable.update { it.copy(input = input, errors = emptyMap(), message = null) }
    }
    fun cancelEdit() { if (!mutable.value.busy) mutable.update { it.copy(editing = false, errors = emptyMap()) } }
    fun askDelete() { if (!mutable.value.busy && mutable.value.profile != null) mutable.update { it.copy(confirmDelete = true) } }
    fun dismissDelete() { if (!mutable.value.busy) mutable.update { it.copy(confirmDelete = false) } }
    fun save() {
        val value = mutable.value
        if (value.busy || value.loading || value.loadFailed || !value.editing) return
        val uid = value.user?.uid ?: return
        val errors = ProfileValidator.errors(value.input)
        if (errors.isNotEmpty()) { mutable.update { it.copy(errors = errors) }; return }
        mutate(uid, "Đã lưu hồ sơ.") {
            if (value.profile == null) repository.create(uid, value.input) else repository.update(uid, value.input)
        }
    }
    fun delete() {
        val value = mutable.value
        if (!value.confirmDelete || value.busy) return
        val uid = value.user?.uid ?: return
        mutate(uid, "Đã xóa hồ sơ trên thiết bị này.") { repository.delete(uid) }
    }
    private fun mutate(uid: String, success: String, action: suspend () -> Unit) {
        mutable.update { it.copy(busy = true, message = null) }
        mutation = viewModelScope.launch {
            try {
                action()
                if (session.user.value?.uid == uid && mutable.value.user?.uid == uid) mutable.update {
                    it.copy(busy = false, editing = false, confirmDelete = false, message = success, errors = emptyMap())
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (session.user.value?.uid == uid && mutable.value.user?.uid == uid) mutable.update {
                    it.copy(busy = false, confirmDelete = false,
                        errors = (error as? InvalidProfile)?.errors.orEmpty(), message = when (error) {
                            is ProfileAlreadyExists -> "Hồ sơ đã tồn tại. Hãy hủy và mở lại phần chỉnh sửa."
                            is ProfileMissing -> "Hồ sơ không còn tồn tại. Vui lòng tải lại."
                            is SessionRequired -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
                            is InvalidProfile -> "Vui lòng kiểm tra thông tin nhập."
                            else -> "Không thể lưu thay đổi. Dữ liệu nhập vẫn được giữ; hãy thử lại."
                        })
                }
            }
        }
    }
}
