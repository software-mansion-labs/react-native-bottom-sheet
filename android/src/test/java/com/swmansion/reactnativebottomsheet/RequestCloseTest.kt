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
  fun `predictive Back cancellation clears its pinned action without executing it`() {
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
    callback.updateState(
      canReceiveBack = false,
      currentAction = RequestCloseInputAction.REQUEST_CLOSE,
    )
    callback.handleOnBackCancelled()

    assertTrue(executed.isEmpty())
    assertFalse(callback.isPredictiveBackInProgress)
    assertFalse(callback.isEnabled)
    assertEquals(2, predictiveStateChanges)
  }

  @Test
  fun `predictive pass-through stays pinned until commit before later Back can request close`() {
    var action = RequestCloseInputAction.PASS_THROUGH
    val executed = mutableListOf<RequestCloseInputAction>()
    val callback =
      RequestCloseBackCallback(
        resolveAction = { action },
        executeAction = executed::add,
      )
    callback.updateState(canReceiveBack = true, currentAction = action)

    callback.handleOnBackStarted(backEvent())
    action = RequestCloseInputAction.REQUEST_CLOSE
    callback.updateState(canReceiveBack = true, currentAction = action)
    callback.handleOnBackPressed()

    assertEquals(listOf(RequestCloseInputAction.PASS_THROUGH), executed)
    assertFalse(callback.isPredictiveBackInProgress)

    callback.handleOnBackPressed()
    assertEquals(
      listOf(RequestCloseInputAction.PASS_THROUGH, RequestCloseInputAction.REQUEST_CLOSE),
      executed,
    )
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

  @Test
  fun `Escape emits only on terminal key-up`() {
    val dispatcher = EscapeRequestCloseDispatcher()
    var emissionCount = 0

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(200L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(0, emissionCount)
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(200L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(1, emissionCount)
  }

  @Test
  fun `Escape rejects modifiers and repeats without duplicate emission`() {
    val dispatcher = EscapeRequestCloseDispatcher()
    var emissionCount = 0
    val emitRequestClose = {
      emissionCount++
      true
    }

    assertFalse(
      dispatcher.dispatch(
        event = escapeDown(210L, metaState = KeyEvent.META_SHIFT_ON),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertFalse(
      dispatcher.dispatch(
        event = escapeUp(210L, metaState = KeyEvent.META_SHIFT_ON),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(220L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(220L, repeatCount = 1),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(0, emissionCount)
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(220L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(1, emissionCount)

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(230L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(230L, metaState = KeyEvent.META_SHIFT_ON),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(1, emissionCount)
  }

  @Test
  fun `new Escape down replaces an orphaned captured press`() {
    val dispatcher = EscapeRequestCloseDispatcher()
    var emissionCount = 0
    val emitRequestClose = {
      emissionCount++
      true
    }

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(240L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(241L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(241L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(1, emissionCount)

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(242L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(242L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(2, emissionCount)
  }

  @Test
  fun `Escape key-up without a captured down is unhandled`() {
    val dispatcher = EscapeRequestCloseDispatcher()
    var emissionCount = 0
    val emitRequestClose = {
      emissionCount++
      true
    }

    assertFalse(
      dispatcher.dispatch(
        event = escapeUp(250L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(0, emissionCount)
    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(251L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(251L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(1, emissionCount)
  }

  @Test
  fun `cancelled Escape key-up consumes and clears the captured press without emitting`() {
    val dispatcher = EscapeRequestCloseDispatcher()
    var emissionCount = 0
    val emitRequestClose = {
      emissionCount++
      true
    }

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(260L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(dispatcher.hasCapturedPress)
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(260L, flags = KeyEvent.FLAG_CANCELED),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(0, emissionCount)
    assertFalse(dispatcher.hasCapturedPress)

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(261L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(261L),
        resolveInitialAction = { RequestCloseInputAction.REQUEST_CLOSE },
        emitRequestCloseIfEligible = emitRequestClose,
      )
    )
    assertEquals(1, emissionCount)
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

  private fun escapeDown(
    downTime: Long,
    repeatCount: Int = 0,
    metaState: Int = 0,
  ) =
    if (repeatCount == 0 && metaState == 0) {
      KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0)
    } else {
      KeyEvent(
        downTime,
        downTime + repeatCount,
        KeyEvent.ACTION_DOWN,
        KeyEvent.KEYCODE_ESCAPE,
        repeatCount,
        metaState,
        -1,
        0,
        0,
        0,
      )
    }

  private fun escapeUp(
    downTime: Long,
    metaState: Int = 0,
    flags: Int = 0,
  ) =
    if (metaState == 0 && flags == 0) {
      KeyEvent(downTime, downTime + 1, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0)
    } else {
      KeyEvent(
        downTime,
        downTime + 2,
        KeyEvent.ACTION_UP,
        KeyEvent.KEYCODE_ESCAPE,
        0,
        metaState,
        -1,
        0,
        flags,
        0,
      )
    }
}
