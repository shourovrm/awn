# Hifz

## Current state
- `:feature:hifz` owns `HifzDatabase` (Room, file `hifz.db`, version 1, schemas in `feature/hifz/schemas`, `HifzMigrationTest`): `plan` (one row: method, lesson size, Manzil cycle, recent days, ayahs a day, revision page limit, cursor ayah, print layout, start day), `page_progress` (status 0..3, learned_on, last_revised_on, FSRS columns), `session` (finished parts of a day with pages and grade), `mistake` (ayah, kind, time). Days are epoch days.
- FSRS lives in `:core:data` (`core.data.fsrs`), shared with `:feature:learn`. Pages use `PageProgressRules.dailyScheduler()` (no minute steps). A page is Solid from 21 days of stability; Forgot sends a page back to Learning.
- Methods are pure Kotlin in `feature.hifz.method`. `AssignmentGenerator.generate` builds a day from the plan, the page records and the parts finished today, and keeps nothing from earlier days. Sabaq, Sabqi, Manzil: Sabqi is pages taught in the last `recentDays` (not today), Manzil is the old pages ordered oldest revised first, `ceil(old pages / cycle)` a day; both share `revisionPageLimit`, Sabqi first. 3 by 3, Ayah chain and Fixed ayahs give a lesson of N ayahs and revise pages whose memory is due. No method gives nothing.
- Lessons (`LessonPlanner`) follow the cursor: a page lesson is the ayahs that start on the page, a half page is half of them (first half rounded up), ayah lessons may cross pages; known pages are skipped. A Forgot grade keeps the cursor so the lesson returns. A page is Memorised when its last ayah is inside a graded lesson, else Learning.
- Pages follow the user's print: `PageLayoutFamily` (Madani 1421 for QPC Hafs, V2 and tajweed; Madani 1405 for V1; IndoPak has no layout data and uses 1421). `PageTable` is built from `QuranTextRepository.ayahsOnPage`; the plan stores its family and `HifzProgress.remap` moves records when the print changes family (new page takes the record of the old page that holds its first ayah).
- `HifzTab(controller, quranText, onStartPractice, onOpenAyah, showTopBar, topBarActions)` is the tab (own top bar by default). Pages: main (Method link row, Today rows with minutes or Done, revision load bar `N of LIMIT pages`, one primary button for the next undone part, progress map of one row per juz and four colours taken from the mode's field palette, Mark what I know, Weak spots), method chooser (also opened from the Method row, with the method's settings and Revision limit), Mark what I know (by surah, juz or pages, then a state). `rememberHifzTodayLine(controller)` gives the one line for the Quran tab ("Sabaq page 24, revise 4 pages", or Done; null without a plan or with No method). `HifzController` holds `screenState`, `practice`, `startPractice(kind)`, `startNextPart()`, `finishPractice(grade)`.
- Weak spots: an ayah with the same mistake (skipped or another word) twice or more in the last 7 days, five at most.
- Practice: `HifzController.startPractice(kind)` stores a `PracticeRequest`; :app calls `AwnServices.startHifzPractice(request)` (words hidden, Quran screen opened at the first ayah, `reciteFrom`). `QuranScreenSlots.hifzBar` (set by :app only while `controller.practice` is non-null) replaces the recite bar in recite mode; `ReciteBarOrDownload` wraps it so the speech download card still comes first. `HifzBar` has a title row (part, ayah or pages, Finish, Close) and a control row (show or hide words, play the part with repeat x1/3/5/10/20, microphone). Finish shows Easy, Good, Hard, Forgot with the suggestion (`HifzProgress.suggestedGrade` from flagged words) filled; one tap records the part (`finishPractice`). Leaving the bar restores the player's repeat setting, exits recite and drops an unfinished practice.
- A stopped recitation writes its flagged words as mistakes (`PracticeMistakes`): skipped word and wrong word only; unclear words, letter substitutions and timing are not counted.

## Open items
- IndoPak 15/16-line pages: add a family when the layout data exists.
- Minutes per part are rough constants (`PartMinutes`).

## Gotchas
- A forgotten page needs a card to be promoted again by a Good revision; half lessons have no card on purpose.

## Tried / rejected
- Carrying overdue pages to the next day: it piles up and users quit when revision piles up.
