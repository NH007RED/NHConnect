# Manual acceptance checklist (two real devices or two emulators)

Prepare: deploy rules + functions, set the translation secret. Create User A (English, India) and User B (Bangla, Bangladesh); verify both emails.

| # | Scenario | Steps | Expected | Pass |
|---|----------|-------|----------|------|
| 1 | EN -> BN | A searches B by username, sends "Where are you going?" | B sees a Bangla translation, "View original" shows the English text, A sees only the original | [ ] |
| 2 | BN -> EN | B replies "আমি বাড়ি যাচ্ছি।" | A sees an English translation + working toggle | [ ] |
| 3 | Provider down | Set `TRANSLATION_PROVIDER=disabled`, redeploy, A sends a message | Message arrives; B sees original + "Translation unavailable — tap to retry." Restore provider, tap: translation appears | [ ] |
| 4 | Offline | Put A in airplane mode, send 3 messages | Clock icons, no errors. Reconnect: all 3 sent exactly once, in order | [ ] |
| 5 | Unauthorized | Run `rules-tests` (or try reading `chats/<other ids>` with a third account) | Read/write rejected | [ ] |
| 6 | Long history | Seed 200+ messages, open chat | Newest 30 shown; scrolling up loads 30 more at a time | [ ] |
| 7 | Language switch | B changes language to Hindi in Settings | New messages arrive in Hindi; banner translates older ones | [ ] |
| 8 | Real device | Sign up, verify, chat, background the app | Push arrives, tap opens the chat; presence/typing/ticks work; error states readable | [ ] |
| 9 | Receipts | A sends; B online but chat closed; B opens chat | A: one grey tick -> two grey (delivered) -> two blue (read) | [ ] |
| 10 | Edit/delete | A edits and deletes a message | B sees "edited" + retranslation; deleted shows placeholder | [ ] |
| 11 | Block/report | B blocks A | A's new messages fail (retry icon); report appears in console under `reports` | [ ] |
| 12 | Account deletion | A deletes account with password | A's profile, username and chats disappear; B sees "Deleted account" | [ ] |
