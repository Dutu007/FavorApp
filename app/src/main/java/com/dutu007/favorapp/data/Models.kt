package com.dutu007.favorapp.data

data class ScoreCard(val userId: String, val name: String, val score: Int)
data class ScoreEventItem(val id: String, val actorName: String, val targetName: String, val delta: Int, val scoreAfter: Int, val note: String?, val createdAt: String)
data class ScoreSettingRow(val coupleId: String, val initialScore: Int, val minScore: Int? = null, val maxScore: Int? = null)
data class CoupleSnapshot(val coupleId: String, val currentUserId: String, val currentUserName: String, val partnerName: String, val cards: List<ScoreCard>, val events: List<ScoreEventItem>, val settings: ScoreSettingRow)
