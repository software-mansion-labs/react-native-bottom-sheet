# iOS Fabric geometry regression

Run manually in a Fabric-enabled iOS example build. The JavaScript test suite
does not exercise native mounting transactions.

1. Record repeated snaps between preview and fullscreen detents in Time
   Profiler. Include inset cards, `scaleContent={false}`, and safe-area content
   protection. During a spring, `mountingTransactionDidMount` must not publish
   content geometry through `pushContentGeometry` / `pushStateSnapshot`.
   Position callbacks should continue publishing movement; compare commit
   activity and visible stutter with the same scenario before the fix.
2. While settled, change detent insets, toggle content scaling, and rotate the
   device. Check descendant measurements and hit targets against the visible
   content: layout-only changes must still publish fresh geometry.
3. Resize content at an active `'content'` detent, both while settled and during
   a spring, with `animateContentHeight` enabled and disabled. The detent must
   resolve against the applied layout, finish at the new height, and emit one
   `onSettle` for the completed snap without an index-change event.
4. Interrupt a spring by dragging, then release. Check that content follows the
   sheet, hit targets remain aligned, and the final settled geometry is correct.
5. Repeat opening, closing, and rotation in inline, portal, and window-overlay
   hosts. Include content arriving after initial presentation. Check safe-area
   offsets and scrolling to the last row after settling.

The fix removes the mount-to-position feedback path; these checks are still
needed to establish its effect on device performance and native layout.
