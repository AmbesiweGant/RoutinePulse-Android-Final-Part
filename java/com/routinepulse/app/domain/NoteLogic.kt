package com.routinepulse.app.domain

import com.routinepulse.app.data.model.NoteDto

object NoteLogic {
    /** "Exam-Prep, biology ,, biology" -> ["exam-prep", "biology"] (lower-case, unique, max 10). */
    fun parseTags(text: String): List<String> =
        text.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it.length <= 30 }
            .distinct()
            .take(10)

    /** Case-insensitive search over subject, title, content and tags. A blank query returns everything. */
    fun filter(notes: List<NoteDto>, query: String): List<NoteDto> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return notes
        return notes.filter { n ->
            n.subject.lowercase().contains(q) ||
                n.title.lowercase().contains(q) ||
                n.content.lowercase().contains(q) ||
                (n.tags ?: emptyList()).any { it.lowercase().contains(q) }
        }
    }

    fun percent(score: Int, total: Int): Int = if (total <= 0) 0 else (score * 100) / total
}
