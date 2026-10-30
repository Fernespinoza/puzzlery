# Puzzlery — Roadmap

Where the app is headed. Nothing here is a promise, just the plan.

## Planned for v0.5.0: Backup & progress
- **Backup & export** — one JSON file holding all of it: every
  setting (AMOLED, timer/best-time toggles, chooser style, night
  mode, everything) plus all personal data and history (best times,
  solve counts, streaks, daily records, attempts, starred set).
  Export it and import it back, so progress survives a new phone or
  a reinstall. The file carries a `schema_version` so an older backup
  can never break a newer app as the format evolves. This release's
  export covers exactly the v0.4.0 state set — the schema locks
  before any release adds new tracked state of its own (favorites
  arrive in v0.6.0 and bump the schema then). Importing a backup
  replaces the app's current state wholesale, behind a confirm
  prompt — never a merge. Standing rule, alongside the TalkBack
  one: any release that adds tracked state must extend this export
  and bump `schema_version` in the same release.
- **Completion badges** — chooser tiles show at a glance which
  puzzles you've cleared, so the 100% chase is visible from the
  home screen. (Cleared = every top-level preset has a legitimate
  best time recorded — the same clean-run standard used everywhere
  else; custom games don't count toward it.)
- **TalkBack, Phase 1** — "describe board": a read-only text dump of
  the puzzle state for screen readers, from the engine's text-format
  hook where a puzzle supports it (not all do yet), with a fallback
  for the rest. An orientation aid, not a play method — actually
  playing via TalkBack is Phase 2's grid navigation. A down payment
  on the 1.0 milestone, shipped early.
- **Also shipping** (rolled in from the planned v0.4.1 patch —
  small fixes ride the next feature release rather than spending
  a release of their own; the roll-in list is frozen at these
  two): day-mode chooser highlight no longer washes out the
  current game's name; playlist editor shows each puzzle's icon
  next to its name.

## Planned for v0.6.0: Preset progression
- **Favorite presets & preset progression** — long-press a preset
  in the preset picker to star it (the star shows in the picker
  and beside that preset in Stats). Random → "From favorites" then
  prefers them: when the puzzle it picks has favorite presets, it
  deals one of those instead of any preset (the unsolved favorites
  variant draws from favorite presets still unsolved; if every
  favorite is already solved, it falls back to any preset of that
  puzzle). Solved
  presets get a check mark in the picker too (solved = a legit
  best time is recorded, the same standard Stats and the unsolved
  random pools use). And tapping a puzzle in the chooser deals its
  next unsolved preset, scanning the preset list in picker order
  (custom games sit outside progression — they aren't presets) — an
  in-progress board still resumes first,
  and a fully solved puzzle deals a random preset. A Settings
  toggle ("Preset progression", default on) switches the chooser
  deal off entirely: taps then work as they always have — resume,
  or a fresh board at the default preset. (Split out of v0.5.0 on
  2026-10-29 so the backup schema locks against existing state
  first; this release's favorites are new tracked state and bump
  the backup `schema_version` when they land.)
- **Share-my-solve** — a short Wordle-style result text after a solve
  (puzzle, mode, time, streak) to paste anywhere. Format specced by
  Fernando 2026-10-29: header with Daily #N on dailies, puzzle •
  size, then ⏱️ time with ⚡ on a new best and 🔥 streak on
  dailies; full spec + scenarios in the project files
  (share-my-solve-spec.md). (Moved here from Better Help on
  2026-10-29 — it's a results feature, not a help feature.)
- **TalkBack, Phase 2 begins** — Solo gets a virtual-view grid
  (`ExploreByTouchHelper`): full move entry via TalkBack for one
  puzzle, the pattern the following tranches follow.

## Planned for v0.7.0: Home dashboard
- **Home dashboard** — opening the app lands on a dashboard instead
  of the full games list: a Continue card for your last played game
  (labeled by context — playlist, daily, or general), today's daily
  with Easy / Hard status and your streak, shortcuts to your most
  recent puzzles, playlists (runs in progress offer Continue), and
  a quick-stats strip (total solves, time played, puzzles cleared).
  The full chooser stays one tap away via an All games entry, and
  the bottom bar is unchanged. Needs one new piece of data — a
  short recents list; everything else already exists.
- **TalkBack, Phase 2** — the grid treatment spreads to the next
  tranche of cell-based puzzles (Pattern, Mines).

## Planned for v0.8.0: Better Help
- **Better Help** — the current Help is the upstream rules text: it
  tells you the rules, but not how to approach a puzzle. Each puzzle
  gets a short strategy guide — ideas and techniques for how to solve
  it (what to look for first, how to get started). Static guidance,
  not a live hint engine: a live "next move" hint would need a solver
  per puzzle and stays a longer-term research item, not this release.
  Guides ship in tranches — the four starred puzzles (Solo, Pattern,
  Mines, Loopy) first, the rest following in later releases.
- **First-run hint** — a short coachmark on first open explaining the
  starred section and the aka tags, so new players get the idea
  immediately.
- **Pinned clues for Pattern** — when a Pattern board is zoomed or
  panned, the row and column clues stay pinned at the edges of the
  game window, swapping to whichever rows and columns are in view
  — the way Katana Nonograms does it (Fernando's reference app;
  he shared a video of it on 2026-10-29 after losing the clues on
  a big daily board). Strictly inside the game window: the app's
  bottom bar stays visible and uncovered, like Katana's own
  toolbar. Builds on the board view's existing pinch zoom and
  pan; the work is exposing Pattern's clue-margin sizes to the
  Android side (they're computed inside the engine today) and
  re-drawing the two strips pinned on every board move. Pattern
  first — Towers and Tents have edge clues too and can follow the
  same mechanism later. (Bundled into this release 2026-10-29:
  the guides are cheap to produce, so this release carries the
  one real frontend feature instead of it taking its own slot.)
- **TalkBack, Phase 2** — next tranche of grid puzzles (Loopy, Keen).

## Planned for v0.9.0: Home-screen widget
- **Widget** — two actions from the home screen: quick-play a random
  game, and open the daily picker. No need to open the app and hunt
  for them first.
- **TalkBack, Phase 2** — the remaining tractable grid puzzles, so
  1.0 is the finish line rather than the whole project.

## v1.0 milestone: TalkBack / accessibility
Built incrementally: every release from v0.4.0 on ships a named
TalkBack piece, so 1.0 is the last piece rather than the whole
project. The phases:
- **Phase 1** — "describe board" (planned for v0.5.0): a text dump
  of the puzzle state for TalkBack to read. Read-only; uses the
  engine's text-format hook where a puzzle supports it, with a
  fallback for the rest.
- **Phase 2** — virtual-view grids (`ExploreByTouchHelper`): full
  move entry via TalkBack, shipped in per-release tranches so it
  never lands as one lump — Solo in v0.6.0, then Pattern and Mines
  (v0.7.0), Loopy and Keen (v0.8.0), and the remaining tractable
  grid puzzles in v0.9.0. Order: cell-based grid games first;
  pointer-path games are out of scope (below).

**v1.0 milestone:** full TalkBack support for the tractable set —
Solo plus the cell-based grid puzzles — with both phases complete,
and nothing else in the release. Pointer-path puzzles (Untangle
and its kin) are excluded by name: they may never be fully playable
non-visually, and that's fine. Real screen-reader support is
something most puzzle apps don't have — that's when this becomes
a 1.0.

## Beyond 1.0 (conditional)
- **Google Play** — once releases slow down from weekly to
  occasional, put Puzzlery on Play for discoverability (decided
  2026-10-29; supersedes the old "not planned" stance). Known
  gates: $25 account, and the 12-tester / 14-day closed test
  new accounts must run first. Play and GitHub become separate
  update lanes; both keep working.

## Update log
- **v0.4.0** (Oct 30, 2026) — the UI overhaul, and the app is now
  **Puzzlery**. Daily puzzle redesigned: Easy / Hard cards with
  streaks, resumable attempts and Practice; Easy is the simplest
  preset on the list; "Show solution" hidden in daily mode. Timer
  options (display-only toggles) with per-preset best times for
  clean runs. Stats page: monthly daily calendar plus a Per-puzzle
  tab. Playlists with Continue — every playlist keeps its own
  boards — plus Restart and Shuffle. Chooser display modes; real
  light and dark themes with an optional true-black (AMOLED)
  night mode. TalkBack foundation: named tiles in icons-only mode
  and a spoken completion announcement that respects the timer
  toggle. Two external code reviews (Claude, Gemini) worked
  through and fixed.
- **v0.2.0** — full 40-puzzle restore; "Show solution" completion fix;
  bottom-bar fixes; roadmap created.
- **v0.3.0** — star toggle on the Show descriptions chooser.
