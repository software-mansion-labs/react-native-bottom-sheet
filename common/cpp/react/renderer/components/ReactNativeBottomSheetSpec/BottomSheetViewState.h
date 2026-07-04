#pragma once

#include <react/renderer/graphics/Size.h>

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
  // Partial updates arrive from two independent native sources — the host
  // pushes `contentOffsetY` on every sheet movement, the overlay dialog pushes
  // its frame size when it (re)measures — and each sends only its own keys.
  // A missing key keeps the previous state's value.
  BottomSheetViewState(
      const BottomSheetViewState& previousState,
      folly::dynamic data)
      : contentOffsetY(
            data.count("contentOffsetY") != 0
                ? static_cast<float>(data["contentOffsetY"].getDouble())
                : previousState.contentOffsetY),
        frameSize(
            data.count("frameWidth") != 0 && data.count("frameHeight") != 0
                ? Size{static_cast<Float>(data["frameWidth"].getDouble()),
                       static_cast<Float>(data["frameHeight"].getDouble())}
                : previousState.frameSize) {}
#endif

  float contentOffsetY{0};
  // Real measured size (dp) of the native-overlay dialog window, reported by
  // Android once the dialog lays out (see BottomSheetHostView's
  // updateOverlayFrameState). Zero until then — and always on iOS, where the
  // sheet is sized by UIKit — so the component descriptor only forces the
  // node's size from it when it is actually set.
  Size frameSize{};

#ifdef ANDROID
  folly::dynamic getDynamic() const {
    return folly::dynamic::object("contentOffsetY", contentOffsetY)(
        "frameWidth", frameSize.width)("frameHeight", frameSize.height);
  }

  MapBuffer getMapBuffer() const {
    return MapBufferBuilder::EMPTY();
  }
#endif
};

} // namespace facebook::react
