#pragma once

#include <memory>
#include <react/renderer/core/State.h>
#include <react/renderer/graphics/Size.h>

namespace facebook::react {

void updateBottomSheetContentOffsetY(
    const State::Shared& state,
    float contentOffsetY);

// Pushes the sheet's measured geometry into its state: the frame size
// (consumed by the component descriptor only in overlay mode) and the content
// region's top inset (applied as Yoga top padding in every mode).
void updateBottomSheetGeometry(
    const State::Shared& state,
    Size frameSize,
    Float contentRegionInsetTop);

} // namespace facebook::react
