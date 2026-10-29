package com.swmansion.reactnativebottomsheet.accessibility

import com.facebook.proguard.annotations.DoNotStrip

/** Exact source metadata; this does not establish attached native membership. */
@DoNotStrip
internal class FabricPortalReceipt
@DoNotStrip
constructor(
  val publicationId: String,
  val hostTag: Int,
  contextPath: IntArray,
  ancestorPath: IntArray,
  private val source: FabricPortalSourceMetadata,
) {
  val contexts: List<Int> = contextPath.toList()
  val ancestors: List<Int> = ancestorPath.toList()
  val logicalTags: List<Int>
    get() = source.logicalTags

  val nativeParents: Map<Int, Int>
    get() = source.nativeParents
}

/** Materialized once for one native query; no metadata survives in the source lease. */
@DoNotStrip
internal class FabricPortalSourceMetadata
@DoNotStrip
constructor(sourceTags: IntArray, parentPairs: IntArray) {
  val logicalTags: List<Int> = sourceTags.toList()
  val nativeParents: Map<Int, Int> = parentPairs.toList().chunked(2).associate { it[0] to it[1] }
}
