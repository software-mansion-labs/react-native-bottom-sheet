package com.swmansion.reactnativebottomsheet.presentation

/** Top and Android Close fallback stay distinct from the presentations retained for isolation. */
internal data class PresentationOwnershipDecision<Identity : Any>(
  val top: Identity?,
  val closeFallbackOwner: Identity?,
  val retainedPresentations: List<Identity>,
)

/**
 * Resolves one authority-established presentation group from current native order facts. The
 * authority supplies unique Active identities in registration order, including visible closing.
 * Registration order selects only Android's Close fallback when no unique Top is proved. This
 * stateless decision neither discovers context boundaries nor changes isolation or input.
 */
internal fun <Identity : Any> resolvePresentationOwnership(
  activePresentations: List<Identity>,
  compare: (Identity, Identity) -> NativePortalOrder,
): PresentationOwnershipDecision<Identity> {
  val topIndex =
    activePresentations.indices.singleOrNull { candidate ->
      activePresentations.indices.all { other ->
        candidate == other ||
          compare(activePresentations[candidate], activePresentations[other]) ==
            NativePortalOrder.ABOVE
      }
    }
  val top = topIndex?.let(activePresentations::get)
  return PresentationOwnershipDecision(
    top,
    if (top == null) activePresentations.lastOrNull() else null,
    top?.let(::listOf) ?: activePresentations.toList(),
  )
}
