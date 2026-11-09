package com.swmansion.reactnativebottomsheet.host

import org.junit.Assert.assertEquals
import org.junit.Test

private const val FLICK_THRESHOLD = 600f

class ReleaseSnapIndexTest {
  // Detents at 300, 330 and 360; velocity is downward-positive, so an upward release is negative.
  private val positions = listOf(300f, 330f, 360f)
  private val candidates = positions.indices.toList()

  private fun resolve(currentHeight: Float, velocity: Float) =
    releaseSnapIndex(candidates, { positions[it] }, currentHeight, velocity, FLICK_THRESHOLD)

  @Test
  fun `a release just under the flick threshold stops where a flick just over it does`() {
    // 590 projects to 369 uncapped, which would snap to 360 and skip 330.
    assertEquals(1, resolve(currentHeight = 310f, velocity = -590f))
    assertEquals(1, resolve(currentHeight = 310f, velocity = -610f))
  }

  @Test
  fun `a gentle release still reaches the detent ahead before its midpoint`() {
    // 305 is short of the 315 midpoint; 100 per second carries it to 315 and past.
    assertEquals(1, resolve(currentHeight = 305f, velocity = -101f))
  }

  @Test
  fun `a drag that already crossed a detent can reach the next one`() {
    assertEquals(2, resolve(currentHeight = 340f, velocity = -590f))
  }

  @Test
  fun `a downward release is capped at the first detent below`() {
    assertEquals(1, resolve(currentHeight = 350f, velocity = 590f))
    assertEquals(1, resolve(currentHeight = 350f, velocity = 610f))
  }

  @Test
  fun `a release that stopped resolves on where it stopped`() {
    assertEquals(0, resolve(currentHeight = 312f, velocity = 0f))
  }
}
