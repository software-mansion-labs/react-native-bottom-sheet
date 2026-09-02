package com.swmansion.reactnativebottomsheet.closerequest

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Owns close request routing for one portal [BottomSheetView]. */
internal class PortalCloseRequestController(
  private val view: View,
  private val currentActivity: () -> Activity?,
  private val emitCloseRequest: () -> Boolean,
) : PortalCloseRequestParticipant {
  private var inputState = CloseRequestInputState.INACTIVE
  private var usesPortalPresentation = false
  private var disposed = false

  private var closeRequestRoutingContext: PortalCloseRequestRoutingContext? = null
  private var closeRequestRegistration: PortalCloseRequestCoordinator.Registration? = null
  private var assignedCloseRequestAction = CloseRequestInputAction.PASS_THROUGH
  private var hasBackCallbackPresentationEnded = false
  private var isReconcilingBackCallback = false
  private var isEscapeListenerInstalled = false

  private var backDispatcher: OnBackPressedDispatcher? = null

  private var backCallback: CloseRequestBackCallback? = null

  private val lifecycleObserver: LifecycleEventObserver =
    LifecycleEventObserver { lifecycleOwner, event ->
      if (disposed || lifecycleOwner !== closeRequestRoutingContext?.lifecycleOwner) {
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

  fun update(
    state: CloseRequestInputState,
    usesPortalPresentation: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.usesPortalPresentation = usesPortalPresentation
    syncRoutingContext()
  }

  fun scheduleRoutingContextSync() {
    if (disposed) return
    view.removeCallbacks(syncRoutingContextRunnable)
    view.post(syncRoutingContextRunnable)
  }

  fun dispatchEscape(event: KeyEvent): Boolean {
    if (disposed || !usesPortalPresentation || !inputState.isModal || !inputState.isAttached)
      return false
    val portalRoot = closeRequestRoutingContext?.rootView ?: return false
    if (portalRoot !== view.rootView) return false
    return PortalCloseRequestCoordinator.dispatchEscape(portalRoot, event)
  }

  fun clear() {
    usesPortalPresentation = false
    view.removeCallbacks(syncRoutingContextRunnable)
    clearRoutingContext()
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    usesPortalPresentation = false
    view.removeCallbacks(syncRoutingContextRunnable)
    clearRoutingContext()
  }

  override fun onAssignedActionChanged(action: CloseRequestInputAction) {
    if (disposed) return
    assignedCloseRequestAction = action
    reconcileBackCallback()
  }

  override fun emitCloseRequestIfEligible(): Boolean {
    if (
      disposed ||
        assignedCloseRequestAction != CloseRequestInputAction.EMIT_CLOSE_REQUEST ||
        currentPortalState().actionIfRoutingOwner != CloseRequestInputAction.EMIT_CLOSE_REQUEST
    ) {
      return false
    }
    return emitCloseRequest()
  }

  private fun syncRoutingContext() {
    if (disposed) return
    val resolvedRoutingContext =
      view
        .takeIf { usesPortalPresentation && inputState.isAttached && inputState.isModal }
        ?.resolvePortalCloseRequestRoutingContext(currentActivity())
    val previousRoutingContext = closeRequestRoutingContext
    if (!routingContextsAreIdentical(resolvedRoutingContext, previousRoutingContext)) {
      removeInputHandlers()
      previousRoutingContext?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      closeRequestRoutingContext = resolvedRoutingContext
      resolvedRoutingContext?.lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)

      if (previousRoutingContext?.rootView !== resolvedRoutingContext?.rootView) {
        closeRequestRegistration?.remove()
        closeRequestRegistration = null
        if (resolvedRoutingContext != null) {
          closeRequestRegistration =
            PortalCloseRequestCoordinator.register(
              resolvedRoutingContext.rootView,
              this,
              currentPortalState(),
            )
        }
      } else if (resolvedRoutingContext != null && closeRequestRegistration == null) {
        closeRequestRegistration =
          PortalCloseRequestCoordinator.register(
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
    closeRequestRegistration?.update(currentPortalState())
    reconcileBackCallback()
  }

  private fun currentPortalState(): PortalCloseRequestState {
    val currentRoutingContext = closeRequestRoutingContext
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

    return PortalCloseRequestState(
      isRoutingOwnerCandidate = isRoutingOwnerCandidate,
      actionIfRoutingOwner =
        if (isRoutingOwnerCandidate) {
          resolveCloseRequestInputAction(effectiveInputState)
        } else {
          CloseRequestInputAction.PASS_THROUGH
        },
    )
  }

  /**
   * Starts Escape registration at the first presentation with a handler, then keeps the listener
   * stable for the structural lifetime of the resolved routing context.
   */
  private fun ensureEscapeListener() {
    val currentRoutingContext = closeRequestRoutingContext
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
        !inputState.hasCloseRequestHandler ||
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
      val currentRoutingContext = closeRequestRoutingContext
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
              currentAction = CloseRequestInputAction.CONSUME,
            )
            return
          }
          disposeBackCallbackImmediately()
        } else {
          callback.updateState(
            canReceiveBack = assignedCloseRequestAction != CloseRequestInputAction.PASS_THROUGH,
            currentAction = assignedCloseRequestAction,
          )
          return
        }
      }

      if (dispatcher != null && presentationActive && inputState.hasCloseRequestHandler) {
        val newCallback =
          CloseRequestBackCallback(
            resolveAction = { assignedCloseRequestAction },
            executeAction = ::executeBackAction,
            onPredictiveBackStateChanged = ::reconcileBackCallback,
          )
        backDispatcher = dispatcher
        backCallback = newCallback
        hasBackCallbackPresentationEnded = false
        dispatcher.addCallback(newCallback)
        newCallback.updateState(
          canReceiveBack = assignedCloseRequestAction != CloseRequestInputAction.PASS_THROUGH,
          currentAction = assignedCloseRequestAction,
        )
      }
    } finally {
      isReconcilingBackCallback = false
    }
  }

  private fun executeBackAction(action: CloseRequestInputAction) {
    if (action == CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      emitCloseRequestIfEligible()
    }
  }

  private fun clearRoutingContext() {
    val wasReconciling = isReconcilingBackCallback
    isReconcilingBackCallback = true
    try {
      removeInputHandlers()
      closeRequestRoutingContext?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      closeRequestRoutingContext = null
      closeRequestRegistration?.remove()
      closeRequestRegistration = null
      assignedCloseRequestAction = CloseRequestInputAction.PASS_THROUGH
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
    first: PortalCloseRequestRoutingContext?,
    second: PortalCloseRequestRoutingContext?,
  ): Boolean {
    if (first == null || second == null) return first === second
    return first.dispatcherOwner === second.dispatcherOwner &&
      first.lifecycleOwner === second.lifecycleOwner &&
      first.rootView === second.rootView
  }
}
