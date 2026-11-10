package com.swmansion.reactnativebottomsheet.host

import android.content.res.Configuration
import java.lang.ref.WeakReference

// How much narrower a fully pushed-back card is drawn, across both sides, in dp. Matches
// `stackWidthInset` on iOS and `STACK_WIDTH_INSET` on web.
private const val STACK_WIDTH_INSET = 32f
// How far a pushed-back card's top edge shows above the sheet covering it, in dp. Matches
// `stackPeek` on iOS and `STACK_PEEK` on web.
internal const val STACK_PEEK = 10f

// How dark a covered card is dimmed, in black, under one stacked sheet and under two or more, in
// light and dark appearance: what native iOS sheets draw over a covered sheet. Matches
// `stackDimming` on iOS and web.
private const val STACK_DIMMING = 0.12f
private const val DEEP_STACK_DIMMING = 0.16f
private const val DARK_STACK_DIMMING = 0.29f
private const val DARK_DEEP_STACK_DIMMING = 0.38f

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
 * How dark a card is dimmed under sheets that are [openProgresses] open: as native sheets dim it
 * under one sheet, and a little more under two or more, and more in [dark] appearance. Each fades
 * in and out with its sheet.
 */
internal fun stackDimming(openProgresses: List<Float>, dark: Boolean): Float {
  val single = if (dark) DARK_STACK_DIMMING else STACK_DIMMING
  val deep = if (dark) DARK_DEEP_STACK_DIMMING else DEEP_STACK_DIMMING
  val covering = openProgresses.sortedDescending()
  return single * (covering.getOrNull(0) ?: 0f) + (deep - single) * (covering.getOrNull(1) ?: 0f)
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
 * never lays out anything.
 *
 * Dimming follows native sheets too: a covered card is dimmed on its own, and a stacked sheet
 * leaves the background to the scrim of the sheet beneath it, so the background is dimmed once
 * however many sheets are open.
 */
internal class Stacking(
  private val state: SheetState,
  private val card: Card,
  private val scrim: Scrim,
) {
  var stacked: Boolean
    get() = state.stacked
    set(value) {
      if (state.stacked == value) return
      state.stacked = value
      applyAll()
    }

  var openOrder = 0
    set(value) {
      if (field == value) return
      field = value
      applyAll()
    }

  var nativeOverlay = false
    set(value) {
      if (field == value) return
      field = value
      applyAll()
    }

  private val reference = WeakReference(this)
  private val location = IntArray(2)

  fun attach() {
    if (sheets.none { it.get() === this }) sheets.add(reference)
    applyAll()
  }

  fun detach() {
    sheets.remove(reference)
    reset()
    applyAll()
  }

  fun reset() {
    resetPush()
    card.setStackDim(0f)
    scrim.paintOpacity = 1f
  }

  private fun resetPush() {
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

  // How open the sheet is, from closed (0) to any open detent (1), and in between as it moves: how
  // far it dims what it covers.
  private val openProgress: Float
    get() =
      state.interpolateAtPosition(
        state.currentSheetHeight(),
        state.detentSpecs.map { if (it.position > 0f) 1f else 0f },
      )

  // Only sheets that fill the same area stack; their positions then compare directly.
  private fun fillsSameAreaAs(other: Stacking): Boolean {
    state.hostView.getLocationOnScreen(location)
    other.state.hostView.getLocationOnScreen(other.location)
    return location[0] == other.location[0] &&
      location[1] == other.location[1] &&
      state.width == other.state.width &&
      state.height == other.state.height
  }

  /** Pushes the sheet back and dims it, and returns the stacked sheets above it that do. */
  private fun follow(participants: List<Stacking>): List<Stacking> {
    val above =
      if (isParticipant) {
        participants.filter { it !== this && it.stacked && it.rank > rank && fillsSameAreaAs(it) }
      } else {
        emptyList()
      }
    val dark =
      (state.hostView.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    card.setStackDim(stackDimming(above.map { it.openProgress }, dark))

    // A sheet that is closing is not pushed: the push reads where it rests.
    val resting = restingPosition
    val container = state.sheetContainer
    if (above.isEmpty() || resting <= 0f) {
      if (container.scaleX != 1f) resetPush()
      return above
    }
    val (left, right) = card.cardSidesAt(resting)
    val push =
      strongestStackPush(rank, above, rankOf = { it.rank }, isStacked = { it.stacked }) { candidate
        ->
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
      if (container.scaleX != 1f) resetPush()
      return above
    }
    // The container's top is the card's. Scaling about a point above it also moves the top up, by
    // the pivot's distance times how much the scale takes off, so one transform carries both and
    // the translation stays the sheet's position alone.
    container.pivotX = (left + right) / 2f
    container.pivotY = -push.shift / (1f - push.scale)
    container.scaleX = push.scale
    container.scaleY = push.scale
    return above
  }

  companion object {
    // Every attached sheet. Few enough that each update walks them all.
    private val sheets = mutableListOf<WeakReference<Stacking>>()

    /** Brings the stack up to date after a sheet moved. Free while no sheet is stacked. */
    fun updateAll() {
      if (sheets.none { it.get()?.stacked == true }) return
      applyAll()
    }

    /**
     * Finds how far every sheet is pushed back and dimmed again and brings it up to date. Also run
     * when the last stacked sheet goes, to put the others back.
     */
    private fun applyAll() {
      sheets.removeAll { it.get() == null }
      val all = sheets.mapNotNull { it.get() }
      val participants = all.filter { it.isParticipant }
      // How open the sheets beneath each stacked sheet are, which dim the background already.
      val openBeneath = HashMap<Stacking, Float>()
      for (sheet in all) {
        val open = sheet.openProgress
        for (above in sheet.follow(participants)) {
          openBeneath[above] = maxOf(openBeneath[above] ?: 0f, open)
        }
      }
      for (sheet in all) sheet.scrim.paintOpacity = 1f - (openBeneath[sheet] ?: 0f)
    }
  }
}
