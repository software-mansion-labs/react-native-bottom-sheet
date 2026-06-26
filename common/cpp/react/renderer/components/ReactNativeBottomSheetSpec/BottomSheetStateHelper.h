#pragma once

#include <memory>
#include <react/renderer/core/State.h>

namespace facebook::react {

void updateBottomSheetContentOffset(
    const State::Shared& state,
    float contentOffsetX,
    float contentOffsetY);

} // namespace facebook::react
