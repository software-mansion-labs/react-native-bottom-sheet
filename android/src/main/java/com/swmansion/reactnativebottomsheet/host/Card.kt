package com.swmansion.reactnativebottomsheet.host

import android.view.View
import com.swmansion.reactnativebottomsheet.BottomSheetCardClipLayout
import com.swmansion.reactnativebottomsheet.BottomSheetCardShadowView
import com.swmansion.reactnativebottomsheet.CardShadow
import kotlin.math.round

private val NO_INSETS = DetentInsets()

/** The card's clip, shadows, and content transform, which follow the sheet's position. */
internal class Card(
  private val state: SheetState,
  private val clipContainer: BottomSheetCardClipLayout,
  private val scrim: Scrim,
) {

  private val clearCardTouchSkip = Runnable { state.sheetContainer.skipsTouchTargeting = false }
  // The card's shadow, stitched from two copies drawn at a fixed height: the top copy shows the
  // card's top half and the bottom copy, translated to the card's bottom edge, its bottom half.
  private val shadowTop = BottomSheetCardShadowView(state.hostView.context)
  private val shadowBottom = BottomSheetCardShadowView(state.hostView.context)
  val shadowViews = listOf(shadowTop, shadowBottom)
  var shadowContentOffsetX = 0f
  var shadowContentScale = 1f

  /** Physical corner radii of the card in dp: top-left, top-right, bottom-right, bottom-left. */
  fun setSurfaceCornerRadii(values: List<Float>) {
    val radii = FloatArray(4) { (values.getOrNull(it) ?: 0f).coerceAtLeast(0f) * state.density }
    clipContainer.setCornerRadii(radii)
    for (shadowView in shadowViews) shadowView.setCornerRadii(radii)
    updateCardGeometry()
  }

  /** Outset card shadows, with lengths in dp. */
  fun setSurfaceShadows(values: List<CardShadow>) {
    val shadows = values.map {
      it.copy(
        offsetX = it.offsetX * state.density,
        offsetY = it.offsetY * state.density,
        blurRadius = it.blurRadius * state.density,
        spreadDistance = it.spreadDistance * state.density,
      )
    }
    for (shadowView in shadowViews) shadowView.setShadows(shadows)
    updateCardGeometry()
  }

  /** The card's elevation shadow, with its length in dp. */
  fun setSurfaceElevation(value: Float) {
    clipContainer.setCardElevation(value.coerceAtLeast(0f) * state.density)
  }

  fun setSurfaceShadowColor(color: Int) {
    clipContainer.setCardShadowColor(color)
  }

  // The card's gaps at a sheet position, blended between the detents around it. Resolved detents
  // ascend by position.
  private fun cardInsetsAt(position: Float): DetentInsets {
    if (!state.hasCardGaps) return NO_INSETS
    val upperIndex = state.detentSpecs.indexOfFirst { position <= it.position }
    if (upperIndex < 0) return state.detentSpecs.lastOrNull()?.insets ?: NO_INSETS
    val upper = state.detentSpecs[upperIndex]
    if (upperIndex == 0) return upper.insets
    val lower = state.detentSpecs[upperIndex - 1]
    val span = upper.position - lower.position
    val t = if (span <= 0f) 1f else (position - lower.position) / span
    val blend = { from: Float, to: Float -> from + (to - from) * t }
    return DetentInsets(
      left = blend(lower.insets.left, upper.insets.left),
      right = blend(lower.insets.right, upper.insets.right),
      bottom = blend(lower.insets.bottom, upper.insets.bottom),
    )
  }

  /**
   * Keeps the card clip and shadows in step with the sheet, in sheet-container coordinates. For a
   * sheet position (the height of the sheet's top above the host's bottom) at or above the first
   * open detent, the card's bottom stays above the bottom gap and its side gaps blend between
   * detents; below it, it keeps that detent's shape and slides down rigidly, so a closing sheet
   * leaves through the gap with its shape intact.
   *
   * RenderNode work, never layout: the clip is an outline (or clip bounds), and the shadow halves
   * are moved and clipped. Only side gaps that change as the sheet moves redraw the shadows.
   */
  fun updateCardGeometry() {
    val position = currentPosition()
    val insets = cardInsetsAt(position)
    val span = state.placeCard(insets, state.sheetContainer.width.toFloat())
    val left = span.left
    val right = left + span.width
    val bottom = cardBottomAt(position, insets)
    clipContainer.setCard(left, 0f, right, bottom)
    updateContentTransform(left, right)
    updateCardShadows(left, right, bottom)
  }

  private fun currentPosition(): Float =
    maxOf(
      state.height.toFloat() - state.sheetContainer.translationY,
      state.firstOpenDetentPosition,
      0f,
    )

  private fun cardBottomAt(position: Float, insets: DetentInsets): Float =
    (position - insets.bottom).coerceIn(0f, state.sheetContainer.height.toFloat())

  /** The height the card has right now, in layout units. */
  fun currentLayoutHeightPx(): Float {
    val position = currentPosition()
    val insets = cardInsetsAt(position)
    val height = cardBottomAt(position, insets)
    val scale = state.contentScale(insets)
    return if (scale > 0f) height / scale else height
  }

  /** Scales the surface and content to the card's width and pins them to its top-left corner. */
  private fun updateContentTransform(cardLeft: Float, cardRight: Float) {
    val widest = state.placeCard(state.widestCardInsets, state.sheetContainer.width.toFloat()).width
    val scale = if (widest > 0f) (cardRight - cardLeft) / widest else 1f
    for (i in 0 until clipContainer.childCount) {
      val child = clipContainer.getChildAt(i)
      child.pivotX = 0f
      child.pivotY = 0f
      child.scaleX = scale
      child.scaleY = scale
      child.translationX = cardLeft - child.left
    }
    shadowContentScale = scale
    // The shadow tree scales and shifts the children from their Yoga position.
    shadowContentOffsetX = cardLeft - scale * state.contentLeft(widest)
  }

  // Fabric recycles the view, which must not keep the card's scale.
  fun resetContentTransform(view: View) {
    view.scaleX = 1f
    view.scaleY = 1f
    view.translationX = 0f
  }

  private fun updateCardShadows(cardLeft: Float, cardRight: Float, cardBottom: Float) {
    if (!shadowTop.hasShadows) {
      for (shadowView in shadowViews) shadowView.visibility = View.INVISIBLE
      return
    }
    for (shadowView in shadowViews) shadowView.visibility = View.VISIBLE
    val shapeHeight = state.sheetContainer.height.toFloat()
    // Whole pixels, so the halves' clips meet exactly and never paint the same row twice.
    val split = round(cardBottom / 2f)
    if (split >= shadowTop.edgeBand && shapeHeight >= cardBottom) {
      // Both copies are drawn at the container's height, which only changes with the layout.
      // The join is far enough from both edges that the copies match there.
      shadowTop.place(cardLeft, cardRight, shapeHeight, 0f, Float.NEGATIVE_INFINITY, split)
      shadowBottom.place(
        cardLeft,
        cardRight,
        shapeHeight,
        round(cardBottom - shapeHeight),
        split,
        Float.POSITIVE_INFINITY,
      )
    } else {
      // Too short to join: draw the card as it is, redrawing as it changes.
      shadowTop.place(
        cardLeft,
        cardRight,
        cardBottom,
        0f,
        Float.NEGATIVE_INFINITY,
        Float.POSITIVE_INFINITY,
      )
      shadowBottom.visibility = View.INVISIBLE
    }
  }

  /**
   * Steps a clipping sheet out of React Native's target walk for a touch off the card, in a gap or
   * a clipped corner, so the walk finds what shows through it. A sheet that doesn't clip stays in
   * the walk, so content it draws outside the card can be pressed. A visible scrim keeps the touch,
   * as it does anywhere else. The walk runs while the touch is dispatched, so the skip ends with
   * that dispatch and never outlives it into another view walk.
   */
  fun onTouchTargeting(x: Float, y: Float) {
    val skips = !scrim.isScrimVisible() && clipContainer.clipsContent && !cardContains(x, y)
    state.sheetContainer.skipsTouchTargeting = skips
    state.hostView.removeCallbacks(clearCardTouchSkip)
    if (skips) state.hostView.post(clearCardTouchSkip)
  }

  /** Whether `(x, y)`, in host coordinates, falls on the card as it is drawn. */
  fun cardContains(x: Float, y: Float): Boolean =
    clipContainer.cardContains(
      x - state.sheetContainer.left - state.sheetContainer.translationX,
      y - state.sheetContainer.top - state.sheetContainer.translationY,
    )

  fun layoutSheetChildren(containerWidth: Int, containerHeight: Int) {
    // The clip container keeps the sheet container's full size, so its
    // children keep sheet-container coordinates; only its clip follows the card.
    for (shadowView in shadowViews) shadowView.layout(0, 0, containerWidth, containerHeight)
    clipContainer.layout(0, 0, containerWidth, containerHeight)
    // Fabric only measures the children, so the host applies the content's Yoga position.
    for (i in 0 until clipContainer.childCount) {
      val child = clipContainer.getChildAt(i)
      val contentLeft = state.contentLeft(child.measuredWidth.toFloat()).toInt()
      child.layout(contentLeft, 0, contentLeft + child.measuredWidth, child.measuredHeight)
    }
  }

  fun clearShadowContentTransform() {
    shadowContentOffsetX = 0f
    shadowContentScale = 1f
  }
}
