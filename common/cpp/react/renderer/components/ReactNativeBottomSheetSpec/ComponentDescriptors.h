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

    // In native-overlay mode the sheet's real container is the full-screen
    // overlay window, not the in-tree slot. Both platforms report the sheet's
    // measured size through the state; force the node's size from it — exactly
    // as <Modal> does — so Yoga lays the subtree out against the overlay's
    // true edge-to-edge bounds rather than JS-estimated window dimensions,
    // which under edge-to-edge exclude the system bars. The size stays zero
    // until the first native report, leaving the JS-provided first-frame
    // estimate in effect; inline sheets are Fabric-owned (this branch is
    // gated on the prop).
    if (props.nativeOverlay) {
      const auto& stateData =
          static_cast<const BottomSheetViewShadowNode::ConcreteState&>(
              *shadowNode.getState())
              .getData();
      if (stateData.frameSize.width != 0 && stateData.frameSize.height != 0) {
        auto& layoutableShadowNode =
            static_cast<YogaLayoutableShadowNode&>(shadowNode);
        layoutableShadowNode.setSize(stateData.frameSize);
        layoutableShadowNode.setPositionType(YGPositionTypeAbsolute);
      }
    }

    ConcreteComponentDescriptor::adopt(shadowNode);
  }
};

// The surface needs no custom initial state, so this mirrors the codegen alias.
using BottomSheetSurfaceViewComponentDescriptor =
    ConcreteComponentDescriptor<BottomSheetSurfaceViewShadowNode>;

class BottomSheetContentWrapperViewComponentDescriptor final
    : public ConcreteComponentDescriptor<BottomSheetContentWrapperViewShadowNode> {
 public:
  using ConcreteComponentDescriptor::ConcreteComponentDescriptor;

  State::Shared createInitialState(
      const Props::Shared& /*props*/,
      const ShadowNodeFamily::Shared& family) const override {
    return std::make_shared<
        BottomSheetContentWrapperViewShadowNode::ConcreteState>(
        std::make_shared<const BottomSheetContentWrapperViewState>(),
        family);
  }

  // The native side pushes the wrapper's target size (sheet width × natively
  // computed detent cap) through the state in every mode; force the node's
  // Yoga size from it so the content subtree lays out against measured window
  // geometry. Zero state — before the sheet's first native measure — leaves
  // the JS-provided styles in effect.
  void adopt(ShadowNode& shadowNode) const override {
    const auto& stateData =
        static_cast<
            const BottomSheetContentWrapperViewShadowNode::ConcreteState&>(
            *shadowNode.getState())
            .getData();
    if (stateData.frameSize.width != 0 && stateData.frameSize.height != 0) {
      auto& layoutableShadowNode =
          static_cast<YogaLayoutableShadowNode&>(shadowNode);
      layoutableShadowNode.setSize(stateData.frameSize);
      layoutableShadowNode.setPositionType(YGPositionTypeAbsolute);
    }

    ConcreteComponentDescriptor::adopt(shadowNode);
  }
};

} // namespace facebook::react
