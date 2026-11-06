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
      : contentOffsetX(
            data.count("contentOffsetX") != 0
                ? static_cast<float>(data["contentOffsetX"].getDouble())
                : previousState.contentOffsetX),
        contentOffsetY(
            data.count("contentOffsetY") != 0
                ? static_cast<float>(data["contentOffsetY"].getDouble())
                : previousState.contentOffsetY),
        contentScale(
            data.count("contentScale") != 0
                ? static_cast<float>(data["contentScale"].getDouble())
                : previousState.contentScale),
        frameSize(
            data.count("frameWidth") != 0 && data.count("frameHeight") != 0
                ? Size{static_cast<Float>(data["frameWidth"].getDouble()),
                       static_cast<Float>(data["frameHeight"].getDouble())}
                : previousState.frameSize),
        contentRegionInset(
            data.count("contentRegionInset") != 0
                ? static_cast<Float>(data["contentRegionInset"].getDouble())
                : previousState.contentRegionInset),
        surfaceOverflow(
            data.count("surfaceOverflow") != 0
                ? static_cast<Float>(data["surfaceOverflow"].getDouble())
                : previousState.surfaceOverflow) {}
#endif

  // Where the card puts the surface and content. They change as the sheet moves,
  // and exist so measure follows what is drawn; the native host applies the same
  // geometry to the views themselves. The sheet's children are scaled by
  // `contentScale` about the sheet's top-left corner and then shifted right by
  // `contentOffsetX`; they, rather than the sheet, report that as their
  // transform (see BottomSheetCardContent). `contentOffsetY` moves the whole
  // sheet coordinate space down to the container's current top.
  float contentOffsetX{0};
  float contentOffsetY{0};
  float contentScale{1};
  // Real measured size (dp) of the sheet in its window, reported natively once
  // it lays out. Consumed by the component descriptor only in native-overlay
  // mode, where the sheet is hoisted out of its in-tree slot; zero until the
  // first native report.
  Size frameSize{};
  // The natively measured inset of the content region (dp): the gap between
  // the sheet's height and the tallest height the sheet can reach — the card's
  // height at the tallest detent, clamped to the space its insets leave, in
  // layout units. Applied in every mode, so in-flow content (the flex: 1
  // wrapper) resolves to exactly the region the sheet can actually show: as
  // Yoga bottom padding on the sheet node, or, when negative because a narrow
  // card's region outgrows the sheet, as the wrapper's bottom overflow.
  Float contentRegionInset{0};
  // How far (dp) the surface extends below the sheet, in layout units, so that
  // once scaled it still covers the card at every detent.
  Float surfaceOverflow{0};

#ifdef ANDROID
  folly::dynamic getDynamic() const {
    return folly::dynamic::object("contentOffsetX", contentOffsetX)(
        "contentOffsetY", contentOffsetY)("contentScale", contentScale)(
        "frameWidth", frameSize.width)("frameHeight", frameSize.height)(
        "contentRegionInset", contentRegionInset)(
        "surfaceOverflow", surfaceOverflow);
  }

  MapBuffer getMapBuffer() const {
    return MapBufferBuilder::EMPTY();
  }
#endif
};

} // namespace facebook::react
