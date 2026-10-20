System: awn (own system; structure from the design-system skill, direction "Colour chapters" from tasteskill, seed 1409703933)

# Awn design system

Every screen is a chapter with one flat colour, floor to ceiling, and one object on it: the Mushaf page. The colours are the Madani Mushaf's own: its cream paper, its green binding, the ochre of its ayah rosettes, and the near-black of night reading. Read, Listen, Recite and Learn each own one field colour, so the colour of the screen tells the user which mode they are in before they read a word.

## Type

- One Latin and Bangla UI face: Roundo (rounded geometric sans), weights 400 and 600. Bangla falls back to Noto Serif Bengali. Quran text uses the QPC Hafs font (mockups stand in Amiri Quran).
- Five sizes only, in sp: 14 (small), 16 (body), 22 (lead), 32 (title), 56 (poster). Quran text 26sp with line height 2.0 so a 15-line page fits the screen; page view may scale it by the user's zoom only.
- Poster and title: weight 600, tracking -0.025em, leading 1.05. Body: weight 400, leading 1.5. Sentence case everywhere; no uppercase tracking, no italics.
- Headlines two lines or fewer at every width. Running text in the full ink of the field, never grey.

## Space

- 8dp grid. Tokens: 8, 16, 24, 32, 48, 64. Screen margin 16dp. Section gaps inside a screen 32dp; between chapters 0dp (fields butt together).
- Touch targets 48dp minimum. Buttons 48dp tall, full width or content width, square corners.
- Safe areas respected; controls never sit under the navigation bar or the keyboard.

## Controls

- Buttons are flat fills with 14dp corners (soft rectangles, never pills): on cream and ochre fields, green fill with cream text; on green and night fields, ochre fill with ink text. One primary action per screen. Player controls are icons (previous, play or pause, next, repeat with its count) on 48dp buttons; everything else is a text label.
- Word blocks on the page use 8dp corners; switches are 48 x 28dp soft rectangles with a square knob.
- Sliders: 8dp filled track in the field ink at 30 % and a 24dp thumb in the accent with 7dp corners.
- The collapsed player is a 72dp green dock with 14dp corners floating 12dp above the bottom edge: play, "Reciter · Ayah n", expand.
- Word states in the page: confirmed, tentative, wrong, hidden. On cream: green ink, ochre ink, red ink, cream block. On night: light green, light ochre, light red, paper-coloured block. States are shown on the word itself, never on body text elsewhere.
- No dividers, no outlined boxes, no shadows at rest, no gradients, no icons on cards. Surfaces are separated by a change of field colour. Sheets and menus that float may carry a shadow.

## Colour

| Token | Value | Use |
|---|---|---|
| paper | #F4ECDA | Read field; ink on green and night |
| ink | #17160F | Text on paper and ochre; Recite field |
| green | #0E5A3C | Listen field; accent on paper and ochre; confirmed word on paper |
| ochre | #D9A33A | Learn field; accent on green and night; tentative word; ayah rosettes |
| red | #B5341F | Wrong word on paper |
| night ink variants | #7CE29A, #F2C94C, #FF7A66 | Confirmed, tentative, wrong on the night field |

Neighbouring fields differ in lightness as well as hue (paper, green, night, ochre). No tints between them; the hard edge where two fields meet is the look. Dark theme is the night field used for Read as well.

## Layout of the Home screen

Wordmark top-left, "Settings" top-right. Four 80dp poster bands, one per chapter, stacked with no gaps: the mode name at 32sp flush left and its live detail at 14sp on the right (current page, current reciter, hifz range, ayahs due). The bands are the navigation; there is no tab bar. Below: two figure tiles (days in a row on green, pages this week on night) at 56sp, the day's snippet (Arabic, one-line rendering, reference), and "Continue reading" as the one primary action.

## Layout of the Read screen

Paper field. Header: surah name left, juz and page right, 16sp. Two page views, chosen in Settings or by a toolbar toggle:
- Mushaf: the page fills the width, 15 lines of Quran text, surah header as a filled green band, ayah ends as ochre rosettes with the number. Tap a word: it gets a green block, and a paper sheet rises with the ayah's translation. Swipe for pages.
- Ayah by ayah: each ayah is a block with the rosette first, the words laid out right to left with their word-by-word meaning under each (12sp), then the transliteration (16sp, green), then the translations in the user's order (Bangla, English). The current ayah sits on a green tint block with 14dp corners. Each line (transliteration, word by word, each translation) can be switched off in Settings.
Both views carry the collapsed player dock at the bottom when audio is loaded.

## Layout of the Settings screen

Paper field, one scrolling list. Groups Reading, Listening, Reciting, Learning, App, each a 22sp green title followed by 44dp rows: label left, current value right in green, or a switch. No dividers; groups are separated by 20dp. Reading holds script, page view, translations (ordered, multiple), transliteration, word by word, text size. Listening holds reciter, download by page or surah, Wi‑Fi only, downloaded audio size. Reciting holds speed, hide words in hifz, hint. App holds theme, language, backup, notice, credits, version.

## Layout of the Listen screen

Green field, paper ink. The page as in Read; the playing ayah's words carry an ochre block one by one. Bottom: reciter name at 32sp, then Previous, Play (ochre fill), Next and Repeat as 48dp controls, and a filled progress band with elapsed and total time.

## Layout of the Recite screen

Night field. The page with word states: confirmed light green, current light ochre, wrong light red, hidden words as paper blocks in hifz. Bottom: "Speed" slider from Slower to Faster, a one-line status ("Skipped: ayah 3, word 4"), and Stop as the one primary action.

## Layout of the Learn screen

Ochre field, ink. Two figure tiles side by side (pages this week, ayahs due today) in green and night with their figures at 56sp; below, the lessons as poster lines at 32sp; the day's snippet in Arabic with its reference at the bottom.

## Gates

- Compose gate test: no `.dp` literal outside the theme package except 0, 1, 2, 4; no `.sp` literal; no `Color(0x` literal; sizes come from `Spacing.kt`, `Type.kt`, `Color.kt`.
- Copy gate: no visible string outside `strings.xml`; banned words: model, threshold, session, engine, alignment, buffer, sync, verify, AI (except in the notice).
- Screenshot check on the emulator at font scale 1.0 and 1.3, light and night, for every screen above.
