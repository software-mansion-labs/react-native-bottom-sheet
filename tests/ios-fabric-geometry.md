# iOS Fabric geometry regression

Run manually in a Fabric-enabled iOS example build. These checks require native
mounting transactions and animation callbacks.

1. Record repeated snaps between short and tall detents in Time Profiler.
   Include cards with different insets at each detent. During a spring,
   `mountingTransactionDidMount` must not publish content geometry through
   `pushContentGeometry` / `pushStateSnapshot`. Position callbacks should
   continue publishing movement; compare commit activity and visible stutter
   with the same scenario before the fix.
2. While settled, change detent insets and rotate the device. Check descendant
   measurements and hit targets against the visible content: layout-only changes
   must still publish fresh geometry.
3. Resize content at an active `'content'` detent, both while settled and during
   a spring, with `animateContentHeight` enabled and disabled. The detent must
   resolve against the applied layout and finish at the new height. Compare
   callback delivery with the baseline to check that this fix preserves it.
4. Interrupt a spring by dragging, then release. Check that content follows the
   sheet, hit targets remain aligned, and the final settled geometry is correct.
5. Repeat opening, closing, and rotation in inline, portal, and window-overlay
   hosts. Include content arriving after initial presentation. Check content
   measurements and scrolling to the last row after settling.

The fix removes the mount-to-position feedback path; these checks are still
needed to establish its effect on device performance and native layout.
