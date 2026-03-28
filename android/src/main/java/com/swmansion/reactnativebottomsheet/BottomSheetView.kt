package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.views.view.ReactViewGroup
import kotlin.math.abs

private data class DetentSpec(val height: Float, val programmatic: Boolean)

interface BottomSheetViewListener {
  fun onIndexChange(index: Int)
  fun onPositionChange(position: Double)
}

class BottomSheetView(context: Context) : ReactViewGroup(context) {

  // MARK: - Listener

  var listener: BottomSheetViewListener? = null

  // MARK: - State

  private var detentSpecs: List<DetentSpec> = emptyList()
  private var targetIndex: Int = 0
  var animateIn: Boolean = true
  private var pendingIndex: Int? = null
  private var hasLaidOut = false
  private var isPanning = false

  // MARK: - Internal

  private val sheetContainer = FrameLayout(context)
  private var activeAnimation: SpringAnimation? = null
  private var velocityTracker: VelocityTracker? = null
  private var choreographerCallback: Choreographer.FrameCallback? = null
  private val density = context.resources.displayMetrics.density
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

  // Touch tracking
  private var initialTouchY = 0f
  private var initialTouchX = 0f
  private var lastTouchY = 0f
  private var activePointerId = MotionEvent.INVALID_POINTER_ID

  init {
    clipChildren = false
    clipToPadding = false
    pointerEvents = PointerEvents.BOX_NONE
    sheetContainer.clipChildren = false
    sheetContainer.clipToPadding = false
    super.addView(
      sheetContainer,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
    )
  }

  val sheetChildCount: Int
    get() = sheetContainer.childCount

  fun getSheetChildAt(index: Int): View? = sheetContainer.getChildAt(index)

  fun addSheetChild(child: View, index: Int) {
    sheetContainer.addView(child, index)
  }

  fun removeSheetChildAt(index: Int) {
    sheetContainer.removeViewAt(index)
  }

  // MARK: - Child view management

  override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
    if (child === sheetContainer) {
      super.addView(child, index, params)
    } else {
      sheetContainer.addView(child, index, params)
    }
  }

  override fun removeView(view: View) {
    if (view === sheetContainer) {
      super.removeView(view)
    } else {
      sheetContainer.removeView(view)
    }
  }

  override fun removeViewAt(index: Int) {
    sheetContainer.removeViewAt(index)
  }

  // MARK: - Layout

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    val w = r - l
    val h = b - t
    if (w <= 0 || h <= 0) return

    layoutSheetContainer(w, h)

    if (!hasLaidOut && detentSpecs.isNotEmpty()) {
      hasLaidOut = true
      val indexToApply = pendingIndex ?: targetIndex
      pendingIndex = null
      targetIndex = indexToApply.coerceIn(0, detentSpecs.size - 1)

      if (animateIn) {
        val closedTy = detentSpecs.lastOrNull()?.height ?: h.toFloat()
        sheetContainer.translationY = closedTy
        emitPosition()
        snapToIndex(targetIndex, 0f)
      } else {
        sheetContainer.translationY = translationY(targetIndex)
        emitPosition()
        listener?.onIndexChange(targetIndex)
      }
      return
    }

    if (activeAnimation != null || isPanning) return
    sheetContainer.translationY = translationY(targetIndex)
  }

  private fun layoutSheetChildren() {
    for (i in 0 until sheetContainer.childCount) {
      val child = sheetContainer.getChildAt(i)
      child.layout(0, 0, child.measuredWidth, child.measuredHeight)
    }
  }

  private fun layoutSheetContainer(viewWidth: Int, viewHeight: Int) {
    val maxHeight = detentSpecs.lastOrNull()?.height ?: viewHeight.toFloat()
    val containerTop = (viewHeight - maxHeight).toInt()
    sheetContainer.layout(0, containerTop, viewWidth, containerTop + maxHeight.toInt())
    layoutSheetChildren()
  }

  // MARK: - Prop setters

  fun setDetents(raw: List<Map<String, Any>>) {
    detentSpecs = raw.mapNotNull { dict ->
      val height = (dict["height"] as? Number)?.toDouble() ?: return@mapNotNull null
      val programmatic = dict["programmatic"] as? Boolean ?: false
      DetentSpec(height = (height * density).toFloat(), programmatic = programmatic)
    }

    if (width > 0 && height > 0 && detentSpecs.isNotEmpty()) {
      layoutSheetContainer(width, height)

      if (hasLaidOut && activeAnimation == null && !isPanning) {
        targetIndex = targetIndex.coerceIn(0, detentSpecs.size - 1)
        sheetContainer.translationY = translationY(targetIndex)
        emitPosition()
      }
    }

    requestLayout()
  }

  fun setIndex(newIndex: Int) {
    if (newIndex < 0) return

    if (!hasLaidOut) {
      pendingIndex = newIndex
      targetIndex = newIndex
      return
    }

    if (newIndex >= detentSpecs.size || newIndex == targetIndex) return
    snapToIndex(newIndex, 0f)
  }

  // MARK: - Snap logic

  private fun translationY(index: Int): Float {
    val maxHeight = detentSpecs.lastOrNull()?.height ?: height.toFloat()
    val snapHeight = detentSpecs.getOrNull(index)?.height ?: 0f
    return maxHeight - snapHeight
  }

  private val draggableMinTy: Float
    get() {
      val highestIndex = detentSpecs.indices.lastOrNull { !detentSpecs[it].programmatic } ?: 0
      return translationY(highestIndex)
    }

  private val draggableMaxTy: Float
    get() {
      val lowestIndex = detentSpecs.indices.firstOrNull { !detentSpecs[it].programmatic } ?: 0
      return translationY(lowestIndex)
    }

  private val isAtMaxDraggable: Boolean
    get() = sheetContainer.translationY <= draggableMinTy + 1f

  private fun emitPosition() {
    val maxHeight = detentSpecs.lastOrNull()?.height ?: height.toFloat()
    val ty = sheetContainer.translationY
    listener?.onPositionChange(((maxHeight - ty) / density).toDouble())
  }

  // MARK: - Choreographer (position tracking during animation)

  private fun startChoreographer() {
    if (choreographerCallback != null) return
    val callback = object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        emitPosition()
        choreographerCallback?.let { Choreographer.getInstance().postFrameCallback(it) }
      }
    }
    choreographerCallback = callback
    Choreographer.getInstance().postFrameCallback(callback)
  }

  private fun stopChoreographer() {
    choreographerCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
    choreographerCallback = null
  }

  // MARK: - Spring animation

  private fun snapToIndex(index: Int, velocity: Float) {
    if (index < 0 || index >= detentSpecs.size) return
    targetIndex = index

    val targetTy = translationY(index)

    activeAnimation?.cancel()

    val spring = SpringAnimation(sheetContainer, DynamicAnimation.TRANSLATION_Y, targetTy).apply {
      spring = SpringForce(targetTy).apply {
        dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        stiffness = SpringForce.STIFFNESS_MEDIUM
      }
      setStartVelocity(velocity)
      addEndListener { _, canceled, _, _ ->
        if (canceled) {
          return@addEndListener
        }
        stopChoreographer()
        emitPosition()
        activeAnimation = null
        listener?.onIndexChange(index)
      }
    }

    activeAnimation = spring
    startChoreographer()
    spring.start()
  }

  private fun bestSnapIndex(currentHeight: Float, velocity: Float): Int {
    val draggable = detentSpecs.withIndex().filter { !it.value.programmatic }
    if (draggable.isEmpty()) return targetIndex

    val flickThreshold = 600f * density

    if (velocity < -flickThreshold) {
      return draggable.firstOrNull { it.value.height > currentHeight }?.index
        ?: draggable.lastOrNull()?.index ?: targetIndex
    }
    if (velocity > flickThreshold) {
      return draggable.lastOrNull { it.value.height < currentHeight }?.index
        ?: draggable.firstOrNull()?.index ?: targetIndex
    }

    return draggable.minByOrNull { abs(it.value.height - currentHeight) }?.index ?: targetIndex
  }

  // MARK: - Touch handling

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    val sheetTop = sheetContainer.top + sheetContainer.translationY
    if (ev.actionMasked == MotionEvent.ACTION_DOWN && ev.y < sheetTop) {
      return false
    }

    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        initialTouchX = ev.x
        initialTouchY = ev.y
        lastTouchY = ev.y
        activePointerId = ev.getPointerId(0)
      }
      MotionEvent.ACTION_MOVE -> {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val pointerIndex = ev.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return false
        val x = ev.getX(pointerIndex)
        val y = ev.getY(pointerIndex)
        val dx = x - initialTouchX
        val dy = y - initialTouchY

        if (abs(dy) > touchSlop && abs(dy) > abs(dx)) {
          if (!isAtMaxDraggable) {
            lastTouchY = y
            requestDisallowInterceptTouchEvent(false)
            return true
          }
          if (dy > 0 && isScrollViewAtTop()) {
            lastTouchY = y
            requestDisallowInterceptTouchEvent(false)
            return true
          }
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        initialTouchX = 0f
        activePointerId = MotionEvent.INVALID_POINTER_ID
      }
    }
    return false
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val sheetTop = sheetContainer.top + sheetContainer.translationY
    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y < sheetTop) {
      return false
    }

    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        beginPan(event)
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (!isPanning) beginPan(event)
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return true
        val y = event.getY(pointerIndex)
        velocityTracker?.addMovement(event)
        val dy = y - lastTouchY
        lastTouchY = y

        val newTy = (sheetContainer.translationY + dy).coerceIn(draggableMinTy, draggableMaxTy)
        sheetContainer.translationY = newTy
        emitPosition()
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        isPanning = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        val velocity = velocityTracker?.let { tracker ->
          tracker.computeCurrentVelocity(1000)
          val v = tracker.yVelocity
          tracker.recycle()
          v
        } ?: 0f
        velocityTracker = null
        val maxHeight = detentSpecs.lastOrNull()?.height ?: height.toFloat()
        val currentHeight = maxHeight - sheetContainer.translationY
        val index = bestSnapIndex(currentHeight, velocity)
        snapToIndex(index, velocity)
        return true
      }
      MotionEvent.ACTION_POINTER_UP -> {
        val actionIndex = event.actionIndex
        if (event.getPointerId(actionIndex) == activePointerId) {
          val newIndex = if (actionIndex == 0) 1 else 0
          activePointerId = event.getPointerId(newIndex)
          lastTouchY = event.getY(newIndex)
        }
        return true
      }
    }
    return super.onTouchEvent(event)
  }

  private fun beginPan(event: MotionEvent) {
    isPanning = true
    activePointerId = event.getPointerId(0)
    lastTouchY = event.y
    velocityTracker?.recycle()
    velocityTracker = VelocityTracker.obtain()
    velocityTracker?.addMovement(event)
    activeAnimation?.let {
      it.cancel()
      activeAnimation = null
      stopChoreographer()
    }
  }

  // MARK: - Scroll view helpers

  private fun isScrollViewAtTop(): Boolean {
    val scrollView = findScrollView(sheetContainer) ?: return true
    return !scrollView.canScrollVertically(-1)
  }

  private fun findScrollView(view: View): View? {
    if (view.canScrollVertically(1) || view.canScrollVertically(-1)) return view
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        findScrollView(view.getChildAt(i))?.let { return it }
      }
    }
    return null
  }

  // MARK: - Cleanup

  fun destroy() {
    activeAnimation?.cancel()
    activeAnimation = null
    stopChoreographer()
    velocityTracker?.recycle()
    velocityTracker = null
    detentSpecs = emptyList()
    targetIndex = 0
    pendingIndex = null
    hasLaidOut = false
    isPanning = false
    initialTouchY = 0f
    initialTouchX = 0f
    lastTouchY = 0f
    activePointerId = MotionEvent.INVALID_POINTER_ID
    sheetContainer.translationY = 0f
    sheetContainer.removeAllViews()
  }
}
