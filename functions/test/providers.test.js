'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { getProvider, withRetry, ProviderError } = require('../lib/providers');

function mockFetch(handler) {
  const original = global.fetch;
  global.fetch = async (url, opts) => handler(url, opts);
  return () => { global.fetch = original; };
}
const json = (status, body) => ({ ok: status >= 200 && status < 300, status, json: async () => body });

test('google provider parses translation and detected language, key sent as header', async () => {
  let seen;
  const restore = mockFetch((url, opts) => {
    seen = { url, headers: opts.headers, body: JSON.parse(opts.body) };
    return json(200, { data: { translations: [{ translatedText: 'হ্যালো', detectedSourceLanguage: 'en' }] } });
  });
  try {
    const r = await getProvider({ provider: 'google', apiKey: 'K' }).translate({ html: 'Hello', target: 'bn' });
    assert.deepStrictEqual(r, { text: 'হ্যালো', detected: 'en' });
    assert.strictEqual(seen.headers['X-goog-api-key'], 'K');
    assert.ok(!seen.url.includes('K'), 'key must not be in the URL');
    assert.strictEqual(seen.body.target, 'bn');
  } finally { restore(); }
});

test('libretranslate provider parses response', async () => {
  const restore = mockFetch(() => json(200, { translatedText: 'I am going home.', detectedLanguage: { language: 'bn' } }));
  try {
    const r = await getProvider({ provider: 'libretranslate', libreUrl: 'https://t.example/', apiKey: 'none' }).translate({ html: 'x', target: 'en' });
    assert.deepStrictEqual(r, { text: 'I am going home.', detected: 'bn' });
  } finally { restore(); }
});

test('misconfiguration and disabled provider produce clear error codes', () => {
  assert.throws(() => getProvider({ provider: 'google', apiKey: 'none' }), (e) => e.code === 'provider_not_configured');
  assert.throws(() => getProvider({ provider: 'disabled' }), (e) => e.code === 'provider_disabled');
  assert.throws(() => getProvider({ provider: 'libretranslate' }), (e) => e.code === 'provider_not_configured');
});

test('HTTP errors map to retryable / non-retryable codes', async () => {
  const p = getProvider({ provider: 'google', apiKey: 'K' });
  let restore = mockFetch(() => json(503, {}));
  await assert.rejects(p.translate({ html: 'x', target: 'bn' }), (e) => e.code === 'provider_unavailable' && e.retryable === true);
  restore();
  restore = mockFetch(() => json(403, {}));
  await assert.rejects(p.translate({ html: 'x', target: 'bn' }), (e) => e.code === 'provider_auth_failed' && e.retryable === false);
  restore();
});

test('withRetry retries only retryable errors', async () => {
  let n = 0;
  const ok = await withRetry(async () => { if (++n < 3) throw new ProviderError('timeout', true); return 'done'; }, 3, 1);
  assert.strictEqual(ok, 'done');
  n = 0;
  await assert.rejects(withRetry(async () => { n++; throw new ProviderError('provider_rejected', false); }, 3, 1));
  assert.strictEqual(n, 1);
});
