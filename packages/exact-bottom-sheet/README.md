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
bun run exact ios --device --run   # build for a connected iPhone, install and launch it (--phone <udid|name>)
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
  `rustup target add aarch64-apple-ios-sim --toolchain 1.97.0`. A phone
  (`--device`) needs `aarch64-apple-ios` instead, an Apple Development identity
  and a provisioning profile on this Mac that covers the phone and the bundle id
  (a team wildcard profile does); Exact picks them automatically.
- About 10 GB of free disk for the first iOS build (the Rust target directory
  alone is several GB).

The command checks rustup, the pinned toolchain, the checkout's dependencies,
Xcode and the simulator target, and prints what to run for each one that is
missing.

## Using the view

### The `BottomSheet` component

The package ships a Contract component, `BottomSheet`, at the `/sheet` subpath.
Its children are the sheet's content:

```contract
use BottomSheet, SheetConfig, SheetDetent, sheetConfig from "@swmansion/exact-bottom-sheet/sheet"

fn d(v: string): SheetDetent = SheetDetent(value=v, programmatic=none, insets=none, scrimOpacity=none)

component App
  state index = 0
  action set(i: number)
    index = i
  action ignore
    let unused = 0
  view
    column position="relative" flex=1
      button press=set(1)
        text "Open"
      BottomSheet(id="sheet", index=index, detents=[d("0"), d("content")], config=SheetConfig(sheetConfig(), modal=some(true), scrimColor=some("rgba(0, 0, 0, 0.5)"), surfaceColor=some("#ffffff")), indexchange=set, settle=set, closerequest=ignore)
        text "Content authored in Contract"
```

As `BottomSheet.tsx` does with its content wrapper, the component lays the
content out at the sheet's content region: the widest card's width (so the
content follows a detached card and is scaled at a narrower detent) and the
region's height (so a `flex=1` child fills the sheet, and a
`scroll flex=1 min-height=0` child scrolls inside it). A zero-height marker
after the children gives a `content` detent its height, as React Native's
content-height marker does. The app binds nothing.

Place it last among its parent's children: it fills its parent's box
(`position: absolute; inset: 0`), takes no touches outside the card and the
scrim (they reach what is under it), and keeps the content invisible until the
sheet has reported its region.

Props:

- `id: string`: names its nodes, `${id}-content` (the content node), `${id}-end`
  (the marker) and `${id}-sheet` (the tag), for tests.
- `index: number`, `detents: SheetDetent[]`: as on the tag (below).
- `config: SheetConfig`: every other prop of the tag, one field each, with the
  tag's types (`option<…>`, `none` for unset). `sheetConfig()` is every field
  unset; change some with copy-with:
  `SheetConfig(sheetConfig(), modal=some(true))`.
- `config.lazy`: the component's own option, as `lazy` on React Native. The
  content is in the tree only while the sheet is open or still closing: it
  mounts in the same update that `index` selects an open detent, and unmounts
  once the sheet settles at a closed detent, so it stays visible while the sheet
  closes, and reopening mid-close keeps it. Its own component state starts over
  on each open. No effect when no detent is closed. Unlike React Native, the tag
  stays mounted: it is what reports the sheet coming to rest (through `change`,
  below), and on the web a new tag attaches only after the next paint, too late
  to report a close that starts right after an open. At rest it raises no events
  and holds no content. The adapters adopt the new content before applying the
  open `index`, so the sheet opens to its height.
- `config.nativeOverlay`: the component's own option, as `nativeOverlay` on
  `ModalBottomSheet`: a modal sheet is lifted above everything, native modal
  routes included, and sized to the window wherever it is mounted (see the
  limitations below). No effect on an inline sheet.
- Events: `indexchange`, `settle` and `closerequest` (below). Their handlers
  must be actions of the root component (Exact's rule for a package view's
  events). Contract has no optional action props, so all three are required:
  bind an action that does nothing to one you don't need. `TrackedBottomSheet`
  takes the same props plus `positionchange`, which runs its action every frame
  while the sheet moves, so it is a separate component rather than a fourth
  required event.

The component's file declares the interface's shapes (`SheetDetent`,
`SheetInsets`, `SheetShadow`, `SheetPosition`, `SheetRegion`) again: a package's
Contract file is resolved where it really is, where no `node_modules` holds the
package, and `use` takes only `.contract` files, so it can't `use` `exact.json`.
Shapes are nominal in Contract, so an app that uses the component takes them
from `/sheet` too; the tag checks them field by field against its declared
props, so the raw tag takes them as well.

### The `swm-bottom-sheet` tag

The tag is the package view itself, for when the component doesn't fit (the
demo's Invalid props screen uses it to send props the sheet refuses, with
`pointer-events="none"` on the tag). Its content is an ordinary node beside it,
named by `contentId`, which the app lays out itself (see "How content is
adopted"):

```contract
use SheetDetent from "@swmansion/exact-bottom-sheet"

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
| `scrollExpand`, `scrollCollapse`                                              | `scrollCoordination.expand` / `.collapse`: `enum<none                                                                                                                                            | initial | handoff>`(default`handoff`/`initial`); the compiler refuses any other value |
| `scrimColor`, `scrimLabel`                                                    | `scrimColor`, `scrimAccessibilityLabel`                                                                                                                                                          |
| `handlesCloseRequest: bool`                                                   | passing `onCloseRequest`. Exact can't tell a view which events are bound, so set it to `true` when you bind `closerequest`. Web only; ignored on iOS                                             |
| `trackPosition: bool`                                                         | none: sends `positionchange` only when `true`. Exact can't tell a view which events are bound, and the event runs every frame the sheet moves; `TrackedBottomSheet` sets it                      |
| `nativeOverlay: bool`                                                         | `nativeOverlay`: set by the component with `config.nativeOverlay`; while the scrim shows, the iOS adapter keeps VoiceOver to the sheet (below)                                                   |
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
| `change(string)` (built-in)     | none: `"<width>,<height>,<resting closed>"`, which a child component's own action can handle    |

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
| the content-height marker (a `View` with `nativeID`)                                | an `ExactContentHeightMarker` view with the same `nativeId`, kept at the node's end marker (below), else at the node's bottom                                |
| the surface view                                                                    | a plain view with `surfaceColor`, mounted with `mountSurfaceComponentView` and sized to the sheet plus `surfaceOverflow`                                     |
| events through the Fabric event emitter                                             | the hosting view's delegate calls, emitted as the typed events                                                                                               |

The web adapter mirrors `BottomSheetWebView.tsx` the same way (React Native for
Web's view defaults, `box-none` pointer handling, the marker element) and calls
`attach`, `setCallbacks` and `update` on the controller.

**The end marker.** When the adopted node holds a descendant whose `testId` is
`contentId` without a trailing `-content`, then `-end` (the component's
`${id}-content` holds `${id}-end`), the adapters read the content's height from
that descendant's top in the node's own, unscaled coordinates, as the hosting
view reads React Native's marker after `children`. So a content laid out at the
region's height (taller than its children) still gives a `content` detent its
children's height. iOS observes the end marker's frame (KVO, as it does the
node's); the web adapter observes the node's children's sizes and keeps a hidden
sizer as tall as the content in the wrapper, since the controller re-reads the
marker only when a wrapper child resizes. Without an end marker, the node's
bottom is the content's height, as before.

**The region.** Whenever the content region changes, the adapters raise the
typed `regionchange` and the built-in `change` event with
`"<width>,<height>,<resting closed>"`; the last part is `1` while the sheet
rests at a closed detent and `0` otherwise, and the adapters raise `change`
again when it flips (an open `index` clears it at once, a settle at a closed
detent sets it). A typed event's handler must be an action of the root
component, but a child component may handle `change` with its own action: that
is how `BottomSheet` sizes its content node with no binding in the app, and how
`lazy` knows when to unmount. Contract has no hook for a prop change, so this is
the tag's job rather than the component's.

Rules for the content, which follow from how Exact patches its views. The
`BottomSheet` component follows them; an app using the raw tag follows them
itself:

- **Keep the content node's parent's child list static.** Exact puts a child
  back into its parent's view when that parent's children change. Choose inside
  the content with `if`, never around the content node or its siblings. The
  adapter re-adopts the node after layouts, props changes and frame changes, but
  a static list avoids the round trip. (The component's wrapper holds the
  content node and the tag, always both.)
- **Author the content at its parent's origin**
  (`position="absolute" top=0 left=0`), and keep it out of sight until the sheet
  adopts it (on iOS a package view attaches after the first frame): the raw
  tag's demo puts it before the page, so the page covers it; the component keeps
  it at `opacity: 0` until the region is reported. Exact sets the node's frame
  in its parent's coordinates and resets its transform; the sheet transforms its
  own wrapper, never the node.
- **Size the content to the region.** In React Native the content wrapper is
  laid out against the sheet's content region at the widest card's width; Exact
  lays the node out itself (on iOS at its parent's width, so it overflows a
  detached card), so the sheet reports the region and the content binds its
  `width` and `height` to it. The component does this from `change`; with the
  raw tag, bind them to `regionchange`. With it the content's frames match React
  Native's (measured on iOS with insets 24/24/24 at 180 and 64/64/0 at 100%: 354
  wide in a 354 card, then scaled to 274 in a 274 card). The web adapter lays
  the content out at the widest card's width itself.
- **Give the tag the sheet's box**, usually `position="absolute" inset=0` over
  the area the sheet and its scrim may cover. Touches outside the card and the
  scrim reach what is under the tag (iOS: the hosting view's hit test returns
  nil and Exact passes the touch through, since the tag has no `press=`; web:
  the tag and host take no pointer events). The component's wrapper is
  `pointer-events: none`, its content node and tag `auto`.

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
- **The web adapter leaves a hidden stand-in** where the adopted node was. The
  runtime removes a region's nodes by walking siblings between its anchors, so
  it can't find a node moved into the sheet; when the region goes it removes the
  stand-in, and the adapter drops the node with it.
- **One content node, no surface node.** `surface` is limited to `surfaceColor`;
  a Contract-drawn surface (an image, a gradient) is not adopted. With the raw
  tag, the content follows the region only when the author binds it (see the
  rules above); the component does it itself.
- **Not mapped**: `wrapNativeView` (no worklets in Exact), `surfaceElevation`
  and `surfaceShadowColor` (Android only). `nativeOverlay` is the component's
  `config.nativeOverlay` (Exact's top layer, below); the raw tag has none.
- **Without `nativeOverlay`, the scrim covers the tag's box**, not the window:
  give a modal sheet's tag the whole screen (the demo's screens hide the
  navigation bar and draw their own header, as the React Native example's do).
- **VoiceOver and `nativeOverlay`**: while an overlay sheet's scrim shows, the
  component marks its layer `aria-modal` (Exact moves VoiceOver into it; the web
  gets the attribute) and the iOS adapter marks each ancestor up to the window
  `accessibilityViewIsModal`, restoring them after, so the rest of the app is
  hidden, as React Native's window-level overlay does. Checked with UIKit's
  accessibility tree (argent `describe` with app accessibility on); Exact's own
  `tree --ax` can't model modal ancestors and still flags the page.
- **Content is found by `testId`**, which Exact keeps in production builds
  (checked with `EXACT_UPDATE_TRUST=production`). The component names it
  `${id}-content`, so pick an `id` the app's own test ids don't use.
- **`nativeOverlay` is Exact's top layer**: the component makes the node that
  holds the content and the tag a `popover="manual"` the size of the viewport
  and shows it with `showPopover` on the tag's first `change`, and again on each
  open from rest, so the sheet opened last is in front (React Native's
  `openOrder`). iOS lifts it above route modal sheets; the web puts it in the
  browser's top layer. No reparenting into a `UIWindow` and no touch handler of
  its own, as React Native's glue needs. While the sheet is closed the layer
  takes no touches. Exact's agent paints popovers itself and can't press through
  them under `--touch platform`, so overlay screens are checked by hand
  (argent), not by `app.test.contract`.
- **`positionchange` runs an action every frame** while the sheet moves (as
  `onPositionChange` fires every frame); bind it only when needed
  (`TrackedBottomSheet`, which sets `trackPosition`; a plain sheet sends none).
  Whether the action's render lands in the same frame as the sheet (React
  Native's UI-thread worklets do) is unmeasured: the simulator's recorder
  captures too few frames to tell.
- **Close requests**: `closerequest` is web only, as `onCloseRequest` is on
  React Native (Android and web); on iOS both handle VoiceOver's escape gesture
  in the shared hosting view.
- **A slow drag on a list at its top can end at a different detent** than in the
  React Native example, near a snap midpoint. Measured with the same injected
  drag (2026-11-12): both sheets see identical per-update travel and release
  velocity, but in the React Native app the sheet's pan is recognized one touch
  move later, so it travels that much less (23 pt for an injected drag, a few
  points for a slow finger). The library code is the same; the extra wait comes
  from the React Native app's gesture setup, not the sheet.
- **Exact's agent clock is virtual**, but the sheet's spring runs on
  `CADisplayLink` and Core Animation (iOS) and `requestAnimationFrame` (web),
  not on Exact's session clock (`context.frames`). Drive it with real time:
  `clock +N real`, and on iOS real touches need `--touch platform`.
- On the web, the demo's dev server must not watch a directory whose path is a
  prefix of the package's (Exact's watcher matched `packages/exact` against
  `packages/exact-example` and rebuilt forever), hence the directory names.
- The Exact demo ports the React Native example screen by screen (each demo a
  route; the ones it can't reproduce and the differences are listed at the top
  of its `app.contract`); the colours are the React Native demo's literals, so
  Exact warns that they ignore dark mode.

## Pinned Exact

`956ae33a35ffa5313d7be3cebc55ed84f9480e0a` (expo/exact, 2026-11-09), also in
`package.json` (`exact.commit`).
