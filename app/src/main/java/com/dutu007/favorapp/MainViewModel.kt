package com.dutu007.favorapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.ApiException
import com.dutu007.favorapp.data.FavorRepository
import com.dutu007.favorapp.data.ScoreRule
import com.dutu007.favorapp.data.ScorePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AppUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayName: String = "",
    val inviteCode: String = "",
    val generatedInvite: String? = null,
    val initialScore: String = "0",
    val minScore: String = "",
    val maxScore: String = "",
    val addMin: String = "1",
    val addMax: String = "5",
    val subtractMin: String = "1",
    val subtractMax: String = "5",
    val manualDelta: String = "",
    val presetLabel: String = "",
    val presetDelta: String = "",
    val scorePresets: List<ScorePreset> = emptyList(),
    val authenticated: Boolean = false,
    val snapshot: CoupleSnapshot? = null,
    val message: String? = null,
    val error: String? = null,
    val flash: String? = null,
    val flashError: Boolean = false,
)

class MainViewModel : ViewModel() {
    private val repository = FavorRepository(FavorApplication.instance)
    private val preferences = FavorApplication.instance.getSharedPreferences("favorapp_settings", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
        refreshSession()
    }

    fun setAuthMode(mode: AuthMode) = _uiState.update { it.copy(authMode = mode, confirmPassword = "", error = null, message = null) }
    fun setEmail(value: String) = _uiState.update { it.copy(email = value.lowercase()) }
    fun setPassword(value: String) = _uiState.update { it.copy(password = value) }
    fun setConfirmPassword(value: String) = _uiState.update { it.copy(confirmPassword = value) }
    fun setDisplayName(value: String) = _uiState.update { it.copy(displayName = value) }
    fun setInviteCode(value: String) = _uiState.update { it.copy(inviteCode = value.uppercase()) }
    fun setInitialScore(value: String) = _uiState.update { it.copy(initialScore = value.filter { c -> c == '-' || c.isDigit() }) }
    fun setMinScore(value: String) = _uiState.update { it.copy(minScore = value.filter { c -> c == '-' || c.isDigit() }) }
    fun setMaxScore(value: String) = _uiState.update { it.copy(maxScore = value.filter { c -> c == '-' || c.isDigit() }) }
    fun setAddMin(value: String) = _uiState.update { it.copy(addMin = value.filter(Char::isDigit)) }
    fun setAddMax(value: String) = _uiState.update { it.copy(addMax = value.filter(Char::isDigit)) }
    fun setSubtractMin(value: String) = _uiState.update { it.copy(subtractMin = value.filter(Char::isDigit)) }
    fun setSubtractMax(value: String) = _uiState.update { it.copy(subtractMax = value.filter(Char::isDigit)) }
    fun setManualDelta(value: String) = _uiState.update { it.copy(manualDelta = value.filter { c -> c == '-' || c.isDigit() }) }
    fun setPresetLabel(value: String) = _uiState.update { it.copy(presetLabel = value) }
    fun setPresetDelta(value: String) = _uiState.update { it.copy(presetDelta = value.filter { c -> c == '-' || c.isDigit() }) }
    fun addPreset() {
        val state = _uiState.value
        val label = state.presetLabel.trim()
        val delta = state.presetDelta.toIntOrNull()
        if (label.isBlank() || label.length > 24 || delta == null || delta == 0 || kotlin.math.abs(delta) > 100) {
            _uiState.update { it.copy(error = "请输入名称，并填写 1 到 100 之间的加分或扣分值") }; return
        }
        if (state.scorePresets.size >= 12) { _uiState.update { it.copy(error = "最多保存 12 个预设项") }; return }
        savePresets(state.scorePresets + ScorePreset(UUID.randomUUID().toString(), label, delta))
        _uiState.update { it.copy(presetLabel = "", presetDelta = "", message = "预设项已添加", error = null) }
    }
    fun removePreset(id: String) {
        savePresets(_uiState.value.scorePresets.filterNot { it.id == id })
        _uiState.update { it.copy(message = "预设项已删除", error = null) }
    }
    fun validateManualDelta(): Int? {
        val value = _uiState.value.manualDelta.toIntOrNull()
        if (value == null || value == 0 || kotlin.math.abs(value) > 100) {
            _uiState.update { it.copy(error = "请输入 1 到 100 之间的分数，负数表示扣分") }; return null
        }
        return value
    }

    private fun loadPresets() {
        val saved = preferences.getString("score_presets", null) ?: return
        val presets = runCatching {
            val array = JSONArray(saved)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ScorePreset(item.getString("id"), item.getString("label"), item.getInt("delta"))
            }
        }.getOrDefault(emptyList())
        _uiState.update { it.copy(scorePresets = presets) }
    }

    private fun savePresets(presets: List<ScorePreset>) {
        val array = JSONArray()
        presets.forEach { preset -> array.put(JSONObject().put("id", preset.id).put("label", preset.label).put("delta", preset.delta)) }
        preferences.edit().putString("score_presets", array.toString()).apply()
        _uiState.update { it.copy(scorePresets = presets) }
    }
    fun clearNotice() = _uiState.update { it.copy(message = null, error = null, flash = null, flashError = false) }

    fun savePartnerNickname(nickname: String) {
        val value = nickname.trim()
        if (value.length > 8) { _uiState.update { it.copy(error = "昵称最多 8 个字") }; return }
        runBusy { repository.updateNickname(value); _uiState.update { it.copy(message = "恋人昵称已保存") }; loadSnapshot() }
    }

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            if (repository.currentUserId() == null) {
                _uiState.update { it.copy(loading = false, authenticated = false, snapshot = null, error = null) }
                return@launch
            }
            try {
                loadSnapshot()
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) repository.clearSession()
                _uiState.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun refreshSnapshot(keyword: String = "", date: String = "", notify: Boolean = false) {
        if (repository.currentUserId() == null) {
            _uiState.update { it.copy(loading = false, authenticated = false, snapshot = null, error = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, message = null, flash = null, flashError = false) }
            try {
                loadSnapshot(keyword, date)
                if (notify) _uiState.update { it.copy(flash = "刷新成功") }
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) {
                    repository.clearSession()
                    _uiState.update { it.copy(authenticated = false, snapshot = null) }
                }
                if (notify) _uiState.update { it.copy(flash = "刷新失败", flashError = true) }
                else _uiState.update { it.copy(error = error.userMessage()) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    fun submitAuth() {
        val state = _uiState.value
        if (!state.email.matches(Regex("^[a-z][a-z0-9_]{2,19}$"))) {
            _uiState.update { it.copy(error = "账号需为 3-20 位字母、数字或下划线，且以字母开头") }
            return
        }
        if (state.password.length !in 8..64 || !state.password.any { it.isUpperCase() } || !state.password.any { it.isLowerCase() } || !state.password.any { it.isDigit() } || state.password.all { it.isLetterOrDigit() }) {
            _uiState.update { it.copy(error = "密码需为 8-64 位，并包含大写字母、小写字母、数字和特殊字符") }
            return
        }
        if (state.authMode == AuthMode.SIGN_UP && state.displayName.isBlank()) {
            _uiState.update { it.copy(error = "注册时请填写昵称") }
            return
        }
        if (state.authMode == AuthMode.SIGN_UP && state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = "两次输入的密码不一致") }
            return
        }

        runBusy {
            if (state.authMode == AuthMode.SIGN_IN) {
                repository.signIn(state.email, state.password)
                loadSnapshot()
            } else {
                repository.signUp(state.email, state.password, state.displayName)
                repository.clearSession()
                _uiState.update {
                    it.copy(
                        authMode = AuthMode.SIGN_IN,
                        password = "",
                        confirmPassword = "",
                        displayName = "",
                        message = "注册成功，请使用新账号登录",
                        error = null,
                        authenticated = false,
                        snapshot = null,
                    )
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
        val state = _uiState.value
        val initial = state.initialScore.toIntOrNull()
        val min = state.minScore.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        val max = state.maxScore.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (initial == null || (state.minScore.isNotBlank() && min == null) || (state.maxScore.isNotBlank() && max == null)) {
            _uiState.update { it.copy(error = "请完整填写分数规则") }; return
        }
        if ((min != null && max != null && min > max) || (min != null && initial < min) || (max != null && initial > max)) {
            _uiState.update { it.copy(error = "初始分数必须在总分上下限内") }; return
        }
        val addMin = state.addMin.toIntOrNull()
        val addMax = state.addMax.toIntOrNull()
        val subtractMin = state.subtractMin.toIntOrNull()
        val subtractMax = state.subtractMax.toIntOrNull()
        if (addMin == null || addMax == null || subtractMin == null || subtractMax == null) {
            _uiState.update { it.copy(error = "请填写单次加分和扣分范围") }; return
        }
        if (addMin !in 1..100 || addMax !in addMin..100) {
            _uiState.update { it.copy(error = "单次加分绝对值范围需为 1-100，且最小值不能大于最大值") }; return
        }
        if (subtractMin !in 1..100 || subtractMax !in subtractMin..100) {
            _uiState.update { it.copy(error = "单次扣分绝对值范围需为 1-100，且最小值不能大于最大值") }; return
        }
        val rules = ScoreRule(initial, min, max, addMin, addMax, subtractMin, subtractMax)
        runBusy {
            val code = repository.createInvite(rules)
            _uiState.update { it.copy(generatedInvite = code, message = "邀请码已生成，复制后发送给对方") }
        }
    }

    fun enterCouple() {
        refreshSnapshot()
        if (_uiState.value.snapshot == null) {
            _uiState.update { it.copy(message = "还在等待对方输入邀请码，请稍后再试") }
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
            repository.addScore(delta, note)
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
            // Keep the per-invite add/subtract ranges; only the score bounds change here.
            repository.updateScoreSettings(initial, min, max, snapshot.settings.addMin, snapshot.settings.addMax, snapshot.settings.subtractMin, snapshot.settings.subtractMax)
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
                if (error is ApiException && error.statusCode == 401) {
                    repository.clearSession()
                    _uiState.update { it.copy(authenticated = false, snapshot = null) }
                }
                _uiState.update { it.copy(error = error.userMessage()) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun loadSnapshot(keyword: String = "", date: String = "") {
        val authenticated = repository.currentUserId() != null
        val snapshot = repository.loadSnapshot(from = date, to = date, keyword = keyword)
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
            raw.contains("add_delta_out_of_range", ignoreCase = true) -> "这次加分不在单次加分范围内"
            raw.contains("subtract_delta_out_of_range", ignoreCase = true) -> "这次扣分不在单次扣分范围内"
            raw.contains("range_does_not_include_current_score", ignoreCase = true) -> "新的上下限不包含当前分数"
            raw.contains("invalid_credentials", ignoreCase = true) -> "账号或密码错误"
            raw.contains("username_taken", ignoreCase = true) -> "账号已被注册"
            raw.contains("initial_score_outside_range", ignoreCase = true) -> "初始分数不在上下限范围内"
            raw.isBlank() -> "操作失败，请稍后重试"
            else -> raw.substringBefore(" (Request").take(160)
        }
    }
}
