'use strict';
// Pure input validation helpers. Each returns an error message string or null.
// Rules deliberately mirror the Android-side Validators.kt so both ends agree.

const EMAIL_REGEX = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const FREQUENCIES = ['daily', 'weekdays', 'custom'];
const PRIORITIES = ['low', 'medium', 'high'];

function validateEmail(email) {
  if (typeof email !== 'string' || !email.trim()) return 'Email is required';
  if (!EMAIL_REGEX.test(email.trim())) return 'Enter a valid email address';
  return null;
}

function validateNewPassword(pw) {
  if (typeof pw !== 'string' || !pw) return 'Password is required';
  if (pw.length < 8) return 'Password must be at least 8 characters';
  if (pw.length > 72) return 'Password must be at most 72 characters'; // bcrypt limit
  if (!/[A-Za-z]/.test(pw) || !/\d/.test(pw)) return 'Password must contain a letter and a number';
  return null;
}

function validateDisplayName(name) {
  if (typeof name !== 'string' || !name.trim()) return 'Display name is required';
  if (name.trim().length < 2) return 'Display name must be at least 2 characters';
  if (name.trim().length > 50) return 'Display name must be 50 characters or fewer';
  return null;
}

function normaliseTags(tags) {
  if (!Array.isArray(tags)) return [];
  const clean = tags
    .filter((t) => typeof t === 'string')
    .map((t) => t.trim().toLowerCase())
    .filter((t) => t.length > 0 && t.length <= 30);
  return [...new Set(clean)].slice(0, 10);
}

function validateHabitInput(body) {
  const b = body || {};
  const title = typeof b.title === 'string' ? b.title.trim() : '';
  if (!title) return { error: 'Habit title is required' };
  if (title.length > 80) return { error: 'Habit title must be 80 characters or fewer' };

  const frequency = b.frequency || 'daily';
  if (!FREQUENCIES.includes(frequency)) return { error: 'Frequency must be daily, weekdays or custom' };

  let customDays = [];
  if (frequency === 'custom') {
    if (!Array.isArray(b.customDays) || b.customDays.length === 0) {
      return { error: 'Choose at least one day for a custom habit' };
    }
    customDays = [...new Set(b.customDays.map(Number))];
    if (customDays.some((d) => !Number.isInteger(d) || d < 1 || d > 7)) {
      return { error: 'Custom days must be numbers 1 (Mon) to 7 (Sun)' };
    }
    customDays.sort((a, c) => a - c);
  }

  let reminderTime = null;
  if (b.reminderTime !== undefined && b.reminderTime !== null && b.reminderTime !== '') {
    if (typeof b.reminderTime !== 'string' || !/^([01]\d|2[0-3]):[0-5]\d$/.test(b.reminderTime)) {
      return { error: 'Reminder time must be in HH:mm format' };
    }
    reminderTime = b.reminderTime;
  }

  const priority = b.priority || 'medium';
  if (!PRIORITIES.includes(priority)) return { error: 'Priority must be low, medium or high' };

  return { value: { title, frequency, customDays, reminderTime, priority } };
}

function validateNoteInput(body) {
  const b = body || {};
  const subject = typeof b.subject === 'string' ? b.subject.trim() : '';
  const title = typeof b.title === 'string' ? b.title.trim() : '';
  const content = typeof b.content === 'string' ? b.content : '';
  if (!subject) return { error: 'Subject is required' };
  if (subject.length > 60) return { error: 'Subject must be 60 characters or fewer' };
  if (!title) return { error: 'Title is required' };
  if (title.length > 120) return { error: 'Title must be 120 characters or fewer' };
  if (!content.trim()) return { error: 'Note content is required' };
  if (content.length > 20000) return { error: 'Note content must be 20000 characters or fewer' };
  return { value: { subject, title, content, tags: normaliseTags(b.tags) } };
}

module.exports = {
  validateEmail,
  validateNewPassword,
  validateDisplayName,
  validateHabitInput,
  validateNoteInput,
  normaliseTags,
};
