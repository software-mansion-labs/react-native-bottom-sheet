package com.swmansion.reactnativebottomsheet

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import android.view.View
import com.facebook.react.viewmanagers.BottomSheetViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetViewManagerInterface

@ReactModule(name = BottomSheetViewManager.NAME)
class BottomSheetViewManager :
  ViewGroupManager<BottomSheetView>(),
  BottomSheetViewManagerInterface<BottomSheetView> {

  companion object {
    const val NAME = "BottomSheetView"
  }

  private val delegate = BottomSheetViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<BottomSheetView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): BottomSheetView {
    val view = BottomSheetView(context)
    view.listener = object : BottomSheetViewListener {
      override fun onIndexChange(index: Int) {
        val event = com.facebook.react.bridge.Arguments.createMap().apply {
          putInt("index", index)
        }
        val reactContext = view.context as? ThemedReactContext ?: return
        reactContext
          .getJSModule(com.facebook.react.uimanager.events.RCTEventEmitter::class.java)
          .receiveEvent(view.id, "topIndexChange", event)
      }

      override fun onPositionChange(position: Double) {
        val event = com.facebook.react.bridge.Arguments.createMap().apply {
          putDouble("position", position)
        }
        val reactContext = view.context as? ThemedReactContext ?: return
        reactContext
          .getJSModule(com.facebook.react.uimanager.events.RCTEventEmitter::class.java)
          .receiveEvent(view.id, "topPositionChange", event)
      }
    }
    return view
  }

  override fun addView(parent: BottomSheetView, child: View, index: Int) {
    parent.addSheetChild(child, index)
  }

  override fun getChildCount(parent: BottomSheetView): Int = parent.sheetChildCount

  override fun getChildAt(parent: BottomSheetView, index: Int): View? = parent.getSheetChildAt(index)

  override fun removeViewAt(parent: BottomSheetView, index: Int) {
    parent.removeSheetChildAt(index)
  }

  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> {
    return mapOf(
      "topIndexChange" to mapOf("registrationName" to "onIndexChange"),
      "topPositionChange" to mapOf("registrationName" to "onPositionChange"),
    )
  }

  @ReactProp(name = "detents")
  override fun setDetents(view: BottomSheetView, detents: ReadableArray?) {
    if (detents == null) return
    val list = mutableListOf<Map<String, Any>>()
    for (i in 0 until detents.size()) {
      val map = detents.getMap(i) ?: continue
      list.add(
        mapOf(
          "height" to map.getDouble("height"),
          "programmatic" to map.getBoolean("programmatic"),
        )
      )
    }
    view.setDetents(list)
  }

  @ReactProp(name = "index")
  override fun setIndex(view: BottomSheetView, index: Int) {
    view.setIndex(index)
  }

  @ReactProp(name = "animateIn")
  override fun setAnimateIn(view: BottomSheetView, animateIn: Boolean) {
    view.animateIn = animateIn
  }

  override fun onDropViewInstance(view: BottomSheetView) {
    super.onDropViewInstance(view)
    view.destroy()
  }
}
