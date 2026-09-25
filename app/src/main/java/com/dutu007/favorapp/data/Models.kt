package com.dutu007.favorapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
)

@Serializable
data class CoupleRow(
    val id: String,
    @SerialName("member_a") val memberA: String,
    @SerialName("member_b") val memberB: String,
    val status: String,
)

@Serializable
data class ScoreSettingRow(
    @SerialName("couple_id") val coupleId: String,
    @SerialName("initial_score") val initialScore: Int,
    @SerialName("min_score") val minScore: Int? = null,
    @SerialName("max_score") val maxScore: Int? = null,
)

@Serializable
data class CoupleScoreRow(
    @SerialName("couple_id") val coupleId: String,
    @SerialName("target_user_id") val targetUserId: String,
    @SerialName("current_score") val currentScore: Int,
)

@Serializable
data class ScoreEventRow(
    val id: String,
    @SerialName("couple_id") val coupleId: String,
    @SerialName("actor_id") val actorId: String,
    @SerialName("target_user_id") val targetUserId: String,
    val delta: Int,
    @SerialName("score_after") val scoreAfter: Int,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

data class ScoreCard(
    val userId: String,
    val name: String,
    val score: Int,
)

data class ScoreEventItem(
    val id: String,
    val actorName: String,
    val targetName: String,
    val delta: Int,
    val scoreAfter: Int,
    val note: String?,
    val createdAt: String,
)

data class CoupleSnapshot(
    val coupleId: String,
    val currentUserId: String,
    val currentUserName: String,
    val partnerName: String,
    val cards: List<ScoreCard>,
    val events: List<ScoreEventItem>,
    val settings: ScoreSettingRow,
)
