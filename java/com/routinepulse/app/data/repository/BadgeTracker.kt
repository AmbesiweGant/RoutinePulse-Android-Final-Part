package com.routinepulse.app.data.repository

import com.routinepulse.app.data.local.SettingsStore
import com.routinepulse.app.domain.Achievements
import com.routinepulse.app.domain.Badge
import com.routinepulse.app.domain.ProgressStats
import kotlinx.coroutines.flow.first

data class BadgeResult(val earned: Set<Badge>, val newlyEarned: List<Badge>)

/** Remembers which badges have been earned so they are never lost and each one is celebrated only once. */
class BadgeTracker(private val settings: SettingsStore) {
    suspend fun evaluate(stats: ProgressStats): BadgeResult {
        val nowEarned = Achievements.earned(stats)
        val stored = settings.earnedBadgesFlow.first()
        val fresh = nowEarned.filter { it.name !in stored }
        val all = stored + nowEarned.map { it.name }
        if (fresh.isNotEmpty()) settings.setEarnedBadges(all)
        return BadgeResult(all.mapNotNull { Badge.fromName(it) }.toSet(), fresh)
    }
}
