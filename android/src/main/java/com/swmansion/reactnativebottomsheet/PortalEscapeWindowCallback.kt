package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import android.view.Window
import java.util.WeakHashMap

/**
 * Installs one callback wrapper per Activity window and keeps portal listeners in registration
 * order. The most recently registered eligible portal receives Escape first.
 */
internal object PortalEscapeWindowCallbackRegistry {
  private class Entry(
    val listeners: RequestCloseKeyEventListenerStack<KeyEvent>,
    val wrapper: PortalEscapeWindowCallback,
  )

  private val entries = WeakHashMap<Window, Entry>()

  fun register(
    window: Window,
    listener: (KeyEvent) -> Boolean,
  ): PortalEscapeWindowCallbackRegistration? {
    val currentCallback = window.callback ?: return null
    val entry = entries[window] ?: createEntry(window, currentCallback)
    entry.listeners.add(listener)
    return PortalEscapeWindowCallbackRegistration(window, listener)
  }

  fun unregister(
    window: Window,
    listener: (KeyEvent) -> Boolean,
  ) {
    val entry = entries[window] ?: return
    entry.listeners.remove(listener)
    if (!entry.listeners.isEmpty) return

    entries.remove(window)
    // A later integration may have replaced or wrapped our callback. Do not discard it while
    // removing the last bottom-sheet listener.
    if (window.callback === entry.wrapper) {
      window.callback = entry.wrapper.delegate
    }
  }

  private fun createEntry(
    window: Window,
    currentCallback: Window.Callback,
  ): Entry {
    val listeners = RequestCloseKeyEventListenerStack<KeyEvent>()
    return Entry(
        listeners = listeners,
        wrapper = PortalEscapeWindowCallback(currentCallback, listeners),
      )
      .also {
        entries[window] = it
        window.callback = it.wrapper
      }
  }
}

internal class PortalEscapeWindowCallbackRegistration(
  val window: Window,
  private val listener: (KeyEvent) -> Boolean,
) {
  private var removed = false

  fun remove() {
    if (removed) return
    removed = true
    PortalEscapeWindowCallbackRegistry.unregister(window, listener)
  }
}

private class PortalEscapeWindowCallback(
  val delegate: Window.Callback,
  private val listeners: RequestCloseKeyEventListenerStack<KeyEvent>,
) : Window.Callback by delegate {
  override fun dispatchKeyEvent(event: KeyEvent): Boolean =
    listeners.dispatch(event, delegate::dispatchKeyEvent)
}
