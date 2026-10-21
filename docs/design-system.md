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
- Switches are 48 x 28dp soft rectangles with a square knob. Sliders: 8dp track in the ink at 30 % and a 24dp thumb in the accent.
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

## Layout of the index

Top bar: app name, search, last page, Learn, Settings. Tabs Surahs (grouped under juz headings that are plain text with the first page at the end, no filled band; the list opens at the surah read last), Juz (8 quarters per juz with their first words and page), Bookmarks (recent pages, then bookmarks). Rows at least 48dp.

## Layout of the Settings screen

A short list of sections, each an icon and a label opening its own page: Mushaf, Reading, Audio, Recite and memorise, Appearance, Backup, About. Pages use 48dp rows: label left, value right in the accent, or a switch; choices open a dialog, never a row that cycles on tap.

## Layout of the Learn screen

Ochre field in Awn mode. Two figure tiles (days in a row, pages this week), the revise action with its count, the lessons, and the day's snippet with its reference.

## Gates

- Compose gate test: no `.dp` literal outside the theme package except 0, 1, 2, 4; no `.sp` literal; no `Color(0x` literal; sizes come from `Spacing.kt`, `Type.kt`, `Color.kt`.
- Copy gate: no quoted text in a Compose text call; banned words in any string file are listed in `docs/copy.md` rule 9.
- Navigation gate: every `NavHost` passes the four `ScreenTransitions` (a slide of 150 to 250 ms) and the manifest sets `enableOnBackInvokedCallback="false"`.
- Screenshot check on the phone at font scale 1.0 and 1.3, in Awn and Black mode, for every screen above (no emulator is used).
