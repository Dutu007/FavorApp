package com.dutu007.favorapp.data

import android.content.Context
import com.dutu007.favorapp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable private data class AuthRequest(val username: String, val password: String, @SerialName("display_name") val displayName: String? = null)
@Serializable private data class InviteRequest(val code: String)
@Serializable private data class ScoreRequest(val delta: Int, val note: String? = null, @SerialName("idempotency_key") val idempotencyKey: String)
@Serializable private data class SettingsRequest(@SerialName("initial_score") val initial: Int, @SerialName("min_score") val min: Int? = null, @SerialName("max_score") val max: Int? = null)
@Serializable private data class AuthResponse(val token: String, val user: UserDto)
@Serializable private data class UserDto(val id: String, val username: String, @SerialName("display_name") val displayName: String)
@Serializable private data class InviteResponse(val code: String)
@Serializable private data class ScoreCardDto(@SerialName("user_id") val userId: String, val name: String, val score: Int)
@Serializable private data class EventDto(val id: String, @SerialName("actor_name") val actorName: String, @SerialName("target_name") val targetName: String, val delta: Int, @SerialName("score_after") val scoreAfter: Int, val note: String? = null, @SerialName("created_at") val createdAt: String)
@Serializable private data class SettingsDto(@SerialName("initial_score") val initial: Int, @SerialName("min_score") val min: Int? = null, @SerialName("max_score") val max: Int? = null)
@Serializable private data class CoupleDto(@SerialName("couple_id") val coupleId: String, @SerialName("current_user_id") val currentUserId: String, @SerialName("current_user_name") val currentUserName: String, @SerialName("partner_name") val partnerName: String, val cards: List<ScoreCardDto>, val events: List<EventDto>, val settings: SettingsDto)
@Serializable private data class ErrorDto(val error: String)

class FavorRepository(context: Context) {
    private val preferences = context.getSharedPreferences("favorapp_session", Context.MODE_PRIVATE)
    private val client = HttpClient(Android) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun signIn(username: String, password: String) { saveAuth(client.post("$baseUrl/api/v1/auth/login") { json(AuthRequest(username, password)) }.bodyChecked<AuthResponse>()) }
    suspend fun signUp(username: String, password: String, displayName: String) { saveAuth(client.post("$baseUrl/api/v1/auth/register") { json(AuthRequest(username, password, displayName)) }.bodyChecked<AuthResponse>()) }
    suspend fun signOut() { client.post("$baseUrl/api/v1/auth/logout") { auth() }.check(); preferences.edit().clear().apply() }
    fun currentUserId(): String? = preferences.getString("user_id", null)
    fun clearSession() { preferences.edit().clear().apply() }
    suspend fun createInvite(): String = client.post("$baseUrl/api/v1/invites") { auth() }.bodyChecked<InviteResponse>().code
    suspend fun acceptInvite(code: String) { client.post("$baseUrl/api/v1/invites/accept") { auth(); json(InviteRequest(code)) }.check() }
    suspend fun addScore(delta: Int, note: String?) { client.post("$baseUrl/api/v1/scores/events") { auth(); json(ScoreRequest(delta, note, UUID.randomUUID().toString())) }.bodyChecked<EventDto>() }
    suspend fun updateScoreSettings(initial: Int, min: Int?, max: Int?) { client.put("$baseUrl/api/v1/score-settings") { auth(); json(SettingsRequest(initial, min, max)) }.check() }
    suspend fun loadSnapshot(): CoupleSnapshot? { val response = client.get("$baseUrl/api/v1/couple") { auth() }; response.check(); val raw = response.bodyAsText().trim(); if (raw == "null") return null; return Json.decodeFromString<CoupleDto>(raw).toSnapshot() }

    private fun saveAuth(auth: AuthResponse) { preferences.edit().putString("token", auth.token).putString("user_id", auth.user.id).apply() }
    private fun HttpRequestBuilder.auth() { header("Authorization", "Bearer ${preferences.getString("token", "")}") }
    private fun HttpRequestBuilder.json(value: Any) { contentType(ContentType.Application.Json); setBody(value) }
    private suspend inline fun <reified T> HttpResponse.bodyChecked(): T { check(); return body() }
    private suspend fun HttpResponse.check() { if (status.value !in 200..299) { val error = runCatching { body<ErrorDto>() }.getOrNull()?.error; throw ApiException(status.value, error ?: "request_failed") } }
    private fun CoupleDto.toSnapshot() = CoupleSnapshot(coupleId, currentUserId, currentUserName, partnerName, cards.map { ScoreCard(it.userId, it.name, it.score) }, events.map { ScoreEventItem(it.id, it.actorName, it.targetName, it.delta, it.scoreAfter, it.note, it.createdAt) }, ScoreSettingRow(coupleId, settings.initial, settings.min, settings.max))
}

class ApiException(val statusCode: Int, val code: String) : Exception(code)
