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
  // Partial updates arrive from independent native sources — the host pushes
  // `contentOffsetY` on every sheet movement and its measured geometry when it
  // changes — and each sends only its own keys. A missing key keeps the
  // previous state's value.
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
                : previousState.frameSize),
        contentRegionInsetTop(
            data.count("contentRegionInsetTop") != 0
                ? static_cast<Float>(data["contentRegionInsetTop"].getDouble())
                : previousState.contentRegionInsetTop) {}
#endif

  float contentOffsetY{0};
  // Real measured size (dp) of the sheet in its window, reported natively once
  // it lays out. Consumed by the component descriptor only in native-overlay
  // mode, where the sheet is hoisted out of its in-tree slot; zero until the
  // first native report.
  Size frameSize{};
  // The natively measured top inset of the content region (dp): the gap
  // between the sheet's top and the detent cap — the overlapping status-bar
  // inset unless extend-under-status-bar. Applied as Yoga top padding on the
  // sheet node in every mode, so in-flow content (the flex: 1 wrapper) lays
  // out exactly within the region the sheet can actually show.
  Float contentRegionInsetTop{0};

#ifdef ANDROID
  folly::dynamic getDynamic() const {
    return folly::dynamic::object("contentOffsetY", contentOffsetY)(
        "frameWidth", frameSize.width)("frameHeight", frameSize.height)(
        "contentRegionInsetTop", contentRegionInsetTop);
  }

  MapBuffer getMapBuffer() const {
    return MapBufferBuilder::EMPTY();
  }
#endif
};

} // namespace facebook::react
