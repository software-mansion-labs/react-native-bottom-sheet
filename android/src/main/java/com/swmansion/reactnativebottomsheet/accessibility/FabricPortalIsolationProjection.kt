package com.swmansion.reactnativebottomsheet.accessibility

import com.facebook.proguard.annotations.DoNotStrip

/** Source projection values; native attachment and final cut validation belong to the adapter. */
@DoNotStrip
internal class FabricPortalIsolationProjection
@DoNotStrip
constructor(hiddenTags: IntArray, retainedPairs: IntArray) {
  val hiddenBranches: Set<Int> = hiddenTags.toSet()
  val retainedChildren: Map<Int, Set<Int>> =
    retainedPairs.toList().chunked(2).groupBy({ it[0] }, { it[1] }).mapValues { it.value.toSet() }
}
