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

internal data class RequestCloseKeyPressToken(
  val downTime: Long,
  val deviceId: Int,
  val source: Int,
  val keyCode: Int,
  val scanCode: Int,
)

internal data class RequestCloseKeyIdentity(
  val deviceId: Int,
  val source: Int,
  val keyCode: Int,
  val scanCode: Int,
)

/**
 * Owns an eligible Escape press from its initial down through its terminal up. A new initial down
 * supersedes an unfinished sequence with the same stable key identity, since Android may omit the
 * preceding up, while sequences from other input identities remain independent. Superseded
 * sequences become short-lived tombstones so their late events cannot finish the replacement:
 * formerly captured events remain consumed, while unclaimed events remain unhandled. Exact press
 * tokens take precedence over the stable-identity fallback used when Android's instrumentation
 * rewrites [KeyEvent.getDownTime] between an otherwise matching down and up.
 */
internal class EscapeRequestCloseDispatcher {
  private data class StalePress(
    val keyIdentity: Any,
    var isComplete: Boolean = false,
    val consumeEvents: Boolean = false,
  )

  private var capturedPress: Any? = null
  private var capturedKeyIdentity: Any? = null
  private val stalePresses = mutableMapOf<Any, StalePress>()

  val hasCapturedPress: Boolean
    get() = capturedPress != null

  fun dispatch(
    pressToken: Any,
    keyIdentity: Any,
    keyCode: Int,
    action: Int,
    repeatCount: Int,
    hasModifiers: Boolean,
    isCanceled: Boolean,
    shouldCapturePress: () -> Boolean,
    emitRequestCloseIfEligible: () -> Boolean,
  ): Boolean {
    if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false

    prepareForInitialDown(pressToken, keyIdentity, action, repeatCount)

    val exactStalePress = stalePresses[pressToken]
    if (exactStalePress != null) {
      if (action == KeyEvent.ACTION_UP) {
        exactStalePress.isComplete = true
      }
      return exactStalePress.consumeEvents
    }

    val matchingStalePress =
      if (action == KeyEvent.ACTION_UP) {
        stalePresses.values.firstOrNull {
          !it.isComplete && it.keyIdentity == keyIdentity
        }
      } else {
        null
      }
    if (matchingStalePress != null) {
      matchingStalePress.isComplete = true
      return matchingStalePress.consumeEvents
    }

    val currentPress = capturedPress
    if (currentPress != null) {
      val matchesCapturedPress =
        pressToken == currentPress ||
          (action == KeyEvent.ACTION_UP && keyIdentity == capturedKeyIdentity)
      if (!matchesCapturedPress) {
        rememberStaleInitialDown(pressToken, keyIdentity, action, repeatCount)
        return false
      }

      return when (action) {
        KeyEvent.ACTION_DOWN -> true
        KeyEvent.ACTION_UP -> {
          capturedPress = null
          capturedKeyIdentity = null
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

    if (hasModifiers || !shouldCapturePress()) {
      stalePresses[pressToken] = StalePress(keyIdentity)
      return false
    }

    capturedPress = pressToken
    capturedKeyIdentity = keyIdentity
    return true
  }

  private fun prepareForInitialDown(
    pressToken: Any,
    keyIdentity: Any,
    action: Int,
    repeatCount: Int,
  ) {
    if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) return

    stalePresses.entries.removeAll { it.value.isComplete }

    val supersededPress = capturedPress
    if (
      supersededPress != null && supersededPress != pressToken && capturedKeyIdentity == keyIdentity
    ) {
      stalePresses[supersededPress] =
        StalePress(
          keyIdentity = keyIdentity,
          isComplete = true,
          consumeEvents = true,
        )
      capturedPress = null
      capturedKeyIdentity = null
    }

    stalePresses.forEach { (staleToken, stalePress) ->
      if (staleToken != pressToken && stalePress.keyIdentity == keyIdentity) {
        stalePress.isComplete = true
      }
    }
  }

  private fun rememberStaleInitialDown(
    pressToken: Any,
    keyIdentity: Any,
    action: Int,
    repeatCount: Int,
  ) {
    if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
      stalePresses[pressToken] = StalePress(keyIdentity)
    }
  }

  fun clear() {
    capturedPress = null
    capturedKeyIdentity = null
    stalePresses.clear()
  }
}
