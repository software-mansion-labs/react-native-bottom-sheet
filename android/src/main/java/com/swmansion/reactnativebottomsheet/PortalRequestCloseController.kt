package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Owns the portal input routing for close requests for one [BottomSheetView]. */
internal class PortalRequestCloseController(
  private val view: View,
  private val currentActivity: () -> Activity?,
  private val emitRequestClose: () -> Boolean,
) : PortalRequestCloseParticipant {
  private var inputState = INACTIVE_INPUT_STATE
  private var usesPortalPresentation = false
  private var disposed = false

  private var requestCloseRoutingContext: PortalRequestCloseRoutingContext? = null
  private var requestCloseRegistration: PortalRequestCloseCoordinator.Registration? = null
  private var assignedRequestCloseAction = RequestCloseInputAction.PASS_THROUGH
  private var hasBackCallbackPresentationEnded = false
  private var isReconcilingBackCallback = false
  private var isEscapeListenerInstalled = false

  private var backDispatcher: OnBackPressedDispatcher? = null

  private var backCallback: RequestCloseBackCallback? = null

  private val lifecycleObserver: LifecycleEventObserver =
    LifecycleEventObserver { lifecycleOwner, event ->
      if (disposed || lifecycleOwner !== requestCloseRoutingContext?.lifecycleOwner) {
        return@LifecycleEventObserver
      }
      if (event == Lifecycle.Event.ON_DESTROY) {
        lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
      }
      reconcileInputHandling()
    }

  private val unhandledKeyEventListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
    dispatchEscape(event)
  }

  private val syncRoutingContextRunnable = Runnable { syncRoutingContext() }

  /** Updates the immutable input snapshot and reconciles the portal routing context. */
  fun update(
    state: RequestCloseInputState,
    usesPortalPresentation: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.usesPortalPresentation = usesPortalPresentation
    syncRoutingContext()
  }

  /** Re-resolves the routing context after Android has completed an attachment/layout turn. */
  fun scheduleRoutingContextSync() {
    if (disposed) return
    view.removeCallbacks(syncRoutingContextRunnable)
    view.post(syncRoutingContextRunnable)
  }

  fun dispatchEscape(event: KeyEvent): Boolean {
    if (disposed || !usesPortalPresentation || !inputState.isModal || !inputState.isAttached)
      return false
    val portalRoot = requestCloseRoutingContext?.rootView ?: return false
    if (portalRoot !== view.rootView) return false
    return PortalRequestCloseCoordinator.dispatchEscape(portalRoot, event)
  }

  /** Removes every routing-context effect. A later [update] may resolve a new context. */
  fun clear() {
    usesPortalPresentation = false
    view.removeCallbacks(syncRoutingContextRunnable)
    clearRoutingContext()
  }

  /** Terminal, idempotent cleanup. */
  fun dispose() {
    if (disposed) return
    disposed = true
    usesPortalPresentation = false
    view.removeCallbacks(syncRoutingContextRunnable)
    clearRoutingContext()
  }

  override fun onAssignedActionChanged(action: RequestCloseInputAction) {
    if (disposed) return
    assignedRequestCloseAction = action
    reconcileBackCallback()
  }

  override fun emitRequestCloseIfEligible(): Boolean {
    if (
      disposed ||
        assignedRequestCloseAction != RequestCloseInputAction.REQUEST_CLOSE ||
        currentPortalState().actionIfRoutingOwner != RequestCloseInputAction.REQUEST_CLOSE
    ) {
      return false
    }
    return emitRequestClose()
  }

  private fun syncRoutingContext() {
    if (disposed) return
    val resolvedRoutingContext =
      view
        .takeIf { usesPortalPresentation && inputState.isAttached && inputState.isModal }
        ?.resolvePortalRequestCloseRoutingContext(currentActivity())
    val previousRoutingContext = requestCloseRoutingContext
    if (!routingContextsAreIdentical(resolvedRoutingContext, previousRoutingContext)) {
      removeInputHandlers()
      previousRoutingContext?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      requestCloseRoutingContext = resolvedRoutingContext
      resolvedRoutingContext?.lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)

      if (previousRoutingContext?.rootView !== resolvedRoutingContext?.rootView) {
        requestCloseRegistration?.remove()
        requestCloseRegistration = null
        if (resolvedRoutingContext != null) {
          requestCloseRegistration =
            PortalRequestCloseCoordinator.register(
              resolvedRoutingContext.rootView,
              this,
              currentPortalState(),
            )
        }
      } else if (resolvedRoutingContext != null && requestCloseRegistration == null) {
        requestCloseRegistration =
          PortalRequestCloseCoordinator.register(
            resolvedRoutingContext.rootView,
            this,
            currentPortalState(),
          )
      }
    }
    reconcileInputHandling()
  }

  private fun reconcileInputHandling() {
    if (disposed) return
    ensureEscapeListener()
    requestCloseRegistration?.update(currentPortalState())
    reconcileBackCallback()
  }

  private fun currentPortalState(): PortalRequestCloseState {
    val currentRoutingContext = requestCloseRoutingContext
    val isLifecycleActive =
      currentRoutingContext
        ?.lifecycleOwner
        ?.lifecycle
        ?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) ?: inputState.isLifecycleActive
    val effectiveInputState =
      inputState.copy(
        isAttached =
          usesPortalPresentation &&
            inputState.isAttached &&
            currentRoutingContext != null &&
            currentRoutingContext.rootView === view.rootView,
        isLifecycleActive = isLifecycleActive,
      )
    val isRoutingOwnerCandidate =
      effectiveInputState.isAttached &&
        effectiveInputState.isModal &&
        effectiveInputState.isLifecycleActive &&
        effectiveInputState.isPresentationActive

    return PortalRequestCloseState(
      isRoutingOwnerCandidate = isRoutingOwnerCandidate,
      actionIfRoutingOwner =
        if (isRoutingOwnerCandidate) {
          resolveRequestCloseInputAction(effectiveInputState)
        } else {
          RequestCloseInputAction.PASS_THROUGH
        },
    )
  }

  /**
   * Starts Escape registration at the first presentation with a handler, then keeps the listener
   * stable for the structural lifetime of the resolved routing context.
   */
  private fun ensureEscapeListener() {
    val currentRoutingContext = requestCloseRoutingContext
    if (
      currentRoutingContext == null ||
        !usesPortalPresentation ||
        !inputState.isAttached ||
        currentRoutingContext.lifecycleOwner?.lifecycle?.currentState == Lifecycle.State.DESTROYED
    ) {
      return
    }
    if (
      isEscapeListenerInstalled ||
        !inputState.hasRequestCloseHandler ||
        !inputState.isPresentationActive
    ) {
      return
    }

    ViewCompat.addOnUnhandledKeyEventListener(view, unhandledKeyEventListener)
    isEscapeListenerInstalled = true
  }

  /** Keeps one OnBackPressedCallback for a complete presentation, including predictive Back. */
  private fun reconcileBackCallback() {
    if (disposed || isReconcilingBackCallback) return
    isReconcilingBackCallback = true
    try {
      val currentRoutingContext = requestCloseRoutingContext
      val dispatcher =
        currentRoutingContext
          ?.takeIf { usesPortalPresentation && inputState.isAttached }
          ?.dispatcherOwner
          ?.onBackPressedDispatcher

      if (backDispatcher !== dispatcher) {
        disposeBackCallbackImmediately()
      }

      val presentationActive = currentRoutingContext != null && inputState.isPresentationActive
      val callback = backCallback
      if (callback != null) {
        if (!presentationActive) {
          hasBackCallbackPresentationEnded = true
        }

        if (hasBackCallbackPresentationEnded) {
          if (callback.isPredictiveBackInProgress) {
            callback.updateState(
              canReceiveBack = false,
              currentAction = RequestCloseInputAction.CONSUME,
            )
            return
          }
          disposeBackCallbackImmediately()
        } else {
          callback.updateState(
            canReceiveBack = assignedRequestCloseAction != RequestCloseInputAction.PASS_THROUGH,
            currentAction = assignedRequestCloseAction,
          )
          return
        }
      }

      if (dispatcher != null && presentationActive && inputState.hasRequestCloseHandler) {
        val newCallback =
          RequestCloseBackCallback(
            resolveAction = { assignedRequestCloseAction },
            executeAction = ::executeBackAction,
            onPredictiveBackStateChanged = ::reconcileBackCallback,
          )
        backDispatcher = dispatcher
        backCallback = newCallback
        hasBackCallbackPresentationEnded = false
        dispatcher.addCallback(newCallback)
        newCallback.updateState(
          canReceiveBack = assignedRequestCloseAction != RequestCloseInputAction.PASS_THROUGH,
          currentAction = assignedRequestCloseAction,
        )
      }
    } finally {
      isReconcilingBackCallback = false
    }
  }

  private fun executeBackAction(action: RequestCloseInputAction) {
    if (action == RequestCloseInputAction.REQUEST_CLOSE) {
      emitRequestCloseIfEligible()
    }
  }

  private fun clearRoutingContext() {
    val wasReconciling = isReconcilingBackCallback
    isReconcilingBackCallback = true
    try {
      removeInputHandlers()
      requestCloseRoutingContext?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      requestCloseRoutingContext = null
      requestCloseRegistration?.remove()
      requestCloseRegistration = null
      assignedRequestCloseAction = RequestCloseInputAction.PASS_THROUGH
    } finally {
      isReconcilingBackCallback = wasReconciling
    }
  }

  private fun removeInputHandlers() {
    val wasReconciling = isReconcilingBackCallback
    isReconcilingBackCallback = true
    try {
      disposeBackCallbackImmediately()
      if (isEscapeListenerInstalled) {
        ViewCompat.removeOnUnhandledKeyEventListener(view, unhandledKeyEventListener)
        isEscapeListenerInstalled = false
      }
    } finally {
      isReconcilingBackCallback = wasReconciling
    }
  }

  private fun disposeBackCallbackImmediately() {
    val callback = backCallback
    backCallback = null
    backDispatcher = null
    hasBackCallbackPresentationEnded = false
    callback?.dispose()
    callback?.remove()
  }

  private fun routingContextsAreIdentical(
    first: PortalRequestCloseRoutingContext?,
    second: PortalRequestCloseRoutingContext?,
  ): Boolean {
    if (first == null || second == null) return first === second
    return first.dispatcherOwner === second.dispatcherOwner &&
      first.lifecycleOwner === second.lifecycleOwner &&
      first.rootView === second.rootView
  }

  private companion object {
    val INACTIVE_INPUT_STATE =
      RequestCloseInputState(
        isAttached = false,
        isLifecycleActive = false,
        isModal = false,
        hasRequestCloseHandler = false,
        isPresentationActive = false,
        isTargetResolvedAndOpen = false,
      )
  }
}
