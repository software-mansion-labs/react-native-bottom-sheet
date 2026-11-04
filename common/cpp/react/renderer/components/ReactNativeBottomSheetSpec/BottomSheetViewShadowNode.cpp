#include "BottomSheetStateHelper.h"
#include "ShadowNodes.h"

namespace facebook::react {

Point BottomSheetViewShadowNode::getContentOriginOffset(
    bool /*includeTransform*/) const {
  auto state =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          getState());
  return {state->getData().contentOffsetX, state->getData().contentOffsetY};
}

Transform BottomSheetViewShadowNode::getTransform() const {
  auto scale =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          getState())
          ->getData()
          .contentScale;
  if (scale == 1) {
    return Transform::Identity();
  }
  // Transforms apply about the node's center; this pins its top-left corner.
  // It only reaches measure and the like: the host scales the native views, and
  // Fabric never mounts a shadow node's `getTransform`. A native-overlay sheet is
  // a layout root, whose transform `measure` skips, so there only
  // `measureInWindow` includes the scale.
  auto size = getLayoutMetrics().frame.size;
  auto transform = Transform::Scale(scale, scale, 1);
  transform.matrix[12] = -size.width / 2 * (1 - scale);
  transform.matrix[13] = -size.height / 2 * (1 - scale);
  return transform;
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

void updateBottomSheetState(
    const State::Shared& state,
    float contentOffsetX,
    float contentOffsetY,
    float contentScale,
    Size frameSize,
    Float contentRegionInset) {
  auto concreteState =
      std::static_pointer_cast<const BottomSheetViewShadowNode::ConcreteState>(
          state);
  concreteState->updateState(
      [contentOffsetX, contentOffsetY, contentScale, frameSize, contentRegionInset](
          const BottomSheetViewState& /*oldState*/)
          -> BottomSheetViewShadowNode::ConcreteState::SharedData {
        // Every field is set: updates coalesce by wholesale replacement (see
        // BottomSheetStateHelper.h), so nothing may rely on the old state.
        auto newState = std::make_shared<BottomSheetViewState>();
        auto& mutableState = const_cast<BottomSheetViewState&>(*newState);
        mutableState.contentOffsetX = contentOffsetX;
        mutableState.contentOffsetY = contentOffsetY;
        mutableState.contentScale = contentScale;
        mutableState.frameSize = frameSize;
        mutableState.contentRegionInset = contentRegionInset;
        return newState;
      });
}

} // namespace facebook::react
