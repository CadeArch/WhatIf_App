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
| debug **any** Tier B / two-device failure, or "the write worked but the other device never saw it" — load this *first*, it is a short symptom→cause table and these failures never look like what they are | `e2e-gotchas` |
| write release notes, answer "what changed since the last release", or bump the version for a Play Console upload | `release-notes` |

**When you hit a testing gotcha, write it into the skill before moving on.** Any behaviour that
made a test lie - pass vacuously, fail for a reason unrelated to the code, or point at the wrong
cause - belongs in `whatif-testing` (Tier A / local Firebase) or `e2e-gotchas` (Tier B /
two-device) the moment you understand it, not at the end of the session. These have each already
cost hours twice, because the second person to hit one had no way to know the first person had
solved it. A fix without the note is half the work.

Everything below applies to any Java change and stays loaded.

# Java engineering — readability, reliability, scalability, encapsulation, reusability

`API/` is the shared model/policy/ViewModel module; `MixedUp/` is the Fragment/Activity UI module.
The rules below are ranked by how much they matter here, each with a real example from this repo
— not generic advice. The goal isn't a rewrite: apply these when you're already touching a file for
a feature/bug — opportunistic, not a sweep.

## 5. Keep business logic in pure, static, testable classes — this repo already has the right model

`GameFlowPolicy`, `GameLogic`, `RoomCreationPolicy`, `UnlockPolicy`, `AuthErrorPolicy` in `API/`
are `public final` classes of `public static` pure functions with **zero Android/Firebase
dependencies** (no `Context`, no `DatabaseReference`, and no Firebase exception types — those
belong in a thin adapter). That purity is why they hold the bulk of this repo's tests as plain JVM
tests needing no emulator or mocking. **Put new game-flow decisions there, not in a Fragment,
Activity or ViewModel** — the ViewModel should keep the Firebase writes and ask a policy what to do.

The counter-examples: `UserViewModel`/`RoomViewModel` (~1100 lines each), `ReadSentenceFrag` and
`MainActivity` still mix several concerns, and `MainActivity`'s disconnect/heartbeat scheduling
can't be unit tested where it sits. Breaking them up is its own roadmap item — this is hard-won
reliability code, so don't refactor it as a drive-by. When you're already in one for a feature,
move one chunk of decision-making out to a policy class rather than adding more inline logic.

## 6. Encapsulation — match Firebase's actual requirement, not "everything public"

Firebase's POJO mapper needs a no-arg constructor plus **either** public fields **or**
getter/setter pairs — not public fields.

- **New model classes**: private/package-private fields + public getters/setters, like
  `LeaderBoardItem`/`Unlockable`.
- **Existing public-field models** (`User`, `Room`, `RoundAssignment`): leave them; a mass
  field-privatization pass is high-risk mechanical churn with no behavior benefit. If you *add* an
  accessor to one, grep for real callers first — `User` once carried ~18 getters/setters nobody
  called, and deleting them broke nothing but a test that existed only to exercise them.

## 7. Reusability — look for the shared shape before writing another one-off

**Use `API/.../ChildEventListenerAdapter.java` for any new `ChildEventListener`** instead of
implementing the raw interface — it defaults the callbacks you don't need and bakes in the
"cancelled" logging. It replaced five near-identical hand-rolled listeners across `RoomViewModel`,
`UserViewModel` and `LeaderBoardViewModel`; see any of those for the pattern.

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
