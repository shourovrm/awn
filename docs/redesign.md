# Single-page redesign

Awn moves from four separate modes (Read, Listen, Recite, Learn) to one Quran screen that reads, plays, recites and memorises on the same page. The inputs are the phone test of 2026-10-20, a study of Quran for Android 3.6.4 on the same phone, and the maintainer's review. This file is the build spec for the redesign wave; `docs/design-system.md` is rewritten from it when the wave is merged.

## Decisions

- The default page type is the 1421 Madani print (`ScriptId` V2, page fonts, 137 MB zip). The 1405 print (V1, 54.5 MB) and the tajweed print (V4, 68.9 MB) are the other page types. QPC Hafs Unicode stays only as the fallback while a page font is missing.
- The page font is downloaded on first launch after the notice, with its size shown and a Wi-Fi recommendation; until it is installed the page draws in QPC Hafs with a "Download" prompt.
- Learn lessons and revision stay a separate list, opened from the index. Memorising (hidden words) is a mode of the Quran screen.
- The app opens on the last page read. The index (surahs, juz, bookmarks) is one tap away.
- Icons replace text labels on every bar and menu. Every icon has a content description in `strings.xml`. Icons are Material Symbols vector drawables (Apache-2.0) copied as XML into the module that uses them, named `ic_<module>_<name>.xml`; no icon library dependency.

## Quran screen

The Quran screen is a horizontal pager of the 604 pages. Swiping right goes to the next page, as in a printed Mushaf. One tap on the page shows or hides both bars together; the bars float over the page and never resize it, and they show for 2 s whenever the screen opens, then hide.

### Views
The top bar has one view button that cycles three views of the same page; the choice is saved.
- **Mushaf**: the printed page, 15 lines, exactly as the chosen print. Header: surah name at the left, juz and hizb at the right ("Juz 2 · ¼ Hizb 3" only on pages where a quarter starts). Page number at the bottom outer corner: right on odd pages, left on even pages. Pages 1 and 2 are drawn as a centred block (banner about 27 % down, text centred), not stretched to the 15-line frame.
- **Translation**: a vertical list of the page's ayahs. Each ayah is a block: reference chip ("2:255"), Arabic, transliteration (if on), then each enabled translation in the user's order. A band with the surah name starts each surah. Swiping horizontally still changes the page.
- **Word by word**: the page's ayahs with each Arabic word and its meaning under it, laid out right to left and wrapping. Tapping a word plays that word (`AudioPlayer.playWord`) when the reciter has word timings, and shows its meaning and transliteration in a small popup.

### Top bar
Left: index button (opens the index). Title: surah name, and "Page 50 · Juz 3" under it. Right: view button, day/night button, text-size button, overflow. The day/night button switches between the day colour mode and the night colour mode chosen in Settings. The text-size button opens a small popup with minus and plus (Arabic and translation size together, 8 steps from 0.85 to 1.6 of the base size). Overflow: Search, Go to page, Bookmark this page, Learn, Settings.

### Ayah selection
A long press (500 ms) on any word of an ayah selects the ayah: its line fragments get the selection fill, and a floating bar appears above it with icon buttons in this order: play from here, recite from here, bookmark, translation, copy, share. Translation opens the ayah panel. Copy puts "Arabic text — translation (2:255)" on the clipboard. Share opens the Android share sheet with the same text.

The ayah panel rises over about 60 % of the screen; the page above is clipped, not scaled, and scrolls so the ayah stays visible. Tabs (icons): Translation, Play. Translation shows the reference, each enabled translation, and previous and next ayah buttons; the selection on the page follows them. Play holds the range controls (see Audio).

### Back
Back closes the innermost open thing, in this order: text-size popup or overflow, ayah panel, ayah selection, recite mode (stops reciting), shown bars. Only when nothing is open does Back leave the Quran screen, to the index (home); Back on the index closes the app. Back never changes the saved view.

### Highlights
Fills are drawn behind the text; the text keeps its colour except word states. Colours come from `Awn.field`: `selectedAyahFill` (long-press selection), `playingAyahFill` (ayah being played), `playingWordFill` (word being played, on top of the ayah fill), `bookmarkedAyahFill` (only when "Highlight bookmarks" is on). Fills cover each line fragment of the ayah, so a multi-line ayah has a stepped shape. During playback the Mushaf view turns the page when the playing ayah moves to the next page, and the Translation and Word by word views scroll the playing ayah to the top.

### Audio bar
A bar at the bottom of the Quran screen with three states:
- Idle: play, reciter name (tap opens the reciter sheet), expand.
- Playing: stop, previous ayah, pause, next ayah, repeat, speed. Repeat and speed show a small value over the icon only when not at the default ("3", "1.25").
- Downloading: cancel, "Downloading", a progress bar, and the size ("2.1 of 5.4 MB").
- Asking: when the missing audio is over 20 MB or the network is metered, "Download 3.2 MB?" with Stream and Download. A failed download names its cause ("Needs 120 MB, 40 MB free", "No internet", "Server not reachable"). In cumulative repeat the playing state also shows the step ("3/7").
Play starts at the first ayah of the page (or asks "Start from" with the two surahs when a page holds two). Missing audio downloads by the "Download amount" setting (page, surah or juz) and plays when ready.

The Play tab of the ayah panel: From (surah, ayah), To (surah, ayah; default the last ayah of the page), "Repeat each ayah" (1, 2, 3, 5, 10, ∞), "Repeat the range" (1, 2, 3, 5, 10, ∞), speed (0.75 to 1.5), "Play only this range", and "Play". The reciter sheet: a search field, then "Downloaded for this page", "With downloads", "All", a tick on the current reciter. The media notification title is "Al-Baqarah 2:6" with the reciter under it.

### Recite and memorise
"Recite from here" switches the bottom bar to the recite bar: microphone start or stop, a speed button, a hide-words switch (memorise), a hint button, a one-line status, and the review button after stopping. Words take their states on the page (confirmed, tentative, wrong, hidden). Words are coloured only after the app hears recitation; background noise alone must not colour words. Back or the close button leaves recite mode and returns the audio bar.

## Index
The index replaces Home. Top bar: app name, search, last page, Learn, Settings. Tabs: Surahs, Juz, Bookmarks.
- Surahs: grouped under juz headers that show the juz's first page; each row shows the number, the name (and its meaning when "Surah name meaning" is on), "Makki · 7 ayahs", and the first page at the right. The list opens scrolled to the surah read last.
- Juz: each juz with its 8 quarters; each quarter row shows the quarter's first Arabic words and its page.
- Bookmarks: "Recent pages" (last 3), then bookmarked pages and ayahs, sortable by date or by position.
- Search: one field over the Arabic (diacritics ignored) and the installed translations. Results show the count, the matching text with the match marked, and "Al-Baqarah 2:153 · page 23". A result opens the Quran screen at that ayah with it selected.
- Go to page: one dialog with a page field and surah and ayah fields that fill each other in (page 312 gives Maryam 96).
Learn keeps its screens (revision, progress, lessons) and its figures (days in a row, pages this week).

## Settings
Settings is a short list of sections; each opens its own page.
- Mushaf: page type (swipe through previews; each shows its size and an install or remove button), show page info.
- Reading: translations (choose and order), transliteration, word-by-word meanings, Arabic size, translation size, highlight bookmarks, volume keys turn pages.
- Audio: reciter, download amount (page, surah, juz), Wi-Fi only, downloads (per surah, per juz, range, everything; each with its size), space used.
- Recite and memorise: speed, hide words, hint, memorised after.
- Appearance: day colours (Awn, Light), night colours (Dark, Black), follow the device, language, surah name meaning.
- Backup: export, import.
- About: notice, credits, version.

## Colour modes
Four colour modes replace the per-mode fields on the Quran screen and the index: Awn (paper, green bars, ochre accents: today's palette), Light (white page, neutral grey bars, green accent), Dark (#121212 page, light text), Black (#000000 page for OLED screens). The status-bar and navigation-bar icons follow the mode (dark icons on light modes). Learn keeps its ochre field in Awn mode.

## Architecture
Feature modules still depend only on :core modules, :player and :tracker; :app composes them. The Quran screen lives in :feature:read and takes the parts it cannot own as slots:

```kotlin
// :feature:read, owned by the reader agent; the other agents build composables that fit it.
data class QuranScreenSlots(
    val audioBar: @Composable (page: Int) -> Unit,               // :feature:listen AudioBar
    val playPanel: @Composable (ayah: AyahRef, page: Int) -> Unit, // :feature:listen PlayRangePanel
    val onPlayFrom: (AyahRef) -> Unit,                           // :feature:listen controller
    val reciteBar: @Composable (onExit: () -> Unit) -> Unit,     // :feature:recite ReciteBar
    val onReciteFrom: (AyahRef) -> Unit,                         // :feature:recite session load
    val reciteWordStates: @Composable () -> Map<WordKey, WordState>, // empty when not reciting
    val isNight: Boolean,
    val onToggleNight: () -> Unit,
    val onOpenIndex: () -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenLearn: () -> Unit,
    val onOpenSettings: () -> Unit,
)
```

The maintainer session wires these slots in :app after the branches merge. Each settings page is a composable exported by the module that owns the setting; the Settings hub in :app links to them.

### Ownership
| Agent | Owns | Delivers |
|---|---|---|
| page | `feature/read/.../page/` (MushafPage, PageRows, PageFontCache, MushafViewModel's layout and font parts), `core/data` page layout and font packs, a new quarter (rub) table in `core/data`, `ScriptId` defaults | exact printed pages in V1, V2, V4; first-run font download prompt; pages 1 and 2; header and page number; highlight drawing (`PageDecorations`: selected ayah, playing ayah, playing word, bookmarked ayahs, word states); long-press reporting with the ayah's on-screen bounds; Mushaf settings page; repeated word-by-word meanings fix |
| reader | the rest of `feature/read` (new `QuranScreen`, views, bars, ayah selection and panel, go-to dialog, `ReadSettings` and its store except `script`) | the Quran screen with `QuranScreenSlots`; Translation and Word by word views; Back order; text size; jump to any page or ayah; page counted as read after 20 s on it; Reading settings page |
| audio | `:player`, `feature/listen` | `AudioBar`, `PlayRangePanel`, reciter sheet, range and repeat and speed and next/previous in `AudioPlayer`, a downloading state, download by amount on play, downloads manager with sizes, notification title, Audio settings page; fix for "Nothing downloaded" |
| recite | `feature/recite`, `:tracker` | `ReciteBar`, recite from an ayah, noise gate, working review sheet, speech files prepared in the background at app start, Recite and memorise settings page; then madd detection toward 9 of 10 |
| shell | `:app`, `core/ui`, `feature/learn` | colour modes and system-bar icons, index (surahs, juz quarters, bookmarks and recent pages), search (query in `core/data`), go-to-page dialog, Settings hub and the Appearance, Backup and About pages, Learn entry, open on last page, Credits text fix |

An agent edits only the files it owns. A needed change in another agent's files goes in the agent's final report, and the maintainer applies it at merge. `AwnTheme(field)` and `Awn.field` keep their current signatures so the other branches still compile.

## Defects covered
Phone-test defects map to agents: 1 Browse jump (reader), 2 playback highlight (page draws, reader drives), 3 recite colouring and review sheet (recite), 4 Reciters status (audio), 5 dotted circles and font size display (page), 6 pages 1 and 2 (page), 7 Back and translation sheet (reader), 8 status-bar icons (shell), 9 design drift (each agent in its area), 10 missing details (audio for the bar and downloads; shell for the index). Also: next and previous from the page (audio), pages-read count (reader), repeated word meanings (page), Credits raw text (shell), first recite start time (recite).

## Rules for this wave
- Work on your branch in your worktree; commit after each completed change (Conventional Commits, subject under 50 characters, body says why, no co-author trailers).
- Build only through `AWN_AGENT=<name> /home/rms/repos/awn/sessions/awn-gradle.sh <tasks>`; it allows three builds at once and waits for a free slot. Never call `./gradlew` directly and never start an emulator; the phone is not yours to use.
- Before your final report run, for your modules: `spotlessApply`, then `spotlessCheck detekt lintGithubRelease` scoped to your modules, and their `testGithubReleaseUnitTest` (or `testReleaseUnitTest` for library modules). Paste the real output.
- The design gates stay: no `.dp` literal other than 0, 1, 2, 4 outside the theme, no `.sp`, no `Color(0x` outside the theme, no visible string outside `strings.xml`, and the banned-word list (`docs/design-system.md`, Gates). UI text is short and conventional; prefer an icon with a content description to a text button.
- Update your topic file in `docs/decisions/` (Current state rewritten, not extended) and add one line per decision to `docs/decisions/LOG.md`.
