# Changelog

Running record for the current branch/session, plus an archive of past sessions. See `README.md`
for stack/build/roadmap; this file is the "what changed and why" log.

## Session Summary Rules

- Put implementation details here when a task is finished, not only in the roadmap.
- Keep roadmap items in `README.md` focused on status; keep this file focused on what changed.
- When a branch/session is finished, use these bullets plus checked roadmap items to generate the
  commit message and PR description.
- When Cade says he is ready to commit, archive the current bullets under
  `Archived Session: <branch-name>` and start a fresh `Current Session Changes` section for the
  next branch.
- Do not delete archived session notes unless Cade explicitly asks for cleanup.

## Current Session Changes

- Deleted 4 unrenamed template test files (`ExampleUnitTest`/`ExampleInstrumentedTest` in both
  modules, one still in package `com.mynewpackage.api` — clearly never renamed from the Android
  Studio template).
- Simplified `ReadSentenceFrag.mutateString(...)` — it was a one-line pass-through to
  `GameLogic.mutateVoiceText(...)`; inlined the call and removed the wrapper.
- Added CLAUDE.md Part 2: Java engineering guidance (readability, reliability, scalability,
  encapsulation, reusability), grounded in a real audit of this codebase (fat `ReadSentenceFrag`/
  `MainActivity`, `User.java`'s public-fields-plus-redundant-getters, 3 independently duplicated
  `ChildEventListener` shapes, `RoomViewModel`'s fake-repository test seam vs. `UserViewModel`'s
  lack of one, the flat `phoneapp` package). Each rule cites the file/pattern, not generic advice.
- Set up the Firebase Emulator Suite for local dev/testing (`firebase.json`, `.firebaserc` under a
  `demo-` project id so no `firebase login` is required, `database.rules.json` intentionally
  wide-open for local-only use). Added an opt-in `-PuseFirebaseEmulator=true` Gradle flag →
  `BuildConfig.USE_FIREBASE_EMULATOR` → new `WhatIfApplication` → `FirebaseEmulatorConfig`
  (API module) that points `FirebaseDatabase`/`FirebaseAuth` at the local emulator before any
  other Firebase call. Defaults off; ordinary builds are unaffected. Motivation: manual testing
  this session (and likely before) was writing directly to the live production `mixedupgame`
  database — this makes not doing that the easy path instead of the only path.
- Bumped `androidx.test.ext:junit` (1.1.2→1.2.1) and `androidx.test.espresso:espresso-core`
  (3.3.0→3.6.1) in both modules — the 2020-era versions' bundled instrumentation helper-activity
  manifests fail to merge once targeting API 36 (missing `android:exported`, required since API 31).
- Added `MixedUp/src/androidTest/.../NavigationFlowTest.java`: 6 real Espresso tests covering the
  solo-reachable flows (launch, Free Play → Start navigation, empty-name validation on both
  Create/Join, name-entry display swap, and an Activity-recreate/rotation regression test). Compiles
  and is structurally correct but could not be executed in this session's sandbox — both configured
  AVDs run a preview API level (37) ahead of what the installed Espresso release supports,
  failing instrumented test startup with `NoSuchMethodException: InputManager.getInstance`
  (Espresso/OS-preview compatibility gap, not a bug in the tests). Needs a stable API 34-36
  emulator or a standard CI runner to get a real pass/fail signal.

### Two-device multiplayer harness — design notes (not yet built)

The actual hard-to-test edge cases (host disconnect mid-round, replay loop with 2+ real clients,
late joins, stale room cleanup) all require two Firebase clients interacting live — something
none of the tests above cover. Two tiers, in order of what to build first:

**Tier A — two simulated players in one JVM test process (build this first).** Firebase's Android
SDK needs a real or Robolectric-simulated `Context` to initialize (`FirebaseApp.initializeApp`),
but does **not** need a real device/emulator UI — the actual multiplayer logic lives in
`RoomViewModel`/`UserViewModel`, plain classes with no view dependencies. Add Robolectric
(`testImplementation 'org.robolectric:robolectric:...'`) to the `API` module, then in a single
fast JVM test: construct two independent `RoomViewModel`+`UserViewModel` pairs (simulating "host"
and "guest"), point both at the Firebase Emulator Suite set up this session
(`FirebaseEmulatorConfig.configureIfEnabled(true)` before either is constructed), and drive real
scenarios deterministically — host creates a room, guest joins, host goes silent (assert the
guest's client-side heartbeat/grace-timer logic fires correctly), guest leaves mid-round, a second
guest joins between replay rounds, etc. This is real multi-client Firebase behavior under test,
runs in seconds, needs no emulator/device, and directly covers the "late joins, player leaves,
stale room data" gap flagged in this file's roadmap — just not the actual UI layer.

**Tier B — true two-device end-to-end (do later, real infra work).** Two real
emulators/devices, each running the full app UI via Espresso/UiAutomator, both pointed at the
Firebase Emulator Suite, orchestrated externally: start both emulators, install the app with
`-PuseFirebaseEmulator=true` on both, run scripted UI interactions against each via
`adb -s <serial>` in parallel (e.g. two `connectedDebugAndroidTest` invocations against different
device serials), synchronizing between them on the room code the host's test process generates
(write it somewhere the guest's test process can read — a temp file, a fixed deterministic seed,
or a small coordination signal). This is the only tier that would have caught this branch's actual
UI bugs (the portrait header overlap, the background seam) since it exercises real layouts, but
the multi-device orchestration and timing coordination is real infrastructure work — worth doing,
but only after Tier A is in place and proves out the Firebase-emulator-based approach.

### Continued this session: Tier A actually built, Espresso verified on a stable emulator, code-quality fixes

- **Deduplicated all 5 `ChildEventListener` instances** (`RoomViewModel.loadRooms`,
  `UserViewModel`'s player listener, `LeaderBoardViewModel`'s 3 listeners) onto a new
  `ChildEventListenerAdapter` (API module) — a standard no-op-default adapter (mirrors
  `MouseAdapter`-style Java conventions) so each listener overrides only the callbacks it actually
  uses instead of repeating empty method bodies. No behavior change (verified: all ~75 existing
  JVM tests still pass) — pure duplication removal, applying CLAUDE.md §7 (reusability) to the
  exact 3 sites it names.
- **Got a real pass/fail signal on the Espresso tests**, not just "compiles": installed Android
  `cmdline-tools`/`sdkmanager`, a stable API 35 system image, and a new `Test_API35` AVD (the dev
  machine's existing AVDs both run a preview API level Espresso doesn't support yet). Found and
  fixed one genuine test bug in the process — `enteringNameShowsDisplayNameInsteadOfEditText`
  wrongly assumed Free Play mode shows the read-only `displayName` view; it only does in account
  mode. Renamed to `freePlayKeepsNameEditableAfterTyping` and asserts the correct behavior instead.
  All 6 tests pass on the stable emulator.
- **Built Tier A** (`MixedUp/src/test/.../MultiplayerEmulatorTest.java`): two simulated players
  (independent `RoomViewModel`+`UserViewModel` pairs on separate named `FirebaseApp` instances)
  against the local Firebase Emulator Suite, covering the "late joins" and "player leaves" gaps
  from the roadmap. Real, working, passing test — not just designed. Getting there required
  tracking down three separate environment issues, worth recording since they'll recur for anyone
  extending this harness:
  1. **Firebase component discovery**: `FirebaseDatabase.getInstance(app)` threw
     `NullPointerException: Firebase Database component is not present` when the test lived in the
     `:API` library module. Library-module manifest merging doesn't pull in dependencies'
     `ComponentDiscoveryService` metadata the way an application module's does — moved the test to
     `:MixedUp` and added `testOptions.unitTests.includeAndroidResources = true` (Robolectric needs
     the merged manifest for this).
  2. **Robolectric doesn't support this project's compileSdk/targetSdk (36) yet** — pinned
     `@Config(sdk = 34)`.
  3. **The real one**: calling `FirebaseDatabase.getInstance(app).getReference()` a *second* time
     for the same `FirebaseApp` produces a `DatabaseReference` whose writes never deliver their
     completion callback in this Robolectric/JVM environment (confirmed via several isolated
     throwaway repro tests, down to raw `DatabaseReference` calls with zero ViewModel/Auth
     involvement — not a serialization, transaction, nested-path, or FirebaseAuth issue; purely
     about calling `.getReference()` twice). Not reproducible on a real device — a
     Robolectric-environment quirk, not a Firebase SDK bug. Fix: obtain the reference **once per
     app** and share it between `RoomViewModel`'s repository and `UserViewModel`'s constructor,
     matching how the real app already does it (both go through the same default `FirebaseApp`).
     Added a small additive `RoomViewModel`/`FirebaseGameRepository` constructor overload and a
     `RoomViewModel.pushRoom(id, onSuccess)` overload (matching the existing optional-`Runnable`
     convention throughout the class) to support this from test code.
- **Cleaned up `User.java`'s encapsulation**: it had 16 public fields *and* redundant getters/setters
  for a subset of them. Grepped every one of those accessors across the entire codebase (including
  test directories, which I missed on the first pass and had to fix `UserTest.java` for) — only
  `getUid()` and 5 setters were ever actually called; deleted the other ~18 as genuinely dead API
  surface. `LeaderBoardItem.java`/`Unlockable.java` already show the correct pattern (package-private
  fields + real, used getters/setters) — documented as the template for new models in CLAUDE.md §6.
- Updated `CLAUDE.md` Part 2 §6/§7 to reflect both fixes above (no longer "here's the problem," now
  "here's what changed and the pattern to follow going forward").

### Continued this session: automated test plan for the 2x-replay and host-leave bugs

- **Extracted `HostDisconnectScheduler`** (new `API` class) out of `MainActivity`'s inlined
  grace-timer/heartbeat-expiry `Handler.postDelayed` logic (`scheduleHostDisconnect`,
  `scheduleHostHeartbeatExpiration`, `expireHostDisconnectedRoom`) — pure "when should the room be
  considered expired given a disconnect/heartbeat timestamp" decision-making behind an injectable
  `DelayedRunner` interface, so the ~20-24s timing math is unit-testable with a fake clock instead
  of needing real wall-clock waits. `MainActivity` keeps every side effect (Firebase writes,
  navigation, the connection banner) unchanged; behavior-preserving, including one existing quirk
  (the heartbeat path's second delay stage can no longer be cancelled once the first fires) kept
  intentionally rather than "fixed" as part of the extraction. `HostDisconnectSchedulerTest`: 8
  pure-JVM tests, no emulator needed.
- **Found and fixed the real cause of the reported "2 players play, then Play Again twice in a
  row causes crashes / guest stuck on the wrong screen" bug**: `UserViewModel.handleRemovedHost`
  fired `hostDisconnectedMessage` — which `MainActivity` reacts to by *instantly* sending the
  player home, with no grace period — on **any** removal of the host's player-list node. That
  includes the legitimate `nurfAllUsers()` wipe done every time a room resets for "Play Again."
  `EndFrag.onViewCreated` is what detaches a client's own players-list listener, so a guest who
  hasn't yet reached `EndFrag` when the host (on a separate, possibly faster device) taps "Again"
  still has that listener live, and gets falsely told the host disconnected mid-reset. The two
  legitimate host-departure paths (`replayState="no"` explicit leave, and the
  `hostConnection`/heartbeat `HostDisconnectScheduler` flow) were already correct and
  grace-period-protected; this was a third, unguarded, instant-firing signal layered on top of
  them. Fix: `handleRemovedHost` no longer sets `hostDisconnectedMessage` at all — real departures
  are still fully covered by the other two paths. Caught by
  `ReplayLoopEmulatorTest.hostReplayResetDoesNotFalselyDisconnectAGuestWhoseListenerIsStillAttached`,
  written first and confirmed failing on the pre-fix code for this exact reason before the fix was
  applied.
- Added `MixedUp/src/test/.../HostLeaveEmulatorTest.java` (2 tests): explicit host leave
  (`replayState="no"` + room delete, observed via `listenToReplayState`) and host connection drop
  (simulated `hostConnection` write → guest's `UserViewModel` observes it → `markRoomExpired`
  tombstone → room delete) — traced and confirmed as two genuinely different paths with different
  messages/destinations, not to be conflated.
- Added `MixedUp/src/test/.../ReplayLoopEmulatorTest.java` (2 tests): a `playOneReplayCycle()`
  helper mirroring `EndFrag.java`'s real host/guest "Again" call sequence exactly, run twice in a
  row (the reported symptom's literal trigger), plus the false-disconnect race test above.
- Confirmed via direct code reading (not assumption) that `UserViewModel.removeCurrentPlayerFromRoom`
  has no empty-room cleanup when the last non-host player leaves — a real gap Cade suspected might
  already be handled and was surprised to find wasn't; fix + test still pending (next up).
- Documented a Robolectric-only gotcha in `CLAUDE.md` §8 after repeating it twice this session:
  calling `FirebaseDatabase.getInstance(app).getReference()` more than once for the same
  `FirebaseApp` silently breaks that second reference's write completion callbacks in this
  JVM/Robolectric environment specifically (not reproducible on a real device) — always obtain one
  reference per app and share it.
- **Fixed the confirmed empty-room-cleanup gap**: `UserViewModel.removeCurrentPlayerFromRoom`
  (only ever called for a non-host player voluntarily leaving — the host has its own explicit
  `deleteRoom` path in `EndFrag`/`CreateGameFrag`/`MainActivity`) now deletes the room if that was
  the last remaining player, via a new `deleteRoomIfPlayersEmpty` helper. Added
  `RoomCleanupEmulatorTest.roomIsDeletedAfterTheLastNonHostPlayerLeaves` — first version of this
  test was a false positive (it passed even with the fix reverted, because it never wrote a real
  `Room` object, so `rooms/{id}` only ever had a `players` child and Firebase RTDB's own
  empty-node auto-pruning deleted it regardless of any app logic); fixed by creating the room via
  `RoomViewModel.pushRoom` first (giving it `roomID`/`gameInProgress` fields that don't
  auto-prune), then re-confirmed the test fails on the reverted code and passes with the fix.
- Added `RoomCleanupEmulatorTest.duplicateDisplayNamesJoinAndAreTrackedAsDistinctPlayers`,
  documenting confirmed-intentional behavior: two players with the same display name get distinct
  random `userID`s from `pushPerson` and are tracked as separate room entries — not a bug.

### Continued this session: broad Tier A coverage across the remaining game-flow inventory

- **`LeaderBoardEmulatorTest`** (4 tests): full voting round trip (push items → cast votes →
  `findBestSentence` auto-fires → winner reaches the leaderboard with correct `percentLoved`), a
  1-1 tie's deterministic resolution, a player who never votes correctly blocking auto-advance,
  and full-leaderboard (20-item) replacement only evicting the weakest entry. Found and fixed a
  real bug in the process: `LeaderBoardViewModel`'s `leaderBoard` listener only ever handled
  `onChildAdded` — after a replacement, Firebase correctly ended up with 20 entries but the local
  in-memory list (what any bound UI would actually show) silently grew to 21, since removals were
  never reflected locally. Added the missing `onChildRemoved` handling; confirmed the new test
  fails without it and passes with it. Also found the leaderboard node is a global (not
  room-scoped) top-level path shared across every test/room, and the local emulator's data
  persists across separate test runs within a session — the first version of this test file
  intermittently failed from cross-test pollution until each test explicitly wipes
  `leaderBoard` in `@Before`.
- **`RoomJoinValidationEmulatorTest`** (5 tests): all four `RoomJoinState` outcomes
  (`DOES_NOT_EXIST` for both an empty id and a never-created room, `AVAILABLE`, `IN_PROGRESS`) plus
  `ERROR` via a fake `GameRepository` pointed at a path the rules intentionally deny. Getting the
  `ERROR` case to actually reproduce required tightening `database.rules.json` itself: it was
  previously wide-open (`.read`/`.write`: `true` at the root), and Firebase RTDB security rules
  cascade as "OR" — a `true` grant at an ancestor can never be revoked by a more specific `false`
  further down, so a naive "lock one subpath" attempt silently no-opped. Restructured the rules to
  default-deny at the root with explicit allow-lists for the app's real top-level paths
  (`rooms`, `leaderBoard`, `AccountPlayers`, `expiredRooms`, `phoneAppVerified`,
  `watchAppVerified`) — functionally identical for the real app (which only ever touches those
  paths) but now able to produce a genuine permission-denied/cancelled-listener error for testing,
  and incidentally better local-dev hygiene than a fully open database.
- **`ReadingTurnEmulatorTest`** (4 tests): `setActiveReaderIndex`'s Firebase round trip resolving
  `activeReaderKey` from `readOrder` by index, `completeReadingAfterFinalPass` marking the round
  complete, and — not previously exercised through the real listener, only the pure
  `GameLogic.isCurrentRound` comparison in isolation — both `listenToActiveReader` and
  `listenToReadingComplete` correctly ignoring a late-arriving update stamped with a stale
  (previous) round id instead of corrupting the current round's state.
- **`RoundAssignmentEmulatorTest`** (2 tests): `createRoundAssignments`' full Firebase write/
  read-back round trip — no player ever assigned their own If/Then, `buildHostFirstReadOrder`
  (previously zero coverage, pure or otherwise) actually putting the host first in the written
  `readOrder`, each assignment's `readOrderIndex` matching its player's real position — plus the
  fewer-than-2-players guard leaving the room completely untouched in Firebase.
- **`RoomExpiryCleanupEmulatorTest`** (2 tests): a reconnecting host clearing its own
  `expiredRooms` tombstone via `deleteExpiredRoomMarker`, and `cleanupOldExpiredRoomMarkers`
  deleting only markers older than its cutoff while leaving recent ones alone — previously only
  the pure TTL constant (`GameFlowPolicy.EXPIRED_ROOM_TOMBSTONE_TTL_MS`) had coverage, not the
  actual conditional-delete-on-read logic.
- **`CollectingPhaseEmulatorTest`** (3 tests): `GameFlowPolicy.allPlayersFinishedIfs`'s
  connected-gate against a real Firebase-loaded player list — a player who finished but is now
  marked `connected=false` still blocks auto-advance — and a late-joining player being picked up
  by an already-attached players listener and correctly reopening the gate as blocked until they
  finish too.
- **`AccountPlayBranchingEmulatorTest`** (2 tests): confirms `GameFlowPolicy.allPlayersHaveAccounts`
  and `EndFrag.onViewCreated`'s independent local loop agree for real, Firebase-round-tripped
  player lists (both all-account and mixed free-play/account rooms). Documented, not fixed, a
  genuine edge-case divergence found while writing this: for an *empty* player list the two are
  NOT equivalent — `GameFlowPolicy.allPlayersHaveAccounts` returns `false`, while `EndFrag`'s
  `counter == total` loop is vacuously `true` (0 == 0). Left alone because `EndFrag` is only ever
  reached with players already in the room (solo play isn't allowed), so the case is unreachable
  in practice, and reworking `EndFrag`'s inline loop into a shared helper is out of scope for a
  test-coverage pass.
- Final full regression: 11 emulator test classes / 30 tests total, all genuinely executed (none
  skipped, confirmed via each class's JUnit XML report) and green, plus `API:testDebugUnitTest`,
  `MixedUp:testDebugUnitTest`, `MixedUp:assembleDebug`, `MixedUp:compileDebugAndroidTestJavaWithJavac`.

## Archived Session: feature/portrait-landscape-ui-refresh

- Fixed blurry/oversized paper-texture backgrounds: `white_papers.png` (1197x376) and
  `lined_paper_read.png` (1180x554) are low-resolution, landscape-shaped source images. The
  `centerCrop` fix from earlier in this branch stopped the non-uniform stretch distortion, but
  still had to scale these up ~6x to cover a tall portrait screen, which looked blurry/oversized.
  Replaced with tiled `<bitmap>` drawables (`white_papers_tiled.xml`, `lined_paper_read_tiled.xml`,
  `android:tileMode="repeat"`) that paint the texture at native resolution instead of scaling a
  single copy — sharp in both orientations, at the cost of a faint (and much less objectionable)
  repeat seam. Reverted the 12 fragment layouts from the `ImageView` + `centerCrop` pattern back to
  plain `android:background`, since tiling drawables don't need the ImageView workaround. Deleted
  the now-unused `read_sentence_paper.xml` drawable.

- Bumped `targetSdkVersion` 35 → 36 (`MixedUp/build.gradle`, `API/build.gradle`) to meet Google
  Play's Aug 31, 2026 target API level requirement (`compileSdk` was already 36).
- Migrated `MainActivity`'s global back-button block from an `onBackPressed()` override to
  `OnBackPressedCallback`, since Android 16 (API 36) no longer calls `onBackPressed()` /
  dispatches `KEYCODE_BACK` (predictive back). Added `android:enableOnBackInvokedCallback="true"`
  to the manifest. Behavior is unchanged: back navigation is still fully blocked everywhere.
- Bumped the transitively-resolved `androidx.core` from 1.3.1 to an explicit 1.13.1 — needed for
  `WindowInsetsCompat.Type`, required by the new mandatory edge-to-edge inset handling below.
- Added `CLAUDE.md` at the repo root: a technical steering doc for ConstraintLayout craft, resource
  organization, accessibility, and orientation-support rules for this codebase.
- Removed `MainActivity`'s `android:screenOrientation="sensorLandscape"` lock — the app now
  follows the device sensor/user rotation setting instead of forcing landscape only.
- Added `ViewCompat.setOnApplyWindowInsetsListener` on `activity_main.xml`'s root view (mandatory
  at API 36 — edge-to-edge can no longer be opted out of) so content and the connection banner
  never draw under the status/nav bars.
- Added `Utils.computeSpanCount()` and wired it into `CollectingQuestionsFrag`,
  `CollectingAnswersFrag`, `WaitingForHostFrag` — RecyclerView grid columns now scale with
  available width instead of a hardcoded `2`, tuned for landscape.
- Added a lightweight design system (`dimens.xml` 8dp spacing scale + button/type-scale styles in
  `themes.xml`: `Widget.WhatIf.Button.Primary`/`.Secondary`, `TextAppearance.WhatIf.*`) so every
  screen has one clear primary action and a consistent type scale instead of 26 one-off outlined
  buttons and 12 near-duplicate text sizes.
- Applied the design system + responsive (portrait+landscape) fixes across all 21 `fragment_*.xml`
  layouts: replaced landscape-tuned fixed margins with bias/guideline-based positioning, added
  height max/percent alongside existing width max/percent so content doesn't stretch, gave every
  screen exactly one filled primary button vs. outlined secondary buttons, applied the type scale,
  fixed the `textSize="16dp"` bug (should be `sp`) in `fragment_read_sentence.xml` and
  `message_banner.xml`, and added `contentDescription` to icon-only elements touched along the way.
- Rebuilt `fragment_waiting_for_host.xml`'s header: the room code, title, and host-only "start"
  button previously shared one horizontal chain sized for landscape width, which overlapped and
  clipped off-screen in portrait. "start" is now a fixed-size element pinned to the top-right
  corner, independent of the title's width.
- Fixed `fragment_vote.xml`: the vote button used to float beside the list with a fixed `140dp`
  side gutter reserved on every screen size; moved it to a bottom-anchored primary button (the
  established pattern on other screens) so the list gets full width in portrait.
- Fixed the decorative background: `activity_main.xml`'s `lined_papers` `ImageView` used
  `scaleType="fitCenter"` (the default) plus manual `scaleX/scaleY/translationX` tuned only for
  landscape, which left large blank white bands top/bottom in portrait — switched to
  `centerCrop`, which always fills the view regardless of aspect ratio.
- Fixed a background seam introduced by the inset padding: padding was originally applied to
  `fragment_container`, which pushed its child (each fragment's own paper-texture background) in
  from the edges, exposing the *different* activity-level background in the status/nav-bar strip.
  Moved the inset padding to apply to whichever fragment's root view is currently active instead
  (via `FragmentManager.FragmentLifecycleCallbacks` + the cached latest insets) — a View's own
  background is never clipped by its own padding, so each screen's texture now paints edge-to-edge
  with no seam, while its real content still avoids the system bars.
- Fixed stretched/distorted paper-texture backgrounds on every screen: `android:background` set
  directly to a raw bitmap (`white_papers`, and `read_sentence_paper.xml`'s `gravity="fill"`)
  stretches non-uniformly to exactly fill view bounds with no aspect-ratio preservation, which
  looked like vertical streaking on tall portrait screens. Replaced with an `ImageView` +
  `centerCrop` (same fix pattern as the earlier `activity_main.xml` background) on all 12 affected
  fragment layouts, scaling uniformly and cropping overflow instead of distorting.
- Verified in the `Pixel_9` emulator (not just compiled): landing, start, create-game,
  join-game, and leaderboards screens checked in both portrait and landscape via `adb screencap`,
  including the fixed background texture.
- Moved "Branch Session Notes" out of `README.md` into a dedicated `CHANGELOG.md` (this file).

## Archived Session: feature/AI-round-5

- Added robust connection-state tracking so temporary network drops mark players disconnected instead of immediately removing them from the room.
- Added a host disconnect grace timer before clients are sent home, with a countdown banner on the host device.
- Moved host disconnect detection to a room-level `hostConnection` signal so it works even when screen-specific player listeners are not active.
- Added a room-level host heartbeat timestamp so non-host clients can expire the room about 20 seconds after the host app is killed, without waiting for Firebase `onDisconnect()` latency.
- Added an activity-level presence pulse so host heartbeat starts reliably after a user becomes host, even if Android/Firebase connection state did not change at that exact moment.
- Tightened host heartbeat/presence updates from 5 seconds to 1 second so host-disconnect expiration aligns more closely with the visible grace countdown.
- Tuned the client send-home delay after host heartbeat expiration to 4 seconds so non-host phones visually leave closer to the host countdown reaching 0 on real devices.
- Disabled autofill/password-manager suggestions on the join-game room-code field.
- Cleaned up stale rooms when the host remains disconnected past the grace window.
- Added an `expiredRooms` tombstone so a host that reconnects after clients have already left can detect the disrupted room and return home instead of recreating partial room data.
- Added expired-room tombstone cleanup: reconnecting hosts remove their room marker, and app startup sweeps markers older than 24 hours.
- Gated host-side disrupted-room navigation behind the host phone's own expired connection countdown, and clear local room identity after disruption so returned clients stop re-firing stale room messages.
- Send disrupted/corrupted-room recovery to the landing screen instead of Start so freeplay/account state is rebuilt cleanly.
- Added shared UI message observer helpers and used them on writing screens.
- Refactored Start screen account/freeplay UI mode into a named helper with safer default XML visibility.
- Reused shared fragment navigation helpers in writing screens.
- Added focused tests around host heartbeat and client send-home timing constants.
- Tightened host disconnect expiration so a late host reconnect after the grace deadline cannot resume the expired game.
- Blocked automatic If/Then phase advancement while any player is marked disconnected.
- Added tests for connection-stability game-flow rules.
- Updated roadmap items for connection resilience and future disconnected-player host controls.
