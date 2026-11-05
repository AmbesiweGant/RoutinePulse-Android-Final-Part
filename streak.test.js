'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { computeStreak, isScheduled, isValidDateString, addDays, isoWeekday } = require('../src/services/streak');

const daily = { frequency: 'daily', customDays: [] };
const weekdays = { frequency: 'weekdays', customDays: [] };

test('addDays crosses month and year boundaries', () => {
  assert.equal(addDays('2024-01-31', 1), '2024-02-01');
  assert.equal(addDays('2024-01-01', -1), '2023-12-31');
});

test('isValidDateString rejects bad input', () => {
  assert.equal(isValidDateString('2024-02-30'), false);
  assert.equal(isValidDateString('24-01-01'), false);
  assert.equal(isValidDateString(null), false);
  assert.equal(isValidDateString('2024-02-29'), true);
});

test('isoWeekday: Monday is 1, Sunday is 7', () => {
  assert.equal(isoWeekday('2024-01-08'), 1);
  assert.equal(isoWeekday('2024-01-07'), 7);
});

test('daily streak counts consecutive days ending today', () => {
  assert.equal(computeStreak(daily, ['2024-01-08', '2024-01-07', '2024-01-06'], '2024-01-08'), 3);
});

test('streak is still alive when today is not done yet', () => {
  assert.equal(computeStreak(daily, ['2024-01-07', '2024-01-06'], '2024-01-08'), 2);
});

test('a missed day breaks the streak', () => {
  assert.equal(computeStreak(daily, ['2024-01-08', '2024-01-06', '2024-01-05'], '2024-01-08'), 1);
});

test('no completions gives zero', () => {
  assert.equal(computeStreak(daily, [], '2024-01-08'), 0);
});

test('weekday habit skips the weekend without breaking', () => {
  // Mon 8 (done), Sat/Sun skipped, Fri 5 + Thu 4 done, Wed 3 missed
  assert.equal(computeStreak(weekdays, ['2024-01-08', '2024-01-05', '2024-01-04'], '2024-01-08'), 3);
});

test('custom habit only schedules chosen days', () => {
  const custom = { frequency: 'custom', customDays: [1, 3] }; // Mon, Wed
  assert.equal(isScheduled(custom, '2024-01-08'), true);
  assert.equal(isScheduled(custom, '2024-01-09'), false);
  assert.equal(computeStreak(custom, ['2024-01-08', '2024-01-03'], '2024-01-08'), 2);
});
