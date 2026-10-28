'use strict';
// Text helpers: protect URLs, @usernames and code from translation, and restore the result.

const PROTECT_SOURCE = '```[\\s\\S]*?```|`[^`\\n]+`|\\bhttps?:\\/\\/[^\\s]+|\\bwww\\.[^\\s]+|(?<![\\w@])@[A-Za-z0-9_]{3,20}';
const protectRe = () => new RegExp(PROTECT_SOURCE, 'gi');

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** Escapes the text and wraps untranslatable tokens in notranslate spans (understood by Google and LibreTranslate html mode). */
function protect(text) {
  const re = protectRe();
  let out = '';
  let last = 0;
  let m;
  while ((m = re.exec(text)) !== null) {
    out += escapeHtml(text.slice(last, m.index));
    out += '<span class="notranslate" translate="no">' + escapeHtml(m[0]) + '</span>';
    last = m.index + m[0].length;
  }
  return out + escapeHtml(text.slice(last));
}

/** Reverses protect(): removes tags and unescapes entities. */
function restore(html) {
  return html
    .replace(/<\/?span[^>]*>/gi, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&#x27;/g, "'")
    .replace(/&amp;/g, '&');
}

/** False for messages that only contain URLs, @mentions, code, emoji, digits or punctuation. */
function hasTranslatableLetters(text) {
  return /\p{L}/u.test(text.replace(protectRe(), ''));
}

function baseLang(code) {
  return String(code || '').toLowerCase().split('-')[0];
}

function truncate(text, n) {
  const chars = Array.from(text || '');
  return chars.length <= n ? chars.join('') : chars.slice(0, n - 1).join('') + '…';
}

function validPair(chatId, msg) {
  if (!msg || typeof msg.senderId !== 'string' || typeof msg.recipientId !== 'string') return false;
  if (msg.senderId === msg.recipientId) return false;
  return chatId === [msg.senderId, msg.recipientId].sort().join('_');
}

/** Text shown in chat lists / notifications. Image messages get a camera prefix. */
function previewOf(msg, text) {
  const t = text !== undefined ? text : msg.originalText;
  if (msg && msg.messageType === 'file') return '📎 ' + (msg.fileName || 'File');
  if (msg && msg.messageType === 'image') return '📷 ' + (t && t.trim() ? t : 'Photo');
  return t || '';
}

module.exports = { previewOf, protect, restore, hasTranslatableLetters, baseLang, truncate, validPair };
