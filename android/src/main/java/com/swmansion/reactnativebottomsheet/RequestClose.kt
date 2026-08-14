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
 * Owns an eligible Escape press from its initial down through its terminal up. Initial downs use
 * the full press token to keep competing sequences distinct. A terminal up can fall back to the
 * stable key identity because Android's instrumentation may rewrite [KeyEvent.getDownTime] between
 * the two events. Completed unclaimed presses remain as short-lived tombstones so the same up is
 * not captured when it reaches both the local hierarchy and the unhandled-key fallback.
 */
internal class EscapeRequestCloseDispatcher {
  private data class UnclaimedPress(
    val keyIdentity: Any,
    var isComplete: Boolean = false,
  )

  private var capturedPress: Any? = null
  private var capturedKeyIdentity: Any? = null
  private val unclaimedPresses = mutableMapOf<Any, UnclaimedPress>()

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

    if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
      unclaimedPresses.entries.removeAll { it.value.isComplete }
    }

    val unclaimedPress =
      unclaimedPresses.keys.firstOrNull { it == pressToken }
        ?: if (action == KeyEvent.ACTION_UP) {
          unclaimedPresses.entries
            .firstOrNull { !it.value.isComplete && it.value.keyIdentity == keyIdentity }
            ?.key
        } else {
          null
        }
    if (unclaimedPress != null) {
      if (action == KeyEvent.ACTION_UP) {
        unclaimedPresses[unclaimedPress]?.isComplete = true
      }
      return false
    }

    val currentPress = capturedPress
    if (currentPress != null) {
      val matchesCapturedPress =
        pressToken == currentPress ||
          (action == KeyEvent.ACTION_UP && keyIdentity == capturedKeyIdentity)
      if (!matchesCapturedPress) {
        rememberUnclaimedInitialDown(pressToken, keyIdentity, action, repeatCount)
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
      unclaimedPresses[pressToken] = UnclaimedPress(keyIdentity)
      return false
    }

    capturedPress = pressToken
    capturedKeyIdentity = keyIdentity
    return true
  }

  private fun rememberUnclaimedInitialDown(
    pressToken: Any,
    keyIdentity: Any,
    action: Int,
    repeatCount: Int,
  ) {
    if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
      unclaimedPresses[pressToken] = UnclaimedPress(keyIdentity)
    }
  }

  fun clear() {
    capturedPress = null
    capturedKeyIdentity = null
    unclaimedPresses.clear()
  }
}
