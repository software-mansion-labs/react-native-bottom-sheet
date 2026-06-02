package com.swmansion.reactnativebottomsheet

import android.view.View
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.ReactStylesDiffMap
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.Event
import com.facebook.react.viewmanagers.BottomSheetViewManagerDelegate
import com.facebook.react.viewmanagers.BottomSheetViewManagerInterface

@ReactModule(name = BottomSheetViewManager.NAME)
class BottomSheetViewManager :
  ViewGroupManager<BottomSheetView>(), BottomSheetViewManagerInterface<BottomSheetView> {

  companion object {
    const val NAME = "BottomSheetView"
  }

  private val delegate = BottomSheetViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<BottomSheetView> = delegate

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): BottomSheetView {
    val view = BottomSheetView(context)
    view.listener =
      object : BottomSheetViewListener {
        override fun onIndexChange(index: Int) {
          val event = Arguments.createMap().apply { putInt("index", index) }
          dispatchEvent(view, "topIndexChange", event)
        }

        override fun onSettle(index: Int) {
          val event = Arguments.createMap().apply { putInt("index", index) }
          dispatchEvent(view, "topSettle", event)
        }

        override fun onPositionChange(position: Double, index: Double) {
          val event =
            Arguments.createMap().apply {
              putDouble("position", position)
              putDouble("index", index)
            }
          dispatchEvent(view, "topPositionChange", event)
        }
      }
    return view
  }

  private fun dispatchEvent(view: BottomSheetView, eventName: String, eventData: WritableMap) {
    val reactContext = UIManagerHelper.getReactContext(view)
    val surfaceId = UIManagerHelper.getSurfaceId(view)
    UIManagerHelper.getEventDispatcherForReactTag(reactContext, view.id)
      ?.dispatchEvent(BottomSheetEvent(surfaceId, view.id, eventName, eventData))
  }

  override fun addView(parent: BottomSheetView, child: View, index: Int) {
    parent.addSheetChild(child, index)
  }

  override fun getChildCount(parent: BottomSheetView): Int = parent.sheetChildCount

  override fun getChildAt(parent: BottomSheetView, index: Int): View? =
    parent.getSheetChildAt(index)

  override fun removeViewAt(parent: BottomSheetView, index: Int) {
    parent.removeSheetChildAt(index)
  }

  override fun updateState(
    view: BottomSheetView,
    props: ReactStylesDiffMap,
    stateWrapper: StateWrapper?,
  ): Any? {
    view.stateWrapper = stateWrapper
    return null
  }

  override fun needsCustomLayoutForChildren(): Boolean = true

  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> {
    return mapOf(
      "topIndexChange" to mapOf("registrationName" to "onIndexChange"),
      "topSettle" to mapOf("registrationName" to "onSettle"),
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
        mapOf<String, Any>(
          "value" to map.getDouble("value"),
          "kind" to (map.getString("kind") ?: "points"),
          "programmatic" to map.getBoolean("programmatic"),
        )
      )
    }
    view.setDetents(list)
  }

  @ReactProp(name = "maxDetentHeight")
  override fun setMaxDetentHeight(view: BottomSheetView, maxDetentHeight: Double) {
    view.setMaxDetentHeight(maxDetentHeight)
  }

  @ReactProp(name = "index")
  override fun setIndex(view: BottomSheetView, index: Int) {
    view.setIndex(index)
  }

  @ReactProp(name = "animateIn")
  override fun setAnimateIn(view: BottomSheetView, animateIn: Boolean) {
    view.animateIn = animateIn
  }

  @ReactProp(name = "modal")
  override fun setModal(view: BottomSheetView, modal: Boolean) {
    view.modal = modal
  }

  @ReactProp(name = "disableScrollableNegotiation")
  override fun setDisableScrollableNegotiation(view: BottomSheetView, value: Boolean) {
    view.disableScrollableNegotiation = value
  }

  @ReactProp(name = "scrimColor", customType = "Color")
  override fun setScrimColor(view: BottomSheetView, scrimColor: Int?) {
    view.setScrimColor(scrimColor)
  }

  @ReactProp(name = "scrimOpacities")
  override fun setScrimOpacities(view: BottomSheetView, value: ReadableArray?) {
    val opacities = mutableListOf<Float>()
    if (value != null) {
      for (i in 0 until value.size()) {
        opacities.add(value.getDouble(i).toFloat())
      }
    }
    view.setScrimOpacities(opacities)
  }

  override fun onDropViewInstance(view: BottomSheetView) {
    super.onDropViewInstance(view)
    view.destroy()
  }
}

private class BottomSheetEvent(
  surfaceId: Int,
  viewTag: Int,
  private val jsEventName: String,
  private val eventData: WritableMap,
) : Event<BottomSheetEvent>(surfaceId, viewTag) {
  override fun getEventName(): String = jsEventName

  override fun canCoalesce(): Boolean = false

  override fun getEventData(): WritableMap = eventData
}
