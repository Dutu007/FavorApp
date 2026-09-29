package com.dutu007.favorapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.ApiException
import com.dutu007.favorapp.data.FavorRepository
import com.dutu007.favorapp.data.ScoreRule
import com.dutu007.favorapp.data.ScoreSettingRow
import com.dutu007.favorapp.data.ScorePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

enum class AuthMode { SIGN_IN, SIGN_UP }

const val NOTE_MAX_LENGTH = 200
const val PRESET_MAX_COUNT = 5

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
        if (label.isBlank()) { _uiState.update { it.copy(error = "请填写备注内容") }; return }
        if (label.length > NOTE_MAX_LENGTH) { _uiState.update { it.copy(error = "备注最多 $NOTE_MAX_LENGTH 个字") }; return }
        if (delta == null || delta == 0) { _uiState.update { it.copy(error = "请填写加分或扣分值，负数表示扣分") }; return }
        if (!validatePresetDelta(delta)) { return }
        if (state.scorePresets.size >= PRESET_MAX_COUNT) { _uiState.update { it.copy(error = "常用记录最多保存 $PRESET_MAX_COUNT 条") }; return }
        savePresets(state.scorePresets + ScorePreset(UUID.randomUUID().toString(), label, delta))
        _uiState.update { it.copy(presetLabel = "", presetDelta = "", message = "预设项已添加", error = null) }
    }
    fun removePreset(id: String) {
        savePresets(_uiState.value.scorePresets.filterNot { it.id == id })
        _uiState.update { it.copy(message = "预设项已删除", error = null) }
    }
    fun validateManualDelta(): Int? {
        val settings = _uiState.value.snapshot?.settings
        val value = _uiState.value.manualDelta.toIntOrNull()
        if (value == null || value == 0) {
            _uiState.update { it.copy(error = "请输入加分或扣分值，负数表示扣分") }; return null
        }
        if (settings == null) return value
        if (!deltaWithinRules(value, settings)) {
            _uiState.update { it.copy(error = deltaRangeError(value, settings)) }; return null
        }
        return value
    }
    fun validatePresetDelta(delta: Int): Boolean {
        val settings = _uiState.value.snapshot?.settings
        if (delta == 0) { _uiState.update { it.copy(error = "预设分值不能为 0") }; return false }
        if (settings == null) return true
        if (!deltaWithinRules(delta, settings)) {
            _uiState.update { it.copy(error = deltaRangeError(delta, settings)) }; return false
        }
        return true
    }
    private fun deltaWithinRules(value: Int, settings: ScoreSettingRow): Boolean {
        val amount = kotlin.math.abs(value)
        return if (value > 0) amount in settings.addMin..settings.addMax else amount in settings.subtractMin..settings.subtractMax
    }
    private fun deltaRangeError(value: Int, settings: ScoreSettingRow): String =
        if (value > 0) "超出当前单次加分范围（${settings.addMin}-${settings.addMax}）" else "超出当前单次扣分范围（${settings.subtractMin}-${settings.subtractMax}）"

    private fun loadPresets() {
        val saved = preferences.getString("score_presets", null) ?: return
        val presets = runCatching {
            val array = JSONArray(saved)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ScorePreset(item.getString("id"), item.getString("label"), item.getInt("delta"))
            }
        }.getOrDefault(emptyList()).take(PRESET_MAX_COUNT)
        _uiState.update { it.copy(scorePresets = presets) }
    }

    private fun savePresets(presets: List<ScorePreset>) {
        val array = JSONArray()
        presets.forEach { preset -> array.put(JSONObject().put("id", preset.id).put("label", preset.label).put("delta", preset.delta)) }
        preferences.edit().putString("score_presets", array.toString()).apply()
        _uiState.update { it.copy(scorePresets = presets) }
    }
    fun clearNotice() = _uiState.update { it.copy(message = null, error = null, flash = null, flashError = false) }

    fun savePartnerNickname(nickname: String): Boolean {
        val value = nickname.trim()
        if (value.length > 8) { _uiState.update { it.copy(error = "昵称最多 8 个字") }; return false }
        runBusy { repository.updateNickname(value); _uiState.update { it.copy(message = "恋人昵称已保存") }; loadSnapshot() }
        return true
    }

    fun avatarUrlFor(userId: String, version: Long): String? = repository.avatarUrl(userId, version)
    val authToken: String get() = repository.authToken()

    fun uploadAvatarFromUri(context: Context, uri: Uri) {
        runBusy {
            val bytes = withContext(Dispatchers.IO) { processAvatar(context, uri) }
            if (bytes == null) {
                _uiState.update { it.copy(error = "无法读取所选图片，换一张试试") }
                return@runBusy
            }
            repository.uploadAvatar(bytes)
            _uiState.update { it.copy(message = "头像已更新") }
            loadSnapshot()
        }
    }

    fun deleteAvatar() {
        runBusy {
            repository.deleteAvatar()
            _uiState.update { it.copy(message = "头像已移除") }
            loadSnapshot()
        }
    }

    // Center-crop to a square, downsample to 256px and re-encode as JPEG,
    // which also strips EXIF (location etc.) before upload.
    private fun processAvatar(context: Context, uri: Uri): ByteArray? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds-only decoding returns null by design, so the stream must be
        // checked separately from the decode result.
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val dataStream = resolver.openInputStream(uri) ?: return null
        val bitmap = dataStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = exifRotation(resolver, uri)
        val upright = if (rotation != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else bitmap
        val side = minOf(upright.width, upright.height)
        val left = (upright.width - side) / 2
        val top = (upright.height - side) / 2
        val square = Bitmap.createBitmap(upright, left, top, side, side)
        val output = ByteArrayOutputStream()
        Bitmap.createScaledBitmap(square, 512, 512, true).compress(Bitmap.CompressFormat.JPEG, 85, output)
        output.toByteArray()
    }.getOrNull()

    private fun exifRotation(resolver: android.content.ContentResolver, uri: Uri): Float {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
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
                if (error is ApiException && error.statusCode == 401) {
                    repository.clearSession()
                    resetScoreKey()
                }
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
                if (notify) _uiState.update { it.copy(flash = "刷新成功", flashError = false) }
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) {
                    repository.clearSession()
                    resetScoreKey()
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
            resetScoreKey()
            _uiState.update { AppUiState(loading = false) }
        }
    }

    private fun resetScoreKey() {
        scoreActionKey = null
        scoreIdempotencyKey = null
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

    // A retry of the same action reuses the idempotency key so a request that
    // actually landed on the server cannot be recorded twice.
    private var scoreActionKey: Pair<Int, String?>? = null
    private var scoreIdempotencyKey: String? = null

    fun addScore(delta: Int, note: String?) {
        val snapshot = _uiState.value.snapshot ?: return
        val partner = snapshot.cards.firstOrNull { it.userId != snapshot.currentUserId }
        if (partner == null) {
            _uiState.update { it.copy(error = "暂时找不到恋人账户") }
            return
        }
        if (note != null && note.length > NOTE_MAX_LENGTH) {
            _uiState.update { it.copy(error = "备注最多 $NOTE_MAX_LENGTH 个字") }
            return
        }
        val action = delta to note?.trim()
        if (scoreActionKey != action || scoreIdempotencyKey == null) {
            scoreIdempotencyKey = UUID.randomUUID().toString()
            scoreActionKey = action
        }
        val key = scoreIdempotencyKey!!
        runBusy {
            repository.addScore(delta, note?.trim(), key)
            scoreIdempotencyKey = null
            scoreActionKey = null
            loadSnapshot()
        }
    }

    fun requestRulesChange(addMinText: String, addMaxText: String, subtractMinText: String, subtractMaxText: String): Boolean {
        val addMin = addMinText.toIntOrNull()
        val addMax = addMaxText.toIntOrNull()
        val subtractMin = subtractMinText.toIntOrNull()
        val subtractMax = subtractMaxText.toIntOrNull()
        if (addMin == null || addMax == null || subtractMin == null || subtractMax == null) {
            _uiState.update { it.copy(error = "单次加减分范围必须是整数") }
            return false
        }
        if (addMin !in 1..100 || addMax !in addMin..100) {
            _uiState.update { it.copy(error = "单次加分范围需为正数1~100，且最小值不能大于最大值") }
            return false
        }
        if (subtractMin !in 1..100 || subtractMax !in subtractMin..100) {
            _uiState.update { it.copy(error = "单次扣分范围需为正数1~100，且最小值不能大于最大值") }
            return false
        }
        runBusy {
            repository.createRulesRequest(addMin, addMax, subtractMin, subtractMax)
            _uiState.update { it.copy(message = "修改请求已发送，等待对方同意") }
            loadSnapshot()
        }
        return true
    }

    fun acceptRulesRequest() {
        val id = _uiState.value.snapshot?.pendingRules?.id ?: return
        runBusy {
            repository.acceptRulesRequest(id)
            _uiState.update { it.copy(message = "已同意对方的记分规则修改") }
            loadSnapshot()
        }
    }

    fun rejectRulesRequest() {
        val id = _uiState.value.snapshot?.pendingRules?.id ?: return
        runBusy {
            repository.rejectRulesRequest(id)
            _uiState.update { it.copy(message = "已拒绝对方的记分规则修改") }
            loadSnapshot()
        }
    }

    fun cancelRulesRequest() {
        val id = _uiState.value.snapshot?.pendingRules?.id ?: return
        runBusy {
            repository.cancelRulesRequest(id)
            _uiState.update { it.copy(message = "修改请求已撤销") }
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
                    resetScoreKey()
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
        notifyRuleDecision(snapshot)
        _uiState.update { it.copy(loading = false, authenticated = authenticated, snapshot = snapshot) }
    }

    // Show the outcome of a rule change request once per decision, remembered across restarts.
    private fun notifyRuleDecision(snapshot: CoupleSnapshot?) {
        val decision = snapshot?.latestRuleDecision ?: return
        val at = runCatching { java.time.OffsetDateTime.parse(decision.respondedAt).toInstant().toEpochMilli() }.getOrNull() ?: return
        if (at <= preferences.getLong("rules_decision_seen_at", 0L)) return
        preferences.edit().putLong("rules_decision_seen_at", at).apply()
        if (decision.requesterId == snapshot.currentUserId) {
            val text = if (decision.status == "accepted") "对方同意了你的记分规则修改" else "对方拒绝了你的记分规则修改"
            _uiState.update { it.copy(message = text) }
        }
    }

    private fun Exception.userMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("invalid_or_expired_code", ignoreCase = true) -> "邀请码无效或已过期"
            raw.contains("already_matched", ignoreCase = true) -> "你已经匹配过恋人了"
            raw.contains("not_matched", ignoreCase = true) -> "还没有匹配恋人，先完成配对再试"
            raw.contains("cannot_match_self", ignoreCase = true) -> "不能使用自己的邀请码"
            raw.contains("below_minimum", ignoreCase = true) -> "加分后会低于最低分限制"
            raw.contains("above_maximum", ignoreCase = true) -> "加分后会超过最高分限制"
            raw.contains("add_delta_out_of_range", ignoreCase = true) -> "这次加分不在单次加分范围内"
            raw.contains("subtract_delta_out_of_range", ignoreCase = true) -> "这次扣分不在单次扣分范围内"
            raw.contains("range_does_not_include_current_score", ignoreCase = true) -> "新的上下限不包含当前分数"
            raw.contains("request_pending", ignoreCase = true) -> "已有一个等待对方处理的修改请求"
            raw.contains("request_already_handled", ignoreCase = true) -> "该修改请求已被处理"
            raw.contains("request_not_pending", ignoreCase = true) -> "该请求已不在等待状态"
            raw.contains("request_not_found", ignoreCase = true) -> "修改请求不存在"
            raw.contains("cannot_respond_own_request", ignoreCase = true) -> "不能处理自己发起的请求"
            raw.contains("note_too_long", ignoreCase = true) -> "备注最多 200 个字"
            raw.contains("display_name_too_long", ignoreCase = true) -> "昵称最多 40 个字"
            raw.contains("ignoreUnknownKeys", ignoreCase = true) || raw.contains("unknown key", ignoreCase = true) -> "App 版本过旧，请更新到最新版本"
            raw.contains("database_error", ignoreCase = true) || raw.contains("invite_failed", ignoreCase = true) || raw.contains("session_failed", ignoreCase = true) || raw.contains("password_hash_failed", ignoreCase = true) || raw.contains("request_failed", ignoreCase = true) -> "服务器开小差了，请稍后重试"
            raw.contains("invalid_credentials", ignoreCase = true) -> "账号或密码错误"
            raw.contains("username_taken", ignoreCase = true) -> "账号已被注册"
            raw.contains("initial_score_outside_range", ignoreCase = true) -> "初始分数不在上下限范围内"
            raw.isBlank() -> "操作失败，请稍后重试"
            else -> raw.substringBefore(" (Request").take(160)
        }
    }
}
