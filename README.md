# NH Connect

Real-time chat with automatic per-user translation. Android (Kotlin, Jetpack Compose, Material 3, MVVM) + Firebase Authentication, Realtime Database, Cloud Functions, Cloud Messaging + Room.
**No Firebase Storage is used anywhere.**

> Honest status: every file here was written for you, the Node backend and the pure-Kotlin logic were run and pass their tests, but the Android app itself was **not compiled in the build sandbox** (no Android SDK there). Open it in Android Studio / Code on the Go, sync, and send me the first build error if there is one.

## How translation works
1. Sender writes `originalText` + `sourceLanguage` + `translationStatus: "pending"` (rules forbid clients from writing anything else about translation).
2. Cloud Function `onMessageCreated` claims the message (idempotent), looks up the recipient's **current** language, auto-detects the source, calls the provider with server-side credentials, validates the answer, and stores `translatedText`, `detectedLanguage`, `translationProvider`, `translationStatus`, `translationError` on that message only.
3. The recipient sees the translation with "View original"; the sender always sees their own original. The original is never overwritten.
4. If the provider is down: status `failed`, the message is still delivered, UI shows **"Translation unavailable — tap to retry."** (tap writes a `translationRequests` entry; the backend retries).
5. After the recipient changes language, new messages use the new language; a banner offers "translate older messages" (re-translates the last 40 into the new language).
URLs, @usernames and `code` are wrapped as `notranslate`. Emoji/numbers-only messages are skipped. Short generic phrases (<=80 chars) are cached for 30 days.

## Setup (about 20 minutes)
### 1. Firebase project
Console -> create project -> **Upgrade to Blaze** (Cloud Functions and the translation API need it; usage for testing is tiny).
- Build -> Authentication -> enable **Email/Password**.
- Build -> Realtime Database -> Create database (note the region; if it is not `us-central1` put its URL in `local.properties`, see `local.properties.example`, and set `NH_FUNCTIONS_REGION` / `NH_DB_INSTANCE` in `functions/.env`).
- Project settings -> Add Android app, package **`com.nhdeveloper.nhconnect`** -> download **google-services.json** into `app/` (template: `app/google-services.json.example`).
- Cloud Messaging is enabled by default (FCM v1).

### 2. Translation provider (needs your credential)
Default: **Google Cloud Translation (Basic v2)**. Enable "Cloud Translation API" in Google Cloud for the same project, create an **API key restricted to that API**, then:
```
cd functions && npm install
cp .env.example .env            # TRANSLATION_PROVIDER=google
firebase functions:secrets:set TRANSLATION_API_KEY     # paste the key
```
Alternative: self-hosted **LibreTranslate**: `TRANSLATION_PROVIDER=libretranslate`, `LIBRETRANSLATE_URL=https://your-host`, secret = its API key or `none`.
Add another provider by adding one function to `functions/lib/providers.js` and a `case` in `getProvider`.
The key lives only in Secret Manager; it is never in the APK, database, or logs.

### 3. Deploy rules + backend
```
npm i -g firebase-tools && firebase login
cp .firebaserc.example .firebaserc     # put your project id
firebase deploy --only database,functions
```
(Phone-only? Use Cloud Shell in the Firebase/Google Cloud console and upload this project folder.)

### 4. Build the app
Android Studio (Ladybug+) or Code on the Go: open the project, Gradle sync (Gradle 8.14.3 wrapper, AGP 8.10.1, Kotlin 2.1.21, compileSdk 35, minSdk 26), then **Build -> Build APK(s)**. Debug APK: `app/build/outputs/apk/debug/`. For a release APK create a keystore and add a `signingConfig` (never commit it).
Without `google-services.json` the project still compiles and the app shows a setup screen.

### 5. Notifications
Android 13+ asks for `POST_NOTIFICATIONS` when the chat list first opens. Pushes are sent by the backend only; tapping one opens the right conversation. Users can turn notifications/previews off in Settings (lock screen shows no content: `visibility: private`). Retries cannot duplicate pushes (server-side `processedAt` claim + per-chat notification tag).

## Data model (Realtime Database)
`users`, `usernames`, `chats/{chatId}/messages|metadata`, `userChats/{uid}/{chatId}` (maintained by Functions), `messageReceipts`, `typing`, `presence`, `userBlocks`, `userSettings`, `deviceTokens`, `reports`, `translationRequests`, plus server-only `rateLimits`, `translationCache`.
`chatId = sorted(uidA, uidB).join("_")`; the rules derive membership and the recipient from this id.
Message sending is idempotent (client-generated push key) and works offline (RTDB persistence + Room + WorkManager retry with exponential backoff).

## Security summary
- Rules (`database.rules.json`): least privilege, participant checks from `chatId`, sender-only writes, `email_verified` required to send, server-only translation fields, validated shapes/lengths, receipts only by the recipient, no listing of `/users` without a bounded username query.
- Backend: derives everything from stored data, never from client claims; per-user rate limit (40 translations/min); provider timeouts + retries; logs contain ids and error codes, never message text.
- **Encryption:** in transit TLS; at rest Google's server-side encryption. **Not end-to-end encrypted** (the server must read text to translate it).
- Account deletion: re-auth, then Auth user deletion triggers `onUserDeleted`, which removes profile, username, presence, tokens, blocks and all of that user's conversations (for both people).
- Reports are write-only for users; read them in the Firebase console.

## Images and profile pictures (v2) — no Firebase Storage
Everything is compressed on the phone and stored as base64 JPEG inside Realtime Database:
- **Chat images:** max 1024 px, JPEG quality <= 75, shrunk until <= ~230 KB. The message holds only a ~1 KB thumbnail (`mediaThumb`); the full image lives in `chatMedia/{chatId}/{messageId}`. The sender sees it instantly (cached in Room), the recipient sees the thumbnail, then the full photo. Each image is downloaded once and cached. Caption is optional (and is translated like text). Tap a photo to view it full-screen. Deleting the message deletes the image node.
- **Profile pictures:** square 256 px, <= ~45 KB, in `userPhotos/{uid}`; `users/{uid}/photoVer` changes on every upload so other phones refresh their cache.
- Rules enforce: only the sender of an image message can write its media, only the two participants can read it, size limits (420 000 / 90 000 characters), and nothing else is accepted.
- Cost note: RTDB bills storage and download. A 200 KB photo is cheap, but heavy use adds up; for large scale move media to an external object store.
- All users: the Chats tab lists your conversations, then "People on NH Connect" (all other registered users, loaded in pages of 30); the People tab lists everyone alphabetically and supports `@username` search.

## Known limits / choices
- Login is by **email** (reference design says "email or username"; username lookup would need a server endpoint that reveals emails). "Continue with Google" is not included.
- Tabs: Chats / People / Settings (no Calls tab; calling is out of scope). Only images are supported as media (no video, audio or files).
- Delivered receipts: written when the app is running (chat list watcher) or the chat is open. Not while the app is fully closed.
- Foreground pushes are not shown (live UI updates instead).
- App strings are English only; translation is for chat content.

## Tests
- `cd functions && npm test` (text protection, providers, retry): 9 tests, passing.
- `./gradlew test` : `MessageDisplayTest`, `DomainTest` (display logic, merge/idempotency, ids, backoff, validators). Logic was also compiled and run standalone with kotlinc.
- `cd rules-tests && npm install && npm test`: security tests against the emulator (needs Firebase CLI + Java). Written but not run in the build sandbox.
- Manual acceptance: `docs/ACCEPTANCE_CHECKLIST.md`.

## Gradle wrapper note
`gradle/wrapper/gradle-wrapper.properties` pins Gradle 8.14.3. The wrapper JAR/scripts are not bundled; Android Studio and Code on the Go use that version automatically. From a terminal run `gradle wrapper` once if you want `./gradlew`.

## UI design (v1.1 premium redesign)
Aurora-gradient headers with rounded corners, floating pill navigation, card-based chat list with gradient unread badges, gradient message bubbles on a subtle doodle wallpaper, language pill in the chat header (e.g. EN ⇄ BN), translation chips inside bubbles, floating composer, grouped settings cards with icon tiles, animated splash with pulsing rings, and a visual onboarding with live translation previews. Light and dark themes share the same palette tokens in `ui/theme/Theme.kt`.

## File sharing, phone to phone (v3)
Pick the paperclip in a chat to share any file (max 200 MB). Only a small "file card" message is stored in the database; the bytes never touch a server:
1. The receiver taps **Download**. The app writes a tiny request to the sender's `p2pInbox`.
2. The sender's app (it starts listening as soon as it is open and signed in) answers, and both phones exchange WebRTC offer/answer/ICE through `p2p/{chatId}/{session}` (signaling only).
3. The file streams directly in 16 KB chunks over an encrypted (DTLS) WebRTC data channel and is saved to **Downloads/NH Connect** (Android 10+; on older Android it goes to the app's own Downloads folder). Progress is shown on both phones.
Safety: size limit, executable types (.apk, .exe, .sh ...) ask for confirmation, file names are sanitized, the receiver checks the exact byte count, and the database rules only let the real sender be asked.
**Honest limits**
- Both people must be online at the same time, and the **sender must have the app open** (Android stops background apps). If the sender is offline, the receiver sees "sender is offline, try again".
- Most networks connect directly through free Google STUN servers. Some mobile carriers (symmetric NAT) cannot; for those add a **TURN** relay: put `TURN_URL`, `TURN_USER`, `TURN_PASS` in `local.properties` (e.g. a Metered/Twilio/self-hosted coturn account). Without it those pairs fail with "Could not connect".
- TURN credentials in `local.properties` end up inside the APK; use time-limited/low-privilege credentials.
- Downloads are not resumable; a failed transfer is retried from the start.
