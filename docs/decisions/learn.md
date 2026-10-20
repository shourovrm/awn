# Learn

## Current state
- `:feature:learn` owns `LearnDatabase` (Room, file `learn.db`, version 1): `event(kind, surah, ayah, page, at)` and `card(surah, ayah, stability, difficulty, due, last_review, reps, lapses, state, step)`. `step` is one column beyond the plan, because FSRS needs the learning step between reviews. Schemas are exported to `feature/learn/schemas`; `LearnMigrationTest` opens every old schema.
- `LearnProgress` implements `ProgressEvents`. :app gets it with `learnProgressEvents(context)` and passes it to :feature:read and :feature:recite. A skip-free hifz completion is rated Good, one with skips Again. `rateCard(ayah, rating)` is the entry point for the recite feature; the Revision screen offers Again and Good by hand until then.
- Derived figures (`ProgressCalculator`, pure functions): streak is consecutive local days with any event, ending today or yesterday; pages this week are distinct pages per local day summed over today and the six days before; an ayah is memorised after `memorisationThreshold` (default 3, range 1 to 10, in `LearnSettings` DataStore) skip-free completions on that many different days; cards are due when their local due date is today or earlier.
- The day boundary is the device's midnight, read from `ZoneId.systemDefault()` on every call.
- `Fsrs.kt` ports py-fsrs 6.3.2 (default 21 weights, fuzzing removed). `tools/generate_fsrs_vectors.py` writes `src/test/resources/fsrs_vectors.json` (200 default sequences, 50 without learning steps, py-fsrs version and git commit recorded); `FsrsReferenceTest` requires equal due times to the second. The app runs FSRS without minute-long learning steps (`dailyScheduler`), so a new or forgotten ayah is due after at least one day.
- 12 lessons in `src/main/assets/lessons/<id>.json`, all drafts (`reviewed_by` null, shown with a Draft label). `tools/check_lessons.py` checks every example word against `quran.sqlite` and prints them. The plan asked for examples from QUL tajweed-marked text; they come from the word table and the author's knowledge instead.
- `src/main/assets/snippets.json` holds 34 ayahs and 26 hadith. `tools/build_snippets.py` writes it; `tools/check_snippets.py` checks it against sunnah.com and the hadith API. English and Bangla renderings are the project's own drafts.
- Lesson and screen text never says the app checks ghunnah, qalqalah, tafkheem or other sifat; live feedback covers skipped words, substituted letters and madd length only. `LessonContentTest` scans the lesson bodies.
- `LearnScreen(modifier, quranText)` is the tab entry and routes between Learn, Revision, Progress, lessons and one lesson; the stateless screens take plain UI state. Without a `QuranTextRepository`, Revision and lesson examples show references only.

## Open items
- A reviewer must check all 12 lessons and the Bangla and English renderings of the snippets, then fill `reviewed_by` and `reviewed_on`.
- :app must pass its `QuranTextRepository` to `LearnScreen` and `learnProgressEvents(context)` to the read and recite features.
- The Learn screen does not show the snippet rendering; Home may.
- Roundo and the Noto Serif Bengali fallback are not bundled, so Bangla uses the platform font.

## Gotchas
- Compose tests under Robolectric run on Android 15 (`@Config(sdk = [35])`): Espresso calls `InputManager.getInstance()`, which Android 16 removed.
- `check_snippets.py` finds the hadith number in the sunnah.com page, which proves little for short numbers; the hadith API comparison is the stronger check.
- Muslim numbers with sub-hadith use sunnah.com's letter form (`804a`); the hadith API calls the same report `804.01`.

## Tried / rejected
- sunnah.com through curl: Cloudflare challenge (403); Python urllib with a browser user agent gets 200.
- Hadith on "chanting" the Quran (يتغنى): rejected because the translation is disputed.
