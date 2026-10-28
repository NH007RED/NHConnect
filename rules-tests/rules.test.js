'use strict';
// Security tests for database.rules.json. Run from the project root folder with the Firebase CLI installed:
//   cd rules-tests && npm install && npm test
const { initializeTestEnvironment, assertFails, assertSucceeds } = require('@firebase/rules-unit-testing');
const { ref, get, set, update, serverTimestamp } = require('firebase/database');
const fs = require('node:fs');
const test = require('node:test');

const A = 'userAAAAAAAAAAAAAAAAAAAAAAAAAAAA'; // ids sort: A < B < C
const B = 'userBBBBBBBBBBBBBBBBBBBBBBBBBBBB';
const C = 'userCCCCCCCCCCCCCCCCCCCCCCCCCCCC';
const chat = `${A}_${B}`;
let env;

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-nhconnect',
    database: { rules: fs.readFileSync('../database.rules.json', 'utf8'), host: '127.0.0.1', port: 9000 },
  });
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.database();
    for (const [uid, name] of [[A, 'alice'], [B, 'bob'], [C, 'carol']]) {
      await set(ref(db, `users/${uid}`), { displayName: name, username: name, usernameLower: name, country: 'IN', language: 'en', createdAt: 1 });
    }
    await set(ref(db, `chats/${chat}/messages/m1`), {
      senderId: A, recipientId: B, originalText: 'hi', sourceLanguage: 'en', targetLanguage: 'bn',
      translationStatus: 'pending', messageType: 'text', createdAt: 1,
    });
  });
});
test.after(async () => env.cleanup());

const dbFor = (uid, verified = true) => env.authenticatedContext(uid, { email_verified: verified }).database();
const newMsg = (from, to, text = 'hello') => ({
  senderId: from, recipientId: to, originalText: text, sourceLanguage: 'en', targetLanguage: 'bn',
  translationStatus: 'pending', messageType: 'text', createdAt: serverTimestamp(),
});

test('participant can send a message', async () => { await assertSucceeds(set(ref(dbFor(A), `chats/${chat}/messages/m2`), newMsg(A, B))); });
test('Test 5: outsider cannot read another conversation', async () => { await assertFails(get(ref(dbFor(C), `chats/${chat}/messages`))); });
test('outsider cannot write into another conversation', async () => { await assertFails(set(ref(dbFor(C), `chats/${chat}/messages/m3`), newMsg(C, B))); });
test('cannot impersonate a sender', async () => { await assertFails(set(ref(dbFor(A), `chats/${chat}/messages/m4`), newMsg(B, A))); });
test('unverified email cannot send', async () => { await assertFails(set(ref(dbFor(A, false), `chats/${chat}/messages/m5`), newMsg(A, B))); });
test('client cannot write translation fields', async () => {
  await assertFails(update(ref(dbFor(A), `chats/${chat}/messages/m1`), { translatedText: 'fake', translationStatus: 'completed' }));
});
test('sender cannot create a message already marked completed', async () => {
  await assertFails(set(ref(dbFor(A), `chats/${chat}/messages/m6`), { ...newMsg(A, B), translationStatus: 'completed', translatedText: 'x' }));
});
test('message text limit', async () => { await assertFails(set(ref(dbFor(A), `chats/${chat}/messages/m7`), newMsg(A, B, 'x'.repeat(4001)))); });
test('recipient cannot edit the sender message', async () => { await assertFails(update(ref(dbFor(B), `chats/${chat}/messages/m1`), { originalText: 'hacked' })); });
test('sender can edit own message', async () => { await assertSucceeds(update(ref(dbFor(A), `chats/${chat}/messages/m1`), { originalText: 'hi!', editedAt: serverTimestamp() })); });
test('only the recipient writes receipts, and only their own', async () => {
  await assertSucceeds(set(ref(dbFor(B), `messageReceipts/${chat}/m1/${B}/readAt`), serverTimestamp()));
  await assertFails(set(ref(dbFor(A), `messageReceipts/${chat}/m1/${A}/readAt`), serverTimestamp()));
  await assertFails(set(ref(dbFor(B), `messageReceipts/${chat}/m1/${A}/readAt`), serverTimestamp()));
});
test('cannot write another user profile', async () => { await assertFails(update(ref(dbFor(A), `users/${B}`), { displayName: 'x' })); });
test('cannot read another user chat list', async () => { await assertFails(get(ref(dbFor(A), `userChats/${B}`))); });
test('cannot forge unread counters or summaries', async () => {
  await assertFails(set(ref(dbFor(A), `userChats/${A}/${chat}/lastMessageText`), 'forged'));
  await assertSucceeds(set(ref(dbFor(A), `userChats/${A}/${chat}/unreadCount`), 0));
});
test('users list cannot be dumped without a query', async () => { await assertFails(get(ref(dbFor(A), 'users'))); });
test('blocked sender cannot message the blocker', async () => {
  await env.withSecurityRulesDisabled((ctx) => set(ref(ctx.database(), `userBlocks/${B}/${A}`), true));
  await assertFails(set(ref(dbFor(A), `chats/${chat}/messages/m8`), newMsg(A, B)));
});
test('translation request only by the recipient', async () => {
  const req = (uid) => ({ requestedBy: uid, requestedAt: serverTimestamp() });
  await assertSucceeds(set(ref(dbFor(B), `translationRequests/${chat}/m1`), req(B)));
  await assertFails(set(ref(dbFor(A), `translationRequests/${chat}/m1x`), req(A)));
});
test('server-only nodes are closed', async () => {
  await assertFails(get(ref(dbFor(A), 'rateLimits')));
  await assertFails(set(ref(dbFor(A), 'translationCache/x'), { text: 'x', at: 1 }));
  await assertFails(get(ref(dbFor(A), 'reports')));
});

test('image message: sender writes media, others cannot', async () => {
  const msg = { ...newMsg(A, B, ''), messageType: 'image', mediaThumb: 'abcd', mediaW: 800, mediaH: 600 };
  await assertSucceeds(set(ref(dbFor(A), `chats/${chat}/messages/img1`), msg));
  const media = { data: 'QUJD', w: 800, h: 600 };
  await assertSucceeds(set(ref(dbFor(A), `chatMedia/${chat}/img1`), media));
  await assertFails(set(ref(dbFor(B), `chatMedia/${chat}/img1b`), media));
  await assertSucceeds(get(ref(dbFor(B), `chatMedia/${chat}/img1/data`)));
  await assertFails(get(ref(dbFor(C), `chatMedia/${chat}/img1/data`)));
});
test('media cannot be attached to a text message or exceed the size limit', async () => {
  await assertFails(set(ref(dbFor(A), `chatMedia/${chat}/m1`), { data: 'QUJD', w: 1, h: 1 }));
  await assertFails(set(ref(dbFor(A), `chatMedia/${chat}/img1`), { data: 'x'.repeat(420001), w: 1, h: 1 }));
});
test('profile pictures: own write only, anyone signed in can read', async () => {
  const photo = { data: 'QUJD', updatedAt: serverTimestamp() };
  await assertSucceeds(set(ref(dbFor(A), `userPhotos/${A}`), photo));
  await assertFails(set(ref(dbFor(B), `userPhotos/${A}`), photo));
  await assertSucceeds(get(ref(dbFor(B), `userPhotos/${A}/data`)));
});

test('file offer: metadata only; download request routed to the sender only', async () => {
  const offer = { ...newMsg(A, B, ''), messageType: 'file', fileName: 'a.pdf', fileSize: 1000, fileMime: 'application/pdf' };
  await assertSucceeds(set(ref(dbFor(A), `chats/${chat}/messages/f1`), offer));
  const req = (by) => ({ chatId: chat, messageId: 'f1', sid: 's1', by, at: serverTimestamp() });
  await assertSucceeds(set(ref(dbFor(B), `p2pInbox/${A}/r1`), req(B)));
  await assertFails(set(ref(dbFor(C), `p2pInbox/${A}/r2`), req(C)));           // not a participant
  await assertFails(set(ref(dbFor(B), `p2pInbox/${C}/r3`), req(B)));           // wrong target
  await assertFails(get(ref(dbFor(B), `p2pInbox/${A}`)));                       // only the target reads its inbox
  await assertSucceeds(set(ref(dbFor(A), `p2p/${chat}/s1/offer`), { t: 'offer', s: 'v=0' }));
  await assertFails(set(ref(dbFor(C), `p2p/${chat}/s1/answer`), { t: 'answer', s: 'v=0' }));
});
