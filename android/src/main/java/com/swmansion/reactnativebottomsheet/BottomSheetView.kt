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
  private var overlayFallbackBackCallback: OnBackPressedCallback? = null
  private var overlayRequestCloseBackCallback: OnBackPressedCallback? = null

  private var requestCloseEnabled = false
  private var requestCloseEligible = false
  private var isViewAttached = false
  private var isHostActive = themedReactContext?.lifecycleState == LifecycleState.RESUMED

  private var portalRequestCloseActivity: ComponentActivity? = null
  private var portalBackCallback: OnBackPressedCallback? = null
  private var portalEscapeWindowRegistration: PortalEscapeWindowCallbackRegistration? = null
  private val escapeRequestCloseDispatcher = EscapeRequestCloseDispatcher()
  private val portalLifecycleObserver = LifecycleEventObserver { owner, event ->
    if (event == Lifecycle.Event.ON_DESTROY && owner === portalRequestCloseActivity) {
      clearPortalRequestCloseActivity()
    }
    updateRequestCloseHandling()
  }
  private val portalEscapeWindowListener = { event: KeyEvent ->
    dispatchPortalEscape(event)
  }
  private val syncPortalActivityRunnable = Runnable { syncPortalRequestCloseActivity() }

  init {
    pointerEvents = PointerEvents.BOX_NONE
    host.interactionListener = { interactive -> updateOverlayTouchability(interactive) }
    host.requestCloseTargetChangedListener = ::updateRequestCloseHandling
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
      host.modal = value
      updateRequestCloseHandling()
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

  fun setRequestCloseEnabled(value: Boolean) {
    if (value == requestCloseEnabled) return
    requestCloseEnabled = value
    updateRequestCloseHandling()
  }

  fun setNativeOverlay(value: Boolean) {
    if (value == nativeOverlay) {
      if (!value && overlayPresentationFailed) {
        overlayPresentationFailed = false
        updateRequestCloseHandling()
      }
      return
    }
    nativeOverlay = value
    overlayPresentationFailed = false
    if (value) presentOverlay() else dismissOverlay()
    syncPortalRequestCloseActivity()
    updateRequestCloseHandling()
  }

  // MARK: - Inline vs overlay presentation

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isViewAttached = true
    syncPortalRequestCloseActivity()
    post(syncPortalActivityRunnable)
    updateRequestCloseHandling()
    overlayDialog?.let { dialog ->
      installOverlayInputHandlers(dialog)
    }
    if (nativeOverlay && overlayDialog == null) {
      presentOverlay()
    }
  }

  override fun onDetachedFromWindow() {
    isViewAttached = false
    removeCallbacks(syncPortalActivityRunnable)
    clearPortalRequestCloseActivity()
    clearOverlayInputHandlers(overlayDialog)
    escapeRequestCloseDispatcher.clear()
    updateRequestCloseHandling()
    super.onDetachedFromWindow()
  }

  override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    super.onWindowFocusChanged(hasWindowFocus)
    syncPortalRequestCloseActivity()
    updateRequestCloseHandling()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (!nativeOverlay && dispatchPortalEscape(event)) return true
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
    updateOverlayWindowInputFlags()
  }

  private fun Window.setOverlayWindowAlpha(interactive: Boolean) {
    attributes = attributes.apply { alpha = if (interactive) 1f else 0f }
  }

  // MARK: - Request close

  /**
   * Emits a controlled request only. Native input handling must never dismiss the dialog, select a
   * detent, or mutate the sheet's target.
   */
  fun emitRequestCloseIfEligible(): Boolean {
    if (!isRequestCloseEligible(requestCloseEligibilityState())) {
      updateRequestCloseHandling()
      return false
    }
    val currentListener = listener ?: return false
    currentListener.onRequestClose()
    return true
  }

  private fun updateRequestCloseHandling() {
    val state = requestCloseEligibilityState()
    requestCloseEligible = isRequestCloseEligible(state)
    updatePortalBackHandler()
    updatePortalEscapeWindowListener()
    overlayFallbackBackCallback?.isEnabled = overlayOwnsCloseInput()
    overlayRequestCloseBackCallback?.isEnabled = requestCloseEligible
    updateOverlayWindowInputFlags()
  }

  /**
   * A native overlay owns close input for its whole visible/interactive lifetime. This is
   * deliberately broader than request-close eligibility: an omitted handler still gives the overlay
   * Modal-like ownership, and a closing animation must not expose the Activity below it.
   */
  private fun overlayOwnsCloseInput(): Boolean =
    nativeOverlay &&
      isViewAttached &&
      isHostActive &&
      overlayDialog?.isShowing == true &&
      (host.isRequestCloseTargetOpen || overlayInteractive == true)

  private fun requestCloseEligibilityState(): RequestCloseEligibility {
    val presentationAttached =
      if (nativeOverlay) {
        overlayDialog?.isShowing == true
      } else {
        !overlayPresentationFailed
      }
    val lifecycleOwner =
      if (nativeOverlay) {
        overlayDialog
      } else {
        portalRequestCloseActivity
      }
    val lifecycleActive =
      lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true

    return RequestCloseEligibility(
      isAttached = isViewAttached && presentationAttached,
      isActive = isHostActive && lifecycleActive,
      isModal = modal,
      isEnabled = requestCloseEnabled,
      isTargetOpen = host.isRequestCloseTargetOpen,
    )
  }

  private fun syncPortalRequestCloseActivity() {
    val activity =
      (themedReactContext?.currentActivity as? ComponentActivity)?.takeIf {
        isViewAttached &&
          !nativeOverlay &&
          !it.isFinishing &&
          !it.isDestroyed &&
          it.lifecycle.currentState != Lifecycle.State.DESTROYED
      }
    if (activity !== portalRequestCloseActivity) {
      removePortalBackHandler()
      removePortalEscapeWindowListener()
      portalRequestCloseActivity?.lifecycle?.removeObserver(portalLifecycleObserver)
      portalRequestCloseActivity = activity
      activity?.lifecycle?.addObserver(portalLifecycleObserver)
    }
    updateRequestCloseHandling()
  }

  private fun clearPortalRequestCloseActivity() {
    removePortalBackHandler()
    removePortalEscapeWindowListener()
    portalRequestCloseActivity?.lifecycle?.removeObserver(portalLifecycleObserver)
    portalRequestCloseActivity = null
  }

  /**
   * Portal Back must be registered with the Activity dispatcher. On target SDK 36 a committed
   * predictive Back can go straight to this dispatcher without producing React Native's
   * `hardwareBackPress` event or a hierarchy key event. Register only while fully eligible so this
   * callback is newer than the screen/navigation callbacks it temporarily supersedes.
   */
  private fun updatePortalBackHandler() {
    val activity = portalRequestCloseActivity
    val required = !nativeOverlay && !overlayPresentationFailed && requestCloseEligible
    if (required && activity != null) {
      if (portalBackCallback != null) return
      val callback =
        object : OnBackPressedCallback(true) {
          override fun handleOnBackPressed() {
            // Eligibility can change after this callback was selected for a Back gesture. The
            // committed gesture is still ours, but must become a no-op instead of being
            // re-dispatched and disrupting predictive Back.
            emitRequestCloseIfEligible()
          }
        }
      portalBackCallback = callback
      activity.onBackPressedDispatcher.addCallback(activity, callback)
      return
    }
    removePortalBackHandler()
  }

  private fun removePortalBackHandler() {
    val callback = portalBackCallback ?: return
    portalBackCallback = null
    callback.remove()
  }

  private fun updatePortalEscapeWindowListener() {
    val required =
      !nativeOverlay &&
        !overlayPresentationFailed &&
        (requestCloseEligible || escapeRequestCloseDispatcher.hasCapturedPress)
    val window = portalRequestCloseActivity?.window
    if (required && window != null) {
      val currentRegistration = portalEscapeWindowRegistration
      if (currentRegistration?.window === window) return
      removePortalEscapeWindowListener()
      portalEscapeWindowRegistration =
        PortalEscapeWindowCallbackRegistry.register(window, portalEscapeWindowListener)
      return
    }
    removePortalEscapeWindowListener()
  }

  private fun removePortalEscapeWindowListener() {
    portalEscapeWindowRegistration?.remove()
    portalEscapeWindowRegistration = null
    // The portal and dialog share the sequence handler. Removing a stale portal registration
    // while a native overlay is active must not abandon a press owned by the dialog window.
    if (!nativeOverlay) {
      escapeRequestCloseDispatcher.clear()
    }
  }

  private fun dispatchPortalEscape(event: KeyEvent): Boolean {
    if (nativeOverlay || overlayPresentationFailed) return false
    return dispatchEscape(event)
  }

  private fun dispatchEscape(event: KeyEvent): Boolean {
    val hadCapturedPress = escapeRequestCloseDispatcher.hasCapturedPress
    val handled =
      escapeRequestCloseDispatcher.dispatch(
        eventToken = requestCloseKeyEventToken(event),
        pressToken = requestCloseKeyPressToken(event),
        keyCode = event.keyCode,
        action = event.action,
        repeatCount = event.repeatCount,
        hasModifiers = !event.hasNoModifiers(),
        isCanceled = event.isCanceled,
        shouldCapturePress = {
          if (nativeOverlay) overlayOwnsCloseInput()
          else isRequestCloseEligible(requestCloseEligibilityState())
        },
        isRequestCloseEligible = {
          isRequestCloseEligible(requestCloseEligibilityState())
        },
        emitRequestCloseIfEligible = ::emitRequestCloseIfEligible,
      )

    if (hadCapturedPress && !escapeRequestCloseDispatcher.hasCapturedPress) {
      updatePortalEscapeWindowListener()
      updateOverlayWindowInputFlags()
    }
    return handled
  }

  private fun requestCloseKeyEventToken(event: KeyEvent) =
    RequestCloseKeyEventToken(
      downTime = event.downTime,
      eventTime = event.eventTime,
      deviceId = event.deviceId,
      source = event.source,
      keyCode = event.keyCode,
      scanCode = event.scanCode,
      action = event.action,
      repeatCount = event.repeatCount,
      metaState = event.metaState,
      flags = event.flags,
    )

  private fun requestCloseKeyPressToken(event: KeyEvent) =
    RequestCloseKeyPressToken(
      downTime = event.downTime,
      deviceId = event.deviceId,
      source = event.source,
      keyCode = event.keyCode,
      scanCode = event.scanCode,
    )

  /**
   * Touchability stays coupled to the existing interaction/scrim state. Keyboard focusability is
   * independently enabled for an eligible request, including when the configured scrim opacity is
   * zero. A captured Escape keeps focus until its terminal up. Returning false for every non-Escape
   * event lets focused inputs keep normal key routing.
   */
  private fun updateOverlayWindowInputFlags() {
    val window = overlayDialog?.window ?: return
    val touchable = overlayInteractive == true
    val ownsCloseInput = overlayOwnsCloseInput()
    val focusable = touchable || ownsCloseInput || escapeRequestCloseDispatcher.hasCapturedPress

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
    overlayFallbackBackCallback?.remove()
    overlayFallbackBackCallback = null
    overlayRequestCloseBackCallback?.remove()
    overlayRequestCloseBackCallback = null
    escapeRequestCloseDispatcher.clear()
  }

  private fun installOverlayInputHandlers(dialog: ComponentDialog) {
    clearOverlayInputHandlers(dialog)
    val fallbackBackCallback =
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          // The native overlay owns Back while it is visible or animating. This callback is the
          // consuming fallback for overlays without a request handler and for requests that are
          // no longer eligible while the sheet finishes closing.
        }
      }
    overlayFallbackBackCallback = fallbackBackCallback
    dialog.onBackPressedDispatcher.addCallback(dialog, fallbackBackCallback)

    val requestCloseBackCallback =
      object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
          // A focusable dialog owns Back. If eligibility changed after the gesture began, keep
          // the non-cancelable overlay open and finish handling as a no-op.
          emitRequestCloseIfEligible()
        }
      }
    overlayRequestCloseBackCallback = requestCloseBackCallback
    dialog.onBackPressedDispatcher.addCallback(dialog, requestCloseBackCallback)
    fallbackBackCallback.isEnabled = overlayOwnsCloseInput()
    requestCloseBackCallback.isEnabled = requestCloseEligible
    dialog.setOnKeyListener(DialogInterface.OnKeyListener { _, _, event -> dispatchEscape(event) })
  }

  // MARK: - Activity lifecycle

  override fun onHostResume() {
    isHostActive = true
    // Restore the overlay if it was torn down while the activity was gone but the
    // sheet should still be presented above it.
    if (nativeOverlay && overlayDialog == null) {
      presentOverlay()
    }
    syncPortalRequestCloseActivity()
    updateRequestCloseHandling()
  }

  override fun onHostPause() {
    isHostActive = false
    updateRequestCloseHandling()
  }

  override fun onHostDestroy() {
    isHostActive = false
    removeCallbacks(syncPortalActivityRunnable)
    clearPortalRequestCloseActivity()
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
    requestCloseEnabled = false
    removeCallbacks(syncPortalActivityRunnable)
    clearPortalRequestCloseActivity()
    themedReactContext?.removeLifecycleEventListener(this)
    host.interactionListener = null
    host.requestCloseTargetChangedListener = null
    val dialog = overlayDialog
    clearOverlayInputHandlers(dialog)
    dialog?.let {
      if (it.isShowing) it.dismiss()
    }
    overlayDialog = null
    overlayRoot = null
    overlayInteractive = null
    overlayFocusable = null
    escapeRequestCloseDispatcher.clear()
    host.destroy()
  }
}

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
