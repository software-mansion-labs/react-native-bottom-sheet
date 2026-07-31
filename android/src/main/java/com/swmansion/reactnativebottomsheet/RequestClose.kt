package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent

internal data class RequestCloseEligibility(
  val isAttached: Boolean,
  val isActive: Boolean,
  val isModal: Boolean,
  val isEnabled: Boolean,
  val isTargetOpen: Boolean,
)

internal fun isRequestCloseEligible(state: RequestCloseEligibility): Boolean =
  state.isAttached && state.isActive && state.isModal && state.isEnabled && state.isTargetOpen

internal data class RequestCloseKeyEventToken(
  val downTime: Long,
  val eventTime: Long,
  val deviceId: Int,
  val source: Int,
  val keyCode: Int,
  val scanCode: Int,
  val action: Int,
  val repeatCount: Int,
  val metaState: Int,
  val flags: Int,
)

internal data class RequestCloseKeyPressToken(
  val downTime: Long,
  val deviceId: Int,
  val source: Int,
  val keyCode: Int,
  val scanCode: Int,
)

/**
 * Owns an eligible Escape press from its initial down through its terminal up. Event tokens
 * deduplicate the hierarchy and window-callback paths if the same [KeyEvent] reaches both.
 */
internal class EscapeRequestCloseDispatcher {
  private var consumedEvent: Any? = null
  private var capturedPress: Any? = null
  private val unclaimedPresses = mutableSetOf<Any>()

  val hasCapturedPress: Boolean
    get() = capturedPress != null

  fun dispatch(
    eventToken: Any,
    pressToken: Any,
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    hasModifiers: Boolean,
    isCanceled: Boolean,
    isRequestCloseEligible: () -> Boolean,
    emitRequestCloseIfEligible: () -> Boolean,
  ): Boolean {
    if (eventToken == consumedEvent) return true
    if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false

    if (pressToken in unclaimedPresses) {
      if (action == KeyEvent.ACTION_UP) {
        unclaimedPresses.remove(pressToken)
      }
      return false
    }

    val currentPress = capturedPress
    if (currentPress != null) {
      if (pressToken != currentPress) {
        rememberUnclaimedInitialDown(pressToken, action, repeatCount)
        return false
      }

      return when (action) {
        KeyEvent.ACTION_DOWN -> consume(eventToken)
        KeyEvent.ACTION_UP -> {
          capturedPress = null
          consume(eventToken)
          if (!isCanceled && !hasModifiers) {
            emitRequestCloseIfEligible()
          }
          true
        }
        else -> false
      }
    }

    if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) {
      return false
    }

    if (hasModifiers || !isRequestCloseEligible()) {
      unclaimedPresses += pressToken
      return false
    }

    capturedPress = pressToken
    return consume(eventToken)
  }

  private fun consume(eventToken: Any): Boolean {
    consumedEvent = eventToken
    return true
  }

  private fun rememberUnclaimedInitialDown(
    pressToken: Any,
    action: Int,
    repeatCount: Int,
  ) {
    if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
      unclaimedPresses += pressToken
    }
  }

  fun clear() {
    consumedEvent = null
    capturedPress = null
    unclaimedPresses.clear()
  }
}

internal class RequestCloseKeyEventListenerStack<Event> {
  private val listeners = mutableListOf<(Event) -> Boolean>()

  val isEmpty: Boolean
    get() = listeners.isEmpty()

  fun add(listener: (Event) -> Boolean) {
    listeners += listener
  }

  fun remove(listener: (Event) -> Boolean) {
    listeners.remove(listener)
  }

  fun dispatch(
    event: Event,
    delegate: (Event) -> Boolean,
  ): Boolean {
    for (index in listeners.lastIndex downTo 0) {
      if (listeners[index](event)) return true
    }
    return delegate(event)
  }
}
