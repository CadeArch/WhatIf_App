---
name: e2e-gotchas
description: Concise symptom-to-cause table for two-device/Tier B end-to-end failures in this repo - RTDB pushes not arriving over the Android emulator's 10.0.2.2 NAT (needs adb reverse), false-green test runs from skipped emulator tests, "restarted" emulators that never actually restarted, cached Firebase snapshots that fake connectivity, swallowed write failures that surface on the other device, degraded long-running AVDs, account-play sign-in failing because the in-memory Auth emulator has no accounts, and cleartext-HTTP blocks that break Auth while Database keeps working. Load this BEFORE debugging any Tier B / two-device Espresso failure, or any "the write worked but the other device never saw it" problem. Companion to whatif-testing, which has the fuller Tier A / local-Firebase reference.
---

# E2E Gotchas (Tier B / two-device)

Every entry here cost real hours at least once. **Check this table before forming a theory** — the
failures in this repo look like network flakiness and almost never are.

## Symptom → cause → fix

| Symptom | Actual cause | Fix |
|---|---|---|
| Writes commit, initial reads work, but **pushes never arrive**. Host sits on "1 player" while the guest's join is already in the DB. Transactions fail at ~15.09s with `aborted due to a network disconnect`. | The `10.0.2.2` host-loopback *redirect* does not reliably carry RTDB's inbound push traffic to the local emulator. The 15s is Firebase's health timeout firing because the server's reply never arrived. **This is not a regression and 10.0.2.2 is not "broken" in general** — ordinary internet traffic uses the same NAT fine. Tier B only started genuinely talking to the *local* emulator once `FirebaseInitProvider` removal + an emulator `databaseUrl` landed; before that every "passing" run was silently hitting production, so this path had never actually been exercised. | App targets `127.0.0.1`; scripts run `adb reverse tcp:9000 tcp:9000` and `tcp:9099`. Already wired into both Tier B scripts + `FirebaseEmulatorConfig`. |
| `BUILD SUCCESSFUL`, zero failures, and nothing was actually tested. | Emulator tests `assumeTrue(emulatorReachable())` and **skip** when it's down; JUnit reports skips as success. Gradle then caches that result and **replays it** — a second run prints `BUILD SUCCESSFUL in 1s` without executing anything. | Check `skipped="0"` in the XML, not the banner. Force real execution: `rm -rf */build/test-results/testDebugUnitTest` first. |
| Restarted the Firebase emulator, but old rules/behavior persist. Log says `All emulators ready` — or nothing at all. | Killing `node` does **not** stop the database emulator; a **Java** process holds port 9000. The new suite dies with `Could not start Database Emulator, port taken` while the old one keeps serving. | Kill the actual port owner (`Get-NetTCPConnection -LocalPort 9000` → `OwningProcess`), then confirm `All emulators ready` in the log before trusting anything. |
| A listener's initial `onDataChange` fires, so the client "is connected" — but no update ever follows. | **An initial snapshot proves nothing.** Firebase serves it from cache; an unconnected client reports `exists=false` identically to a connected one on an empty path. This assumption sent two whole investigations down the wrong path. | Prove connectivity with that client's own `.info/connected`. Never infer it from a snapshot. |
| Device A's operation fails, but the only error appears on **device B** as a timeout. | A completion result was ignored (`setValue(...)` without checking `task.isSuccessful()`), so the real failure was invisible where it happened. | Assert on every completion result in harness code. This is what finally made the NAT bug diagnosable. |
| Operations that took milliseconds now take seconds or time out; runs get flakier the longer the session goes. | Long-running AVDs degrade after hours/many runs (watch accumulated `qemu-system-x86_64` CPU). | Cold-restart both AVDs (`adb emu kill`) and stop the Gradle daemon. Took room reservation from a 15s timeout back to 76ms. |
| Data "missing" from the emulator — a REST read returns `null`. | Wrong namespace. Tier A/Robolectric use `demo-mixedupgame-default-rtdb`; any real APK uses `mixedupgame-default-rtdb`. Both live in the same emulator process and the wrong one silently returns `null`. | Pass the right `?ns=`. |
| A `curl` probe "proves" security rules allow/deny something. | **It proves nothing.** The RTDB emulator answers unauthenticated REST as **admin** and skips rule evaluation entirely — a write to a deny-all root succeeds. | Only client-SDK behavior can test rules. Don't write REST-based rule preflights; they always pass. |
| Emulator refuses to start: `database.rules.emulator.json:2:3: Expected 'rules' property.` | The rules parser allows **only** a top-level `rules` property. JSON-style `"//"` comment keys are rejected. | Use `//` line comments above the object instead. |
| Account-play run: both devices parked on the sign-in screen, never reach `create_game`. | The credentials in `local-test-accounts.md` are **production** accounts. The Auth emulator is in-memory and starts empty, so they do not exist locally — and every suite restart wipes whatever was seeded. | `run-tier-b-account-play.ps1` now seeds them itself (create-or-sign-in, then set `displayName`, idempotent). Never copy production accounts anywhere. |
| Sign-in fails with `An internal error has occurred. [ Cleartext HTTP traffic to 127.0.0.1 not permitted ]`, while Database works fine. | `network_security_config.xml` matches on the **exact host string** — `localhost` does not cover the literal `127.0.0.1`. Auth is REST and is blocked; Database's WebSocket is exempt from the policy, which is why only half the app breaks. | List `127.0.0.1` literally in the debug `network_security_config.xml`. |
| App sits on "Connection lost" forever; `Provided authentication credentials are invalid`. | Stale persisted Firebase Auth session — the Auth emulator is in-memory, so a restart deletes the account the device still holds a refresh token for. Survives `install -r`. | `pm clear` before every run (both scripts do). Free play is just as vulnerable as account play. |
| A write's completion callback never fires, though the value reaches the database. | A second `getReference()` on the same `FirebaseApp`. Only the first root reliably delivers callbacks. Real on-device, not just under Robolectric. | One root per FirebaseApp, created once, reused. |
| An Espresso retry helper gives up immediately instead of retrying. | `matches(...)` failures are `AssertionError`-derived. | `EspressoWaitUtils.waitFor` must catch `Throwable`, not `RuntimeException`. |

## Writing two-device tests

**Fabricate the other player instead of driving a second device, when the test is about *this*
device's UI.** Most membership flows hinge on someone being absent, removed, or stale - that is
*state*, and `E2ERoomFixture` writes it straight into the room. Producing the same state with a real
device means launching it, joining, then killing it at exactly the right instant. The app cannot
tell the difference: same room, same listeners, same policy. Keep two real devices only where the
assertion is about what the *other* player experiences (`HostEndsGameMidRoundTest`).

**Budget reading/turn loops by time, not by iteration count.** A step count is really a budget for
waiting on the *other* device, and it runs out while the round is progressing perfectly well - a
generous-looking 8 steps expired on the guest seconds before its turn arrived, leaving it sitting
passively at the exact moment it needed to click. Use a wall-clock deadline (~180s for a three-reader
round across two devices) and `fail()` with what was stuck.

**Do not name a test helper after a statically imported Espresso matcher.** A member
`isDisplayed(int)` shadows the imported `isDisplayed()`, and every existing `matches(isDisplayed())`
in the file stops compiling with "method isDisplayed cannot be applied to given types" - which reads
as a problem with the calls, not the helper. Name them `isShowing` / `isTappable`.

**A persistent snackbar does not stay.** Snackbars are a queue, so anything else the app shows
displaces `LENGTH_INDEFINITE` permanently. Any bar a test waits for must be re-shown by the app
(check `isShownOrQueued()` and re-evaluate on a tick) or the test will look for something that
existed a minute ago. This was a real product bug, not just a test problem: the host's Remove
control vanished on reaching the reading screen.

## Method notes

**Separate environment from code before debugging code.** `git stash push -u`, run against clean
HEAD, `git stash pop`. That one step proved a whole session of changes innocent after hours of
suspecting them — do it early, not last.

**`wpa_supplicant: CTRL-EVENT-BEACON-LOSS` is noise.** It fires every ~8s on a perfectly healthy
emulator. If it is the only evidence for a "flaky network" theory, the theory is wrong.

**Prefer the app's own breadcrumbs.** `errorLogs/` (Emulator UI at `localhost:4000`) carries the
exception, stack, version and a 40-line trail. Check it before raw logcat — but filter logcat by
the *current* run's timestamps, since reading a previous run's lines will invent a bug that isn't
there (happened this session).

**When a run finally gets further, re-read what actually failed.** Failures cascade: a guest
timing out on the room code is usually a *consequence* of the host never publishing, not an
independent bug.
