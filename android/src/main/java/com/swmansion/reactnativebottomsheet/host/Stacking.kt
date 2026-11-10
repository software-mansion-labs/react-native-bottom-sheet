package com.swmansion.reactnativebottomsheet.host

import java.lang.ref.WeakReference

// How much narrower a fully pushed-back card is drawn, across both sides, in dp. Matches
// `stackWidthInset` on iOS and `STACK_WIDTH_INSET` on web.
private const val STACK_WIDTH_INSET = 32f
// How far a pushed-back card's top edge shows above the sheet covering it, in dp. Matches
// `stackPeek` on iOS and `STACK_PEEK` on web.
internal const val STACK_PEEK = 10f

// How far a sheet is pushed back for [progress], the covering sheet's linear progress towards its
// tallest detent: the curve measured on native iOS sheets, which push back quickly at first and
// ease into the end.
private fun easedStackProgress(progress: Float): Float =
  1.38f * progress - 0.38f * progress * progress

/** How far a sheet is pushed back: its scale, and how far its top edge moves up. */
internal data class StackPush(val scale: Float, val shift: Float)

/**
 * How far the sheet above pushes back a sheet whose card rests [coveredPosition] above the host's
 * bottom and is [coveredCardWidth] wide. Only its tallest detent can cover that card's top edge, as
 * only a native sheet's large detent pushes back the one beneath, and [interpolate] blends a
 * per-detent value at its current position, like a scrim opacity.
 */
internal fun stackPush(
  coveringDetentPositions: List<Float>,
  interpolate: (values: List<Float>) -> Float,
  coveredPosition: Float,
  coveredCardWidth: Float,
  peek: Float,
  widthInset: Float,
): StackPush {
  // How far each of the covering sheet's detents reaches into the strip the covered edge peeks out
  // of. Negative leaves the edge uncovered.
  val tallest = coveringDetentPositions.maxOrNull() ?: 0f
  val reaches = coveringDetentPositions.map {
    if (it > 0f && it == tallest) it - (coveredPosition - peek) else -1f
  }
  val progress = easedStackProgress(interpolate(reaches.map { if (it >= 0f) 1f else 0f }))
  val shift = interpolate(reaches.map { it.coerceIn(0f, peek) })
  val scale =
    if (coveredCardWidth > widthInset) 1f - progress * widthInset / coveredCardWidth else 1f
  return StackPush(scale, shift)
}

/**
 * How far the sheets in [sheets] push back the one at [rank]: as far as the stacked sheet above it
 * that covers it most, in scale and shift alike, whatever sits between them. A closed sheet's
 * [push] is none, and a closing one's fades out as it goes, so none needs passing over.
 */
internal fun <T> strongestStackPush(
  rank: Long,
  sheets: List<T>,
  rankOf: (T) -> Long,
  isStacked: (T) -> Boolean,
  push: (T) -> StackPush,
): StackPush {
  val pushes = sheets.filter { rankOf(it) > rank && isStacked(it) }.map(push)
  return StackPush(
    scale = pushes.minOfOrNull { it.scale } ?: 1f,
    shift = pushes.maxOfOrNull { it.shift } ?: 0f,
  )
}

/**
 * A sheet's part in the stack. A `stacked` modal sheet pushes back the open modal sheets beneath
 * it, as native iOS sheets do: each is scaled down about its top edge and shifted up, so the edge
 * peeks out above the sheet covering it.
 *
 * The sheet beneath never moves on its own clock: its transform is a function of the positions of
 * the sheets above, applied in the frame they move in. RenderNode properties only, so following
 * never lays out or redraws anything.
 */
internal class Stacking(private val state: SheetState, private val card: Card) {
  var stacked: Boolean
    get() = state.stacked
    set(value) {
      if (state.stacked == value) return
      state.stacked = value
      updateAll()
    }

  var openOrder = 0
    set(value) {
      if (field == value) return
      field = value
      updateAll()
    }

  var nativeOverlay = false
    set(value) {
      if (field == value) return
      field = value
      updateAll()
    }

  private val reference = WeakReference(this)
  private val location = IntArray(2)

  fun attach() {
    if (sheets.none { it.get() === this }) sheets.add(reference)
    updateAll()
  }

  fun detach() {
    sheets.remove(reference)
    reset()
    updateAll()
  }

  fun reset() {
    val container = state.sheetContainer
    container.scaleX = 1f
    container.scaleY = 1f
  }

  private val isParticipant: Boolean
    get() =
      state.modal &&
        state.hostView.isAttachedToWindow &&
        state.hasLaidOut &&
        state.detentSpecs.isNotEmpty()

  // Where the sheet sits on screen: overlays above portal sheets, then by when each last opened.
  private val rank: Long
    get() = (if (nativeOverlay) 1L shl 32 else 0L) + openOrder

  // Where the card's top rests once the sheet settles. The push reads this rather than the moving
  // position, so two sheets never chase each other.
  private val restingPosition: Float
    get() = state.detentSpecs.getOrNull(state.targetIndex)?.position ?: 0f

  // Only sheets that fill the same area stack; their positions then compare directly.
  private fun fillsSameAreaAs(other: Stacking): Boolean {
    state.hostView.getLocationOnScreen(location)
    other.state.hostView.getLocationOnScreen(other.location)
    return location[0] == other.location[0] &&
      location[1] == other.location[1] &&
      state.width == other.state.width &&
      state.height == other.state.height
  }

  private fun follow(participants: List<Stacking>) {
    val resting = restingPosition
    val container = state.sheetContainer
    if (!isParticipant || resting <= 0f) {
      if (container.scaleX != 1f) reset()
      return
    }

    val (left, right) = card.cardSidesAt(resting)
    val push =
      strongestStackPush(
        rank,
        participants.filter { it !== this && fillsSameAreaAs(it) },
        rankOf = { it.rank },
        isStacked = { it.stacked },
      ) { candidate ->
        val position = candidate.state.currentSheetHeight()
        stackPush(
          coveringDetentPositions = candidate.state.detentSpecs.map { it.position },
          interpolate = { candidate.state.interpolateAtPosition(position, it) },
          coveredPosition = resting,
          coveredCardWidth = right - left,
          peek = STACK_PEEK * state.density,
          widthInset = STACK_WIDTH_INSET * state.density,
        )
      }
    if (push.scale >= 1f) {
      if (container.scaleX != 1f) reset()
      return
    }
    // The container's top is the card's. Scaling about a point above it also moves the top up, by
    // the pivot's distance times how much the scale takes off, so one transform carries both and
    // the translation stays the sheet's position alone.
    container.pivotX = (left + right) / 2f
    container.pivotY = -push.shift / (1f - push.scale)
    container.scaleX = push.scale
    container.scaleY = push.scale
  }

  companion object {
    // Every attached sheet. Few enough that each update walks them all.
    private val sheets = mutableListOf<WeakReference<Stacking>>()

    /**
     * Finds how far every sheet is pushed back again and brings its transform up to date. Cheap
     * enough to run on every frame a sheet moves in: without a stacked sheet it only finds each
     * sheet unscaled, as it already is.
     */
    fun updateAll() {
      sheets.removeAll { it.get() == null }
      val all = sheets.mapNotNull { it.get() }
      val participants = all.filter { it.isParticipant }
      for (sheet in all) sheet.follow(participants)
    }
  }
}
