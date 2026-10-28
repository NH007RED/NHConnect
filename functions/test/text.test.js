'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { protect, restore, hasTranslatableLetters, validPair, truncate, baseLang, previewOf } = require('../lib/text');

test('protect wraps URLs, mentions and code; restore round-trips', () => {
  const src = 'Hi @alice_01 see https://example.com/a?b=1 and `rm -rf` <b> & more';
  const html = protect(src);
  assert.match(html, /notranslate/);
  assert.ok(!html.includes('<b>'), 'raw tags must be escaped');
  assert.strictEqual(restore(html), src);
});

test('hasTranslatableLetters ignores urls, emoji, digits', () => {
  assert.strictEqual(hasTranslatableLetters('https://example.com'), false);
  assert.strictEqual(hasTranslatableLetters('😀😀 123 !!'), false);
  assert.strictEqual(hasTranslatableLetters('@bob'), false);
  assert.strictEqual(hasTranslatableLetters('ok'), true);
  assert.strictEqual(hasTranslatableLetters('আমি বাড়ি যাচ্ছি।'), true);
});

test('validPair requires sorted uid pair id', () => {
  assert.ok(validPair('a_b', { senderId: 'b', recipientId: 'a' }));
  assert.ok(!validPair('b_a', { senderId: 'a', recipientId: 'b' }));
  assert.ok(!validPair('a_a', { senderId: 'a', recipientId: 'a' }));
  assert.ok(!validPair('a_b', {}));
});

test('truncate and baseLang', () => {
  assert.strictEqual(truncate('abcdef', 4), 'abc…');
  assert.strictEqual(truncate('abc', 4), 'abc');
  assert.strictEqual(baseLang('zh-CN'), 'zh');
});

test('previewOf marks photos', () => {
  assert.strictEqual(previewOf({ messageType: 'image', originalText: '' }), '📷 Photo');
  assert.strictEqual(previewOf({ messageType: 'image', originalText: 'x' }, 'Sunset'), '📷 Sunset');
  assert.strictEqual(previewOf({ messageType: 'text', originalText: 'hello' }), 'hello');
});

test('previewOf marks files', () => {
  assert.strictEqual(previewOf({ messageType: 'file', fileName: 'report.pdf' }), '📎 report.pdf');
});
