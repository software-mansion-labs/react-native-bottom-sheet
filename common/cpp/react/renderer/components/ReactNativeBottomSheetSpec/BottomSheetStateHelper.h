#pragma once

#include <memory>
#include <react/renderer/core/State.h>
#include <react/renderer/graphics/Size.h>

namespace facebook::react {

void updateBottomSheetContentOffsetY(
    const State::Shared& state,
    float contentOffsetY);

// Pushes the overlay window's measured size into the sheet node's state
// (consumed by BottomSheetViewComponentDescriptor::adopt in overlay mode).
void updateBottomSheetFrameSize(const State::Shared& state, Size frameSize);

// Pushes the content wrapper's target size (overlay width × detent cap) into
// the wrapper node's state. Pass a zero size to clear, restoring JS-provided
// styles.
void updateBottomSheetContentWrapperFrameSize(
    const State::Shared& state,
    Size frameSize);

} // namespace facebook::react
