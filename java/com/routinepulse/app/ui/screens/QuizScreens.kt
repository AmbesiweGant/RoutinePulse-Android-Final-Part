package com.routinepulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routinepulse.app.R
import com.routinepulse.app.data.model.QuizDto
import com.routinepulse.app.domain.NoteLogic
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.theme.PriorityHigh
import com.routinepulse.app.ui.viewmodel.QuizHubViewModel
import com.routinepulse.app.ui.viewmodel.QuizPlayViewModel

/** Quiz tab: generate a quiz from any note, or re-attempt an earlier one (REQ-06). */
@Composable
fun QuizHubScreen(vm: QuizHubViewModel, onOpenQuiz: (String) -> Unit) {
    val s by vm.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val loadError = s.error

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(s.readyQuizId) {
        val id = s.readyQuizId
        if (id != null) {
            vm.consumeReady()
            onOpenQuiz(id)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(stringResource(R.string.quiz_title))
        when {
            s.loading -> LoadingBox()
            loadError != null && s.quizzes.isEmpty() && s.notes.isEmpty() ->
                ErrorState(errorMessage(loadError), onRetry = vm::load)
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.quiz_from_note_title), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.quiz_from_note_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PrimaryButton(
                                text = stringResource(if (s.generating) R.string.quiz_generating else R.string.quiz_choose_note),
                                onClick = { showPicker = true },
                                loading = s.generating,
                                enabled = s.notes.isNotEmpty()
                            )
                            if (s.notes.isEmpty()) {
                                Text(
                                    stringResource(R.string.quiz_need_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (loadError != null) item { ErrorBanner(errorMessage(loadError)) }
                item { SectionLabel(stringResource(R.string.quiz_your_quizzes)) }
                if (s.quizzes.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.quiz_empty_title),
                            subtitle = stringResource(R.string.quiz_empty_subtitle)
                        )
                    }
                }
                items(s.quizzes, key = { it.id }) { quiz -> QuizCard(quiz) { onOpenQuiz(quiz.id) } }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.quiz_choose_note)) },
            text = {
                LazyColumn {
                    items(s.notes, key = { it.id }) { note ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showPicker = false; vm.generate(note.id) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(note.title, fontWeight = FontWeight.SemiBold)
                            Text(note.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun QuizCard(quiz: QuizDto, onClick: () -> Unit) {
    val last = quiz.lastScore
    val status = if (last == null) stringResource(R.string.quiz_not_attempted)
    else stringResource(R.string.quiz_last_score, last, quiz.questionCount)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(quiz.noteTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.quiz_card_meta, quiz.questionCount, quiz.attemptCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Mockup 5: answer the multiple-choice questions, submit, and see instant scoring. */
@Composable
fun QuizPlayScreen(vm: QuizPlayViewModel, quizId: String, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    LaunchedEffect(quizId) { vm.load(quizId) }
    val quiz = s.quiz
    val loadError = s.error

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(stringResource(R.string.quiz_play_title), onBack = onBack)
        when {
            s.loading -> LoadingBox()
            quiz == null -> ErrorState(
                message = if (loadError != null) errorMessage(loadError) else stringResource(R.string.error_unknown),
                onRetry = { vm.load(quizId) }
            )
            else -> LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column {
                        Text(quiz.noteTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val sourceLabel = if (quiz.source == "ai") R.string.quiz_source_ai else R.string.quiz_source_rules
                        Text(
                            stringResource(sourceLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val result = s.result
                if (result != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.quiz_score, result.score, result.total, NoteLogic.percent(result.score, result.total)),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                result.message?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                            }
                        }
                    }
                }

                itemsIndexed(quiz.questions ?: emptyList()) { qIndex, question ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${qIndex + 1}. ${question.text}", fontWeight = FontWeight.SemiBold)
                            val chosen = s.selected[qIndex]
                            question.options.forEachIndexed { oIndex, option ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = chosen == oIndex,
                                            role = Role.RadioButton,
                                            onClick = { vm.select(qIndex, oIndex) }
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = chosen == oIndex, onClick = null)
                                    Text(option, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp))
                                }
                            }
                            val fb = s.result?.feedback?.getOrNull(qIndex)
                            if (fb != null) {
                                val text = if (fb.correct) stringResource(R.string.quiz_correct)
                                else stringResource(R.string.quiz_incorrect, fb.correctAnswer.orEmpty())
                                Text(
                                    text,
                                    color = if (fb.correct) MaterialTheme.colorScheme.primary else PriorityHigh,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (loadError != null) ErrorBanner(errorMessage(loadError))
                        if (s.result == null) {
                            PrimaryButton(
                                text = stringResource(R.string.quiz_submit),
                                onClick = vm::submit,
                                enabled = s.canSubmit,
                                loading = s.submitting
                            )
                        } else {
                            PrimaryButton(text = stringResource(R.string.quiz_retake), onClick = vm::retake)
                        }
                    }
                }
            }
        }
    }
}
