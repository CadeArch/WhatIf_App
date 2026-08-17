---
name: release-notes
description: Produce Play Store release notes for this app and keep the "what shipped when" boundary exact. Covers finding the last released commit (a v* git tag, falling back to the previous versionName bump), gathering the merges and CHANGELOG archives since it, translating engineering bullets into user-facing copy inside the Play Console's 500-character limit, and tagging the release afterwards so the next boundary needs no reconstruction. Load this when Cade asks for release notes, asks what has changed since the last release, or bumps the version for a Play Console upload.
---

# Release notes

## The tracking mechanism: a `v<versionName>` tag per release

**The release boundary lives in git tags, not in memory, a file, or this session's context.**
Anything else drifts the moment a branch lands after a version bump but before the AAB is actually
built - which has already happened here: PR #19 merged *after* the commit that bumped to 2.0.5, so
nothing in the repo could say whether it shipped in 2.0.5 or 2.0.6.

So the rule is:

- **When Cade confirms a build was uploaded to the Play Console, tag that commit** `v<versionName>`
  (e.g. `v2.0.6`) and push the tag: `git tag v2.0.6 <sha> && git push origin v2.0.6`.
- Tag when the upload actually happens, **not** when the version is bumped. The bump commit is not
  the release - work can and does land between the two.
- Never guess a boundary and tag it silently. If no tag exists for the previous release, say so and
  name the specific ambiguity (see the fallback below) rather than presenting a reconstructed range
  as fact.

Two old tags (`BaseGameFunctional`, `BaseGameLooping`) predate this convention and are milestones,
not releases. Only `v*` tags count.

## Producing the notes

1. **Find the boundary.**
   ```bash
   git tag -l 'v*' --sort=-v:refname | head -1        # the last release
   git log <tag>..HEAD --merges --oneline             # what has landed since
   ```
   **Fallback when no `v*` tag exists** (true for everything up to 2.0.6): find the commit that set
   the *previous* versionName and use it as an approximate boundary -
   `git log -G'versionName "' --oneline -- MixedUp/build.gradle`. Flag it as approximate and name
   what might be on the wrong side of it.

2. **Gather the substance from `CHANGELOG.md`, not from commit subjects.** Each merged branch has an
   `## Archived Session: <branch-name>` section whose `- **Bold lead**` bullets are the real
   summary. The commit subjects are too terse and the diffs are too much.

3. **Translate to user-facing copy.** The changelog is written for engineers - it names classes,
   methods and root causes. Release notes are for players:
   - Say what the player can now do or no longer suffers, never the mechanism. "The mic no longer
     swallows taps", not "`QUEUE_FLUSH` cancelled the in-flight utterance".
   - Group by what a player would notice, not by branch or PR.
   - Skip anything invisible to a player: refactors, test suites, tooling, skill/doc updates,
     `CLAUDE.md` edits. They belong in the changelog, not the store.
   - Bug fixes are worth listing when the bug was one players actually hit - "games no longer end
     when someone's phone drops out" is a headline, not a footnote.

4. **Respect the format.** The Play Console "What's new" field caps at **500 characters per
   language** - confirmed against the Console, which rejects anything longer with "Release note for
   en-US is too long".
   - **Measure it, do not estimate it:** `printf '%s' "$(cat notes.txt)" | wc -m`. An eyeballed
     count has already been wrong here.
   - **Aim for ~430, not 495.** Cade edits the copy, and a note that only just fits goes over the
     moment a word changes.
   - Plain hyphens, not em-dashes or smart quotes. The Console counts characters, and non-ASCII
     punctuation is a common reason a note is longer than expected.
   - Plain sentences in two or three short paragraphs read better in that box than a wall of
     bullets.
   - If a longer version is offered for use elsewhere, **say explicitly that it will not fit the
     Console field** - offering both without that warning has already led to the long one being
     pasted in and rejected.

5. **Offer to tag.** Finish by offering the exact tag command for the commit being released, so the
   next set of notes needs no reconstruction at all.

## What actually goes in a release

Everything merged to `master` between the boundary and the build. This repo lands work through PRs
(`git-pr-workflow`), so `--merges` gives a clean list; `--no-merges` on a squash-free history just
repeats the same work as individual commits.
