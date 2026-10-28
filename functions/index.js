'use strict';
/**
 * NH Connect backend (Firebase Cloud Functions v2, Node 22).
 *
 *  onMessageCreated  - new message: bookkeeping (chat summaries, unread), translation, push notification
 *  onMessageUpdated  - edited / deleted message: refresh summaries and re-translate (no loops: only reacts to text changes)
 *  onTranslationRequest - recipient taps "retry" or switches language: (re)translate one message
 *  onUserDeleted     - account deletion cleanup
 *  cleanup           - scheduled: rate-limit buckets and old translation cache entries
 *
 * Secrets: firebase functions:secrets:set TRANSLATION_API_KEY   (never stored in the app or database)
 */
const admin = require('firebase-admin');
const logger = require('firebase-functions/logger');
const functionsV1 = require('firebase-functions/v1');
const { onValueCreated, onValueUpdated } = require('firebase-functions/v2/database');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { defineSecret, defineString } = require('firebase-functions/params');
const crypto = require('node:crypto');

const { protect, restore, hasTranslatableLetters, baseLang, truncate, validPair, previewOf } = require('./lib/text');
const { ProviderError, getProvider, withRetry } = require('./lib/providers');

admin.initializeApp();
const db = () => admin.database();
const TS = () => admin.database.ServerValue.TIMESTAMP;

const TRANSLATION_API_KEY = defineSecret('TRANSLATION_API_KEY');
const TRANSLATION_PROVIDER = defineString('TRANSLATION_PROVIDER', { default: 'google' });
const LIBRETRANSLATE_URL = defineString('LIBRETRANSLATE_URL', { default: '' });

const REGION = process.env.NH_FUNCTIONS_REGION || 'us-central1';
const withInstance = (o) => (process.env.NH_DB_INSTANCE ? { ...o, instance: process.env.NH_DB_INSTANCE } : o);
const baseOpts = { region: REGION, secrets: [TRANSLATION_API_KEY], timeoutSeconds: 120, memory: '256MiB' };

const MAX_TRANSLATIONS_PER_MINUTE = 40;
const CACHE_MAX_CHARS = 80;          // only short, generic phrases are cached ("ok", "how are you?")
const CACHE_TTL_MS = 30 * 24 * 3600 * 1000;

// ---------- helpers ----------

async function allowRate(uid, kind) {
  const bucket = Math.floor(Date.now() / 60000);
  const r = await db().ref(`rateLimits/${kind}/${uid}/${bucket}`).transaction((c) => (c || 0) + 1);
  return (r.snapshot.val() || 0) <= MAX_TRANSLATIONS_PER_MINUTE;
}

function cacheKey(text, target) {
  return crypto.createHash('sha256').update(target + '\u0000' + text).digest('hex');
}

function provider() {
  return getProvider({
    provider: TRANSLATION_PROVIDER.value(),
    apiKey: TRANSLATION_API_KEY.value(),
    libreUrl: LIBRETRANSLATE_URL.value(),
  });
}

/** Translates one message for the recipient's CURRENT language and stores the result on that message only. */
async function translateMessage(chatId, messageId, requestedBy) {
  const msgRef = db().ref(`chats/${chatId}/messages/${messageId}`);
  const snap = await msgRef.get();
  if (!snap.exists()) return;
  const msg = snap.val();
  if (!validPair(chatId, msg)) { logger.warn('ignoring message with invalid chat pair', { chatId, messageId }); return; }
  if (requestedBy && requestedBy !== msg.recipientId) return;
  if (msg.deleted) {
    await msgRef.update({ translatedText: null, translationStatus: 'skipped', translationError: null });
    return;
  }

  // Idempotency: only one invocation may move pending -> translating.
  const claim = await msgRef.child('translationStatus').transaction((cur) => {
    if (cur === null) return cur;
    return cur === 'pending' ? 'translating' : undefined;
  });
  if (!claim.committed) return;

  const finish = (fields) => msgRef.update({
    translationUpdatedAt: TS(), translationError: null, translatedText: null, translationProvider: null, ...fields,
  });

  const text = msg.originalText;
  try {
    const recipient = (await db().ref(`users/${msg.recipientId}`).get()).val();
    const target = (recipient && recipient.language) || msg.targetLanguage;

    if (!(await allowRate(requestedBy || msg.senderId, 'translate'))) {
      return finish({ translationStatus: 'failed', translationError: 'rate_limited', targetLanguage: target });
    }
    if (!hasTranslatableLetters(text)) {
      return finish({ translationStatus: 'skipped', targetLanguage: target });
    }

    const short = Array.from(text).length <= CACHE_MAX_CHARS;
    const ckey = cacheKey(text, target);
    let result = null;
    if (short) {
      const c = (await db().ref(`translationCache/${ckey}`).get()).val();
      if (c && c.at > Date.now() - CACHE_TTL_MS) result = { text: c.text, detected: c.detected, provider: c.provider, cached: true };
    }
    if (!result) {
      const p = provider();
      const r = await withRetry(() => p.translate({ html: protect(text), target: baseLang(target) }));
      result = { text: restore(r.text), detected: r.detected, provider: p.name };
      if (short && result.text) {
        await db().ref(`translationCache/${ckey}`).set({ text: result.text, detected: result.detected || null, provider: p.name, at: Date.now() });
      }
    }

    // The sender may have edited the message while we were translating: do not store a stale translation.
    const latest = (await msgRef.child('originalText').get()).val();
    if (latest !== text) return;

    const detected = result.detected ? baseLang(result.detected) : null;
    if (detected && detected === baseLang(target)) {
      return finish({ translationStatus: 'skipped', detectedLanguage: detected, targetLanguage: target });
    }
    if (!result.text || !result.text.trim()) throw new ProviderError('bad_response', false);
    await finish({
      translationStatus: 'completed', translatedText: result.text, translationProvider: result.provider,
      detectedLanguage: detected, targetLanguage: target,
    });
    await refreshRecipientPreview(chatId, messageId, msg.recipientId, truncate(previewOf(msg, result.text), 100));
    return result.text;
  } catch (e) {
    const code = e instanceof ProviderError ? e.code : 'translation_failed';
    logger.warn('translation failed', { chatId, messageId, code }); // never log message content
    await finish({ translationStatus: 'failed', translationError: code });
  }
}

async function refreshRecipientPreview(chatId, messageId, recipientId, preview) {
  await db().ref(`userChats/${recipientId}/${chatId}`).transaction((cur) => {
    if (!cur) return cur;
    if (cur.lastMessageId === messageId) cur.lastMessageText = preview;
    return cur;
  });
}

async function ensureMetadata(chatId, msg) {
  const ref = db().ref(`chats/${chatId}/metadata`);
  await ref.transaction((cur) => cur || {
    participants: { [msg.senderId]: true, [msg.recipientId]: true },
    createdAt: Date.now(),
  });
}

async function updateSummaries(chatId, messageId, msg, { incrementUnread }) {
  const base = { lastMessageId: messageId, lastMessageAt: msg.createdAt || Date.now(), lastSenderId: msg.senderId };
  const preview = msg.deleted ? '' : truncate(previewOf(msg), 100);
  const updates = {};
  updates[`userChats/${msg.senderId}/${chatId}/peerId`] = msg.recipientId;
  updates[`userChats/${msg.recipientId}/${chatId}/peerId`] = msg.senderId;
  for (const uid of [msg.senderId, msg.recipientId]) {
    for (const [k, v] of Object.entries(base)) updates[`userChats/${uid}/${chatId}/${k}`] = v;
    updates[`userChats/${uid}/${chatId}/lastMessageText`] = preview;
  }
  await db().ref().update(updates);
  if (incrementUnread) {
    await db().ref(`userChats/${msg.recipientId}/${chatId}/unreadCount`).transaction((c) => (c || 0) + 1);
  }
}

async function notifyRecipient(chatId, messageId, msg, previewText) {
  const r = msg.recipientId;
  const [settings, chat, blocked, tokensSnap, sender] = await Promise.all([
    db().ref(`userSettings/${r}`).get(),
    db().ref(`userChats/${r}/${chatId}`).get(),
    db().ref(`userBlocks/${r}/${msg.senderId}`).get(),
    db().ref(`deviceTokens/${r}`).get(),
    db().ref(`users/${msg.senderId}/displayName`).get(),
  ]);
  const s = settings.val() || {};
  if (s.notificationsEnabled === false) return;
  if (blocked.exists() || (chat.val() && chat.val().muted === true)) return;
  const tokens = Object.keys(tokensSnap.val() || {});
  if (tokens.length === 0) return;

  const showPreview = s.notificationPreview !== false;
  const res = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: sender.val() || 'NH Connect',
      body: showPreview ? truncate(previewOf(msg, previewText), 80) || 'New message' : 'New message',
    },
    data: { chatId, peerId: msg.senderId, messageId },
    android: {
      priority: 'high',
      collapseKey: chatId,
      notification: { channelId: 'messages', tag: chatId, visibility: 'private' }, // lock screen hides content
    },
  });
  const dead = [];
  res.responses.forEach((x, i) => {
    const c = x.error && x.error.code;
    if (c === 'messaging/registration-token-not-registered' || c === 'messaging/invalid-registration-token') dead.push(tokens[i]);
  });
  await Promise.all(dead.map((t) => db().ref(`deviceTokens/${r}/${t}`).remove()));
}

// ---------- triggers ----------

exports.onMessageCreated = onValueCreated(withInstance({ ...baseOpts, ref: '/chats/{chatId}/messages/{messageId}' }), async (event) => {
  const { chatId, messageId } = event.params;
  const msg = event.data.val();
  if (!validPair(chatId, msg)) return;

  // At-least-once delivery: only the first invocation does the bookkeeping and sends the push.
  const processed = await db().ref(`chats/${chatId}/messages/${messageId}/processedAt`).transaction((c) => (c === null ? Date.now() : undefined));
  if (!processed.committed) return;

  await ensureMetadata(chatId, msg);
  await updateSummaries(chatId, messageId, msg, { incrementUnread: true });
  const translated = await translateMessage(chatId, messageId);
  await notifyRecipient(chatId, messageId, msg, translated || msg.originalText);
});

exports.onMessageUpdated = onValueUpdated(withInstance({ ...baseOpts, ref: '/chats/{chatId}/messages/{messageId}' }), async (event) => {
  const { chatId, messageId } = event.params;
  const before = event.data.before.val();
  const after = event.data.after.val();
  if (!before || !after) return;
  // Translation fields written by this function do not change originalText/deleted, so this exits (no loops).
  if (before.originalText === after.originalText && before.deleted === after.deleted) return;
  if (!validPair(chatId, after)) return;

  const chat = (await db().ref(`userChats/${after.recipientId}/${chatId}/lastMessageId`).get()).val();
  if (after.deleted) {
    await db().ref(`chatMedia/${chatId}/${messageId}`).remove(); // drop the compressed image
    await translateMessage(chatId, messageId); // clears translation
  } else {
    await db().ref(`chats/${chatId}/messages/${messageId}`).update({ translationStatus: 'pending', translatedText: null });
    await translateMessage(chatId, messageId);
  }
  if (chat === messageId) {
    await updateSummaries(chatId, messageId, { ...after, createdAt: (await db().ref(`userChats/${after.recipientId}/${chatId}/lastMessageAt`).get()).val() }, { incrementUnread: false });
    if (!after.deleted) {
      const t = (await db().ref(`chats/${chatId}/messages/${messageId}/translatedText`).get()).val();
      if (t) await refreshRecipientPreview(chatId, messageId, after.recipientId, truncate(previewOf(after, t), 100));
    }
  }
});

exports.onTranslationRequest = onValueCreated(withInstance({ ...baseOpts, ref: '/translationRequests/{chatId}/{messageId}' }), async (event) => {
  const { chatId, messageId } = event.params;
  const req = event.data.val() || {};
  try {
    const msgRef = db().ref(`chats/${chatId}/messages/${messageId}`);
    const msg = (await msgRef.get()).val();
    if (!validPair(chatId, msg) || msg.recipientId !== req.requestedBy) return;
    const status = (await msgRef.child('translationStatus').get()).val();
    if (status === 'translating') return;           // already in flight
    await msgRef.update({ translationStatus: 'pending' });
    await translateMessage(chatId, messageId, req.requestedBy);
  } finally {
    await event.data.ref.remove();
  }
});

exports.onUserDeleted = functionsV1.region(REGION).auth.user().onDelete(async (user) => {
  const uid = user.uid;
  const profile = (await db().ref(`users/${uid}`).get()).val();
  const chats = Object.keys((await db().ref(`userChats/${uid}`).get()).val() || {});
  const updates = {};
  if (profile && profile.usernameLower) updates[`usernames/${profile.usernameLower}`] = null;
  for (const chatId of chats) {
    const peer = chatId.split('_').find((p) => p !== uid);
    updates[`chats/${chatId}`] = null;               // policy: conversations are removed for both people
    updates[`messageReceipts/${chatId}`] = null;
    updates[`typing/${chatId}`] = null;
    updates[`translationRequests/${chatId}`] = null;
    updates[`chatMedia/${chatId}`] = null;
    updates[`p2p/${chatId}`] = null;
    if (peer) updates[`userChats/${peer}/${chatId}`] = null;
  }
  for (const p of ['users', 'userPhotos', 'p2pInbox', 'presence', 'userChats', 'userBlocks', 'userSettings', 'deviceTokens']) updates[`${p}/${uid}`] = null;
  await db().ref().update(updates);
  logger.info('account data removed', { uid });
});

exports.cleanup = onSchedule({ schedule: 'every 24 hours', region: REGION }, async () => {
  await db().ref('rateLimits').remove();
  await db().ref('p2p').remove();       // signaling leftovers (transfers last seconds to minutes)
  await db().ref('p2pInbox').remove();
  const old = await db().ref('translationCache').orderByChild('at').endAt(Date.now() - CACHE_TTL_MS).get();
  const updates = {};
  old.forEach((c) => { updates[c.key] = null; });
  if (Object.keys(updates).length) await db().ref('translationCache').update(updates);
});

exports._internal = { translateMessage };
