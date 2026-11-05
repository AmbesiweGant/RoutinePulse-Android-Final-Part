package com.routinepulse.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routinepulse.app.R
import com.routinepulse.app.data.model.Frequency
import com.routinepulse.app.data.model.Priority
import com.routinepulse.app.domain.DateUtils
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.viewmodel.HabitEditViewModel

/** Mockup 3: add / edit habit (REQ-04). [habitId] is null when creating a new habit. */
@Composable
fun HabitEditScreen(vm: HabitEditViewModel, habitId: String?, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(habitId) { vm.load(habitId) }
    LaunchedEffect(s.saved) { if (s.saved) onBack() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = stringResource(if (s.isEdit) R.string.habit_edit_title else R.string.habit_add_title),
            onBack = onBack
        )
        if (s.loading) {
            LoadingBox()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RpTextField(
                    value = s.title,
                    onValueChange = vm::onTitle,
                    label = stringResource(R.string.habit_field_title),
                    error = fieldErrorText(s.titleError)
                )

                SectionLabel(stringResource(R.string.habit_field_frequency))
                ChoiceRow(
                    options = listOf(
                        Frequency.DAILY to stringResource(R.string.frequency_daily),
                        Frequency.WEEKDAYS to stringResource(R.string.frequency_weekdays),
                        Frequency.CUSTOM to stringResource(R.string.frequency_custom)
                    ),
                    selected = s.frequency,
                    onSelect = vm::onFrequency
                )
                if (s.frequency == Frequency.CUSTOM) {
                    val dayLabels = listOf(
                        1 to R.string.day_mon, 2 to R.string.day_tue, 3 to R.string.day_wed, 4 to R.string.day_thu,
                        5 to R.string.day_fri, 6 to R.string.day_sat, 7 to R.string.day_sun
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        dayLabels.forEach { (day, labelRes) ->
                            DayToggle(
                                label = stringResource(labelRes),
                                selected = day in s.customDays,
                                onClick = { vm.toggleDay(day) }
                            )
                        }
                    }
                    fieldErrorText(s.daysError)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                SectionLabel(stringResource(R.string.habit_field_reminder))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SecondaryButton(
                        text = s.reminderTime ?: stringResource(R.string.habit_no_reminder),
                        onClick = {
                            val minutes = DateUtils.parseTimeToMinutes(s.reminderTime) ?: (7 * 60 + 30)
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> vm.onReminder(DateUtils.formatTime(hour, minute)) },
                                minutes / 60, minutes % 60, true
                            ).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (s.reminderTime != null) {
                        TextButton(onClick = { vm.onReminder(null) }) { Text(stringResource(R.string.habit_clear_reminder)) }
                    }
                }

                SectionLabel(stringResource(R.string.habit_field_priority))
                ChoiceRow(
                    options = listOf(
                        Priority.LOW to stringResource(R.string.priority_low),
                        Priority.MEDIUM to stringResource(R.string.priority_medium),
                        Priority.HIGH to stringResource(R.string.priority_high)
                    ),
                    selected = s.priority,
                    onSelect = vm::onPriority
                )

                s.error?.let { ErrorBanner(errorMessage(it)) }

                PrimaryButton(
                    text = stringResource(R.string.habit_save),
                    onClick = vm::save,
                    loading = s.saving
                )
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}
