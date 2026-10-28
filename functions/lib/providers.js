'use strict';
// Replaceable translation providers. Each provider exposes: translate({ html, target }) -> { text, detected }
// Credentials come from the server environment only (Secret Manager), never from the app.

class ProviderError extends Error {
  constructor(code, retryable, message) {
    super(message || code);
    this.code = code;
    this.retryable = retryable;
  }
}

async function postJson(url, body, headers, timeoutMs) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...headers },
      body: JSON.stringify(body),
      signal: ctl.signal,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      const retryable = res.status === 429 || res.status >= 500;
      const code = res.status === 429 ? 'provider_rate_limited' : res.status >= 500 ? 'provider_unavailable'
        : res.status === 401 || res.status === 403 ? 'provider_auth_failed' : 'provider_rejected';
      throw new ProviderError(code, retryable, 'HTTP ' + res.status); // never include the request body in errors/logs
    }
    return data;
  } catch (e) {
    if (e instanceof ProviderError) throw e;
    if (e && e.name === 'AbortError') throw new ProviderError('timeout', true);
    throw new ProviderError('network_error', true);
  } finally {
    clearTimeout(timer);
  }
}

function google(apiKey, timeoutMs) {
  return {
    name: 'google',
    async translate({ html, target }) {
      const data = await postJson(
        'https://translation.googleapis.com/language/translate/v2',
        { q: [html], target, format: 'html' }, // source omitted -> auto-detect
        { 'X-goog-api-key': apiKey }, timeoutMs);
      const t = data && data.data && data.data.translations && data.data.translations[0];
      if (!t || typeof t.translatedText !== 'string') throw new ProviderError('bad_response', false);
      return { text: t.translatedText, detected: t.detectedSourceLanguage || null };
    },
  };
}

function libretranslate(baseUrl, apiKey, timeoutMs) {
  return {
    name: 'libretranslate',
    async translate({ html, target }) {
      const body = { q: html, source: 'auto', target, format: 'html' };
      if (apiKey && apiKey !== 'none') body.api_key = apiKey;
      const data = await postJson(baseUrl.replace(/\/+$/, '') + '/translate', body, {}, timeoutMs);
      if (typeof data.translatedText !== 'string') throw new ProviderError('bad_response', false);
      return { text: data.translatedText, detected: data.detectedLanguage ? data.detectedLanguage.language : null };
    },
  };
}

/** Returns a provider, or throws ProviderError('provider_not_configured' | 'provider_disabled'). */
function getProvider(cfg) {
  const timeoutMs = cfg.timeoutMs || 10000;
  switch ((cfg.provider || '').toLowerCase()) {
    case 'google':
      if (!cfg.apiKey || cfg.apiKey === 'none') throw new ProviderError('provider_not_configured', false);
      return google(cfg.apiKey, timeoutMs);
    case 'libretranslate':
      if (!cfg.libreUrl) throw new ProviderError('provider_not_configured', false);
      return libretranslate(cfg.libreUrl, cfg.apiKey, timeoutMs);
    case 'disabled':
      throw new ProviderError('provider_disabled', false);
    default:
      throw new ProviderError('provider_not_configured', false);
  }
}

/** Up to `attempts` tries with exponential backoff, only for retryable errors. */
async function withRetry(fn, attempts = 3, baseDelayMs = 400) {
  let last;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (e) {
      last = e;
      if (!(e instanceof ProviderError) || !e.retryable || i === attempts - 1) break;
      await new Promise((r) => setTimeout(r, baseDelayMs * 2 ** i));
    }
  }
  throw last;
}

module.exports = { ProviderError, getProvider, withRetry, google, libretranslate };
