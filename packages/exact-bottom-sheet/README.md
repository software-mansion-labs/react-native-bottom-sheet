# @swmansion/exact-bottom-sheet (experimental)

The bottom sheet of `@swmansion/react-native-bottom-sheet` as a package view for
[Exact](https://github.com/expo/exact), Expo's Contract-based framework, on iOS
and the web. It reuses the library's own sheet implementations in place:

- **iOS**: `ios/HostingView/*.swift` (`BottomSheetHostingView` and its
  extensions, `CriticalSpring`, `SheetPanGestureRecognizer`, `Types`), compiled
  unchanged into the package's Swift module next to a thin adapter,
  `apple/ExactBottomSheet.swift`.
- **Web**: `src/web/*.ts` (`BottomSheetWebController` and what it imports),
  bundled unchanged into `web/generated/sheet-controller.js` and driven by a
  thin adapter, `web/index.js`.

The package is private and not part of the npm package (the root `package.json`
`files` list doesn't include `packages/`). The demo app is
`packages/exact-demo`.

## Commands

From the repository root:

```sh
bun run exact web          # build the demo for the web (packages/exact-demo/target/web-dist)
bun run exact web --run    # serve the demo's dev loop at http://127.0.0.1:8765/ (--port <n>)
bun run exact ios          # build the demo for an iOS simulator and install it
bun run exact ios --run    # ...and launch it (--sim <udid|name> or EXACT_SIM when several are booted)
```

`scripts/exact.mjs` finds or clones the pinned Exact checkout, checks the
prerequisites, runs `scripts/prepare.mjs`, installs the demo's dependencies and
runs the demo's own `exact.mjs` verb (`web-build`, `web`, `ios`). Inside
`packages/exact-demo`, `bun exact.mjs <verb>` works too once `prepare.mjs` has
run (`agent`, `test`, `verify`… see `bun exact.mjs help`).

Environment:

- `EXACT_HOME`: the Exact checkout, default `~/.cache/exact/checkout`. Cloned at
  the commit pinned in `package.json` (`exact.commit`) when missing; a checkout
  at another commit is used with a warning.
- `EXACT_BUN`: a Bun binary of the pinned version; default
  `~/.bun-1.4.2/bin/bun`, then `bun` on `PATH`.
- `EXACT_FEEDBACK`: the command sets it to `0` unless you set it, so Exact's CLI
  sends no feedback notes to Expo.

## Prerequisites

- Bun 1.4.2 (Exact pins it). Side by side with another Bun:
  `curl -fsSL https://bun.sh/install | BUN_INSTALL=~/.bun-1.4.2 bash -s bun-v1.4.2`
- rustup (no toolchain needed up front):
  `curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path --default-toolchain none`.
  The command puts `~/.cargo/bin` on `PATH` itself.
- Exact's own setup, once:
  `(cd ~/.cache/exact/checkout && ~/.bun-1.4.2/bin/bun scripts/exact.mjs setup)`.
  It installs the pinned Rust toolchains, wasm-bindgen, Binaryen, the checkout's
  dependencies and the Hermes bundles. It ends with an error when Chrome is
  missing; that only matters for Exact's headless web driver (`agent web`,
  `test web`), not for building or serving the demo.
- iOS: Xcode, and Rust's simulator target, which the setup did not add here:
  `rustup target add aarch64-apple-ios-sim --toolchain 1.97.0`.
- About 10 GB of free disk for the first iOS build (the Rust target directory
  alone is several GB).

The command checks rustup, the pinned toolchain, the checkout's dependencies,
Xcode and the simulator target, and prints what to run for each one that is
missing.

## Using the view

```contract
use SheetDetent, SheetPosition, SheetRegion from "@swmansion/exact-bottom-sheet"

component App
  state index = 0
  action open
    index = 1
  action changed(i: number)
    index = i
  view
    column position="relative" flex=1
      column testId="sheet-content" position="absolute" top=0 left=0 width="100%"
        text "Content authored in Contract"
      column position="absolute" inset=0
        button press=open
          text "Open"
      swm-bottom-sheet contentId="sheet-content" position="absolute" inset=0 index=index detents=[SheetDetent(value="0", programmatic=none, insets=none, scrimOpacity=none), SheetDetent(value="content", programmatic=none, insets=none, scrimOpacity=none)] modal=true scrimColor="rgba(0, 0, 0, 0.5)" surfaceColor="#ffffff" surfaceCornerRadii=[16, 16, 0, 0] indexchange=changed
```

Props (all strings to the native side; non-strings as JSON):

| Prop                                                                          | React Native counterpart                                                                                                                                                                         |
| ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `contentId: string`                                                           | `children`: the `testId` of the Contract node to adopt as content                                                                                                                                |
| `detents: SheetDetent[]`                                                      | `detents`. `value` is a string: points (`"256"`), a percentage (`"50%"`) or `"content"`; `programmatic`, `insets` (`left`, `right`, `bottom`) and `scrimOpacity` (modal only) as in React Native |
| `index: number`                                                               | `index`                                                                                                                                                                                          |
| `topInset`, `modal`, `animateIn`, `animateContentHeight`, `settleDuration`    | the same names (`modal: true` is `ModalBottomSheet`)                                                                                                                                             |
| `scrollExpand`, `scrollCollapse`                                              | `scrollCoordination.expand` / `.collapse`: `"none"`, `"initial"`, `"handoff"` (default `handoff` / `initial`)                                                                                    |
| `scrimColor`, `scrimLabel`                                                    | `scrimColor`, `scrimAccessibilityLabel`                                                                                                                                                          |
| `handlesCloseRequest: bool`                                                   | passing `onCloseRequest`. Exact can't tell a view which events are bound, so set it to `true` when you bind `closerequest`. Web only; ignored on iOS                                             |
| `surfaceColor`                                                                | `surface`, limited to a fill colour (see Limitations)                                                                                                                                            |
| `surfaceCornerRadii` (TL, TR, BR, BL), `surfaceCornerCurve`, `surfaceShadows` | `surfaceStyle` corner radii, `borderCurve`, `boxShadow`                                                                                                                                          |

Events, each a typed event whose handler must be an action of the root component
(Exact's rule for package views):

| Event                           | React Native counterpart                                                                        |
| ------------------------------- | ----------------------------------------------------------------------------------------------- |
| `indexchange(number)`           | `onIndexChange`                                                                                 |
| `settle(number)`                | `onSettle`                                                                                      |
| `positionchange(SheetPosition)` | `onPositionChange` (`position`, `index`)                                                        |
| `closerequest()`                | `onCloseRequest` (web: Escape on a modal sheet with `handlesCloseRequest`; never raised on iOS) |
| `regionchange(SheetRegion)`     | none: the content region's size (see below)                                                     |

Colours accept `#rgb`, `#rgba`, `#rrggbb`, `#rrggbbaa`, `rgb()`, `rgba()` and a
few names on iOS; any CSS colour on the web.

Props are validated as in React Native (`detents`, `index`, `settleDuration`,
`scrimOpacity`, `topInset` and insets, with the same messages). An invalid
update is refused as a whole: the adapter logs the error (iOS: Exact's log; web:
the console) and throws from `setProps`, and Exact keeps the last accepted
props. The web adapter runs the library's own validators from
`src/bottomSheetUtils.ts`; the iOS adapter carries a Swift port of them.

When `contentId` changes or the view is destroyed, the adopted node goes back to
its parent at its original place among its siblings.

## Architecture

### How content is adopted

An Exact package view is a leaf: it takes props, not Contract children. The
sheet's content is therefore an ordinary Contract node placed beside the tag,
named by the tag's `contentId` (its `testId`), and the adapter moves that node's
platform view into the sheet:

- **iOS**: Exact draws a Contract node that has props as a `NodeView`, a plain
  `UIView`. The adapter finds the view whose `accessibilityIdentifier` (Exact's
  `testId`) is `contentId` among the siblings of the tag's own view, then of its
  ancestors, and inserts it into a wrapper view it mounts in the hosting view's
  card with `mountChildComponentView`, the call the Fabric glue makes for the
  `BottomSheetContentWrapperView`. Exact keeps laying the node out and drawing
  it; touches reach it through ordinary UIKit hit testing, and its scroll views
  are real `UIScrollView`s, which the hosting view's scroll coordination finds
  as it finds React Native's.
- **Web**: Exact's nodes are DOM elements. The adapter finds
  `[data-testid="<contentId>"]` the same way and moves it into the content
  wrapper element, inside the eight elements `BottomSheetWebView.tsx` builds.

What the Fabric glue feeds the hosting view, and where it comes from here:

| Fabric glue (`ios/BottomSheetComponentView.mm`, `BottomSheetContentView.mm`)        | Exact adapter                                                                                                                                                |
| ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| detent dictionaries from `normalizeDetents` (JS)                                    | the same dictionaries, built from `detents`/`topInset`/`modal` (including the widest-card flag)                                                              |
| the content wrapper laid out by Yoga against the pushed `contentRegionInset`        | the adapter's wrapper view, sized to the content region (sheet height less `contentRegionInset`, between the widest card's side insets) on every layout      |
| `contentRegionStateDidCommit` + `contentRegionLayoutDidApply` (state commit, mount) | both called when the region changes (the wrapper's layout is synchronous), and `contentRegionLayoutDidApply` again whenever the adopted node's frame changes |
| the content-height marker (a `View` with `nativeID`)                                | an `ExactContentHeightMarker` view with the same `nativeId`, kept at the adopted node's bottom                                                               |
| the surface view                                                                    | a plain view with `surfaceColor`, mounted with `mountSurfaceComponentView` and sized to the sheet plus `surfaceOverflow`                                     |
| events through the Fabric event emitter                                             | the hosting view's delegate calls, emitted as the typed events                                                                                               |

The web adapter mirrors `BottomSheetWebView.tsx` the same way (React Native for
Web's view defaults, `box-none` pointer handling, the marker element) and calls
`attach`, `setCallbacks` and `update` on the controller.

Rules for authors, which follow from how Exact patches its views:

- **Keep the content node's parent's child list static.** Exact puts a child
  back into its parent's view when that parent's children change. Choose inside
  the content with `if`, never around the content node or its siblings. The
  adapter re-adopts the node after layouts, props changes and frame changes, but
  a static list avoids the round trip.
- **Author the content at its parent's origin**
  (`position="absolute" top=0 left=0`), and place it before the page so the page
  covers it until the sheet adopts it (on iOS a package view attaches after the
  first frame). Exact sets the node's frame in its parent's coordinates and
  resets its transform; the sheet transforms its own wrapper, never the node.
- **Size scrolling content to the region.** In React Native the content wrapper
  is laid out against the sheet's content region; Exact lays the node out
  itself, so the sheet reports the region (`regionchange`) and a scrolling
  content binds `height` to it (the demo's scroll screen). A content detent is
  measured from the node's own height.
- **Give the tag the sheet's box**, usually `position="absolute" inset=0` over
  the area the sheet and its scrim may cover. Touches outside the card and the
  scrim reach what is under the tag (iOS: the hosting view's hit test returns
  nil and Exact passes the touch through, since the tag has no `press=`; web:
  the tag and host take no pointer events).

### Generated files

`scripts/prepare.mjs` writes two gitignored files:

- `apple/exact.json`, the Apple implementation manifest. Exact resolves
  `sources.swift` relative to the implementation as installed under the app's
  `node_modules`, where Bun links every file on its own (and skips symlinks), so
  a relative path back into `ios/` can't resolve there. The manifest lists
  absolute paths: the adapter and every `ios/HostingView/*.swift`, so a new file
  there is picked up.
- `web/generated/sheet-controller.js`, `src/web/controller.ts` and the
  validators of `src/bottomSheetUtils.ts` bundled with Bun. Exact bundles a web
  implementation's imports as page code only inside the implementation's
  directory (elsewhere it refuses `document` and timers).
  `src/bottomSheetUtils.ts` imports `react-native`, which the bundle resolves to
  an inert stub; the script fails if the bundle still references `processColor`,
  `StyleSheet` or `I18nManager`.

## Limitations

- **Android is not supported.** Exact's Android host paints Contract content on
  a Canvas rather than as Android views, and a hosted view is a leaf that only
  gets synthesized clicks, so the Kotlin sheet could neither host Contract
  content nor receive drags.
- **One content node, no surface node.** `surface` is limited to `surfaceColor`;
  a Contract-drawn surface (an image, a gradient) is not adopted. The content
  isn't relaid out against the widest card's width when detents have side insets
  (Exact lays it out at its own width); it is still scaled with the card.
- **Not mapped**: `lazy`, `nativeOverlay`/`openOrder` (the sheet covers the
  tag's box, not the window), `wrapNativeView`, `surfaceElevation` and
  `surfaceShadowColor` (Android only).
- **The scrim covers the tag's box.** A modal sheet in the demo covers the demo
  area below its tab row, not the whole window.
- **`positionchange` runs an action every frame** while the sheet moves (as
  `onPositionChange` fires every frame); bind it only when needed.
- **Exact's agent clock is virtual**, but the sheet's spring runs on
  `CADisplayLink` and Core Animation (iOS) and `requestAnimationFrame` (web),
  not on Exact's session clock (`context.frames`). Drive it with real time:
  `clock +N real`, and on iOS real touches need `--touch platform`.
- On the web, the demo's dev server must not watch a directory whose path is a
  prefix of the package's (Exact's watcher matched `packages/exact` against
  `packages/exact-example` and rebuilt forever), hence the directory names.
- The Exact demo is a single screen with three demos chosen by state, so Exact
  warns about screens chosen by state; the colours are the React Native demo's
  literals, so Exact warns that they ignore dark mode.

## Pinned Exact

`956ae33a35ffa5313d7be3cebc55ed84f9480e0a` (expo/exact, 2026-11-09), also in
`package.json` (`exact.commit`).
