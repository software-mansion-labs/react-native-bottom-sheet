@file:OptIn(com.facebook.react.common.annotations.UnstableReactNativeAPI::class)

package com.swmansion.reactnativebottomsheet.accessibility

import androidx.annotation.UiThread
import com.facebook.jni.HybridData
import com.facebook.proguard.annotations.DoNotStrip
import com.facebook.react.fabric.FabricUIManager
import com.facebook.react.uimanager.StateWrapper
import com.facebook.soloader.SoLoader

/** One source resource per observed UIManager; all access is deferred to the UI queue. */
@UiThread
@DoNotStrip
internal class FabricPortalPublication
private constructor(@field:DoNotStrip private val mHybridData: HybridData) :
  FabricPublicationLease {
  private var active = true

  override fun bootstrap() {
    if (active) bootstrapNative()
  }

  override fun sheetTags(surfaceId: Int, tag: Int, state: StateWrapper?): IntArray? =
    if (active) sheetTagsNative(surfaceId, tag, state) else null

  override fun receipt(
    surfaceId: Int,
    tag: Int,
    states: Map<Int, StateWrapper?>,
  ): FabricPortalReceipt? {
    if (!active || tag !in states) return null
    val delivered = states.toList()
    return receiptNative(
      surfaceId,
      tag,
      delivered.map { it.first }.toIntArray(),
      delivered.map { it.second }.toTypedArray(),
    )
  }

  override fun release() {
    if (!active) return
    active = false
    mHybridData.resetNative()
  }

  override fun isolation(
    surfaceId: Int,
    states: Map<Int, StateWrapper?>,
    nativeParents: Map<Int, Int>,
    retainedByHost: Map<Int, List<Int>>,
  ): FabricPortalIsolationProjection? {
    if (!active) return null
    val delivered = states.toList()
    return isolationNative(
      surfaceId,
      delivered.map { it.first }.toIntArray(),
      delivered.map { it.second }.toTypedArray(),
      nativeParents.flatMap { (child, parent) -> listOf(child, parent) }.toIntArray(),
      retainedByHost
        .map { (host, presentations) -> intArrayOf(host, *presentations.toIntArray()) }
        .toTypedArray(),
    )
  }

  @DoNotStrip private external fun bootstrapNative()

  @DoNotStrip
  private external fun sheetTagsNative(surfaceId: Int, tag: Int, state: StateWrapper?): IntArray?

  @DoNotStrip
  private external fun receiptNative(
    surfaceId: Int,
    tag: Int,
    tags: IntArray,
    states: Array<StateWrapper?>,
  ): FabricPortalReceipt?

  @DoNotStrip
  private external fun isolationNative(
    surfaceId: Int,
    tags: IntArray,
    states: Array<StateWrapper?>,
    nativeParents: IntArray,
    retainedGroups: Array<IntArray>,
  ): FabricPortalIsolationProjection?

  companion object {
    fun acquire(identity: Any): FabricPublicationLease? {
      val manager = identity as? FabricUIManager ?: return null
      SoLoader.loadLibrary("react_codegen_ReactNativeBottomSheetSpec")
      return initHybrid(manager)?.let(::FabricPortalPublication)
    }

    @JvmStatic @DoNotStrip private external fun initHybrid(manager: FabricUIManager): HybridData?
  }
}
