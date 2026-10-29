package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import androidx.annotation.UiThread
import com.facebook.react.bridge.UIManager
import com.facebook.react.uimanager.IllegalViewOperationException
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.UIManagerHelper
import com.swmansion.reactnativebottomsheet.BottomSheetView
import com.swmansion.reactnativebottomsheet.presentation.NativePortalOrder
import com.swmansion.reactnativebottomsheet.presentation.NativePortalOrderResolver
import com.swmansion.reactnativebottomsheet.presentation.resolvePortalPresentationContext
import com.swmansion.reactnativebottomsheet.presentation.resolvePresentationOwnership

@UiThread
private fun FabricPublicationLease.deliveredStatesFromRegistry(
  manager: UIManager,
  surfaceId: Int,
  presentation: BottomSheetView,
): Map<Int, StateWrapper?>? {
  val tags = sheetTags(surfaceId, presentation.id, presentation.stateWrapper) ?: return null
  val states = mutableMapOf<Int, StateWrapper?>()
  for (tag in tags) {
    val view =
      try {
        manager.resolveView(tag)
      } catch (_: IllegalViewOperationException) {
        return null
      }
    if (view !is BottomSheetView || view.id != tag) return null
    if (tag == presentation.id && view !== presentation) return null
    val state = view.stateWrapper
    states[tag] = state
  }
  return states
}

/** Ephemeral input facts for one exact surface, supplied Active owners, and optional origin. */
internal data class FabricPortalInputContexts(
  val contextOwners: Map<Int, List<BottomSheetView>>,
  val focusContexts: List<Int>,
  val nativeDepth: Int?,
)

@UiThread
internal fun FabricPublicationLease.attachedInputContextsFromRegistry(
  manager: UIManager,
  surfaceId: Int,
  presentations: Collection<BottomSheetView>,
  windowRoot: View,
  origin: View?,
): FabricPortalInputContexts? {
  val first = presentations.firstOrNull() ?: return null
  val states = deliveredStatesFromRegistry(manager, surfaceId, first) ?: return null
  val receipts = mutableMapOf<BottomSheetView, FabricPortalReceipt>()
  val owners = presentations.toList()
  val sourceReceipts = receipts(surfaceId, owners.map { it.id }, states) ?: return null
  if (sourceReceipts.size != owners.size) return null
  for ((owner, receipt) in owners.zip(sourceReceipts)) {
    if (receipt.attachedNativePath(manager::resolveView, owner, windowRoot) == null) return null
    receipts[owner] = receipt
  }
  val source = receipts.getValue(first)
  if (receipts.values.any { it.publicationId != source.publicationId }) return null
  val groups = receipts.keys.groupBy { receipts.getValue(it).contexts.firstOrNull() ?: return null }
  val native = origin?.let {
    source.nativeOrigin(manager::resolveView, it, windowRoot) ?: return null
  }
  val contexts = native?.let { contextPath(surfaceId, it.tag, states) ?: return null }.orEmpty()
  // Registry access is a platform boundary: reject a changed delivered inventory.
  val current = deliveredStatesFromRegistry(manager, surfaceId, first) ?: return null
  if (current.keys != states.keys || states.any { (tag, state) -> current[tag] !== state })
    return null
  return FabricPortalInputContexts(groups, contexts, native?.depth)
}

/**
 * Reads cuts for the complete supplied Active group, choosing retained owners in each logical
 * context with the shared native-order policy. Delivered source States stay complete; physical
 * validation covers the proposed local subtrees and their parent paths. A failure in that scope
 * rejects the whole group. Consume these proposals in current UI work, with final validation before
 * mutation.
 */
@UiThread
internal fun FabricPublicationLease.attachedIsolationCutsFromRegistry(
  manager: UIManager,
  presentations: Collection<BottomSheetView>,
  windowRoot: View,
): Map<Int, PortalAccessibilityCut>? {
  val cutsBySurface = mutableMapOf<Int, PortalAccessibilityCut>()
  for ((surface, owners) in presentations.groupBy(UIManagerHelper::getSurfaceId)) {
    if (surface <= 0) return null
    val states = deliveredStatesFromRegistry(manager, surface, owners.first()) ?: return null
    val receipts = mutableMapOf<BottomSheetView, FabricPortalReceipt>()
    val sourceReceipts = receipts(surface, owners.map { it.id }, states) ?: return null
    if (sourceReceipts.size != owners.size) return null
    for ((owner, receipt) in owners.zip(sourceReceipts)) {
      if (receipt.attachedNativePath(manager::resolveView, owner, windowRoot) == null) return null
      receipts[owner] = receipt
    }
    val source = receipts.getValue(owners.first())
    val localGroups =
      receipts.keys.groupBy { receipts.getValue(it).contexts.firstOrNull() ?: return null }
    val nativeContexts = owners.associateWith { it.resolvePortalPresentationContext() }
    val retainedPresentations =
      localGroups.values.flatMap { group ->
        resolvePresentationOwnership(group) { first, second ->
            val firstContext = nativeContexts[first]
            val secondContext = nativeContexts[second]
            if (firstContext == null || secondContext == null) NativePortalOrder.UNKNOWN
            else NativePortalOrderResolver.compare(firstContext, secondContext)
          }
          .retainedPresentations
      }
    val retainedByHost =
      retainedPresentations
        .groupBy { receipts.getValue(it).hostTag }
        .mapValues { (_, retained) -> retained.map { it.id } }
    val projection = isolation(surface, states, source.nativeParents, retainedByHost) ?: return null
    val branches =
      source.nativeIsolationBranches(projection, manager::resolveView, windowRoot) ?: return null
    cutsBySurface[surface] = PortalAccessibilityCut(branches, retainedPresentations)
  }
  return cutsBySurface
}
