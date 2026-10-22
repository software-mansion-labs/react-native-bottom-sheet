package com.swmansion.reactnativebottomsheet.closerequest

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.BackEventCompat
import androidx.annotation.RequiresApi

/** Receives system input separately from the Activity dispatcher used by RN's async default. */
internal class PortalBackInput(view: View, private val callback: CloseRequestBackCallback) {
  private val platform = if (Build.VERSION.SDK_INT >= 33) Api33(view, callback) else null
  private var enabled = false

  fun update(enabled: Boolean) {
    this.enabled = enabled
    platform?.update(enabled)
  }

  fun dispatchKey(event: KeyEvent) =
    (enabled || callback.isBackInProgress) && callback.dispatchKey(event)

  fun dispose() {
    enabled = false
    platform?.update(false)
  }

  @RequiresApi(33)
  private class Api33(private val view: View, callback: CloseRequestBackCallback) {
    private var dispatcher: OnBackInvokedDispatcher? = null
    private val handler =
      if (Build.VERSION.SDK_INT >= 34) Api34.createCallback(callback)
      else OnBackInvokedCallback { callback.handleOnBackPressed() }

    fun update(enabled: Boolean) {
      val current = if (enabled) view.findOnBackInvokedDispatcher() else null
      if (dispatcher === current) return
      dispatcher?.unregisterOnBackInvokedCallback(handler)
      dispatcher = current
      current?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, handler)
    }
  }

  @RequiresApi(34)
  private object Api34 {
    fun createCallback(callback: CloseRequestBackCallback): OnBackInvokedCallback =
      object : OnBackAnimationCallback {
        override fun onBackStarted(backEvent: BackEvent) {
          callback.handleOnBackStarted(
            BackEventCompat(
              backEvent.touchX,
              backEvent.touchY,
              backEvent.progress,
              backEvent.swipeEdge,
            )
          )
        }

        override fun onBackCancelled() = callback.handleOnBackCancelled()

        override fun onBackInvoked() = callback.handleOnBackPressed()
      }
  }
}
