# Awn interface copy

Every visible string lives in a module's `strings.xml` (English) and `values-bn/strings.xml` (Bangla); the copy gate in `core/ui` fails the build on a quoted text literal in a Compose text call and on a banned word in any string file. This file is the vocabulary and the rules those strings follow, from the design-system copy pass of 2026-10-21 (Material 3 copy rules: sentence case, button labels of one to three words, errors that say what failed and how to fix it).

## Vocabulary
Use these words for these things, and no other word for the same thing.

| Word | Means |
|---|---|
| page | one page of the printed Mushaf, 1 to 604 |
| ayah, surah, juz, hizb | the Quran's own units; never "verse", "chapter" or "part" |
| Mushaf | the printed-page view, and the edition it shows: Madinah Mushaf · 1421, Madinah Mushaf · 1405, Madinah, tajweed colours, Indo-Pak, 15 lines; never "print" or "page type" in the UI |
| translation | one translator's text; "Translation" is also the view that lists them |
| word by word | the view and setting with each word's meaning |
| reciter | a person whose recitation plays |
| recite | the user reading aloud to the app |
| download | getting a Mushaf, translation or recitation onto the phone |
| bookmark | a saved page or ayah |
| memorise, revise | learning by heart and coming back to it |
| hint | the first letter of the next word, on request |
| review | the list of words to look at after reciting |
| day colours, night colours | the two colour choices; never "theme" except "phone theme" |
| space used | storage taken by downloads |
| Continue reading | the slim card that opens the page read last ("Start reading" before anything was read) |
| Today | the load of the Hifz and Learn tiles on the Quran tab; never a backlog |
| Show | the choice of list on the Quran tab: Surahs, Juz, Pages |
| Go to page | opens a page by number, on the Quran tab and in the Quran screen menu |
| Hifz | the memorising tab; a word of its own, not translated into "memorise" |
| surah row | meaning, Makki or Madani, ayah count joined by " · ": "Abraham · Makki · 52 ayahs"; "read last" is added on the surah read last |

## Rules
1. A label is a noun phrase of at most three words, never a question. "Volume keys turn pages" is the one four-word exception, because no shorter form says what the switch does.
2. A button is a verb and its object: "Play from here", "Export bookmarks". A lone verb is enough when the object is on screen: "Download", "Remove".
3. An icon button's spoken label says what pressing it does: "Switch to night", not "Night".
4. A hint is one line of at most twelve words stating the effect, and appears only when the label is not enough.
5. A status line says what happened, then what the user can do: "Download failed. Check your connection."
6. An error names what failed and offers one action, in the user's words: "Can't read that file. Choose an Awn backup."
7. One calm voice: no jokes, no exclamation marks, sentence case everywhere.
8. Numbers the user cannot act on stay out of the main screens; the review sheet's held-vowel timing is the one place they appear.
9. Never name the mechanism. Banned in any visible string: model, threshold, session, engine, alignment, buffer, sync, verify, fallback, pack, tracker, phoneme, cache, manifest (and AI outside the notice).
10. The recite feature is "experimental feedback on likely mistakes"; it never claims to check pronunciation like a teacher.

## Changes in this pass
62 strings that only the removed Read, Listen and Recite screens used were deleted in both languages. These were rewritten:

| String | Before | After | Reason |
|---|---|---|---|
| appearance_follow_device | Follow the device | Follow phone theme | names what is followed |
| audio_space_used | Downloaded audio | Space used | same name as the Audio page |
| backup_exported | Saved | Bookmarks exported | says what happened |
| backup_imported | Imported | Bookmarks imported | says what happened |
| backup_failed | Can't use that file | Can't read that file. Choose an Awn backup. | error with one action |
| progress_streak_caption | day streak | days in a row | same words as the Learn tile |
| revision_prompt | Recite it from memory. | Recite from memory | instruction, no full stop |
| audio_error | Cannot play this audio | Can't play this ayah. Check your connection. | error with one action |
| manager_failed, font_prompt_failed | Download failed (Try again) | Download failed. Check your connection. | one wording, names the likely cause |
| panel_repeat_range | Repeat the range | Repeat range | label, three words |
| panel_only_range | Play only this range | Only this range | label, three words |
| reciter_group_page | Downloaded for this page | Ready offline | the user's reason to care |
| reciter_group_downloads | With downloads | Partly downloaded | says what it means |
| audio_setting_download_amount | Download amount | Download by | reads with its value: "Download by: Surah" |
| bar_day_mode, bar_night_mode | Day, Night | Switch to day, Switch to night | says what the button does |
| page_unavailable | Page not available | Can't show this page | plain error |
| font_prompt_keep | Keep Hafs text | Not now | "Hafs text" named the mechanism; every print is Hafs |
| mushaf_year_v4 | 1421 print, colour coded | 1421 print with tajweed colours | says which colours |
| mushaf_show_page_info | Show page info | Page details | label, not a command |
| recite_status_no_text | No text for this range | Can't load these ayahs | user's words |
| recite_status_no_microphone | Can't use the microphone | adds "Close other recording apps." | error with one action |

The Bangla strings changed in this pass are the maintainer session's translations and need a native reader's check, like the agents' Bangla before them.
