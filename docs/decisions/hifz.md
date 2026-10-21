# Hifz

## Current state
- `:feature:hifz` owns `HifzDatabase` (Room, file `hifz.db`, version 1, schemas in `feature/hifz/schemas`, `HifzMigrationTest`): `plan` (one row: method, lesson size, Manzil cycle, recent days, ayahs a day, revision page limit, cursor ayah, print layout, start day), `page_progress` (status 0..3, learned_on, last_revised_on, FSRS columns), `session` (finished parts of a day with pages and grade), `mistake` (ayah, kind, time). Days are epoch days.
- FSRS lives in `:core:data` (`core.data.fsrs`), shared with `:feature:learn`. Pages use `PageProgressRules.dailyScheduler()` (no minute steps). A page is Solid from 21 days of stability; Forgot sends a page back to Learning.
- Methods are pure Kotlin in `feature.hifz.method`. `AssignmentGenerator.generate` builds a day from the plan, the page records and the parts finished today, and keeps nothing from earlier days. Sabaq, Sabqi, Manzil: Sabqi is pages taught in the last `recentDays` (not today), Manzil is the old pages ordered oldest revised first, `ceil(old pages / cycle)` a day; both share `revisionPageLimit`, Sabqi first. 3 by 3, Ayah chain and Fixed ayahs give a lesson of N ayahs and revise pages whose memory is due. No method gives nothing.
- Lessons (`LessonPlanner`) follow the cursor: a page lesson is the ayahs that start on the page, a half page is half of them (first half rounded up), ayah lessons may cross pages; known pages are skipped. A Forgot grade keeps the cursor so the lesson returns. A page is Memorised when its last ayah is inside a graded lesson, else Learning.
- Pages follow the user's print: `PageLayoutFamily` (Madani 1421 for QPC Hafs, V2 and tajweed; Madani 1405 for V1; IndoPak has no layout data and uses 1421). `PageTable` is built from `QuranTextRepository.ayahsOnPage`; the plan stores its family and `HifzProgress.remap` moves records when the print changes family (new page takes the record of the old page that holds its first ayah).

## Open items
- IndoPak 15/16-line pages: add a family when the layout data exists.
- Minutes per part are rough constants (`PartMinutes`).

## Gotchas
- A forgotten page needs a card to be promoted again by a Good revision; half lessons have no card on purpose.

## Tried / rejected
- Carrying overdue pages to the next day: it piles up and users quit when revision piles up.
