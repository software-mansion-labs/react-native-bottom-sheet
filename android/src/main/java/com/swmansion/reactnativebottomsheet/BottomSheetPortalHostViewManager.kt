package com.swmansion.reactnativebottomsheet

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetPortalHostViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetPortalHostViewManagerInterface

@ReactModule(name = BottomSheetPortalHostViewManager.NAME)
class BottomSheetPortalHostViewManager :
  ViewGroupManager<BottomSheetPortalHostView>(),
  BottomSheetPortalHostViewManagerInterface<BottomSheetPortalHostView> {

  companion object {
    const val NAME = "BottomSheetPortalHostView"
  }

  private val delegate = BottomSheetPortalHostViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<BottomSheetPortalHostView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): BottomSheetPortalHostView =
    BottomSheetPortalHostView(context)
}
