import test from 'node:test';
import assert from 'node:assert/strict';

import { createStreamSanitizer, presentSuggestion, sanitizeSuggestion } from './code-prediction-service.js';

test('sanitizeSuggestion drops a repeated current line and keeps at most four lines', () => {
  const prefix = 'function add(a, b) {\n  return ';
  const raw = '  return a + b;\n}\nextra\nmore\nstill';
  assert.equal(sanitizeSuggestion(prefix, raw), 'a + b;\n}\nextra\nmore');
});

test('sanitizeSuggestion strips fences and a leading newline after a line break', () => {
  const suggestion = sanitizeSuggestion('const value =\n', '```js\n\n42\n```');
  assert.equal(suggestion, '42');
});

test('stream sanitizer reveals only the newly visible suffix', () => {
  const sanitizer = createStreamSanitizer('function add(a, b) {\n  return ');
  assert.equal(sanitizer.push('  return a'), 'a');
  assert.equal(sanitizer.push(' + b;\n}\n'), ' + b;\n}');
  assert.equal(sanitizer.finish(), '');
});

test('presentSuggestion keeps a code continuation and drops a Chinese explanation', () => {
  const prefix = 'public void run() {\n    if (';
  assert.equal(presentSuggestion(prefix, 'input == null) {\n        return;\n    }'), 'input == null) {\n        return;\n    }');
  assert.equal(presentSuggestion(prefix, '你的代码可以这样写：\n```java\ninput == null) {\n```'), 'input == null) {');
  assert.equal(presentSuggestion(prefix, '这段没有代码块'), '');
});

test('stream sanitizer hides a code fence until real code arrives', () => {
  const sanitizer = createStreamSanitizer('const value =\n');
  assert.equal(sanitizer.push('```js\n'), '');
  assert.equal(sanitizer.push('42'), '42');
  assert.equal(sanitizer.push('\n```'), '');
  assert.equal(sanitizer.finish(), '');
});
