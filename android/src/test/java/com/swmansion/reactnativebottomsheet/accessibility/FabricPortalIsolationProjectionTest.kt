package com.swmansion.reactnativebottomsheet.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class FabricPortalIsolationProjectionTest {
  @Test
  fun `JNI output preserves every retained child of each logical parent`() {
    val projection =
      FabricPortalIsolationProjection(
        intArrayOf(12, 22),
        intArrayOf(11, 13, 13, 14, 21, 23, 13, 15),
      )
    assertEquals(setOf(12, 22), projection.hiddenBranches)
    assertEquals(
      mapOf(11 to setOf(13), 13 to setOf(14, 15), 21 to setOf(23)),
      projection.retainedChildren,
    )
  }

  @Test
  fun `empty projection is a release value and decoded values do not retain input arrays`() {
    val empty = FabricPortalIsolationProjection(intArrayOf(), intArrayOf())
    assertEquals(emptySet<Int>(), empty.hiddenBranches)
    assertEquals(emptyMap<Int, Set<Int>>(), empty.retainedChildren)

    val hidden = intArrayOf(12)
    val retained = intArrayOf(13, 14)
    val projection = FabricPortalIsolationProjection(hidden, retained)
    hidden[0] = 99
    retained[1] = 99
    assertEquals(setOf(12), projection.hiddenBranches)
    assertEquals(mapOf(13 to setOf(14)), projection.retainedChildren)
  }
}
