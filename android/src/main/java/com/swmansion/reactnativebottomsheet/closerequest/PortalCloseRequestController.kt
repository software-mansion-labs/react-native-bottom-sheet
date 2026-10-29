package com.swmansion.reactnativebottomsheet.closerequest

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.ReactActivity
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationAssignment
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationContext
import com.swmansion.reactnativebottomsheet.presentation.PortalPresentationCoordinator

/** Owns close request routing for one portal [BottomSheetView]. */
internal class PortalCloseRequestController(
  private val view: View,
  private val currentActivity: () -> Activity?,
  private val emitCloseRequest: () -> Boolean,
) : PortalCloseRequestParticipant {
  override val inputAnchor: View
    get() = view

  override fun currentInputAction(): CloseRequestInputAction = currentPortalAction()

  private var inputState = CloseRequestInputState.INACTIVE
  private var usesPortalPresentation = false
  private var disposed = false

  private var closeRequestRoutingContext: PortalCloseRequestRoutingContext? = null
  private var presentationContext: PortalPresentationContext? = null
  private var presentationAssignment = PortalPresentationAssignment.NONE
  private var hasBackCallbackPresentationEnded = false
  private var isReconcilingBackCallback = false
  private var isKeyListenerInstalled = false

  private var backDispatcher: OnBackPressedDispatcher? = null

  private var backCallback: CloseRequestBackCallback? = null
  private var backInput: PortalBackInput? = null
  private var legacyBackKeyRegistration: LegacyBackKeyRouting.Registration? = null
  private var appliedImeVisible: Boolean? = null

  private val lifecycleObserver =
    object : LifecycleEventObserver {
      override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (disposed || source !== closeRequestRoutingContext?.lifecycleOwner) return
        if (event == Lifecycle.Event.ON_DESTROY) source.lifecycle.removeObserver(this)
        reconcileInputHandling()
      }
    }

  private val unhandledKeyEventListener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
    dispatchEscape(event) || backInput?.dispatchKey(event) == true
  }

  fun update(
    state: CloseRequestInputState,
    usesPortalPresentation: Boolean,
  ) {
    if (disposed) return
    inputState = state
    this.usesPortalPresentation = usesPortalPresentation
    syncRoutingContext()
  }

  fun onImeVisibilityChanged(visible: Boolean) {
    if (disposed || appliedImeVisible == visible) return
    appliedImeVisible = visible
    reconcileBackCallback()
  }

  fun onPresentationChanged(
    context: PortalPresentationContext?,
    assignment: PortalPresentationAssignment,
  ) {
    if (disposed) return
    if (
      context?.windowRoot !== presentationContext?.windowRoot ||
        assignment == PortalPresentationAssignment.NONE
    ) {
      presentationContext?.windowRoot?.let { PortalCloseRequestCoordinator.release(it, this) }
    }
    presentationContext = context
    presentationAssignment = assignment
    syncRoutingContext()
  }

  fun dispatchEscape(event: KeyEvent): Boolean {
    if (disposed || !usesPortalPresentation || !inputState.isModal || !inputState.isAttached)
      return false
    val context = presentationContext ?: return false
    val portalRoot = context.windowRoot
    if (context.windowRoot !== view.rootView) return false
    PortalPresentationCoordinator.reconcile(portalRoot)
    return PortalCloseRequestCoordinator.dispatchEscape(portalRoot, event)
  }

  fun clear() {
    usesPortalPresentation = false
    clearRoutingContext()
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    usesPortalPresentation = false
    clearRoutingContext()
  }

  override fun onInputPolicyChanged() {
    if (disposed) return
    ensureInputListener()
    reconcileBackCallback()
  }

  override fun emitCloseRequestIfEligible(): Boolean {
    if (disposed || currentPortalAction() != CloseRequestInputAction.EMIT_CLOSE_REQUEST) {
      return false
    }
    return emitCloseRequest()
  }

  private fun syncRoutingContext() {
    if (disposed) return
    val resolvedRoutingContext =
      view
        .takeIf {
          usesPortalPresentation &&
            inputState.isAttached &&
            inputState.isModal &&
            presentationContext != null
        }
        ?.resolvePortalCloseRequestRoutingContext(currentActivity())
    val previousRoutingContext = closeRequestRoutingContext
    if (!routingContextsAreIdentical(resolvedRoutingContext, previousRoutingContext)) {
      previousRoutingContext?.windowRoot?.let {
        PortalCloseRequestCoordinator.removeParticipant(it, this)
      }
      removeInputHandlers()
      appliedImeVisible = null
      previousRoutingContext?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      closeRequestRoutingContext = resolvedRoutingContext
      resolvedRoutingContext?.lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
    }
    reconcileInputHandling()
  }

  private fun reconcileInputHandling() {
    if (disposed) return
    ensureInputListener()
    presentationContext?.windowRoot?.let { windowRoot ->
      PortalCloseRequestCoordinator.updateParticipant(windowRoot, this)
      if (presentationAssignment != PortalPresentationAssignment.NONE) {
        PortalCloseRequestCoordinator.assign(windowRoot, this, currentPortalAction())
      } else {
        PortalCloseRequestCoordinator.release(windowRoot, this)
      }
    }
    reconcileBackCallback()
  }

  private fun currentPortalAction(): CloseRequestInputAction {
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
            currentRoutingContext.windowRoot === view.rootView,
        isLifecycleActive = isLifecycleActive,
      )
    return resolveCloseRequestInputAction(effectiveInputState)
  }

  /**
   * Starts key registration when a presentation can handle input, then keeps the listener stable
   * for the structural lifetime of the resolved routing context.
   */
  private fun ensureInputListener() {
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
      isKeyListenerInstalled ||
        (!inputState.hasCloseRequestHandler &&
          !PortalCloseRequestCoordinator.hasInputHandler(currentRoutingContext.windowRoot)) ||
        !inputState.isPresentationActive
    ) {
      return
    }

    ViewCompat.addOnUnhandledKeyEventListener(view, unhandledKeyEventListener)
    isKeyListenerInstalled = true
  }

  /** Keeps one system input registration through a presentation and its captured Back. */
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
      val canProxyBack =
        presentationAssignment != PortalPresentationAssignment.NONE &&
          currentRoutingContext?.windowRoot?.let(PortalCloseRequestCoordinator::hasInputHandler) ==
            true
      val proxyAction =
        if (canProxyBack) CloseRequestInputAction.EMIT_CLOSE_REQUEST
        else CloseRequestInputAction.PASS_THROUGH
      val callback = backCallback
      val capturedAction =
        if (callback?.isBackInProgress == true)
          currentRoutingContext?.windowRoot?.let {
            PortalCloseRequestCoordinator.capturedBackAction(it, this)
          }
        else null
      if (callback != null) {
        if (!presentationActive) {
          hasBackCallbackPresentationEnded = true
        }

        if (hasBackCallbackPresentationEnded) {
          if (callback.isBackInProgress) {
            callback.updateState(
              canReceiveBack = false,
              currentAction = capturedAction ?: CloseRequestInputAction.CONSUME,
            )
            updateBackInput(callback)
            return
          }
          disposeBackCallbackImmediately()
        } else {
          callback.updateState(
            canReceiveBack = canProxyBack,
            currentAction = capturedAction ?: proxyAction,
          )
          updateBackInput(callback)
          return
        }
      }

      if (
        dispatcher != null &&
          presentationActive &&
          (inputState.hasCloseRequestHandler || canProxyBack)
      ) {
        val newCallback =
          CloseRequestBackCallback(
            resolveAction = {
              currentRoutingContext?.windowRoot?.let {
                PortalCloseRequestCoordinator.resolveBackAction(it, this)
              } ?: CloseRequestInputAction.PASS_THROUGH
            },
            executeAction = ::executeBackAction,
            onCaptureStateChanged = ::reconcileBackCallback,
            onCaptureCancelled = {
              PortalCloseRequestCoordinator.cancelBackCapture(
                currentRoutingContext.windowRoot,
                this,
              )
            },
          )
        backDispatcher = dispatcher
        backCallback = newCallback
        hasBackCallbackPresentationEnded = false
        val newInput = PortalBackInput(view, newCallback)
        backInput = newInput
        legacyBackKeyRegistration =
          (currentRoutingContext.dispatcherOwner as? Activity)?.window?.let { window ->
            LegacyBackKeyRouting.register(window, newInput)
          }
        newCallback.updateState(
          canReceiveBack = canProxyBack,
          currentAction = proxyAction,
        )
        updateBackInput(newCallback)
      }
    } finally {
      isReconcilingBackCallback = false
    }
  }

  private fun updateBackInput(callback: CloseRequestBackCallback) {
    val imeVisible =
      appliedImeVisible
        ?: (ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true)
    backInput?.update(callback.isEnabled && (!imeVisible || callback.isBackInProgress))
  }

  private fun executeBackAction(action: CloseRequestInputAction) {
    closeRequestRoutingContext?.windowRoot?.let {
      PortalCloseRequestCoordinator.executeBackAction(it, this, action)
    }
    if (action == CloseRequestInputAction.PASS_THROUGH) {
      val dispatcher = backDispatcher ?: return
      val host = currentActivity() as? ReactActivity
      if (host != null && closeRequestRoutingContext?.dispatcherOwner === host) {
        // RN's later default enters the Activity dispatcher, where this portal has no callback.
        @Suppress("DEPRECATION") host.onBackPressed()
      } else {
        dispatcher.onBackPressed()
      }
    }
  }

  private fun clearRoutingContext() {
    val wasReconciling = isReconcilingBackCallback
    isReconcilingBackCallback = true
    try {
      removeInputHandlers()
      closeRequestRoutingContext?.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
      closeRequestRoutingContext = null
      presentationContext?.windowRoot?.let {
        PortalCloseRequestCoordinator.removeParticipant(it, this)
      }
      presentationContext = null
      presentationAssignment = PortalPresentationAssignment.NONE
    } finally {
      isReconcilingBackCallback = wasReconciling
    }
  }

  private fun removeInputHandlers() {
    val wasReconciling = isReconcilingBackCallback
    isReconcilingBackCallback = true
    try {
      disposeBackCallbackImmediately()
      if (isKeyListenerInstalled) {
        ViewCompat.removeOnUnhandledKeyEventListener(view, unhandledKeyEventListener)
        isKeyListenerInstalled = false
      }
    } finally {
      isReconcilingBackCallback = wasReconciling
    }
  }

  private fun disposeBackCallbackImmediately() {
    val callback = backCallback
    backInput?.dispose()
    backInput = null
    legacyBackKeyRegistration?.remove()
    legacyBackKeyRegistration = null
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
      first.windowRoot === second.windowRoot
  }
}
