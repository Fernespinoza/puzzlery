# Puzzlery

**An accessible daily logic suite • Powered by S. Tatham's engines** — with playlists, dailies, timers, and a random picker.

This is a fork of [chrisboyle/sgtpuzzles](https://github.com/chrisboyle/sgtpuzzles),
which is [Simon Tatham's Portable Puzzle Collection](https://www.chiark.greenend.org.uk/~sgtatham/puzzles/)
ported to Android.

## Screenshots

| Puzzle list | Daily puzzle | Stats calendar |
|---|---|---|
| ![Puzzle list](fastlane/metadata/android/en-US/images/phoneScreenshots/1-puzzle-list-v4.jpg) | ![Daily puzzle](fastlane/metadata/android/en-US/images/phoneScreenshots/2-daily-dialog-v4.jpg) | ![Stats calendar](fastlane/metadata/android/en-US/images/phoneScreenshots/3-stats-calendar-v4.jpg) |
| Playlists | Puzzle list (night) | Loopy (night) |
| ![Playlists](fastlane/metadata/android/en-US/images/phoneScreenshots/4-playlists-v4.jpg) | ![Puzzle list at night](fastlane/metadata/android/en-US/images/phoneScreenshots/6-puzzle-list-night-v4.jpg) | ![Loopy at night](fastlane/metadata/android/en-US/images/phoneScreenshots/7-loopy-gameplay-night-v4.jpg) |

## Credits and license

All credit for the puzzles themselves goes to Simon Tatham, Chris Boyle, and the
contributors listed in the LICENCE file. This fork is distributed under the same
MIT-style license — see the LICENCE file; copyright notices are preserved.

## What this fork adds

The upstream port deliberately stays close to upstream's design; this fork adds
the things it chose not to:

* A solve timer that starts on your first move and pauses when you leave the
  puzzle, so time away from the game doesn't count. You can hide the timer
  (and the best time) in Settings; both are shown by default.
* Best times per preset, shown next to the timer, with a "New best time for
  this preset!" note when a solve sets one. A best only counts for a clean
  run: fresh start to finish, with no Show solution and no restart.
* Named, ordered playlists with Next-on-solve and shuffle. A run in
  progress can be continued later — every playlist keeps its own boards —
  and can be restarted or reshuffled at any time.
* A date-seeded daily puzzle — everyone gets the same puzzle each day — with
  Easy and Hard versions, streaks, and per-day completion tracking.
  Unfinished attempts can be continued later, you can practice either
  version without affecting the daily record, and Show-solution is hidden
  during dailies.
* A stats page: a monthly calendar of daily results with streaks, plus a
  per-puzzle tab showing your best time on each preset, total solves, and
  total time spent. The per-puzzle picker uses the same Starred list as the
  main screen.
* A random picker that draws from all puzzles or just your favorites
  (long-press a puzzle on the main screen to star it), picking a random
  preset too — including "unsolved only" options for chasing 100%.
* Chooser display modes: names only, names with descriptions, icons only,
  or icons with names. Solo, Pattern, Mines, and Loopy come starred by
  default, and the descriptions note the common names players know: Solo
  aka Sudoku, Pattern aka Nonograms / Picross, Loopy aka Slitherlink.
* Real light and dark themes that follow your system setting, plus an
  optional true-black (AMOLED) night mode.
* The start of TalkBack support: game tiles are named for screen readers
  in icons-only mode, and solving a puzzle announces the completion (and
  the time, when the timer is shown). More lands every release on the
  road to full support in 1.0.

## Current release — v0.5.0

**Backup & progress** — your records are yours, and now they travel:

- Backup & export — every setting and all your history (best times,
  streaks, daily records, starred puzzles) in one JSON file. Import
  replaces everything behind a confirm prompt, and every entry is
  type-checked first, so a damaged or doctored file can't corrupt
  the app
- Completion badges — a ring on each puzzle's tile fills as you
  clear its presets, and turns gold when you've cleared them all
- Stats calendar pages back through past months, not just this one
- Share your solve — a short Wordle-style result text after a win,
  with your time, a ⚡ for a new best, and your streak and Daily
  number on dailies; there's a plain-words version too
- Automatic update check — once a day the app checks GitHub for a
  newer release and offers the download. It's on by default, can be
  turned off in Settings, and is the app's only use of the network
- TalkBack: "Describe board" reads the board out in rows from the
  game menu; puzzles carry support tags in the chooser and Stats so
  you know what you can do before you open them; TalkBack's
  two-finger gestures no longer drag the board around
- Leaving a game takes you back where you launched it — Stats, or
  the Daily screen

See [ROADMAP.md](ROADMAP.md) for what's next.

## About this project

This is my first-ever project. I'm learning Android development by building it,
in collaboration with an AI coding assistant — yes, it's vibe coded, and I'm not
going to pretend otherwise. Feedback, bug reports, and pull requests are welcome.

Coders should read on...

Prerequisites
-------------

Some knowledge of Android Development and/or C, and ideally JNI, depending
on what you want to work on: the puzzles themselves are in C, most
Android-specific code is in Kotlin, and JNI (Java Native Interface) is the
API bridge between the two.

If you have the option, a Linux-based development machine, as I haven't
tried to build on Windows or OSX, and it will need some tweaks.

[Android Studio](https://developer.android.com/studio)

[Android NDK (Native Development Kit)](https://developer.android.com/ndk)

If using gradle directly, create `local.properties` with `sdk.dir=/your_path_here`
(I think Android Studio writes this automatically)

I probably missed a few things here. File a bug when you find them. :-)

Getting/configuring the source
------------------------------

This is a fork of https://github.com/chrisboyle/sgtpuzzles - you can clone/download
it from there if you want the unmodified upstream port, or clone this repo for
the fork. A fork means you can easily send me a "pull request" of your change,
and I can review and integrate it, all within github.

You'll also find a branch called `upstream`, which is Simon's code whenever
I last synced. Handy for diffs, to see what I broke on Android. :-)

This repository includes old `git-svn` commits that predate upstream's move
to git. History will look slightly nicer if, after cloning, you do:

    git fetch origin 'refs/replace/*:refs/replace/*'

Sadly github itself does not appear to support replacement so you will still
see duplicates there.

You should now be able to edit, build and launch the app like any other Android
project (except a lot of it is in C). Don't forget that you'll be signing with
a dev key, so to test on a device that already has the Google Play version,
uninstall that first.

Architecture / where to find stuff
----------------------------------

Simon has some excellent [developer documentation](https://www.chiark.greenend.org.uk/~sgtatham/puzzles/devel/)
which is definitely worth reading first, at least the Introduction.

The Android front-end (`android.c`) is basically just glue, passing everything to
Kotlin. The Kotlin classes providing the UI layer, game chooser, etc. are in
`app/src/main/kotlin`.  The main class `GamePlay` has the native methods that
`android.c` implements. The game area on screen is a `GameView`, which basically
just has a bitmap for the puzzle to draw on. Note that almost no native code is
run until the user has chosen a game (unless there's a previous game to resume).

Files for other platforms etc. that are not currently usable in this fork are
in the `not-in-use` directory.

If your change is relevant to other platforms, you should definitely ping Simon
about it as well as me, but test on at least one other platform first in a
separate checkout (see above).

Happy hacking!

--
Puzzlery
