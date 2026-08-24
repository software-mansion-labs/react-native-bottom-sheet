package com.swmansion.reactnativebottomsheet

import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RequestCloseTest {
  @Test
  fun `resolver separates request consume and pass-through`() {
    assertEquals(
      RequestCloseInputAction.REQUEST_CLOSE,
      resolveRequestCloseInputAction(inputState()),
    )
    assertEquals(
      RequestCloseInputAction.CONSUME,
      resolveRequestCloseInputAction(inputState(isTargetOpen = false)),
    )

    listOf(
        inputState(hasHandler = false),
        inputState(isPresentationActive = false, isTargetOpen = false),
        inputState(isActive = false),
        inputState(isAttached = false),
        inputState(isModal = false),
      )
      .forEach { state ->
        assertEquals(
          RequestCloseInputAction.PASS_THROUGH,
          resolveRequestCloseInputAction(state),
        )
      }
  }

  @Test
  fun `predictive request can only degrade within one Back sequence`() {
    var action = RequestCloseInputAction.REQUEST_CLOSE
    val executed = mutableListOf<RequestCloseInputAction>()
    val callback =
      RequestCloseBackCallback(
        resolveAction = { action },
        executeAction = executed::add,
      )
    callback.updateState(canReceiveBack = true, currentAction = action)

    callback.handleOnBackStarted(backEvent())
    action = RequestCloseInputAction.CONSUME
    callback.updateState(canReceiveBack = true, currentAction = action)
    action = RequestCloseInputAction.REQUEST_CLOSE
    callback.updateState(canReceiveBack = true, currentAction = action)
    callback.handleOnBackPressed()

    assertEquals(listOf(RequestCloseInputAction.CONSUME), executed)
    assertFalse(callback.isPredictiveBackInProgress)

    callback.handleOnBackPressed()
    assertEquals(
      listOf(RequestCloseInputAction.CONSUME, RequestCloseInputAction.REQUEST_CLOSE),
      executed,
    )
  }

  @Test
  fun `disposed predictive callback ignores late cancellation and commit`() {
    val executed = mutableListOf<RequestCloseInputAction>()
    var predictiveStateChanges = 0
    val callback =
      RequestCloseBackCallback(
        resolveAction = { RequestCloseInputAction.REQUEST_CLOSE },
        executeAction = executed::add,
        onPredictiveBackStateChanged = { predictiveStateChanges++ },
      )
    callback.updateState(
      canReceiveBack = true,
      currentAction = RequestCloseInputAction.REQUEST_CLOSE,
    )
    callback.handleOnBackStarted(backEvent())
    callback.dispose()
    val stateChangesBeforeLateEvents = predictiveStateChanges

    callback.handleOnBackCancelled()
    callback.handleOnBackPressed()

    assertTrue(executed.isEmpty())
    assertFalse(callback.isPredictiveBackInProgress)
    assertEquals(stateChangesBeforeLateEvents, predictiveStateChanges)
  }

  @Test
  fun `Escape uses shared consume and pass-through actions`() {
    val dispatcher = EscapeRequestCloseDispatcher()
    var emissionCount = 0

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(10L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = {
          emissionCount++
          true
        },
      )
    )
    dispatcher.degradeCapturedRequestClose()
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(10L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(0, emissionCount)

    assertFalse(
      dispatcher.dispatch(
        event = escapeDown(20L),
        resolveInitialAction = { RequestCloseInputAction.PASS_THROUGH },
        emitRequestCloseIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertFalse(
      dispatcher.dispatch(
        event = escapeUp(20L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(0, emissionCount)
  }

  private fun inputState(
    isAttached: Boolean = true,
    isActive: Boolean = true,
    isModal: Boolean = true,
    hasHandler: Boolean = true,
    isPresentationActive: Boolean = true,
    isTargetOpen: Boolean = true,
  ) =
    RequestCloseInputState(
      isAttached = isAttached,
      isActive = isActive,
      isModal = isModal,
      hasHandler = hasHandler,
      isPresentationActive = isPresentationActive,
      isTargetOpen = isTargetOpen,
    )

  private fun backEvent() = BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)

  private fun escapeDown(downTime: Long) =
    KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)

  private fun escapeUp(downTime: Long) =
    KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)
}
