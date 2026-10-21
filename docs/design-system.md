System: awn (own system; structure from the design-system skill, direction "Colour chapters" from tasteskill, seed 1409703933)

# Awn design system

Awn has one main screen, the Quran screen: the printed Mushaf page, floor to ceiling, with bars that float over it only when the user taps. The colours are the Madani Mushaf's own (cream paper, green binding, ochre ayah rosettes, near-black night reading), and four colour modes repaint the whole app. `docs/redesign.md` is the behaviour spec for the screens below; `docs/copy.md` holds the vocabulary and copy rules.

## Type

- One Latin and Bangla UI face: Roundo (rounded geometric sans), weights 400 and 600. Bangla falls back to Noto Serif Bengali. Quran text uses the QPC Hafs font (mockups stand in Amiri Quran).
- Six sizes only, in sp: 12 (caption, word-by-word meanings), 14 (small), 16 (body), 22 (lead), 32 (title), 56 (poster). Quran text 26sp with line height 2.0 so a 15-line page fits the screen; page view may scale it by the user's zoom only.
- Poster and title: weight 600, tracking -0.025em, leading 1.05. Body: weight 400, leading 1.5. Sentence case everywhere; no uppercase tracking, no italics.
- Headlines two lines or fewer at every width. Running text in the full ink of the mode, never grey.

## Space

- 8dp grid. Tokens: 8, 16, 24, 32, 48, 64. Screen margin 16dp. Section gaps inside a screen 32dp.
- Touch targets 48dp minimum. Buttons 48dp tall, full width or content width, square corners.
- Safe areas respected; controls never sit under the navigation bar or the keyboard.

## Controls

- Bars and menus use icon buttons (48dp target, 24dp Material Symbols glyph) with a spoken label that says what pressing them does. Text buttons only where an icon would be ambiguous: dialog actions and the one primary action of a settings page.
- Buttons are flat fills with 14dp corners (soft rectangles, never pills): green fill with paper text on light modes, ochre fill with ink text on dark modes. One primary action per screen.
- The audio bar sits at the bottom of the Quran screen: idle (play, reciter name, play options), playing (stop, previous, pause, next, repeat, speed; repeat and speed show a small value only when not at the default), downloading (cancel, progress, size). The recite bar replaces it while reciting.
- Ayah selection: a floating bar of icon buttons above the selected ayah (play from here, recite from here, bookmark, translation, copy, share). The ayah panel rises over about 60 % of the screen and moves the page up so the ayah stays visible.
- Fills behind Quran text, never on the text: selected ayah (teal), playing ayah (green), playing word (ochre, on the ayah fill), bookmarked ayah (faint ochre). Fills cover each line fragment, so a long ayah has a stepped shape.
- Word states while reciting: confirmed, tentative, wrong, hidden. Light modes: green, ochre, red ink and a block for hidden words; dark modes: the light variants and a paper-coloured block.
- Switches (`AwnSwitch`) are thin-rail: a 32dp wide, 4dp rail (ink at 25 % off, accent at 45 % on) and a 16dp round knob (off: page colour with a 2dp ring of the ink at 55 %; on: accent fill) that slides in 120 ms, inside a 48dp touch target; a settings row toggles from the whole row. Sliders: 8dp track in the ink at 30 % and a 24dp thumb in the accent.
- Single choices (`AwnChoiceRow`): the row shows the label, an optional hint line and the chosen value in the accent with a small chevron; a tap opens a menu anchored under the value (12dp corners, raised surface, 44dp items, tick on the chosen one). No segmented buttons, no chips, no rows that cycle on tap. Several choices at once are tick boxes.
- Steppers (`SettingsStepperRow`): minus, value in tabular figures, plus; each button is a 48dp touch target.
- No dividers, no outlined boxes, no shadows at rest, no gradients. Floating bars, sheets and menus may carry a shadow.
- Bars fade in and out in at most 120 ms; closing a popup, panel or selection is instant. Screens change with a 200 ms slide (150 to 250 ms is allowed): the new screen slides in from the trailing edge, Back slides the old one out to it and the previous screen in from the leading edge, mirrored in right-to-left layouts (`ScreenTransitions`, NavTransitionsTest). The manifest opts out of predictive back, whose back-to-home animation shrinks the whole window.

## Colour

Four colour modes, chosen in Settings > Appearance as day colours (Awn or Light) and night colours (Dark or Black), switched on the page with the day/night button or by following the phone theme.

| Mode | Page | Bars | Ink | Accent |
|---|---|---|---|---|
| Awn | paper #F4ECDA | green #0E5A3C | ink #17160F | green on paper, ochre #D9A33A on green |
| Light | #FFFFFF | #ECEEEF | #1B1B1B | green |
| Dark | #121212 | #1F1F1F | #E8E2D2 | ochre |
| Black | #000000 | #121212 | #E8E2D2 | ochre |

Red #B5341F marks wrong words on light modes; #7CE29A, #F2C94C and #FF7A66 are the confirmed, tentative and wrong inks on dark modes. Learn keeps its ochre field in Awn mode. Status-bar and navigation-bar icons follow the colour actually painted under them, dark on light modes.

## Layout of the Quran screen

The page pager fills the screen; swiping right goes to the next page. Mushaf view: the printed page in the chosen print, header (surah that starts on the page, else the continuing one, at the left; juz and hizb quarter at the right), page number at the outer bottom corner; pages 1 and 2 as a centred block. Translation and Word by word views: one block per ayah under a surah band and the Basmalah. Top bar, when shown: index, two-line title (surah; page and juz), view, day/night, text size, settings, more.

## First-launch flow

A new installation shows three screens once, in order: the language screen, the notice, the Quran; the first Quran screen also shows one floating card, dimmed page behind it, that offers the printed Mushaf (three prints with sizes, Download with the size of the tick, Not now; then progress with Keep reading and Cancel). Floating cards (print offer, Go to page) are `raisedSurface` with 14dp corners and a shadow, with soft-filled fields and flat 48dp buttons. An installation that already accepted the notice goes straight to the Quran. The language screen fills the window with binding green in Awn and Light and with the night page colour in Dark and Black, with the app mark (64dp), the title (22sp lead), two language blocks of at least 72dp (each language in its own script at 22sp with its name in the screen's language beneath, a 24dp ring that becomes a tick on the chosen one), a hint that it can be changed in Settings, and an ochre Continue button. The block chosen first is Bangla on a Bangla phone, else English; choosing a block rewrites the screen's own text at once, and Continue applies the language to the whole app. Settings > Appearance > Language is the same choice in an `AwnChoiceRow`, and the Settings hub has a Language row first in the App group that opens the same choice.

## Layout of the index

Top bar: app name, search, last page, Learn, Settings. Tabs Surahs (grouped under juz headings that are plain text with the first page at the end, no filled band; the list opens at the surah read last), Juz (8 quarters per juz with their first words and page), Bookmarks (recent pages, then bookmarks). Rows at least 48dp.

## Layout of the Settings screen

The hub has a search field (44dp, 12dp corners, soft fill) at the top of the body under the green bar, then sections grouped under small accent headings: Reading (Mushaf, Reading), Listening (Audio, Downloads, Recite and memorise), App (Appearance, Backup, About). Each section is a 64dp row: icon, label, the current value as a muted second line, chevron. No boxes, no dividers. Typing filters every setting of every page (label and value names, English and Bangla); a result opens its page scrolled to the row, which flashes once.
Inner pages use the same two-line rows (64dp with a second line, 52dp without) under small accent group headings: switches (`SettingsSwitchRow`), choices, steppers, links (`SettingsLinkRow`).
Downloadable lists (translations, transliteration, word by word, prints) are one page each, opened from a row. Entries sit under a heading in the language's own script with its name in the app language at the end; each row has a tick box at the start (only once the entry is on the phone), the name with a muted detail line (size, Included, progress), and one button at the end: download, cancel, or remove (not for bundled entries or the print in use). Remove asks first in a small dialog that names the entry and the space it frees.

## Layout of the Learn screen

Ochre field in Awn mode. Two figure tiles (days in a row, pages this week), the revise action with its count, the lessons, and the day's snippet with its reference.

## Gates

- Compose gate test: no `.dp` literal outside the theme package except 0, 1, 2, 4; no `.sp` literal; no `Color(0x` literal; sizes come from `Spacing.kt`, `Type.kt`, `Color.kt`.
- Copy gate: no quoted text in a Compose text call; banned words in any string file are listed in `docs/copy.md` rule 9.
- Navigation gate: every `NavHost` passes the four `ScreenTransitions` (a slide of 150 to 250 ms) and the manifest sets `enableOnBackInvokedCallback="false"`.
- Screenshot check on the phone at font scale 1.0 and 1.3, in Awn and Black mode, for every screen above (no emulator is used).
