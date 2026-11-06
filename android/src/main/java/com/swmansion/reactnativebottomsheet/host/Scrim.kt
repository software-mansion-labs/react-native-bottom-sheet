package com.swmansion.reactnativebottomsheet.host

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** The scrim the host draws behind a modal sheet, and the sheet's visibility when closed. */
internal class Scrim(private val state: SheetState) {
  private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var scrimColor = Color.TRANSPARENT
  // Scrim opacity per detent index, from each detent’s `scrimOpacity`. Kept apart from
  // `rawDetentSpecs`, so a scrim change only repaints the scrim.
  var scrimOpacities = emptyList<Float>()
  private var scrimProgress = 0f

  fun setScrimColor(color: Int?) {
    scrimColor = color ?: Color.TRANSPARENT
    state.hostView.invalidate()
  }

  fun clearScrimProgress() {
    scrimProgress = 0f
  }

  fun updateScrim(position: Float = state.currentSheetHeight()) {
    if (!state.modal) {
      scrimProgress = 0f
      state.hostView.invalidate()
      return
    }

    // When settled at the closed detent, dynamic content updates can briefly
    // produce stale non-zero positions. Keep scrim hidden in this state.
    if (
      (state.isTargetingClosedDetent && state.activeAnimation == null && !state.isPanning) ||
        (state.suppressScrimForClosingTarget && state.isTargetingClosedDetent)
    ) {
      hideScrim()
      return
    }

    // While the sheet is fully open and only its content/detent geometry is
    // resizing, the position momentarily lags the grown detent height. Keep the
    // scrim pinned to the fully-open opacity instead of dipping it until the
    // re-anchor settles.
    if (state.scrimPinnedFull) {
      scrimProgress = fullyOpenScrimOpacity()
      state.hostView.invalidate()
      return
    }

    scrimProgress = scrimOpacityAt(position)
    state.hostView.invalidate()
  }

  /** The opacity at the tallest detent, held while the sheet re-anchors. */
  private fun fullyOpenScrimOpacity(): Float {
    val maxPosition = state.detentSpecs.maxOfOrNull { it.position } ?: return 1f
    return scrimOpacityAt(maxPosition)
  }

  /**
   * Interpolates the scrim opacity for a sheet height by bracketing it between adjacent detent
   * heights and lerping each detent index's configured value.
   */
  private fun scrimOpacityAt(position: Float): Float =
    state.interpolateAtPosition(
      position,
      state.detentSpecs.indices.map {
        (scrimOpacities.getOrNull(it) ?: 1f).coerceIn(0f, 1f)
      },
    )

  fun hideScrim() {
    scrimProgress = 0f
    state.hostView.invalidate()
  }

  fun updateSheetVisibility(position: Float) {
    state.sheetContainer.alpha = if (position <= 0.5f) 0f else 1f
  }

  fun isScrimVisible(): Boolean = state.modal && scrimProgress > 0.001f

  fun drawScrim(canvas: Canvas) {
    if (!state.modal || scrimProgress <= 0.001f) {
      return
    }

    val alpha = (Color.alpha(scrimColor) * scrimProgress).toInt().coerceIn(0, 255)
    scrimPaint.color =
      Color.argb(alpha, Color.red(scrimColor), Color.green(scrimColor), Color.blue(scrimColor))
    canvas.drawRect(0f, 0f, state.width.toFloat(), state.height.toFloat(), scrimPaint)
  }
}
