package com.swmansion.reactnativebottomsheet.host

import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.uimanager.StateWrapper

/** Keeps Fabric's shadow tree in step with the natively measured geometry. */
internal class ContentRegion(
  private val state: SheetState,
  private val card: Card,
  private val host: SheetHost,
) {

  private val hostView: ViewGroup
    get() = state.hostView

  private val width: Int
    get() = hostView.width

  private val height: Int
    get() = hostView.height

  // The state wrapper can arrive after geometry has already been derived
  // (mount ordering is not guaranteed), so flush the current snapshot and
  // re-derive on assignment; pushes dedup, so this is cheap when nothing was
  // pending.
  var stateWrapper: StateWrapper? = null
    set(value) {
      field = value
      if (value == null) {
        lastSentStateSnapshot = null
      } else {
        // The wrapper carries the state Fabric just committed: the frame size
        // and content-region inset the content is now laid out against.
        value.stateData
          ?.takeIf { it.hasKey("contentRegionInset") }
          ?.let {
            onStateCommitted(
              frameWidthDp = it.getDouble("frameWidth"),
              frameHeightDp = it.getDouble("frameHeight"),
              contentRegionInsetDp = it.getDouble("contentRegionInset"),
            )
          }
        pushStateSnapshot()
        recomputeNativeGeometry()
      }
    }

  private var lastShadowOffsetY = Float.NaN
  private var lastGeometryStateWidth = 0
  private var lastGeometryStateHeight = 0
  private var lastGeometryStateInsetTop = Float.NaN
  private var lastSentStateSnapshot: StateSnapshot? = null

  // The content region height (px) a detents change moved the layout to, while
  // Fabric has yet to lay the content out against it. Resolving a `content`
  // detent from the stale measurement could trip the ascending-order check: a
  // filling scrollable measured in a 300dp region cannot sit above a 500dp
  // detent that the same update introduced. NaN when nothing is pending.
  var isContentRegionChangePending = false

  // The content-region inset (dp) of the state Fabric last committed, recorded
  // mid-batch. NaN before the first commit.
  private var committedContentRegionInsetDp = Float.NaN

  // The frame size (dp) of the state Fabric last committed, recorded mid-batch.
  // In native-overlay mode the sheet node is sized from this state, so a commit
  // that carries a new frame is one that re-measured the content against it.
  // NaN before the first commit.
  private var committedFrameWidthDp = Float.NaN
  private var committedFrameHeightDp = Float.NaN

  // The committed inset once its mount batch has completed, so the content —
  // and its marker — is laid out against it. Only this value vouches for a
  // content measurement: mid-batch, the children may not have been re-measured
  // yet. NaN before the first batch completes.
  var appliedContentRegionInsetDp = Float.NaN

  /**
   * The height of the content region: the Yoga box the sheet's in-flow children (the `flex: 1`
   * content wrapper) are laid out in. It is the tallest height the sheet can reach — the card's
   * height at the tallest detent, clamped to the space its insets leave — rather than the host's,
   * so a scrollable under a fixed or percentage tallest detent is laid out against the height it
   * can actually show instead of leaving its last rows below the card's edge. A trailing `content`
   * detent is bounded by that space alone: its resolved height is measured from this very region,
   * so bounding the region by it would be circular. In layout units, so divided by the scale at
   * that detent, and never taller than the sheet's node.
   */
  private fun contentRegionHeightPx(): Float {
    val tallest = state.rawDetentSpecs.lastOrNull() ?: return height.toFloat()
    val available = state.availableCardHeight(tallest)
    val bound =
      when (tallest.kind) {
        DetentKind.POINTS -> tallest.value
        DetentKind.PERCENTAGE -> available * tallest.value
        DetentKind.CONTENT -> available
      }
    val cardHeight = bound.coerceIn(0f, available)
    val scale = state.contentScale(tallest.insets)
    if (scale <= 0f) return cardHeight
    return (cardHeight / scale).coerceAtMost(height.toFloat())
  }

  /**
   * Re-derives all natively measured geometry from this view's own size, window position, the
   * window's top inset, and the tallest detent, then pushes it into the shadow tree: the sheet
   * frame (consumed by the component descriptor only in overlay mode, exactly as <Modal> does) and
   * the content region's inset (applied as Yoga bottom padding in every mode, so in-flow content
   * lays out exactly within the region the sheet can show; see [contentRegionHeightPx]). Yoga thus
   * lays the sheet subtree out against real window geometry instead of JS-provided dimensions
   * (issue #48). In overlay mode this view fills the dialog, so its own metrics are the dialog's.
   */
  fun recomputeNativeGeometry() {
    if (width <= 0 || height <= 0 || !hostView.isAttachedToWindow) return

    val topInset =
      ViewCompat.getRootWindowInsets(hostView)
        ?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
        ?.top ?: 0
    val location = IntArray(2)
    hostView.getLocationInWindow(location)
    // Only the part of the inset this view actually extends under matters: a
    // sheet inside a container that starts below the status bar keeps its full
    // height.
    val topOverlap = (topInset - location[1]).coerceIn(0, height).toFloat()

    val geometryChanged =
      height.toFloat() != state.lastAppliedMaxDetentHeight || topOverlap != state.statusBarOverlapPx
    state.statusBarOverlapPx = topOverlap
    pushGeometryState(width, height, height - contentRegionHeightPx())
    if (geometryChanged) {
      host.refreshDetentsFromLayout()
      hostView.requestLayout()
    }
  }

  // The content-region inset (dp) the current detents call for: what
  // recomputeNativeGeometry pushes, computable even while detached.
  fun expectedContentRegionInsetDp(): Float {
    if (height <= 0) return Float.NaN
    return (height - contentRegionHeightPx()) / state.density
  }

  private val contentRegionCommitPass = Runnable {
    appliedContentRegionInsetDp = committedContentRegionInsetDp
    // needsCustomLayoutForChildren: Fabric only re-measured the children, so
    // give the content wrapper its fresh measured bounds — Android hit-testing
    // is bounds-based, and a wrapper still sized to a shorter region would make
    // the content laid out below it untouchable. Then re-resolve any `content`
    // detent that was waiting for the fresh measurement.
    if (width > 0 && height > 0) {
      host.layoutSheetContainer(width, height)
    }
    host.refreshDetentsFromLayout()
  }

  /**
   * Records the state Fabric committed: the sheet's frame size and the content-region inset. The
   * content wrapper is re-measured in the same mount batch whenever either changed (the inset is
   * its Yoga padding; in native-overlay mode the sheet node is sized from the frame), so once the
   * batch completes its measured size and the content marker reflect the new state: the posted pass
   * then marks the inset applied, places the children at their new sizes, and re-resolves the
   * detents.
   *
   * The frame counts on its own because a rotation can leave the inset unchanged: the overlay
   * window resizes first, the host's own layout places the content against its old measure, and
   * only this commit's batch re-measures it. Without a pass after that batch the content keeps its
   * pre-rotation frame, and Android hit-testing ignores everything laid out past it.
   */
  fun onStateCommitted(frameWidthDp: Double, frameHeightDp: Double, contentRegionInsetDp: Double) {
    val inset = contentRegionInsetDp.toFloat()
    val frameWidth = frameWidthDp.toFloat()
    val frameHeight = frameHeightDp.toFloat()
    if (
      inset == committedContentRegionInsetDp &&
        frameWidth == committedFrameWidthDp &&
        frameHeight == committedFrameHeightDp
    ) {
      return
    }
    committedContentRegionInsetDp = inset
    committedFrameWidthDp = frameWidth
    committedFrameHeightDp = frameHeight
    hostView.removeCallbacks(contentRegionCommitPass)
    hostView.post(contentRegionCommitPass)
  }

  private fun pushGeometryState(widthPx: Int, heightPx: Int, insetTopPx: Float) {
    if (
      widthPx == lastGeometryStateWidth &&
        heightPx == lastGeometryStateHeight &&
        insetTopPx == lastGeometryStateInsetTop
    ) {
      return
    }
    lastGeometryStateWidth = widthPx
    lastGeometryStateHeight = heightPx
    lastGeometryStateInsetTop = insetTopPx
    pushStateSnapshot()
  }

  /**
   * Pushes the complete state — geometry and content offset together — in every update. Android's
   * state-update path builds the new C++ state data from a base captured at call time and replaces
   * it wholesale at commit time, so partial payloads from independent sources can wipe each other's
   * keys when updates race within a commit window. Complete snapshots make the replacement harmless
   * regardless of ordering.
   */
  fun pushStateSnapshot() {
    val sw = stateWrapper ?: return
    val snapshot =
      StateSnapshot(
        contentOffsetX = (card.shadowContentOffsetX / state.density).toDouble(),
        contentOffsetY = if (lastShadowOffsetY.isNaN()) 0.0 else lastShadowOffsetY.toDouble(),
        contentScale = card.shadowContentScale.toDouble(),
        frameWidth = (lastGeometryStateWidth / state.density).toDouble(),
        frameHeight = (lastGeometryStateHeight / state.density).toDouble(),
        contentRegionInset =
          ((if (lastGeometryStateInsetTop.isNaN()) 0f else lastGeometryStateInsetTop) /
              state.density)
            .toDouble(),
      )
    // Fabric replaces the wrapper after committing our state. Republishing the same
    // snapshot from its setter would start another commit, even while the sheet is idle.
    // Keep this cache across wrapper replacements, but never mark an unsent snapshot.
    if (snapshot == lastSentStateSnapshot) return
    val map = Arguments.createMap()
    map.putDouble("contentOffsetX", snapshot.contentOffsetX)
    map.putDouble("contentOffsetY", snapshot.contentOffsetY)
    map.putDouble("contentScale", snapshot.contentScale)
    map.putDouble("frameWidth", snapshot.frameWidth)
    map.putDouble("frameHeight", snapshot.frameHeight)
    map.putDouble("contentRegionInset", snapshot.contentRegionInset)
    lastSentStateSnapshot = snapshot
    sw.updateState(map)
  }

  fun updateShadowState(translationY: Float) {
    // The content's in-host displacement from its Yoga position: the sheet's
    // translation. The content-region inset shrinks
    // the content via Yoga BOTTOM padding, keeping the Yoga origin at zero, so
    // the full displacement is carried here.
    val offsetY = (translationY / state.density).toDouble()
    lastShadowOffsetY = offsetY.toFloat()
    pushStateSnapshot()
  }

  fun clearGeometryState() {

    lastGeometryStateWidth = 0
    lastGeometryStateHeight = 0
    lastGeometryStateInsetTop = Float.NaN
    isContentRegionChangePending = false
    committedContentRegionInsetDp = Float.NaN
    committedFrameWidthDp = Float.NaN
    committedFrameHeightDp = Float.NaN
    appliedContentRegionInsetDp = Float.NaN
    hostView.removeCallbacks(contentRegionCommitPass)
  }

  fun clearShadowOffset() {

    lastShadowOffsetY = Float.NaN
  }
}
