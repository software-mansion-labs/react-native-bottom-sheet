package com.swmansion.reactnativebottomsheet.accessibility

import com.facebook.react.uimanager.StateWrapper

/** Keep per-owner fixture decisions local while exercising the production batch interface. */
internal abstract class TestFabricPublicationLease : FabricPublicationLease {
  abstract fun receipt(
    surfaceId: Int,
    tag: Int,
    states: Map<Int, StateWrapper?>,
  ): FabricPortalReceipt?

  override fun receipts(
    surfaceId: Int,
    tags: List<Int>,
    states: Map<Int, StateWrapper?>,
  ): List<FabricPortalReceipt>? = tags.map { receipt(surfaceId, it, states) ?: return null }
}

internal fun FabricPortalReceipt(
  publicationId: String,
  hostTag: Int,
  contextPath: IntArray,
  ancestorPath: IntArray,
  sourceTags: IntArray,
  parentPairs: IntArray,
): FabricPortalReceipt =
  FabricPortalReceipt(
    publicationId,
    hostTag,
    contextPath,
    ancestorPath,
    FabricPortalSourceMetadata(sourceTags, parentPairs),
  )
