#pragma once

#include "ShadowNodes.h"
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

    // The natively measured top inset of the content region — the gap between
    // the sheet's top and the detent cap — applied as Yoga top padding in
    // every mode. In-flow content (the flex: 1 wrapper) then lays out exactly
    // within the region the sheet can actually show; absolutely positioned
    // children (the surface) ignore padding and keep filling the node.
    layoutableShadowNode.setPadding({0, stateData.contentRegionInsetTop, 0, 0});

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

    ConcreteComponentDescriptor::adopt(shadowNode);
  }
};

// The surface needs no custom initial state, so this mirrors the codegen alias.
using BottomSheetSurfaceViewComponentDescriptor =
    ConcreteComponentDescriptor<BottomSheetSurfaceViewShadowNode>;

} // namespace facebook::react
