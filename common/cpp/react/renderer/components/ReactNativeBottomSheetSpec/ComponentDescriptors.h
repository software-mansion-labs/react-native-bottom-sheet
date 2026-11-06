#pragma once

#include "ShadowNodes.h"
#include <algorithm>
#include <react/renderer/components/view/YogaLayoutableShadowNode.h>
#include <react/renderer/core/ConcreteComponentDescriptor.h>

namespace facebook::react {

class BottomSheetViewComponentDescriptor final
    : public ConcreteComponentDescriptor<BottomSheetViewShadowNode> {
 public:
  using ConcreteComponentDescriptor::ConcreteComponentDescriptor;

  State::Shared createInitialState(
      const Props::Shared& /*props*/,
      const ShadowNodeFamily::Shared& family) const override {
    return std::make_shared<BottomSheetViewShadowNode::ConcreteState>(
        std::make_shared<const BottomSheetViewState>(),
        family);
  }

  // Runs on every create/clone, so the node's root-ness tracks the current
  // `nativeOverlay` prop. A native-overlay sheet presents its content in a
  // window-level full-screen overlay, so — like <Modal> — it must act as a layout
  // root for descendant measure/touch coordinates to resolve correctly.
  void adopt(ShadowNode& shadowNode) const override {
    auto& node = static_cast<BottomSheetViewShadowNode&>(shadowNode);
    const auto& props =
        static_cast<const BottomSheetViewProps&>(*node.getProps());
    node.setIsOverlayRoot(props.nativeOverlay);

    const auto& stateData =
        static_cast<const BottomSheetViewShadowNode::ConcreteState&>(
            *shadowNode.getState())
            .getData();
    auto& layoutableShadowNode =
        static_cast<YogaLayoutableShadowNode&>(shadowNode);

    // The natively measured inset of the content region — the gap between the
    // sheet's height and the tallest height the sheet can reach (the tallest
    // detent, clamped to the space its insets leave) — applied as Yoga BOTTOM
    // padding in every mode. Padding can't go negative, so a region taller than
    // the sheet is the wrapper's overflow instead (see `syncCardContent`).
    // In-flow content (the flex: 1 wrapper) then resolves to exactly that
    // height; absolutely positioned children (the surface) ignore padding and
    // are sized by their offsets, down to the surface's overflow. Bottom rather
    // than top keeps the content's Yoga origin at zero, matching its native
    // placement at the top of the (already offset) sheet container on both
    // platforms — the container's top offset is carried by the contentOffsetY
    // state instead.
    layoutableShadowNode.setPadding(
        {0, 0, 0, std::max<Float>(stateData.contentRegionInset, 0)});

    // In native-overlay mode the sheet's real container is the full-screen
    // overlay window, not the in-tree slot. Both platforms report the sheet's
    // measured size through the state; force the node's size from it — exactly
    // as <Modal> does — so Yoga lays the subtree out against the overlay's
    // true edge-to-edge bounds rather than JS-estimated window dimensions,
    // which under edge-to-edge exclude the system bars. The size stays zero
    // until the first native report, leaving the JS-provided first-frame
    // estimate in effect; inline sheets are Fabric-owned (this branch is
    // gated on the prop).
    if (props.nativeOverlay &&
        stateData.frameSize.width != 0 && stateData.frameSize.height != 0) {
      layoutableShadowNode.setSize(stateData.frameSize);
      layoutableShadowNode.setPositionType(YGPositionTypeAbsolute);
    }

    // The card's geometry rides the state, which this node's children report
    // as their transform. A clone with new state or new children copies it into
    // them here; React appends the children of its own clones later, which
    // `appendChild` covers.
    node.syncCardContent();

    ConcreteComponentDescriptor::adopt(shadowNode);
  }
};

// The surface and the content wrapper need no custom initial state, so these
// mirror the codegen aliases.
using BottomSheetSurfaceViewComponentDescriptor =
    ConcreteComponentDescriptor<BottomSheetSurfaceViewShadowNode>;

using BottomSheetContentWrapperViewComponentDescriptor =
    ConcreteComponentDescriptor<BottomSheetContentWrapperViewShadowNode>;

} // namespace facebook::react
