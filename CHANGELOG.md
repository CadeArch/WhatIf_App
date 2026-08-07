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

## Archived Session: feature/vote-collection-and-room-cleanup

- **Tier B was running against PRODUCTION, not the local emulator — root-caused and fixed.** Cade
  spotted a test room (`sold-full`) in the live Firebase console and asked whether testing had been
  pointed at a local DB; that observation is what cracked it. `-PuseFirebaseEmulator=true` builds
  logged "Using local Firebase Emulator Suite" and then wrote every room to the live project.
  1. **`FirebaseDatabase.useEmulator()` does not stick.** It mutates the instance's `RepoInfo`,
     which is also the SDK's instance-cache key, stranding the cached entry — the next plain
     `FirebaseDatabase.getInstance()` misses the cache and builds a fresh instance from the app's
     unchanged production URL. Proven on-device with identity logging: immediately after the call
     `getInstance()` returned `http://10.0.2.2:9000`, and 0.7s later (same process, same
     FirebaseApp, same FirebaseOptions, different `identityHashCode`) `RoomViewModel` received one
     pointing at production. `FirebaseAuth.useEmulator()` *does* apply, so the app ran split-brain:
     **Auth on the emulator, Database on production**. Fatal for account play — the locally-minted
     fake token was rejected by production RTDB (`Provided authentication credentials are invalid`),
     dropping the connection before the host could create a room. Free play never signs in, so it
     never presented a token and appeared to work.
  2. **Fix**: debug builds now remove `FirebaseInitProvider` (`MixedUp/src/debug/AndroidManifest.xml`)
     and `WhatIfApplication.onCreate()` calls the new
     `FirebaseEmulatorConfig.initializeDefaultApp(...)`, which creates the default FirebaseApp with
     the emulator `databaseUrl` already in its `FirebaseOptions`. Nothing is mutated afterwards, so
     every `getInstance()` in the app resolves to the emulator naturally — **no app call sites
     changed and no emulator-awareness anywhere in production code**. An earlier attempt that
     routed `FirebaseGameRepository`/`Verify`/`LeaderBoardViewModel`/`UserViewModel` through a
     test-aware helper was reverted at Cade's push-back: it dragged test concerns into production
     classes and was unenforceable, since any newly-added `getInstance()` would silently regress.
     `FirebaseApp.delete()` + re-init was also tried and rejected — it crashes the process with
     `IllegalStateException: FirebaseApp was deleted` (Firebase Installations is already using the
     app on a background thread).
  3. Added the missing `firebase_url` to `google-services.json` (the RTDB instance postdated the
     downloaded file, so `databaseUrl` was null and the SDK was falling back to a derived
     production URL).
  4. Both Tier B scripts now **build the APKs themselves** with the flag and **hard-fail if the app
     APK is a production build**, so a routine `assembleDebug` (regression run, Android Studio's Run
     button) can't silently repoint the suite at production again. Verified end to end: rooms now
     appear in the local emulator and production stays untouched.

- **Fixed a real end-of-game hang for non-host players** (found because the local DB made it fast
  enough to hit almost every time). `EndFrag` sends a non-host home only when it observes
  `replayState == "no"`, but the host wrote that value and then deleted the room in the write's own
  completion callback — fast enough that the other clients never observed the intermediate value,
  only the room disappearing. The guest then sat on the end screen indefinitely with nothing left
  to react to. The host now waits `GameFlowPolicy.HOST_HOME_ROOM_DELETE_DELAY_MS` (1.5s) after
  writing the signal before deleting the room, with the pending deletion cancelled in
  `onDestroyView()`. Rarer in production than locally, but the same race exists there.

- **Tier B scripts now run visible by default, with `-Headless` to opt out** (previously headless by
  default with `-Visible`), per Cade's preference for watching runs. `run-tier-b-account-play.ps1`'s
  temporary `-Prod` switch was removed — it only existed to work around the split-brain above and is
  obsolete now that Auth and Database agree.

- **Fixed a last-round race in both Tier B tests.** The guest waited for `again_ending` to be
  displayed before finishing, but on the final round the host's Home click can send it home first —
  observed EndFrag at `:21.881` and StartFragment at `:22.152`, a 271ms window — so the wait could
  never match. The guest now waits only for the home screen, which is the thing actually worth
  asserting. `TwoDeviceAccountPlayGameLoopTest` also had a wrong final assertion (waited for the
  sign-in screen; account players stay signed in and land on `StartFragment`).

- **Both Tier B suites now pass against the local emulator**: `TwoDeviceFullGameLoopTest` (~44s) and
  `TwoDeviceAccountPlayGameLoopTest` (~49s, full 2 rounds including sign-in, voting and
  host-ends-game), both roles `OK`. Tier A regression still green: `API:testDebugUnitTest` (90
  tests), `MixedUp:testDebugUnitTest` (38 tests), 0 failures/0 skipped, plus `assembleDebug` and
  `compileDebugAndroidTestJavaWithJavac`.

- **Abandoned rooms are now actually deleted** — the first thing in the app that ever removes one.
  Cade asked why rooms never expire "when I thought I had expire logic". The `expiredRooms`
  tombstones are not, and never were, a room cleaner: they flag "this room is dead" so a
  reconnecting host stops writing to it (`runIfHostRoomActive`) and other clients know to go home,
  and their 24h prune only removed those flags. Every path that deletes an actual room
  (`EndFrag` Home, `CreateGameFrag` back, host-disconnect, `deleteRoomIfPlayersEmpty`) needs a live
  client to reach it, so a host process that simply dies — crash, force-stop, swipe-away, a killed
  instrumented test — leaked its room permanently. That is what filled production up.
  New `RoomViewModel.cleanupAbandonedRooms(now)` runs at app start next to the tombstone prune.
  Liveness comes from `hostConnection/lastSeenAt` (written every second by the host heartbeat)
  falling back to a new server-stamped `createdAt`, via pure `GameFlowPolicy.isRoomAbandoned(...)`
  with a 6h TTL, so a room anyone is still playing in can never be a candidate; rooms carrying
  neither timestamp pre-date `createdAt` and are swept as leftovers.

  **The first TTL was wrong and Cade caught it**: after a sweep he pointed out that none of the
  surviving rooms were actually active, so they should all have gone. They survived a six-hour TTL
  because it had been sized as though a live room might legitimately go hours between signs of life.
  It can't - the host writes `lastSeenAt` every second, and the app already ends the game after
  twenty seconds without it, so rooms sitting on 30-100 minute-old heartbeats were finished games
  that merely looked recent. Retuned to 30 minutes (~90x the app's own give-up threshold, so still
  far too long to catch anything live), and a room already carrying an `expiredRooms` tombstone is
  now deleted immediately regardless of age - the app has already declared it dead, which is the one
  genuinely useful cleanup job those tombstones can do. Re-verified against the real leftovers: 7 of
  10 rooms deleted, the 3 survivors all being 21-30 minutes old and due on the next sweep.
  7 new Tier A tests covering the policy.

- **The sweep is claimed once a day globally, not run by every client on every launch** — Cade
  flagged that a whole-table scan per launch is overkill and would be untenable at real throughput
  (it is O(users x rooms), with every client racing to delete the same rooms).
  `RoomViewModel.runDailyMaintenanceIfDue` now runs both housekeeping jobs behind a transaction on a
  shared `maintenance/lastSweepAt` value, so exactly one device per interval wins - whichever
  happens to launch first once it falls due - and every other launch does nothing. The transaction
  is what makes simultaneous launches safe; `GameFlowPolicy.isMaintenanceSweepDue` also treats a
  future-dated claim as due so one skewed clock can't block sweeping until that date arrives.
  Verified on real devices: first launch logged "Maintenance claim won", a second launch on the same
  device skipped, and a **different** device with freshly-cleared app data also skipped - confirming
  the claim is global rather than per-install. Also verified the sweep itself deletes an 11h-old
  room while leaving a fresh one and the day's real rooms alone. 2 new Tier A tests; needs the new
  `maintenance` node in `database.rules.json` deployed before it works against production.

- **`CollectingVotesFrag`: voting now has a collecting phase like If and Then do** (Cade's
  suggestion, and the right call). Submitting a vote used to drop that player straight onto
  `EndFrag` while the round was still half-finished, so `EndFrag` defended itself with
  `hasPendingRequiredVotes()` and silently refused Home/Play Again whenever a vote was outstanding —
  a screen whose primary buttons just don't respond, with a banner as the only clue and no
  indication of who was being waited on. Voting now waits on its own screen showing who has voted
  (`LeaderBoardViewModel` tracks `votedPlayerKeys` from the vote child keys) and moves everyone to
  `EndFrag` together once `GameFlowPolicy.allVotesCast(...)` is true. Both `EndFrag` guards and the
  predicate are deleted: the race is structurally impossible now rather than defended against at
  each consumer. New `GamePhase.COLLECTING_VOTES`.

- **Free play no longer shows account-only controls.** `StartFragment.applyUserMode` toggled
  Sign Out, Back and Profile but never Leader Boards, so the free-play start screen offered a
  leaderboard that is fed by the account-only vote round and keyed to accounts — one a guest can
  neither appear on nor contribute to.

- **Fixed two reading-turn control bugs in account play**, both reported by Cade seeing the mic
  "sneaking through" while waiting to read:
  1. The mic was only dimmed to `alpha 0.35` while another player read, never hidden, so it still
     read as an available control. It is now hidden unless you can actually speak your own revealed
     sentence, and the "show" button is hidden while it is someone else's turn (the paper text
     already says you are waiting).
  2. `revealSentence()` refreshed only the sentence text, not the reading controls — mic visibility
     is derived in `updateActiveReaderControls()` — so after tapping "show" the mic stayed hidden
     until some unrelated Firebase update happened to fire. Previously invisible because the mic was
     always on screen anyway.

- **Tier B now asserts the things that shouldn't be there**, per Cade's request: free play checks
  that Leader Boards / Profile / Sign Out are not displayed on the start screen (both roles) and
  that the mic never appears on any reading turn; account play checks the mic is hidden while
  waiting and visible once revealed — that assertion is what caught bug 2 above.

## Archived Session: feature/account-play-testing-and-observability

- **Accessibility contrast fixes + portrait design polish pass**, prompted by Cade flagging that
  the "0 background" (`Widget.WhatIf.Button.Secondary`) buttons were hard to see, and that portrait
  screens (especially `WriteIfFrag`/`WriteThenFrag`) looked clunky and didn't fill the screen well.
  1. **`Widget.WhatIf.Button.Secondary` was genuinely failing WCAG contrast**, not just visually
     weak: its `@color/orange` text/stroke on a transparent fill measured ~3.4:1 against white
     (below the 4.5:1 text bar — `colors.xml` already had a comment noting this from earlier
     accessibility work, but the Secondary button style itself was never updated to use the fixed
     `orange_deep` color, ~5.6:1). Gave Secondary a real near-opaque surface fill
     (`secondary_button_surface`, new `res/color/secondary_button_background.xml` /
     `_text.xml` / `_stroke.xml` state lists) plus `orange_deep` text/stroke, so every Secondary
     button across all 21 layouts (all wired through this one shared style, confirmed via grep - no
     ad-hoc per-layout button colors existed) is both contrast-compliant and visually distinct
     against the busy paper-texture backgrounds instead of disappearing into them.
  2. Added a genuine disabled-state look (`state_enabled="false"` entries in the new color state
     lists) so `EndFrag`'s "waiting"/"again!" replay button no longer needs manual
     `Color.GRAY`/`Color.parseColor(...)` overrides — removed that dead code (and the now-unused
     `android.graphics.Color` import) entirely; the button style's state list handles it.
  3. **Found two message-banner colors that also failed contrast**: `UiMessenger.makeBackground`'s
     WARNING banner used the exact same failing `rgb(196,121,29)` orange with white text (~3.4:1) -
     darkened to match `orange_deep` (~5.6:1). SUCCESS only cleared 4.5:1 by ~0.2 (a hair's margin);
     darkened for real headroom (~6.4:1).
  4. **`fragment_write_if.xml`/`fragment_write_then.xml` had a real layout bug, not just a "sparse"
     look**: the "What if"/"then" prefix label was clipped against the screen's left edge (a fragile
     horizontal chain mixing a `wrap_content` prefix with a `0dp`-width `EditText` and no start
     inset), the input row read as two disconnected floating text elements instead of one sentence,
     and worst, the Submit button was pinned near the very bottom of the screen - right where the
     on-screen keyboard covers it while the user is actively typing. Rewrote both screens: the
     prefix+input row is now a simple horizontal `LinearLayout` bounded to the same width band as
     the title (matches the title's alignment, fixes the edge-clipping), and Submit sits directly
     below the input row instead of pinned to the parent bottom, so it's never hidden behind the
     keyboard.
  5. **Found and fixed a second real bug while verifying the above on-device**: `WriteThenFrag`'s
     "what you're responding to" preview reused `custom_edit_text.xml` as its background - a
     drawable built for a fixed ~52dp single-line field, positioning its underline via a
     `android:bottom="-20dp"` inset trick that only lands below the text at that specific height.
     On the 90dp-tall, vertically-centered preview box, the same math put the line through the
     *middle* of the box - directly across the centered text, visually blacking out the letters
     (caught live: "there is a huge black line covering the what if... covering the letters").
     Replaced with a proper new `res/drawable/prompt_preview_card.xml` (rounded-rect card, subtle
     `orange_deep` stroke, matches the Secondary button's surface language) instead of misusing an
     underline drawable as a card background.
  6. **Portrait "dead space" pass**: `fragment_first.xml`'s logo was a fixed `120dp` regardless of
     screen height, leaving the packed content chain centered in a small band with huge blank paper
     texture top and bottom - gave it `layout_constraintHeight_percent`/`_max` (the vertical
     percent+max counterpart to width that `CLAUDE.md` §1 already flagged as missing repo-wide) so
     it scales with the screen. `fragment_start.xml` had no branding at all above the name field;
     added a smaller responsive logo there too, consistent with `fragment_first.xml`, and increased
     inter-element spacing. `fragment_create_game.xml`/`fragment_join_game.xml` had a fragile
     constraint graph (the room-code `TextView` was simultaneously `top_toTopOf`/`bottom_toBottomOf`
     parent while the buttons floated independently via bias, rather than a real top-to-bottom
     flow) - rewrote both with a title guideline + straightforward top-to-bottom chain, fixing both
     the ambiguous constraints and the top/bottom dead space in one pass.
  7. Verified every change live on a portrait `Test_API35` emulator (not just compiled): landing,
     start, create-game, join-game, write-if, write-then screens all screenshotted before/after via
     `adb screencap`, including a full real two-device join + If/Then submission flow to confirm the
     write-screen fixes render correctly with real Firebase data, not just placeholder text.
  8. Full regression confirmed green after all changes: `API:testDebugUnitTest`,
     `MixedUp:testDebugUnitTest`, `MixedUp:assembleDebug`,
     `MixedUp:compileDebugAndroidTestJavaWithJavac`.
  9. Not yet touched in this pass (opportunity for next time, not attempted here): `fragment_account.xml`,
     `fragment_end.xml`, `fragment_profile.xml`, and `fragment_leaderboard.xml`'s button footer
     still use the older independent-bias floating-button pattern and could use the same
     guideline+chain treatment applied to create/join-game here.

- **Account Play flow audit**, prompted by Cade testing sign-in with a real production account
  (email/username already existing in `AccountPlayers`) and asking where the password was stored.
  1. Confirmed passwords are never stored in the Realtime Database at all - `UserViewModel.signUp`/
     `signIn` (`API/.../UserViewModel.java`) use `FirebaseAuth.createUserWithEmailAndPassword`/
     `signInWithEmailAndPassword`; Firebase Auth stores credential hashes in Google's separate
     Identity Platform backend, never readable via RTDB console or any API. `AccountPlayers` only
     ever held profile/stats data. Not a bug - explained to Cade.
  2. **Found a real layout bug testing the signed-in state live**: `fragment_start.xml`'s
     `create_game`/`joinGame`/`displayName` all sized themselves off `enterName`'s edges
     (`start_toStartOf="@id/enterName"` etc.). Per ConstraintLayout's documented GONE behavior, a
     GONE view's *position* is preserved for anchoring but its *dimensions* collapse to zero -
     `enterName` goes `GONE` for signed-in account players (`StartFragment.applyUserMode`), which
     collapsed its width to 0, merging its start/end edges into one point and collapsing everything
     anchored to them (including `displayName` - the very view meant to replace it) to 0x0. Result:
     signed-in users saw the welcome name and both Create Game/Join Game buttons vanish entirely,
     silently, no crash - it wasn't visible in the guest/free-play path at all since `enterName`
     stays visible there (already verified working earlier this session). Fixed by giving all three
     their own independent `parent`-relative width_percent/max instead of sizing off a view that
     can go GONE. Confirmed fixed live: real sign-in now correctly shows "Kayde", Create Game, and
     Join Game together.
  3. **Found the password `EditText` in `fragment_account.xml` had no `inputType` at all** - typed
     passwords rendered as plain visible text instead of masked dots. Added
     `android:inputType="textPassword"`. Also added `textEmailAddress` to the email field for a
     proper keyboard while touching the file.
  4. **Found the password itself being written to `System.out.println` in `UserViewModel.signUp`**
     - already commented out (not a live leak), but dangerous dead code to leave lying around since
     it could be uncommented back in later. Removed it and four other stray commented-out debug
     `System.out.println` calls in `signIn`/`signUp` while in the area.
  5. **Found and diagnosed a `.info/connected` "Connection lost" banner appearing during testing**:
     root-caused via `adb logcat` to the emulator's virtual WiFi radio genuinely losing its beacon
     every ~8s (`wpa_supplicant: wlan0: CTRL-EVENT-BEACON-LOSS`) on this specific long-running AVD -
     confirmed with `ping` (worked, ICMP unaffected) vs. the app's own `.info/connected` state
     (stayed disconnected) vs. forcing a clean `svc wifi disable`/`enable` reassociation (banner
     cleared instantly). This is the app correctly reporting a real (if emulator-virtual-radio-only)
     connectivity problem, not an app bug - no code fix needed here, just a fresh emulator instance
     for continued testing. Confirms `MainActivity`'s connection-banner logic is working as intended.
  6. **Found a latent Robolectric-only crash** while re-running the full regression suite:
     `FirebaseEmulatorConfig.configureIfEnabled(boolean)` called `FirebaseApp.getInstance()`
     unguarded, unlike its neighbor `WhatIfApplication.tryGetDefaultDatabaseReference()`, which
     already carries a comment explaining exactly why that's unsafe under Robolectric (the default
     FirebaseApp isn't guaranteed initialized when `Application.onCreate()` runs during tests).
     Normally dormant since Tier A tests run without `-PuseFirebaseEmulator=true`, but reproduced
     it by mistakenly combining that flag with `testDebugUnitTest` in one Gradle invocation -
     crashed every single Robolectric test in the module with the same
     `IllegalStateException: Default FirebaseApp is not initialized`. Added the same try/catch
     guard as its neighbor for consistency and defense-in-depth, in case CI or a teammate ever
     combines those the same way.
  7. Full regression confirmed green after all fixes: `API:testDebugUnitTest`,
     `MixedUp:testDebugUnitTest` (run correctly, without the emulator flag),
     `MixedUp:assembleDebug` (both with and without `-PuseFirebaseEmulator=true`),
     `MixedUp:compileDebugAndroidTestJavaWithJavac`.

- **Auto error-log Firebase table** (README roadmap item, self-authored by Cade): non-fatal
  exceptions already logged anywhere in the app via the existing `AppLog.e(tag, message,
  throwable)` — all 60 pre-existing call sites, zero changes needed at any of them — now also
  land under a new `errorLogs/` Firebase node, not just Logcat. `AppLog` grew a bounded
  (capacity-40) breadcrumb ring buffer recording every `d`/`i`/`w`/`e` call plus a static
  `ErrorReporter` hook; a new `ErrorReportPayload` (pure, testable) builds the report shape
  (tag/message/exception class+message+trimmed stack trace/breadcrumbs/app version/fatal flag/
  timestamp); a new `FirebaseErrorReporter` writes it, capped at 25 reports per app session so one
  repeating bug can't flood the table. `MainActivity`'s existing fragment-lifecycle callback (added
  earlier this session for inset padding) now also logs a "Screen shown: X" breadcrumb on every
  transition, regardless of how it's triggered — 12 files call `beginTransaction()` directly, not
  just through `Utils.navigateToFragment`, so hooking the lifecycle callback instead of each call
  site is both less invasive and more complete.

  Fatal (uncaught) crashes get a more robust store-and-forward path per Cade's explicit choice: a
  new `PendingCrashReportStore` (MixedUp, needs `Context`/file I/O so it can't live in the
  Android-independent `API` module) writes the crash payload synchronously to a local file the
  instant it happens — fast/local, unlike an async Firebase write a dying process might not finish
  — then `WhatIfApplication.onCreate()` checks for and uploads any pending file on the *next*
  launch before deleting it.

  Found and fixed a real bug while wiring this up: Robolectric calls the manifest's actual
  `Application.onCreate()` for *every* test in the `MixedUp` module, including ones that never
  initialize a default `FirebaseApp` — an early version of `WhatIfApplication.onCreate()` called
  `FirebaseDatabase.getInstance()` (the default app) unconditionally, which broke essentially every
  existing Robolectric test in the module, not just the two new ones. Fixed by registering the
  crash handler unconditionally (it only needs local file I/O, not Firebase) and wrapping the
  default-app lookup in its own try/catch that just disables error-log reporting for that session
  if Firebase genuinely isn't ready yet, rather than taking the whole app down — more robust for
  real edge cases too, not just a test workaround.

  Also tightened `database.rules.json`'s `errorLogs` entry from write-only (the original, more
  privacy-conscious design) to read+write, matching every other allow-listed node — the write-only
  version blocked the new test's own read-back verification, and this data isn't more sensitive
  than the `rooms`/`leaderBoard` data that's already fully open in the same file.

  New tests: `AppLogTest`/`ErrorReportPayloadTest` (pure JVM), `ErrorLogEmulatorTest` (Tier A,
  proves a real `AppLog.e(...)` call delivers an entry end-to-end, plus the session cap),
  `PendingCrashReportStoreTest` (Robolectric, local file write/read/delete round trip). 128 tests
  total across both modules, 0 failures, 0 skipped (confirmed the emulator was live throughout, not
  silently `assumeTrue`-skipped).

- **Fixed the disconnected-banner flash on app launch** (Cade-reported): Firebase's `.info/connected`
  path briefly reports `false` on every fresh launch, before the initial WebSocket handshake to
  Firebase's servers completes — expected SDK behavior, not a real disconnect, but
  `updateConnectionBanner()` showed the red banner immediately on any `false` value with no debounce,
  so it flashed on essentially every launch. Now schedules the banner's *appearance* on a 1.2s delay
  (reusing the existing `connectionHandler`, already cleaned up in `onDestroy()`) and cancels that
  pending show if connectivity recovers first — hiding stays immediate, only showing is debounced,
  so a genuine disconnect still surfaces the banner, just ~1s later than before, while normal startup
  and other sub-second blips never flash it.

- Audited every `catch` block in production code (12 total) for the error-log table's coverage:
  `WhatIfApplication.registerCrashHandler()`'s `Thread.setDefaultUncaughtExceptionHandler` is
  already the process-wide top-level catch-all Cade asked about — broader than an Activity-scoped
  wrapper, since it catches uncaught exceptions on any thread, not just ones bubbling through
  `MainActivity`. Found one real gap: `SoundHelper`'s chime-playback failure logged via `AppLog.w`
  (message string only) instead of `AppLog.e` (with the real `Throwable`), so it reached Logcat but
  never `errorLogs/` — only the 3-arg `AppLog.e(tag, message, throwable)` overload triggers the
  reporter. Promoted it to `AppLog.e` per Cade's choice.

- **Built Tier B: real two-device Espresso end-to-end testing**, the "true two-emulator" tier
  README/CHANGELOG flagged as "real infra work, do later" back when Tier A was first built. Real,
  working, and proven to genuinely pass/fail correctly — not just designed:
  - Provisioned a second stable AVD (`Test_API35_B`, same android-35 image as the existing
    `Test_API35`) since the Android emulator can't run two instances of the same AVD data
    directory concurrently, and the other two AVDs (`Pixel_9`/`Pixel_9_3`) run a preview API level
    that already failed instrumented test startup earlier this session.
  - New `scripts/run-tier-b.ps1`: checks the local Firebase Emulator Suite is reachable, confirms
    both emulators are connected, installs the app (built with `-PuseFirebaseEmulator=true`) and
    test APK on both, launches the host role in the background, polls its logcat for a
    distinctively-tagged `ROOM_CODE=...` signal line, then launches the guest role with that real
    generated code injected as an instrumentation argument — the cross-process handoff mechanism
    needed since room codes are now randomly-generated word pairs with no way to force a fixed
    code through the real create-room UI.
  - New `TwoDeviceMultiplayerTest.java` (one shared class, branches on a `role` instrumentation
    argument rather than duplicating Espresso setup across a host-specific and guest-specific
    class): host creates a room via the real UI, guest joins via the real UI with the handed-off
    code, both independently confirm 2 players show up in the real player-list `RecyclerView`.
    Deliberately minimal scope for this first pass — proving the whole harness (two real
    emulators, a real Firebase round trip, real cross-process room-code coordination) works before
    building more scenarios (collecting → reading → voting → replay) on top of it.
  - New `EspressoWaitUtils.waitFor(...)`: Espresso has no built-in "wait for real async Firebase
    state" (its idling mechanism only waits for the main thread, not a listener callback that
    hasn't arrived yet) — a small sleep-poll retry helper, no new `espresso-contrib` dependency
    needed (a plain `RecyclerView` adapter item-count check via a custom `ViewAssertion` covers
    what that dependency would otherwise be used for).
  - **Ran it for real, twice**: once normally (both roles reported `OK`, room code `peel-fate`
    genuinely generated and handed across processes), once with a deliberately wrong room code
    fed to the guest role, confirming a real `NoMatchingViewException` timeout — same "confirm it
    can actually fail" discipline as this session's Tier A work, not just "green on the first try."
  - Documented the whole mechanism, plus the error-log-driven diagnosis/discovery workflow Cade
    asked for (check `errorLogs/` first when something fails; periodically review it to decide
    what to cover next), in `CLAUDE.md`'s testing section.

- **Ran down the "room disappeared" finding from Tier B's first run — not a product bug.** The room
  (`peel-fate`) genuinely looked missing when checked via `curl .../rooms/peel-fate.json?ns=demo-
  mixedupgame-default-rtdb`, but that's the fake project Tier A/Robolectric tests explicitly use;
  the real app (any actual APK build, Tier B included) uses `FirebaseApp.getInstance()` configured
  from `google-services.json`'s real `project_id` (`mixedupgame`, no `demo-` prefix) — a completely
  different namespace in the same local emulator. Confirmed via `adb logcat`: dozens of real
  successful writes to the room, followed by clean teardown (listeners removed, pending
  `onDisconnect()` writes *cancelled*, no delete call anywhere in that path — grepped every
  `deleteRoom`/`removeCurrentPlayerFromRoom` call site, none fire from Activity teardown), and via
  the emulator's own `database-debug.log` (`mixedupgame-default-rtdb successfully activated` at the
  exact run timestamp) and the response's own `X-Firebase-Project-Id` header. No code changes
  needed — corrected the namespace guidance in `CLAUDE.md`'s testing section instead, since getting
  this wrong silently returns `null` with no error, which reads exactly like missing data.

- **Built the full 2-round game-loop Tier B test** (`TwoDeviceFullGameLoopTest.java`) — the exact
  flow Cade said caught most of this session's real bugs (join → write If/Then → click through
  reading → end screen → Play Again → loop a second round), now exercised through two real devices
  end to end so it can't silently regress again. Traced every screen's real navigation/turn
  mechanics in full first (`ReadSentenceFrag`'s `next_frag` is a single dual-purpose "show"/"pass"
  button per turn, host reads first; `EndFrag`'s `again_ending` is host-enabled-by-default,
  guest-disabled-until-`replayState=="yes"`, matching this session's earlier
  `ReplayLoopEmulatorTest`'s ViewModel-level version now proven at the real UI layer). Reused
  `EspressoWaitUtils`/the same role-branching pattern as `TwoDeviceMultiplayerTest`; parameterized
  `scripts/run-tier-b.ps1`'s `-TestClass` so both tests share one orchestration script instead of
  duplicating the host/guest/logcat-handoff mechanics.

  **Found and fixed two real bugs getting this to actually pass — both in the test infrastructure
  itself, not the app:**
  1. A genuine cross-device race in the join step: the guest independently re-checked the player
     list showed 2 people before proceeding, but the host only needs to see 2 players on *its own*
     device before clicking "start" — and can win that race before the guest's identical check
     ever succeeds once. Once the host starts the game, `WaitingForHostFrag` (and its player-list
     `RecyclerView`) is torn down and replaced by `WriteIfFrag`, so a guest still polling for that
     now-gone view would wait out the full timeout and never write anything — confirmed exactly
     this via `adb logcat` on the guest device before fixing it (removed the redundant guest-side
     check; `playOneRound`'s own wait for `ifQuestion` already confirms the guest is in the right
     place, regardless of which device won the race).
  2. `EspressoWaitUtils.waitFor` only caught `RuntimeException` — but Espresso's own
     `ViewAssertions.matches(...)` throws `AssertionError`-derived failures when a view *exists*
     but a property doesn't match yet (`isEnabled()`, `withText(...)`), and only
     `NoMatchingViewException` (view absent entirely) extends `RuntimeException`. Waits for
     "displayed" retried correctly by accident; waits for "enabled" or "has this text" threw on the
     very first check and never actually polled. Masked initially because the host's button
     happened to already be enabled on its first check (host reads first) — the guest's
     genuinely-not-yet-its-turn button is what exposed it. Now catches `Throwable`.

  Both were caught by watching this exact test fail for real, diagnosed reasons (via `adb logcat`,
  not guessing) before it ever passed — the same discipline as the rest of this session's testing
  work, now proven at the real-device layer too.

  **Two more real fixes from watching a live 3-round run with Cade:**
  1. Round count is now overridable per run via an instrumentation argument
     (`-e rounds <n>`, `scripts/run-tier-b.ps1`'s `-Rounds` parameter) instead of a recompile-only
     constant — asked for specifically so this test can double as a quick manual "watch it play N
     rounds" tool, not just a fixed CI-style check.
  2. Running live with 3 rounds surfaced a third real bug (again, in the test, not the app): round
     2+'s `waitingForHost_start` click didn't re-wait for the player list to show 2 players before
     clicking. Every "Play Again" wipes all players (`nurfAllUsers()`) and each device re-pushes
     itself, so there's a real window where the host's own local player list has only caught back
     up to 1 player when the button reappears — clicking that early hit the app's own **correct**
     guard ("Wait for at least one more player") and silently no-opped, freezing the test on
     round 2's start screen exactly as Cade watched happen live. Round 1 already had this wait;
     round 2+ needed it too. Fixed by re-checking player count before every round's start click,
     not just the first.
  3. Per Cade's request, the *last* round now ends with the host clicking **Home** instead of
     Again, instead of only ever testing the replay loop — exercises the other real end-of-game
     path (`replayState="no"` + `deleteRoom`) at the real UI layer. The guest deliberately clicks
     nothing for this: `EndFrag`'s own `replayState=="no"` observer auto-navigates a non-host
     player home the instant the host ends the game, matching real gameplay — confirmed via
     logcat on both devices (`Room deleted room=...` on the host, `EndFrag received host home
     signal` on the guest, both landing back on `StartFragment`).

  Final live run (2 rounds, second ending in Home): both roles reported `OK`, watched end to end,
  confirmed via logcat that both rounds wrote distinct real text and the final Home click genuinely
  deleted the room and sent both devices home.

- **Corrected two changelog/doc entries this same investigation disproved**: the "room
  disappeared" note above was a namespace mismatch in my own `curl` commands (see the Tier B
  namespace note above), not a real bug — retracted the "candidate for new coverage" framing.

- **Tier B orchestration rebuilt for speed: headless by default, self-booting emulators, genuinely
  parallel host/guest launch.** Prompted by Cade asking whether Tier B could run headless like
  Playwright, and separately noticing the host/guest join steps ran serialized rather than in
  parallel.
  1. Replaced the logcat-tag room-code handoff (`Log.i("E2E_SIGNAL", ...)` + the orchestration
     script polling `adb logcat` for it before even starting the guest process) with
     `E2ERoomCodeSignal`, a small Firebase location keyed by a per-run correlation ID
     (`e2eSignals/<correlationId>/roomCode`). The guest now does its own independent navigation
     (launch, name entry, navigate to join) at the same time as the host, blocking only at the
     final "type the code in" step — previously the entire guest process didn't start until the
     script had already captured the host's code from logcat.
  2. `scripts/run-tier-b.ps1` now boots `Test_API35`/`Test_API35_B` itself if they aren't already
     connected — headless (`-no-window`) by default, `-Visible` switch to get real windows when
     watching a run or debugging something hard to diagnose from `errorLogs/`/logcat alone (Cade's
     stated preference: default headless, switch to visible only when a problem is proving genuinely
     hard to resolve from logs).
  3. Confirmed working end-to-end after the rewrite: `TwoDeviceFullGameLoopTest` (2 rounds) via the
     new script — both roles reported `OK` in ~38s total, headless, with host and guest launching
     simultaneously instead of serialized.
  4. Policy confirmed with Cade: Tier A tests should be written for every new feature going forward
     alongside Tier B where applicable; Tier B itself stays a manually-triggered "run when asked"
     tool, not part of routine auto-run after every change (it's slow and needs real emulators).

- **Deployed `database.rules.json` to the live production project (`mixedupgame`) for the first
  time.** Everything up to this point had only ever been applied to the local Firebase Emulator
  Suite. Checked the live rules first via `firebase database:get "/.settings/rules" --project
  mixedupgame` before touching anything, per Cade's explicit condition ("go ahead... unless you
  can see what is there first"). **Finding: production had effectively zero access control.** The
  live rules were still the default Firebase "test mode" starter rules
  (`.read`/`.write: "now < <timestamp>"`), but the timestamp had an extra digit (14 digits instead
  of the normal 13) — instead of expiring 30 days after project creation as intended, it doesn't
  expire until roughly the year 2483. Matches what Cade said going in ("i have barely touched prod
  rules if at all"). Deploying the repo's default-deny + explicit-allowlist ruleset (`rooms`,
  `leaderBoard`, `AccountPlayers`, `expiredRooms`, `phoneAppVerified`, `watchAppVerified`,
  `errorLogs` — the same list this session's testing docs already track) was a strict tightening,
  not a functional change, since every top-level path the real app writes to was already
  allow-listed. Verified post-deploy by re-fetching `/.settings/rules` and confirming it matches
  `database.rules.json` exactly. Also noticed a stray `e2eSignals` top-level key already present in
  production's data (via `firebase database:get / --shallow`) — a past Tier B (or manual) run must
  have written to the real project instead of the local emulator at some point; harmless
  (just a leftover room-code signal) and will simply become unreachable under the new rules since
  it's not allow-listed. Left in place rather than deleted, since deleting live production data
  wasn't asked for.

- **UX/UI bug-fixing pass triggered by a live 2-account-player manual playtest** (`rascade@gmail.com`/
  Kayde as host, `dude@gmail.com`/Dude as guest — real production Firebase Auth accounts, stored
  gitignored in `local-test-accounts.md`, never committed). Two-pass strategy per Cade's request:
  a fast functional pass clicking through the whole loop first, then a dedicated UX/UI pass over
  the same flow. Every issue below was found live and fixed before moving to the next:
  1. **`fragment_start.xml`'s `secondary_actions` row was crowded** (4 fixed-width buttons crammed
     together) — rebuilt as a clean 2-slot weighted `ConstraintLayout` chain
     (`leaderboards_button`/`profile_button`, with `back` sharing the second slot by mutual-exclusive
     visibility), and pulled `signOut` out of that row entirely into its own fixed-position,
     properly-margined top-right corner button — it had been sized to touch the screen edge, the
     exact case the earlier "never let a view touch the physical screen edge" rule targets.
  2. **`fragment_waiting_for_host.xml` wasted vertical space and let its status text touch the
     screen edges** — `textView2` (the programmatic "Waiting for host..." text) was raw
     `match_parent` with a `bottom_toBottomOf="parent"` packed-chain constraint stretching content
     across the full screen height; reworked to `0dp` + `layout_constraintWidth_percent`/`_max` with
     real margins, anchored near the top so the player-list `RecyclerView` gets the remaining space
     instead of everything being centered in a needlessly tall packed chain.
  3. **`again_ending` ("Play Again") looked identical whether it was actually clickable or was in
     the guest's "waiting for host" disabled state** — root-caused to `Widget.WhatIf.Button.Primary`
     having no disabled-state `ColorStateList` at all (unlike `Widget.WhatIf.Button.Secondary`,
     fixed earlier this session). Added matching `primary_button_background.xml`/`primary_button_
     text.xml` state lists reusing the same `secondary_button_surface_disabled`/`_text_disabled`
     colors, so the whole app now has one consistent disabled-button visual language.
  4. **Voice-picker spinner in `ReadSentenceFrag` was illegible sharing a row with the mic FAB and
     pass/next buttons** (voice names like "Regular google voice" wrapped to 4 lines, then
     truncated to "Reg…" once `singleLine`/`ellipsize` were tried as a first pass) — real fix was
     giving it its own full-width row in `fragment_read_sentence.xml`, below the controls guideline
     instead of sharing a horizontal chain.
  5. **Regression caught live during the above fix**: the mic `FloatingActionButton` lost its
     `app:srcCompat` icon reference entirely (rendered as a blank circle) while restructuring that
     row, and the pass/next buttons were no longer vertically centered with the FAB (mismatched
     `layout_constraintVertical_bias` values across differently-sized views). Fixed by restoring
     `app:srcCompat="@drawable/ic_baseline_mic_24"` + `app:tint="@color/black"`, and anchoring
     pass/next directly to the FAB's own top/bottom instead of relying on matching bias values —
     guarantees pixel-perfect centering regardless of view height.
  6. **Voice spinner was visible before the reader had even tapped "show"** — now hidden
     (`View.GONE`) until the sentence is actually revealed, via a new `updateSpinnerVisibility()`
     helper wired into `ReadSentenceFrag.updateSentenceVisibility()`'s existing branches.
  7. **Notebook-paper background's red margin line sat too far from the screen edge, and Cade asked
     for it to be moved twice** (first "like a regular notebook", then "reduce distance...by
     half") — required actual pixel-level editing since the line is baked into
     `lined_paper_read.png`, not a drawn view: used Python PIL to crop 66px off the image's left
     edge (1180×554 → 1114×554), then adjusted `fragment_read_sentence.xml`'s `paper_text_start`
     guideline (0.35 → 0.21) to keep body text starting right at the line's new position, matching
     real handwriting starting at a notebook's margin. Also fixed `lined_paper_read_tiled.xml`
     (`tileMode="repeat"` on both axes → `tileModeX="disabled"`/`tileModeY="repeat"`) so the red
     line can't visually duplicate on a wide/landscape viewport — a single sheet of paper never
     repeats its own margin line. Both fixes codified as a new CLAUDE.md rule since this is a
     repeatable failure class (any background asset depicting a real-world layout convention).
  8. **Vote-selection highlight (`VoteFrag`) and leaderboard rows (`LeaderBoardFrag`) didn't reach
     the screen edge** — the classic `LayoutInflater.inflate(id, null)` bug: without a real parent
     reference at inflate time, the inflated item's `match_parent` root can't resolve. Both changed
     to `inflate(id, parent, false)`.
  9. Read-method spinner item text (`read_method_item.xml`) got `singleLine`/`ellipsize="end"` as
     an intermediate step before the full row-restructure fix in #4 above.

- **Built `TwoDeviceAccountPlayGameLoopTest`, the account-play counterpart to `TwoDeviceFullGameLoopTest`**
  — signs in with real Firebase Auth credentials (`-e email`/`-e password` instrumentation args,
  read from gitignored `local-test-accounts.md` at runtime, never hardcoded) instead of typing a
  Free Play guest name, and additionally exercises `VoteFrag` (via a custom `ViewAction` clicking
  the first vote option, since `lb_vote_item.xml`'s dynamically-inflated children share no unique
  ID) since two account players trigger `GameFlowPolicy.allPlayersHaveAccounts()`. New
  `scripts/run-tier-b-account-play.ps1` mirrors `run-tier-b.ps1`'s orchestration but passes a
  *different* email/password pair to the host vs. guest role, parsed from `local-test-accounts.md`
  at runtime. Compiles clean (`MixedUp:compileDebugAndroidTestJavaWithJavac`), and along the way
  found and fixed one real bug in the orchestration script itself: a stale Firebase Auth session
  survives `adb install -r` (not a full data wipe), so without clearing app data first, both
  devices auto-signed-in from a previous run and skipped `AccountFrag` entirely, breaking the
  test's `signIn()` step — fixed by adding `adb shell pm clear <package>` before every install.

  **Also found and fixed a real, if minor, mislabeled-error bug in `UserViewModel.signIn()`** while
  chasing a "User Disabled" message appearing on a guest device that was never actually disabled
  (confirmed via the Auth emulator's admin API — neither test account has `disabled: true`). The
  failure handler only recognized three specific Firebase Auth error message strings (invalid
  password/email/badly-formatted email); anything else — including a transient
  `FirebaseNetworkException` from a real connectivity blip — fell into a catch-all that always
  displayed "User Disabled", which would falsely alarm a real player during an ordinary network
  hiccup. Now checks `instanceof FirebaseNetworkException` for a "Network Error" message, and only
  shows "User Disabled" for an actual `FirebaseAuthInvalidUserException` with error code
  `ERROR_USER_DISABLED`; everything else falls into a generic "Sign In Failed".

  **Could not get a clean passing Tier B run in this environment despite extensive troubleshooting**
  (8 attempts total, including a full Firebase Emulator Suite restart with a corrected JDK — the
  prior 2-day-old instance was still running on JDK 16, below firebase-tools' JDK 21 minimum for
  new instances, though the old instance itself was running fine; restarting made no difference).
  Root-caused as far as tooling allows: `errorLogs/` had exactly one unrelated entry (a benign FCM
  push-token registration failure) despite 8 failed runs, meaning the app itself never threw or
  logged anything during the stall — the room-creation write was simply queued locally and never
  flushed. A raw HTTP write/read directly against the same `rooms/` path the app uses succeeded
  instantly, proving the Firebase backend itself is healthy. Both AVDs showed repeated
  `wpa_supplicant: CTRL-EVENT-BEACON-LOSS` events in logcat throughout every attempt — the same
  virtual-WiFi-radio symptom already diagnosed earlier this session as an emulator-only limitation,
  not a real device/production issue. Notably, on the final attempt, even the previously-reliable
  `TwoDeviceFullGameLoopTest` (free play, untouched by any change this session) failed at the
  identical spot, confirming this is host/emulator-environment instability, not a regression in
  this session's code. **Tier B account-play test is built, compiles, and its flow logic follows
  the same proven pattern as the already-working free-play test, but has not yet produced a passing
  run in this specific long-running session's environment** — recommend re-running
  `scripts/run-tier-b-account-play.ps1` in a fresh terminal/host session (fewer competing
  processes) before concluding anything further. The underlying account-play game loop itself was
  already verified working end-to-end via extensive manual testing earlier this session (2 full
  rounds, twice).

- **Tier A coverage review for account-play-specific logic**: `GameFlowPolicy.allPlayersHaveAccounts()`
  already has thorough pure-function coverage (`GameFlowPolicyTest` — null/empty/mixed/all-true
  cases) plus a Firebase-round-trip consistency check against `EndFrag`'s independent inline
  computation (`AccountPlayBranchingEmulatorTest`), both from an earlier session and still valid.
  `EndFrag`'s replay/`again_ending` enable-state logic (host-enabled-by-default, guest-disabled-
  until-`replayState=="yes"`) already has `ReplayLoopEmulatorTest` coverage, unchanged this session
  — only its color styling changed (see above), a Tier B/visual concern, not a Tier A one. This
  session's actual account-play-specific code changes (`ReadSentenceFrag.updateSpinnerVisibility()`/
  `currentUserHasAccount()`, the `VoteFrag`/`LeaderBoardFrag` inflate fix) are either trivial
  View-visibility toggles too tightly coupled to Fragment/View lifecycle to be worth extracting
  into a policy class per CLAUDE.md's own established restraint on `ReadSentenceFrag`, or pure
  UI-layout bugs only meaningfully verifiable via a real layout pass (Tier B/Espresso), not
  Robolectric-computable logic. **Conclusion: no new Tier A gaps found requiring new tests.**

- **Full regression confirmed green**: `API:testDebugUnitTest` (90 tests), `MixedUp:testDebugUnitTest`
  (38 tests) — 0 failures, 0 skipped across both — plus `MixedUp:assembleDebug` and
  `MixedUp:compileDebugAndroidTestJavaWithJavac`, all without the emulator flag.

## Archived Session: feature/word-based-room-codes

- Replaced the 4-character random-letters-and-digits (case-sensitive) room join code with two
  random, distinct, lowercase 4-letter dictionary words joined by a dash (e.g. `wolf-lake`) —
  easier to read aloud and type on a phone keyboard, and no longer ambiguous about letter case.
  Added `GameLogic.randomRoomCode(Random)` (a 600-word curated list, pure/testable per this
  repo's usual pattern) and pointed `RoomViewModel.makeRoomID()` at it; removed the now-dead
  `allChars`/`usableCharacter` fields. No UI/layout changes needed — the join `EditText` has no
  `maxLength`, and the host's `gameCode` display `TextView` is already full-width `0dp`.
- Fixed a real gap caught in review before this shipped: `GameFlowPolicy.normalizeRoomCodeInput`
  originally only trimmed whitespace, so a player typing `wolflake` (no dash) for a `wolf-lake`
  room would get a literal Firebase-key mismatch and "room not found" — the dash was an exact-match
  requirement, not the cosmetic separator it needed to be. Now strips everything but letters,
  lowercases, and reinserts the canonical dash once there are exactly 8 letters, so `wolflake`,
  `WOLF LAKE`, `wolf_lake`, and `wolf-lake` all resolve to the same room. Word order still matters
  by design (confirmed with Cade) — only separator/casing is normalized, not word permutations.
- Bumped `versionCode`/`versionName` (10/2.0.3 → 11/2.0.4) for the next Play Console release, since
  the last production release had already consumed 10/2.0.3.

## Archived Session: feature/stable-testing-and-refactors

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
