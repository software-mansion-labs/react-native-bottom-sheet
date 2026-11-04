package com.swmansion.reactnativebottomsheet

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetContentWrapperViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetContentWrapperViewManagerInterface
import com.facebook.react.views.view.ReactViewGroup

/**
 * The in-flow wrapper around the sheet's content: a plain view natively. Its own component type
 * only so its shadow node can report the card's scale (see `BottomSheetCardContent` in
 * `ShadowNodes.h`).
 */
@ReactModule(name = BottomSheetContentWrapperViewManager.NAME)
class BottomSheetContentWrapperViewManager :
  ViewGroupManager<ReactViewGroup>(),
  BottomSheetContentWrapperViewManagerInterface<ReactViewGroup> {

  companion object {
    const val NAME = "BottomSheetContentWrapperView"
  }

  private val delegate = BottomSheetContentWrapperViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<ReactViewGroup> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): ReactViewGroup =
    ReactViewGroup(context)
}
