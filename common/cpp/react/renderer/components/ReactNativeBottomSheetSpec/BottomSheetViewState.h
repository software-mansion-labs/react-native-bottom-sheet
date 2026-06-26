#pragma once

#ifdef ANDROID
#include <folly/dynamic.h>
#include <react/renderer/mapbuffer/MapBuffer.h>
#include <react/renderer/mapbuffer/MapBufferBuilder.h>
#endif

namespace facebook::react {

class BottomSheetViewState final {
 public:
  BottomSheetViewState() = default;

#ifdef ANDROID
  BottomSheetViewState(
      const BottomSheetViewState& previousState,
      folly::dynamic data)
      : contentOffsetY(
            static_cast<float>(data["contentOffsetY"].getDouble())) {}
#endif

  float contentOffsetY{0};

#ifdef ANDROID
  folly::dynamic getDynamic() const {
    return folly::dynamic::object("contentOffsetY", contentOffsetY);
  }

  MapBuffer getMapBuffer() const {
    return MapBufferBuilder::EMPTY();
  }
#endif
};

} // namespace facebook::react
