# CLAUDE.md

Steering doc for working in this repo. See `README.md` for stack, build commands, gameplay rules,
and the roadmap, and `CHANGELOG.md` for the session-by-session change history. This file has two
parts: **Part 1** is the Android UI/XML reference (how to write correct, responsive, accessible
layouts in `MixedUp/src/main/res/layout/*.xml`); **Part 2** is general Java/architecture guidance
(readability, reliability, scalability, encapsulation, reusability) for `MixedUp/src/main/java/`
and `API/src/main/java/`.

# Part 1: Android UI/XML

All 21 layouts here already use `ConstraintLayout` as the root, which is the right foundation.
The sections below are how to use it correctly, not a suggestion to switch frameworks.

## 1. ConstraintLayout fundamentals — how to size and position views

**The 0dp rule.** Never use `match_parent` on a direct child of `ConstraintLayout`. Set
`layout_width`/`layout_height="0dp"` plus start/end (or top/bottom) constraints instead — this is
"match constraint" mode, the ConstraintLayout equivalent of `match_parent` that still respects
sibling constraints, margins, and percent/max-width modifiers. `activity_main.xml`'s
`fragment_container` does this correctly (`0dp` + four constraints). Where you see `match_parent`
inside a ConstraintLayout in this repo (e.g. `textView2` in `fragment_waiting_for_host.xml`), treat
it as legacy, not a pattern to copy — it ignores width percent/max constraints entirely.

**Every view needs a constraint on each axis it's not `wrap_content`-sized on.** A view with no
vertical constraint collapses to the top-left (0,0) or renders ambiguously depending on the editor.
If a view's position looks "random," check for a missing `layout_constraintTop_*`/`Bottom_*` or
`Start_*`/`End_*` before touching anything else.

**Percent + max-width is this codebase's working pattern for avoiding stretched UI — keep using
it.** `fragment_start.xml`, `fragment_first.xml`, `fragment_account.xml` etc. size primary content
with:
```xml
android:layout_width="0dp"
app:layout_constraintWidth_percent="0.72"
app:layout_constraintWidth_max="480dp"
```
This keeps buttons/fields from stretching edge-to-edge on wide landscape screens or tablets — a
named failure mode in Android's own responsive-layout guidance. Apply the equivalent vertically
(`layout_constraintHeight_percent` / `layout_constraintHeight_max`) for content that would
otherwise stretch too tall on portrait's much taller viewport — this repo does **not** do this yet
and needs it for the portrait work (see §4).

**Chains** distribute a run of views that all constrain to each other. Set
`layout_constraintHorizontal_chainStyle` (or `..._Vertical_chainStyle`) on the *first* view in the
chain:
- `packed` (this repo's default, e.g. `fragment_first.xml`'s button stack) — views hug together,
  the whole group centered/biased in the remaining space via `layout_constraintVertical_bias`.
- `spread` — views distribute evenly across the available space.
- `spread_inside` — like spread, but the first/last view pin to the chain's outer edges.

**Guidelines** (`androidx.constraintlayout.widget.Guideline`) create an invisible anchor line at a
fixed percent or dp offset — used well in `fragment_read_sentence.xml` and `fragment_account.xml`
to define reusable margins without repeating the same dp value on every view. Prefer a guideline
over duplicating a magic-number margin on 3+ sibling views.

**Barriers** (`androidx.constraintlayout.widget.Barrier`) create a dynamic edge that follows the
largest of several referenced views — use this instead of a fixed guideline/margin whenever text
length is variable (player names, dynamic room state text, localized strings later). This repo
doesn't use barriers yet; reach for one before hardcoding a margin next to variable-length text.

**`wrap_content` + `layout_constraintWidth_max`/`_min` without `0dp`** does not behave like you'd
expect — `wrap_content` sizes to content first, and the max/min only clamp after that. If you want
"as small as content, but never bigger than X," use `0dp` + `Width_max` (see `spinnerObject` in
`fragment_read_sentence.xml` for a correct-ish example, though it's missing a percent width).

## 2. Resource organization — this repo's biggest structural gap

`strings.xml` currently has 3 entries; every other user-facing string in the 21 layouts (and in
Java `setText()` calls) is a hardcoded literal — e.g. `android:text="Finish the prompt"` in
`fragment_write_if.xml`. There is also no `dimens.xml` — every margin/padding/textSize is a literal
`dp`/`sp` value repeated across files (e.g. `56dp` top margin appears standalone in
`fragment_write_if.xml` with no shared source of truth).

Going forward, for **any layout you touch**:
- Move new or edited user-facing text into `res/values/strings.xml` and reference it with
  `@string/...`. Don't do a repo-wide sweep unprompted — migrate opportunistically as you edit a
  screen, since a blanket rename is high-diff, low-value churn on its own.
- Repeated spacing/sizing constants (button height `75dp`, corner radius `15dp`, stroke width
  `6dp`, standard content margin) belong in `res/values/dimens.xml` as `@dimen/...`, not repeated
  literals. The `orange`/`black`/etc. color palette already lives in `colors.xml` — treat dimens
  the same way.
- Shared visual attributes (the `strokeColor="@color/orange"` + `strokeWidth="6dp"` +
  `cornerRadius="15dp"` combo appears on nearly every `MaterialButton` in this app) belong in a
  named style in `themes.xml`/a new `styles.xml`, similar to the existing `AppMaterialButton` and
  `RoomCodeText` styles — not copy-pasted onto every button.

## 3. Accessibility — currently zero coverage, fix as you touch screens

No layout in this repo sets `contentDescription`, and no `TalkBack`/screen-reader path has been
considered. Rules to apply going forward:

- Every `ImageView`/icon-only button (`FloatingActionButton` mic icon in
  `fragment_read_sentence.xml`, the logo `ImageView` in `fragment_first.xml`) needs
  `android:contentDescription`, or `android:importantForAccessibility="no"` if it's purely
  decorative (the background paper textures, e.g. `white_papers`/`lined_papers`, qualify as
  decorative and should get `importantForAccessibility="no"` rather than a description).
- Interactive touch targets should be **at least 48dp × 48dp**. Most buttons here already clear
  that (`75dp` height is common), but double-check anything sized down for a dense row (e.g. the
  `100dp × 50dp` buttons in `fragment_waiting_for_host.xml`/`fragment_leaderboard.xml` — 50dp
  height is borderline; don't shrink further).
- Text sizes must use `sp`, never `dp` — `sp` scales with the user's system font-size setting,
  `dp` does not. **Bug already in this repo:** `fragment_read_sentence.xml` (`pass_reading_turn`
  and `next_frag` buttons) and `message_banner.xml` use `android:textSize="16dp"`. That's a typo,
  not a style choice — fix to `16sp` whenever you're in one of those files.
- Aim for 4.5:1 text/background contrast (the existing `black` text on `white_papers`/light button
  fills is fine; watch for it if new colors are introduced).

## 4. Orientation and window-size support (active project)

`MixedUp/src/main/AndroidManifest.xml` locks `MainActivity` to `android:screenOrientation="sensorLandscape"`.
The app is being adapted to work well in both orientations. Rules for this work:

1. **Design for space, not orientation.** Build one adaptive layout that reflows based on
   available width/height rather than assuming a fixed aspect ratio. Prefer a single
   `ConstraintLayout` file over duplicate `layout-land/`/`layout-port/` variants unless a screen
   genuinely needs a different arrangement. Google's current guidance: don't build
   orientation-specific layouts by default — make the existing UI re-layout well regardless of
   posture.
2. **Never size/position content with orientation-shaped assumptions.** A fixed
   `layout_marginTop="56dp"` tuned for a short landscape viewport (`fragment_write_if.xml`) leaves
   a huge gap on a tall portrait screen and can clip on a very short landscape phone. Prefer
   vertical chains with `layout_constraintVertical_bias`, percent guidelines, or wrapping
   variable-length content in `ScrollView`/`NestedScrollView` so nothing is ever cut off.
3. **Percent+max-width already used here should get a vertical counterpart** (§1) so the same
   content block doesn't stretch absurdly tall in portrait.
4. **RecyclerView grids compute span count from width, not a fixed constant.** Fixed via
   `Utils.computeSpanCount(Context)` in `MixedUp/.../Utils.java`, used by
   `CollectingQuestionsFrag`/`CollectingAnswersFrag`/`WaitingForHostFrag` — divides current screen
   width by a minimum comfortable item width instead of hardcoding `2`. Reuse this helper for any
   new grid; don't reintroduce a literal span count in a `GridLayoutManager` constructor.
5. **Large screens no longer honor the orientation lock.** Apps targeting API 36 (this app now
   does — see README) get `orientation`/`resizability`/aspect-ratio restrictions ignored on
   displays with smallest width ≥ 600dp. `sensorLandscape` is not guaranteed there, so every screen
   must actually work in portrait, not just compile with a lock that mostly hides the problem on
   phones.
6. **Edge-to-edge is mandatory, not optional, at API 36.** `windowOptOutEdgeToEdgeEnforcement` is
   ignored for apps targeting API 36+; content can draw behind the status bar/nav bar/cutouts.
   Top-level containers (`activity_main.xml`, and any screen with content anchored to `parent`
   top/bottom) must consume `WindowInsets` (`ViewCompat.setOnApplyWindowInsetsListener`/
   `WindowInsetsCompat`) and apply that padding themselves.
7. **Never override `Activity.onBackPressed()`.** It's not called for apps targeting API 36+
   (predictive back replaced it) — see `MainActivity.onCreate()` for the
   `OnBackPressedCallback` pattern already in use (a permanently-enabled no-op callback that
   intentionally blocks all back navigation so players can't corrupt an in-progress Firebase room
   by backing out mid-game).
8. **Use window size classes, not orientation checks, for structural decisions** (e.g. leaderboard
   columns, RecyclerView span count) — compact/medium/expanded width, not
   `getResources().getConfiguration().orientation`.
9. **Test matrix before calling UI work done:** phone portrait, phone landscape, and at least one
   large-screen/tablet-sized viewport (rotated both ways). This is a party game people rotate
   mid-session — both orientations are a real, common path, not an edge case.

# Part 2: Java engineering — readability, reliability, scalability, encapsulation, reusability

`API/` is the shared model/policy/ViewModel module; `MixedUp/` is the Fragment/Activity UI module.
The rules below are ranked by how much they matter here, each with a real example from this repo
— not generic advice. The goal isn't a rewrite: apply these when you're already touching a file for
a feature/bug, the same "opportunistic, not a sweep" approach as Part 1 §2.

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

**Call `FirebaseDatabase.getInstance(app).getReference()` exactly once per `FirebaseApp` in these
tests, and reuse that one `DatabaseReference`.** A second `.getReference()` call for the same app
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
