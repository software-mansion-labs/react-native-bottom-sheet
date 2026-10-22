package com.swmansion.reactnativebottomsheet

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import java.util.concurrent.atomic.AtomicInteger

class RecordingReactActivity : ReactActivity() {
  override fun createReactActivityDelegate(): ReactActivityDelegate =
    RecordingReactActivityDelegate(this)
}

class RecordingReactActivityDelegate(private val host: ReactActivity) :
  ReactActivityDelegate(host, null) {
  override fun onCreate(savedInstanceState: Bundle?) = Unit

  override fun onPause() = Unit

  override fun onResume() = Unit

  override fun onDestroy() = Unit

  override fun onWindowFocusChanged(hasFocus: Boolean) = Unit

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = false

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = false

  override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean = false

  override fun onUserLeaveHint() = Unit

  override fun onBackPressed(): Boolean {
    val count = backEntryCount.incrementAndGet()
    if (defaultBackLater && count == 1) {
      Handler(Looper.getMainLooper()).post { host.invokeDefaultOnBackPressed() }
    }
    return true
  }

  companion object {
    val backEntryCount = AtomicInteger()
    var defaultBackLater = false
  }
}

class LegacyRecordingReactActivity : ReactActivity() {
  override fun createReactActivityDelegate(): ReactActivityDelegate =
    RecordingReactActivityDelegate(this)
}
