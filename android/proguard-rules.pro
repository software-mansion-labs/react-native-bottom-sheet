# JSTouchDispatcherCompat resolves this 3-arg overload reflectively by name on RN >= 0.82
# (see JSTouchDispatcherCompat.kt). Keep it so R8/ProGuard in release builds never renames it
# away, which would make the reflective lookup fall back to the 2-arg overload and silently drop
# the active-touch sweep. Remove this rule together with the shim once the minimum supported
# React Native is >= 0.82.
-keep class com.facebook.react.uimanager.JSTouchDispatcher {
    public void onChildStartedNativeGesture(android.view.MotionEvent, com.facebook.react.uimanager.events.EventDispatcher, com.facebook.react.bridge.ReactContext);
}
