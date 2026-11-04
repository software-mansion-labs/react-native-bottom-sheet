package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** An outset shadow of the card, as `boxShadow` describes it, in px. */
internal data class CardShadow(
  val offsetX: Float,
  val offsetY: Float,
  val blurRadius: Float,
  val spreadDistance: Float,
  val color: Int,
)

/**
 * Draws the shadows of a card `shapeHeight` tall, spanning the view's width, with its top at the
 * view's top. It sits below the clipping container, so the clip never cuts the shadows.
 *
 * Blurred shadows are expensive to draw, so the host keeps `shapeHeight` fixed while the sheet
 * moves and stitches the card's shadow from two of these views instead: one for the card's top half
 * and one, translated, for its bottom half. Translation and clip bounds are RenderNode properties,
 * so a moving card does not redraw the shadows.
 *
 * Drawn the way React Native draws `boxShadow`: the shadow's shape, blurred, with the card clipped
 * out of it. Hardware-accelerated blurs on shapes need API 28, so earlier versions draw no shadow.
 */
internal class BottomSheetCardShadowView(context: Context) : View(context) {
  private val cornerRadii = FloatArray(4)
  private var shadows: List<CardShadow> = emptyList()
  private var blurFilters: List<BlurMaskFilter?> = emptyList()
  private var shapeHeight = 0f
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val path = Path()
  private val shapeRect = RectF()
  private val pathRadii = FloatArray(8)
  private val clip = Rect()

  init {
    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
  }

  val hasShadows: Boolean
    get() = shadows.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

  /**
   * How far from the card's top and bottom edges its shape affects the shadow. Between these bands
   * the card's sides are straight, so the shadow there is the same for any card height, and two
   * copies can be joined anywhere in it without a seam.
   */
  var edgeBand = 1f
    private set

  private fun updateEdgeBand() {
    // Past three sigma (1.5 blur radii) a Gaussian blur adds nothing visible.
    val reach = shadows.maxOfOrNull {
      1.5f * it.blurRadius + abs(it.spreadDistance) + abs(it.offsetY)
    }
    edgeBand = (reach ?: 0f) + (cornerRadii.maxOrNull() ?: 0f) + 1f
  }

  fun setShadows(value: List<CardShadow>) {
    if (value == shadows) return
    shadows = value
    blurFilters = value.map(::blurFilter)
    updateEdgeBand()
    invalidate()
  }

  fun setCornerRadii(radii: FloatArray) {
    if (radii.contentEquals(cornerRadii)) return
    radii.copyInto(cornerRadii)
    updateEdgeBand()
    invalidate()
  }

  /**
   * Draws the card's shadows `shapeHeight` tall, moved down by `offsetY` and shown only between
   * `clipTop` and `clipBottom`, all in the parent's coordinates. Only a new `shapeHeight` redraws.
   */
  fun place(shapeHeight: Float, offsetY: Float, clipTop: Float, clipBottom: Float) {
    if (shapeHeight != this.shapeHeight) {
      this.shapeHeight = shapeHeight
      invalidate()
    }
    translationY = offsetY
    // Unbounded sideways and outward, so the clip only joins the two halves.
    val reach = (width + height) * 2
    clip.set(
      -reach,
      if (clipTop.isInfinite()) -reach else floor(clipTop - offsetY).toInt(),
      width + reach,
      if (clipBottom.isInfinite()) height + reach else ceil(clipBottom - offsetY).toInt(),
    )
    clipBounds = clip
  }

  override fun onDraw(canvas: Canvas) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    if (shadows.isEmpty() || shapeHeight <= 0f || width <= 0) return
    canvas.save()
    // An outset shadow never paints inside the card, which may be translucent.
    if (buildPath(-CLIP_INSET)) canvas.clipOutPath(path)
    // The first listed shadow goes on top, so it is drawn last.
    for (index in shadows.lastIndex downTo 0) {
      val shadow = shadows[index]
      if (!buildPath(shadow.spreadDistance)) continue
      paint.color = shadow.color
      paint.maskFilter = blurFilters[index]
      canvas.save()
      canvas.translate(shadow.offsetX, shadow.offsetY)
      canvas.drawPath(path, paint)
      canvas.restore()
    }
    canvas.restore()
  }

  /**
   * The card's outline grown by `outset`, as a `boxShadow` spread does. Returns false if a negative
   * spread leaves no shape, which then casts no shadow.
   */
  private fun buildPath(outset: Float): Boolean {
    shapeRect.set(0f, 0f, width.toFloat(), shapeHeight)
    shapeRect.inset(-outset, -outset)
    if (shapeRect.width() <= 0f || shapeRect.height() <= 0f) return false
    val limit = minOf(shapeRect.width(), shapeRect.height()) / 2f
    for (corner in 0 until 4) {
      val base = cornerRadii[corner]
      val radius = if (base > 0f) (base + outset).coerceIn(0f, limit) else 0f
      pathRadii[corner * 2] = radius
      pathRadii[corner * 2 + 1] = radius
    }
    path.reset()
    path.addRoundRect(shapeRect, pathRadii, Path.Direction.CW)
    return true
  }
}

/**
 * Keeps antialiased clip and shadow edges from leaving a gap: only visible without a background.
 */
private const val CLIP_INSET = 0.4f

/**
 * CSS blur radius is twice the Gaussian sigma; Skia derives its sigma from a mask filter's radius
 * as 0.57735 * radius + 0.5.
 */
private fun blurFilter(shadow: CardShadow): BlurMaskFilter? {
  val sigma = shadow.blurRadius / 2f
  if (sigma <= 0.5f) return null
  return BlurMaskFilter((sigma - 0.5f) / 0.57735f, BlurMaskFilter.Blur.NORMAL)
}
