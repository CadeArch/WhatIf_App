---
name: whatif-android-ui
description: Android UI/XML reference for this repo's layouts (MixedUp/src/main/res/layout/*.xml) - ConstraintLayout sizing/positioning rules, the "never let a view touch the screen edge" rule, percent+max-width patterns, chains/guidelines/barriers, GONE-collapse pitfalls, background-asset alignment and tiling, string/dimen/style resource organization, accessibility (contentDescription, 48dp targets, sp not dp), and orientation/window-size support. Load this BEFORE editing any layout XML, adding or restyling a view, changing visibility of a view other views are constrained to, touching a drawable/background, or doing portrait/landscape/tablet work.
---

# WhatIf App - Android UI/XML

Load this whenever you are about to touch a layout. The rules below come from bugs that actually
shipped in this repo, not style preferences - several of them (edge-touching views, GONE-collapse,
`16dp` text sizes) were found live after being written the "obvious" way first.


All 21 layouts here already use `ConstraintLayout` as the root, which is the right foundation.
The sections below are how to use it correctly, not a suggestion to switch frameworks.

## 1. ConstraintLayout fundamentals — how to size and position views

**The 0dp rule.** Never use `match_parent` on a direct child of `ConstraintLayout`. Set
`layout_width`/`layout_height="0dp"` plus start/end (or top/bottom) constraints instead — this is
"match constraint" mode, the ConstraintLayout equivalent of `match_parent` that still respects
sibling constraints, margins, and percent/max-width modifiers. `activity_main.xml`'s
`fragment_container` does this correctly (`0dp` + four constraints).

**Never let a view touch the physical screen edge — every constraint straight to a `parent` edge
needs a non-zero inset.** This is a real, repeatedly-hit bug class in this repo, not a style
preference: `fragment_waiting_for_host.xml`'s `textView2` used raw `android:layout_width=
"match_parent"` with zero padding, so its programmatic status text ("Waiting for host. Players can
still join.") rendered flush against both screen edges; a `Widget.WhatIf.Button.Secondary`
"Sign Out" button pinned `top_toTopOf="parent"` / `end_toEndOf="parent"` with no margin at all sat
literally in the pixel corner. Both shipped before being caught live. The rule, concretely:
- **0dp-width views bounded by `parent` on a side**: give that side real inset — either
  `layout_constraintWidth_percent`/`_max` (the established pattern for headline text and primary
  buttons throughout this repo — the percent gap *is* the inset, no extra margin needed), or an
  explicit `paddingStart`/`paddingEnd`/margin if the view must stay full-bleed width for other
  reasons (`fragment_leaderboard.xml`'s title needs `paddingStart="@dimen/space_16"` for exactly
  this reason — it's 0dp + `start_toStartOf="parent"` with no percent constraint).
- **Fixed-size views pinned to a `parent` corner/edge** (utility buttons like "back", "Sign Out",
  "start"): always carry `layout_marginTop`/`marginEnd`/etc. on every side touching `parent` —
  `@dimen/space_16` is the minimum before you should even consider a value; see `back_account` in
  `fragment_account.xml` or `waitingForHost_start` in `fragment_waiting_for_host.xml` for the
  established pattern to copy.
- This does **not** apply to a view bounded by a guideline or a sibling (e.g. the read-sentence
  controls row bounded by `paper_text_start`/`paper_text_end` guidelines) — those are already inset
  by construction. It only applies where the constraint target is literally `parent`.

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

**Background assets that depict a real-world layout convention must have their foreground content
actually respect that convention, and tiled assets must never duplicate a one-off design element.**
Caught live: `lined_paper_read.png` (used by `ReadSentenceFrag`) draws a single red notebook-margin
line baked into the image at a fixed position (~35% of a portrait phone's width) — but the
screen's own content guideline (`paper_text_start`) was independently set to 13%, so text started
well to the *left* of the margin line, floating across it instead of beginning at it the way real
handwriting starts right at a notebook's red line. Fixed by measuring the line's actual on-screen
position and setting the guideline to match. **Rule**: when a background is a recognizable
real-world object with an implied layout (ruled paper, a form, a ticket stub), don't pick content
guideline positions independently of the asset — measure where the asset's own feature actually
sits and align to it.
Separately, that same drawable was tiling with plain `android:tileMode="repeat"`, which repeats on
*both* axes — on a wide/landscape viewport wider than the source image, that would draw the red
line a second time, which a real single sheet of paper never does. **Rule**: any tiled background
with a distinctive one-off feature (a margin line, a logo mark, anything that shouldn't visually
repeat) must set `tileModeX`/`tileModeY` independently and disable tiling on whichever axis would
duplicate that feature — never leave a bare `tileMode="repeat"` on an asset like this unexamined.

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

