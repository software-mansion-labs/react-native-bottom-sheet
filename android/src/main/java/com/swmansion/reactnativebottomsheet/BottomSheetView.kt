@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.common.LifecycleState
import com.facebook.react.config.ReactFeatureFlags
import com.facebook.react.uimanager.JSPointerDispatcher
import com.facebook.react.uimanager.JSTouchDispatcher
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.RootView
import com.facebook.react.uimanager.StateWrapper
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.EventDispatcher
import com.facebook.react.views.view.ReactViewGroup

/**
 * Fabric-mounted bottom-sheet view. It is a thin coordinator around a single [BottomSheetHostView]
 * that does the real work (scrim, gestures, detents, layout, events).
 *
 * In the default (portal) mode the host is a full-size child of this view, so behavior is identical
 * to hosting the logic directly. In native-overlay mode the host is hoisted into a full-screen,
 * edge-to-edge, transparent dialog that floats above everything—including native modal
 * screens—letting the sheet escape the JS portal's React tree (see issue #16).
 *
 * Child mounting and prop setters are delegated to the host; the view manager already routes
 * children through [addSheetChild]/[sheetChildCount].
 */
class BottomSheetView(context: Context) : ReactViewGroup(context), LifecycleEventListener {

  private val host = BottomSheetHostView(context)
  private val themedReactContext = context as? ThemedReactContext
  private var overlayDialog: ComponentDialog? = null
  private var overlayRoot: BottomSheetDialogRootView? = null
  private var nativeOverlay = false
  private var overlayPresentationFailed = false
  // Cached only while a dialog is present, so per-frame interaction callbacks
  // don't thrash the window flags.
  private var overlayInteractive: Boolean? = null
  private var overlayFocusable: Boolean? = null
  private var overlayBackCallback: RequestCloseBackCallback? = null
  private var overlayHostBackDispatcher: OnBackPressedDispatcher? = null

  private var requestCloseHandlerPresent = false
  private var isViewAttached = false
  private var isHostActive = themedReactContext?.lifecycleState == LifecycleState.RESUMED

  private var portalRequestCloseHost: PortalRequestCloseHost? = null
  private var portalRequestCloseRegistration: PortalRequestCloseCoordinator.Registration? = null
  private var portalRequestCloseAction = RequestCloseInputAction.PASS_THROUGH
  private var portalBackDispatcher: OnBackPressedDispatcher? = null
  private var portalBackCallback: RequestCloseBackCallback? = null
  private var portalBackPresentationEnded = false
  private var isReconcilingPortalBackHandler = false
  private var portalEscapeListenerInstalled = false
  private var portalEscapeListenerLifetimeStarted = false
  private val nativeOverlayEscapeDispatcher = EscapeRequestCloseDispatcher()
  private val portalLifecycleObserver: LifecycleEventObserver =
    LifecycleEventObserver { owner, event ->
      if (owner !== portalRequestCloseHost?.lifecycleOwner) return@LifecycleEventObserver
      if (event == Lifecycle.Event.ON_DESTROY) {
        owner.lifecycle.removeObserver(portalLifecycleObserver)
      }
      publishPortalRequestCloseState()
      reconcilePortalBackHandler()
    }
  private val portalUnhandledKeyEventListener =
    ViewCompat.OnUnhandledKeyEventListenerCompat { _, event ->
      dispatchPortalEscape(event)
    }
  private val portalRequestCloseTarget =
    object : PortalRequestCloseTarget {
      override fun onPortalRequestCloseActionChanged(action: RequestCloseInputAction) {
        portalRequestCloseAction = action
        reconcilePortalBackHandler()
      }

      override fun emitPortalRequestCloseIfEligible(): Boolean =
        this@BottomSheetView.emitPortalRequestCloseIfEligible()
    }
  private val syncPortalHostRunnable = Runnable { syncPortalRequestCloseHost() }

  init {
    pointerEvents = PointerEvents.BOX_NONE
    host.interactionListener = { interactive -> updateOverlayTouchability(interactive) }
    host.requestCloseStateChangedListener = ::onRequestCloseStateChanged
    attachHostInline()
    // The overlay dialog's window is bound to the host activity, so we follow the
    // activity lifecycle: tear the window down before the activity is destroyed
    // (otherwise the window leaks) and restore it when the activity resumes.
    themedReactContext?.addLifecycleEventListener(this)
  }

  override fun setId(id: Int) {
    super.setId(id)
    overlayRoot?.id = id
  }

  // MARK: - Listener / state forwarding

  var listener: BottomSheetViewListener?
    get() = host.listener
    set(value) {
      host.listener = value
    }

  var stateWrapper: StateWrapper?
    get() = host.stateWrapper
    set(value) {
      host.stateWrapper = value
    }

  var eventDispatcher: EventDispatcher? = null
    set(value) {
      field = value
      overlayRoot?.eventDispatcher = value
    }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun requestCloseTestSnapshot() =
    BottomSheetViewRequestCloseTestSnapshot(
      isTargetOpen = host.isRequestCloseTargetOpen,
      isPresentationActive = host.isRequestClosePresentationActive,
      overlayDialog = overlayDialog,
      overlayRoot = overlayRoot,
      portalBackDispatcher = portalBackDispatcher,
      portalBackCallback = portalBackCallback,
      portalPredictiveBackInProgress = portalBackCallback?.isPredictiveBackInProgress == true,
      portalEscapeListener =
        portalUnhandledKeyEventListener.takeIf {
          portalEscapeListenerInstalled
        },
      overlayBackCallback = overlayBackCallback,
      overlayPredictiveBackInProgress = overlayBackCallback?.isPredictiveBackInProgress == true,
    )

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun updateOverlayInteractionForTesting(interactive: Boolean) =
    updateOverlayTouchability(interactive)

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal fun skipActiveAnimationToEndForTesting(): Boolean =
    host.skipActiveAnimationToEndForTesting()

  // MARK: - Child view management (routed to the host's sheet container)

  val sheetChildCount: Int
    get() = host.sheetChildCount

  fun getSheetChildAt(index: Int): View? = host.getSheetChildAt(index)

  fun addSheetChild(child: View, index: Int) = host.addSheetChild(child, index)

  fun removeSheetChildAt(index: Int) = host.removeSheetChildAt(index)

  // MARK: - Prop setters (forwarded to the host)

  fun setDetents(raw: List<Map<String, Any>>) = host.setDetents(raw)

  fun setIndex(newIndex: Int) = host.setIndex(newIndex)

  var animateIn: Boolean
    get() = host.animateIn
    set(value) {
      host.animateIn = value
    }

  var animateContentHeight: Boolean
    get() = host.animateContentHeight
    set(value) {
      host.animateContentHeight = value
    }

  var modal: Boolean
    get() = host.modal
    set(value) {
      if (value == host.modal) return
      host.modal = value
      syncPortalRequestCloseHost()
    }

  var scrollableExpandNegotiation: Int
    get() = host.scrollableExpandNegotiation
    set(value) {
      host.scrollableExpandNegotiation = value
    }

  var scrollableCollapseNegotiation: Int
    get() = host.scrollableCollapseNegotiation
    set(value) {
      host.scrollableCollapseNegotiation = value
    }

  var extendUnderStatusBar: Boolean
    get() = host.extendUnderStatusBar
    set(value) {
      host.extendUnderStatusBar = value
    }

  fun setScrimColor(color: Int?) = host.setScrimColor(color)

  fun setScrimOpacities(values: List<Float>) = host.setScrimOpacities(values)

  fun setRequestCloseHandlerPresent(value: Boolean) {
    if (value == requestCloseHandlerPresent) return
    requestCloseHandlerPresent = value
    syncPortalRequestCloseHost()
  }

  fun setNativeOverlay(value: Boolean) {
    if (value == nativeOverlay) {
      if (!value && overlayPresentationFailed) {
        overlayPresentationFailed = false
        syncPortalRequestCloseHost()
      }
      return
    }
    nativeOverlay = value
    overlayPresentationFailed = false
    if (value) {
      // Presentation modes own separate Escape dispatchers. Clear the overlay dispatcher before
      // creating its dialog; clearing the portal host below invalidates the root-owned sequence.
      nativeOverlayEscapeDispatcher.clear()
      clearPortalRequestCloseHost()
      presentOverlay()
    } else {
      dismissOverlay()
    }
    syncPortalRequestCloseHost()
  }

  // MARK: - Inline vs overlay presentation

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isViewAttached = true
    syncPortalRequestCloseHost()
    post(syncPortalHostRunnable)
    overlayDialog?.let { dialog ->
      installOverlayInputHandlers(dialog)
    }
    if (nativeOverlay && overlayDialog == null) {
      presentOverlay()
    }
  }

  override fun onDetachedFromWindow() {
    isViewAttached = false
    removeCallbacks(syncPortalHostRunnable)
    clearPortalRequestCloseHost()
    clearOverlayInputHandlers(overlayDialog)
    nativeOverlayEscapeDispatcher.clear()
    updateRequestCloseHandling()
    super.onDetachedFromWindow()
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    syncPortalRequestCloseHost()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    // Portal Escape is intentionally resolved before `super`: an eligible portal behaves as a
    // modal boundary, so a focused descendant cannot consume the sequence first. The
    // OnUnhandledKeyEventListener remains only an outside-subtree fallback after normal dispatch
    // leaves Escape unhandled.
    if (dispatchPortalEscape(event)) return true
    return super.dispatchKeyEvent(event)
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    if (host.parent === this) {
      host.measure(
        MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY),
      )
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (host.parent === this) {
      host.layout(0, 0, right - left, bottom - top)
    }
  }

  private fun attachHostInline() {
    if (host.parent === this) return
    (host.parent as? ViewGroup)?.removeView(host)
    super.addView(host, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
  }

  private fun presentOverlay() {
    val reactContext = context as? ThemedReactContext
    val activity = reactContext?.currentActivity
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
      // Without an activity there is no window to host the dialog; stay inline.
      nativeOverlay = false
      overlayPresentationFailed = true
      updateRequestCloseHandling()
      return
    }
    (host.parent as? ViewGroup)?.removeView(host)

    val dialog = ComponentDialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
    val root =
      BottomSheetDialogRootView(reactContext).apply {
        id = this@BottomSheetView.id
        eventDispatcher = this@BottomSheetView.eventDispatcher ?: currentEventDispatcher()
        addView(
          host,
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          ),
        )
      }
    dialog.setContentView(
      root,
      ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      ),
    )
    dialog.setCancelable(false)
    dialog.window?.let { configureOverlayWindow(it, activity) }
    overlayInteractive = null
    overlayFocusable = null
    overlayRoot = root
    overlayDialog = dialog
    overlayHostBackDispatcher = (activity as? ComponentActivity)?.onBackPressedDispatcher
    installOverlayInputHandlers(dialog)
    try {
      dialog.show()
      overlayPresentationFailed = false
      dialog.window?.let { configureOverlayWindow(it, activity) }
      updateRequestCloseHandling()
    } catch (_: RuntimeException) {
      // Show failed (e.g. the activity went away mid-present). Dismiss so the
      // partially-created window can't leak, then fall back to inline.
      clearOverlayInputHandlers(dialog)
      runCatching { if (dialog.isShowing) dialog.dismiss() }
      overlayDialog = null
      overlayRoot = null
      overlayInteractive = null
      overlayFocusable = null
      overlayHostBackDispatcher = null
      nativeOverlay = false
      overlayPresentationFailed = true
      (host.parent as? ViewGroup)?.removeView(host)
      attachHostInline()
      updateRequestCloseHandling()
    }
  }

  private fun dismissOverlay() {
    val dialog = overlayDialog
    clearOverlayInputHandlers(dialog)
    dialog?.let {
      (host.parent as? ViewGroup)?.removeView(host)
      if (it.isShowing) it.dismiss()
    }
    overlayDialog = null
    overlayRoot = null
    overlayInteractive = null
    overlayFocusable = null
    attachHostInline()
    updateRequestCloseHandling()
  }

  private fun currentEventDispatcher(): EventDispatcher? =
    UIManagerHelper.getEventDispatcherForReactTag(UIManagerHelper.getReactContext(this), id)

  private fun configureOverlayWindow(window: Window, activity: Activity) {
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window.setGravity(Gravity.TOP or Gravity.START)
    window.setLayout(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
    )
    // The sheet draws its own scrim, so the window must not add system dimming.
    window.setDimAmount(0f)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    // The sheet runs its own enter/exit animation; suppress the dialog's.
    window.setWindowAnimations(0)
    window.enableTransparentEdgeToEdge(activity)

    // Start non-interactive (closed): pass touches and focus to the screen behind
    // until the sheet animates open. Keep the dialog window alpha at 0 while it
    // is non-interactive so Android's untrusted-touch occlusion check does not
    // treat the full-screen dialog as covering the IME.
    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    window.setOverlayWindowAlpha(interactive = false)
    window.setSoftInputMode(
      WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
        WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
    )
    window.setLayout(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
    )
  }

  private fun Window.enableTransparentEdgeToEdge(activity: Activity) {
    @Suppress("DEPRECATION")
    clearFlags(
      WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
    )
    addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

    WindowCompat.setDecorFitsSystemWindows(this, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      attributes = attributes.apply {
        layoutInDisplayCutoutMode =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
          } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
          }
      }
    }

    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      isStatusBarContrastEnforced = false
      isNavigationBarContrastEnforced = false
    }
    @Suppress("DEPRECATION")
    statusBarColor = Color.TRANSPARENT
    @Suppress("DEPRECATION")
    navigationBarColor = Color.TRANSPARENT

    @Suppress("DEPRECATION")
    decorView.systemUiVisibility =
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

    runCatching {
      val activityController =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
      WindowCompat.getInsetsController(this, decorView).apply {
        isAppearanceLightStatusBars = activityController.isAppearanceLightStatusBars
        isAppearanceLightNavigationBars = activityController.isAppearanceLightNavigationBars
      }
    }
  }

  /** Keeps touch pass-through tied only to the existing sheet/scrim interaction state. */
  private fun updateOverlayTouchability(interactive: Boolean) {
    if (interactive == overlayInteractive) return
    overlayInteractive = interactive
    updateRequestCloseHandling()
  }

  private fun Window.setOverlayWindowAlpha(interactive: Boolean) {
    attributes = attributes.apply { alpha = if (interactive) 1f else 0f }
  }

  // MARK: - Request close

  /**
   * Emits a controlled request only. Native input handling must never dismiss the dialog, select a
   * detent, or mutate the sheet's target.
   */
  fun emitRequestCloseIfEligible(): Boolean =
    if (nativeOverlay) {
      emitNativeOverlayRequestCloseIfEligible()
    } else {
      emitPortalRequestCloseIfEligible()
    }

  private fun emitPortalRequestCloseIfEligible(): Boolean {
    if (
      portalRequestCloseAction != RequestCloseInputAction.REQUEST_CLOSE ||
        currentPortalRequestCloseState().actionIfOwner != RequestCloseInputAction.REQUEST_CLOSE
    ) {
      return false
    }
    return emitRequestClose()
  }

  private fun emitNativeOverlayRequestCloseIfEligible(): Boolean {
    if (currentNativeOverlayRequestCloseAction() != RequestCloseInputAction.REQUEST_CLOSE) {
      return false
    }
    return emitRequestClose()
  }

  private fun emitRequestClose(): Boolean {
    val currentListener = listener ?: return false
    currentListener.onRequestClose()
    return true
  }

  private fun updateRequestCloseHandling() {
    val action = currentNativeOverlayRequestCloseAction()
    if (action != RequestCloseInputAction.REQUEST_CLOSE) {
      nativeOverlayEscapeDispatcher.degradeCapturedRequestClose()
    }
    overlayBackCallback?.updateState(
      canReceiveBack = overlayCanReceiveBack(),
      currentAction = action,
    )
    updateOverlayWindowInputFlags()
  }

  private fun onRequestCloseStateChanged() {
    ensurePortalEscapeListener()
    publishPortalRequestCloseState()
    reconcilePortalBackHandler()
    updateRequestCloseHandling()
  }

  /** Whether the dialog can be the platform recipient for a new Back sequence. */
  private fun overlayCanReceiveBack(): Boolean {
    val lifecycleActive =
      overlayDialog?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return nativeOverlay &&
      isViewAttached &&
      isHostActive &&
      lifecycleActive &&
      overlayDialog?.isShowing == true &&
      host.isRequestClosePresentationActive
  }

  /**
   * Modal close-input ownership is opt-in, like React Native Modal's `onRequestClose`. A handler
   * keeps that boundary in place while the target finishes closing; without one, Back is forwarded
   * to the host Activity and Escape stays in normal dialog key routing.
   */
  private fun overlayOwnsCloseInput(): Boolean =
    currentNativeOverlayRequestCloseAction() != RequestCloseInputAction.PASS_THROUGH

  private fun nativeOverlayRequestCloseInputState(): RequestCloseInputState {
    val lifecycleActive =
      overlayDialog?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    return RequestCloseInputState(
      isAttached = nativeOverlay && isViewAttached && overlayDialog?.isShowing == true,
      isActive = isHostActive && lifecycleActive,
      isModal = modal,
      hasHandler = requestCloseHandlerPresent,
      isPresentationActive = host.isRequestClosePresentationActive,
      isTargetOpen = host.isRequestCloseTargetOpen,
    )
  }

  private fun currentNativeOverlayRequestCloseAction(): RequestCloseInputAction =
    resolveRequestCloseInputAction(nativeOverlayRequestCloseInputState())

  private fun currentPortalRequestCloseState(): PortalRequestCloseState {
    val currentHost = portalRequestCloseHost
    val lifecycleActive =
      currentHost?.lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)
        ?: isHostActive
    val inputState =
      RequestCloseInputState(
        isAttached =
          isViewAttached &&
            !nativeOverlay &&
            !overlayPresentationFailed &&
            currentHost != null &&
            currentHost.rootView === rootView,
        isActive = lifecycleActive,
        isModal = modal,
        hasHandler = requestCloseHandlerPresent,
        isPresentationActive = host.isRequestClosePresentationActive,
        isTargetOpen = host.isRequestCloseTargetOpen,
      )
    val isOwnerCandidate =
      isViewAttached &&
        modal &&
        !nativeOverlay &&
        !overlayPresentationFailed &&
        currentHost != null &&
        currentHost.rootView === rootView &&
        lifecycleActive &&
        host.isRequestClosePresentationActive

    return PortalRequestCloseState(
      isOwnerCandidate = isOwnerCandidate,
      actionIfOwner =
        if (isOwnerCandidate) {
          resolveRequestCloseInputAction(inputState)
        } else {
          RequestCloseInputAction.PASS_THROUGH
        },
    )
  }

  private fun publishPortalRequestCloseState() {
    portalRequestCloseRegistration?.update(currentPortalRequestCloseState())
  }

  private fun syncPortalRequestCloseHost() {
    val currentActivity =
      if (themedReactContext != null) themedReactContext.currentActivity else context.findActivity()
    val resolvedHost = takeIf {
      isViewAttached && modal && !nativeOverlay && !overlayPresentationFailed
    }
      ?.resolvePortalRequestCloseHost(currentActivity)
    val previousHost = portalRequestCloseHost
    if (!portalHostsAreIdentical(resolvedHost, previousHost)) {
      removePortalRequestCloseHandlers()
      previousHost?.lifecycleOwner?.lifecycle?.removeObserver(portalLifecycleObserver)
      portalRequestCloseHost = resolvedHost
      resolvedHost?.lifecycleOwner?.lifecycle?.addObserver(portalLifecycleObserver)

      if (previousHost?.rootView !== resolvedHost?.rootView) {
        portalRequestCloseRegistration?.remove()
        portalRequestCloseRegistration = null
        if (resolvedHost != null) {
          portalRequestCloseRegistration =
            PortalRequestCloseCoordinator.register(
              resolvedHost.rootView,
              portalRequestCloseTarget,
              currentPortalRequestCloseState(),
            )
        }
      } else if (resolvedHost != null && portalRequestCloseRegistration == null) {
        portalRequestCloseRegistration =
          PortalRequestCloseCoordinator.register(
            resolvedHost.rootView,
            portalRequestCloseTarget,
            currentPortalRequestCloseState(),
          )
      }
    }
    ensurePortalEscapeListener()
    publishPortalRequestCloseState()
    reconcilePortalBackHandler()
    updateRequestCloseHandling()
  }

  private fun clearPortalRequestCloseHost() {
    removePortalRequestCloseHandlers()
    portalRequestCloseHost?.lifecycleOwner?.lifecycle?.removeObserver(portalLifecycleObserver)
    portalRequestCloseHost = null
    portalRequestCloseRegistration?.remove()
    portalRequestCloseRegistration = null
    portalRequestCloseAction = RequestCloseInputAction.PASS_THROUGH
    portalEscapeListenerLifetimeStarted = false
  }

  private fun portalHostsAreIdentical(
    first: PortalRequestCloseHost?,
    second: PortalRequestCloseHost?,
  ): Boolean {
    if (first == null || second == null) return first === second
    return first.dispatcherOwner === second.dispatcherOwner &&
      first.lifecycleOwner === second.lifecycleOwner &&
      first.rootView === second.rootView
  }

  /**
   * Starts Escape registration at the first active presentation with a handler, then keeps it
   * stable for the structural lifetime of this resolved host.
   */
  private fun ensurePortalEscapeListener() {
    val currentHost = portalRequestCloseHost
    if (currentHost == null || nativeOverlay || !isViewAttached) {
      return
    }
    if (currentHost.lifecycleOwner?.lifecycle?.currentState == Lifecycle.State.DESTROYED) return

    if (requestCloseHandlerPresent && host.isRequestClosePresentationActive) {
      portalEscapeListenerLifetimeStarted = true
    }
    if (!portalEscapeListenerLifetimeStarted || portalEscapeListenerInstalled) return

    ViewCompat.addOnUnhandledKeyEventListener(this, portalUnhandledKeyEventListener)
    portalEscapeListenerInstalled = true
  }

  /**
   * Keeps one physical Back callback for one complete presentation. Eligibility changes only update
   * [OnBackPressedCallback.isEnabled], preserving dispatcher order and predictive-Back selection
   * until that presentation fully ends.
   */
  private fun reconcilePortalBackHandler() {
    if (isReconcilingPortalBackHandler) return
    isReconcilingPortalBackHandler = true
    try {
      val currentHost = portalRequestCloseHost
      val dispatcher =
        currentHost
          ?.takeIf {
            !nativeOverlay && isViewAttached
          }
          ?.dispatcherOwner
          ?.onBackPressedDispatcher

      if (portalBackDispatcher !== dispatcher) {
        disposePortalBackHandlerImmediately()
      }

      val presentationActive = currentHost != null && host.isRequestClosePresentationActive
      val callback = portalBackCallback
      if (callback != null) {
        if (!presentationActive) {
          portalBackPresentationEnded = true
        }

        if (portalBackPresentationEnded) {
          if (callback.isPredictiveBackInProgress) {
            callback.updateState(
              canReceiveBack = false,
              currentAction = RequestCloseInputAction.CONSUME,
            )
            return
          }
          disposePortalBackHandlerImmediately()
        } else {
          callback.updateState(
            canReceiveBack = portalRequestCloseAction != RequestCloseInputAction.PASS_THROUGH,
            currentAction = portalRequestCloseAction,
          )
          return
        }
      }

      if (dispatcher != null && presentationActive && requestCloseHandlerPresent) {
        val callback =
          RequestCloseBackCallback(
            resolveAction = { portalRequestCloseAction },
            executeAction = ::executePortalRequestCloseAction,
            onPredictiveBackStateChanged = ::reconcilePortalBackHandler,
          )
        portalBackDispatcher = dispatcher
        portalBackCallback = callback
        portalBackPresentationEnded = false
        // Registration is presentation-scoped, so manual registration plus isEnabled updates keep
        // transient lifecycle and ownership changes from reordering this callback.
        dispatcher.addCallback(callback)
        callback.updateState(
          canReceiveBack = portalRequestCloseAction != RequestCloseInputAction.PASS_THROUGH,
          currentAction = portalRequestCloseAction,
        )
      }
    } finally {
      isReconcilingPortalBackHandler = false
    }
  }

  /** Structural teardown bypasses presentation retention, including an active predictive Back. */
  private fun disposePortalBackHandlerImmediately() {
    val callback = portalBackCallback
    portalBackCallback = null
    portalBackDispatcher = null
    portalBackPresentationEnded = false
    callback?.dispose()
    callback?.remove()
  }

  private fun executePortalRequestCloseAction(action: RequestCloseInputAction) {
    when (action) {
      RequestCloseInputAction.REQUEST_CLOSE -> emitPortalRequestCloseIfEligible()
      RequestCloseInputAction.CONSUME,
      RequestCloseInputAction.PASS_THROUGH -> Unit
    }
  }

  private fun removePortalRequestCloseHandlers() {
    val wasReconciling = isReconcilingPortalBackHandler
    isReconcilingPortalBackHandler = true
    try {
      disposePortalBackHandlerImmediately()
      removePortalEscapeListener()
      portalEscapeListenerLifetimeStarted = false
    } finally {
      isReconcilingPortalBackHandler = wasReconciling
    }
  }

  private fun removePortalEscapeListener() {
    if (portalEscapeListenerInstalled) {
      ViewCompat.removeOnUnhandledKeyEventListener(this, portalUnhandledKeyEventListener)
      portalEscapeListenerInstalled = false
    }
  }

  private fun dispatchPortalEscape(event: KeyEvent): Boolean {
    if (nativeOverlay || overlayPresentationFailed || !modal || !isViewAttached) return false
    val portalRoot = portalRequestCloseHost?.rootView ?: return false
    if (portalRoot !== rootView) return false
    return PortalRequestCloseCoordinator.dispatchEscape(portalRoot, event) ==
      PortalEscapeDispatchResult.HANDLED
  }

  private fun dispatchEscape(event: KeyEvent): Boolean {
    val hadCapturedPress = nativeOverlayEscapeDispatcher.hasCapturedPress
    val handled =
      nativeOverlayEscapeDispatcher.dispatch(
        event = event,
        resolveInitialAction = ::currentNativeOverlayEscapeAction,
        emitRequestCloseIfEligible = ::emitNativeOverlayRequestCloseIfEligible,
      )

    if (hadCapturedPress && !nativeOverlayEscapeDispatcher.hasCapturedPress) {
      updateOverlayWindowInputFlags()
    }
    return handled
  }

  private fun currentNativeOverlayEscapeAction(): RequestCloseInputAction =
    currentNativeOverlayRequestCloseAction()

  /**
   * Touchability stays coupled to the existing interaction/scrim state. Keyboard focusability is
   * independently enabled for an eligible request, including when the configured scrim opacity is
   * zero. A captured Escape and a pinned predictive-Back action keep focus until their terminal
   * event. Returning false for every non-Escape event lets focused inputs keep normal key routing.
   */
  private fun updateOverlayWindowInputFlags() {
    val window = overlayDialog?.window ?: return
    val touchable = overlayInteractive == true
    val ownsCloseInput = overlayOwnsCloseInput()
    val focusable =
      touchable ||
        ownsCloseInput ||
        nativeOverlayEscapeDispatcher.hasCapturedPress ||
        overlayBackCallback?.isPredictiveBackInProgress == true

    if (touchable) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    } else {
      window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    if (focusable != overlayFocusable) {
      overlayFocusable = focusable
      if (focusable) {
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      } else {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
      }
    }

    window.setOverlayWindowAlpha(touchable || ownsCloseInput)
  }

  private fun clearOverlayInputHandlers(dialog: ComponentDialog?) {
    dialog?.setOnKeyListener(null)
    overlayBackCallback?.dispose()
    overlayBackCallback?.remove()
    overlayBackCallback = null
    overlayHostBackDispatcher = null
    nativeOverlayEscapeDispatcher.clear()
  }

  private fun installOverlayInputHandlers(dialog: ComponentDialog) {
    if (overlayHostBackDispatcher == null) {
      overlayHostBackDispatcher =
        (dialog.context.findActivity() as? ComponentActivity)?.onBackPressedDispatcher
    }
    if (overlayBackCallback == null) {
      val backCallback =
        RequestCloseBackCallback(
          resolveAction = ::currentNativeOverlayRequestCloseAction,
          executeAction = ::executeNativeOverlayRequestCloseAction,
          onPredictiveBackStateChanged = ::updateOverlayWindowInputFlags,
        )
      overlayBackCallback = backCallback
      // The dialog is torn down explicitly, so keep one structural registration and vary only
      // isEnabled. Lifecycle-aware registration would remove and re-add the callback, changing
      // dispatcher order and predictive-Back selection across transient lifecycle updates.
      dialog.onBackPressedDispatcher.addCallback(backCallback)
    }
    dialog.setOnKeyListener(DialogInterface.OnKeyListener { _, _, event -> dispatchEscape(event) })
    updateRequestCloseHandling()
  }

  private fun executeNativeOverlayRequestCloseAction(action: RequestCloseInputAction) {
    when (action) {
      RequestCloseInputAction.REQUEST_CLOSE -> emitNativeOverlayRequestCloseIfEligible()
      RequestCloseInputAction.CONSUME -> Unit
      RequestCloseInputAction.PASS_THROUGH -> overlayHostBackDispatcher?.onBackPressed()
    }
  }

  // MARK: - Activity lifecycle

  override fun onHostResume() {
    isHostActive = true
    // Restore the overlay if it was torn down while the activity was gone but the
    // sheet should still be presented above it.
    if (nativeOverlay && overlayDialog == null) {
      presentOverlay()
    }
    syncPortalRequestCloseHost()
  }

  override fun onHostPause() {
    isHostActive = false
    publishPortalRequestCloseState()
    reconcilePortalBackHandler()
    updateRequestCloseHandling()
  }

  override fun onHostDestroy() {
    isHostActive = false
    removeCallbacks(syncPortalHostRunnable)
    clearPortalRequestCloseHost()
    updateRequestCloseHandling()
    // Dismiss before the activity's window token is destroyed to avoid a leaked
    // window. `nativeOverlay` is left intact so `onHostResume` can restore it;
    // the host falls back to inline parenting in the meantime.
    if (overlayDialog != null) {
      dismissOverlay()
    }
  }

  // MARK: - Cleanup

  fun destroy() {
    isViewAttached = false
    isHostActive = false
    requestCloseHandlerPresent = false
    removeCallbacks(syncPortalHostRunnable)
    clearPortalRequestCloseHost()
    themedReactContext?.removeLifecycleEventListener(this)
    host.interactionListener = null
    host.requestCloseStateChangedListener = null
    val dialog = overlayDialog
    clearOverlayInputHandlers(dialog)
    dialog?.let {
      if (it.isShowing) it.dismiss()
    }
    overlayDialog = null
    overlayRoot = null
    overlayInteractive = null
    overlayFocusable = null
    nativeOverlayEscapeDispatcher.clear()
    host.destroy()
  }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal class BottomSheetViewRequestCloseTestSnapshot(
  val isTargetOpen: Boolean,
  val isPresentationActive: Boolean,
  val overlayDialog: ComponentDialog?,
  val overlayRoot: View?,
  val portalBackDispatcher: OnBackPressedDispatcher?,
  val portalBackCallback: OnBackPressedCallback?,
  val portalPredictiveBackInProgress: Boolean,
  val portalEscapeListener: ViewCompat.OnUnhandledKeyEventListenerCompat?,
  val overlayBackCallback: OnBackPressedCallback?,
  val overlayPredictiveBackInProgress: Boolean,
)

private class BottomSheetDialogRootView(context: ThemedReactContext) :
  ReactViewGroup(context), RootView {

  var eventDispatcher: EventDispatcher? = null

  private val jSTouchDispatcher = JSTouchDispatcher(this)
  @Suppress("DEPRECATION")
  private val jSPointerDispatcher: JSPointerDispatcher? =
    if (ReactFeatureFlags.dispatchPointerEvents) JSPointerDispatcher(this) else null
  private val reactContext: ThemedReactContext
    get() = context as ThemedReactContext

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    val childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY)
    val childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY)
    for (index in 0 until childCount) {
      getChildAt(index).measure(childWidthMeasureSpec, childHeightMeasureSpec)
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    for (index in 0 until childCount) {
      getChildAt(index).layout(0, 0, right - left, bottom - top)
    }
  }

  override fun handleException(t: Throwable) {
    reactContext.reactApplicationContext.handleException(RuntimeException(t))
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    eventDispatcher?.let { dispatcher ->
      jSTouchDispatcher.handleTouchEvent(event, dispatcher, reactContext)
      jSPointerDispatcher?.handleMotionEvent(event, dispatcher, true)
    }
    return super.onInterceptTouchEvent(event)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    eventDispatcher?.let { dispatcher ->
      jSTouchDispatcher.handleTouchEvent(event, dispatcher, reactContext)
      jSPointerDispatcher?.handleMotionEvent(event, dispatcher, false)
    }
    super.onTouchEvent(event)
    return true
  }

  override fun onInterceptHoverEvent(event: MotionEvent): Boolean {
    eventDispatcher?.let { dispatcher ->
      jSPointerDispatcher?.handleMotionEvent(event, dispatcher, true)
    }
    return super.onInterceptHoverEvent(event)
  }

  override fun onHoverEvent(event: MotionEvent): Boolean {
    eventDispatcher?.let { dispatcher ->
      jSPointerDispatcher?.handleMotionEvent(event, dispatcher, false)
    }
    return super.onHoverEvent(event)
  }

  override fun onChildStartedNativeGesture(childView: View?, ev: MotionEvent) {
    eventDispatcher?.let { dispatcher ->
      // Sweeps the active touch marked by handleTouchEvent when a native child takes over the
      // gesture. The 3-arg overload that performs the sweep only exists on RN 0.82+, so it is
      // resolved through a compat shim (see JSTouchDispatcherCompat) that uses it when present
      // and falls back to the 2-arg overload on RN 0.76-0.81 (issue #35).
      jSTouchDispatcher.onChildStartedNativeGestureCompat(ev, dispatcher, reactContext)
      jSPointerDispatcher?.onChildStartedNativeGesture(childView, ev, dispatcher)
    }
  }

  override fun onChildEndedNativeGesture(childView: View, ev: MotionEvent) {
    eventDispatcher?.let { dispatcher ->
      jSTouchDispatcher.onChildEndedNativeGesture(ev, dispatcher)
    }
    jSPointerDispatcher?.onChildEndedNativeGesture()
  }

  override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    // Keep receiving root intercept callbacks so JS touch cancellation mirrors React Native Modal.
  }
}
