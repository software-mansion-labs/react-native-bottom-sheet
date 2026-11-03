package com.swmansion.reactnativebottomsheet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.facebook.react.uimanager.PointerEvents
import com.facebook.react.uimanager.ReactPointerEventsView

/**
 * Hosts the sheet's surface and content and clips them to the card shape.
 *
 * The layout keeps the sheet container's full size, so its children keep their coordinates; only
 * the clip shape follows the card. Updating the shape is RenderNode work (an outline, or a canvas
 * clip), never a relayout of the Fabric-managed children.
 */
internal class BottomSheetCardClipLayout(context: Context) :
  FrameLayout(context), ReactPointerEventsView {
  /** The card in this view's coordinates. */
  private val cardRect = RectF()

  /** Physical corner radii in px: top-left, top-right, bottom-right, bottom-left. */
  private val cornerRadii = FloatArray(4)
  private val clipPath = Path()
  private val pathRadii = FloatArray(8)

  private enum class ClipMode {
    /** No corner is rounded: the sheet stays unclipped. */
    NONE,

    /** Every corner shares one radius: an outline round rect. */
    UNIFORM,

    /**
     * Only the top corners are rounded, sharing one radius: an outline round rect extended below
     * the card by that radius, cut back to the card with a rect clip. Keeps the outline's
     * antialiased corners on every API level.
     */
    TOP,

    /** Any other radii on API 33+, where outlines clip to arbitrary paths. */
    OUTLINE_PATH,

    /** Any other radii before API 33: a canvas path clip, without antialiasing. */
    CANVAS_PATH,
  }

  private var clipMode = ClipMode.NONE

  override val pointerEvents: PointerEvents = PointerEvents.BOX_NONE

  init {
    clipChildren = false
    clipToPadding = false
    outlineProvider =
      object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
          val left = cardRect.left.toInt()
          val top = cardRect.top.toInt()
          val right = cardRect.right.toInt()
          val bottom = cardRect.bottom.toInt()
          when (clipMode) {
            ClipMode.UNIFORM -> outline.setRoundRect(left, top, right, bottom, cornerRadii[0])
            ClipMode.TOP -> {
              val radius = cornerRadii[0]
              outline.setRoundRect(left, top, right, bottom + radius.toInt() + 1, radius)
            }
            ClipMode.OUTLINE_PATH ->
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                outline.setPath(buildCardPath())
              }
            ClipMode.NONE,
            ClipMode.CANVAS_PATH -> outline.setRect(left, top, right, bottom)
          }
        }
      }
  }

  val isShaped: Boolean
    get() = cornerRadii.any { it > 0f }

  fun setCornerRadii(radii: FloatArray) {
    if (radii.contentEquals(cornerRadii)) return
    radii.copyInto(cornerRadii)
    val (topLeft, topRight, bottomRight, bottomLeft) = cornerRadii.toList()
    // All-zero radii leave the sheet unclipped, as before cards existed, so
    // content drawn outside the sheet stays visible.
    clipMode =
      when {
        !isShaped -> ClipMode.NONE
        cornerRadii.all { it == topLeft } -> ClipMode.UNIFORM
        topLeft == topRight && bottomRight == 0f && bottomLeft == 0f -> ClipMode.TOP
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ClipMode.OUTLINE_PATH
        else -> ClipMode.CANVAS_PATH
      }
    clipToOutline = clipMode != ClipMode.NONE && clipMode != ClipMode.CANVAS_PATH
    invalidateOutline()
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
    if (clipToOutline) invalidateOutline()
    if (clipMode == ClipMode.TOP || clipMode == ClipMode.CANVAS_PATH) invalidate()
  }

  /** Whether `(x, y)`, in this view's coordinates, falls on the card shape. */
  fun cardContains(x: Float, y: Float): Boolean {
    if (!cardRect.contains(x, y)) return false
    if (!isShaped) return true
    val path = buildCardPath()
    val bounds = RectF()
    path.computeBounds(bounds, true)
    val region = android.graphics.Region()
    region.setPath(
      path,
      android.graphics.Region(
        bounds.left.toInt(),
        bounds.top.toInt(),
        bounds.right.toInt(),
        bounds.bottom.toInt(),
      ),
    )
    return region.contains(x.toInt(), y.toInt())
  }

  override fun dispatchDraw(canvas: Canvas) {
    val saveCount = canvas.save()
    when (clipMode) {
      ClipMode.TOP -> canvas.clipRect(cardRect)
      ClipMode.CANVAS_PATH -> canvas.clipPath(buildCardPath())
      else -> {}
    }
    super.dispatchDraw(canvas)
    canvas.restoreToCount(saveCount)
  }

  private fun buildCardPath(): Path {
    val limit = minOf(cardRect.width(), cardRect.height()) / 2f
    for (corner in 0 until 4) {
      val radius = cornerRadii[corner].coerceAtMost(limit)
      pathRadii[corner * 2] = radius
      pathRadii[corner * 2 + 1] = radius
    }
    clipPath.reset()
    clipPath.addRoundRect(cardRect, pathRadii, Path.Direction.CW)
    return clipPath
  }
}
