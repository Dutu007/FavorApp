package com.dutu007.favorapp.data

import android.content.Context
import com.dutu007.favorapp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable private data class AuthRequest(val username: String, val password: String, @SerialName("display_name") val displayName: String? = null)
@Serializable private data class InviteRequest(val code: String)
@Serializable private data class NicknameRequest(val nickname: String)
@Serializable private data class InviteCreateRequest(@SerialName("initial_score") val initial: Int, @SerialName("min_score") val min: Int?, @SerialName("max_score") val max: Int?, @SerialName("add_min") val addMin: Int, @SerialName("add_max") val addMax: Int, @SerialName("subtract_min") val subtractMin: Int, @SerialName("subtract_max") val subtractMax: Int)
@Serializable private data class ScoreRequest(val delta: Int, val note: String? = null, @SerialName("idempotency_key") val idempotencyKey: String)
@Serializable private data class RulesChangeRequest(@SerialName("add_min") val addMin: Int = 1, @SerialName("add_max") val addMax: Int = 5, @SerialName("subtract_min") val subtractMin: Int = 1, @SerialName("subtract_max") val subtractMax: Int = 5)
@Serializable private data class AuthResponse(val token: String, val user: UserDto)
@Serializable private data class UserDto(val id: String, val username: String, @SerialName("display_name") val displayName: String)
@Serializable private data class InviteResponse(val code: String)
@Serializable private data class ScoreCardDto(@SerialName("user_id") val userId: String, val name: String, val score: Int, @SerialName("avatar_version") val avatarVersion: Long = 0)
@Serializable private data class EventDto(val id: String, @SerialName("actor_id") val actorId: String = "", @SerialName("actor_name") val actorName: String, @SerialName("target_name") val targetName: String, val delta: Int, @SerialName("score_after") val scoreAfter: Int, val note: String? = null, @SerialName("created_at") val createdAt: String)
@Serializable private data class SettingsDto(@SerialName("initial_score") val initial: Int, @SerialName("min_score") val min: Int? = null, @SerialName("max_score") val max: Int? = null, @SerialName("add_min") val addMin: Int = 1, @SerialName("add_max") val addMax: Int = 5, @SerialName("subtract_min") val subtractMin: Int = 1, @SerialName("subtract_max") val subtractMax: Int = 5)
@Serializable private data class PendingRulesDto(val id: String, @SerialName("requester_id") val requesterId: String, @SerialName("initial_score") val initial: Int, @SerialName("min_score") val min: Int? = null, @SerialName("max_score") val max: Int? = null, @SerialName("add_min") val addMin: Int = 1, @SerialName("add_max") val addMax: Int = 5, @SerialName("subtract_min") val subtractMin: Int = 1, @SerialName("subtract_max") val subtractMax: Int = 5)
@Serializable private data class RuleDecisionDto(@SerialName("requester_id") val requesterId: String, val status: String, @SerialName("responded_at") val respondedAt: String)
@Serializable private data class CoupleDto(@SerialName("couple_id") val coupleId: String, @SerialName("current_user_id") val currentUserId: String, @SerialName("current_user_name") val currentUserName: String, @SerialName("partner_name") val partnerName: String, @SerialName("partner_nickname") val partnerNickname: String = "", val cards: List<ScoreCardDto>, val events: List<EventDto>, val settings: SettingsDto, @SerialName("pending_rules") val pendingRules: PendingRulesDto? = null, @SerialName("latest_rule_decision") val latestRuleDecision: RuleDecisionDto? = null)
@Serializable private data class ErrorDto(val error: String)
@Serializable private data class AppReleaseDto(val version_code: Int, val version_name: String, val notes: String = "", val sha256: String = "", val size: Long = 0)
@Serializable private data class GiftStepDto(val label: String, val at: String? = null)
@Serializable private data class GiftDto(val id: String, @SerialName("requester_id") val requesterId: String, @SerialName("requester_name") val requesterName: String, val title: String, val kind: String, val note: String? = null, val status: String, @SerialName("current_step") val currentStep: Int = 0, val steps: List<GiftStepDto> = emptyList(), @SerialName("cancelled_at") val cancelledAt: String? = null, @SerialName("created_at") val createdAt: String)
@Serializable private data class GiftGoalDto(@SerialName("user_id") val userId: String, @SerialName("target_score") val targetScore: Int)
@Serializable private data class GiftsResponseDto(val goals: List<GiftGoalDto> = emptyList(), val gifts: List<GiftDto> = emptyList())
@Serializable private data class GiftGoalRequest(@SerialName("target_score") val targetScore: Int)
@Serializable private data class GiftCreateRequest(val title: String, val kind: String, val note: String? = null)
@Serializable private data class GiftStepsRequest(val steps: List<String>)
@Serializable private data class GiftProgressRequest(@SerialName("current_step") val currentStep: Int)
@Serializable private data class GiftStatusRequest(val status: String)

class FavorRepository(context: Context) {
    private val preferences = context.getSharedPreferences("favorapp_session", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(Android) { install(ContentNegotiation) { json(json) } }
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun signIn(username: String, password: String) { saveAuth(client.post("$baseUrl/api/v1/auth/login") { json(AuthRequest(username, password)) }.bodyChecked<AuthResponse>()) }
    suspend fun signUp(username: String, password: String, displayName: String) { saveAuth(client.post("$baseUrl/api/v1/auth/register") { json(AuthRequest(username, password, displayName)) }.bodyChecked<AuthResponse>()) }
    suspend fun signOut() { client.post("$baseUrl/api/v1/auth/logout") { auth() }.check(); preferences.edit().clear().apply() }
    fun currentUserId(): String? = preferences.getString("user_id", null)
    fun clearSession() { preferences.edit().clear().apply() }
    suspend fun createInvite(rules: ScoreRule): String = client.post("$baseUrl/api/v1/invites") { auth(); json(InviteCreateRequest(rules.initialScore, rules.minScore, rules.maxScore, rules.addMin, rules.addMax, rules.subtractMin, rules.subtractMax)) }.bodyChecked<InviteResponse>().code
    suspend fun acceptInvite(code: String) { client.post("$baseUrl/api/v1/invites/accept") { auth(); json(InviteRequest(code)) }.check() }
    suspend fun addScore(delta: Int, note: String?, idempotencyKey: String) { client.post("$baseUrl/api/v1/scores/events") { auth(); json(ScoreRequest(delta, note, idempotencyKey)) }.bodyChecked<EventDto>() }
    suspend fun updateNickname(nickname: String) { client.put("$baseUrl/api/v1/couple/nickname") { auth(); json(NicknameRequest(nickname)) }.check() }
    suspend fun uploadAvatar(bytes: ByteArray) { client.put("$baseUrl/api/v1/me/avatar") { auth(); contentType(ContentType("image", "jpeg")); setBody(bytes) }.check() }
    suspend fun deleteAvatar() { client.delete("$baseUrl/api/v1/me/avatar") { auth() }.check() }
    fun avatarUrl(userId: String, version: Long): String? = if (version > 0) "$baseUrl/api/v1/users/$userId/avatar?v=$version" else null
    fun authToken(): String = preferences.getString("token", "").orEmpty()
    suspend fun createRulesRequest(addMin: Int, addMax: Int, subtractMin: Int, subtractMax: Int) { client.post("$baseUrl/api/v1/score-settings/requests") { auth(); json(RulesChangeRequest(addMin, addMax, subtractMin, subtractMax)) }.check() }
    suspend fun acceptRulesRequest(id: String) { client.post("$baseUrl/api/v1/score-settings/requests/$id/accept") { auth() }.check() }
    suspend fun rejectRulesRequest(id: String) { client.post("$baseUrl/api/v1/score-settings/requests/$id/reject") { auth() }.check() }
    suspend fun cancelRulesRequest(id: String) { client.post("$baseUrl/api/v1/score-settings/requests/$id/cancel") { auth() }.check() }
    suspend fun loadGifts(): GiftBoard {
        val dto = client.get("$baseUrl/api/v1/gifts") { auth() }.bodyChecked<GiftsResponseDto>()
        return GiftBoard(
            dto.goals.map { GiftGoalEntry(it.userId, it.targetScore) },
            dto.gifts.map { GiftItem(it.id, it.requesterId, it.requesterName, it.title, it.kind, it.note, it.status, it.currentStep, it.steps.map { s -> GiftStep(s.label, s.at) }, it.cancelledAt, it.createdAt) },
        )
    }
    suspend fun setGiftGoal(target: Int) { client.put("$baseUrl/api/v1/gifts/goal") { auth(); json(GiftGoalRequest(target)) }.check() }
    suspend fun createGift(title: String, kind: String, note: String?) { client.post("$baseUrl/api/v1/gifts") { auth(); json(GiftCreateRequest(title, kind, note)) }.check() }
    suspend fun updateGiftSteps(id: String, labels: List<String>) { client.put("$baseUrl/api/v1/gifts/$id/steps") { auth(); json(GiftStepsRequest(labels)) }.check() }
    suspend fun setGiftProgress(id: String, currentStep: Int) { client.post("$baseUrl/api/v1/gifts/$id/progress") { auth(); json(GiftProgressRequest(currentStep)) }.check() }
    suspend fun cancelGift(id: String) { client.post("$baseUrl/api/v1/gifts/$id/status") { auth(); json(GiftStatusRequest("cancelled")) }.check() }
    suspend fun latestRelease(): AppRelease { val dto = client.get("$baseUrl/api/v1/app/latest") { auth() }.bodyChecked<AppReleaseDto>(); return AppRelease(dto.version_code, dto.version_name, dto.notes, dto.sha256, dto.size) }
    suspend fun downloadApk(destination: File, onProgress: (Long, Long) -> Unit) {
        client.prepareGet("$baseUrl/api/v1/app/apk") { auth() }.execute { response ->
            if (response.status.value !in 200..299) throw ApiException(response.status.value, "download_failed")
            val total = response.contentLength() ?: -1L
            destination.parentFile?.mkdirs()
            val channel = response.bodyAsChannel()
            destination.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    received += read
                    onProgress(received, total)
                }
            }
        }
    }
    suspend fun loadSnapshot(from: String? = null, to: String? = null, keyword: String? = null): CoupleSnapshot? { val response = client.get("$baseUrl/api/v1/couple") { auth(); url { from?.takeIf { it.isNotBlank() }?.let { parameters.append("from", it) }; to?.takeIf { it.isNotBlank() }?.let { parameters.append("to", it) }; keyword?.takeIf { it.isNotBlank() }?.let { parameters.append("keyword", it) } } }; response.check(); val raw = response.bodyAsText().trim(); if (raw == "null") return null; return json.decodeFromString<CoupleDto>(raw).toSnapshot() }

    private fun saveAuth(auth: AuthResponse) { preferences.edit().putString("token", auth.token).putString("user_id", auth.user.id).apply() }
    private fun HttpRequestBuilder.auth() { header("Authorization", "Bearer ${preferences.getString("token", "")}") }
    private fun HttpRequestBuilder.json(value: Any) { contentType(ContentType.Application.Json); setBody(value) }
    private suspend inline fun <reified T> HttpResponse.bodyChecked(): T { check(); return body() }
    private suspend fun HttpResponse.check() { if (status.value !in 200..299) { val error = runCatching { body<ErrorDto>() }.getOrNull()?.error; throw ApiException(status.value, error ?: "request_failed") } }
    private fun CoupleDto.toSnapshot() = CoupleSnapshot(coupleId, currentUserId, currentUserName, partnerName, partnerNickname, cards.map { ScoreCard(it.userId, it.name, it.score, it.avatarVersion) }, events.map { ScoreEventItem(it.id, it.actorId, it.actorName, it.targetName, it.delta, it.scoreAfter, it.note, it.createdAt) }, ScoreSettingRow(coupleId, settings.initial, settings.min, settings.max, settings.addMin, settings.addMax, settings.subtractMin, settings.subtractMax), pendingRules?.let { PendingRuleRequest(it.id, it.requesterId, it.initial, it.min, it.max, it.addMin, it.addMax, it.subtractMin, it.subtractMax) }, latestRuleDecision?.let { RuleDecision(it.requesterId, it.status, it.respondedAt) })
}

class ApiException(val statusCode: Int, val code: String) : Exception(code)
