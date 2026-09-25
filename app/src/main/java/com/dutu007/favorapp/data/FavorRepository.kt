package com.dutu007.favorapp.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FavorRepository(
    private val client: io.github.jan.supabase.SupabaseClient = SupabaseClientProvider.client,
) {
    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String) {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("display_name", displayName.trim()) }
        }
    }

    suspend fun signOut() = client.auth.signOut()

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun currentUserEmail(): String = client.auth.currentUserOrNull()?.email.orEmpty()

    suspend fun createInvite(): String = client.postgrest.rpc("create_invite").decodeAs()

    suspend fun acceptInvite(code: String) {
        client.postgrest.rpc(
            "accept_invite",
            buildJsonObject { put("input_code", code.trim()) },
        )
    }

    suspend fun addScore(coupleId: String, targetUserId: String, delta: Int, note: String?) {
        client.postgrest.rpc(
            "add_score_event",
            buildJsonObject {
                put("target_couple_id", coupleId)
                put("target_user_id", targetUserId)
                put("score_delta", delta)
                if (!note.isNullOrBlank()) put("event_note", note.trim())
            },
        )
    }

    suspend fun updateScoreSettings(coupleId: String, initial: Int, min: Int?, max: Int?) {
        client.postgrest.rpc(
            "update_score_settings",
            buildJsonObject {
                put("target_couple_id", coupleId)
                put("new_initial_score", initial)
                put("new_min_score", min)
                put("new_max_score", max)
            },
        )
    }

    suspend fun loadSnapshot(): CoupleSnapshot? {
        val currentUserId = currentUserId() ?: return null
        val couple = client.from("couples").select().decodeList<CoupleRow>()
            .firstOrNull { it.status == "active" }
            ?: return null

        val profiles = client.from("profiles").select().decodeList<ProfileRow>().associateBy { it.id }
        val scores = client.from("couple_scores").select().decodeList<CoupleScoreRow>()
        val settings = client.from("score_settings").select().decodeList<ScoreSettingRow>()
            .first { it.coupleId == couple.id }
        val events = client.from("score_events").select().decodeList<ScoreEventRow>()
            .filter { it.coupleId == couple.id }
            .sortedByDescending { it.createdAt }

        val partnerId = if (couple.memberA == currentUserId) couple.memberB else couple.memberA
        fun profileName(id: String): String = profiles[id]?.displayName?.takeIf { it.isNotBlank() }
            ?: if (id == currentUserId) "我" else "恋人"

        return CoupleSnapshot(
            coupleId = couple.id,
            currentUserId = currentUserId,
            currentUserName = profileName(currentUserId),
            partnerName = profileName(partnerId),
            cards = scores.map { ScoreCard(it.targetUserId, profileName(it.targetUserId), it.currentScore) },
            events = events.map {
                ScoreEventItem(
                    id = it.id,
                    actorName = profileName(it.actorId),
                    targetName = profileName(it.targetUserId),
                    delta = it.delta,
                    scoreAfter = it.scoreAfter,
                    note = it.note,
                    createdAt = it.createdAt,
                )
            },
            settings = settings,
        )
    }
}
