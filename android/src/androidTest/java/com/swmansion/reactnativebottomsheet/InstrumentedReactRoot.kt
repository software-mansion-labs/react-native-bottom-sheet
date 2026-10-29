package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.facebook.react.uimanager.RootView

internal class InstrumentedReactRoot(context: Context) : FrameLayout(context), RootView {
  override fun onChildStartedNativeGesture(childView: View?, ev: MotionEvent) = Unit

  override fun onChildEndedNativeGesture(childView: View, ev: MotionEvent) = Unit

  override fun handleException(t: Throwable) = throw t
}

internal fun Activity.setInstrumentedReactContentView(content: View) {
  setContentView(
    InstrumentedReactRoot(this).apply {
      addView(
        content,
        ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT,
        ),
      )
    }
  )
}
