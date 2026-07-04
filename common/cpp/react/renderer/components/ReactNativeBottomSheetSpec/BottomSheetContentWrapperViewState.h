#pragma once

#include <react/renderer/graphics/Size.h>

#ifdef ANDROID
#include <folly/dynamic.h>
#include <react/renderer/mapbuffer/MapBuffer.h>
#include <react/renderer/mapbuffer/MapBufferBuilder.h>
#endif

namespace facebook::react {

class BottomSheetContentWrapperViewState final {
 public:
  BottomSheetContentWrapperViewState() = default;

#ifdef ANDROID
  BottomSheetContentWrapperViewState(
      const BottomSheetContentWrapperViewState& previousState,
      folly::dynamic data)
      : frameSize(
            data.count("frameWidth") != 0 && data.count("frameHeight") != 0
                ? Size{static_cast<Float>(data["frameWidth"].getDouble()),
                       static_cast<Float>(data["frameHeight"].getDouble())}
                : previousState.frameSize) {}
#endif

  // Target size (dp) of the content wrapper: the sheet's measured width by the
  // natively computed detent cap. Pushed by the native side in every mode once
  // the sheet is measured in a window. Zero (before the first native report)
  // means the JS-provided styles stay in effect.
  Size frameSize{};

#ifdef ANDROID
  folly::dynamic getDynamic() const {
    return folly::dynamic::object("frameWidth", frameSize.width)(
        "frameHeight", frameSize.height);
  }

  MapBuffer getMapBuffer() const {
    return MapBufferBuilder::EMPTY();
  }
#endif
};

} // namespace facebook::react
