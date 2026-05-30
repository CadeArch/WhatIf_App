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
- Target SDK: 30

## Build And Test

```powershell
.\gradlew.bat :MixedUp:assembleDebug
.\gradlew.bat :API:testDebugUnitTest
```

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
- Players write an "If" sentence, then receive their assigned player's "If" and write a "Then" response.
- Guest/free-play games skip voting.
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

### Game Flow

- [x] Revisit sentence pairing so the "If" and "Then" assignment feels more random.
- [x] Store a randomized Firebase assignment map when the host starts each round so every device uses the same hidden If/Then pairing.
- [x] Let the active reader pass the read-aloud turn to the next player when they are done.
- [x] Add a short delay or timer for the last submitter so screen transitions are less abrupt.

### Sentence Formatting

- Standardize sentence cleanup:
  - trim whitespace,
  - strip unwanted punctuation,
  - capitalize the first letter of "If" prompts,
  - add periods where needed for display/read-aloud text.
- Consider pre-filling "If" and "Then" labels in the prompt screens so users only type the custom part.

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
