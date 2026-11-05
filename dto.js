'use strict';
// Shapes database documents into the JSON the Android app expects.
const { addDays, computeStreak, isScheduled } = require('./services/streak');

function habitDto(h, dates, today) {
  const windowStart = addDays(today, -13);
  return {
    id: h.id,
    title: h.title,
    frequency: h.frequency,
    customDays: h.customDays || [],
    reminderTime: h.reminderTime || null,
    priority: h.priority,
    streakCount: computeStreak(h, dates, today),
    completedToday: dates.includes(today),
    scheduledToday: isScheduled(h, today),
    recentCompletions: dates.filter((d) => d >= windowStart && d <= today).sort(),
    completionDates: [...dates].sort(), // up to ~400 days; the app stores these for offline streaks and the streak board
    createdAt: h.createdAt,
    updatedAt: h.updatedAt,
  };
}

function noteDto(n, conflict = false) {
  const dto = {
    id: n.id,
    subject: n.subject,
    title: n.title,
    content: n.content,
    tags: n.tags || [],
    updatedAt: n.updatedAt,
  };
  if (conflict) dto.conflict = true; // the server copy was newer, so it won (last-write-wins)
  return dto;
}

// Question answers (correctIndex) are only revealed by the submit endpoint.
function quizDto(q, withQuestions) {
  const dto = {
    id: q.id,
    quizId: q.id, // Part 1 design names this field quizId; id is kept for consistency with other resources
    noteId: String(q.noteId),
    noteTitle: q.noteTitle,
    source: q.source,
    questionCount: q.questions.length,
    lastScore: q.lastScore === undefined ? null : q.lastScore,
    attemptCount: q.attempts.length,
    generatedAt: q.generatedAt,
  };
  if (withQuestions) dto.questions = q.questions.map((x) => ({ text: x.text, options: x.options }));
  return dto;
}

module.exports = { habitDto, noteDto, quizDto };
