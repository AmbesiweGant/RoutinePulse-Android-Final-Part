'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const v = require('../src/validation');

test('email validation', () => {
  assert.equal(v.validateEmail('student@example.com'), null);
  assert.ok(v.validateEmail('not-an-email'));
  assert.ok(v.validateEmail('   '));
  assert.ok(v.validateEmail(undefined));
});

test('password validation requires length, a letter and a number', () => {
  assert.equal(v.validateNewPassword('Study2026'), null);
  assert.ok(v.validateNewPassword('short1'));
  assert.ok(v.validateNewPassword('onlyletters'));
  assert.ok(v.validateNewPassword('12345678'));
  assert.ok(v.validateNewPassword('a1'.repeat(40)));
});

test('display name validation', () => {
  assert.equal(v.validateDisplayName('Lindiwe'), null);
  assert.ok(v.validateDisplayName('L'));
  assert.ok(v.validateDisplayName(''));
});

test('habit input: valid daily habit gets defaults', () => {
  const { error, value } = v.validateHabitInput({ title: '  Drink water ' });
  assert.equal(error, undefined);
  assert.deepEqual(value, { title: 'Drink water', frequency: 'daily', customDays: [], reminderTime: null, priority: 'medium' });
});

test('habit input: rejects bad values', () => {
  assert.ok(v.validateHabitInput({ title: '' }).error);
  assert.ok(v.validateHabitInput({ title: 'x', frequency: 'hourly' }).error);
  assert.ok(v.validateHabitInput({ title: 'x', frequency: 'custom', customDays: [] }).error);
  assert.ok(v.validateHabitInput({ title: 'x', frequency: 'custom', customDays: [9] }).error);
  assert.ok(v.validateHabitInput({ title: 'x', reminderTime: '25:99' }).error);
  assert.ok(v.validateHabitInput({ title: 'x', priority: 'urgent' }).error);
});

test('habit input: custom days are de-duplicated and sorted', () => {
  const { value } = v.validateHabitInput({ title: 'Gym', frequency: 'custom', customDays: [5, 1, 5], reminderTime: '07:30' });
  assert.deepEqual(value.customDays, [1, 5]);
  assert.equal(value.reminderTime, '07:30');
});

test('note input and tag normalisation', () => {
  const { value } = v.validateNoteInput({ subject: 'Biology', title: 'Cells', content: 'Text', tags: [' Exam-Prep ', 'exam-prep', '', 5] });
  assert.deepEqual(value.tags, ['exam-prep']);
  assert.ok(v.validateNoteInput({ subject: '', title: 'x', content: 'y' }).error);
  assert.ok(v.validateNoteInput({ subject: 's', title: 't', content: '   ' }).error);
});
