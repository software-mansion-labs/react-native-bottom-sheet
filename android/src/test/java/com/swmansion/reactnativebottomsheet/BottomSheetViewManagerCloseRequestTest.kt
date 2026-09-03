// BridgeReactContext is required by the legacy-architecture test fixture.
@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.BatchEventDispatchedListener
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.uimanager.events.EventDispatcherListener
import com.facebook.react.viewmanagers.BottomSheetViewManagerDelegate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], shadows = [TestArgumentsShadow::class, TestUIManagerHelperShadow::class])
class BottomSheetViewManagerCloseRequestTest {
  @Before
  fun useLocalReactNativeFeatureFlags() {
    ReactNativeFeatureFlagsForTests.setUp()
  }

  @Test
  fun `Back emits one topCloseRequest mapped to onCloseRequest through the manager bridge`() {
    withActivity<ComponentActivity> { activity ->
      val eventDispatcher = RecordingEventDispatcher()
      TestUIManagerHelperShadow.eventDispatcher = eventDispatcher
      val reactContext = BridgeReactContext(activity.applicationContext)
      val themedContext = ThemedReactContext(reactContext, activity, "test", 1)
      val manager = BottomSheetViewManager()
      val sheet = createViewThroughManager(manager, themedContext)
      sheet.id = 1
      val delegate =
        BottomSheetViewManagerDelegate<BottomSheetView, BottomSheetViewManager>(manager)

      delegate.setProperty(sheet, "modal", true)
      delegate.setProperty(sheet, "hasCloseRequestHandler", true)
      manager.setAnimateIn(sheet, false)
      manager.setDetents(
        sheet,
        JavaOnlyArray.of(
          JavaOnlyMap.of("value", 0.0, "kind", "points", "programmatic", false),
          JavaOnlyMap.of("value", 300.0, "kind", "points", "programmatic", false),
        ),
      )
      manager.setIndex(sheet, 1)
      activity.setContentView(sheet)
      layout(sheet)
      reactContext.onHostResume(activity)
      eventDispatcher.eventNames.clear()

      activity.onBackPressedDispatcher.onBackPressed()

      assertEquals(listOf("topCloseRequest"), eventDispatcher.eventNames)
      assertEquals(
        "onCloseRequest",
        manager.exportedCustomDirectEventTypeConstants["topCloseRequest"]
          ?.let { it as Map<*, *> }
          ?.get("registrationName"),
      )

      sheet.destroy()
      reactContext.onHostDestroy()
      TestUIManagerHelperShadow.eventDispatcher = null
    }
  }

  private fun layout(view: View) {
    view.measure(
      View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, 1080, 1920)
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun createViewThroughManager(
    manager: BottomSheetViewManager,
    context: ThemedReactContext,
  ): BottomSheetView =
    BottomSheetViewManager::class
      .java
      .getDeclaredMethod("createViewInstance", ThemedReactContext::class.java)
      .apply { isAccessible = true }
      .invoke(manager, context) as BottomSheetView

  private inline fun <reified T : Activity> withActivity(block: (T) -> Unit) {
    val controller = Robolectric.buildActivity(T::class.java).setup()
    try {
      block(controller.get())
    } finally {
      controller.close()
    }
  }
}

@Implements(UIManagerHelper::class)
class TestUIManagerHelperShadow {
  companion object {
    var eventDispatcher: EventDispatcher? = null

    @JvmStatic
    @Implementation
    fun getEventDispatcherForReactTag(
      context: ReactContext,
      reactTag: Int,
    ): EventDispatcher? = eventDispatcher
  }
}

@Implements(Arguments::class)
class TestArgumentsShadow {
  companion object {
    @JvmStatic @Implementation fun createMap(): WritableMap = JavaOnlyMap()
  }
}

private class RecordingEventDispatcher : EventDispatcher {
  val eventNames = mutableListOf<String>()

  override fun dispatchEvent(event: Event<*>) {
    eventNames.add(event.eventName)
  }

  override fun dispatchAllEvents() = Unit

  override fun addListener(listener: EventDispatcherListener) = Unit

  override fun removeListener(listener: EventDispatcherListener) = Unit

  override fun addBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  override fun removeBatchEventDispatchedListener(listener: BatchEventDispatchedListener) = Unit

  @Suppress("OVERRIDE_DEPRECATION") override fun onCatalystInstanceDestroyed() = Unit
}
