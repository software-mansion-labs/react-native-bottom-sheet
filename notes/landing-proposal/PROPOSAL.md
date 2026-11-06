# Landing overview: proposal

Status: proposal, nothing in `docs/` has changed. Try it first:
[`index.html`](index.html) is a self-contained prototype running the **real
library** from this checkout's `src/`. "Show design notes" (bottom right)
annotates the sections as you scroll.

## The idea

**The page is a stack of sheets.** There are no device frames anywhere, because
the library runs on iOS, Android, and the web, and a phone would say otherwise.

- **The hero is a real sheet on the page.** It's a full-width `BottomSheet` with
  a navy blueprint as its surface. Drag it from anywhere and the headline
  inverts exactly along its edge, while the blueprint underneath names what's
  native and what's React.
- **Every section is a sheet with a snap point.** As you scroll, the next
  section rises over the current one, which recedes and dims the way a screen
  does under a sheet. When you stop, the page settles on the next section the
  way a sheet settles on a detent.
- **Every example is a real sheet in a real app scenario,** on a plain frame
  with no device around it, doing the job it would do in that app. Each frame
  rises from the section's bottom edge to just above its title. Each sheet rests
  where it can move both up and down, and a closed one offers a way back.

Three things make this more than a trick:

1. **It is the pitch.** "Native mechanics. Your surface." is the library's real
   split, and the hero shows it as two layers you can pull apart.
2. **It is the API.** The inversion runs on `onPositionChange`, shown live on
   the sheet. The visitor sees position tracking before reading about it.
3. **It is on-brand.** The navy, the isometric line art, and the A/B/C callouts
   come straight from the existing cover image, so the page, the README, and the
   social card read as one thing.

## Header

The same as the Reanimated and Gesture Handler docs, measured from them: 60px
tall and transparent, with a two-line wordmark 24px from the left, a 72×36
segmented theme toggle, and a 24px GitHub icon. It has this library's name, and
**no logo or version dropdown for now**.

The toggle works. In dark mode the hero's roles swap, a dark page with a light
sheet rising, while the app screens and navy blocks keep their own colors. It
starts from the system preference and remembers the choice.

## What the research says

| Source                                                                                                                                            | Keep                                                                                                              | Leave                                                                                          |
| ------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| [Reanimated](https://docs.swmansion.com/react-native-reanimated/)                                                                                 | Each demo paired with the code behind it. Aeonik, navy, square CTAs.                                              | An illustrated hero with no link to the product. Phone mockups. Demos that play by themselves. |
| [Gesture Handler](https://docs.swmansion.com/react-native-gesture-handler/)                                                                       | Touch before reading ("Tap and drag the circles"). Hard offset shadows.                                           | Abstract tiles below the fold, rather than the product itself.                                 |
| [Enriched Markdown](https://enriched.swmansion.com/markdown/)                                                                                     | The strongest idea of the three: the page chrome _is_ the product (rulers, scrollbars, format bar). Dashed edges. | Features shown mostly as videos, since we can run the real thing.                              |
| [Sonner](https://sonner.emilkowal.ski/), [Vaul](https://vaul.emilkowal.ski/)                                                                      | The hero's main action is the product. Sonner pairs every option with its code.                                   | Their near-absence of narrative: we have to explain native vs JS, they don't.                  |
| [Evil Martians, 100 dev tool pages](https://evilmartians.com/chronicles/we-studied-100-devtool-landing-pages-here-is-what-actually-works-in-2025) | Centered hero. Two CTAs. Trust straight after the hero. Features framed as problems.                              |                                                                                                |

Evil Martians warns against flashy interactions and says live embeds suit only
narrow tools. A bottom sheet is about as narrow as tools get, and the
interaction is the product, so I think the live hero passes that test. I found
no data on whether interactive heroes convert better, so this rests on judgment,
not numbers.

## Page structure

Each row after the hero is one sheet in the stack.

| #    | Section                         | Visitor learns                               | Interaction                                                                                                                                                      | Status in prototype                          |
| ---- | ------------------------------- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------- |
| Hero | Native mechanics. Your surface. | What it is, and that it's real               | Drag the sheet anywhere, or press the A/B/C callouts                                                                                                             | Real library                                 |
| 01   | "Stops where you mean it to"    | Detents, `'content'`, insets, `programmatic` | **Music player:** the queue is the sheet. Presets, each with its code and live height ticks. The album shrinks away as the queue rises                           | Real library                                 |
| 02   | One gesture, two owners         | `scrollableNegotiation`                      | **Video page:** comments are the sheet. Drag on the list while a live trace shows the handoff                                                                    | Real library, real values                    |
| 03   | Your JS can be busy             | Why native matters                           | **Portfolio tracker:** the watchlist is the sheet, with prices updating from React every 5 seconds. Block JS and no update arrives, while the sheet keeps moving | Real sheet, **illustrated block**, see below |
| End  | We are Software Mansion         | Who's behind it                              | "Hire us", then the logo and legal line, the same as the Reanimated docs, with the Gobi Maps sponsor line added                                                  | Our own background                           |

Each example has a **"Check docs"** button: 01 goes to Detents and index, 02 to
Scrollable negotiation, and 03 to How it compares, the closest page today since
the docs have nothing on threading.

The surface and placement examples were cut to keep the page short. They're
saved in `archive/` and could become live examples on the Surface, Inline
sheets, and Modal sheets pages.

Every section peeks 56px above the fold, so each screen shows the next sheet and
invites the scroll. That strip is a real handle: drag it up or down with a grab
cursor and it settles on the sheet's spring, and a tap or a flick also opens it.

The video and portfolio sheets measure their app's content and rest directly
below it, so no window size leaves dead space between the two.

**On small screens each section is one screen:** the title, the options, and the
example are visible together. The example fills whatever height is left, each
row of options is one swipeable line, and the code sits behind a "Show code"
toggle. Opening it shrinks the example instead of pushing it away. Sections 02
and 03 drop their body text there, and section 01 hides its index row, since
dragging covers it.

Fixed heights in the examples scale to fit their frame, because the library
rejects detents that fall out of order. Each example also has an error boundary,
so a bad layout can't blank the whole page. **The final page should keep both.**

**Section 03 must become recordings from iOS and Android.** On the web the sheet
and JS share a thread, so the page can only pretend, and it says so. There's
deliberately no competitor side by side: Reanimated-based sheets also run
gestures off JS, so a staged "theirs freezes" clip wouldn't be fair.

## Motion

Emil's rule that matters most here is cohesion: motion should match the
component's personality. Ours is already defined by the library, a critically
damped spring with **no overshoot**, so nothing on the page bounces.

| Element                                     | Motion                                                                                                                                    | Why                                                                  |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Section rising over the last                | Tied to scroll with CSS view timelines: scale to 0.94, dim to 45%, corners round                                                          | It's what a sheet does to a screen, and it stays off the main thread |
| Section settling on its snap point          | The sheet's spring over 450ms, once scrolling stops                                                                                       | The page settles exactly as the product does                         |
| Anything moving with a sheet (height ticks) | `--ease-sheet`, 450ms: `CriticalSpring` from `src/web/criticalSpring.ts`, sampled into CSS `linear()`                                     | The page and the product share one curve                             |
| Headline inversion, hint pill               | Written directly from `onPositionChange`                                                                                                  | Tied to the finger, with no second animation to lag behind           |
| Switching an example's case                 | Heights spring on their own. A change of shape closes the sheet, swaps, and reopens. All through public props, with no library changes    | Nothing jumps, and the demo stays honest about what the API does     |
| Copy and code beside an example             | All variants share one slot as tall as the tallest, and crossfade over 220ms                                                              | The text around the example never moves                              |
| Stock prices updating, every 5 seconds      | Changed digits roll up on a rise and down on a fall, 480ms strong ease-out, staggered 40ms from the right, with a brief green or red tint | Calm enough to read, and the direction is obvious at a glance        |
| Press feedback                              | `scale(0.97)`, 160ms strong ease-out                                                                                                      | The page feels like it's listening                                   |
| Hero copy on load                           | 10px rise and fade, 55ms stagger, once                                                                                                    | Occasional, so it can be pleasant, and it blocks nothing             |
| Code swap, copy icon                        | Fade with 2px blur                                                                                                                        | Hides the crossfade of two states                                    |
| "Block JS" button                           | `clip-path` fills over 4s linear, snaps back in 200ms                                                                                     | Slow while deciding, fast when responding                            |
| Negotiation trace                           | Advances only around real movement                                                                                                        | An idle page stays still                                             |

Per-frame work follows the skill's performance rules: direct `transform` and
`clip-path` writes on single elements, never React state or inherited CSS
variables. Hover is gated behind `(hover: hover) and (pointer: fine)`.

## How the stack works

This describes the prototype, where the slides are page scrolling. The shipped
page in `docs/src/components/Landing/` keeps the same look, but each slide there
is a real `BottomSheet` with stops at the peek and `100%`, so its drag, snap and
shadow come from the library.

- `position: sticky` holds each section while the next rises over it.
- A section taller than the screen sticks only once its bottom is reached, so
  nothing is skipped. This matters most on phones, where most sections are tall.
- The recede is timed by a small invisible element that scrolls normally,
  because a view timeline on the sticky section itself would be unreliable.
- Where scroll timelines aren't supported, the sections still stack. They just
  don't recede. Phones skip the recede for now too.
- **One gesture moves one section.** With a wheel or trackpad, a scroll's first
  movement commits to the next section on the sheet's spring, and the rest of
  that gesture, momentum included, is ignored. So a flick can't skip a section
  or overshoot one. A new swipe during the old one's fading momentum still
  counts. This does take over wheel input, which is the deliberate trade for
  steps that are hard to miss.
- **Lists come first.** A list or code panel under the pointer scrolls before
  the page does, and reaching its end doesn't change section until that gesture
  ends. A section taller than the screen scrolls freely inside, stopping at its
  edge.
- **Touch uses native snapping,** with `scroll-snap-stop: always`, so a hard
  flick stops at each section.
- **Keyboard and scrollbar settle afterwards,** knowing the direction. Native
  `mandatory` snapping alone isn't enough there, because it only knows the
  closest point: two wheel ticks on an early version snapped straight back to
  the top.

## Building it in Docusaurus

1. **Route.** Add `docs/src/pages/index.tsx` for `/`, and move `overview.mdx`
   off `slug: /` to `/overview`. No internal links point at `/` today.
2. **React Native Web.** Add it to `docs/package.json`, plus a small plugin in
   `docs/plugins/` that aliases `react-native` and adds `.web.*` extensions via
   `configureWebpack`. Alias the library to `../src`, so demos can't drift from
   the code.
3. **SSR.** The sheet goes in `BrowserOnly`. Serve a static poster with
   identical geometry so nothing shifts, then mount on idle.
4. **Components.** `docs/src/components/landing/`, one file per section, reusing
   the palette and Aeonik setup in `custom.css`. No animation library is needed.

Shipping in phases keeps each step useful on its own:

- **Phase 1:** hero, the stack, ending. This alone replaces today's overview.
- **Phase 2:** chapters 01 and 02.
- **Phase 3:** the recordings for 03, real testimonials.

**Cost, measured:** the sheet plus React Native Web is **43 KB gzipped** on top
of the React that Docusaurus already ships (135 KB minified, built with esbuild
from this checkout). Docusaurus's own bundler will differ somewhat.

## Accessibility

- The A/B/C callouts are real buttons, so every snap works without dragging.
- The inverted hero copy is a visual duplicate: `aria-hidden`, and out of the
  tab order.
- Reduced motion keeps fades and drops travel: no recede, no nudge, no intro
  slide.
- On phones, the nav strip and the 56px peek stay outside the hero sheet, so
  there's always somewhere to start a page scroll.

## Not yet verified

The prototype was driven with a mouse in a throttled, hidden browser pane, so:

- **Feel and frame rate are unjudged.** Please try it on a real screen. I
  checked geometry and state, not smoothness. That includes how the snap feels
  on a trackpad, where I could only test with discrete wheel ticks.
- **Touch is untested on a real device**, including native snapping and starting
  a page scroll from the strips above.
- **Real trackpads are untested.** Momentum is simulated in my tests, and the
  gesture-end timing may need tuning for how a real trackpad feels.
- **The reduced-motion and keyboard paths are written but untested.**
- **Docusaurus integration is unproven.** The bundling works under esbuild, but
  Docusaurus compiling `../src` from outside the site is the first thing to
  spike.
- **Only checked in the app's built-in Chromium browser.** Safari and Firefox
  still need a pass, especially for the stack.
- **All app data is fictional sample data,** including the portfolio's tickers
  and prices. Product and album art are simple original drawings, which real
  photography could replace.
- **Aeonik is a local copy.** It's encrypted in this repo, so the prototype
  loads Regular, Medium and Bold from `fonts/`, which is gitignored. Without
  that folder the page falls back to the system font. The docs would use their
  decrypted `static/fonts/aeonik` files instead.
- **The header has no logo yet,** only the wordmark. It's ready to sit beside
  one when your designers have it.
- **No testimonials.** I left them out rather than inventing any, and they'd
  need real quotes.

## Decisions for you

1. **Is `/` the landing page**, with the current overview moved to `/overview`?
   I recommend yes.
2. **The headline.** "Native mechanics. Your surface." is my pick. It's a claim
   about architecture rather than speed, so it stays true and needs no
   benchmark.
3. **Is `react-native-web` acceptable as a docs dependency?** The whole concept
   depends on it, and it's what lets the demos be real.
4. **Can someone capture the section 03 recordings on iOS and Android?** If not,
   I'd cut that section rather than ship the illustration.

## Rebuilding the prototype

```bash
node notes/landing-proposal/build.mjs
```

Source is in `src/`. esbuild comes through `bunx`, so the repo gains no
dependency.
