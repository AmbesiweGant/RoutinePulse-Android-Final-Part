package com.routinepulse.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routinepulse.app.R
import com.routinepulse.app.data.model.NoteDto
import com.routinepulse.app.domain.NoteLogic
import com.routinepulse.app.ui.components.*
import com.routinepulse.app.ui.viewmodel.NotesViewModel

/** Mockup 4: searchable list of study notes (REQ-05). */
@Composable
fun NotesScreen(vm: NotesViewModel, onOpenNote: (String) -> Unit, onNewNote: () -> Unit) {
    val s by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    val visible = NoteLogic.filter(s.notes, s.query)
    val loadError = s.error

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(stringResource(R.string.notes_title))
            RpTextField(
                value = s.query,
                onValueChange = vm::onQuery,
                label = stringResource(R.string.notes_search),
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)
            )
            when {
                s.loading -> LoadingBox()
                loadError != null && s.notes.isEmpty() -> ErrorState(errorMessage(loadError), onRetry = vm::load)
                s.notes.isEmpty() -> EmptyState(
                    title = stringResource(R.string.notes_empty_title),
                    subtitle = stringResource(R.string.notes_empty_subtitle)
                )
                visible.isEmpty() -> EmptyState(
                    title = stringResource(R.string.notes_no_results_title),
                    subtitle = stringResource(R.string.notes_no_results_subtitle)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { note -> NoteCard(note) { onOpenNote(note.id) } }
                }
            }
        }
        FloatingActionButton(
            onClick = onNewNote,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.note_new))
        }
    }
}

@Composable
private fun NoteCard(note: NoteDto, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(note.subject, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                note.content.replace("\n", " "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val tags = note.tags ?: emptyList()
            if (tags.isNotEmpty()) {
                Text(
                    stringResource(R.string.note_tags_preview, tags.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
