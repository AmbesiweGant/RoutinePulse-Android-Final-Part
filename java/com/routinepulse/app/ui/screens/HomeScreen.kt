package com.routinepulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routinepulse.app.R
import com.routinepulse.app.data.model.HabitDto
import com.routinepulse.app.data.model.Priority
import com.routinepulse.app.domain.DateUtils
import com.routinepulse.app.domain.Greeting
import com.routinepulse.app.domain.HabitLogic
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.theme.PriorityHigh
import com.routinepulse.app.ui.theme.PriorityLow
import com.routinepulse.app.ui.theme.PriorityMedium
import com.routinepulse.app.ui.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.LocalTime

/** Mockup 2: dashboard with today's streak, habit checklist and the next reminder (REQ-04). */
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    userName: String,
    onAddHabit: () -> Unit,
    onEditHabit: (String) -> Unit,
    onOpenProgress: () -> Unit
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var habitToDelete by remember { mutableStateOf<HabitDto?>(null) }

    // Reload each time the screen is shown (e.g. after adding a habit and coming back).
    LaunchedEffect(Unit) { vm.load() }

    val actionError = state.actionError
    val actionErrorText = if (actionError != null) errorMessage(actionError) else null
    LaunchedEffect(actionError) {
        if (actionErrorText != null) {
            snackbar.showSnackbar(actionErrorText)
            vm.clearActionError()
        }
    }

    // Celebrate newly unlocked badges once (gamification, feature 4).
    val badgeText = if (state.newBadges > 0) stringResource(R.string.home_new_badges, state.newBadges) else null
    LaunchedEffect(state.newBadges) {
        if (badgeText != null) {
            snackbar.showSnackbar(badgeText)
            vm.consumeNewBadges()
        }
    }

    val name = userName.ifBlank { stringResource(R.string.default_user_name) }
    val greeting = when (DateUtils.greetingFor(remember { LocalTime.now().hour })) {
        Greeting.MORNING -> stringResource(R.string.greeting_morning, name)
        Greeting.AFTERNOON -> stringResource(R.string.greeting_afternoon, name)
        Greeting.EVENING -> stringResource(R.string.greeting_evening, name)
    }
    val today = remember { LocalDate.now() }
    val nowMinutes = remember { LocalTime.now().let { it.hour * 60 + it.minute } }
    val loadError = state.error

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(greeting)
            // Offline mode: changes are saved on the phone first and sent to the server when there is internet.
            if (state.pending > 0) {
                Text(
                    stringResource(R.string.home_pending_sync, state.pending),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            when {
                state.loading -> LoadingBox()
                loadError != null && state.habits.isEmpty() -> ErrorState(errorMessage(loadError), onRetry = vm::load)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { StreakCard(state.habits) }
                    item { ProgressCard(onOpenProgress) }
                    if (state.habits.isEmpty()) {
                        item {
                            EmptyState(
                                title = stringResource(R.string.home_empty_title),
                                subtitle = stringResource(R.string.home_empty_subtitle)
                            )
                        }
                    }
                    items(state.habits, key = { it.id }) { habit ->
                        HabitRow(
                            habit = habit,
                            busy = habit.id in state.busyIds,
                            today = today,
                            onToggle = { vm.toggle(habit) },
                            onEdit = { onEditHabit(habit.id) },
                            onDelete = { habitToDelete = habit }
                        )
                    }
                    val next = HabitLogic.nextReminder(state.habits, nowMinutes)
                    if (next != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.home_next_reminder, next.title, next.reminderTime.orEmpty()),
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddHabit,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.habit_add))
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    habitToDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = { Text(stringResource(R.string.habit_delete_title)) },
            text = { Text(stringResource(R.string.habit_delete_message, habit.title)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(habit); habitToDelete = null }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun ProgressCard(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_progress_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.home_progress_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreakCard(habits: List<HabitDto>) {
    val done = HabitLogic.completedCount(habits)
    val due = HabitLogic.dueToday(habits).size
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_streak_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.home_streak_days, HabitLogic.bestStreak(habits)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.home_done_today, done, due),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { HabitLogic.progress(habits) },
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    strokeWidth = 6.dp
                )
                Text("$done/$due", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: HabitDto,
    busy: Boolean,
    today: LocalDate,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (Priority.fromWire(habit.priority)) {
        Priority.LOW -> PriorityLow
        Priority.MEDIUM -> PriorityMedium
        Priority.HIGH -> PriorityHigh
    }
    val streakText = stringResource(R.string.habit_streak, habit.streakCount)
    val reminder = habit.reminderTime
    val meta = if (reminder != null) "$streakText  \u2022  $reminder" else streakText

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(priorityColor))
            Checkbox(
                checked = habit.completedToday,
                onCheckedChange = { onToggle() },
                enabled = !busy && (habit.scheduledToday || habit.completedToday)
            )
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(habit.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (habit.scheduledToday) meta else stringResource(R.string.habit_rest_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val recent = (habit.recentCompletions ?: emptyList()).toSet()
                    DateUtils.lastNDays(today, 14).forEach { day ->
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (day.toString() in recent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
}
