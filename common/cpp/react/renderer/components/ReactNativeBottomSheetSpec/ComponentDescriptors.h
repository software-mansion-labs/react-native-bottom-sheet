#pragma once

#include "ShadowNodes.h"
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
    ConcreteComponentDescriptor::adopt(shadowNode);
  }
};

// The surface needs no custom initial state, so this mirrors the codegen alias.
using BottomSheetSurfaceViewComponentDescriptor =
    ConcreteComponentDescriptor<BottomSheetSurfaceViewShadowNode>;

} // namespace facebook::react
