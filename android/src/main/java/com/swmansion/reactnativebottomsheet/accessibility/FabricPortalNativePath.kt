package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.facebook.react.uimanager.IllegalViewOperationException
import com.facebook.react.views.modal.ReactModalHostView
import java.util.Collections
import java.util.IdentityHashMap

private fun cachedNativeLookup(resolveView: (Int) -> View?): (Int) -> View? {
  val registry = mutableMapOf<Int, View?>()
  return { tag ->
    if (tag !in registry) {
      registry[tag] =
        try {
          resolveView(tag)
        } catch (_: IllegalViewOperationException) {
          null
        }
    }
    registry[tag]
  }
}

private fun View.isManagedContainer(owner: View?, child: View): Boolean {
  if (owner == null) return false
  if (this is PortalNativeContainer) return portalNativeOwner === owner
  if (owner !is ReactModalHostView || this !is ViewGroup) return false
  val index = indexOfChild(child)
  return index >= 0 && owner.childCount == childCount && owner.getChildAt(index) === child
}

/** Validates expected source materialization/edges, not family bindings or completed mounting. */
@UiThread
internal fun FabricPortalReceipt.nativeMembers(resolveView: (Int) -> View?): Map<Int, View>? {
  val rootTag = nativeParents.entries.singleOrNull { it.value == 0 }?.key ?: return null
  if (!logicalTags.toSet().containsAll(nativeParents.keys)) return null
  val registered = cachedNativeLookup(resolveView)
  val members = mutableMapOf<Int, View>()
  for (tag in logicalTags) {
    val view = registered(tag)
    if ((tag in nativeParents) != (view != null)) return null
    if (view != null) {
      if (view.id != tag) return null
      members[tag] = view
    }
  }
  val root = members[rootTag] as? ViewGroup ?: return null
  val pending = mutableListOf<View>(root)
  for ((tag, expectedParent) in nativeParents) {
    if (tag == rootTag) continue
    var child = members[tag] ?: return null
    val seen = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
    while (true) {
      val parent = child.parent as? ViewGroup ?: return null
      if (!seen.add(parent)) return null
      pending.add(parent)
      val index = parent.indexOfChild(child)
      if (index < 0 || parent.getChildAt(index) !== child) return null
      val owner = registered(parent.id)
      if (owner === parent || parent.isManagedContainer(owner, child)) {
        if (owner !== members[expectedParent]) return null
        break
      }
      child = parent
    }
  }
  val seen = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
  while (pending.isNotEmpty()) {
    val view = pending.removeAt(pending.lastIndex)
    // Managed roots may be reached through both source and physical paths.
    if (!seen.add(view)) continue
    if (registered(view.id) === view && members[view.id] !== view) return null
    if (view is ViewGroup) {
      for (index in 0 until view.childCount) pending.add(view.getChildAt(index))
    }
  }
  return members
}

/**
 * Checks queried ancestry, not full native branch/family membership or completed mounting. Missing
 * intermediate registry views may be flattened logical nodes. The returned physical path does not
 * replace a logical context with its nearest materialized ancestor.
 */
@UiThread
internal fun FabricPortalReceipt.nativePath(
  resolveView: (Int) -> View?,
  presentation: View,
): List<View>? {
  if (ancestors.firstOrNull() != presentation.id || hostTag !in ancestors) return null
  val registered = cachedNativeLookup(resolveView)
  val expected = mutableListOf<View>()
  for (tag in ancestors) {
    val view = registered(tag) ?: continue
    if (view.id != tag) return null
    expected.add(view)
  }
  if (expected.firstOrNull() !== presentation || registered(ancestors.last()) == null) return null
  val host = registered(hostTag) as? ViewGroup ?: return null
  if (
    host.getTag(com.facebook.react.R.id.view_tag_native_id) !=
      "__react_native_bottom_sheet_portal_host"
  )
    return null
  val path = mutableListOf<View>()
  var current = presentation
  var next = 0
  while (true) {
    if (path.any { it === current }) return null
    path.add(current)
    val owner = registered(current.id)
    if (owner === current) {
      if (expected.getOrNull(next) !== current) return null
      next++
      if (next == expected.size) return path
    } else if (owner != null && expected.getOrNull(next) === owner && next == expected.lastIndex) {
      // Native presentation roots redirect children into an owned container.
      // A copied tag alone cannot establish this physical owner relationship.
      val child = path.getOrNull(path.lastIndex - 1) ?: return null
      if (!current.isManagedContainer(owner, child)) return null
      return path
    }
    val parent = current.parent as? ViewGroup ?: return null
    val index = parent.indexOfChild(current)
    if (index < 0 || parent.getChildAt(index) !== current) return null
    current = parent
  }
}

/** Adds current attachment/window checks; it does not establish full applied native membership. */
@UiThread
internal fun FabricPortalReceipt.attachedNativePath(
  resolveView: (Int) -> View?,
  presentation: View,
  windowRoot: View,
): List<View>? {
  val path = nativePath(resolveView, presentation) ?: return null
  val token = windowRoot.windowToken ?: return null
  if (
    !windowRoot.isAttachedToWindow ||
      windowRoot.rootView !== windowRoot ||
      windowRoot.windowToken == null
  )
    return null
  if (
    path.any {
      !it.isAttachedToWindow || it.windowToken !== token || it.rootView !== windowRoot
    }
  )
    return null
  return path
}

/** Maps a current source projection to native frontiers without mutating accessibility state. */
@UiThread
internal fun FabricPortalReceipt.nativeIsolationBranches(
  projection: FabricPortalIsolationProjection,
  resolveView: (Int) -> View?,
  windowRoot: View,
): Set<View>? {
  if (!windowRoot.isAttachedToWindow || windowRoot.rootView !== windowRoot) return null
  val members = nativeMembers(resolveView) ?: return null
  val logical = logicalTags.toSet()
  if (
    projection.retainedChildren.any { (parent, children) ->
      parent !in logical || children.any { it !in logical }
    }
  )
    return null
  val protected = nativeIdentitySet()
  for (tag in projection.retainedChildren.values.flatten().toSet()) {
    val view = members[tag] ?: continue // Flattened edges stay logical.
    protected.addAll(view.currentWindowPath(windowRoot) ?: return null)
  }
  val branches = nativeIdentitySet()
  for (tag in projection.hiddenBranches) branches.add(members[tag] ?: return null)
  val childrenByParent = nativeParents.entries.groupBy({ it.value }, { it.key })
  for (parent in projection.retainedChildren.keys) {
    val view = members[parent] ?: continue // Never substitute a materialized ancestor.
    val group = view as? ViewGroup ?: return null
    val containers = Collections.newSetFromMap(IdentityHashMap<ViewGroup, Boolean>())
    containers.add(group)
    for (tag in childrenByParent[parent].orEmpty()) {
      var child = members[tag] ?: return null
      val seen = nativeIdentitySet()
      while (true) {
        val container = child.parent as? ViewGroup ?: return null
        if (!seen.add(container)) return null
        if (container === group) break
        if (container.isManagedContainer(group, child)) {
          containers.add(container)
          break
        }
        child = container
      }
    }
    val pending = ArrayDeque<View>()
    for (container in containers) {
      pending.addAll(container.currentNativeChildren() ?: return null)
    }
    while (pending.isNotEmpty()) {
      val child = pending.removeLast()
      if (members[child.id] === child) continue // Source frontiers were projected in C++.
      if (child !in protected) {
        branches.add(child)
      } else {
        val container = child as? ViewGroup ?: return null
        pending.addAll(container.currentNativeChildren() ?: return null)
      }
    }
  }
  val result = nativeIdentitySet()
  for (branch in branches) {
    if (branch in protected) return null
    val path = branch.currentWindowPath(windowRoot) ?: return null
    if (path.drop(1).none { it in branches }) result.add(branch)
  }
  return result
}

private fun nativeIdentitySet(): MutableSet<View> =
  Collections.newSetFromMap(IdentityHashMap<View, Boolean>())

private fun View.currentWindowPath(windowRoot: View): List<View>? {
  val token = windowRoot.windowToken ?: return null
  val path = mutableListOf<View>()
  var current = this
  while (true) {
    if (
      path.any { it === current } ||
        !current.isAttachedToWindow ||
        current.windowToken !== token ||
        current.rootView !== windowRoot
    )
      return null
    path.add(current)
    if (current === windowRoot) return path
    val parent = current.parent as? ViewGroup ?: return null
    val index = parent.indexOfChild(current)
    if (index < 0 || parent.getChildAt(index) !== current) return null
    current = parent
  }
}

private fun ViewGroup.currentNativeChildren(): List<View>? {
  val children = mutableListOf<View>()
  for (index in 0 until childCount) {
    val child = getChildAt(index) ?: return null
    val parent = child.parent as? ViewGroup ?: return null
    val actualIndex = parent.indexOfChild(child)
    if (actualIndex < 0 || parent.getChildAt(actualIndex) !== child) return null
    if (parent !== this && !parent.isManagedContainer(this, child)) return null
    children.add(child)
  }
  return children
}
