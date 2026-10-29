package com.swmansion.reactnativebottomsheet.closerequest

import android.os.Build
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.Window
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Routes legacy `KEYCODE_BACK` presses to the portal's system input before the host entry point.
 *
 * Without `OnBackInvokedCallback` routing (Android 15 and below, or a target SDK below 36),
 * `ReactActivity.onBackPressed` hands Back to JS `BackHandler` first and reaches the dispatcher
 * only through `invokeDefaultOnBackPressed`, which hosts may override without calling `super` (Expo
 * moves the task to the back on API 30 and below). Capturing the key sequence at the window
 * callback gives the portal the precedence it has under `OnBackInvokedCallback` routing, where
 * `KEYCODE_BACK` never reaches the window callback. Both paths use [PortalBackInput], keeping local
 * owner capture and IME precedence separate from React Native's asynchronous default Back path.
 *
 * Other libraries also wrap window callbacks, so the wrapper is installed once per window and never
 * removed. Without an enabled registration, it passes every event through.
 */
internal object LegacyBackKeyRouting {
  internal interface Registration {
    fun remove()
  }

  private class Entry(val input: PortalBackInput) {
    var isRegistered = true
  }

  private class WindowState {
    val entries = mutableListOf<Entry>()
    var capturedEntry: Entry? = null

    /**
     * Tracks the current Back sequence from its initial down through its terminal up. A press
     * captured for an enabled registration stays consumed even when that registration is removed or
     * disabled before the up, which then only finishes the sequence.
     */
    fun dispatch(event: KeyEvent): Boolean {
      if (event.keyCode != KeyEvent.KEYCODE_BACK) return false

      return when (event.action) {
        KeyEvent.ACTION_DOWN -> {
          if (event.repeatCount == 0) {
            capturedEntry =
              entries.toList().asReversed().firstOrNull {
                it.isRegistered && it.input.dispatchKey(event)
              }
          } else {
            capturedEntry?.takeIf { it.isRegistered }?.input?.dispatchKey(event)
          }
          capturedEntry != null
        }
        KeyEvent.ACTION_UP -> {
          val completedEntry = capturedEntry ?: return false
          capturedEntry = null
          if (completedEntry.isRegistered) completedEntry.input.dispatchKey(event)
          true
        }
        else -> capturedEntry != null
      }
    }
  }

  private class RoutingWindowCallback(
    private val state: WindowState,
    private val delegate: Window.Callback,
  ) : Window.Callback by delegate {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
      state.dispatch(event) || delegate.dispatchKeyEvent(event)

    // Delegation does not forward Java default methods.
    override fun onProvideKeyboardShortcuts(
      data: MutableList<KeyboardShortcutGroup>?,
      menu: Menu?,
      deviceId: Int,
    ) = delegate.onProvideKeyboardShortcuts(data, menu, deviceId)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPointerCaptureChanged(hasCapture: Boolean) =
      delegate.onPointerCaptureChanged(hasCapture)
  }

  private class RegistrationImpl(
    private val entry: Entry,
    state: WindowState,
  ) : Registration {
    private val stateReference = WeakReference(state)

    override fun remove() {
      if (!entry.isRegistered) return
      entry.isRegistered = false
      stateReference.get()?.entries?.remove(entry)
    }
  }

  // The window's callback chain keeps its state alive; entries reference the Activity, so the map
  // must not.
  private val statesByWindow = WeakHashMap<Window, WeakReference<WindowState>>()

  fun register(
    window: Window,
    input: PortalBackInput,
  ): Registration {
    val state = statesByWindow[window]?.get() ?: installState(window)
    val entry = Entry(input)
    state.entries.add(entry)
    return RegistrationImpl(entry, state)
  }

  private fun installState(window: Window): WindowState {
    val state = WindowState()
    val currentCallback = window.callback
    if (currentCallback != null) {
      window.callback = RoutingWindowCallback(state, currentCallback)
      statesByWindow[window] = WeakReference(state)
    }
    return state
  }
}
