package com.routinepulse.app.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.routinepulse.app.R
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.viewmodel.NoteEditViewModel

/** Note editor with markdown preview and the "Generate quiz" action (REQ-05, REQ-06). */
@Composable
fun NoteEditScreen(
    vm: NoteEditViewModel,
    noteId: String?,
    onBack: () -> Unit,
    onQuizReady: (String) -> Unit
) {
    val s by vm.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) { vm.load(noteId) }
    LaunchedEffect(s.saved, s.deleted) { if (s.saved || s.deleted) onBack() }
    LaunchedEffect(s.quizReadyId) {
        val id = s.quizReadyId
        if (id != null) {
            vm.consumeQuizReady()
            onQuizReady(id)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = stringResource(if (s.isEdit) R.string.note_edit_title else R.string.note_new_title),
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RpTextField(s.subject, vm::onSubject, stringResource(R.string.note_field_subject), error = fieldErrorText(s.subjectError))
                RpTextField(s.title, vm::onTitle, stringResource(R.string.note_field_title), error = fieldErrorText(s.titleError))
                RpTextField(s.tagsText, vm::onTags, stringResource(R.string.note_field_tags))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel(stringResource(R.string.note_field_content))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = vm::togglePreview) {
                            Text(stringResource(if (s.preview) R.string.note_edit_mode else R.string.note_preview))
                        }
                    }
                }
                if (s.preview) {
                    MarkdownText(s.content)
                } else {
                    RpTextField(
                        value = s.content,
                        onValueChange = vm::onContent,
                        label = stringResource(R.string.note_content_hint),
                        singleLine = false,
                        minLines = 8,
                        error = fieldErrorText(s.contentError)
                    )
                }

                s.error?.let { ErrorBanner(errorMessage(it)) }

                PrimaryButton(
                    text = stringResource(R.string.note_save),
                    onClick = vm::save,
                    loading = s.saving,
                    enabled = !s.generating
                )
                // Single-tap quiz generation (REQ-06): saves the note, then asks the API to build the quiz.
                PrimaryButton(
                    text = stringResource(if (s.generating) R.string.quiz_generating else R.string.quiz_generate),
                    onClick = vm::saveAndGenerateQuiz,
                    loading = s.generating,
                    enabled = !s.saving
                )
                if (s.isEdit) {
                    SecondaryButton(text = stringResource(R.string.note_delete), onClick = { confirmDelete = true })
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
