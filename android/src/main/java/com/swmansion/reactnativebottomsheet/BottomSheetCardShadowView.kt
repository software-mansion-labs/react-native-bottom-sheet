package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View

/** An outset shadow of the card, as `boxShadow` describes it, in px. */
internal data class CardShadow(
  val offsetX: Float,
  val offsetY: Float,
  val blurRadius: Float,
  val spreadDistance: Float,
  val color: Int,
)

/**
 * Casts the card's shadows. It sits below the clipping container, so the clip never cuts the
 * shadows, and redraws (RenderNode work, never a relayout) as the card changes.
 *
 * Only the shadow is drawn, not the shape that casts it: the shape is drawn far outside the view
 * and its shadow layer offset back onto the card, the same way React Native draws `boxShadow`.
 * Hardware-accelerated shadow layers on shapes need API 28, so earlier versions draw no shadow.
 */
internal class BottomSheetCardShadowView(context: Context) : View(context) {
  private val cardRect = RectF()
  private val cornerRadii = FloatArray(4)
  private var shadows: List<CardShadow> = emptyList()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val path = Path()
  private val shapeRect = RectF()
  private val pathRadii = FloatArray(8)

  init {
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  fun setShadows(value: List<CardShadow>) {
    if (value == shadows) return
    shadows = value
    invalidate()
  }

  fun setCornerRadii(radii: FloatArray) {
    if (radii.contentEquals(cornerRadii)) return
    radii.copyInto(cornerRadii)
    invalidate()
  }

  fun setCard(left: Float, top: Float, right: Float, bottom: Float) {
    if (
      cardRect.left == left &&
        cardRect.top == top &&
        cardRect.right == right &&
        cardRect.bottom == bottom
    ) {
      return
    }
    cardRect.set(left, top, right, bottom)
    if (shadows.isNotEmpty()) invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || cardRect.isEmpty) return
    // Far enough that the shape itself never lands on screen.
    val shapeOffset = (width + height).toFloat() * 2f
    for (shadow in shadows) {
      // CSS blur radius is twice the Gaussian sigma; Skia derives its sigma
      // from a shadow-layer radius as 0.57735 * radius + 0.5.
      val sigma = shadow.blurRadius / 2f
      val radius = if (sigma > 0.5f) (sigma - 0.5f) / 0.57735f else 0f
      paint.color = shadow.color
      paint.setShadowLayer(
        radius.coerceAtLeast(Float.MIN_VALUE),
        shapeOffset + shadow.offsetX,
        shadow.offsetY,
        shadow.color,
      )
      buildPath(shadow.spreadDistance)
      canvas.save()
      canvas.translate(-shapeOffset, 0f)
      canvas.drawPath(path, paint)
      canvas.restore()
    }
  }

  /** The card's outline grown by `outset`, as a `boxShadow` spread does. */
  private fun buildPath(outset: Float) {
    shapeRect.set(cardRect)
    shapeRect.inset(-outset, -outset)
    val limit = (minOf(shapeRect.width(), shapeRect.height()) / 2f).coerceAtLeast(0f)
    for (corner in 0 until 4) {
      val base = cornerRadii[corner]
      val radius = if (base > 0f) (base + outset).coerceIn(0f, limit) else 0f
      pathRadii[corner * 2] = radius
      pathRadii[corner * 2 + 1] = radius
    }
    path.reset()
    path.addRoundRect(shapeRect, pathRadii, Path.Direction.CW)
  }
}
