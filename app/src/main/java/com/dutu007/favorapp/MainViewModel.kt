package com.dutu007.favorapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dutu007.favorapp.data.CoupleSnapshot
import com.dutu007.favorapp.data.ApiException
import com.dutu007.favorapp.data.AppRelease
import com.dutu007.favorapp.data.AgreementItem
import com.dutu007.favorapp.data.FavorRepository
import com.dutu007.favorapp.data.GiftBoard
import com.dutu007.favorapp.data.GiftItem
import com.dutu007.favorapp.data.ScoreRule
import com.dutu007.favorapp.data.ScoreSettingRow
import com.dutu007.favorapp.data.ScorePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.joinAll
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
import java.time.LocalDate

enum class AuthMode { SIGN_IN, SIGN_UP }

const val NOTE_MAX_LENGTH = 200
const val PRESET_MAX_COUNT = 5
const val GIFT_HISTORY_PAGE_SIZE = 20
const val AGREEMENT_TITLE_MAX_LENGTH = 40
const val AGREEMENT_NOTE_MAX_LENGTH = 2000
const val AGREEMENTS_PAGE_SIZE = 40

data class AppUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val snapshotRefreshing: Boolean = false,
    val giftsRefreshing: Boolean = false,
    val giftHistoryRefreshing: Boolean = false,
    val giftHistoryLoadingMore: Boolean = false,
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
    val gifts: GiftBoard? = null,
    val giftTitle: String = "",
    val giftNote: String = "",
    val giftGoalDraft: String = "",
    val giftHistory: List<GiftItem> = emptyList(),
    val giftHistoryTotal: Int = 0,
    val giftHistoryHasMore: Boolean = false,
    val agreements: List<AgreementItem> = emptyList(),
    val agreementsPendingCount: Int = 0,
    val agreementsCompletedCount: Int = 0,
    val agreementsTotal: Int = 0,
    val agreementsHasMore: Boolean = false,
    val agreementsCompletedFilter: Boolean = false,
    val agreementsLoading: Boolean = false,
    val agreementsLoadingMore: Boolean = false,
    val agreementsBusy: Boolean = false,
    val agreementsError: String? = null,
    val agreementConflictItem: AgreementItem? = null,
    val agreementSavedFormKey: String? = null,
    val agreementDeletedId: String? = null,
    val notice: AppNotice? = null,
    val updateAvailable: AppRelease? = null,
    val updateDownloading: Boolean = false,
    val updateProgress: Int = 0,
    val updateReadyPath: String? = null,
)

class MainViewModel : ViewModel() {
    private val repository = FavorRepository(FavorApplication.instance)
    private val preferences = FavorApplication.instance.getSharedPreferences("favorapp_settings", android.content.Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private data class ReadContext(val token: String, val userId: String, val epoch: Long, val coupleId: String? = null)
    private class ReadRequest {
        var generation = 0L
        var key: Any? = null
        var context: ReadContext? = null
        var job: Job? = null
    }
    private var readEpoch = 0L
    private val snapshotRead = ReadRequest()
    private val giftBoardRead = ReadRequest()
    private val giftHistoryRead = ReadRequest()
    private var giftHistoryNextOffset = 0
    private var giftsRefreshGroup = false
    private var giftsRefreshJob: Job? = null

    private data class AgreementSession(val token: String, val userId: String, val coupleId: String)
    private data class AgreementContext(val session: AgreementSession, val epoch: Long)
    private var agreementSession: AgreementSession? = null
    private var agreementEpoch = 0L
    private var agreementListRequest = 0L
    private var agreementNextOffset = 0
    private var agreementRevision: String? = null
    private var agreementLoadJob: Job? = null
    private var agreementMutationJob: Job? = null

    init {
        loadPresets()
        refreshSession()
    }

    fun setAuthMode(mode: AuthMode) = _uiState.update { it.copy(authMode = mode, confirmPassword = "") }
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
        if (label.isBlank()) { _uiState.update { it.withNotice("请填写备注内容", isError = true) }; return }
        if (label.length > NOTE_MAX_LENGTH) { _uiState.update { it.withNotice("备注最多 $NOTE_MAX_LENGTH 个字", isError = true) }; return }
        if (delta == null || delta == 0) { _uiState.update { it.withNotice("请填写加分或扣分值，负数表示扣分", isError = true) }; return }
        if (!validatePresetDelta(delta)) { return }
        if (state.scorePresets.size >= PRESET_MAX_COUNT) { _uiState.update { it.withNotice("常用记录最多保存 $PRESET_MAX_COUNT 条", isError = true) }; return }
        savePresets(state.scorePresets + ScorePreset(UUID.randomUUID().toString(), label, delta))
        _uiState.update { it.copy(presetLabel = "", presetDelta = "").withNotice("预设项已添加") }
    }
    fun removePreset(id: String) {
        savePresets(_uiState.value.scorePresets.filterNot { it.id == id })
        _uiState.update { it.withNotice("预设项已删除") }
    }
    fun validateManualDelta(): Int? {
        val settings = _uiState.value.snapshot?.settings
        val value = _uiState.value.manualDelta.toIntOrNull()
        if (value == null || value == 0) {
            _uiState.update { it.withNotice("请输入加分或扣分值，负数表示扣分", isError = true) }; return null
        }
        if (settings == null) return value
        if (!deltaWithinRules(value, settings)) {
            _uiState.update { it.withNotice(deltaRangeError(value, settings), isError = true) }; return null
        }
        return value
    }
    fun validatePresetDelta(delta: Int): Boolean {
        val settings = _uiState.value.snapshot?.settings
        if (delta == 0) { _uiState.update { it.withNotice("预设分值不能为 0", isError = true) }; return false }
        if (settings == null) return true
        if (!deltaWithinRules(delta, settings)) {
            _uiState.update { it.withNotice(deltaRangeError(delta, settings), isError = true) }; return false
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
    fun clearNotice() = _uiState.update { it.copy(notice = null) }
    fun dismissNotice(id: String) = _uiState.update { it.withoutNotice(id) }

    fun savePartnerNickname(nickname: String): Boolean {
        val value = nickname.trim()
        if (value.length > 8) { _uiState.update { it.withNotice("昵称最多 8 个字", isError = true) }; return false }
        runBusy { repository.updateNickname(value); _uiState.update { it.withNotice("恋人昵称已保存") }; loadSnapshot() }
        return true
    }

    fun avatarUrlFor(userId: String, version: Long): String? = repository.avatarUrl(userId, version)
    val authToken: String get() = repository.authToken()
    val currentVersionName: String get() = com.dutu007.favorapp.BuildConfig.VERSION_NAME

    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            try {
                val release = repository.latestRelease()
                if (release.versionCode > com.dutu007.favorapp.BuildConfig.VERSION_CODE) {
                    _uiState.update { it.copy(updateAvailable = release) }
                } else if (manual) {
                    _uiState.update { it.withNotice("已是最新版本 $currentVersionName") }
                }
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) clearAuthenticatedSession()
                if (manual) {
                    val text = if (error is ApiException && error.statusCode == 404) "服务器还没有可下载的版本" else error.userMessage()
                    _uiState.update { it.withNotice(text, isError = true) }
                }
            }
        }
    }

    fun dismissUpdate() = _uiState.update { it.copy(updateAvailable = null) }

    fun noteInstallPermissionNeeded() = _uiState.update { it.withNotice("请先允许安装未知应用，返回后再点一次“立即更新”", isError = true) }

    fun downloadUpdate(context: Context) {
        val release = _uiState.value.updateAvailable ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(updateDownloading = true, updateProgress = 0) }
            try {
                val dir = java.io.File(context.cacheDir, "updates").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val target = java.io.File(dir, "favor-${release.versionName}.apk")
                withContext(Dispatchers.IO) { repository.downloadApk(target) { received, total -> if (total > 0) _uiState.update { s -> s.copy(updateProgress = (received * 100 / total).toInt()) } } }
                if (release.sha256.isNotBlank() && !target.sha256().equals(release.sha256, ignoreCase = true)) {
                    target.delete()
                    throw IllegalStateException("安装包校验失败，请重新下载")
                }
                _uiState.update { it.copy(updateDownloading = false, updateReadyPath = target.absolutePath, updateAvailable = null) }
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) clearAuthenticatedSession()
                _uiState.update { it.copy(updateDownloading = false).withNotice(error.message ?: "下载失败，请稍后重试", isError = true) }
            }
        }
    }

    fun consumeReadyUpdate() = _uiState.update { it.copy(updateReadyPath = null) }

    fun uploadAvatarFromUri(context: Context, uri: Uri) {
        runBusy {
            val bytes = withContext(Dispatchers.IO) { processAvatar(context, uri) }
            if (bytes == null) {
                _uiState.update { it.withNotice("无法读取所选图片，换一张试试", isError = true) }
                return@runBusy
            }
            repository.uploadAvatar(bytes)
            _uiState.update { it.withNotice("头像已更新") }
            loadSnapshot()
        }
    }

    fun deleteAvatar() {
        runBusy {
            repository.deleteAvatar()
            _uiState.update { it.withNotice("头像已移除") }
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
        val context = readContext()
        if (context == null) {
            clearAuthenticatedSession()
            _uiState.update { it.copy(loading = false) }
            return
        }
        if (_uiState.value.loading && _uiState.value.snapshotRefreshing) return
        _uiState.update { it.copy(loading = true) }
        startSnapshotRead()?.invokeOnCompletion {
            if (isReadContextCurrent(context) && _uiState.value.authenticated) checkForUpdate()
        }
    }

    fun refreshSnapshot(keyword: String = "", date: String = "") {
        if (repository.currentUserId() == null) {
            clearAuthenticatedSession()
            _uiState.update { it.copy(loading = false, authenticated = false, snapshot = null) }
            return
        }
        startSnapshotRead(keyword, date)
    }

    fun submitAuth() {
        val state = _uiState.value
        if (!state.email.matches(Regex("^[a-z][a-z0-9_]{2,19}$"))) {
            _uiState.update { it.withNotice("账号需为 3-20 位字母、数字或下划线，且以字母开头", isError = true) }
            return
        }
        if (state.password.length !in 8..64 || !state.password.any { it.isUpperCase() } || !state.password.any { it.isLowerCase() } || !state.password.any { it.isDigit() } || state.password.all { it.isLetterOrDigit() }) {
            _uiState.update { it.withNotice("密码需为 8-64 位，并包含大写字母、小写字母、数字和特殊字符", isError = true) }
            return
        }
        if (state.authMode == AuthMode.SIGN_UP && state.displayName.isBlank()) {
            _uiState.update { it.withNotice("注册时请填写昵称", isError = true) }
            return
        }
        if (state.authMode == AuthMode.SIGN_UP && state.password != state.confirmPassword) {
            _uiState.update { it.withNotice("两次输入的密码不一致", isError = true) }
            return
        }

        runBusy {
            if (state.authMode == AuthMode.SIGN_IN) {
                repository.signIn(state.email, state.password)
                resetReadRequests()
                resetAgreementSession()
                _uiState.update { it.copy(loading = true) }
                loadSnapshot()
            } else {
                repository.signUp(state.email, state.password, state.displayName)
                clearAuthenticatedSession()
                _uiState.update {
                    it.copy(
                        authMode = AuthMode.SIGN_IN,
                        password = "",
                        confirmPassword = "",
                        displayName = "",
                        authenticated = false,
                        snapshot = null,
                    ).withNotice("注册成功，请使用新账号登录")
                }
            }
        }
    }

    fun signOut() {
        runBusy {
            repository.signOut()
            resetReadRequests()
            resetScoreKey()
            resetAgreementSession()
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
            _uiState.update { it.withNotice("请完整填写分数规则", isError = true) }; return
        }
        if ((min != null && max != null && min > max) || (min != null && initial < min) || (max != null && initial > max)) {
            _uiState.update { it.withNotice("初始分数必须在总分上下限内", isError = true) }; return
        }
        val addMin = state.addMin.toIntOrNull()
        val addMax = state.addMax.toIntOrNull()
        val subtractMin = state.subtractMin.toIntOrNull()
        val subtractMax = state.subtractMax.toIntOrNull()
        if (addMin == null || addMax == null || subtractMin == null || subtractMax == null) {
            _uiState.update { it.withNotice("请填写单次加分和扣分范围", isError = true) }; return
        }
        if (addMin !in 1..100 || addMax !in addMin..100) {
            _uiState.update { it.withNotice("单次加分绝对值范围需为 1-100，且最小值不能大于最大值", isError = true) }; return
        }
        if (subtractMin !in 1..100 || subtractMax !in subtractMin..100) {
            _uiState.update { it.withNotice("单次扣分绝对值范围需为 1-100，且最小值不能大于最大值", isError = true) }; return
        }
        val rules = ScoreRule(initial, min, max, addMin, addMax, subtractMin, subtractMax)
        runBusy {
            val code = repository.createInvite(rules)
            _uiState.update { it.copy(generatedInvite = code).withNotice("邀请码已生成，复制后发送给对方") }
        }
    }

    fun enterCouple() {
        refreshSnapshot()
        if (_uiState.value.snapshot == null) {
            _uiState.update { it.withNotice("还在等待对方输入邀请码，请稍后再试") }
        }
    }

    fun acceptInvite() {
        val code = _uiState.value.inviteCode.trim()
        if (code.isBlank()) {
            _uiState.update { it.withNotice("请输入邀请码", isError = true) }
            return
        }
        runBusy {
            repository.acceptInvite(code)
            _uiState.update { it.copy(inviteCode = "", generatedInvite = null).withNotice("匹配成功") }
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
            _uiState.update { it.withNotice("暂时找不到恋人账户", isError = true) }
            return
        }
        if (note != null && note.length > NOTE_MAX_LENGTH) {
            _uiState.update { it.withNotice("备注最多 $NOTE_MAX_LENGTH 个字", isError = true) }
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
            _uiState.update { it.withNotice("单次加减分范围必须是整数", isError = true) }
            return false
        }
        if (addMin !in 1..100 || addMax !in addMin..100) {
            _uiState.update { it.withNotice("单次加分范围需为正数1~100，且最小值不能大于最大值", isError = true) }
            return false
        }
        if (subtractMin !in 1..100 || subtractMax !in subtractMin..100) {
            _uiState.update { it.withNotice("单次扣分范围需为正数1~100，且最小值不能大于最大值", isError = true) }
            return false
        }
        runBusy {
            repository.createRulesRequest(addMin, addMax, subtractMin, subtractMax)
            _uiState.update { it.withNotice("修改请求已发送，等待对方同意") }
            loadSnapshot()
        }
        return true
    }

    fun acceptRulesRequest() {
        val id = _uiState.value.snapshot?.pendingRules?.id ?: return
        runBusy {
            repository.acceptRulesRequest(id)
            _uiState.update { it.withNotice("已同意对方的记分规则修改") }
            loadSnapshot()
        }
    }

    fun rejectRulesRequest() {
        val id = _uiState.value.snapshot?.pendingRules?.id ?: return
        runBusy {
            repository.rejectRulesRequest(id)
            _uiState.update { it.withNotice("已拒绝对方的记分规则修改") }
            loadSnapshot()
        }
    }

    fun cancelRulesRequest() {
        val id = _uiState.value.snapshot?.pendingRules?.id ?: return
        runBusy {
            repository.cancelRulesRequest(id)
            _uiState.update { it.withNotice("修改请求已撤销") }
            loadSnapshot()
        }
    }

    // Gift rewards live on their own board: a shared score goal plus one
    // in-flight gift whose timeline both partners update by hand.
    fun setGiftTitle(value: String) = _uiState.update { it.copy(giftTitle = value) }
    fun setGiftNote(value: String) = _uiState.update { it.copy(giftNote = value.take(NOTE_MAX_LENGTH)) }
    fun setGiftGoalDraft(value: String) = _uiState.update { it.copy(giftGoalDraft = value.filter(Char::isDigit)) }

    fun loadGifts() { startGiftBoardRead() }

    // The reward page needs both the score snapshot and the gift board. Keep
    // its refresh indicator visible until both reads have settled.
    fun refreshGifts() {
        val context = readContext(requireCouple = true) ?: return
        if (giftsRefreshJob?.isActive == true) return
        giftsRefreshGroup = true
        _uiState.update { it.copy(giftsRefreshing = true) }
        giftsRefreshJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                listOfNotNull(startSnapshotRead(), startGiftBoardRead()).joinAll()
            } finally {
                if (isReadContextCurrent(context)) {
                    giftsRefreshGroup = false
                    _uiState.update { it.copy(giftsRefreshing = giftBoardRead.job?.isActive == true) }
                }
            }
        }
        giftsRefreshJob?.start()
    }

    fun saveGiftGoal() {
        val target = _uiState.value.giftGoalDraft.toIntOrNull()
        if (target == null || target !in 1..1000000) {
            _uiState.update { it.withNotice("目标分数需为 1-1000000 的整数", isError = true) }
            return
        }
        runBusy {
            repository.setGiftGoal(target)
            _uiState.update { it.copy(giftGoalDraft = "").withNotice("TA 的目标已更新") }
            startGiftBoardRead(force = true)
        }
    }

    fun submitGiftRedemption() {
        val state = _uiState.value
        val title = state.giftTitle.trim()
        if (title.isEmpty()) { _uiState.update { it.withNotice("请填写想要的礼物", isError = true) }; return }
        if (title.length > 40) { _uiState.update { it.withNotice("礼物名称最多 40 个字", isError = true) }; return }
        if (state.giftNote.length > NOTE_MAX_LENGTH) { _uiState.update { it.withNotice("备注最多 $NOTE_MAX_LENGTH 个字", isError = true) }; return }
        runBusy {
            repository.createGift(title, state.giftNote.trim().takeIf { it.isNotEmpty() })
            _uiState.update { it.copy(giftTitle = "", giftNote = "").withNotice("兑换已提交，等待对方确认") }
            startGiftBoardRead(force = true)
        }
    }

    // Full history screen: server-side pages of finished gifts, newest first.
    fun loadGiftHistory(reset: Boolean, force: Boolean = false) {
        val state = _uiState.value
        if (!reset && (state.giftHistoryRefreshing || state.giftHistoryLoadingMore || !state.giftHistoryHasMore)) return
        val offset = if (reset) 0 else giftHistoryNextOffset
        startRead(
            request = giftHistoryRead, key = offset, context = readContext(requireCouple = true),
            force = force,
            loading = { current, active ->
                current.copy(giftHistoryRefreshing = reset && active, giftHistoryLoadingMore = !reset && active)
            },
        ) { isCurrent ->
            val page = repository.loadGiftHistory(offset, GIFT_HISTORY_PAGE_SIZE)
            if (isCurrent()) {
                giftHistoryNextOffset = offset + page.gifts.size
                _uiState.update {
                    val merged = if (offset == 0) page.gifts else (it.giftHistory + page.gifts).distinctBy { gift -> gift.id }
                    it.copy(
                        giftHistory = merged,
                        giftHistoryTotal = page.total,
                        giftHistoryHasMore = page.gifts.isNotEmpty() && giftHistoryNextOffset < page.total,
                    )
                }
            }
        }
    }

    // Either partner may delete a finished record after confirming twice.
    fun deleteGift(gift: GiftItem) {
        runBusy {
            repository.deleteGift(gift.id)
            _uiState.update { it.withNotice("记录已删除") }
            startGiftBoardRead(force = true)
            loadGiftHistory(true, force = true)
        }
    }

    // Either partner can rename/add/remove nodes and pick how far the timeline
    // has gotten; finishing every node completes the gift.
    // The preparer accepts the wish, lays out the nodes and drives progress;
    // the requester watches and may withdraw the request.
    fun acceptGift(gift: GiftItem) {
        runBusy {
            repository.acceptGift(gift.id)
            _uiState.update { it.withNotice("已同意，安排一下进度节点吧") }
            startGiftBoardRead(force = true)
        }
    }

    fun updateGiftSteps(gift: GiftItem, labels: List<String>) {
        val cleaned = labels.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) { _uiState.update { it.withNotice("至少保留一个进度节点", isError = true) }; return }
        if (cleaned.size > 8) { _uiState.update { it.withNotice("节点最多 8 个", isError = true) }; return }
        if (cleaned.any { it.length > 12 }) { _uiState.update { it.withNotice("节点文字最多 12 个字", isError = true) }; return }
        runBusy {
            repository.updateGiftSteps(gift.id, cleaned)
            _uiState.update { it.withNotice("礼物节点已更新") }
            startGiftBoardRead(force = true)
        }
    }

    fun setGiftProgress(gift: GiftItem, position: Int) {
        if (position < 0 || position > gift.steps.size) return
        runBusy {
            repository.setGiftProgress(gift.id, position)
            _uiState.update { it.withNotice(if (position == gift.steps.size) "礼物送达，这一阶段圆满啦" else "礼物进度已更新") }
            startGiftBoardRead(force = true)
        }
    }

    fun cancelGift(gift: GiftItem) {
        runBusy {
            repository.cancelGift(gift.id)
            _uiState.update { it.withNotice("兑换已取消") }
            startGiftBoardRead(force = true)
        }
    }

    fun clearAgreementError() = _uiState.update { it.copy(agreementsError = null) }
    fun clearAgreementConflict() = _uiState.update { it.copy(agreementConflictItem = null) }
    fun clearAgreementSavedFormEvent() = _uiState.update { it.copy(agreementSavedFormKey = null) }
    fun clearAgreementDeletedEvent() = _uiState.update { it.copy(agreementDeletedId = null) }

    fun selectAgreementFilter(completed: Boolean) {
        if (_uiState.value.agreementsCompletedFilter == completed) return
        invalidateAgreementLoad()
        agreementNextOffset = 0
        agreementRevision = null
        _uiState.update {
            it.copy(
                agreementsCompletedFilter = completed,
                agreements = emptyList(),
                agreementsTotal = if (completed) it.agreementsCompletedCount else it.agreementsPendingCount,
                agreementsHasMore = false,
                agreementsError = null,
            )
        }
        loadAgreements()
    }

    fun loadAgreements(refresh: Boolean = true) = startAgreementLoad(refresh, clearError = true)

    private fun startAgreementLoad(refresh: Boolean, clearError: Boolean, conflictId: String? = null) {
        val context = agreementContext() ?: return
        val state = _uiState.value
        if (state.agreementsBusy || state.agreementsLoading || (!refresh && (state.agreementsLoadingMore || !state.agreementsHasMore))) return
        invalidateAgreementLoad()
        val completed = state.agreementsCompletedFilter
        val offset = if (refresh) 0 else agreementNextOffset
        val expectedRevision = agreementRevision
        val request = agreementListRequest
        _uiState.update {
            it.copy(
                agreementsLoading = refresh,
                agreementsLoadingMore = !refresh,
                agreementsError = if (clearError) null else it.agreementsError,
            )
        }
        agreementLoadJob = viewModelScope.launch {
            try {
                val page = repository.loadAgreements(completed, offset, AGREEMENTS_PAGE_SIZE)
                if (!isAgreementRequestCurrent(context, request, completed)) return@launch
                if (offset > 0 && page.revision != expectedRevision) {
                    // A partner's edit can shift the server's sorted pages.
                    // Restart rather than skipping or repeating records.
                    agreementLoadJob = null
                    invalidateAgreementLoad()
                    agreementNextOffset = 0
                    agreementRevision = null
                    _uiState.update {
                        it.copy(agreements = emptyList(), agreementsHasMore = false)
                    }
                    startAgreementLoad(refresh = true, clearError = false)
                    return@launch
                }
                agreementNextOffset = offset + page.items.size
                agreementRevision = page.revision
                _uiState.update {
                    it.copy(
                        agreements = if (offset == 0) page.items else (it.agreements + page.items).distinctBy { item -> item.id },
                        agreementsPendingCount = page.pendingCount,
                        agreementsCompletedCount = page.completedCount,
                        agreementsTotal = page.total,
                        agreementsHasMore = page.items.isNotEmpty() && agreementNextOffset < page.total,
                    )
                }
                if (conflictId != null) {
                    val latest = try {
                        repository.loadAgreement(conflictId)
                    } catch (error: ApiException) {
                        if (error.code == "agreement_not_found") null else throw error
                    }
                    if (isAgreementRequestCurrent(context, request, completed)) {
                        _uiState.update { it.copy(agreementConflictItem = latest) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isAgreementRequestCurrent(context, request, completed)) handleAgreementError(error)
            } finally {
                if (isAgreementRequestCurrent(context, request, completed)) {
                    _uiState.update { it.copy(agreementsLoading = false, agreementsLoadingMore = false) }
                }
            }
        }
    }

    fun saveAgreement(
        item: AgreementItem?,
        title: String,
        note: String,
        dueDate: String?,
        idempotencyKey: String,
    ) {
        if (_uiState.value.agreementsBusy) return
        val cleanedTitle = title.trim()
        val cleanedDate = dueDate?.trim()?.takeIf { it.isNotEmpty() }
        val validationError = when {
            cleanedTitle.isEmpty() || cleanedTitle.codePointCount(0, cleanedTitle.length) > AGREEMENT_TITLE_MAX_LENGTH -> "请填写约定名称（最多 $AGREEMENT_TITLE_MAX_LENGTH 个字）"
            note.codePointCount(0, note.length) > AGREEMENT_NOTE_MAX_LENGTH -> "备注最多 $AGREEMENT_NOTE_MAX_LENGTH 个字"
            cleanedDate != null && !validAgreementDate(cleanedDate) -> "希望完成日期需为有效的 YYYY-MM-DD 日期"
            idempotencyKey.isBlank() -> "保存约定失败，请重新打开表单再试"
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(agreementsError = validationError).withNotice(validationError, isError = true) }
            return
        }
        runAgreementMutation(item?.id) { context ->
            val saved = if (item == null) {
                repository.createAgreement(cleanedTitle, note, cleanedDate, idempotencyKey)
            } else {
                repository.updateAgreement(item, cleanedTitle, note, cleanedDate, item.completed)
            }
            if (isAgreementContextCurrent(context)) {
                if (item == null && (saved.title != cleanedTitle || saved.note != note || saved.dueDate != cleanedDate)) {
                    // The first POST may have succeeded even if its response was
                    // lost. An edited retry still uses that form's original key.
                    val text = "这条约定已创建，当前输入尚未保存。请选择最新内容或保留你的修改。"
                    _uiState.update {
                        it.copy(
                            agreementConflictItem = saved,
                            agreementsError = text,
                            agreementSavedFormKey = null,
                        ).withNotice(text, isError = true)
                    }
                } else {
                    _uiState.update {
                        it.copy(agreementSavedFormKey = idempotencyKey)
                            .withNotice(if (item == null) "约定已添加" else "约定已保存")
                    }
                }
            }
        }
    }

    fun setAgreementCompleted(item: AgreementItem, completed: Boolean) {
        if (item.completed == completed) return
        runAgreementMutation(item.id) { context ->
            val updated = repository.updateAgreement(item, item.title, item.note, item.dueDate, completed)
            if (isAgreementContextCurrent(context)) {
                _uiState.update {
                    it.withNotice(
                        text = if (completed) "我们完成啦" else "约定已恢复为待完成",
                        undoAgreement = if (completed) updated else null,
                    )
                }
            }
        }
    }

    fun deleteAgreement(item: AgreementItem) {
        runAgreementMutation(item.id) { context ->
            repository.deleteAgreement(item)
            if (isAgreementContextCurrent(context)) {
                _uiState.update {
                    it.copy(agreementDeletedId = item.id).withNotice("约定已删除")
                }
            }
        }
    }

    fun undoLastAgreementCompletion() {
        val item = _uiState.value.notice?.undoAgreement ?: return
        setAgreementCompleted(item, false)
    }

    private fun runAgreementMutation(itemId: String?, block: suspend (AgreementContext) -> Unit) {
        val context = agreementContext() ?: return
        if (_uiState.value.agreementsBusy) return
        invalidateAgreementLoad()
        _uiState.update { it.copy(agreementsBusy = true, agreementsError = null) }
        agreementMutationJob = viewModelScope.launch {
            var refresh = false
            var conflictId: String? = null
            try {
                block(context)
                refresh = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isAgreementContextCurrent(context)) {
                    handleAgreementError(error)
                    if (error is ApiException && (error.statusCode == 409 || error.code == "agreement_not_found")) {
                        refresh = true
                        conflictId = itemId.takeIf { error.code == "agreement_conflict" }
                        _uiState.update {
                            it.copy(agreementConflictItem = null)
                        }
                    }
                }
            } finally {
                if (isAgreementContextCurrent(context)) {
                    _uiState.update { it.copy(agreementsBusy = false) }
                    if (refresh) {
                        invalidateAgreementLoad()
                        startAgreementLoad(refresh = true, clearError = false, conflictId = conflictId)
                    }
                }
            }
        }
    }

    private fun validAgreementDate(value: String): Boolean =
        value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) &&
            runCatching { LocalDate.parse(value).year in 1..9999 }.getOrDefault(false)

    private fun currentAgreementSession(): AgreementSession? {
        val userId = repository.currentUserId() ?: return null
        val snapshot = _uiState.value.snapshot ?: return null
        if (!_uiState.value.authenticated || snapshot.currentUserId != userId) return null
        return AgreementSession(repository.authToken(), userId, snapshot.coupleId)
    }

    private fun agreementContext(): AgreementContext? {
        val session = currentAgreementSession()
        if (session == null) {
            resetAgreementSession()
            return null
        }
        if (agreementSession != session) {
            val selectedFilter = if (agreementSession == null) _uiState.value.agreementsCompletedFilter else false
            resetAgreementSession()
            agreementSession = session
            _uiState.update { it.copy(agreementsCompletedFilter = selectedFilter) }
        }
        return AgreementContext(session, agreementEpoch)
    }

    private fun isAgreementContextCurrent(context: AgreementContext): Boolean =
        context.epoch == agreementEpoch && context.session == currentAgreementSession()

    private fun isAgreementRequestCurrent(context: AgreementContext, request: Long, completed: Boolean): Boolean =
        isAgreementContextCurrent(context) && request == agreementListRequest && completed == _uiState.value.agreementsCompletedFilter

    private fun invalidateAgreementLoad() {
        agreementListRequest++
        agreementLoadJob?.cancel()
        agreementLoadJob = null
        _uiState.update { it.copy(agreementsLoading = false, agreementsLoadingMore = false) }
    }

    private fun resetAgreementSession() {
        agreementEpoch++
        agreementSession = null
        invalidateAgreementLoad()
        agreementMutationJob?.cancel()
        agreementMutationJob = null
        agreementNextOffset = 0
        agreementRevision = null
        _uiState.update {
            it.copy(
                agreements = emptyList(), agreementsPendingCount = 0, agreementsCompletedCount = 0,
                agreementsTotal = 0, agreementsHasMore = false, agreementsCompletedFilter = false,
                agreementsBusy = false, agreementsError = null, agreementConflictItem = null,
                agreementSavedFormKey = null, agreementDeletedId = null,
                notice = it.notice?.takeIf { notice -> notice.undoAgreement == null },
            )
        }
    }

    private fun clearAuthenticatedSession() {
        repository.clearSession()
        resetScoreKey()
        resetReadRequests()
        resetAgreementSession()
        _uiState.update {
            it.copy(
                authenticated = false,
                snapshot = null,
                gifts = null,
                giftHistory = emptyList(),
                giftHistoryTotal = 0,
                notice = null,
            )
        }
    }

    private fun handleAgreementError(error: Exception) {
        if (error is ApiException && error.statusCode == 401) {
            clearAuthenticatedSession()
            _uiState.update { it.withNotice("登录已过期，请重新登录", isError = true) }
        } else {
            val text = error.userMessage()
            _uiState.update { it.copy(agreementsError = text).withNotice(text, isError = true) }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is ApiException && error.statusCode == 401) {
                    clearAuthenticatedSession()
                }
                _uiState.update { it.withNotice(error.userMessage(), isError = true) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private fun loadSnapshot(keyword: String = "", date: String = "") {
        startSnapshotRead(keyword, date, force = true)
    }

    private fun startSnapshotRead(keyword: String = "", date: String = "", force: Boolean = false): Job? =
        startRead(
            request = snapshotRead, key = keyword to date, context = readContext(), force = force,
            loading = { current, active -> current.copy(snapshotRefreshing = active, loading = current.loading && active) },
        ) { isCurrent ->
            val snapshot = repository.loadSnapshot(from = date, to = date, keyword = keyword)
            if (isCurrent()) {
                if (_uiState.value.snapshot?.coupleId != snapshot?.coupleId) resetGiftReadRequests()
                notifyRuleDecision(snapshot)
                _uiState.update { it.copy(loading = false, authenticated = true, snapshot = snapshot) }
                if (agreementSession != null && agreementSession != currentAgreementSession()) resetAgreementSession()
            }
        }

    private fun startGiftBoardRead(force: Boolean = false): Job? =
        startRead(
            request = giftBoardRead, key = Unit, context = readContext(requireCouple = true), force = force,
            loading = { current, active -> current.copy(giftsRefreshing = active || giftsRefreshGroup) },
        ) { isCurrent ->
            val board = repository.loadGifts()
            if (isCurrent()) _uiState.update { it.copy(gifts = board) }
        }

    // Read requests own their loading state. A superseded request cannot clear
    // a newer indicator or apply a response from an older account or filter.
    private fun startRead(
        request: ReadRequest,
        key: Any,
        context: ReadContext?,
        force: Boolean = false,
        loading: (AppUiState, Boolean) -> AppUiState,
        block: suspend (() -> Boolean) -> Unit,
    ): Job? {
        context ?: return null
        if (!force && request.job?.isActive == true && request.key == key && request.context == context) return request.job
        invalidateRead(request)
        request.key = key
        request.context = context
        val generation = request.generation
        val isCurrent = { request.generation == generation && isReadContextCurrent(context) }
        _uiState.update { loading(it, true) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block(isCurrent)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent()) {
                    val text = if (error is ApiException && error.statusCode == 401) {
                        clearAuthenticatedSession()
                        "登录已过期，请重新登录"
                    } else error.userMessage()
                    _uiState.update { it.withNotice(text, isError = true) }
                }
            } finally {
                if (isCurrent()) _uiState.update { loading(it, false) }
            }
        }
        request.job = job
        job.start()
        return job
    }

    private fun readContext(requireCouple: Boolean = false): ReadContext? {
        val userId = repository.currentUserId() ?: return null
        val coupleId = if (requireCouple) _uiState.value.snapshot?.coupleId ?: return null else null
        return ReadContext(repository.authToken(), userId, readEpoch, coupleId)
    }

    private fun isReadContextCurrent(context: ReadContext): Boolean =
        context.epoch == readEpoch && context.token == repository.authToken() && context.userId == repository.currentUserId() &&
            (context.coupleId == null || context.coupleId == _uiState.value.snapshot?.coupleId)

    private fun invalidateRead(request: ReadRequest) {
        request.generation++
        request.job?.cancel()
        request.job = null
        request.context = null
    }

    private fun resetGiftReadRequests() {
        invalidateRead(giftBoardRead)
        invalidateRead(giftHistoryRead)
        giftsRefreshJob?.cancel()
        giftsRefreshJob = null
        giftsRefreshGroup = false
        giftHistoryNextOffset = 0
        _uiState.update {
            it.copy(
                gifts = null, giftHistory = emptyList(), giftHistoryTotal = 0, giftHistoryHasMore = false,
                giftsRefreshing = false, giftHistoryRefreshing = false, giftHistoryLoadingMore = false,
            )
        }
    }

    private fun resetReadRequests() {
        readEpoch++
        invalidateRead(snapshotRead)
        resetGiftReadRequests()
        _uiState.update { it.copy(snapshot = null, authenticated = false, loading = false, snapshotRefreshing = false) }
    }

    // Show the outcome of a rule change request once per decision, remembered across restarts.
    private fun notifyRuleDecision(snapshot: CoupleSnapshot?) {
        val decision = snapshot?.latestRuleDecision ?: return
        val at = runCatching { java.time.OffsetDateTime.parse(decision.respondedAt).toInstant().toEpochMilli() }.getOrNull() ?: return
        if (at <= preferences.getLong("rules_decision_seen_at", 0L)) return
        preferences.edit().putLong("rules_decision_seen_at", at).apply()
        if (decision.requesterId == snapshot.currentUserId) {
            val text = if (decision.status == "accepted") "对方同意了你的记分规则修改" else "对方拒绝了你的记分规则修改"
            _uiState.update { it.withNotice(text) }
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
            raw.contains("agreement_conflict", ignoreCase = true) -> "这条约定刚刚被对方修改了，请查看最新内容后再保存"
            raw.contains("agreement_not_found", ignoreCase = true) -> "这条约定已被删除，请刷新清单"
            raw.contains("invalid_agreement_title", ignoreCase = true) -> "请填写约定名称（最多 $AGREEMENT_TITLE_MAX_LENGTH 个字）"
            raw.contains("agreement_note_too_long", ignoreCase = true) -> "备注最多 $AGREEMENT_NOTE_MAX_LENGTH 个字"
            raw.contains("invalid_agreement_date", ignoreCase = true) -> "希望完成日期需为有效的 YYYY-MM-DD 日期"
            raw.contains("invalid_agreement_request", ignoreCase = true) -> "约定内容无效，请检查后再试"
            raw.contains("note_too_long", ignoreCase = true) -> "备注最多 200 个字"
            raw.contains("score_below_goal", ignoreCase = true) -> "分数还没达到目标，继续加油"
            raw.contains("goal_not_set", ignoreCase = true) -> "对方还没给你设置目标，提醒 TA 一下吧"
            raw.contains("invalid_gift_goal", ignoreCase = true) -> "目标分数需为 1-1000000 的整数"
            raw.contains("invalid_gift_title", ignoreCase = true) -> "请填写礼物名称（最多 40 个字）"
            raw.contains("gift_delete_not_finished", ignoreCase = true) -> "进行中的礼物不能删除，先完成或取消"
            raw.contains("gift_already_active", ignoreCase = true) -> "你已经有一份礼物在进行中，完成后再兑换下一份"
            raw.contains("gift_already_finished", ignoreCase = true) -> "这份礼物已经完成或取消了"
            raw.contains("gift_already_handled", ignoreCase = true) -> "这份心愿已经处理过了"
            raw.contains("gift_accept_by_giver_only", ignoreCase = true) -> "只能由准备礼物的一方同意"
            raw.contains("gift_edit_by_giver_only", ignoreCase = true) -> "进度由准备礼物的一方编辑哦"
            raw.contains("gift_not_accepted", ignoreCase = true) -> "先同意这份心愿，再安排进度"
            raw.contains("gift_no_steps", ignoreCase = true) -> "先编辑进度节点，再调整进度"
            raw.contains("cannot_cancel_other_gift", ignoreCase = true) -> "只能取消自己兑换的礼物"
            raw.contains("gift_steps_invalid", ignoreCase = true) -> "进度节点需为 1-8 个，每个最多 12 个字"
            raw.contains("gift_step_out_of_range", ignoreCase = true) -> "礼物进度刚刚变了，刷新后再试"
            raw.contains("gift_not_found", ignoreCase = true) -> "礼物不存在"
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

private fun java.io.File.sha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    inputStream().use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
