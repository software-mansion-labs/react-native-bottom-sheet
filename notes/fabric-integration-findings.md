# Fabric integration findings: sheet geometry, state, and animation

Technical reference for the investigation behind issues
[#48](https://github.com/software-mansion-labs/react-native-bottom-sheet/issues/48)
/
[PR #49](https://github.com/software-mansion-labs/react-native-bottom-sheet/pull/49)
and the native-measurement refactor that followed (`75f7a59` → `287ac5c` on
`main`). Everything below was verified against source (RN core `main` + tags
v0.76–v0.83.2, plus the pinned 0.85.3; react-native-screens `main`) or
empirically — Android on a physical Pixel 10, iOS on the iPhone 17 simulator
with per-frame logging.

---

## 1. How Fabric lays out Android views

- Fabric applies geometry **imperatively, per view**, in
  `SurfaceMountingManager.updateLayout`: `view.measure(EXACTLY w, EXACTLY h)`
  followed by `view.layout(x, y, x+w, y+h)` with Yoga-computed, parent-relative
  frames. It **never sets `LayoutParams`** — they keep whatever default the
  parent generated at `addView`.
- **`needsCustomLayoutForChildren()`**: if a child's _parent_ view manager
  returns `true`, Fabric still calls `measure()` on the child but **skips the
  `layout()` call** — the native parent owns placement. Present in every RN
  version the library supports (verified v0.76.0, v0.79.0, v0.81.0, v0.83.2 and
  `main`):

  ```java
  if (parentViewManager == null || !parentViewManager.needsCustomLayoutForChildren()) {
    viewToUpdate.layout(x, y, x + width, y + height);
  }
  ```

- React-managed views defend against traditional Android traversals:
  - `ReactViewGroup.onMeasure` just echoes the spec size; `onLayout` is a no-op
    ("UIManager handles actually laying out children").
  - **`ReactViewGroup.requestLayout()` is a no-op terminator** — it doesn't set
    flags and doesn't propagate. Layout requests from inside a Fabric subtree
    die at the nearest `ReactViewGroup`. This is the single most consequential
    fact in the whole saga.
- **Hit-testing is native-bounds based.** `JSTouchDispatcher` →
  `TouchTargetHelper` does a DFS over the real Android view hierarchy using
  `view.width/height` and `child.left/top`. A 0×0 view cannot be entered, so its
  entire subtree is untouchable regardless of what the C++ shadow tree thinks.
  Fabric's C++ hit-testing is not used for Android touch targeting.

### The #48 touch bug (0×0 children), three facts combined

1. The library's `BottomSheetViewManager` declares
   `needsCustomLayoutForChildren() = true` → Fabric measures the surface +
   content wrapper but never lays them out.
2. The only code that places them is `layoutSheetChildren`, driven from the
   host's own `onLayout`.
3. The host extends `ReactViewGroup`, whose `requestLayout()` is a no-op — so
   when Fabric inserts children into the container, the resulting
   `requestLayout` dies and **no traversal ever re-runs `onLayout`**.

Whether a given sheet worked depended on _incidental_ window traversals (inset
settling, IME, dialog-show ordering) — hence the device-dependent flakiness:
physical edge-to-edge devices sequence insets/traversals differently than
emulators. Portal sheets were rescued by two accidental triggers (Fabric laying
out the coordinator view cascades into the host; content-detent changes re-run
placement), overlay sheets weren't.

**Fix pattern (from RNS `ScreenContainer` / `CustomToolbar`):** override
`requestLayout()` to post a deduplicated self-relayout. RNS comment for the same
disease: subviews stuck at "position 0,0 … even if Yoga has correctly set their
width and height". RNS posts on `ReactChoreographer` `NATIVE_ANIMATED_MODULE`
("catch the current looper loop instead of enqueueing the update in the next
loop"); a plain `post()` runs after the current mount batch, which is what
matters (children are measured by then). Include the RNS null-guard —
`requestLayout` fires during superclass construction, before fields initialize.

---

## 2. How `<Modal>` and Screens size dialog-hosted content (the state → `adopt()` pattern)

Both converge on the same architecture: **native reports its measured geometry
into Fabric state; the C++ component descriptor forces the shadow node's Yoga
size from that state on every clone.**

### RN core `<Modal>`

- `ReactModalHostView` forwards all children to a `DialogRootViewGroup` living
  in the Dialog's window; its own `onLayout` is deliberately inert.
- `DialogRootViewGroup.onSizeChanged` →
  `StateWrapper.updateState({screenWidth, screenHeight})` (dp).
- `ModalHostViewComponentDescriptor::adopt()` runs on **every create/clone** and
  stamps the Yoga style:

  ```cpp
  layoutableShadowNode.setSize(Size{stateData.screenSize.width, stateData.screenSize.height});
  layoutableShadowNode.setPositionType(YGPositionTypeAbsolute);
  ```

  Absolute positioning detaches the modal node from its in-tree slot (which may
  be 0×0); the explicit size makes Yoga lay the whole subtree out at the dialog
  size. Children then mount with correct nonzero frames **without any Android
  traversal** — a child inserted a minute after `dialog.show()` is laid out by
  Fabric the moment its mount item runs.

- Android-only `createInitialState` pre-seeds the state with the screen size via
  JNI (`getEncodedScreenSizeWithoutVerticalInsets`) so there's no 0×0 first
  frame.
- The dialog window's traversal only sizes the `DialogRootViewGroup` itself
  (parented to a `MATCH_PARENT` FrameLayout content view), feeding the loop:
  window resize → `onSizeChanged` → state → Yoga → remount.

### `RootNodeKind` (`ShadowNodeTraits`)

Set only by `RootShadowNode` and `ModalHostViewShadowNode` (and this library's
sheet in overlay mode). It makes
`LayoutableShadowNode::getRelativeLayoutMetrics` stop its ancestor walk at the
node, so `measure()`/`measureInWindow` and culling for the subtree resolve in
the detached window's coordinate space. **It does not affect Yoga layout at
all** — the size escape hatch is entirely `adopt()`'s `setSize`.

### react-native-screens

- `Screen` extends `FabricEnabledViewGroup`: `onLayout` reports
  `{frameWidth, frameHeight, contentOffsetX, contentOffsetY}` into state (px→dp
  with the view's own display density, epsilon-guarded against setState loops);
  `RNSScreenComponentDescriptor::adopt()` does `setSize` when the frame is
  nonzero, resets to `{YGUndefined, YGUndefined}` when zeroed (rotation).
- Every container manager (`ScreenStack`, `ScreenContainer`, header config)
  returns `needsCustomLayoutForChildren() = true` — native owns the Screen's
  _frame_, Fabric owns everything _inside_ it; the state round-trip reconciles
  the two.
- Anti-collapse hacks where native must measure Fabric children:
  `ScreenStackHeaderSubview.onMeasure` caches the last React-provided EXACTLY
  size and always reports it (`setMeasuredDimension(reactWidth, reactHeight)`)
  so a native `AT_MOST` pass can never zero it.
- `formSheet` (shipping): content wrapped in `ScreenContentWrapper` — Fabric's
  `layout()` call on that wrapper _is_ the content-height signal
  (`onContentWrapperLayout` reads the Yoga height, drives
  `BottomSheetBehavior.maxHeight`, reports the new frame back via state). Their
  wrapper doubles as the sensor because their content region isn't cap-fixed; a
  cap-fixed region (this library) needs a separate sensor — the trailing marker
  view is exactly that mechanism one level deeper.
- `formSheet` (gamma, real `BottomSheetDialog` window): children teleported into
  the dialog; host node sized purely via state → `adopt()` → `setSize`; async
  commit latency closed with a synchronous event flush + an `onPreDraw` gate
  that blocks drawing while a Fabric mount is pending. Pre-draw as _frame gating
  / one-shot signal_ is legitimate; pre-draw as _per-frame corrective layout_
  (the #49 proposal) is a workaround — RNS never re-asserts bounds every frame
  anywhere in their tree. The library's own #32 pre-draw (bounded
  wait-for-measurable gate for the initial content-detent snap) is the
  legitimate kind.

---

## 3. The #48 sizing bug: JS dimension guessing under edge-to-edge

- Under Android edge-to-edge (the Expo / RN default),
  `useWindowDimensions().height` reports the layout window **minus both
  system-bar insets**; on emulators it happens to equal the full frame, which is
  why nothing reproduced there. Numbers from the issue author's Galaxy Note 20:
  `window = 808.8`, `safeAreaFrame = 882.3` (= true screen),
  `insets.top = 25.5`, `insets.bottom = 48`.
- The overlay dialog itself is _forced_ full-screen edge-to-edge by the library,
  so its real height is the full 882.3 — but the JS-computed cap
  (`hostHeight − insets.top`) came out 783.2 instead of 856.8. Fully open, the
  sheet's top sat ~73.5dp (= top + bottom insets) below the status bar. Point
  detents were pre-clamped in JS to the same undersized value.
- The general lesson: **JS dimension hooks describe a window; the sheet may live
  in a different one.** The only exact source is the geometry of the window the
  sheet actually occupies, measured natively and fed into the shadow tree.
  `useSafeAreaFrame` (the #49 JS half) was the closer estimate for the dominant
  case but still a heuristic — it measures the main window's provider, not the
  dialog.

---

## 4. The resulting architecture (single source of truth = native measurement)

One state channel on the sheet node (`BottomSheetViewState`), fields all in dp:

| Field                | Producer                                                      | Consumer                                                                                                                                                             |
| -------------------- | ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `contentOffsetY`     | host, on every sheet movement (`containerTop + translationY`) | `getContentOriginOffset()` — shadow-tree coordinates for `measure()`/`measureInWindow`                                                                               |
| `frameSize`          | host, once measured in its window                             | `adopt()`: `setSize` + `YGPositionTypeAbsolute`, **gated on `nativeOverlay`** (inline stays Fabric-owned)                                                            |
| `contentRegionInset` | host: `height − cap`                                          | `adopt()`: **Yoga bottom padding, every mode** — the in-flow `flex: 1` wrapper resolves to exactly the detent cap; the absolutely-positioned surface ignores padding |

- **The native detent cap** replaces the `maxDetentHeight` prop entirely:
  `cap = height − max(0, topInset − originYInWindow)` (unless
  `extendUnderStatusBar`), where `topInset` = status bars + display cutout from
  `rootWindowInsets` (Android) / `window.safeAreaInsets.top` (iOS), and the
  origin term means only the _overlapping_ part of the inset counts — a sheet
  inside a container that starts below the status bar keeps its full height.
  Recomputed on attach / size change / inset change / layout; JS no longer
  clamps detents (native `resolveDetentSpecs` always did).
- JS retains exactly two geometry roles: the **inline/portal host style is
  fill-parent** (the portal host spans the provider, laid out by Fabric in the
  same window — exact by construction), and `useWindowDimensions` provides the
  **overlay host's pre-state first-frame estimate** only.
- `react-native-safe-area-context` dropped as a peer dependency; behavior change
  documented: a non-modal inline sheet nested in a shorter container now uses
  the container as its canvas (detents clamp to it) instead of an invisible
  screen-height canvas.

---

## 5. State-channel pitfalls — why every push must be a complete snapshot

These cost the most debugging time across both platforms, and all three are
general RN facts.

1. **The EventQueue coalesces consecutive state updates for the same family by
   dropping the older one wholesale — on both platforms.**
   `EventQueue::enqueueStateUpdate` (RN 0.85.3,
   `ReactCommon/react/renderer/core/EventQueue.cpp`):

   ```cpp
   if (!stateUpdateQueue_.empty()) {
     const auto position = stateUpdateQueue_.back();
     if (stateUpdate.family == position.family) {
       stateUpdateQueue_.pop_back();   // older update discarded entirely
     }
   }
   stateUpdateQueue_.push_back(std::move(stateUpdate));
   ```

   The update callbacks do **not** compose — a discarded update's callback never
   runs. So two partial pushes racing within one event-beat window (a geometry
   push from `didLayout` followed microseconds later by a per-frame
   `contentOffsetY` push from the position emitter) means the geometry update is
   _deterministically_ dropped, forever, on every layout pass. Observed on iOS
   as `adopt()` logging `inset=0 frame=0×0` at 60 Hz while `pushNativeGeometry`
   kept pushing `inset=62`: the geometry lambda simply never executed. An
   earlier version of these notes claimed iOS was immune because its C++
   callback overload receives the committed data at apply time — a true merge
   _when applied_. That's correct but irrelevant: coalescing drops updates
   **before** application. Merge-on-apply cannot save an update that is never
   applied.

2. **Android's `updateState(folly::dynamic)` is additionally wholesale
   replacement from a call-time base.**
   `ConcreteState::updateState(folly::dynamic&&)` does
   `updateState(Data(getData(), std::move(data)))` — `getData()` is read from
   the _state instance the Java wrapper holds_ at call time, and the constructed
   Data _replaces_ the committed data later. Two partial payloads racing within
   a commit window both build on the same stale base; the last writer wipes the
   other's keys even without queue coalescing. Observed directly on the Pixel
   10: `pushGeometry w=1080 h=2424 inset=173` followed by
   `adopt inset=0 frame=0×0 offY=857`.
3. **A component whose state only the platform side writes can never bootstrap
   its own channel** (Android, RN 0.85). The `StateWrapper` is delivered to
   `ViewManager.updateState` either at create time or via an UPDATE*STATE mount
   instruction — and that instruction only fires when the C++ state \_changes*.
   A preallocated view whose state never changes C++-side gets neither:
   `createViewInstance` was observed firing with `updateState` never following,
   so Java-side pushes were silently dropped forever (chicken-and-egg). This
   killed the intermediate `BottomSheetContentWrapperView` design outright.
   Don't build on a channel whose bootstrap you haven't demonstrated; route
   everything through one channel that provably bootstraps.

**Consequence — the convention this library now follows on both platforms:**
every native state push carries the **complete snapshot** (`pushStateSnapshot`
on Android, `pushStateSnapshot` → `updateBottomSheetState` on iOS; the C++
helper sets every field and deliberately ignores `oldState`). This is also what
RNS's `FabricEnabledViewGroup` and Modal's `DialogRootViewGroup` do — all keys,
every time. The C++ merge constructor
(`data.count(key) ? data[key] : previousState.key`) is kept as defense but
cannot fix either race by itself.

---

## 6. Platform asymmetry: who places the sheet's children

- **Android**: `needsCustomLayoutForChildren() = true` → the host natively
  places children inside the (already offset, cap-sized, translated)
  `sheetContainer`; Yoga _origins_ of the direct children are ignored, only
  measured _sizes_ matter.
- **iOS**: there is no such opt-out — Fabric applies Yoga frames (origin
  included) directly to the child component views, which physically live inside
  the offset `sheetContainer`.
- Consequence: any Yoga-side origin shift on the sheet's children lands **once**
  on Android (native placement wins) but **twice** on iOS (container offset +
  Yoga origin). This is why the content-region inset must be **bottom** padding,
  not top: same region height, zero origin shift. Top padding double-offset iOS
  content — short modal sheets rendered their content below the visible surface.
  With bottom padding, `contentOffsetY` carries the container's top offset on
  both platforms (`containerTop + translationY`).
- The bottom-padding scheme has an iOS-only side effect of its own — see §7.

---

## 7. iOS: `RCTViewComponentView` sizes its `contentView` to the padding-inset content frame

`RCTViewComponentView.updateLayoutMetrics` does
`_contentView.frame = layoutMetrics.getContentFrame()` — the node frame **inset
by Yoga padding**. Any component that puts padding on its own node (for its Yoga
children) while hosting a full-bleed native content view gets that view silently
shrunk by the padding.

For the sheet this created a feedback loop through the native measurement path:
the pushed 62 pt bottom padding shrank the native host 874 → 812, the shrunken
host re-derived the cap from its own height (812 − 62 = 750), and the geometry
converged one inset short. Deceptively, the converged state _looked_ correct
(the container's offset happened to place the sheet top right), but the detent
cap was 62 pt short, the surface stopped 62 pt above the screen bottom, and the
bottom strip of the sheet was outside the hit-test region.

**Fix:** override `updateLayoutMetrics`, call super, then re-assert
`_sheetView.frame = self.bounds` (guarded to inline parenting — in overlay mode
the sheet view lives in the overlay container and isn't the contentView).
General rule: node padding is for Yoga children only; a native host view that
must fill the node has to undo the base class's content-frame sizing.

---

## 8. iOS: Fabric transiently detaches views from the window mid-commit

A JS commit that changes the sheet `index` also flips the wrapper's
`pointerEvents` (`none` ↔ `auto` with collapsed state), which changes **ancestor
view flattening** — so Fabric reparents the component view within a single
commit's mount phase: **remove → updateProps (while `window == nil`) → insert**.
The window is nil for a handful of milliseconds, exactly while the props that
trigger the open/close arrive. Three separate bugs grew from this one behavior:

1. **Phantom cap change.** While detached, the hosting view's cap computation
   can't see the window and fell back to full bounds height; the "cap change"
   triggered the re-anchor path. **Fix:** geometry refreshes
   (`refreshDetentsFromLayout`, `layoutSubviews`) go inert while
   `hasLaidOut && window == nil`; `didMoveToWindow` re-runs them on reattach.
2. **Pending-snap clobber.** `snapToIndex` while detached queues a
   `pendingSnapRequest` (correct — don't start render-server animations
   off-window). But the slot is single-entry: the phantom re-anchor's
   _maintenance_ snap (to the current `targetIndex`) overwrote the _user's_
   queued snap from `setDetentIndex`, so programmatic opens were silently
   swallowed — the sheet "opened" to its old index. Fixing 1 removes the
   maintenance snap; the surviving queue entry is always the user's intent,
   flushed by `didMoveToWindow`.
3. **Overlay teardown thrash.** The component view's `didMoveToWindow(nil)`
   synchronously restored inline presentation — tearing down the window-level
   overlay on _every_ open/close. The per-frame position log showed each
   animation bouncing through the inline slot: detents re-resolved against the
   slot's cap (392 pt instead of 812 pt), the spring started in that canvas
   (`snapToIndex … targetTy=391.67 maxHeight=391.67`), then the sheet re-hoisted
   into the overlay and re-anchored mid-flight — visibly broken open/close
   animations. **Fix:** defer overlay teardown by one runloop turn
   (`dispatch_async` to main) and skip it if the window is back (the mid-commit
   case). A genuine departure from the window still tears down, one
   imperceptible tick later.

After the fixes, the position stream shows each open/close as a single
`snapToIndex` against the true cap followed by a monotonic 60 Hz spring to
settle — no re-resolution, no mid-flight re-anchor, no spring cancellation.

**The general lesson:** on iOS, `window == nil` in `didMoveToWindow` /
mid-commit code paths does _not_ mean "the view left the screen" — it may mean
"Fabric is reparenting me right now." Any side effect keyed on window loss
(geometry recomputation, presentation teardown, animation cancellation) must
either go inert or defer one runloop turn and re-check. This is the iOS sibling
of §1's `requestLayout()` trap: both are cases where the RN integration
invalidates a UIKit/Android lifecycle assumption.

---

## 9. Cap changes must re-anchor translations (the 54 pt bug)

- Detent _specs_ store heights; `translationY(index) = cap − height` derives
  from the **cap**. `refreshDetentsFromLayout` early-returned when resolved
  specs were unchanged — correct for content changes, wrong for cap changes.
- The failure sequence (observed on device): Fabric can lay the host out
  **before attach** (mount-order), so the first `onLayout` runs with the cap
  unknowable (`rootWindowInsets` needs attach) → initial snap targets
  `ty = H − detent`; insets land moments later → cap = `H − 173px`; specs
  unchanged → early return → container re-laid against the new cap but the
  translation kept — visible height `120 − 66 = 54pt` until the first drag
  re-derived everything.
- Fix on both platforms: track `lastAppliedMaxDetentHeight` (the cap the
  container geometry and translations are currently anchored to, stamped where
  the container is laid out), fall through the early return when the resolved
  cap differs, and run the existing resize/re-anchor path **relating the current
  translation to the previous cap, not the freshly resolved one** (the
  pre-existing code read `resolvedMaxDetentHeight()` for `previousMaxHeight`
  after the cap had already changed — a latent bug that never mattered while the
  cap came from a prop set before layout).

---

## 10. Content-height measurement (the marker) — and why it stays

- `'content'` detents need the children's flow height — information that flows
  **shadow tree → native**, the opposite direction of the state channel. The
  sensor is a trailing zero-size marker `<View>` appended after the children:
  its flow position _is_ the content height, and Fabric's `layout()` call on it
  is the native signal (Android: `OnLayoutChangeListener`; iOS: KVO on the
  marker layer's `position`/`bounds`).
- This is structurally the same mechanism RNS ships: their
  `ScreenContentWrapper`'s own Fabric `layout()` call is the signal, because
  their fit-to-contents wrapper is content-sized. This library's wrapper is
  deliberately **cap-sized** (that's what gives `flex: 1` children and
  scrollables a fixed region to resolve against), so the sensor sits one level
  deeper as a sibling-of-content sentinel. Folding it natively into the wrapper
  would trade one hidden `View` + one listener for per-child listeners/KVO with
  mount churn on both platforms — not worth it.

---

## 11. Assorted facts worth keeping

- **Codegen header shadowing**: the library's custom `ShadowNodes.h` /
  `ComponentDescriptors.h` / state headers work because the CMake include path
  puts `common/cpp` _before_ the generated codegen dir, so generated
  registration code (`registry->add(concreteComponentDescriptorProvider<...>)`)
  compiles against the custom classes. Component-name constants still come from
  generated `ShadowNodes.cpp`. Same trick on iOS via the podspec header search
  path. Adding/removing a component = TS spec +
  `codegenConfig.ios.componentProvider` + custom headers + both native
  managers/views.
- The generated spec builds as its own
  `libreact_codegen_ReactNativeBottomSheetSpec.so` — when verifying symbols,
  inspect that, not `libappmodules.so`.
- `RectangleEdges<Float>` initializer order is `{left, top, right, bottom}`;
  `YogaLayoutableShadowNode::setPadding` takes points and dirties the node.
- In Yoga (as in CSS), **absolutely positioned children ignore the parent's
  padding** (they position against the padding box whose top edge is the border
  edge); in-flow children lay out within it. This is what lets one `setPadding`
  cap the content region without touching the surface. (But on iOS, mind §7: the
  component view's own `contentView` is _not_ exempt.)
- `adopt()` runs on every create _and_ clone — anything it sets from state is
  re-stamped per commit; the base `ConcreteComponentDescriptor::adopt` does
  nothing. During an animation that pushes per-frame state, that means `adopt()`
  runs at frame rate — keep it cheap and idempotent.
- Android state maps: `Arguments.createMap()` → `StateWrapperImpl.updateState` →
  folly::dynamic → the `(previousState, data)` constructor. `getMapBuffer()`
  returning `MapBufferBuilder::EMPTY()` is fine — that's the C++→Java direction,
  unused here.
- `Dimensions`/`useWindowDimensions` reliability varies by device/OS under
  edge-to-edge; the emulator ≠ physical device for anything inset-related.
  **Nothing in the Android bug class reproduces on emulators** — the repro demo
  ("Native overlay full-height list", the #48 shape) exists precisely for
  physical-device validation.
- Native-side logging that reaches the platform log stream: Android —
  `android.util.Log` in Kotlin, `__android_log_print` in C++ (e.g. inside
  `adopt()`; link `log` in the JNI CMake), read with `adb logcat -s TAG`;
  `uiautomator dump` reports real native view bounds. iOS — `NSLog` in Swift,
  `os_log` in C++ (works from `common/cpp` too), read with
  `xcrun simctl spawn booted log show --last 60s --predicate 'eventMessage CONTAINS "TAG"'`.
- Incremental `xcodebuild` gotcha: after editing pod sources, the app's
  `.debug.dylib` often fails to relink even though the build "succeeds" (the
  build database doesn't re-stat deleted outputs, and Swift content-hashing
  defeats plain `touch`). Verify with `strings` against
  **`ReactNativeBottomSheet.debug.dylib`** (the real code in the debug-dylib app
  layout — the `ReactNativeBottomSheet` stub is just a launcher), and force the
  relink by deleting both binaries _and_ making a content change in an
  app-target source, building until the dylib timestamp moves.

---

## 12. Resolution — the three iOS bugs that remained after the Android fixes

With the Android fixes in place, iOS portal modals still didn't open. Three
independent defects, all diagnosed by per-frame on-simulator logging and fixed
on `main`:

1. **State never landed** — EventQueue coalescing discarded every geometry push
   (§5.1). Fixed by the complete-snapshot convention (`aa99aed`).
2. **Padding shrank the host** — `RCTViewComponentView` contentView sizing fed
   the inset back into the measured cap (§7). Fixed by re-asserting the
   contentView frame (`aa99aed`).
3. **Mid-commit window detachment** — phantom cap changes swallowed programmatic
   opens, and the overlay teardown thrash broke open/close animations (§8).
   Fixed by detached-inertness (`aa99aed`) and deferred overlay teardown
   (`287ac5c`).

Final verification matrix: iOS (iPhone 17 simulator) — modal open at content
height with scrim and scrim-tap dismiss; full-height FlatList reaching the cap
with the last item + bottom inset visible; all native-overlay demos including
nested-mount `measureInWindow` and bottom-edge taps; inline detents; both
under-status-bar variants; per-frame-verified smooth overlay open/close
animations. Android (physical Pixel 10) — nested-mount `measureInWindow` y=451;
full-height overlay list scrolled to the end with the last item visible and
tappable; programmatic-detent demo at position 120 pt with a clean 720 pt round
trip.
