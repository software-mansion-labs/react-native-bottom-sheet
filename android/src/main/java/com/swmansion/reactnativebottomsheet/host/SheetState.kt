package com.swmansion.reactnativebottomsheet.host

import android.view.ViewGroup
import androidx.dynamicanimation.animation.SpringAnimation
import com.facebook.react.modules.i18nmanager.I18nUtil
import com.swmansion.reactnativebottomsheet.BottomSheetCardContainer
import com.swmansion.reactnativebottomsheet.BottomSheetViewListener

/** Calls the parts of the sheet make back into the host view. */
internal interface SheetHost {
  val listener: BottomSheetViewListener?

  fun notifyCloseRequestStateChanged()

  /** Performs a close request: reports it to `onCloseRequest`, or snaps the sheet closed. */
  fun performCloseRequest(): Boolean

  fun updateInteractionState()

  fun layoutSheetContainer(viewWidth: Int, viewHeight: Int)

  fun refreshDetentsFromLayout()
}

/** The state several parts of the sheet read, and the geometry derived directly from it. */
internal class SheetState(val hostView: ViewGroup, val sheetContainer: BottomSheetCardContainer) {
  val density = hostView.context.resources.displayMetrics.density
  val scrollIndicators = ScrollIndicators(this)
  val width: Int
    get() = hostView.width

  val height: Int
    get() = hostView.height

  var rawDetentSpecs: List<RawDetentSpec> = emptyList()
  // Whether any detent keeps a gap at the card's sides or bottom, so the card is narrower or
  // shorter than the surface and has to clip it. A top inset only lowers the detent's height.
  var hasCardGaps = false
  // The side insets of the widest open card, which the content is laid out against.
  var widestCardInsets = DetentInsets()
  // The widest the card gets, in px; 0 for no cap. Sheet-wide.
  var cardMaxWidth = 0f
  // The share of the free width left of the card: 0 left, 0.5 center, 1 right.
  var cardPlacementBias = 0.5f
  var detentSpecs: List<DetentSpec> = emptyList()
  var targetIndex: Int = 0
  var animateContentHeight: Boolean = true
  var modal: Boolean = false

  var scrollExpandCoordination: Int = ScrollCoordinationLevel.HANDOFF.value
  var scrollCollapseCoordination: Int = ScrollCoordinationLevel.INITIAL.value

  // Seconds the settle spring takes. 0 uses the default, the same on every platform.
  var settleDuration = 0f
  var pendingIndex: Int? = null
  var hasLaidOut = false
  var isPanning = false
  var panStartingIndex: Int? = null
  var activeDragRange: ClosedFloatingPointRange<Float>? = null
  var activeDragDetentSpecs: List<DetentSpec>? = null
  var activeAnimation: SpringAnimation? = null
  var activeAnimationEmitsSettle = false
  var initialTouchY = 0f
  var initialTouchX = 0f
  var suppressScrimForClosingTarget = false
  var scrimPinnedFull = false

  // The height the container geometry and translations are currently anchored
  // to. It can change after a layout (rotation), and translations computed
  // against the old value must then be re-anchored even when the resolved detent
  // specs are unchanged — the specs store positions, but translationY derives
  // from the height.
  var lastAppliedMaxDetentHeight = Float.NaN

  // The default top inset: the part of the window's top (status bar / display
  // cutout) inset that actually overlaps this view. Measured from real window
  // geometry in every mode — inline, portal, and overlay — so no JS-estimated
  // dimensions are involved.
  var statusBarOverlapPx = 0f

  val closedIndex: Int?
    get() = detentSpecs.indexOfFirst { it.position == 0f }.takeIf { it >= 0 }

  val scrimDismissIndex: Int?
    get() = closedIndex?.takeIf { !detentSpecs[it].programmatic }

  // Whether JS supplied `onCloseRequest`. A close request then goes to the handler instead of
  // snapping the sheet to its closed detent.
  var hasCloseRequestHandler = false

  val firstOpenDetentPosition: Float
    get() = detentSpecs.firstOrNull { it.position > 0f }?.position ?: 0f

  val isTargetingClosedDetent: Boolean
    get() = detentSpecs.getOrNull(targetIndex)?.position == 0f

  // How much the content is scaled at a detent: its card's width over the width of the widest card,
  // which the content is laid out against.
  fun contentScale(insets: DetentInsets): Float {
    val widest = placeCard(widestCardInsets, width.toFloat()).width
    if (widest <= 0f) return 1f
    return placeCard(insets, width.toFloat()).width / widest
  }

  // The card's horizontal span for its side insets: the space they leave, capped at the maximum
  // width, where the placement puts it in the whole host, moved only as far as needed to keep clear
  // of the insets. Mirrors `placeCard` in `cardPlacement.ts`.
  fun placeCard(insets: DetentInsets, hostWidth: Float): CardSpan {
    val available = (hostWidth - insets.left - insets.right).coerceAtLeast(0f)
    val cardWidth = if (cardMaxWidth > 0f) minOf(cardMaxWidth, available) else available
    val preferred = cardPlacementBias * (hostWidth - cardWidth)
    val rightmost = maxOf(insets.left, hostWidth - insets.right - cardWidth)
    return CardSpan(preferred.coerceAtLeast(insets.left).coerceAtMost(rightmost), cardWidth)
  }

  // Where Yoga lays out a child of the sheet that is `childWidth` wide: at the widest card's inset
  // on the start edge, which is the right one in a right-to-left layout. Narrower than the space
  // the insets leave only under a maximum width.
  fun contentLeft(childWidth: Float): Float =
    if (I18nUtil.instance.isRTL(hostView.context)) {
      sheetContainer.width - widestCardInsets.right - childWidth
    } else {
      widestCardInsets.left
    }

  // The tallest the card can be at a detent: the host's height, less the detent's top and bottom
  // insets. Percentages resolve against it, so `100%` fits.
  fun availableCardHeight(spec: RawDetentSpec): Float =
    (height - (spec.insets.top ?: statusBarOverlapPx) - spec.insets.bottom).coerceAtLeast(0f)

  // Interpolates a per-detent value (one per detent, by index) by the sheet
  // position, using each detent's resolved position as the breakpoint.
  fun interpolateAtPosition(position: Float, values: List<Float>): Float {
    if (detentSpecs.isEmpty()) return 0f
    val pairs =
      detentSpecs.indices.map { detentSpecs[it].position to values[it] }.sortedBy { it.first }

    val first = pairs.first()
    val last = pairs.last()
    if (position <= first.first) return first.second
    if (position >= last.first) return last.second

    for (i in 1 until pairs.size) {
      val upper = pairs[i]
      if (position <= upper.first) {
        val lower = pairs[i - 1]
        val span = upper.first - lower.first
        val t = if (span <= 0f) 1f else (position - lower.first) / span
        return lower.second + (upper.second - lower.second) * t
      }
    }
    return last.second
  }

  fun currentSheetHeight(): Float {
    val maxHeight = height.toFloat()
    return maxHeight - sheetContainer.translationY
  }
}
