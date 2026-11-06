# Design E / v5 — Clear Sheet

The current design uses Roman numerals, action icons and minimal explanatory text. Only this version is kept. All mockup text is in English, including example cards, tooltips, accessibility labels, gallery notes and canvas titles.

[Main screen](learn-front.html) · [Gallery](index.html). `canvas.json` and `E-*.dc.html` contain the same screens for the design editor. Run `python3 design/build.py` to regenerate them from `build.py`, `styles.css` and `editor-shapes.js`. The demo script is embedded in HTML, including standalone boards. Do not edit generated files manually.

## Appearance

- The deck name is on the left. On the right, matching metric blocks show **Learning / 14 / 40** and **Successes / 3 / 4**. In inactive sections, the first label is **Browse**. The first number is the current position, not the number of rated cards. Labels are centered over the slash; equal-width number columns keep this alignment even for values such as 41 / 100.
- Sections: **New · I · II · III · IV · Learned**. An underline marks the open section; **▶** marks the scheduled active epoch. The marker sits beside the count without shifting it off center.
- **⇄** flips a card; the arrow pointing to an end bar moves it to the end of the pass. Flip occupies the second footer slot in both learning and browsing.
- Arrows page through cards. A plus creates a card. Undo uses an icon; line samples show pen widths. There is no pen/eraser switch: the pen tip draws and the stylus side button erases.
- The question has no ratings or instructional text. The answer shows equally weighted **Missed it** and **Nailed it** buttons.
- Empty sections and forms have no extra explanation. Dialogs briefly describe consequences; deletion and saving use text labels.
- Icon buttons have accessible names through `aria-label`; primary actions also have `title` tooltips. The reason for a disabled postpone action is included in both. These labels do not occupy screen space; the native app needs equivalent descriptions.

## Geometry

The frame is 992 × 1323 design units. Margins are 32, gaps 16, header 64, tabs 72 and footer 112. The sheet is **928 × 963**, positioned at **32, 200**, in learning, browsing and editing. Four corner marks define its boundary.

Flip and postpone targets are 112 × 112. Action icons are 32; the active marker is 18. Metric labels are 18, values 26 and the deck title 28. Black and white, gray for disabled controls; no shadows, gradients or animation.

Design units do not automatically equal dp. On Boox, check logical density, system insets, touch targets of at least 64 dp and text of at least 18 sp. The handwritten font represents vector strokes. On Next pass, the large epoch numeral is shifted 6 design units to the left for optical alignment below the check mark. Compare all four variants in the gallery section **Next pass · I–IV**: [I](transition-preview-e1.html), [II](transition-preview-e2.html), [III](transition-preview-e3.html), [IV](transition-preview-e4.html). The comparison screens show their respective active tabs and counts; Start pass is a static control. The original Next pass screen keeps its working link to learning in II.

## Behavior

The open section and active epoch are independent. Tapping the section with ▶ returns to the saved pass position. Ratings appear only on the answer. A card can be postponed once per pass, except for the last card, and only before the answer is first revealed. Postpone stays disabled after returning to the question. Its icon remains in place without additional visible text. The answer-revealed flag survives section changes and app restarts, and resets on the next pass of that epoch.

Tapping the sheet does not flip the card; the flip button works in both directions. In the editor, the pen draws and the stylus side button erases; controls accept pen and finger input. The eraser removes a whole stroke. Undo supports at least 20 actions in the intended app. Either card side may be blank.

Exit the editor by saving or confirming deletion. For a new card, Cancel asks before discarding its draft. System Back offers **Save and exit / Keep editing**. Blank sides are never silently discarded.

Menus and dialogs do not dim the background. Dialog backgrounds are inert for touch and keyboard focus. The native app must move focus into a dialog and restore it on exit. Import and export use the system file picker; import always creates a new deck.

## New windows from the November 3, 2026 specification

- [All done for today](dlg-today-done.html): a separate waiting dialog, without dates or a countdown. In this example, II is not due yet and both I and New are empty. **Open New** opens the [empty pool](pool-waiting.html), where the create button is available. The underline moves to New while ▶ stays on II. Tapping II checks again. Unlike **Nothing to review yet**, this state has cards available for future review.
- [Extra pass through I](transition-insert.html): after a batch of 40 ratings in a long pass through IV. The screen shows I and 40 cards; **Start pass** opens the [first card](learn-insert.html). The interrupted 100-card pass through IV keeps its position after card 40. This extra pass does not increment the pass count for I.
- During the extra pass, the **IV, 100** tab opens [browsing in IV](browse-e4-insert.html), with flipping, paging, deletion and return to New. Counts stay at `LONG_COUNTS`, ▶ stays on I and successes read `1 / 3`. There are no ratings or postpone action. Browsing does not move the interrupted pass position; tapping I returns to the extra pass. Its menus and dialogs use the same links.
- While waiting, **I, 0** opens [empty I](empty-e1-waiting.html), with `WAITING_COUNTS` and ▶ on II. There are no card actions or paging controls. Tapping II checks again and opens **All done for today**.
- [Resume IV](transition-resume.html): after the extra pass, **60 of 100 remaining** is shown. **Continue** opens [card 41 of 100](learn-resume.html). Order and ratings are preserved; pass initialization and interval checks do not run again. If both I and New are empty, there is no extra pass.

The first cards of the extra and resumed passes have answer views, question views after revealing the answer, and context menus. Postpone is disabled after revealing the answer, as in the base scenario. These states appear under **New transitions** in the gallery and on the shared canvas. The intermediate 40 ratings are not simulated; these are separate examples of starting and resuming a pass.

## Startup and error screens from the implementation

- [We need file access](startup-file-access.html): a dialog with **Open settings**.
- [Database damaged](startup-database-damaged.html): `Deckly/deckly.db` could not be opened; **Close** is the only action.
- [Database not found](startup-database-missing.html): `deckly.db` is missing and card drawings are unchanged; **Close** is the only action.
- [Loading the app](startup-loading.html): Deckly centered on a white screen, without an indicator or actions.
- [Drawing unavailable](card-damaged.html): the normal header, tabs and controls with a blank sheet. **Drawing unavailable** is an accessibility label only; there is no visible warning.

The three startup dialogs use the existing style on a white background, without a deck header, new icons or colors. Their wording is the English equivalent of the implemented startup messages. Buttons look enabled, but HTML does not open system settings or close the app. Loading does not automatically navigate. The damaged-card example shows the question without simulating flipping; other links open base static examples. In the app, both sides of a damaged drawing file are blank and the card can be deleted using the normal menu.

## Shapes in the editor

A proposed mode for creating and editing cards, on both question and answer sides. The app implementation and specification are outside this design task.

[New card menu](editor-new-shapes.html) · [Edit card menu](editor-edit-shapes.html) · [Answer side menu](editor-new-back-shapes.html) · [Selected shape](editor-new-shape-selected.html) · [Resizing](editor-new-shape-resized.html) · [Arrow direction](editor-new-shape-arrow.html) · [Rotation](editor-new-shape-rotated.html).

- The shape icon sits beside the pen widths in the bottom toolbar. The menu has 15 elements: line, arrow with a shaft, double arrow with a shaft, rectangle, oval, triangle, diamond, hexagon, star, moon, sun, heart, cloud, lightning and drop. Every item has an accessible name and a tooltip. No shape is selected when the palette opens. Opening it does not insert anything; focus goes to the palette itself, not to a shape. Lines and arrows have two endpoint handles: drag either end to change length and direction, including vertical, diagonal and left-pointing arrows. The other endpoint stays fixed. The shaft moves the whole object. Arrowheads keep their size when the shaft length changes. Other shapes have four resize handles and a rotation handle above the frame.
- All shapes are hollow: black outlines with no fill. Existing handwriting remains visible through them. **Outline width** adjusts the outline from 1 to 12 design units, default 3, with a number and a line sample. Pen width is independent. Slider changes appear immediately in the menu. The chosen width is remembered for the next opening, independently of which shape was last inserted or selected. The HTML demo stores it in browser local storage (with a page-local fallback when storage is unavailable); new static boards show the default 3.
- Choosing a shape closes the menu and immediately inserts a new, selected shape at the center of the sheet with the width currently set in the palette. No additional tap on the sheet is required. Its editing handles appear: two endpoints for lines and arrows, or a dashed frame with four corners and a rotation handle for other shapes. Touch near the outline to select a shape and drag its outline to move it; drag a corner to adjust width and height independently along the rotated axes, or the upper handle to rotate around the center. The selection rectangle and hollow interior do not select or move the shape, leaving the interior available for writing. The demo uses an invisible selection band extending 8 design units beyond each edge of the visible stroke; the endpoint, corner and rotation handles remain separate editing targets. Stretching preserves outline width. Handle targets are 64 × 64, the minimum shape frame is 80 × 80 and the shortest line shaft is 64 units. Shapes and their handles stay inside the sheet; move a shape away from an edge if it needs more room to rotate.
- Touching the sheet away from any outline or editing handle finishes the current edit, keeps the shape and clears selection. This includes the hollow interior: the same pen contact is left available to start writing. The check also finishes editing and restores the normal toolbar. The cross cancels a new insertion or restores an existing shape to its state before editing. Tap near the outline of a placed shape to select it again; touching another outline switches selection to that shape. The shape button reopens an unselected palette to add another shape. Opening the palette finishes the current edit; changing the slider affects new insertions, not existing shapes. Multiple shapes can be added.
- The HTML demo accepts pen and mouse input; the intended tablet canvas uses the pen. Placement, movement and resizing exist only on the current page. Saving, switching sides, erasing and undo history are not implemented by this prototype. Without JavaScript, static boards remain available; choosing an item opens the star placement example.

**Shapes in the editor** contains 32 boards: menu, immediate insertion, selection, moving, resizing, applied shape, arrow direction and rotation, for both editors and both sides. Each board opens in its depicted state, including in `canvas.json`. Sheet and header geometry are unchanged. Explanations appear in the gallery, not on the card sheet.

## Voice

Use short, conversational English and natural contractions: **Couldn't open file**, **We haven't deleted anything** and **You can't undo this**. In editor dialogs, **Keep editing** makes the alternative to saving or discarding explicit. **Missed it / Nailed it** are the approved informal rating labels. Destructive actions retain clear consequences; no additional slang is introduced.

## Scope and verification

These are linked mockups with an interactive shape demo. Forms do not save data, the pen does not draw freehand strokes, and ratings lead to fixed examples. Separate pages do not form a persistent session. The gallery is a desktop document and may scroll.

The set contains **133 screens and 133 boards**. Checks cover local links, icon labels, Roman epoch labels, no instructional copy during learning, ratings only on the answer, disabled postponing after revealing the answer, and inert dialog backgrounds. Example: `learn-front → learn-back → learn-front-seen`, also covered for II, the extra pass and the resumed pass.

For every tab in **New transitions**, the destination has the same section and card count. Zero opens an empty section; a positive count opens browsing or learning with the same denominator. The specification allows one exception: waiting II opens **All done for today** with the same 23 cards. Separate checks cover the counts, active marker, lack of ratings, menu and flip in interrupted IV browsing, and the counts, active marker and absent card actions in empty I. Links into base static scenarios may change the active epoch; these mockups do not simulate a continuous session.

English labels, titles, tooltips, accessibility descriptions, gallery notes and example card content are generated from the sources. HTML declares `lang="en"`. Generation must be deterministic. Shape geometry checks cover minimum size, sheet boundaries, independent resizing on rotated axes, fixed opposite endpoints, arbitrary arrow direction, constant arrowhead size, rotation and constant outline width.

This directory describes the design and its HTML demo. Application behavior and application test results are documented by their own implementation tasks; no assumptions about their language or current test coverage are made here.

Geometry is checked from the CSS frame. Browser rendering and the physical Boox have not been verified: browser startup was blocked in this environment.
