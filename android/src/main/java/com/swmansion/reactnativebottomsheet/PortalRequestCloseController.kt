package com.swmansion.reactnativebottomsheet

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Owns the portal transport for close-request input for one [BottomSheetView]. */
internal class PortalRequestCloseController(
  private val view: View,
  private val currentActivity: () -> Activity?,
  private val emitRequestClose: () -> Boolean,
) : PortalRequestCloseTarget {
  private var inputState = INACTIVE_INPUT_STATE
  private var enabled = false
  private var disposed = false

  private var requestCloseHost: PortalRequestCloseHost? = null
  private var requestCloseRegistration: PortalRequestCloseCoordinator.Registration? = null
  private var requestCloseAction = RequestCloseInputAction.PASS_THROUGH
  private var backPresentationEnded = false
  private var isReconcilingBackHandler = false
  private var escapeListenerInstalled = false

  internal var backDispatcher: OnBackPressedDispatcher? = null
    private set

  internal var backCallback: RequestCloseBackCallback? = null
    private set

  internal val escapeListener: ViewCompat.OnUnhandledKeyEventListenerCompat?
    get() = unhandledKeyEventListener.takeIf { escapeListenerInstalled }

  private val lifecycleObserver: LifecycleEventObserver = LifecycleEventObserver { owner, event ->
    if (disposed || owner !== requestCloseHost?.lifecycleOwner) {
      return@LifecycleEventObserver
    }
    if (event == Lifecycle.Event.ON_DESTROY) {
      owner.lifecycle.removeObserver(lifecycleObserver)
    }
    refreshHandling()
  }

  private val unhandledKeyEventListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
    dispatchEscape(event)
  }

  private val syncHostRunnable = Runnable { syncHost() }

  /** Updates the immutable input snapshot and reconciles the currently resolved portal host. */
  fun update(
    state: RequestCloseInputState,
    enabled: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.enabled = enabled
    syncHost()
  }

  /** Re-resolves view-tree owners after Android has completed an attachment/layout turn. */
  fun scheduleHostSync() {
    if (disposed) return
    view.removeCallbacks(syncHostRunnable)
    view.post(syncHostRunnable)
  }

  fun dispatchEscape(event: KeyEvent): Boolean {
    if (disposed || !enabled || !inputState.isModal || !inputState.isAttached) return false
    val portalRoot = requestCloseHost?.rootView ?: return false
    if (portalRoot !== view.rootView) return false
    return PortalRequestCloseCoordinator.dispatchEscape(portalRoot, event)
  }

  /** Removes every host-scoped effect. A later [update] may establish a new host. */
  fun clear() {
    enabled = false
    view.removeCallbacks(syncHostRunnable)
    clearHost()
  }

  /** Terminal, idempotent cleanup. */
  fun dispose() {
    if (disposed) return
    disposed = true
    enabled = false
    view.removeCallbacks(syncHostRunnable)
    clearHost()
  }

  override fun onPortalRequestCloseActionChanged(action: RequestCloseInputAction) {
    if (disposed) return
    requestCloseAction = action
    reconcileBackHandler()
  }

  override fun emitPortalRequestCloseIfEligible(): Boolean {
    if (
      disposed ||
        requestCloseAction != RequestCloseInputAction.REQUEST_CLOSE ||
        currentPortalState().actionIfOwner != RequestCloseInputAction.REQUEST_CLOSE
    ) {
      return false
    }
    return emitRequestClose()
  }

  private fun syncHost() {
    if (disposed) return
    val resolvedHost =
      view
        .takeIf { enabled && inputState.isAttached && inputState.isModal }
        ?.resolvePortalRequestCloseHost(currentActivity())
    val previousHost = requestCloseHost
    if (!hostsAreIdentical(resolvedHost, previousHost)) {
      removeInputHandlers()
      previousHost?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      requestCloseHost = resolvedHost
      resolvedHost?.lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)

      if (previousHost?.rootView !== resolvedHost?.rootView) {
        requestCloseRegistration?.remove()
        requestCloseRegistration = null
        if (resolvedHost != null) {
          requestCloseRegistration =
            PortalRequestCloseCoordinator.register(
              resolvedHost.rootView,
              this,
              currentPortalState(),
            )
        }
      } else if (resolvedHost != null && requestCloseRegistration == null) {
        requestCloseRegistration =
          PortalRequestCloseCoordinator.register(
            resolvedHost.rootView,
            this,
            currentPortalState(),
          )
      }
    }
    refreshHandling()
  }

  private fun refreshHandling() {
    if (disposed) return
    ensureEscapeListener()
    requestCloseRegistration?.update(currentPortalState())
    reconcileBackHandler()
  }

  private fun currentPortalState(): PortalRequestCloseState {
    val currentHost = requestCloseHost
    val lifecycleActive =
      currentHost?.lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)
        ?: inputState.isActive
    val transportState =
      inputState.copy(
        isAttached =
          enabled &&
            inputState.isAttached &&
            currentHost != null &&
            currentHost.rootView === view.rootView,
        isActive = lifecycleActive,
      )
    val isOwnerCandidate =
      transportState.isAttached &&
        transportState.isModal &&
        transportState.isActive &&
        transportState.isPresentationActive

    return PortalRequestCloseState(
      isOwnerCandidate = isOwnerCandidate,
      actionIfOwner =
        if (isOwnerCandidate) {
          resolveRequestCloseInputAction(transportState)
        } else {
          RequestCloseInputAction.PASS_THROUGH
        },
    )
  }

  /**
   * Starts Escape registration at the first presentation with a handler, then keeps the listener
   * stable for the structural lifetime of the resolved host.
   */
  private fun ensureEscapeListener() {
    val currentHost = requestCloseHost
    if (
      currentHost == null ||
        !enabled ||
        !inputState.isAttached ||
        currentHost.lifecycleOwner?.lifecycle?.currentState == Lifecycle.State.DESTROYED
    ) {
      return
    }
    if (escapeListenerInstalled || !inputState.hasHandler || !inputState.isPresentationActive) {
      return
    }

    ViewCompat.addOnUnhandledKeyEventListener(view, unhandledKeyEventListener)
    escapeListenerInstalled = true
  }

  /** Keeps one Back callback for a complete presentation, including predictive Back. */
  private fun reconcileBackHandler() {
    if (disposed || isReconcilingBackHandler) return
    isReconcilingBackHandler = true
    try {
      val currentHost = requestCloseHost
      val dispatcher =
        currentHost
          ?.takeIf { enabled && inputState.isAttached }
          ?.dispatcherOwner
          ?.onBackPressedDispatcher

      if (backDispatcher !== dispatcher) {
        disposeBackHandlerImmediately()
      }

      val presentationActive = currentHost != null && inputState.isPresentationActive
      val callback = backCallback
      if (callback != null) {
        if (!presentationActive) {
          backPresentationEnded = true
        }

        if (backPresentationEnded) {
          if (callback.isPredictiveBackInProgress) {
            callback.updateState(
              canReceiveBack = false,
              currentAction = RequestCloseInputAction.CONSUME,
            )
            return
          }
          disposeBackHandlerImmediately()
        } else {
          callback.updateState(
            canReceiveBack = requestCloseAction != RequestCloseInputAction.PASS_THROUGH,
            currentAction = requestCloseAction,
          )
          return
        }
      }

      if (dispatcher != null && presentationActive && inputState.hasHandler) {
        val newCallback =
          RequestCloseBackCallback(
            resolveAction = { requestCloseAction },
            executeAction = ::executeRequestCloseAction,
            onPredictiveBackStateChanged = ::reconcileBackHandler,
          )
        backDispatcher = dispatcher
        backCallback = newCallback
        backPresentationEnded = false
        dispatcher.addCallback(newCallback)
        newCallback.updateState(
          canReceiveBack = requestCloseAction != RequestCloseInputAction.PASS_THROUGH,
          currentAction = requestCloseAction,
        )
      }
    } finally {
      isReconcilingBackHandler = false
    }
  }

  private fun executeRequestCloseAction(action: RequestCloseInputAction) {
    if (action == RequestCloseInputAction.REQUEST_CLOSE) {
      emitPortalRequestCloseIfEligible()
    }
  }

  private fun clearHost() {
    val wasReconciling = isReconcilingBackHandler
    isReconcilingBackHandler = true
    try {
      removeInputHandlers()
      requestCloseHost?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      requestCloseHost = null
      requestCloseRegistration?.remove()
      requestCloseRegistration = null
      requestCloseAction = RequestCloseInputAction.PASS_THROUGH
    } finally {
      isReconcilingBackHandler = wasReconciling
    }
  }

  private fun removeInputHandlers() {
    val wasReconciling = isReconcilingBackHandler
    isReconcilingBackHandler = true
    try {
      disposeBackHandlerImmediately()
      if (escapeListenerInstalled) {
        ViewCompat.removeOnUnhandledKeyEventListener(view, unhandledKeyEventListener)
        escapeListenerInstalled = false
      }
    } finally {
      isReconcilingBackHandler = wasReconciling
    }
  }

  private fun disposeBackHandlerImmediately() {
    val callback = backCallback
    backCallback = null
    backDispatcher = null
    backPresentationEnded = false
    callback?.dispose()
    callback?.remove()
  }

  private fun hostsAreIdentical(
    first: PortalRequestCloseHost?,
    second: PortalRequestCloseHost?,
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
        isActive = false,
        isModal = false,
        hasHandler = false,
        isPresentationActive = false,
        isTargetOpen = false,
      )
  }
}
