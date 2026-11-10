package com.swmansion.reactnativebottomsheet

import android.graphics.Color
import android.view.View
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ColorPropConverter
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
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
      val inset = { key: String -> if (map.hasKey(key)) map.getDouble(key) else 0.0 }
      list.add(
        mapOf<String, Any>(
          "value" to map.getDouble("value"),
          "kind" to (map.getString("kind") ?: "points"),
          "programmatic" to map.getBoolean("programmatic"),
          "usesStatusBarTopInset" to
            (map.hasKey("usesStatusBarTopInset") && map.getBoolean("usesStatusBarTopInset")),
          "insetTop" to inset("insetTop"),
          "insetLeft" to inset("insetLeft"),
          "insetRight" to inset("insetRight"),
          "insetBottom" to inset("insetBottom"),
          "widestCard" to (map.hasKey("widestCard") && map.getBoolean("widestCard")),
          "scrimOpacity" to
            (if (map.hasKey("scrimOpacity")) map.getDouble("scrimOpacity") else 1.0),
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

  override fun setStacked(view: BottomSheetView, value: Boolean) {
    view.setStacked(value)
  }

  override fun setOpenOrder(view: BottomSheetView, value: Int) {
    view.setOpenOrder(value)
  }

  override fun setHasCloseRequestHandler(view: BottomSheetView, value: Boolean) {
    view.setHasCloseRequestHandler(value)
  }

  override fun setScrollExpandCoordination(view: BottomSheetView, value: Int) {
    view.scrollExpandCoordination = value
  }

  override fun setScrollCollapseCoordination(view: BottomSheetView, value: Int) {
    view.scrollCollapseCoordination = value
  }

  override fun setSettleDuration(view: BottomSheetView, value: Double) {
    view.settleDuration = value.toFloat()
  }

  override fun setScrimColor(view: BottomSheetView, scrimColor: Int?) {
    view.setScrimColor(scrimColor)
  }

  override fun setScrimAccessibilityLabel(view: BottomSheetView, value: String?) {
    view.setScrimAccessibilityLabel(value)
  }

  override fun setSurfaceCornerRadii(view: BottomSheetView, value: ReadableArray?) {
    val radii = mutableListOf<Float>()
    if (value != null) {
      for (i in 0 until value.size()) {
        radii.add(value.getDouble(i).toFloat())
      }
    }
    view.setSurfaceCornerRadii(radii)
  }

  override fun setSurfaceShadows(view: BottomSheetView, value: ReadableArray?) {
    val shadows = mutableListOf<CardShadow>()
    if (value != null) {
      for (i in 0 until value.size()) {
        val shadow = value.getMap(i) ?: continue
        shadows.add(
          CardShadow(
            offsetX = shadow.getDouble("offsetX").toFloat(),
            offsetY = shadow.getDouble("offsetY").toFloat(),
            blurRadius = shadow.getDouble("blurRadius").toFloat(),
            spreadDistance = shadow.getDouble("spreadDistance").toFloat(),
            color = shadowColor(view, shadow),
          ),
        )
      }
    }
    view.setSurfaceShadows(shadows)
  }

  override fun setSurfaceElevation(view: BottomSheetView, value: Double) {
    view.setSurfaceElevation(value.toFloat())
  }

  override fun setSurfaceShadowColor(view: BottomSheetView, value: Int?) {
    view.setSurfaceShadowColor(value ?: Color.BLACK)
  }

  // Processed colors arrive as numbers, or as maps for `PlatformColor`.
  private fun shadowColor(view: BottomSheetView, shadow: ReadableMap): Int {
    val value: Any? =
      when (shadow.getType("color")) {
        ReadableType.Number -> shadow.getDouble("color")
        ReadableType.Map -> shadow.getMap("color")
        else -> null
      }
    return ColorPropConverter.getColor(value, view.context, Color.BLACK)
  }

  // Android always draws circular corners; the curve applies on iOS only.
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
