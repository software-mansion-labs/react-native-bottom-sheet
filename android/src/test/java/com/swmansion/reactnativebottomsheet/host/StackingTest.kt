package com.swmansion.reactnativebottomsheet.host

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PEEK = 10f
private const val WIDTH_INSET = 32f
private const val CARD_WIDTH = 400f

class StackingTest {
  // The covering sheet's detents: closed, medium and large.
  private val detents = listOf(0f, 400f, 800f)

  /** Linear interpolation of a per-detent value at [position], as the sheet state does it. */
  private fun interpolateAt(
    position: Float,
    detents: List<Float> = this.detents,
  ): (List<Float>) -> Float = { values ->
    val upper = detents.indexOfFirst { position <= it }
    when {
      upper < 0 -> values.last()
      upper == 0 -> values.first()
      else -> {
        val t = (position - detents[upper - 1]) / (detents[upper] - detents[upper - 1])
        values[upper - 1] + (values[upper] - values[upper - 1]) * t
      }
    }
  }

  private fun push(driverPosition: Float, coveredPosition: Float) =
    stackPush(
      detents,
      interpolateAt(driverPosition),
      coveredPosition,
      CARD_WIDTH,
      PEEK,
      WIDTH_INSET,
    )

  @Test
  fun `a sheet as tall as the one above is pushed back in full`() {
    val push = push(driverPosition = 800f, coveredPosition = 800f)
    assertEquals(1f - WIDTH_INSET / CARD_WIDTH, push.scale, 1e-4f)
    assertEquals(PEEK, push.shift, 1e-4f)
  }

  @Test
  fun `a detent that leaves the top edge uncovered leaves the sheet in place`() {
    assertEquals(
      StackPush(scale = 1f, shift = 0f),
      push(driverPosition = 400f, coveredPosition = 800f),
    )
  }

  @Test
  fun `only the tallest detent pushes, so a medium sheet leaves a medium one alone`() {
    assertEquals(
      StackPush(scale = 1f, shift = 0f),
      push(driverPosition = 400f, coveredPosition = 400f),
    )
  }

  @Test
  fun `the push follows the sheet above between its detents, eased like native sheets`() {
    // Halfway from medium to large, 1.38 * 0.5 - 0.38 * 0.25 of the push; the shift stays linear.
    val push = push(driverPosition = 600f, coveredPosition = 800f)
    assertEquals(1f - 0.595f * WIDTH_INSET / CARD_WIDTH, push.scale, 1e-4f)
    assertEquals(PEEK / 2f, push.shift, 1e-4f)
  }

  @Test
  fun `the eased push starts and ends where the linear one does`() {
    assertEquals(1f, push(driverPosition = 400f, coveredPosition = 800f).scale, 1e-4f)
    assertEquals(
      1f - WIDTH_INSET / CARD_WIDTH,
      push(driverPosition = 800f, coveredPosition = 800f).scale,
      1e-4f,
    )
  }

  @Test
  fun `a sheet with one open detent pushes from closed to open`() {
    val detents = listOf(0f, 800f)
    val push = stackPush(detents, interpolateAt(400f, detents), 800f, CARD_WIDTH, PEEK, WIDTH_INSET)
    assertEquals(1f - 0.595f * WIDTH_INSET / CARD_WIDTH, push.scale, 1e-4f)
    assertEquals(PEEK / 2f, push.shift, 1e-4f)
  }

  @Test
  fun `a closed sheet above pushes nothing`() {
    assertEquals(
      StackPush(scale = 1f, shift = 0f),
      push(driverPosition = 0f, coveredPosition = 800f),
    )
  }

  @Test
  fun `a sheet resting just below the edge shifts it only as far as the peek needs`() {
    // The driver's large detent rests 4 below the covered edge, so 6 more show 10.
    val push = push(driverPosition = 800f, coveredPosition = 804f)
    assertEquals(1f - WIDTH_INSET / CARD_WIDTH, push.scale, 1e-4f)
    assertEquals(6f, push.shift, 1e-4f)
  }

  @Test
  fun `a shorter sheet under a taller one never shifts more than the peek`() {
    assertEquals(PEEK, push(driverPosition = 800f, coveredPosition = 400f).shift, 1e-4f)
  }

  private val full = StackPush(scale = 1f - WIDTH_INSET / CARD_WIDTH, shift = PEEK)
  private val none = StackPush(scale = 1f, shift = 0f)

  private data class Sheet(
    val rank: Long,
    val position: Float,
    val stacked: Boolean = true,
    val detents: List<Float> = listOf(0f, 400f, 800f),
  )

  private fun push(rank: Long, coveredPosition: Float, vararg sheets: Sheet) =
    strongestStackPush(rank, sheets.toList(), Sheet::rank, Sheet::stacked) {
      stackPush(
        it.detents,
        interpolateAt(it.position, it.detents),
        coveredPosition,
        CARD_WIDTH,
        PEEK,
        WIDTH_INSET,
      )
    }

  private fun assertPush(expected: StackPush, actual: StackPush) {
    assertEquals(expected.scale, actual.scale, 1e-4f)
    assertEquals(expected.shift, actual.shift, 1e-4f)
  }

  @Test
  fun `a large sheet pushes back a large one beneath a medium one between them`() {
    assertPush(
      full,
      push(1, 800f, Sheet(rank = 2, position = 400f), Sheet(rank = 3, position = 800f)),
    )
  }

  @Test
  fun `a sheet between that is not stacked no longer blocks the push`() {
    assertPush(
      full,
      push(
        1,
        800f,
        Sheet(rank = 2, position = 800f, stacked = false),
        Sheet(rank = 3, position = 800f),
      ),
    )
  }

  @Test
  fun `a sheet that is not stacked pushes nothing`() {
    assertPush(none, push(1, 800f, Sheet(rank = 2, position = 800f, stacked = false)))
  }

  @Test
  fun `the sheet that covers most wins`() {
    val half = Sheet(rank = 2, position = 600f)
    val whole = Sheet(rank = 3, position = 800f)
    assertPush(full, push(1, 800f, half, whole))
    assertPush(full, push(1, 800f, whole.copy(rank = 2), half.copy(rank = 3)))
  }

  @Test
  fun `the shift is the most any sheet above gives`() {
    // Both cover the edge; one rests 4 below it and shifts 6, the other reaches above the peek.
    val low = Sheet(rank = 2, position = 800f)
    val high = Sheet(rank = 3, position = 820f, detents = listOf(0f, 820f))
    assertPush(full, push(1, 804f, low, high))
    assertEquals(6f, push(1, 804f, low).shift, 1e-4f)
  }

  @Test
  fun `a closing sheet fades its push out while another holds the sheet back`() {
    val medium = Sheet(rank = 2, position = 400f)
    assertPush(
      StackPush(scale = 1f - 0.595f * WIDTH_INSET / CARD_WIDTH, shift = PEEK / 2f),
      push(1, 800f, medium, Sheet(rank = 3, position = 600f)),
    )
    assertPush(none, push(1, 800f, medium, Sheet(rank = 3, position = 0f)))
    assertPush(
      full,
      push(1, 800f, Sheet(rank = 2, position = 800f), Sheet(rank = 3, position = 200f)),
    )
  }

  @Test
  fun `sheets beneath push nothing`() {
    assertPush(
      none,
      push(3, 800f, Sheet(rank = 1, position = 800f), Sheet(rank = 2, position = 800f)),
    )
  }

  @Test
  fun `a card under one open sheet is dimmed as native sheets dim it`() {
    assertEquals(0.12f, stackDimming(listOf(1f), dark = false), 1e-4f)
  }

  @Test
  fun `a card under two or more open sheets is dimmed a little more, and no further`() {
    assertEquals(0.16f, stackDimming(listOf(1f, 1f), dark = false), 1e-4f)
    assertEquals(0.16f, stackDimming(listOf(1f, 1f, 1f), dark = false), 1e-4f)
  }

  @Test
  fun `the dimming fades in and out with the sheets above`() {
    assertEquals(0f, stackDimming(emptyList(), dark = false), 1e-4f)
    assertEquals(0.06f, stackDimming(listOf(0.5f), dark = false), 1e-4f)
    // The more open sheet sets the first share, whatever the order.
    assertEquals(0.12f + 0.04f * 0.5f, stackDimming(listOf(0.5f, 1f), dark = false), 1e-4f)
  }

  @Test
  fun `cards are dimmed more in dark appearance, as native sheets dim them`() {
    assertEquals(0.29f, stackDimming(listOf(1f), dark = true), 1e-4f)
    assertEquals(0.38f, stackDimming(listOf(1f, 1f), dark = true), 1e-4f)
  }
}
