#pragma once

#include <memory>
#include <react/renderer/core/State.h>
#include <react/renderer/graphics/Size.h>

namespace facebook::react {

// Pushes a complete snapshot of the sheet's natively measured state: the
// content origin offset, the frame size (consumed by the component descriptor
// only in overlay mode), and the content region's inset (applied as Yoga
// bottom padding in every mode).
//
// A complete snapshot is mandatory: the EventQueue coalesces consecutive
// state updates for the same family by dropping the older update wholesale
// (the update callbacks do not compose), so split partial updates erase each
// other — e.g. a per-frame offset push right after a geometry push discards
// the geometry forever. Same convention as Android's pushStateSnapshot.
void updateBottomSheetState(
    const State::Shared& state,
    float contentOffsetY,
    Size frameSize,
    Float contentRegionInset);

} // namespace facebook::react
