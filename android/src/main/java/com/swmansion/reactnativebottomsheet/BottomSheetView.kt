@file:Suppress("DEPRECATION")

package com.swmansion.reactnativebottomsheet

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import com.facebook.react.bridge.LifecycleEventListener
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
  // Cached only while a dialog is present, so per-frame interaction callbacks
  // don't thrash the window flags.
  private var overlayInteractive: Boolean? = null

  init {
    pointerEvents = PointerEvents.BOX_NONE
    host.interactionListener = { interactive -> updateOverlayTouchability(interactive) }
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
    }

  var disableScrollableNegotiation: Boolean
    get() = host.disableScrollableNegotiation
    set(value) {
      host.disableScrollableNegotiation = value
    }

  fun setScrimColor(color: Int?) = host.setScrimColor(color)

  fun setScrimOpacities(values: List<Float>) = host.setScrimOpacities(values)

  fun setMaxDetentHeight(maxDetentHeight: Double) = host.setMaxDetentHeight(maxDetentHeight)

  fun setNativeOverlay(value: Boolean) {
    if (value == nativeOverlay) return
    nativeOverlay = value
    if (value) presentOverlay() else dismissOverlay()
  }

  // MARK: - Inline vs overlay presentation

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
    // Stay unopinionated about the system back gesture, exactly like the inline
    // (portal-based) modal, which registers no back handling and leaves it to the
    // consumer. The dialog is a separate focusable window that would otherwise
    // consume the back press and dismiss itself, so instead of acting on it we
    // forward it to the host activity. That runs whatever the consumer wired up
    // (JS `BackHandler`, React Navigation, …), just as a back press would for an
    // inline modal.
    dialog.onBackPressedDispatcher.addCallback(
      dialog,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
        }
      },
    )
    overlayInteractive = null
    overlayRoot = root
    overlayDialog = dialog
    try {
      dialog.show()
      dialog.window?.let { configureOverlayWindow(it, activity) }
    } catch (_: RuntimeException) {
      // Show failed (e.g. the activity went away mid-present). Dismiss so the
      // partially-created window can't leak, then fall back to inline.
      runCatching { if (dialog.isShowing) dialog.dismiss() }
      overlayDialog = null
      overlayRoot = null
      overlayInteractive = null
      nativeOverlay = false
      (host.parent as? ViewGroup)?.removeView(host)
      attachHostInline()
    }
  }

  private fun dismissOverlay() {
    overlayDialog?.let { dialog ->
      (host.parent as? ViewGroup)?.removeView(host)
      if (dialog.isShowing) dialog.dismiss()
    }
    overlayDialog = null
    overlayRoot = null
    overlayInteractive = null
    attachHostInline()
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
    // until the sheet animates open.
    window.addFlags(NON_INTERACTIVE_FLAGS)
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
      attributes =
        attributes.apply {
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

  /**
   * Toggles the overlay window's touchability/focusability with the sheet's interactivity. While
   * the sheet is closed the window is transparent to touch and focus, so the screen behind stays
   * usable; once it animates open or shows its scrim the window captures input (the scrim handles
   * dismissal).
   */
  private fun updateOverlayTouchability(interactive: Boolean) {
    val window = overlayDialog?.window ?: return
    if (interactive == overlayInteractive) return
    overlayInteractive = interactive
    if (interactive) {
      window.clearFlags(NON_INTERACTIVE_FLAGS)
    } else {
      window.addFlags(NON_INTERACTIVE_FLAGS)
    }
  }

  // MARK: - Activity lifecycle

  override fun onHostResume() {
    // Restore the overlay if it was torn down while the activity was gone but the
    // sheet should still be presented above it.
    if (nativeOverlay && overlayDialog == null) {
      presentOverlay()
    }
  }

  override fun onHostPause() {}

  override fun onHostDestroy() {
    // Dismiss before the activity's window token is destroyed to avoid a leaked
    // window. `nativeOverlay` is left intact so `onHostResume` can restore it;
    // the host falls back to inline parenting in the meantime.
    if (overlayDialog != null) {
      dismissOverlay()
    }
  }

  // MARK: - Cleanup

  fun destroy() {
    themedReactContext?.removeLifecycleEventListener(this)
    host.interactionListener = null
    overlayDialog?.let { if (it.isShowing) it.dismiss() }
    overlayDialog = null
    overlayRoot = null
    overlayInteractive = null
    host.destroy()
  }

  private companion object {
    const val NON_INTERACTIVE_FLAGS =
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
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
      // 2-arg JSTouchDispatcher overloads throughout — see onChildStartedNativeGesture below.
      jSTouchDispatcher.handleTouchEvent(event, dispatcher)
      jSPointerDispatcher?.handleMotionEvent(event, dispatcher, true)
    }
    return super.onInterceptTouchEvent(event)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    eventDispatcher?.let { dispatcher ->
      jSTouchDispatcher.handleTouchEvent(event, dispatcher)
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
      // Use the 2-arg JSTouchDispatcher overloads here and in handleTouchEvent. The 3-arg forms
      // opt into Fabric's active-touch tracking: handleTouchEvent marks the touch and this method
      // sweeps it when a native child (e.g. a nested scrollable) takes over. The 3-arg
      // onChildStartedNativeGesture that performs that sweep only exists on RN 0.82+ (issue #35),
      // so to keep mark/sweep consistent and compile across the >=0.76 peer range we opt out of
      // tracking entirely. Once support for RN 0.81 and below is dropped, this call and the
      // handleTouchEvent calls can move back to the 3-arg overloads.
      jSTouchDispatcher.onChildStartedNativeGesture(ev, dispatcher)
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
