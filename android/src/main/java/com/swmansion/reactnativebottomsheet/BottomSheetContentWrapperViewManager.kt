package com.swmansion.reactnativebottomsheet

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ReactStylesDiffMap
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetContentWrapperViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetContentWrapperViewManagerInterface

@ReactModule(name = BottomSheetContentWrapperViewManager.NAME)
class BottomSheetContentWrapperViewManager :
  ViewGroupManager<BottomSheetContentWrapperView>(),
  BottomSheetContentWrapperViewManagerInterface<BottomSheetContentWrapperView> {

  companion object {
    const val NAME = "BottomSheetContentWrapperView"
  }

  private val delegate = BottomSheetContentWrapperViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<BottomSheetContentWrapperView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): BottomSheetContentWrapperView =
    BottomSheetContentWrapperView(context)

  override fun updateState(
    view: BottomSheetContentWrapperView,
    props: ReactStylesDiffMap,
    stateWrapper: StateWrapper?,
  ): Any? {
    view.stateWrapper = stateWrapper
    return null
  }
}
