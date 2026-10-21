# Learn

## Current state
- `:feature:learn` owns `LearnDatabase` (Room, file `learn.db`, version 1): `event(kind, surah, ayah, page, at)` and `card(surah, ayah, stability, difficulty, due, last_review, reps, lapses, state, step)`. `step` is one column beyond the plan, because FSRS needs the learning step between reviews. Schemas are exported to `feature/learn/schemas`; `LearnMigrationTest` opens every old schema.
- `LearnProgress` implements `ProgressEvents`. :app gets it with `learnProgressEvents(context)` and passes it to :feature:read and :feature:recite. `hifzCompleted` rates the card by itself: no skips Good, one skip Hard, two or more Again. `gradeAyah(ayah, rating)` is the user's own grade from the recite bar or the Revision screen: within 10 minutes of an automatic rating of the same ayah it restores the card kept in memory before rating, so one recitation counts as one review; otherwise it equals `rateCard`. `dueAyahs()` lists due ayahs in reading order.
- Derived figures (`ProgressCalculator`, pure functions): streak is consecutive local days with any event, ending today or yesterday; pages this week are distinct pages per local day summed over today and the six days before; an ayah is memorised after `memorisationThreshold` (default 3, range 1 to 10, in `LearnSettings` DataStore) skip-free completions on that many different days; cards are due when their local due date is today or earlier.
- The day boundary is the device's midnight, read from `ZoneId.systemDefault()` on every call.
- `Fsrs.kt` ports py-fsrs 6.3.2 (default 21 weights, fuzzing removed). `tools/generate_fsrs_vectors.py` writes `src/test/resources/fsrs_vectors.json` (200 default sequences, 50 without learning steps, py-fsrs version and git commit recorded); `FsrsReferenceTest` requires equal due times to the second. The app runs FSRS without minute-long learning steps (`dailyScheduler`), so a new or forgotten ayah is due after at least one day.
- 12 lessons in `src/main/assets/lessons/<id>.json`, all drafts (`reviewed_by` null, shown with a Draft label). `tools/check_lessons.py` checks every example word against `quran.sqlite` and prints them. The plan asked for examples from QUL tajweed-marked text; they come from the word table and the author's knowledge instead.
- `src/main/assets/snippets.json` holds 34 ayahs and 26 hadith. `tools/build_snippets.py` writes it; `tools/check_snippets.py` checks it against sunnah.com and the hadith API. English and Bangla renderings are the project's own drafts.
- Lesson and screen text never says the app checks ghunnah, qalqalah, tafkheem or other sifat; live feedback covers skipped words, substituted letters and madd length only. `LessonContentTest` scans the lesson bodies.
- `LearnScreen(modifier, quranText, onReviseAt, barActions)` is the Learn tab (`barActions` = Settings icon from :app); `learnDueAyahCount` and `learnFirstDueAyah` feed the Today row; it shows days in a row and pages this week (moved from Home) and keeps the ochre field in Awn mode, the active mode's colours otherwise. It is the entry and routes between Learn, Revision, Progress, lessons and one lesson; the stateless screens take plain UI state. Without a `QuranTextRepository`, Revision and lesson examples show references only. With `onReviseAt` set, "Revise N ayahs" calls it with the first due ayah in reading order (:app opens the Quran screen there in recite mode, words hidden); without it the in-tab Revision screen runs, with four 48 dp grade buttons (Forgot = FSRS Again, Hard, Good, Easy).
- Reminder: `ReviseReminder` sets one inexact `AlarmManager` alarm per day at the chosen time (settings `reminderEnabled` default off, hour 20, minute 0, in `LearnSettings`); `ReviseReminderReceiver` shows "N ayahs to revise" only when something is due and the notification permission is held, then sets tomorrow's alarm, and re-sets it after boot or an app update. `ReviseReminderSettingsPage()` is the Settings hub page (switch, time row, time dialog, asks for POST_NOTIFICATIONS on Android 13+). `ReviseReminder.rescheduleIfEnabled(context)` is for :app at start.
- Screens: `LearnBar` shows the back icon button with the screen title; figure tiles grow with text size (minimum 192 dp high); `RevisionAndProgressScreenTest` draws Revision and Progress 360 dp wide at font scale 2.0 in Light, Dark and Black.

## Open items
- A reviewer must check all 12 lessons and the Bangla and English renderings of the snippets, then fill `reviewed_by` and `reviewed_on`.
- The snippet appears only on the Learn screen (Home is gone), in Arabic without a rendering.
- Roundo and the Noto Serif Bengali fallback are not bundled, so Bangla uses the platform font.

## Gotchas
- Compose tests under Robolectric run on Android 15 (`@Config(sdk = [35])`): Espresso calls `InputManager.getInstance()`, which Android 16 removed.
- `check_snippets.py` finds the hadith number in the sunnah.com page, which proves little for short numbers; the hadith API comparison is the stronger check.
- Muslim numbers with sub-hadith use sunnah.com's letter form (`804a`); the hadith API calls the same report `804.01`.

## Tried / rejected
- sunnah.com through curl: Cloudflare challenge (403); Python urllib with a browser user agent gets 200.
- Hadith on "chanting" the Quran (يتغنى): rejected because the translation is disputed.
