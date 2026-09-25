package com.dutu007.favorapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.FavorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AppUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val inviteCode: String = "",
    val generatedInvite: String? = null,
    val authenticated: Boolean = false,
    val snapshot: CoupleSnapshot? = null,
    val message: String? = null,
    val error: String? = null,
)

class MainViewModel : ViewModel() {
    private val repository = FavorRepository()
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshSession()
    }

    fun setAuthMode(mode: AuthMode) = _uiState.update { it.copy(authMode = mode, error = null, message = null) }
    fun setEmail(value: String) = _uiState.update { it.copy(email = value) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value) }
    fun setDisplayName(value: String) = _uiState.update { it.copy(displayName = value) }
    fun setInviteCode(value: String) = _uiState.update { it.copy(inviteCode = value.uppercase()) }
    fun clearNotice() = _uiState.update { it.copy(message = null, error = null) }

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                loadSnapshot()
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun submitAuth() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.update { it.copy(error = "请输入邮箱和至少 6 位密码") }
            return
        }
        if (state.authMode == AuthMode.SIGN_UP && state.displayName.isBlank()) {
            _uiState.update { it.copy(error = "注册时请填写昵称") }
            return
        }

        runBusy {
            if (state.authMode == AuthMode.SIGN_IN) {
                repository.signIn(state.email, state.password)
                loadSnapshot()
            } else {
                repository.signUp(state.email, state.password, state.displayName)
                if (repository.currentUserId() == null) {
                    _uiState.update {
                        it.copy(message = "注册成功，请先查收验证邮件，再登录")
                    }
                } else {
                    loadSnapshot()
                }
            }
        }
    }

    fun signOut() {
        runBusy {
            repository.signOut()
            _uiState.update { AppUiState(loading = false) }
        }
    }

    fun createInvite() {
        runBusy {
            val code = repository.createInvite()
            _uiState.update { it.copy(generatedInvite = code, message = "邀请码已生成，复制后发送给对方") }
        }
    }

    fun acceptInvite() {
        val code = _uiState.value.inviteCode.trim()
        if (code.isBlank()) {
            _uiState.update { it.copy(error = "请输入邀请码") }
            return
        }
        runBusy {
            repository.acceptInvite(code)
            _uiState.update { it.copy(inviteCode = "", generatedInvite = null, message = "匹配成功") }
            loadSnapshot()
        }
    }

    fun addScore(delta: Int, note: String?) {
        val snapshot = _uiState.value.snapshot ?: return
        val partner = snapshot.cards.firstOrNull { it.userId != snapshot.currentUserId }
        if (partner == null) {
            _uiState.update { it.copy(error = "暂时找不到恋人账户") }
            return
        }
        runBusy {
            repository.addScore(snapshot.coupleId, partner.userId, delta, note)
            loadSnapshot()
        }
    }

    fun saveSettings(initialText: String, minText: String, maxText: String) {
        val snapshot = _uiState.value.snapshot ?: return
        val initial = initialText.toIntOrNull()
        val min = minText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val max = maxText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (initial == null || (minText.isNotBlank() && min == null) || (maxText.isNotBlank() && max == null)) {
            _uiState.update { it.copy(error = "分数设置必须是整数") }
            return
        }
        if (min != null && max != null && min > max) {
            _uiState.update { it.copy(error = "最低分不能大于最高分") }
            return
        }
        runBusy {
            repository.updateScoreSettings(snapshot.coupleId, initial, min, max)
            _uiState.update { it.copy(message = "分数设置已保存") }
            loadSnapshot()
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, message = null) }
            try {
                block()
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.userMessage()) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun loadSnapshot() {
        val authenticated = repository.currentUserId() != null
        val snapshot = repository.loadSnapshot()
        _uiState.update { it.copy(loading = false, authenticated = authenticated, snapshot = snapshot) }
    }

    private fun Exception.userMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("invalid_or_expired_code", ignoreCase = true) -> "邀请码无效或已过期"
            raw.contains("already_matched", ignoreCase = true) -> "你已经匹配过恋人了"
            raw.contains("cannot_match_self", ignoreCase = true) -> "不能使用自己的邀请码"
            raw.contains("below_minimum", ignoreCase = true) -> "加分后会低于最低分限制"
            raw.contains("above_maximum", ignoreCase = true) -> "加分后会超过最高分限制"
            raw.contains("range_does_not_include_current_score", ignoreCase = true) -> "新的上下限不包含当前分数"
            raw.contains("Invalid login credentials", ignoreCase = true) -> "邮箱或密码错误"
            raw.isBlank() -> "操作失败，请稍后重试"
            else -> raw.substringBefore(" (Request").take(160)
        }
    }
}
