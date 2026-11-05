package com.routinepulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routinepulse.app.R
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.Priority
import com.routinepulse.app.domain.Achievements
import com.routinepulse.app.domain.Badge
import com.routinepulse.app.domain.BoardLogic
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.theme.PriorityHigh
import com.routinepulse.app.ui.theme.PriorityLow
import com.routinepulse.app.ui.theme.PriorityMedium
import com.routinepulse.app.ui.viewmodel.ProgressTab
import com.routinepulse.app.ui.viewmodel.ProgressViewModel
import java.time.LocalDate

private fun badgeName(b: Badge): Int = when (b) {
    Badge.FIRST_STEP -> R.string.badge_first_step
    Badge.STREAK_3 -> R.string.badge_streak_3
    Badge.STREAK_7 -> R.string.badge_streak_7
    Badge.STREAK_14 -> R.string.badge_streak_14
    Badge.STREAK_30 -> R.string.badge_streak_30
    Badge.HABIT_COLLECTOR -> R.string.badge_habit_collector
    Badge.PERFECT_DAY -> R.string.badge_perfect_day
    Badge.NOTE_TAKER -> R.string.badge_note_taker
    Badge.QUIZ_STARTER -> R.string.badge_quiz_starter
    Badge.QUIZ_ACE -> R.string.badge_quiz_ace
}

private fun badgeDescription(b: Badge): Int = when (b) {
    Badge.FIRST_STEP -> R.string.badge_first_step_desc
    Badge.STREAK_3 -> R.string.badge_streak_3_desc
    Badge.STREAK_7 -> R.string.badge_streak_7_desc
    Badge.STREAK_14 -> R.string.badge_streak_14_desc
    Badge.STREAK_30 -> R.string.badge_streak_30_desc
    Badge.HABIT_COLLECTOR -> R.string.badge_habit_collector_desc
    Badge.PERFECT_DAY -> R.string.badge_perfect_day_desc
    Badge.NOTE_TAKER -> R.string.badge_note_taker_desc
    Badge.QUIZ_STARTER -> R.string.badge_quiz_starter_desc
    Badge.QUIZ_ACE -> R.string.badge_quiz_ace_desc
}

/** Progress: level + GitHub-style streak board (feature 5) and achievement badges (feature 4). */
@Composable
fun ProgressScreen(vm: ProgressViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    val today = remember { LocalDate.now() }
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(stringResource(R.string.progress_title), onBack = onBack)
        if (s.loading) {
            LoadingBox()
        } else {
            LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { SummaryCard(s.stats.totalCheckIns, s.stats.longestStreak) }

                if (s.newlyEarned.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.progress_new_badges), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                s.newlyEarned.forEach { Text(stringResource(badgeName(it))) }
                                TextButton(onClick = vm::dismissNewBadges) { Text(stringResource(R.string.action_ok)) }
                            }
                        }
                    }
                }

                item {
                    ChoiceRow(
                        options = listOf(
                            ProgressTab.BOARD to stringResource(R.string.progress_tab_board),
                            ProgressTab.BADGES to stringResource(R.string.progress_tab_badges)
                        ),
                        selected = s.tab,
                        onSelect = vm::selectTab
                    )
                }

                if (s.tab == ProgressTab.BOARD) {
                    item { StreakBoardCard(s.habits, today) }
                    if (s.habits.isNotEmpty()) {
                        item { SectionLabel(stringResource(R.string.progress_per_habit)) }
                        items(s.habits, key = { it.id }) { habit -> HabitHistoryRow(habit, today) }
                    }
                } else {
                    items(Badge.entries.toList(), key = { it.name }) { badge ->
                        BadgeRow(badge, earned = badge in s.earned)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(totalCheckIns: Int, longestStreak: Int) {
    val level = Achievements.level(totalCheckIns)
    val toNext = Achievements.CHECK_INS_PER_LEVEL - (totalCheckIns % Achievements.CHECK_INS_PER_LEVEL)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.progress_level, level), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(
                progress = { Achievements.levelProgress(totalCheckIns) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            Text(stringResource(R.string.progress_to_next, toNext, level + 1), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.progress_check_ins, totalCheckIns))
            Text(stringResource(R.string.progress_longest_streak, longestStreak))
        }
    }
}

/** GitHub-style grid: one column per week (Mon..Sun), darker green = more of that day's habits done. */
@Composable
private fun StreakBoardCard(habits: List<HabitDto>, today: LocalDate) {
    val weeks = remember(today) { BoardLogic.weeks(today, 15) }
    val dayLabels = listOf(
        R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
        R.string.day_fri, R.string.day_sat, R.string.day_sun
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.progress_board_title), fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    dayLabels.forEach { res ->
                        Box(Modifier.size(width = 14.dp, height = 14.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(res), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.width(2.dp))
                weeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            val level = if (day == null) null else BoardLogic.level(BoardLogic.ratio(habits, day))
                            BoardCell(level)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.progress_less), style = MaterialTheme.typography.bodySmall)
                listOf(0, 1, 2, 3).forEach { BoardCell(it) }
                Text(stringResource(R.string.progress_more), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.progress_rate_30, BoardLogic.completionRate(habits, today, 30)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BoardCell(level: Int?) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val color = when (level) {
        null -> Color.Transparent                                      // a day that has not happened yet
        -1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) // rest day: nothing scheduled
        0 -> MaterialTheme.colorScheme.outlineVariant                  // scheduled, nothing done
        1 -> primaryColor.copy(alpha = 0.35f)
        2 -> primaryColor.copy(alpha = 0.65f)
        else -> primaryColor
    }
    Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

/** Last 28 days of one habit, coloured by its priority (colour-coded categories from the Part 1 design). */
@Composable
private fun HabitHistoryRow(habit: HabitDto, today: LocalDate) {
    val color = when (Priority.fromWire(habit.priority)) {
        Priority.LOW -> PriorityLow
        Priority.MEDIUM -> PriorityMedium
        Priority.HIGH -> PriorityHigh
    }
    val done = (habit.completionDates ?: emptyList()).toSet()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(habit.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (27 downTo 0).forEach { offset ->
                val day = today.minusDays(offset.toLong())
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (day.toString() in done) color else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, earned: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (earned) 1f else 0.55f)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(if (earned) "\u2605" else "\u2606", fontSize = 22.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(badgeName(badge)), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(badgeDescription(badge)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(if (earned) R.string.badge_earned else R.string.badge_locked),
                style = MaterialTheme.typography.labelMedium,
                color = if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
