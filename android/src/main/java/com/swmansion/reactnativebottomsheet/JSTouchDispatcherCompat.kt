package com.swmansion.reactnativebottomsheet

import android.view.MotionEvent
import com.facebook.react.bridge.ReactContext
import com.facebook.react.uimanager.JSTouchDispatcher
import com.facebook.react.uimanager.events.EventDispatcher
import java.lang.reflect.Method

/*
 * Compatibility shim for JSTouchDispatcher.onChildStartedNativeGesture across React Native
 * versions. Self-contained so it can be deleted in one step (see "WHEN TO REMOVE").
 *
 * WHY THIS EXISTS
 * React Native 0.82 added a 3-arg overload
 * `onChildStartedNativeGesture(MotionEvent, EventDispatcher, ReactContext)` that sweeps the active
 * touch for the gesture's target tag (the counterpart to the mark performed by the 3-arg
 * `handleTouchEvent`, which BottomSheetView keeps using). That overload does not exist on the
 * 0.76-0.81 React Natives still allowed by the `react-native` peer dependency, so referencing it
 * directly fails to compile there (issue #35).
 *
 * WHAT IT DOES
 * Resolves the 3-arg overload reflectively once and uses it when present (RN >= 0.82), preserving
 * the active-touch sweep so it stays paired with the mark from `handleTouchEvent`. Falls back to
 * the always-available 2-arg overload on RN 0.76-0.81 (where no sweep-on-takeover exists anyway,
 * matching React Native's own behavior on those versions). A `-keep` rule in proguard-rules.pro
 * guarantees the reflected method survives R8 so release builds never silently lose the sweep.
 *
 * WHEN TO REMOVE
 * Once the minimum supported React Native is >= 0.82: delete this file and the matching rule in
 * proguard-rules.pro, then call the 3-arg overload directly at the BottomSheetView call site,
 *   jSTouchDispatcher.onChildStartedNativeGesture(ev, dispatcher, reactContext)
 * (annotated with `@OptIn(UnstableReactNativeAPI::class)`).
 */
private val onChildStartedNativeGestureWithContext: Method? =
  try {
    JSTouchDispatcher::class.java.getMethod(
      "onChildStartedNativeGesture",
      MotionEvent::class.java,
      EventDispatcher::class.java,
      ReactContext::class.java,
    )
  } catch (e: NoSuchMethodException) {
    null
  }

internal fun JSTouchDispatcher.onChildStartedNativeGestureCompat(
  event: MotionEvent,
  eventDispatcher: EventDispatcher,
  reactContext: ReactContext,
) {
  val withContext = onChildStartedNativeGestureWithContext
  if (withContext != null) {
    withContext.invoke(this, event, eventDispatcher, reactContext)
  } else {
    onChildStartedNativeGesture(event, eventDispatcher)
  }
}
