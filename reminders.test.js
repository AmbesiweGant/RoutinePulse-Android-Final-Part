'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { localParts, selectDueNotifications } = require('../src/services/reminders');
const { habitDto, noteDto } = require('../src/dto');

// 2024-01-08 is a Monday.
const NOW = new Date('2024-01-08T05:30:00Z'); // 07:30 in South Africa (UTC+2, no daylight saving)

test('localParts converts UTC to the users time zone', () => {
  const p = localParts(NOW, 'Africa/Johannesburg');
  assert.equal(p.date, '2024-01-08');
  assert.equal(p.hhmm, '07:30');
  assert.equal(p.hour, 7);
});

test('localParts can roll over to the next calendar day', () => {
  const p = localParts(new Date('2024-01-08T23:30:00Z'), 'Africa/Johannesburg');
  assert.equal(p.date, '2024-01-09');
  assert.equal(p.hhmm, '01:30');
});

test('localParts falls back to UTC for an unknown zone and handles midnight', () => {
  const p = localParts(new Date('2024-01-08T00:05:00Z'), 'Not/AZone');
  assert.equal(p.timeZone, 'UTC');
  assert.equal(p.hhmm, '00:05');
});

const habit = (over = {}) => ({
  id: 'h1', title: 'Study Biology', frequency: 'daily', customDays: [],
  reminderTime: '07:30', lastReminderKey: null, lastRiskDate: null, ...over,
});

test('a reminder is due at the exact minute', () => {
  const parts = localParts(NOW, 'Africa/Johannesburg');
  const due = selectDueNotifications([habit()], new Map(), parts);
  assert.equal(due.length, 1);
  assert.equal(due[0].type, 'reminder');
  assert.equal(due[0].key, '2024-01-08 07:30');
});

test('no reminder at a different minute', () => {
  const parts = localParts(new Date('2024-01-08T05:31:00Z'), 'Africa/Johannesburg');
  assert.equal(selectDueNotifications([habit()], new Map(), parts).length, 0);
});

test('a reminder is not repeated once it was sent', () => {
  const parts = localParts(NOW, 'Africa/Johannesburg');
  const sent = habit({ lastReminderKey: '2024-01-08 07:30' });
  assert.equal(selectDueNotifications([sent], new Map(), parts).length, 0);
});

test('no reminder when the habit is already done today', () => {
  const parts = localParts(NOW, 'Africa/Johannesburg');
  const done = new Map([['h1', ['2024-01-08']]]);
  assert.equal(selectDueNotifications([habit()], done, parts).length, 0);
});

test('weekday habits stay quiet at the weekend', () => {
  const saturday = localParts(new Date('2024-01-13T05:30:00Z'), 'Africa/Johannesburg'); // Sat 07:30
  assert.equal(selectDueNotifications([habit({ frequency: 'weekdays' })], new Map(), saturday).length, 0);
});

test('streak-risk alert at 20:00 when a streak of 2+ days is not done today', () => {
  const evening = localParts(new Date('2024-01-08T18:00:00Z'), 'Africa/Johannesburg'); // 20:00
  const dates = new Map([['h1', ['2024-01-07', '2024-01-06', '2024-01-05']]]);
  const due = selectDueNotifications([habit({ reminderTime: null })], dates, evening);
  assert.equal(due.length, 1);
  assert.equal(due[0].type, 'streak_risk');
  assert.equal(due[0].streak, 3);
  assert.equal(due[0].hoursLeft, 4);
});

test('no streak-risk alert for a streak of 1, or if already sent today', () => {
  const evening = localParts(new Date('2024-01-08T18:00:00Z'), 'Africa/Johannesburg');
  const one = new Map([['h1', ['2024-01-07']]]);
  assert.equal(selectDueNotifications([habit({ reminderTime: null })], one, evening).length, 0);
  const three = new Map([['h1', ['2024-01-07', '2024-01-06', '2024-01-05']]]);
  assert.equal(selectDueNotifications([habit({ reminderTime: null, lastRiskDate: '2024-01-08' })], three, evening).length, 0);
});

test('habitDto exposes updatedAt and the full completion list used for offline sync', () => {
  const h = { id: 'h1', title: 'T', frequency: 'daily', customDays: [], reminderTime: null, priority: 'low',
    createdAt: 'c', updatedAt: 'u' };
  const dto = habitDto(h, ['2024-01-07', '2024-01-08', '2024-01-06'], '2024-01-08');
  assert.deepEqual(dto.completionDates, ['2024-01-06', '2024-01-07', '2024-01-08']);
  assert.equal(dto.updatedAt, 'u');
  assert.equal(dto.streakCount, 3);
  assert.equal(dto.completedToday, true);
});

test('noteDto only flags conflict when the server copy won', () => {
  const n = { id: 'n1', subject: 's', title: 't', content: 'c', tags: [], updatedAt: 'u' };
  assert.equal(noteDto(n).conflict, undefined);
  assert.equal(noteDto(n, true).conflict, true);
});
