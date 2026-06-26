#pragma once

#include <memory>
#include <react/renderer/core/State.h>

namespace facebook::react {

void updateBottomSheetContentOffsetY(
    const State::Shared& state,
    float contentOffsetY);

} // namespace facebook::react
