package com.swmansion.reactnativebottomsheet.accessibility

import com.facebook.react.uimanager.StateWrapper

/** Native source resource, independent of presentation ownership and isolation. */
internal interface FabricPublicationLease {
  fun bootstrap()

  /** Inventory of one exact source revision, independent of native ancestry or Active state. */
  fun sheetTags(surfaceId: Int, tag: Int, state: StateWrapper?): IntArray?

  /** Requires every sheet's delivered State from one source revision, including inactive sheets. */
  fun receipt(surfaceId: Int, tag: Int, states: Map<Int, StateWrapper?>): FabricPortalReceipt?

  /** Source contexts for an arbitrary current native anchor; an empty path means outside. */
  fun contextPath(surfaceId: Int, tag: Int, states: Map<Int, StateWrapper?>): List<Int>?

  /** Projection only; the caller validates native membership before supplying its parent map. */
  fun isolation(
    surfaceId: Int,
    states: Map<Int, StateWrapper?>,
    nativeParents: Map<Int, Int>,
    retainedByHost: Map<Int, List<Int>>,
  ): FabricPortalIsolationProjection?

  fun release()
}
