#include "BottomSheetStateHelper.h"
#include "ShadowNodes.h"

namespace facebook::react {

Point BottomSheetViewShadowNode::getContentOriginOffset(
    bool /*includeTransform*/) const {
  auto state =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          getState());
  return {0, state->getData().contentOffsetY};
}

void BottomSheetViewShadowNode::setIsOverlayRoot(bool isOverlayRoot) {
  // In native-overlay mode the sheet content is physically re-rooted to a
  // window-level full-screen overlay, decoupled from where this view sits in the
  // React tree. Marking the node as a layout root — exactly as <Modal> does —
  // makes `getRelativeLayoutMetrics` stop its ancestor walk here, so measure and
  // touch coordinates for descendants resolve relative to this full-screen root
  // instead of accumulating the (possibly nonzero) outer-tree origin. Inline that
  // accumulation is correct, so the trait is only set in overlay mode.
  if (isOverlayRoot) {
    traits_.set(ShadowNodeTraits::Trait::RootNodeKind);
  } else {
    traits_.unset(ShadowNodeTraits::Trait::RootNodeKind);
  }
}

void updateBottomSheetContentOffsetY(
    const State::Shared& state,
    float contentOffsetY) {
  auto concreteState =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          state);
  concreteState->updateState(
      [contentOffsetY](const BottomSheetViewState& /*oldState*/)
          -> BottomSheetViewShadowNode::ConcreteState::SharedData {
        auto newState = std::make_shared<BottomSheetViewState>();
        const_cast<BottomSheetViewState&>(*newState).contentOffsetY =
            contentOffsetY;
        return newState;
      });
}

} // namespace facebook::react
