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

        override fun onCloseRequest() {
          dispatchEvent(view, "topCloseRequest", Arguments.createMap())
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
    view.eventDispatcher =
      UIManagerHelper.getEventDispatcherForReactTag(UIManagerHelper.getReactContext(view), view.id)
    return null
  }

  override fun needsCustomLayoutForChildren(): Boolean = true

  override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> {
    return mapOf(
      "topIndexChange" to mapOf("registrationName" to "onIndexChange"),
      "topSettle" to mapOf("registrationName" to "onSettle"),
      "topPositionChange" to mapOf("registrationName" to "onPositionChange"),
      "topCloseRequest" to mapOf("registrationName" to "onCloseRequest"),
    )
  }

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
        ),
      )
    }
    view.setDetents(list)
  }

  override fun setIndex(view: BottomSheetView, index: Int) {
    // Props within an update arrive in no particular order, so apply the index after the update's
    // detents, as iOS does. Otherwise an index that targets a newly added detent is dropped.
    view.pendingIndex = index
  }

  override fun onAfterUpdateTransaction(view: BottomSheetView) {
    super.onAfterUpdateTransaction(view)
    view.applyPendingIndex()
  }

  override fun setAnimateIn(view: BottomSheetView, animateIn: Boolean) {
    view.animateIn = animateIn
  }

  override fun setAnimateContentHeight(view: BottomSheetView, animateContentHeight: Boolean) {
    view.animateContentHeight = animateContentHeight
  }

  override fun setModal(view: BottomSheetView, modal: Boolean) {
    view.modal = modal
  }

  override fun setNativeOverlay(view: BottomSheetView, value: Boolean) {
    view.setNativeOverlay(value)
  }

  override fun setHasCloseRequestHandler(view: BottomSheetView, value: Boolean) {
    view.setHasCloseRequestHandler(value)
  }

  override fun setExtendUnderStatusBar(view: BottomSheetView, value: Boolean) {
    view.extendUnderStatusBar = value
  }

  override fun setScrollableExpandNegotiation(view: BottomSheetView, value: Int) {
    view.scrollableExpandNegotiation = value
  }

  override fun setScrollableCollapseNegotiation(view: BottomSheetView, value: Int) {
    view.scrollableCollapseNegotiation = value
  }

  override fun setScrimColor(view: BottomSheetView, scrimColor: Int?) {
    view.setScrimColor(scrimColor)
  }

  override fun setScrimOpacities(view: BottomSheetView, value: ReadableArray?) {
    val opacities = mutableListOf<Float>()
    if (value != null) {
      for (i in 0 until value.size()) {
        opacities.add(value.getDouble(i).toFloat())
      }
    }
    view.setScrimOpacities(opacities)
  }

  @ReactProp(name = "surfaceCornerRadii")
  override fun setSurfaceCornerRadii(view: BottomSheetView, value: ReadableArray?) {
    val radii = mutableListOf<Float>()
    if (value != null) {
      for (i in 0 until value.size()) {
        radii.add(value.getDouble(i).toFloat())
      }
    }
    view.setSurfaceCornerRadii(radii)
  }

  // Android always draws circular corners; the curve applies on iOS only.
  @ReactProp(name = "surfaceCornerCurve")
  override fun setSurfaceCornerCurve(view: BottomSheetView, value: String?) = Unit

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
