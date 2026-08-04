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
