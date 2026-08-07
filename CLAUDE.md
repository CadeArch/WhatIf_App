# CLAUDE.md

Steering doc for working in this repo. See `README.md` for stack, build commands, gameplay rules,
and the roadmap, and `CHANGELOG.md` for the session-by-session change history.

## Never commit, push, open a PR, or merge unless Cade asks for it in that message

Finishing a feature, going green on tests, or clearing a task list is **not** permission to commit.
Neither is having been asked to land the *previous* branch — that request does not carry forward.
Wait for an explicit "commit this" / "ship it" / "open a PR" every single time, then follow the
`git-pr-workflow` skill for the mechanics.

This rule lives here, in always-loaded context, on purpose. The `git-pr-workflow` skill also states
it, but that skill only loads once committing is already underway — by then the decision it is
meant to prevent has been made. A guardrail against acting is useless if it only appears after you
have decided to act.

## Load these skills first — they are not optional reminders

Most of this repo's hard-won rules live in two skills instead of here, because they only matter for
specific work and used to cost a few hundred lines of context on every unrelated turn. **They only
help if you actually load them, so treat the triggers below as required steps, not suggestions** —
every rule in them exists because the "obvious" approach shipped a real bug first.

| Before you... | Load |
|---|---|
| edit any `res/layout/*.xml`, add/restyle a view, change the visibility of a view others are constrained to, touch a drawable/background, or do portrait/landscape/tablet work | `whatif-android-ui` |
| write or run any test, run a Tier B script, touch `FirebaseEmulatorConfig`/the emulator build flag, or debug "Client is offline" / "Connection lost" / data not appearing in the local emulator | `whatif-testing` |

Everything below applies to any Java change and stays loaded.

# Java engineering — readability, reliability, scalability, encapsulation, reusability

`API/` is the shared model/policy/ViewModel module; `MixedUp/` is the Fragment/Activity UI module.
The rules below are ranked by how much they matter here, each with a real example from this repo
— not generic advice. The goal isn't a rewrite: apply these when you're already touching a file for
a feature/bug — opportunistic, not a sweep.

## 5. Keep business logic in pure, static, testable classes — this repo already has the right model

`API/.../GameFlowPolicy.java`, `GameLogic.java`, and `RoomCreationPolicy.java` are `public final
class`es of `public static` pure functions with zero Android/Firebase dependencies (no `Context`,
no `DatabaseReference`) — connection-grace timing, sentence formatting, randomized assignment,
vote tallying, room-code retry logic. They're also this repo's best-tested code: ~75 JUnit test
methods across `API/src/test`, all plain JVM tests with no emulator/mocking needed, because the
functions are pure. **When you add new game-flow logic, put it here, not in a Fragment or
Activity** — that's what makes it fast to test and safe to change.

The counter-example already in this repo: `ReadSentenceFrag.java` (~620 lines) mixes view binding,
Firebase listener setup, TTS/voice logic, and navigation/game-phase decisions in one class, and
`MainActivity.java` (~520 lines) implements the host-disconnect grace timer and heartbeat
expiration directly with `Handler.postDelayed` — even though `GameFlowPolicy` already owns the
*constants* those timers use (`CONNECTION_GRACE_MS`, `millisUntilHostHeartbeatExpires(...)`), the
scheduling logic itself lives in the `Activity` and can't be unit tested there. Don't treat this as
a mandate to refactor those files outright (that's real, working, hard-won reliability code — see
`CHANGELOG.md`'s archived session notes for how much iteration went into the disconnect/heartbeat
behavior specifically); but if you're already in one of them for a feature, look for a chunk of
decision-making that could move to a policy class as you go, rather than adding more inline logic
next to what's already there.

## 6. Encapsulation — match Firebase's actual requirement, not "everything public"

Firebase Realtime Database's POJO mapper needs a no-arg constructor plus **either** public fields
**or** public getter/setter pairs — it does not require public fields.

`API/.../models/LeaderBoardItem.java` and `Unlockable.java` already do this correctly: package-private
fields, real getter/setter pairs, and (verified by grep) every one of those accessors is actually
called somewhere in the app — this is the pattern to follow for new model classes.

`API/.../models/User.java` used to have both public fields *and* redundant getters/setters for a
subset of the same fields — grepped every getter/setter for real call sites and found only
`getUid()` plus 5 setters (`setHostPlayedAgain`, `setIfFinished`, `setIfSentence`, `setThenFinished`,
`setThenSentence`) were ever actually called; the other ~18 were dead API surface nobody used
(callers always mutated the public fields directly instead). Deleted the unused ones — kept the 6
real accessors, `User.java`'s fields stay public. `Room.java`/`RoundAssignment.java` are plain
public-field POJOs with no getters/setters at all, so they don't have this "two competing paths"
problem in the first place.

- **New model classes**: private/package-private fields + public getters/setters only, matching
  `LeaderBoardItem`/`Unlockable` — fully Firebase-compatible, and every mutation is a method call
  you could add validation/logging to later.
- **Existing public-field models** (`User`, `Room`, `RoundAssignment`): don't do a mass
  field-privatization pass unprompted — every direct field access across every Fragment/ViewModel
  would need to become a method call, a large, high-risk, mechanical change with no behavior
  benefit on its own. Do the cheap, safe part opportunistically instead: if you add a getter/setter
  to one of these classes, grep for real callers before assuming it needs to stay — as `User.java`
  showed, "someone might call this" often isn't true here, only unit tests exercising the accessor
  in isolation, which don't need it to exist for the app to work (rewrite the test to use the field
  directly, as `UserTest.java` now does).

## 7. Reusability — look for the shared shape before writing another one-off

`API/src/main` used to have five independently hand-rolled `ChildEventListener` anonymous classes
with the same shape — `RoomViewModel.loadRooms()`, `UserViewModel`'s player listener, and three in
`LeaderBoardViewModel` — each repeating empty `onChildChanged`/`onChildRemoved`/`onChildMoved`
overrides and near-identical `onCancelled` logging. Extracted `API/.../ChildEventListenerAdapter.java`:
a no-op-default base (the standard `MouseAdapter`-style Java convention) that bakes in the
"cancelled" log line and lets each listener override only the callback(s) it actually uses. Applied
to all 5 existing sites with zero behavior change (verified against the full existing test suite
before/after). **Use this base for any new `ChildEventListener`** instead of implementing the raw
interface — see any of the 5 sites above for the pattern.

## 9. Package structure — flat today, tracked on the roadmap

All 25 Java files in `MixedUp/src/main/java` sit directly in one package
(`com.CadeMixedUpGame.phoneapp`), no subpackages by feature/screen. `README.md`'s roadmap already
has this ("Reorganize `src/main/java` into human-readable folders/sub-folders by
features/screens... handled as a focused branch because it is higher-churn than small safety
refactors") — that roadmap item is the place to do this, not a side effect of an unrelated change,
since renaming packages touches every import in the module.

## 10. Defensive null-handling — keep using the pattern already established here

This codebase consistently guards nullable `Boolean`/collection fields with
`Boolean.TRUE.equals(x)` / `Boolean.FALSE.equals(x)` rather than unboxing directly (see
`GameFlowPolicy.countFinishedIfs`, `allPlayersConnected`) — safe against `NullPointerException`
when a Firebase field hasn't loaded yet or an old room record predates a newer field. Keep this
pattern for any new nullable-`Boolean` check from Firebase data; a direct `if (player.connected)`
on a `Boolean` field is a latent NPE the moment that field is legitimately null (unset in the DB,
not yet synced, etc.) rather than `false`.
