package com.swmansion.reactnativebottomsheet.closerequest

/** Current presentation facts supplied by the shared membership authority. */
internal data class InputContextCandidate<Identity>(
  val identity: Identity,
  val isActive: Boolean,
)

/**
 * The adapter's current focus classification in the input window. Unknown mapping is not absence.
 * For [Inside], [Inside.containingContexts] runs from the innermost context to its enclosing ones.
 * These are validated membership facts, not contexts inferred from root views or screen positions.
 */
internal sealed interface CurrentInputFocus<out Identity> {
  data class Inside<Identity>(val containingContexts: List<InputContextCandidate<Identity>>) :
    CurrentInputFocus<Identity>

  data object KnownOutside : CurrentInputFocus<Nothing>

  data object Absent : CurrentInputFocus<Nothing>

  data object Unmappable : CurrentInputFocus<Nothing>
}

/** Selection precedes local Top resolution, action eligibility and captured input execution. */
internal sealed interface ActiveInputContext<out Identity> {
  data class Portal<Identity>(val identity: Identity) : ActiveInputContext<Identity>

  data object WindowModal : ActiveInputContext<Nothing>

  data object Host : ActiveInputContext<Nothing>

  /** Requests the existing window input owner policy; it does not assign Top or widen isolation. */
  data object WindowFallback : ActiveInputContext<Nothing>
}

/**
 * Selects a fresh Android Back/physical Escape context from current, validated per-window facts.
 * The foreground window-modal flag describes an Active library nativeOverlay. Native adapters
 * supply focus and containment; this policy neither discovers them nor selects a sheet's Top.
 */
internal fun <Identity> selectActiveInputContext(
  accessibilityFocus: CurrentInputFocus<Identity>,
  keyboardFocus: CurrentInputFocus<Identity>,
  hasForegroundWindowModalPresentation: Boolean,
): ActiveInputContext<Identity> {
  if (hasForegroundWindowModalPresentation) return ActiveInputContext.WindowModal
  val selectedFocus =
    if (accessibilityFocus == CurrentInputFocus.Absent) keyboardFocus else accessibilityFocus
  return when (selectedFocus) {
    is CurrentInputFocus.Inside ->
      selectedFocus.containingContexts
        .firstOrNull { it.isActive }
        ?.let {
          ActiveInputContext.Portal(it.identity)
        } ?: ActiveInputContext.Host
    CurrentInputFocus.KnownOutside -> ActiveInputContext.Host
    CurrentInputFocus.Absent,
    CurrentInputFocus.Unmappable -> ActiveInputContext.WindowFallback
  }
}
