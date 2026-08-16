---
name: whatif-testing
description: Testing and local-Firebase reference for this repo - the GameRepository test seam, Tier A (Robolectric + local Firebase Emulator Suite) conventions and gotchas, Tier B two-device Espresso end-to-end (scripts/run-tier-b.ps1, run-tier-b-account-play.ps1), the stale-Firebase-Auth-session trap that wedges the app offline, why FirebaseDatabase.useEmulator() does not stick, RTDB security-rule cascading, leaderBoard cross-test pollution, and error-log-driven diagnosis via errorLogs/ and its project namespaces. Load this BEFORE writing or running any test, running a Tier B script, touching FirebaseEmulatorConfig or the emulator build flag, or debugging "Client is offline" / "Connection lost" / data-not-showing-up-in-the-emulator problems.
---

# WhatIf App - Testing And Local Firebase

Load this before writing or running tests, or when app data is not landing where you expect.
Most of this was paid for the hard way: a full session was lost to Tier B silently writing into
the live Firebase project while logging that it was using the emulator, and another to a stale
auth session that looked exactly like a flaky network. Check the specific traps here before
forming a theory.

## 0. Before debugging a Tier B / two-device failure, load the `e2e-gotchas` skill

It is a symptom-to-cause table for exactly these failures and it is short. The two that bite most
often, repeated here because they make a run *look* green or *look* like a code bug:

- **`BUILD SUCCESSFUL` is not evidence.** Emulator tests `assumeTrue(emulatorReachable())` and skip
  when it is down; JUnit counts skips as success, and Gradle then replays that cached result on the
  next run without executing anything. Check `skipped="0"` in the XML, never the banner.
- **Writes committing does not mean the connection works.** RTDB over the Android emulator's
  `10.0.2.2` NAT delivers writes and initial reads while dropping server pushes, so one device sits
  forever on data that is already in the database. The app targets `127.0.0.1` and the Tier B
  scripts `adb reverse` ports 9000/9099 for this reason - see `e2e-gotchas`.
## 0c. Robolectric does not run the main looper - idle it or every Firebase wait deadlocks

The Firebase SDK delivers **every** completion callback on the main looper, and Robolectric only
runs that looper when you tell it to. So this deadlocks:

```java
latch.await(10, TimeUnit.SECONDS);   // blocks the test thread...
                                     // ...while the callback that would release it never runs
```

The symptom is a timeout - "join timed out", "write timed out" - which reads exactly like the
emulator being unreachable, so it sends you off checking ports and rules. It is not. Cost a full
round of debugging on `PlayerRemovalEmulatorTest`, where all six tests failed this way while the
emulator was up and a neighbouring test class passed.

Every wait in an emulator test must pump the looper:

```java
private void pump() throws InterruptedException {
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    Thread.sleep(50);
}

private boolean awaitLatch(CountDownLatch latch) throws InterruptedException {
    long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
    while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
        pump();
    }
    return latch.getCount() == 0;
}
```

That includes bare `Thread.sleep()` settle periods - use a pumping equivalent, or the callbacks you
are waiting to *not* happen cannot happen either, and the test passes vacuously. Copy the helpers
from `CollectingPhaseEmulatorTest` or `PlayerRemovalEmulatorTest` rather than writing fresh ones.

## 0d. Make time pass with data, not with a test-only threshold

Every timing rule in this app is evaluated against stored data - `canKickPlayer` reads
`player.disconnectedAt`, the host-away deadline reads `hostConnection/lastSeenAt`. So a test makes
someone look long-gone by **writing a timestamp from ten minutes ago**, and production code agrees
instantly with its real clock and real policy.

Do not add an overridable threshold, a debug flag, or a shortened constant for tests. It proves the
app behaves correctly *in test mode*, which is not the thing anyone needs to know - and it is the
same pattern already rejected once here (routing production database calls through a test-aware
helper). See `PlayerRemovalEmulatorTest.makeLongGone`.

## 8. Testability seam: depend on `GameRepository`, not `DatabaseReference`, for anything you want to unit test

`API/.../repositories/GameRepository.java` is a clean interface (`root()`, `room(id)`,
`players(id)`, `listenToPlayers(...)`, etc.) that `RoomViewModel` depends on — which is exactly why
`RoomViewModelTest.java` can test replay-state reset and room-ID generation using a
`FakeGameRepository implements GameRepository` inner class instead of a real Firebase connection.
`UserViewModel`, by contrast, takes a raw `DatabaseReference` directly in its constructor and can't
be tested the same way. If you're adding meaningfully new logic to `UserViewModel`, consider giving
it a `GameRepository` dependency the same way `RoomViewModel` has one, so it picks up the same fake-based
test seam — but this is a constructor-signature change with ripple effects on every call site, so
treat it as a deliberate step tied to a real feature, not a drive-by refactor.

For anything that genuinely needs a real Firebase connection to test (listener add/change/remove
behavior, multi-client scenarios), use the local Firebase Emulator Suite (`firebase.json`,
`.firebaserc`, README's "Local Firebase Emulator" section) rather than the live project — manual
runs and tests against production have already polluted real room/leaderboard data at least once.

**One `DatabaseReference` root per `FirebaseApp` - and this is a PRODUCTION rule, not only a test
one.** `API/.../FirebaseRoot.java` now owns the app's single root; call `FirebaseRoot.get()` rather
than `FirebaseDatabase.getInstance().getReference()`. The app previously created three (one in
`FirebaseGameRepository`, two in `UserViewModel`), and only the first behaves: a write through a
later one reaches the database but its completion listener may never fire, and **an unresolved local
write masks server updates at that path**. That is how two ViewModels in the same process ended up
disagreeing about the same room - a host stuck reading its own stale `activeReaderIndex=1` while the
guest had moved to 2, each waiting on the other, with reading deadlocked between them. Only tests
that deliberately want a separate connection pass their own reference in.

**The same rule in tests:** A second `.getReference()` call for the same app
produces a reference whose writes silently never deliver their completion callback in this
Robolectric/JVM environment (confirmed multiple times this session, including once by writing this
same bug into a second test file after already having found and documented it in the first — not a
real-device issue, purely this test harness). Store the reference from `setUp()` in a field and
pass it around instead of re-deriving it inline anywhere in the test.

**Firebase RTDB security rules cascade as "OR", not "most specific wins."** Once an ancestor path
grants `.read`/`.write: true`, no descendant rule can revoke it with `.read: false` — the read/write
still succeeds. Learned the hard way trying to produce a permission-denied `ERROR` state for
`RoomJoinValidationEmulatorTest`: nesting `.read: false` under a `.read: true` root did nothing. If
you need `database.rules.json` to actually deny something, the root itself must default-deny
(`.read`/`.write: false`) with explicit allow-listed subpaths for the app's real top-level roots
(`rooms`, `leaderBoard`, `AccountPlayers`, `expiredRooms`, `phoneAppVerified`, `watchAppVerified` —
grep `db.child("` in `API/src/main/java` if a new top-level root gets added, since that list needs
to stay in sync or real writes will start failing).

**`leaderBoard` is a single global, non-room-scoped top-level Firebase node**, and the local
emulator's data persists across separate test *methods* and separate `./gradlew` invocations within
a session (it's a running process, not reset per test). Any test that seeds or asserts against
`leaderBoard` needs to explicitly wipe it in `@Before` (`db.child("leaderBoard").removeValue()`,
awaited) — otherwise an earlier test's leftover entries silently corrupt later assertions like
`isLeaderBoardFull()`'s `size() < 20` check. Room-scoped data (`rooms/{roomId}/...`) doesn't have
this problem since each test already generates a fresh unique room id.

**When a Tier A test is meant to catch a specific suspected bug, don't trust a first-try pass.**
Twice this session a test passed immediately not because the bug didn't exist, but because the test
didn't actually reproduce the race/condition that triggers it (see `ReplayLoopEmulatorTest`'s two
methods — the "clean sequential" version passed trivially by waiting for each step to fully
complete, which is the opposite of a race; the version that actually reproduced the reported bug
required *not* detaching a listener the way the real losing client wouldn't have). Before accepting
a passing "regression test," check whether it was actually possible for it to fail — temporarily
revert the fix and confirm the test fails for the diagnosed reason, not just that it's green.

**Tier B: real two-device Espresso end-to-end.** `scripts/run-tier-b.ps1` orchestrates two real
emulators (`Test_API35` host, `Test_API35_B` guest — both stable API 35; the other two AVDs,
`Pixel_9`/`Pixel_9_3`, run a preview API level Espresso can't start instrumented tests on) each
running the actual app UI, both pointed at the local Firebase Emulator Suite. The script boots
both emulators itself if they aren't already up. Both scripts run **visible by default** — Cade
wants to watch these, not just get a pass/fail — and take `-Headless` for a quick/unattended run.
They also build the APKs themselves with `-PuseFirebaseEmulator=true` and hard-fail if the app APK
turns out to be a production build, because a plain `assembleDebug` (a regression run, Android
Studio's Run button) silently overwrites it and used to repoint the whole suite at the live
project. Room codes are randomly-generated word pairs with
no way to force a fixed code through the real create-room UI, so the host's generated code has to
cross process boundaries to the guest — this goes through `E2ERoomCodeSignal`, a small Firebase
location keyed by a per-run correlation ID (`e2eSignals/<correlationId>/roomCode`), not a logcat
signal the orchestration script has to capture first. That means **both roles launch
simultaneously** and each runs its own independent local navigation (app launch, name entry,
navigate to create/join) in real parallel — the guest only blocks on the signal at the last
possible step (typing the code in), not on the host's process finishing first. Both test classes
(`TwoDeviceMultiplayerTest`, `TwoDeviceFullGameLoopTest`) are one shared class each, branching on a
`role` (`host`/`guest`) instrumentation argument rather than two separate classes, to avoid
duplicating Espresso setup. `EspressoWaitUtils.waitFor(...)` is a sleep-poll retry helper for real
async Firebase state Espresso's own idling mechanism doesn't wait for (`NavigationFlowTest`'s
purely-synchronous-navigation tests never needed this) — it must catch `Throwable`, not just
`RuntimeException`: Espresso's `matches(...)` assertion failures are `AssertionError`-derived, and
a narrower catch silently stops retrying them (found live when a guest's reading-turn wait threw
immediately with no retry while the host's identical wait happened to pass on the first try).
Confirmed this harness can genuinely fail, not just always pass, by running the guest role with a
deliberately wrong room code and confirming a real `NoMatchingViewException` timeout — same
discipline as the Tier A "confirm it can actually fail" rule above.

**Tier B policy**: only run Tier B when explicitly asked (it's slow and uses real emulators) —
don't auto-run it after every change the way Tier A/Robolectric tests should be. Tier A tests
should still run routinely to catch regressions fast.

**A stale persisted Firebase Auth session wedges the app permanently offline — always `pm clear`,
never just `install -r`.** This masquerades as a network/emulator bug and cost ~9 failed Tier B runs
plus a wrong "emulator WiFi is flaky" diagnosis before being root-caused, so recognize it fast:
- **Symptoms**: the app sits on the "Connection lost" banner forever; `.info/connected` never goes
  true (or connects then immediately drops); every read/write fails with
  `java.lang.Exception: Client is offline`; `errorLogs/` shows `Failed loading expired room markers
  for cleanup`; logcat shows `PersistentConnection: pc_1 - Provided authentication credentials are
  invalid` (the real tell — it names `google-services.json`, which sends you down the wrong path).
- **Cause**: the Auth emulator is **in-memory** — restarting the Emulator Suite deletes every
  account. Any device that had signed in still holds a persisted refresh token
  (`shared_prefs/com.google.firebase.auth.api.Store*.xml`) for a user that no longer exists. The SDK
  can't refresh it, hands RTDB an unusable credential, and RTDB refuses to establish the websocket
  at all. It never self-heals: the app never signs out, and `install -r` **deliberately preserves
  app data**, so it survives reinstalls *and* app-version changes (an old build off a different
  branch fails identically, which is what makes it look environmental rather than local-state).
- **Fix / prevention**: `adb shell pm clear com.CadeMixedUpGame.phoneapp` before a run — both
  `scripts/run-tier-b.ps1` and `scripts/run-tier-b-account-play.ps1` now do this on every install,
  and free play is *just as vulnerable as* account play even though it never signs in.
- **Don't blame `wpa_supplicant: CTRL-EVENT-BEACON-LOSS`.** It fires every ~8s on a perfectly
  healthy emulator with a rock-stable Firebase connection — verified directly. It is background
  noise, never the cause; if it's the only evidence for a "flaky emulator network" theory, the
  theory is wrong. Prove connectivity claims against the *client's own* `.info/connected` state and
  a raw `curl` to the RTDB emulator instead.
- To keep accounts across Suite restarts for manual dev, start it with
  `firebase emulators:start --only database,auth --import ./emulator-data --export-on-exit`
  (`/emulator-data/` is already gitignored).
- **The deeper cause of that "invalid credentials" line was RTDB talking to production while Auth
  talked to the emulator** — see the next entry. Once both point at the same place, the fake local
  token is valid and the wedge disappears; `pm clear` remains worth doing so runs start signed out.

**`FirebaseDatabase.useEmulator()` does not reliably stick — the emulator URL must be in
`FirebaseOptions` before the default app is created.** This one silently wrote real Tier B rooms
into the live project for days while logging "Using local Firebase Emulator Suite":
- **Symptom**: `-PuseFirebaseEmulator=true` builds log the emulator line, yet the local emulator's
  `rooms/` stays empty and rows show up in the production console instead. Free play still "passes"
  (production rules are open enough), while account play dies at room creation with
  `PersistentConnection: Provided authentication credentials are invalid` — because Auth's
  `useEmulator()` *does* apply, so a fake locally-minted token is being presented to **production**
  RTDB. That split brain is the real tell.
- **Cause**: `useEmulator()` mutates the instance's `RepoInfo`, which is also the SDK's cache key,
  so the entry is stranded and the *next* `FirebaseDatabase.getInstance()` builds a fresh instance
  from the app's unchanged production URL. Proven on-device: right after the call `getInstance()`
  returned `http://10.0.2.2:9000`, and 0.7s later (same process, same FirebaseApp, same
  FirebaseOptions, different `identityHashCode`) RoomViewModel got one pointing at production.
- **Fix**: debug builds remove `FirebaseInitProvider` (`MixedUp/src/debug/AndroidManifest.xml`) and
  `WhatIfApplication.onCreate()` calls `FirebaseEmulatorConfig.initializeDefaultApp(...)`, which
  creates the default app with the emulator `databaseUrl` already set. Nothing is mutated, so every
  `getInstance()` anywhere resolves to the emulator with **zero emulator-awareness in app code** —
  don't "fix" this by routing call sites through a test-aware helper, which is unenforceable (any
  new `getInstance()` silently regresses) and drags test concerns into production classes.
- Don't try `FirebaseApp.delete()` + re-init instead: it crashes with `IllegalStateException:
  FirebaseApp was deleted` because Firebase Installations is already using it on a background thread.
- **Verify, don't assume**: check the local emulator actually received the write
  (`curl "http://localhost:9000/rooms.json?ns=mixedupgame-default-rtdb&shallow=true"`). The log line
  alone proves nothing.

**Error-log-driven diagnosis and flow discovery** — the auto error-log table (`AppLog`'s breadcrumb
trail + `ErrorReporter` hook, `errorLogs/` in Firebase) exists specifically to make this workflow
possible, not just to exist:
- **When a Tier B (or any manual) run surfaces a real bug**, check `errorLogs/` first — Emulator UI
  at `http://localhost:4000`, or `curl http://localhost:9000/errorLogs.json?ns=<namespace>` — before
  digging through raw logcat. Every entry already has the exception, stack trace, app version, and
  the 40-line breadcrumb trail (including screen transitions) leading up to it.
- **`<namespace>` depends on which client wrote the data — get this wrong and you'll conclude data
  is missing when it's just elsewhere.** Tier A/Robolectric tests explicitly construct a
  `FirebaseOptions` with `.setProjectId("demo-mixedupgame")`, a fake project that only exists for
  testing — use `ns=demo-mixedupgame-default-rtdb` for anything they write. Any *real* app build
  (Tier B included) uses `FirebaseApp.getInstance()`, configured from `MixedUp/google-services.json`,
  whose actual `project_id` is `mixedupgame` (no `demo-` prefix) — use `ns=mixedupgame-default-rtdb`
  for that. Both namespaces coexist in the same local emulator process; querying the wrong one
  silently returns `null` with no error, which reads exactly like "the data doesn't exist." Learned
  this the hard way chasing what looked like a room-deletion bug after Tier B's first real run — it
  wasn't one; the room was sitting under the other namespace the whole time (confirmed via
  `adb logcat` showing normal writes/teardown and the response's own `X-Firebase-Project-Id` header).
- **For deciding what to cover next**, periodically review accumulated `errorLogs/` entries
  (group by `tag`/`exceptionClass`) — including ones from ordinary manual play, not just automated
  runs — for recurring or novel patterns without dedicated regression coverage yet, and turn those
  into new Tier A or Tier B tests.

