package com.swmansion.reactnativebottomsheet.accessibility

import com.facebook.react.uimanager.StateWrapper

internal fun FabricPublicationLease.receipt(
  surfaceId: Int,
  tag: Int,
  states: Map<Int, StateWrapper?>,
): FabricPortalReceipt? = receipts(surfaceId, listOf(tag), states)?.singleOrNull()
