'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { generateRuleBasedQuestions, validateQuestions, scoreQuiz, validateAnswers } = require('../src/services/quizGenerator');

const NOTE = `# Cell Structure
Mitochondria are the powerhouse of the cell and produce most of the cellular energy.
The nucleus contains the genetic material of the cell.
Ribosomes assemble proteins from amino acids inside the cytoplasm.
The Golgi apparatus packages proteins for transport around the cell.`;

test('generates fill-in-the-blank questions with the right answer present', () => {
  const qs = generateRuleBasedQuestions(NOTE, { rng: () => 0.5 });
  assert.ok(qs.length >= 2, 'expected at least 2 questions');
  for (const q of qs) {
    assert.ok(q.text.includes('_____'));
    assert.ok(q.options.length >= 3);
    assert.equal(new Set(q.options.map((o) => o.toLowerCase())).size, q.options.length, 'options must be unique');
    assert.ok(q.correctIndex >= 0 && q.correctIndex < q.options.length);
  }
});

test('answer text is removed from the question stem', () => {
  const qs = generateRuleBasedQuestions(NOTE, { rng: () => 0.1 });
  for (const q of qs) {
    const answer = q.options[q.correctIndex];
    const stem = q.text.replace('Fill in the blank: ', '');
    assert.equal(new RegExp('\\b' + answer + '\\b', 'i').test(stem), false);
  }
});

test('a too-short note produces no questions', () => {
  assert.deepEqual(generateRuleBasedQuestions('Too short.'), []);
});

test('maxQuestions is respected', () => {
  assert.ok(generateRuleBasedQuestions(NOTE, { maxQuestions: 2 }).length <= 2);
});

test('validateQuestions drops malformed AI output', () => {
  const good = { text: 'Q?', options: ['a', 'b', 'c', 'd'], correctIndex: 2 };
  const out = validateQuestions([good, { text: 'bad', options: ['a'], correctIndex: 0 }, null, { ...good, correctIndex: 9 }]);
  assert.equal(out.length, 1);
  assert.deepEqual(validateQuestions('nonsense'), []);
});

test('scoreQuiz counts correct answers and returns feedback', () => {
  const questions = [
    { text: 'a', options: ['x', 'y', 'z'], correctIndex: 1 },
    { text: 'b', options: ['x', 'y', 'z'], correctIndex: 0 },
  ];
  const r = scoreQuiz(questions, [1, 2]);
  assert.equal(r.score, 1);
  assert.equal(r.total, 2);
  assert.deepEqual(r.correctAnswers, [1, 0]);
  assert.equal(r.feedback[0].correct, true);
  assert.equal(r.feedback[1].correctAnswer, 'x');
});

test('validateAnswers checks length and range', () => {
  const questions = [{ text: 'a', options: ['x', 'y', 'z'], correctIndex: 0 }];
  assert.equal(validateAnswers([2], questions), null);
  assert.ok(validateAnswers([], questions));
  assert.ok(validateAnswers([3], questions));
  assert.ok(validateAnswers(['1'], questions));
});
