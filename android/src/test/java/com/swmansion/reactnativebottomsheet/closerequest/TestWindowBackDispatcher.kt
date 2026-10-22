package com.swmansion.reactnativebottomsheet.closerequest

import android.os.Build
import android.view.KeyEvent
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import androidx.activity.ComponentActivity
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/**
 * SDK-boundary driver. Physical instrumentation verifies the framework input pipeline separately.
 */
@Implements(className = "android.window.WindowOnBackInvokedDispatcher", minSdk = 33)
class TestWindowBackDispatcher {
  private val callbacks = linkedMapOf<OnBackInvokedCallback, Int>()
  private var captured: OnBackAnimationCallback? = null

  @Implementation
  fun registerOnBackInvokedCallback(priority: Int, callback: OnBackInvokedCallback) {
    callbacks.remove(callback)
    callbacks[callback] = priority
  }

  @Implementation
  fun unregisterOnBackInvokedCallback(callback: OnBackInvokedCallback) {
    callbacks.remove(callback)
  }

  private fun top() = callbacks.keys.lastOrNull { callbacks[it] == callbacks.values.maxOrNull() }

  fun start() {
    captured = top() as? OnBackAnimationCallback
    captured?.onBackStarted(BackEvent(0f, 0f, 0f, BackEvent.EDGE_LEFT))
  }

  fun cancel() {
    val previous = captured
    captured = null
    previous?.onBackCancelled()
  }

  fun commit(fallback: () -> Unit) {
    val target = captured ?: top()
    captured = null
    if (target != null) target.onBackInvoked() else fallback()
  }
}

internal fun ComponentActivity.systemBack(
  fallback: () -> Unit = { onBackPressedDispatcher.onBackPressed() }
) {
  if (Build.VERSION.SDK_INT < 33) {
    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
    dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
  } else windowBackDriver().commit(fallback)
}

internal fun ComponentActivity.startSystemBack() = windowBackDriver().start()

internal fun ComponentActivity.cancelSystemBack() = windowBackDriver().cancel()

private fun ComponentActivity.windowBackDriver(): TestWindowBackDispatcher =
  Shadow.extract(requireNotNull(window.decorView.findOnBackInvokedDispatcher()))
