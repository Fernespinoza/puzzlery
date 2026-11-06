# Puzzlery — Roadmap

Where the app is headed. Nothing here is a promise, just the plan.

## Shipped in v0.5.0: Backup & progress
- **Backup & export** — one JSON file holding all of it: every
  setting (AMOLED, timer/best-time toggles, chooser style, night
  mode, everything) plus all personal data and history (best times,
  solve counts, streaks, daily records, attempts, starred set).
  Export it and import it back, so progress survives a new phone or
  a reinstall. The file carries a `schema_version` so an older backup
  can never break a newer app as the format evolves. This release's
  export covers exactly the v0.4.0 state set — the schema locks
  before any release adds new tracked state of its own (favorites
  arrive in v0.7.0 and join the export and its type table then). Importing a backup
  replaces the app's current state wholesale, behind a confirm
  prompt — never a merge. Standing rule, alongside the TalkBack
  one: any release that adds tracked state must extend the export,
  and add its keys to Backup's type table (`expectedTag`) when the
  code reads them with a typed getter; `schema_version` bumps only
  when a key's type or the file format itself changes.
- **Completion badges** — a progress badge per puzzle: a check on a
  dark disc, its ring filling clockwise as you legitimately solve
  the puzzle's top-level presets, turning solid gold when every
  preset is solved, so the 100% chase is visible from the home
  screen. It sits on the icon in the grid chooser modes and beside
  the star toggle in the list modes, can be turned off in Settings,
  and TalkBack speaks the progress ("3 of 5 presets solved" /
  "cleared"). (Cleared = every top-level preset has a legitimate
  best time recorded — the same clean-run standard used everywhere
  else; custom games don't count toward it.)
- **Past-month calendar viewing** — the stats calendar gains month
  navigation, so any past month can be viewed read-only. Completion
  records are kept forever, so past months render in full; attempt
  boards from past months are already cleaned up monthly, so a
  past month can only ever show solved-or-untouched — view-only
  by construction, nothing resumable or playable. (Added
  2026-10-30, after the backup work it rides on.)
- **TalkBack, Phase 1** — "describe board": a read-only text dump of
  the puzzle state for screen readers, captured by swapping in a
  recording frontend, forcing one redraw through it, and formatting
  the drawn texts back onto their lattice. An orientation aid, not
  a play method — actually playing via TalkBack is Phase 2's grid
  navigation. A down payment on the 1.0 milestone, shipped early.
- **TalkBack support tags** (Fernando, 2026-10-31) — as each
  puzzle gains TalkBack support it gets tagged with its level, so
  players can see before opening a game what they can do with it:
  shown in the chooser's description text, spoken in each tile's
  TalkBack label, and noted on the puzzle's Stats tab. Levels are
  per-puzzle and evidence-based — a game is only tagged once its
  describe output has been verified on device (full text board,
  clues only, or none), and the tag upgrades to full support as
  the Phase 2 tranches land. Standing rule for every later
  TalkBack tranche: shipping support for a puzzle includes
  updating its tag.
- **Share-my-solve** (pulled forward from v0.6.0 on 2026-10-31,
  Fernando's call) — a short Wordle-style result text after a
  solve, in two variants: emoji (⏱️ time, ⚡ on a new best, 🔥
  streak and a Daily #N on dailies — the number counts from
  v0.4.0's release day) and a plain-words variant for contexts
  where emoji read badly. Full spec: share-my-solve-spec.md.
- **Automatic update check** (added 2026-10-31, Fernando: "should
  have been one of the first things") — the app checks GitHub
  once a day for a newer release and offers the download; on by
  default, with a Settings toggle and a manual check. The check
  is the app's only network use.
- **Also shipping** (rolled in from the planned v0.4.1 patch —
  small fixes ride the next feature release rather than spending
  a release of their own): day-mode chooser highlight no longer
  washes out the current game's name; playlist editor shows each
  puzzle's icon next to its name.

## Planned for v0.6.0: Better Help
_(Build order inside this release, settled in the 2026-11-05
grilling session: Solo's TalkBack grid first, then the first-run
hint and the first guide tranche, then pinned clues and the
Pattern mode bar, and Bookmarks last.)_
_(Release order reshuffled 2026-11-03, Fernando's call: the original
v0.8.0 headline (Better Help) leads at v0.6.0, preset progression moves
to v0.7.0, and the home dashboard to v0.8.0. The TalkBack tranches stay
in their version slots — only the headline content moved.)_

- **Better Help** — the current Help is the upstream rules text: it
  tells you the rules, but not how to approach a puzzle. Each puzzle
  gets a short strategy guide — ideas and techniques for how to solve
  it (what to look for first, how to get started). Static guidance,
  not a live hint engine: a live "next move" hint would need a solver
  per puzzle and stays a longer-term research item, not this release.
  Guides ship in tranches — the four starred puzzles (Solo, Pattern,
  Mines, Loopy) first, the rest following in later releases.
  Every guide uses one fixed shape — First moves / Key patterns /
  Common traps, roughly 200–300 words, for a player who knows the
  rules but not the approach — and ships only after Fernando has
  read and approved it. Tranches after the starred four follow the
  TalkBack tranche order: a puzzle gets its guide when or after it
  becomes fully playable.
- **Bookmarks** — the Slitherlink checkpoint move (Fernando,
  2026-10-31): save the current board state mid-solve, try a route,
  and return to the bookmark if it dead-ends. One bookmark per game
  session, held in memory only (a stored bookmark from a different
  board of the same size would swap the whole puzzle on restore —
  session scope makes that impossible). Two game-menu items: Set
  bookmark / Go to bookmark (the latter disabled until one is set).
  Does NOT disqualify a solve (Fernando's call): a bookmark can
  only return to a state the player genuinely reached by playing
  this board — no solver, no foreign states. Honest caveat (added
  after external review, 2026-10-31): it is strictly stronger than
  Undo in one way — after undoing PAST the bookmark and taking a
  different branch, Undo alone could never get back, but the
  bookmark still can. It is a save-point. Restoring is one-way —
  Undo won't resurrect the branch left behind. Keep the bookmark
  state out of the Activity so rotation doesn't lose it.
  Build-time checks: the restore path must not trip the restart
  flag or reset the solve timer; if it can't avoid those, rescope
  before building.
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
- **Pattern mode bar** (Fernando, 2026-11-04) — a Pattern-only
  bar above the app's bottom bar: Fill / Mark / Erase / Pan, plus
  Undo and Redo, in Puzzlery's own icons. The mode decides what a
  touch means (no more tap-versus-long-press guessing), dragging
  paints continuously in the current mode, and Pan moves the
  board without painting — the natural partner to pinned clues.
  The concept is borrowed from Katana Nonograms; the artwork and
  implementation are ours. Mode bars are decided puzzle by
  puzzle, never as a blanket rule: Pattern earns one because of
  the cluster of board features it carries; Mines (Reveal/Flag)
  is a candidate, not a commitment.
- **TalkBack validation & labels** (2026-11-05) — support levels
  are public and honest: every puzzle is labelled Full play /
  Describe only / Not yet, in its Help and spoken in the chooser.
  A tranche is *implemented* when it ships but only *complete*
  after a TalkBack-first player has validated it — until then the
  label says so ("Full play (untested)"). Validation runs on
  single-puzzle tester builds that open straight into the puzzle
  being tested, recruited per tranche from Fernando's ETLB circle
  and public Reddit posts.
- **TalkBack, Phase 2 begins** — Solo gets a virtual-view grid
  (`ExploreByTouchHelper`): full move entry via TalkBack for one
  puzzle, the pattern the following tranches follow.


## Planned for v0.7.0: Preset progression
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
  first; this release's favorites are new tracked state, so they join
  the backup export and its type table when they land.)
- **TalkBack, Phase 2** — the grid treatment spreads to the next
  tranche of cell-based puzzles (Pattern, Mines).


## Planned for v0.8.0: Home dashboard
- **Home dashboard** — opening the app lands on a dashboard instead
  of the full games list: a Continue card for your last played game
  (labeled by context — playlist, daily, or general), today's daily
  with Easy / Hard status and your streak, shortcuts to your most
  recent puzzles, playlists (runs in progress offer Continue), and
  a quick-stats strip (total solves, time played, puzzles cleared).
  The full chooser stays one tap away via an All games entry, and
  the bottom bar is unchanged. Needs one new piece of data — a
  short recents list; everything else already exists.
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
- **Phase 1** — "describe board" (shipped in v0.5.0): a text dump
  of the puzzle state for TalkBack to read. Read-only; built by
  capturing one full redraw through a recording front-end and
  formatting the drawn text as spoken rows, with per-puzzle
  support tags recording what the capture can honestly read.
- **Phase 2** — virtual-view grids (`ExploreByTouchHelper`): full
  move entry via TalkBack, shipped in per-release tranches so it
  never lands as one lump — Solo in v0.6.0, then Pattern and Mines
  (v0.7.0), Loopy and Keen (v0.8.0), and the remaining tractable
  grid puzzles in v0.9.0. Order: cell-based grid games first;
  pointer-path games are out of scope (below).

**Phase 2 implementation order** (published so players can see
what's coming; grouped by how a puzzle is played, so each group
reuses the machinery the group before it proved — a player
asking for a puzzle by name can pull it forward):
- **v0.6.0** — Solo (cell entry: digits and pencil marks).
- **v0.7.0** — Pattern, Mines (cell grids with marks and flags).
- **v0.8.0** — Loopy, Keen (edge drawing; digits with cages).
- **v0.9.0, cell-entry grids** — Unequal, Unruly, Singles,
  Magnets, Light Up, Tents, Towers, Slant, Filling, Mosaic,
  Range, Undead.
- **v0.9.0, edge & path grids** — Bridges, Galaxies, Palisade,
  Pearl, Tracks.
- **v0.9.0, tap-action boards** — Black Box, Dominosa, Flip,
  Flood, Guess, Map, Same Game.
- **v0.9.0, sliders, rotators & two-tap drags** — Cube, Fifteen,
  Inertia, Net, Netslide, Pegs, Rectangles, Signpost, Sixteen,
  Twiddle.
- **Research at 1.0** — Untangle (free dragging; see the
  milestone note below).

**v1.0 milestone:** full TalkBack support for the tractable set —
Solo plus the cell-based grid puzzles — with both phases complete,
and nothing else in the release. Pointer-path puzzles (Untangle
and its kin) are excluded from the 1.0 set by name — but not
parked blind: at 1.0 they get a genuine research pass for a
reasonable non-visual adaptation, and only a puzzle with no
reasonable route stays parked (a player asking for one by name
reopens it). Real screen-reader support is
something most puzzle apps don't have — that's when this becomes
a 1.0.

## Beyond 1.0
- **Cadence** — the weekly build phase ends at 1.0: a monthly
  rhythm, with anything that does come up still shipping on a
  Friday for consistency. Housekeeping that arms at 1.0: a
  Friday check for work sitting unpushed in the repo (silent
  when clean), and deliberate reviews of upstream (Tatham /
  Boyle) when their code changes — merges always need a build
  and Fernando's go-ahead.
- **Puzzlery on the web (the 2.0 direction)** — the same suite
  in a browser, playable on PC with all the same features. The
  Android TalkBack work is the design rehearsal: on the web the
  parallel accessible structure is a semantic (ARIA) grid that
  JAWS and NVDA can walk, and the Describe-board text ports
  almost word for word. Android development slows at 1.0
  because the target moves here, not because the project stops.
- **Spanish localization** — UI and TalkBack labels translated
  (Fernando reviews; he's the native speaker). Strategy guides
  stay English until there's visible demand.
- **Google Play — parked** (2026-11-05, superseding the
  2026-10-29 conditional plan): Puzzlery is a passion project —
  free, no accounts, no ads, no analytics, permanently — and
  Play's discoverability alone doesn't earn its gates ($25
  account, the 12-tester / 14-day closed test, a second
  re-signed update lane). It reopens only on demand: players
  asking for a Play listing, or Fernando reopening it.

## Update log
- **v0.5.0** (Nov 6, 2026) — Backup & progress: full backup &
  export with type-checked import, completion badges (chooser
  tiles and the Stats puzzle picker), past-month Stats calendar,
  share-my-solve, the automatic update check (the app's first
  network use, for that check only), TalkBack Phase 1 Describe
  board with per-puzzle support tags, and return-to-origin
  navigation.
- **The grilling session** (Nov 5, 2026) — Puzzlery's direction
  settled in one pass (22 decisions): accessibility first with
  the tranche as every release's ship gate; implemented is not
  complete — TalkBack tranches are validated by TalkBack-first
  testers on single-puzzle builds; nested presets stay out of
  the completion math but appear in Stats and favorites; the
  Phase 2 order published in full; post-1.0 monthly cadence,
  a web version as the 2.0 direction, Spanish localization,
  and Google Play parked unless players demand it.
- **Roadmap reshuffle** (Nov 3, 2026) — Fernando reordered the
  post-0.5.0 releases: Better Help (strategy guides, Bookmarks,
  pinned clues for Pattern) now leads at v0.6.0, preset progression
  (the smart chooser) moves to v0.7.0, and the home dashboard to
  v0.8.0. TalkBack tranches stay in their version slots.
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
