package com.swmansion.reactnativebottomsheet.closerequest

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
class CloseRequestInputTest {
  @Test
  fun `resolver separates request consume and pass-through`() {
    assertEquals(
      CloseRequestInputAction.EMIT_CLOSE_REQUEST,
      resolveCloseRequestInputAction(inputState()),
    )
    assertEquals(
      CloseRequestInputAction.CONSUME,
      resolveCloseRequestInputAction(inputState(isTargetResolvedAndOpen = false)),
    )

    listOf(
        inputState(hasCloseRequestHandler = false),
        inputState(isPresentationActive = false, isTargetResolvedAndOpen = false),
        inputState(isLifecycleActive = false),
        inputState(isAttached = false),
        inputState(isModal = false, hasCloseRequestHandler = true),
      )
      .forEach { state ->
        assertEquals(
          CloseRequestInputAction.PASS_THROUGH,
          resolveCloseRequestInputAction(state),
        )
      }
  }

  @Test
  fun `predictive request can only degrade within one Back sequence`() {
    var action = CloseRequestInputAction.EMIT_CLOSE_REQUEST
    val executed = mutableListOf<CloseRequestInputAction>()
    val callback =
      CloseRequestBackCallback(
        resolveAction = { action },
        executeAction = executed::add,
      )
    callback.updateState(canReceiveBack = true, currentAction = action)

    callback.handleOnBackStarted(backEvent())
    action = CloseRequestInputAction.CONSUME
    callback.updateState(canReceiveBack = true, currentAction = action)
    action = CloseRequestInputAction.EMIT_CLOSE_REQUEST
    callback.updateState(canReceiveBack = true, currentAction = action)
    callback.handleOnBackPressed()

    assertEquals(listOf(CloseRequestInputAction.CONSUME), executed)
    assertFalse(callback.isPredictiveBackInProgress)

    callback.handleOnBackPressed()
    assertEquals(
      listOf(CloseRequestInputAction.CONSUME, CloseRequestInputAction.EMIT_CLOSE_REQUEST),
      executed,
    )
  }

  @Test
  fun `disposed predictive callback ignores late cancellation and commit`() {
    val executed = mutableListOf<CloseRequestInputAction>()
    var predictiveStateChanges = 0
    val callback =
      CloseRequestBackCallback(
        resolveAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        executeAction = executed::add,
        onPredictiveBackStateChanged = { predictiveStateChanges++ },
      )
    callback.updateState(
      canReceiveBack = true,
      currentAction = CloseRequestInputAction.EMIT_CLOSE_REQUEST,
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
    val executed = mutableListOf<CloseRequestInputAction>()
    var predictiveStateChanges = 0
    val callback =
      CloseRequestBackCallback(
        resolveAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        executeAction = executed::add,
        onPredictiveBackStateChanged = { predictiveStateChanges++ },
      )
    callback.updateState(
      canReceiveBack = true,
      currentAction = CloseRequestInputAction.EMIT_CLOSE_REQUEST,
    )

    callback.handleOnBackStarted(backEvent())
    callback.updateState(
      canReceiveBack = false,
      currentAction = CloseRequestInputAction.EMIT_CLOSE_REQUEST,
    )
    callback.handleOnBackCancelled()

    assertTrue(executed.isEmpty())
    assertFalse(callback.isPredictiveBackInProgress)
    assertFalse(callback.isEnabled)
    assertEquals(2, predictiveStateChanges)
  }

  @Test
  fun `predictive pass-through stays pinned until commit before later Back can request close`() {
    var action = CloseRequestInputAction.PASS_THROUGH
    val executed = mutableListOf<CloseRequestInputAction>()
    val callback =
      CloseRequestBackCallback(
        resolveAction = { action },
        executeAction = executed::add,
      )
    callback.updateState(canReceiveBack = true, currentAction = action)

    callback.handleOnBackStarted(backEvent())
    action = CloseRequestInputAction.EMIT_CLOSE_REQUEST
    callback.updateState(canReceiveBack = true, currentAction = action)
    callback.handleOnBackPressed()

    assertEquals(listOf(CloseRequestInputAction.PASS_THROUGH), executed)
    assertFalse(callback.isPredictiveBackInProgress)

    callback.handleOnBackPressed()
    assertEquals(
      listOf(CloseRequestInputAction.PASS_THROUGH, CloseRequestInputAction.EMIT_CLOSE_REQUEST),
      executed,
    )
  }

  @Test
  fun `Escape uses shared consume and pass-through actions`() {
    val dispatcher = EscapeCloseRequestDispatcher()
    var emissionCount = 0

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(10L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = {
          emissionCount++
          true
        },
      )
    )
    dispatcher.degradeCapturedCloseRequest()
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(10L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(0, emissionCount)

    assertFalse(
      dispatcher.dispatch(
        event = escapeDown(20L),
        resolveInitialAction = { CloseRequestInputAction.PASS_THROUGH },
        emitCloseRequestIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertFalse(
      dispatcher.dispatch(
        event = escapeUp(20L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(0, emissionCount)
  }

  @Test
  fun `Escape emits only on terminal key-up`() {
    val dispatcher = EscapeCloseRequestDispatcher()
    var emissionCount = 0

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(200L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(0, emissionCount)
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(200L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = {
          emissionCount++
          true
        },
      )
    )
    assertEquals(1, emissionCount)
  }

  @Test
  fun `Escape rejects modifiers and repeats without duplicate emission`() {
    val dispatcher = EscapeCloseRequestDispatcher()
    var emissionCount = 0
    val emitCloseRequest = {
      emissionCount++
      true
    }

    assertFalse(
      dispatcher.dispatch(
        event = escapeDown(210L, metaState = KeyEvent.META_SHIFT_ON),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertFalse(
      dispatcher.dispatch(
        event = escapeUp(210L, metaState = KeyEvent.META_SHIFT_ON),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(220L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(220L, repeatCount = 1),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(0, emissionCount)
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(220L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(1, emissionCount)

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(230L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(230L, metaState = KeyEvent.META_SHIFT_ON),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(1, emissionCount)
  }

  @Test
  fun `new Escape down replaces an orphaned captured press`() {
    val dispatcher = EscapeCloseRequestDispatcher()
    var emissionCount = 0
    val emitCloseRequest = {
      emissionCount++
      true
    }

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(240L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(241L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(241L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(1, emissionCount)

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(242L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(242L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(2, emissionCount)
  }

  @Test
  fun `Escape key-up without a captured down is unhandled`() {
    val dispatcher = EscapeCloseRequestDispatcher()
    var emissionCount = 0
    val emitCloseRequest = {
      emissionCount++
      true
    }

    assertFalse(
      dispatcher.dispatch(
        event = escapeUp(250L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(0, emissionCount)
    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(251L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(251L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(1, emissionCount)
  }

  @Test
  fun `cancelled Escape key-up consumes and clears the captured press without emitting`() {
    val dispatcher = EscapeCloseRequestDispatcher()
    var emissionCount = 0
    val emitCloseRequest = {
      emissionCount++
      true
    }

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(260L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(dispatcher.hasCapturedPress)
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(260L, flags = KeyEvent.FLAG_CANCELED),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(0, emissionCount)
    assertFalse(dispatcher.hasCapturedPress)

    assertTrue(
      dispatcher.dispatch(
        event = escapeDown(261L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertTrue(
      dispatcher.dispatch(
        event = escapeUp(261L),
        resolveInitialAction = { CloseRequestInputAction.EMIT_CLOSE_REQUEST },
        emitCloseRequestIfEligible = emitCloseRequest,
      )
    )
    assertEquals(1, emissionCount)
  }

  private fun inputState(
    isAttached: Boolean = true,
    isLifecycleActive: Boolean = true,
    isModal: Boolean = true,
    hasCloseRequestHandler: Boolean = true,
    isPresentationActive: Boolean = true,
    isTargetResolvedAndOpen: Boolean = true,
  ) =
    CloseRequestInputState(
      isAttached = isAttached,
      isLifecycleActive = isLifecycleActive,
      isModal = isModal,
      hasCloseRequestHandler = hasCloseRequestHandler,
      isPresentationActive = isPresentationActive,
      isTargetResolvedAndOpen = isTargetResolvedAndOpen,
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
