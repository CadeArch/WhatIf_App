# MixedUp

MixedUp is an Android party game built with Java, Fragments, and Firebase Realtime Database. Players join a room, write "If" prompts and "Then" responses, then the app mixes the pieces into unexpected sentences. Account-only games can continue into voting and leaderboard submission.

## Project Links

- Firebase database: https://console.firebase.google.com/u/0/project/mixedupgame/database/mixedupgame-default-rtdb/data
- Adobe XD walkthrough: https://xd.adobe.com/view/d865483a-67ff-4a2c-bced-c4d787b48586-8d36/screen/7c679a4d-125c-429d-bfe9-65bd4ffe7b58/

## Current Stack

- Android app module: `MixedUp`
- Shared API/model/viewmodel module: `API`
- Language: Java
- UI: Android Fragments and XML layouts
- Backend: Firebase Auth, Firebase Realtime Database, Firebase Messaging
- Build: Android Gradle Plugin 9.2.1, Gradle 9.4.1, JDK 17+
- Compile SDK: 36
- Minimum SDK: 25
- Target SDK: 36

## Build And Test

```powershell
.\gradlew.bat :MixedUp:assembleDebug
.\gradlew.bat :API:testDebugUnitTest
```

### Local Firebase Emulator (avoid touching production data)

By default, debug builds talk to the **live production** Firebase project (`mixedupgame`) — the
same one linked above. That's fine for a quick manual check, but don't leave it running for
scripted/repeated test flows; it pollutes real room/leaderboard data. For that, run against the
local Firebase Emulator Suite instead:

```powershell
# One-time setup: requires Node.js and the Firebase CLI (`npm install -g firebase-tools`).
# The emulator suite needs a JDK 21+ on PATH — Android Studio's bundled JBR works:
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio3\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

firebase emulators:start --only database,auth
```

This starts local Database + Auth emulators (`firebase.json`) under a `demo-` project id
(`.firebaserc`), which requires no `firebase login` and never touches the real project. Emulator
UI: http://127.0.0.1:4000/. `database.rules.json` is intentionally wide-open (`.read`/`.write:
true`) for local testing — it is **not** the production security rules and must never be deployed.

To point a debug build at it instead of production:

```powershell
.\gradlew.bat :MixedUp:assembleDebug -PuseFirebaseEmulator=true
```

This flag flows through `BuildConfig.USE_FIREBASE_EMULATOR` → `WhatIfApplication.onCreate()` →
`FirebaseEmulatorConfig.configureIfEnabled(...)` (API module). Defaults to `false`, so ordinary
builds are unaffected.

## Developer Notes

### Release Signing

The local release keystore is stored outside the repo:

```text
C:\Users\Cade Rasmussen\Documents\GoogleApps_releases\what_if
```

Known non-secret signing details:

- Keystore file: `what_if.jks`
- Key alias: `what_if_release_1.0`
- Upload key SHA-256: `AA:D0:0C:61:10:34:11:E3:90:61:E3:5C:F9:25:5A:06:62:FA:F9:FE:2E:99:2E:C7:F6:6B:2C:69:BB:86:CD:2D`

Do not commit keystores or signing passwords. Keystore file types are ignored in `.gitignore`; keep the actual password in Android Studio/password manager only.

### Android Developer Verification Asset

`MixedUp/src/main/assets/adi-registration.properties` was added only for Google Play Android developer package ownership verification. After Google confirms the package is registered and future releases no longer need the token, remove this asset so the app does not carry a stale verification-only file.

## Beta Smoke Test

Run this checklist before sharing a build:

- 2 players: create room, join room, start, submit If, submit Then, pass both read turns, confirm the game advances automatically, play again, finish a second round, then go Home.
- 3 players: repeat the core flow and confirm each phone gets exactly one hidden read turn before the game advances automatically.
- 5 players: repeat the core flow and confirm room code entry, player list updates, pass order, and End screen stay responsive.
- Late join: try joining before Create, after Create, after Start, and between replay rounds.
- Leave paths: guest leaves from End, host goes Home from End, host chooses Play Again, guest chooses Play Again.
- Failure paths: force-close one guest during lobby, writing, reading, and replay; confirm the player list or host-facing state recovers clearly.
- Regression pass: bad room code, empty name, empty If, empty Then, duplicate taps on Start/Join/Submit/Pass/Done.

Useful Logcat filters:

- `MU.GameFlow`
- `MU.Room`
- `MU.Firebase`
- `MU.Vote`
- `MU.UI`
- `MU.TTS`
- `MU.Push`
- Broad app logs: `MU.`

## Current Gameplay Rules

- The host creates a room and shares the room code.
- Guests can join while the room is still in the lobby.
- The room is locked once the match starts.
- When the host starts the match, Firebase locks the room and stores a hidden randomized assignment map for that round.
- Players finish a "What if" prompt, then receive their assigned player's prompt and finish a "then" response.
- Free players get the full core game loop: create, join, write, read, pass reading turns, replay, and leave.
- Account-only features are voting, microphone/Text-To-Speech, unlockables, profile progress, and leaderboard submission.
- Account-only games go to voting and can submit winning sentences to the leaderboard.
- The host controls whether the room goes home or plays again.


## Completed Highlights

- Firebase-backed room creation and joining.
- Player list listener for rooms.
- If/Then submission flow.
- Collection screens showing submitted players.
- Mixed sentence reading screen.
- Account-only voting flow.
- Leaderboard submission and replacement logic.
- Unlockable voice model and partial voice mutation support.
- Player profiles and games-played tracking.
- Push notification setup.
- Hybrid user messaging with inline errors, Snackbars, and persistent banners.
- Structured Logcat logging through `MU.*` tags.
- Real-device plus emulator multiplayer smoke test.

## Helpful References

- Firebase Auth Android docs: https://firebase.google.com/docs/auth/android/password-auth
- Firebase Auth error reference: https://firebase.google.com/docs/reference/js/v8/firebase.auth.Auth#signinwithemailandpassword
- Firebase Cloud Messaging: https://firebase.google.com/docs/cloud-messaging/android/first-message
- RecyclerView basics: https://stackoverflow.com/questions/40584424/simple-android-recyclerview-example
- GridLayoutManager example: https://www.journaldev.com/13792/android-gridlayoutmanager-example
- Spinner example: https://stackoverflow.com/questions/13377361/how-to-create-a-drop-down-list
- ScrollView bottom cutoff fix: https://stackoverflow.com/questions/38663428/android-scrollview-gets-cut-off-at-the-bottom
- Disable night mode: https://stackoverflow.com/questions/57175226/how-to-disable-night-mode-in-my-application-even-if-night-mode-is-enable-in-andr
- Google Play publishing overview: https://www.goodbarber.com/blog/how-to-publish-your-app-on-google-play-and-the-app-store-a107/


## Roadmap

### Reliability And Firebase

- [x] Verify room lockdown behavior when the host starts a match.
- [x] Replace broad room-list loading with a direct room-code lookup when joining.
- [x] Use clearer user recovery for failed Firebase reads/writes instead of automatic retry for now.
- [x] Add stronger success/failure handling for important Firebase operations.
- [x] Keep tracking and removing Firebase/list callbacks explicitly when leaving rooms or ending matches.
- [x] Confirm the host cannot go home or play again until all required votes are cast.
- [x] Store plain Firebase model objects instead of `MutableLiveData` wrappers so room/player/account data has a clean database shape.
- [x] Store play-again decisions on the room instead of the host player node so replay survives player-list cleanup.
- [x] Remove a non-host player's room node when they leave from the end screen.
- [x] Add Firebase `onDisconnect()` cleanup for player room nodes when players force-close the app, lose connection, or leave without tapping Home.
- [x] Add player connection-state tracking so brief network drops do not instantly remove players from a room.
- [x] Add a host disconnect grace timer before clients are sent home after the host loses connection.
- [x] Block automatic phase advancement while any player is marked disconnected, so the group waits for stable phones before moving on.
- [x] Add an explicit round/session id to round data so old listener events can be ignored if they arrive late.
- [x] Add JVM tests for local replay round-state cleanup.
- [x] Detect host removal from the room player list and show a clear host-left message on active game screens.
- [x] Add a visible reconnect/retry state when Firebase writes fail during submit, pass, vote, play again, or room cleanup.
- [x] Remove legacy per-player `hostStarted` start flag; game start now uses room `currentRoundId`.
- [x] Add a small shared action-button loading helper so submit/pass/vote/replay buttons use one busy-state pattern.
- [x] Add a lightweight connection status banner when Firebase reports offline/disconnected state.
- [x] Gate lobby start navigation on a fresh round id so stale replay data cannot flash clients into the next screen when the host sends everyone Home.
- [x] Reserve unique rooms atomically and retry rare room-code collisions before showing a code to the host.
- [x] Clean up unstarted Firebase rooms when the host backs out of the Create Game screen.
- [x] Add a beta smoke-test checklist for 2, 3, and 5 players across fresh game, replay, home, leave, and app force-close flows.
- [x] Ensure gameroom is cleaned up after the host ends from `EndFrag`.
- [x] Add a graceful host-left flow for lobby, writing, reading, voting, and replay screens. Host migration is intentionally deferred; non-host players are sent back to Start with a clear message when the host disconnects.
- [ ] Add Firebase emulator or fake-repository tests for replay loops, late joins, player leaves, and stale room data.
- [ ] Add manual QA steps for toggling airplane mode during submit, pass, vote, play again, and Home.
- [ ] Add host controls for removing a player who stays disconnected too long but is not the host.

### Publishing And Policy

- [x] Target Android 15 / API 35 for Google Play policy compliance.
- [x] Target Android 16 / API 36 ahead of the Aug 31, 2026 Google Play requirement; migrated the global back-button block from `onBackPressed()` to `OnBackPressedCallback` since Android 16 no longer dispatches `onBackPressed()`/`KEYCODE_BACK` (predictive back).
- [x] Add an in-app account deletion option from the Profile screen.
- [x] Add a public account deletion support page for Google Play Data safety review.
- [ ] Update the Google Play Data safety form with the account deletion declaration before resubmitting.
- [ ] Enable GitHub Pages for `/docs` or confirm the GitHub-hosted account deletion page URL is accepted by Play Console.
- [ ] Move the public account deletion/support page to a free Canva site so the code repository can be private again.
- [ ] Review whether Firebase Analytics and Firebase Messaging are both needed long-term; removing unused SDKs can reduce future Data safety disclosures.
- [ ] Remove or document the Android developer verification asset after Google package ownership verification is complete.

### Game Flow

- [x] Revisit sentence pairing so the "If" and "Then" assignment feels more random.
- [x] Store a randomized Firebase assignment map when the host starts each round so every device uses the same hidden If/Then pairing.
- [x] Let the active reader pass the read-aloud turn to the next player when they are done.
- [x] Hide each read result until that phone becomes the active reader, then let the player reveal it when the group is ready.
- [x] Store host-first read order and active reader key in Firebase so phones cannot race into simultaneous read turns.
- [x] Keep the reading phase open until every player has read, then let only the host finish the phase for all devices.
- [x] Auto-finish the reading phase from Firebase after the final reader passes, without requiring the host to tap Done.
- [x] Add a short delay or timer for the last submitter so screen transitions are less abrupt.
- [x] Add clearer player-facing messages for waiting on host, waiting on readers, replay disabled, and missing players.
- [x] Players can join between replay rounds, and the lobby copy/state enforces that clearly.
- [x] Room DB clean up when HOST ends gameroom decides NOT to play again.

### Accessibility

- [x] Choose a room-code font that makes mixed-case letters and numbers easier to distinguish.
- [x] Hook up Enter/Done on the keyboard to press the affirmative button on room join, If, and Then entry screens.

### Automated Testing

Manual reverification of multiplayer edge cases (disconnects, replay, stale rooms, late joins)
after every branch is expensive. Options considered, in order of increasing cost/coverage:

- [x] Logic-level tests: `GameFlowPolicy`/`GameLogic`/`RoomCreationPolicy` (API module) already
  have ~75 JUnit test methods — connection timing, assignment randomization, vote tallying, replay
  reset via the `FakeGameRepository` pattern in `RoomViewModelTest`. Removed the 4 unrenamed
  template `ExampleUnitTest`/`ExampleInstrumentedTest` files. See `CLAUDE.md` Part 2 for how to
  keep adding to this (put new game-flow logic in a pure static policy class, not a Fragment).
- [x] Single-player Espresso UI tests: `MixedUp/src/androidTest/.../NavigationFlowTest.java` —
  launch/navigation, empty-name validation, free-play name-entry behavior, and an
  Activity-recreate (rotation) regression test. Bumped `androidx.test.ext:junit`/`espresso-core`
  (were 2020-era 1.1.2/3.3.0, failed manifest merge once targeting API 36). Verified actually
  passing (not just compiling) on a stable API 35 AVD (`Test_API35`) via
  `.\gradlew.bat :MixedUp:connectedDebugAndroidTest` — the dev machine's other AVDs run a preview
  API level (37) ahead of what Espresso currently supports and fail instrumented test startup
  with `NoSuchMethodException: InputManager.getInstance`; use a stable API 34-36 emulator (or a
  standard CI runner) to run these, not a bleeding-edge preview AVD.
- [ ] Two-device multiplayer harness — two tiers, see `CHANGELOG.md` design/implementation notes:
  - [x] **Tier A**: `MixedUp/src/test/.../MultiplayerEmulatorTest.java` — two simulated players
    (separate `RoomViewModel`+`UserViewModel` pairs on separate `FirebaseApp` instances) against
    the local Firebase Emulator Suite, in one Robolectric JVM test process. Covers late joins and
    player leaves. Run it with the emulator suite already running (see above):
    `.\gradlew.bat :MixedUp:testDebugUnitTest --tests "com.CadeMixedUpGame.phoneapp.MultiplayerEmulatorTest"`.
    Skips (doesn't fail) if the emulator isn't running.
  - [ ] **Tier B** (later): true two-emulator end-to-end via Espresso/UiAutomator, the only tier
    that catches real UI bugs like this branch's portrait header overlap and background seam.

### Architecture And Maintainability

- [x] Look for duplicate code that can be abstracted into shared helpers or `Utils` methods for reuse across fragments.
- [ ] After the Play Console release is approved, audit and upgrade the local build toolchain intentionally: Android Gradle Plugin, Gradle wrapper, supported Gradle JVM, and Android Studio sync settings.
- [ ] Reorganize `src/main/java` into human-readable folders/sub-folders by features/screens of the app, such as account, lobby, writing, reading, voting, leaderboard, messaging, and devtools. This is worthwhile, but should be handled as a focused branch because it is higher-churn than small safety refactors.
- [ ] Group each fragment with its helper classes/adapters where practical so it is clear which files support each game screen.
- [x] Find long, complex functions and break them into smaller named methods for clarity and easier unit testing.
- [ ] Move pure game decisions out of fragments and into testable helpers or ViewModel/repository methods.
- [x] Add focused unit tests for extracted logic, especially replay cleanup, reader turn advancement, assignment selection, and validation.
- [ ] Standardize fragment setup patterns for binding views, observing ViewModels, handling submit clicks, and cleanup in `onDestroyView`. Started with shared UI message observer helpers.
- [ ] Review listener ownership so every Firebase listener has one obvious attach/detach location.

### Sentence Formatting

- [x] Standardize sentence cleanup:
  - trim whitespace,
  - strip unwanted punctuation,
  - capitalize the first letter of "If" prompts,
  - add periods where needed for display/read-aloud text.
- [x] Pre-fill "What if" and "then" labels in the prompt screens so users only type the custom part.

### Voting And Leaderboard

- Make the leaderboard cleaner and easier to scan.
- Format leaderboard entries as:
  - question first,
  - pause/ellipsis,
  - answer on the next line,
  - contributor shown near each sentence part in smaller readable text.
- Keep leaderboard scrolling acceptable, but prioritize readable typography.
- Improve vote selection so users cannot select an invalid number of sentences.

### UI And Accessibility

- [x] Lock gameplay to landscape orientation for now so unsupported portrait layouts are not shown during Play Store rollout.
- [x] Improve portrait mode support: unlocked orientation, reworked all 21 layouts to be responsive in both orientations, added a shared spacing/type/button-hierarchy design system, and added edge-to-edge inset handling (see `CLAUDE.md` and `CHANGELOG.md`).
- Add player-controlled sound settings for turn alerts and other game cues, including mute/vibrate options.
- Add a simple white/black background option.
- Add a mode or button to show the real original prompt instead of only randomized results.
- Ensure large-font accessibility works on prompt screens, buttons, and instructions.
- Let text fields and buttons expand cleanly for larger system font sizes.
- Make prompt instructions the same size as the phrase being entered, but use different colors.
- Keep all core game text easy to read.
- Consider a thicker custom underline for text entry fields.

### Unlockable Text-To-Speech Voices

- Finish and polish unlockable voice styles:
  - Disobedient Google,
  - Forgetful Google,
  - Shaggy Google,
  - Jokester Google,
  - different accents.
- Shaggy voice idea: add "like" in different places.
- Jokester voice idea: add "haha, jk" at the end, or other creative things

## Changelog

Session/branch change history (what changed and why, per session) now lives in `CHANGELOG.md`,
including the rules for maintaining it. Keep roadmap checkboxes above focused on status; put
implementation detail bullets in that file instead.
