# Web App Port Plan (for iPhone/cross-platform play)

**Status: not started.** This is a forward-looking plan written before any web work has begun, so
it can be handed to a fresh agent (in a new, separate repo) once Cade is ready to start. It is
deliberately general in places — framework choice and exact file structure are left as open
decisions to be pinned down when this actually kicks off, not guessed at now.

## Why this exists

The current app (`WhatIf_App`, this repo) is a native Android app (Java, `ConstraintLayout`
Fragments, Firebase Realtime Database) with no iOS build. Apple's $99/year Developer Program is
the only way to distribute a *native* iOS app (App Store, or even free/limited sideloading routes
don't scale to "share with friends casually"). A web app avoids that entirely: Firebase Hosting's
free tier serves a real `https://` URL with zero Apple gatekeeping, works in iPhone Safari with no
install step, and — since this app's actual game state already lives in Firebase Realtime
Database, not in anything Android-specific — a web client can talk to the *same* Firebase project
and the *same* data shape as the existing Android app. Done well, Android and iPhone players could
even join the same room together.

## What this app is, for a cold read

A real-time multiplayer party game. A host creates a room (Firebase RTDB node under `rooms/{id}`),
players join with a display name, everyone privately writes an "If" and "Then" sentence, those get
randomly reassigned to other players to read aloud in front of the group (mismatched on purpose,
for comedy), the group votes on the funniest combination, results feed a global leaderboard, and
the group can immediately replay with fresh assignments. Core game flow, in order: lobby/join →
collecting (write If/Then) → reading (turn-based, host-first order) → voting → leaderboard → replay
or end. See this repo's `README.md` (gameplay rules) and `CLAUDE.md` (engineering conventions) for
full detail if this plan is being read without the rest of the repo alongside it.

## What carries over almost unchanged — the actual advantage here

- **The entire Firebase Realtime Database schema.** `rooms/{roomId}` (`gameInProgress`, `players/`,
  `currentRoundId`, `roundAssignments/`, `readOrder`, `activeReaderIndex`/`activeReaderKey`,
  `readingComplete`, `replayState`, `hostConnection`), plus top-level `leaderBoard/`,
  `AccountPlayers/`, `expiredRooms/`, `errorLogs/`. A web client reads/writes the *same* nodes with
  the *same* shape via the Firebase JS SDK — no schema redesign needed. `database.rules.json` in
  this repo already governs both clients identically, no changes needed there either.
- **The game-flow "policy" logic** (`API/src/main/java/com/CadeMixedUpGame/api/GameFlowPolicy.java`,
  `GameLogic.java`, `RoomCreationPolicy.java`): pure, side-effect-free decision functions — grace
  timing, sentence formatting, room-code generation, randomized (no-self) assignment, vote tallying,
  reading-order construction, stale-round-id guards. These need to be *re-implemented* in
  TypeScript/JavaScript (Java doesn't run in a browser), but the algorithms themselves are already
  fully worked out, small, and battle-tested — porting them is a mechanical translation exercise,
  not a redesign. Their existing JUnit test suites (`API/src/test/...`) are a ready-made checklist
  of behavior/edge cases the web port's own tests should replicate.
- **The hard-won bug fixes and their reasoning**, even though the *code* doesn't port directly:
  - The false "host disconnected" race during replay (a guest's still-attached player-list listener
    misreading `nurfAllUsers()`'s routine wipe as a real departure) — `CHANGELOG.md`'s
    "stable-testing-and-refactors" session has the full trace.
  - Two intentionally-different host-departure paths (explicit `replayState="no"` leave vs.
    `hostConnection`/heartbeat-based disconnect detection with a grace period) — don't collapse
    these into one signal in the web port; that distinction is deliberate.
  - Empty-room cleanup on last-player-leave, expired-room tombstones, the leaderboard-replacement
    local-list bug — all documented in `CHANGELOG.md`, worth re-reading before re-implementing the
    equivalent logic so the same mistakes aren't repeated.
- **Firebase Hosting is part of the same free ("Spark") plan already in use for the database** — no
  new billing setup, no new Firebase project *required* (though a second project is an option if
  full isolation from the Android app's production data is ever wanted).

## What needs to be rebuilt (the actual scope of this project)

- **The entire UI layer.** All 21 `MixedUp/src/main/res/layout/*.xml` screens and their
  `Fragment`/`Activity` Java classes become HTML/CSS/JS(TS) — this is the bulk of the work.
- **Navigation.** `Utils.navigateToFragment`-style fragment transactions → client-side view
  switching/routing. This app doesn't need deep-linking or browser back/forward semantics (a party
  game played in one sitting), so simple state-driven view swapping is likely sufficient — doesn't
  need a heavyweight router.
- **Text-to-speech** (the "mutate and read aloud" feature, `ReadSentenceFrag`'s TTS + voice-mutation
  in `GameLogic.mutateVoiceText`): Android's `TextToSpeech` → the Web Speech API
  (`SpeechSynthesisUtterance`). **Flag this early and test it first** — Web Speech API support in
  iOS Safari specifically has historically been inconsistent/limited compared to Chrome, and this
  is a core, load-bearing gameplay feature, not a nice-to-have. Worth a throwaway spike before
  committing to the full port.
- **Sound effects** (`SoundHelper`'s `AudioTrack`-based chime): Web Audio API or a plain `<audio>`
  element — straightforward.
- **Push notifications** (Firebase Cloud Messaging, used for host-ended-game notifications per
  `CHANGELOG.md`): Web Push via FCM for web needs a service worker + VAPID key setup, and **iOS
  Safari's web push support is recent and narrower than Android's** (requires iOS 16.4+, and in
  some iOS versions only works once the site is added to the home screen as a PWA, not from a
  regular browser tab). Treat as a stretch goal, not assumed-working, given the whole point of this
  port is iPhone support.
- **Firebase Auth** (used for account-play mode, `gamesPlayed`/leaderboard-contribution tracking):
  the Firebase Auth JS SDK is fully supported on web and should port cleanly — lowest-risk item on
  this list.
- **Local persistence** (if any `SharedPreferences` usage carries meaning worth keeping, e.g. a
  remembered display name): `localStorage` on web.

## Framework choice — left open, notes for whoever decides

Cade hasn't committed to plain HTML/CSS/TS vs. a component framework (React, Svelte, Vue) yet.
Notes for that decision when it's made:

- **Plain HTML/CSS/TS**: fewer moving parts, nothing new to learn if unfamiliar with a framework,
  fastest to get a trivial first screen live. Likely to get unwieldy fast for *this* app
  specifically, though — 21 screens, each driven by live Firebase listener state (mirroring the
  existing `ValueEventListener`/`ChildEventListener` + Android `LiveData` pattern throughout
  `RoomViewModel`/`UserViewModel`), is exactly the kind of "UI re-renders from changing state"
  problem frameworks solve for free and hand-rolled DOM updates solve badly.
- **React (or similar)**: components + hooks map naturally onto the existing ViewModel/LiveData
  shape (a Firebase `onValue` listener updating a `useState` ≈ the Android `ValueEventListener`
  updating `LiveData` the Fragments already observe) — porting the *shape* of the existing
  ViewModels' state management is more direct than with vanilla JS. Also the most likely to have
  strong tooling/AI-assistant familiarity if a fresh agent picks this up cold in a new repo, which
  matters given that's the explicit intent here.
- Recommendation if forced to pick today: React (or Svelte if less boilerplate is preferred) — but
  this is Cade's call once the port actually starts, not a hard commitment from this plan.

## Firebase free tier specifics

- **Hosting (Spark/free plan)**: free `https://<project-id>.web.app` (and `.firebaseapp.com`)
  subdomain, automatic SSL, global CDN, generous free quota (10 GB stored, 360 MB/day transferred)
  — plenty for a hobby party game's traffic. No custom domain needed (see prior discussion in this
  repo's session history if resumed with context — free custom TLDs are unreliable; a real one is
  cheap, ~$10-15/year, but optional).
- **Realtime Database (Spark/free plan)**: already in use for the Android app; the same 1 GB
  stored / 10 GB downloaded per month quota applies to *both* clients combined once a web client
  exists — worth keeping an eye on for a busy game night, unlikely to be an issue for casual
  friend-group play.
- **Deploy mechanics**: Firebase CLI (`firebase deploy --only hosting`) — the same CLI tool already
  used this session for the local Emulator Suite, so the tooling is already familiar.
- Whether the web app lives in the *same* Firebase project as the Android app (simplest — one
  `database.rules.json`, one `errorLogs`/`leaderBoard`, true cross-platform play) or a *separate*
  project (full isolation, but no shared rooms/leaderboard between platforms) is a real decision to
  make explicitly before starting, not default into. Same-project is recommended given the stated
  goal is playing *with* iPhone friends, which implies shared rooms.

## Suggested phased approach

1. **Prove the hosting loop first, before any real feature work.** Deploy a bare "Hello World" to
   Firebase Hosting from the new repo and confirm the free `.web.app` URL + HTTPS actually works
   end to end. Cheap to validate early, expensive to discover broken late.
2. **Port the pure game-logic functions to TypeScript first**, with a unit test suite mirroring
   `API/src/test/java/com/CadeMixedUpGame/api/{GameLogicTest,GameFlowPolicyTest}.java`'s existing
   coverage — this is self-contained, low-risk, and doesn't require any UI to exist yet.
3. **Spike the Web Speech API on actual iOS Safari early** (see the TTS flag above) — this is the
   one feature most likely to force an architecture rethink if it doesn't work the way expected, so
   validate it before building 20 screens around an assumption.
4. **Wire up the Firebase JS SDK against the same project/schema**, build the smallest possible
   real thing first: create a room, join it from a second browser tab, see the player list update
   live. This mirrors how this repo's own Tier A test harness
   (`MixedUp/src/test/.../MultiplayerEmulatorTest.java` and friends) validated the *real* Firebase
   behavior incrementally rather than building everything before testing anything.
5. **Build out the remaining screens in the game's real order** (collecting → reading → voting →
   leaderboard → replay), reusing the fully-documented flow from this repo's `README.md`/
   `CLAUDE.md`/`CHANGELOG.md` rather than re-deriving it.
6. **Test actual cross-platform play as a first-class scenario early**, not a final afterthought —
   one Android device and one web (iPhone Safari) client in the same room, since that's the entire
   point of this project.
7. **Consider a PWA manifest + service worker** for "Add to Home Screen" installability once the
   core is working — nicer iPhone UX (full-screen, home-screen icon), and a prerequisite for web
   push to work at all on iOS in some versions. Not needed for a functional v1.

## Verification for whoever picks this up

- Unit tests for the ported pure logic (mirroring the existing Java test suites) before trusting
  any of it.
- Manual cross-platform play test (Android app + web client, same room) as the actual acceptance
  criterion for "this project succeeded," not just "the web app works standalone."
- Confirm the Web Speech API and (if pursued) Web Push actually work on real iOS Safari, not just
  desktop Chrome during development — both are the specific reason this project exists.
