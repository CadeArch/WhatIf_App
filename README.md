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
- Target SDK: 33

## Build And Test

```powershell
.\gradlew.bat :MixedUp:assembleDebug
.\gradlew.bat :API:testDebugUnitTest
```

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
- [x] Add an explicit round/session id to round data so old listener events can be ignored if they arrive late.
- [x] Add JVM tests for local replay round-state cleanup.
- [x] Detect host removal from the room player list and show a clear host-left message on active game screens.
- [ ] Add a host-migration or graceful host-left flow for lobby, writing, reading, voting, and replay screens.
- [x] Add a visible reconnect/retry state when Firebase writes fail during submit, pass, vote, play again, or room cleanup.
- [x] Remove legacy per-player `hostStarted` start flag; game start now uses room `currentRoundId`.
- [ ] Add Firebase emulator or fake-repository tests for replay loops, late joins, player leaves, and stale room data.
- [x] Add a small shared action-button loading helper so submit/pass/vote/replay buttons use one busy-state pattern.
- [x] Add a lightweight connection status banner when Firebase reports offline/disconnected state.
- [x] Gate lobby start navigation on a fresh round id so stale replay data cannot flash clients into the next screen when the host sends everyone Home.
- [x] Reserve unique rooms atomically and retry rare room-code collisions before showing a code to the host.
- [x] Clean up unstarted Firebase rooms when the host backs out of the Create Game screen.
- [ ] Add manual QA steps for toggling airplane mode during submit, pass, vote, play again, and Home.
- [x] Add a beta smoke-test checklist for 2, 3, and 5 players across fresh game, replay, home, leave, and app force-close flows.
- [ ] ensure gameroom is cleaned up after host ends rounds in the ending frag

### Game Flow

- [x] Revisit sentence pairing so the "If" and "Then" assignment feels more random.
- [x] Store a randomized Firebase assignment map when the host starts each round so every device uses the same hidden If/Then pairing.
- [x] Let the active reader pass the read-aloud turn to the next player when they are done.
- [x] Hide each read result until that phone becomes the active reader, then let the player reveal it when the group is ready.
- [x] Store host-first read order and active reader key in Firebase so phones cannot race into simultaneous read turns.
- [x] Keep the reading phase open until every player has read, then let only the host finish the phase for all devices.
- [x] Auto-finish the reading phase from Firebase after the final reader passes, without requiring the host to tap Done.
- [x] Add a short delay or timer for the last submitter so screen transitions are less abrupt.
- [ ] Add clearer player-facing messages for waiting on host, waiting on readers, replay disabled, and missing players.
- [x] Players can join between replay rounds, and the lobby copy/state enforces that clearly.
- [x] Room DB clean up when HOST ends gameroom decides NOT to play again.

### Accessibility

- [x] Choose a room-code font that makes mixed-case letters and numbers easier to distinguish.
- [x] Hook up Enter/Done on the keyboard to press the affirmative button on room join, If, and Then entry screens.

### Architecture And Maintainability

- [ ] Look for duplicate code that can be abstracted into shared helpers or `Utils` methods for reuse across fragments.
- [ ] Reorganize `src/main/java` into human-readable packages/folders by feature, such as account, lobby, writing, reading, voting, leaderboard, messaging, and devtools.
- [ ] Group each fragment with its helper classes/adapters where practical so it is clear which files support each game screen.
- [ ] Find long, complex functions and break them into smaller named methods for clarity and easier unit testing.
- [ ] Move pure game decisions out of fragments and into testable helpers or ViewModel/repository methods.
- [ ] Add focused unit tests for extracted logic, especially replay cleanup, reader turn advancement, assignment selection, and validation.
- [ ] Standardize fragment setup patterns for binding views, observing ViewModels, handling submit clicks, and cleanup in `onDestroyView`.
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

- Improve portrait mode support.
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
